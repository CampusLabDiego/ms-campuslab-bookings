package cl.campuslab.bookings.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import cl.campuslab.bookings.dto.BookingRequest;
import cl.campuslab.bookings.dto.BookingResponse;
import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;

import cl.campuslab.bookings.messaging.BookingAuditMessage;
import cl.campuslab.bookings.messaging.BookingAuditService;
import cl.campuslab.bookings.messaging.BookingTaskMessage;
import cl.campuslab.bookings.messaging.BookingTaskService;

import cl.campuslab.bookings.repository.BookingRepository;
import cl.campuslab.bookings.stock.ArrivalPolicy;
import cl.campuslab.bookings.stock.BookingStockWorkflow;
import cl.campuslab.bookings.stock.CatalogClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CatalogClient catalog;
    private final BookingStockWorkflow stockWorkflow;
    private final BookingTaskService tasks;
    private final BookingAuditService audit;

    public BookingService(
            BookingRepository bookingRepository,
            CatalogClient catalog,
            BookingStockWorkflow stockWorkflow,
            BookingTaskService tasks,
            BookingAuditService audit
    ) {
        this.bookingRepository = bookingRepository;
        this.catalog = catalog;
        this.stockWorkflow = stockWorkflow;
        this.tasks = tasks;
        this.audit = audit;
    }

    public List<BookingResponse> findAll(Jwt jwt) {
        List<Booking> bookings = puedeGestionar(jwt)
                ? bookingRepository.findAll()
                : bookingRepository.findByUserId(ownerId(jwt));

        return bookings.stream()
                .map(this::toResponse)
                .toList();
    }

    public BookingResponse findById(Long id, Jwt jwt) {
        Booking booking;

        if (puedeGestionar(jwt)) {
            booking = findEntityById(id);
        } else {
            booking = bookingRepository
                    .findByIdAndUserId(id, ownerId(jwt))
                    .orElseThrow(() ->
                            new BookingNotFoundException(id));
        }

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse create(
            BookingRequest request,
            Jwt jwt
    ) {
        if (!request.endDateTime()
                .isAfter(request.startDateTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de término debe ser posterior al inicio."
            );
        }

        CatalogClient.ResourceView resource =
                catalog.resource(request.resourceId(), jwt);

        if (!Boolean.TRUE.equals(resource.active())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El recurso está inactivo."
            );
        }

        boolean esLaboratorio =
                "LABORATORIO".equals(resource.type())
                        || "LABORATORY".equals(resource.type());

        if (esLaboratorio && request.quantity() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un laboratorio se reserva con cantidad 1."
            );
        }

        Booking booking = Booking.builder()
                .userId(ownerId(jwt))
                .userEmail(displayEmail(jwt))
                .resourceId(request.resourceId())
                .resourceName(resource.name())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .purpose(request.purpose())
                .quantity(request.quantity())
                .status(BookingStatus.SOLICITADA)
                .build();

        Booking saved = bookingRepository.saveAndFlush(booking);

        tasks.enqueue(
                saved,
                BookingTaskMessage.EventType.REQUESTED,
                "booking:" + saved.getId() + ":requested"
        );

        audit.enqueue(
                saved,
                BookingAuditMessage.EventType.RESERVATION_CREATED,
                null,
                null,
                null,
                "booking:" + saved.getId() + ":created"
        );

        return toResponse(saved);
    }

    public BookingResponse changeStatus(
            Long id,
            BookingStatus newStatus,
            Jwt jwt
    ) {
        if (!puedeGestionar(jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu cuenta no tiene permiso para cambiar estados."
            );
        }

        // El workflow guarda el estado pendiente y el comando de stock.
        stockWorkflow.prepare(id, newStatus);

        // La confirmación llegará mediante RabbitMQ.
        return toResponse(findEntityById(id));
    }

    public BookingResponse reconcileStock(Long id, Jwt jwt) {
        if (!puedeGestionar(jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu cuenta no puede sincronizar stock."
            );
        }

        stockWorkflow.prepareReconciliation(id);

        return toResponse(findEntityById(id));
    }

    @Transactional
    public BookingResponse confirmArrival(Long id, Jwt jwt) {
        if (!tieneRol(jwt, "Cliente")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Esta operación corresponde al Cliente "
                            + "propietario de la reserva."
            );
        }

        String propietario = ownerId(jwt);

        Booking booking = bookingRepository
                .findLockedById(id)
                .filter(actual ->
                        propietario.equals(actual.getUserId()))
                .orElseThrow(() ->
                        new BookingNotFoundException(id));

        if (booking.getPendingStatus() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La reserva tiene una operación pendiente. "
                            + "Espera a que termine antes de confirmar "
                            + "la llegada."
            );
        }

        // Repetir la confirmación no genera cambios ni eventos.
        if (booking.getStatus() == BookingStatus.EN_USO) {
            return toResponse(booking);
        }

        if (booking.getStatus() != BookingStatus.EN_PREPARACION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo puedes confirmar la llegada cuando "
                            + "la reserva está EN PREPARACIÓN."
            );
        }

        if (!Boolean.TRUE.equals(booking.getStockReserved())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El stock de esta reserva todavía no está "
                            + "confirmado. Solicita al Operador "
                            + "que lo sincronice."
            );
        }

        try {
            ArrivalPolicy.validate(
                    booking.getStartDateTime(),
                    booking.getEndDateTime(),
                    LocalDateTime.now(ZoneOffset.UTC)
            );

        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    error.getMessage()
            );
        }

        String previousStatus = booking.getStatus().name();

        booking.setStatus(BookingStatus.EN_USO);

        Booking saved = bookingRepository.saveAndFlush(booking);

        tasks.enqueue(
                saved,
                BookingTaskMessage.EventType.ARRIVAL_CONFIRMED,
                "booking:" + saved.getId() + ":arrival"
        );

        audit.enqueue(
                saved,
                BookingAuditMessage.EventType.ARRIVAL_CONFIRMED,
                previousStatus,
                BookingStatus.EN_USO.name(),
                null,
                "booking:" + saved.getId() + ":arrival"
        );

        return toResponse(saved);
    }

    private boolean puedeGestionar(Jwt jwt) {
        return tieneRol(jwt, "Admin")
                || tieneRol(jwt, "Operador");
    }

    private boolean tieneRol(Jwt jwt, String rol) {
        List<String> roles = jwt.getClaimAsStringList("roles");

        return roles != null && roles.contains(rol);
    }

    private String ownerId(Jwt jwt) {
        String id = jwt.getClaimAsString("oid");

        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El token no identifica al propietario."
            );
        }

        return id;
    }

    private String displayEmail(Jwt jwt) {
        String username =
                jwt.getClaimAsString("preferred_username");

        if (username != null && !username.isBlank()) {
            return username;
        }

        String email = jwt.getClaimAsString("email");

        if (email != null && !email.isBlank()) {
            return email;
        }

        return ownerId(jwt);
    }

    private Booking findEntityById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() ->
                        new BookingNotFoundException(id));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getUserEmail(),
                booking.getResourceId(),
                booking.getResourceName(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getPurpose(),
                booking.getQuantity(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getPendingStatus()
        );
    }
}
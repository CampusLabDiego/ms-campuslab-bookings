package cl.campuslab.bookings.service;

import cl.campuslab.bookings.dto.BookingRequest;
import cl.campuslab.bookings.dto.BookingResponse;
import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;
import cl.campuslab.bookings.repository.BookingRepository;

import cl.campuslab.bookings.stock.BookingStockWorkflow;
import cl.campuslab.bookings.stock.CatalogClient;
import cl.campuslab.bookings.stock.CatalogRejectedException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CatalogClient catalog;
    private final BookingStockWorkflow stockWorkflow;

    public BookingService(
            BookingRepository bookingRepository,
            CatalogClient catalog,
            BookingStockWorkflow stockWorkflow
    ) {
        this.bookingRepository = bookingRepository;
        this.catalog = catalog;
        this.stockWorkflow = stockWorkflow;
    }

    public List<BookingResponse> findAll(Jwt jwt) {

        List<Booking> bookings = puedeGestionar(jwt)
                ? bookingRepository.findAll()
                : bookingRepository.findByUserId(ownerId(jwt));

        return bookings.stream()
                .map(this::toResponse)
                .toList();
    }

    public BookingResponse findById(
            Long id,
            Jwt jwt
    ) {
        Booking booking;

        if (puedeGestionar(jwt)) {
            booking = findEntityById(id);
        } else {
            booking = bookingRepository
                    .findByIdAndUserId(id, ownerId(jwt))
                    .orElseThrow(() ->
                            new BookingNotFoundException(id)
                    );
        }

        return toResponse(booking);
    }

    public BookingResponse create(
            BookingRequest request,
            Jwt jwt
    ) {
        if (
                !request.endDateTime()
                        .isAfter(request.startDateTime())
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de término debe ser posterior al inicio."
            );
        }

        CatalogClient.ResourceView resource =
                catalog.resource(
                        request.resourceId(),
                        jwt
                );

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

        return toResponse(
                bookingRepository.save(booking)
        );
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

        BookingStockWorkflow.PendingChange change =
                stockWorkflow.prepare(id, newStatus);

        executeStock(change, jwt);

        return toResponse(findEntityById(id));
    }

    public BookingResponse reconcileStock(
            Long id,
            Jwt jwt
    ) {
        if (!puedeGestionar(jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu cuenta no puede sincronizar stock."
            );
        }

        BookingStockWorkflow.PendingChange change =
                stockWorkflow.prepareReconciliation(id);

        executeStock(change, jwt);

        return toResponse(findEntityById(id));
    }

    private void executeStock(
            BookingStockWorkflow.PendingChange change,
            Jwt jwt
    ) {
        if (change == null) {
            return;
        }

        try {
            catalog.apply(change, jwt);

        } catch (CatalogRejectedException rejected) {
            stockWorkflow.reject(change);
            throw rejected;
        }

        // Si ocurre un error incierto, la intención queda
        // guardada para reintentar la misma operación.
        stockWorkflow.complete(change);
    }

    private boolean puedeGestionar(Jwt jwt) {
        List<String> roles =
                jwt.getClaimAsStringList("roles");

        return roles != null
                && (
                        roles.contains("Admin")
                        || roles.contains("Operador")
                );
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
                        new BookingNotFoundException(id)
                );
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
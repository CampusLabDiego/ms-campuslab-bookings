package cl.campuslab.bookings.service;

import cl.campuslab.bookings.dto.BookingRequest;
import cl.campuslab.bookings.dto.BookingResponse;
import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;
import cl.campuslab.bookings.exception.InvalidBookingStatusException;
import cl.campuslab.bookings.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
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
                    .orElseThrow(() -> new BookingNotFoundException(id));
        }

        return toResponse(booking);
    }

    public BookingResponse create(BookingRequest request, Jwt jwt) {

        if (!request.endDateTime().isAfter(request.startDateTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de término debe ser posterior al inicio."
            );
        }

        Booking booking = Booking.builder()
                .userId(ownerId(jwt))
                .userEmail(displayEmail(jwt))
                .resourceId(request.resourceId())
                .resourceName(request.resourceName())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .purpose(request.purpose())
                .quantity(request.quantity())
                .status(BookingStatus.SOLICITADA)
                .build();

        return toResponse(bookingRepository.save(booking));
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

        Booking booking = findEntityById(id);

        validateStatusTransition(booking.getStatus(), newStatus);

        booking.setStatus(newStatus);

        return toResponse(bookingRepository.save(booking));
    }

    private boolean puedeGestionar(Jwt jwt) {

        List<String> roles = jwt.getClaimAsStringList("roles");

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

        String username = jwt.getClaimAsString("preferred_username");

        if (username != null && !username.isBlank()) {
            return username;
        }

        String email = jwt.getClaimAsString("email");

        if (email != null && !email.isBlank()) {
            return email;
        }

        return ownerId(jwt);
    }

    private void validateStatusTransition(
            BookingStatus currentStatus,
            BookingStatus newStatus
    ) {

        boolean validTransition = switch (currentStatus) {

            case SOLICITADA ->
                    newStatus == BookingStatus.APROBADA
                    || newStatus == BookingStatus.CANCELADA;

            case APROBADA ->
                    newStatus == BookingStatus.EN_PREPARACION
                    || newStatus == BookingStatus.CANCELADA;

            case EN_PREPARACION ->
                    newStatus == BookingStatus.EN_USO;

            case EN_USO ->
                    newStatus == BookingStatus.DEVUELTA;

            case DEVUELTA, CANCELADA -> false;
        };

        if (!validTransition) {
            throw new InvalidBookingStatusException(
                    "No se puede cambiar de "
                    + currentStatus
                    + " a "
                    + newStatus
            );
        }
    }

    private Booking findEntityById(Long id) {

        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
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
                booking.getUpdatedAt()
        );
    }
}
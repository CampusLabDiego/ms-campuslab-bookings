package cl.campuslab.bookings.stock;

import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;
import cl.campuslab.bookings.exception.InvalidBookingStatusException;
import cl.campuslab.bookings.repository.BookingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingStockWorkflow {

    private final BookingRepository bookings;

    public BookingStockWorkflow(BookingRepository bookings) {
        this.bookings = bookings;
    }

    // Guarda la intención antes de comunicarse con Catálogo.
    @Transactional
    public PendingChange prepare(
            Long id,
            BookingStatus target
    ) {
        return prepare(
                locked(id),
                target,
                false
        );
    }

    @Transactional
    public PendingChange prepareReconciliation(Long id) {
        Booking booking = locked(id);

        if (!BookingTransitions.active(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se sincronizan reservas aprobadas, "
                    + "en preparación o en uso."
            );
        }

        return prepare(
                booking,
                booking.getStatus(),
                true
        );
    }

    private PendingChange prepare(
            Booking booking,
            BookingStatus target,
            boolean reconciliation
    ) {
        if (booking.getPendingStatus() != null) {

            if (booking.getPendingStatus() != target) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Hay una operación pendiente. "
                        + "Reintenta primero el cambio a "
                        + booking.getPendingStatus()
                        + "."
                );
            }

        } else {

            if (
                    !reconciliation
                    && booking.getStatus() == target
            ) {
                return null;
            }

            if (
                    !reconciliation
                    && !BookingTransitions.allowed(
                            booking.getStatus(),
                            target
                    )
            ) {
                throw new InvalidBookingStatusException(
                        "No se puede cambiar de "
                        + booking.getStatus()
                        + " a "
                        + target
                );
            }

            booking.setPendingStatus(target);

            bookings.saveAndFlush(booking);
        }

        return new PendingChange(
                booking.getId(),
                booking.getResourceId(),
                booking.getQuantity(),
                target,
                BookingTransitions.stockAction(target)
        );
    }

    @Transactional
    public void complete(PendingChange change) {
        Booking booking = locked(change.bookingId());

        // Otro reintento pudo haber completado la misma operación.
        if (
                booking.getPendingStatus() == null
                && booking.getStatus() == change.target()
        ) {
            return;
        }

        if (booking.getPendingStatus() != change.target()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Actualiza la reserva antes de continuar."
            );
        }

        booking.setStatus(change.target());
        booking.setPendingStatus(null);

        bookings.saveAndFlush(booking);
    }

    @Transactional
    public void reject(PendingChange change) {
        Booking booking = locked(change.bookingId());

        if (booking.getPendingStatus() == change.target()) {
            booking.setPendingStatus(null);

            bookings.saveAndFlush(booking);
        }
    }

    private Booking locked(Long id) {
        return bookings.findLockedById(id)
                .orElseThrow(() ->
                        new BookingNotFoundException(id)
                );
    }

    public record PendingChange(
            Long bookingId,
            Long resourceId,
            int quantity,
            BookingStatus target,
            String action
    ) {
    }
}
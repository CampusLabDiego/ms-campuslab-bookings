package cl.campuslab.bookings.stock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;
import cl.campuslab.bookings.exception.InvalidBookingStatusException;

import cl.campuslab.bookings.messaging.BookingAuditMessage;
import cl.campuslab.bookings.messaging.BookingAuditService;
import cl.campuslab.bookings.messaging.StockCommandMessage;
import cl.campuslab.bookings.messaging.StockOutbox;
import cl.campuslab.bookings.messaging.StockOutboxRepository;

import cl.campuslab.bookings.repository.BookingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingStockWorkflow {

    private static final List<StockOutbox.State> ACTIVE_STATES =
            List.of(
                    StockOutbox.State.PENDING,
                    StockOutbox.State.PUBLISHED
            );

    private final BookingRepository bookings;
    private final StockOutboxRepository outbox;
    private final BookingAuditService audit;

    public BookingStockWorkflow(
            BookingRepository bookings,
            StockOutboxRepository outbox,
            BookingAuditService audit
    ) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Transactional
    public PendingChange prepare(
            Long id,
            BookingStatus target
    ) {
        return prepare(locked(id), target, false);
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

        PendingChange change = new PendingChange(
                booking.getId(),
                booking.getResourceId(),
                booking.getQuantity(),
                target,
                BookingTransitions.stockAction(target)
        );

        enqueue(booking, change);

        return change;
    }

    private void enqueue(
            Booking booking,
            PendingChange change
    ) {
        StockOutbox existing = outbox
                .findFirstByBookingIdAndStateInOrderByCreatedAtDesc(
                        change.bookingId(),
                        ACTIVE_STATES
                )
                .orElse(null);

        if (existing != null) {
            boolean sameOperation =
                    existing.getTargetStatus().equals(
                            change.target().name()
                    )
                    && existing.getResourceId().equals(
                            change.resourceId()
                    )
                    && existing.getQuantity() == change.quantity()
                    && existing.getAction().name().equals(
                            change.action()
                    );

            if (!sameOperation) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La reserva ya tiene otra operación "
                                + "de stock pendiente."
                );
            }

            // Reutiliza la operación sin duplicar el evento.
            return;
        }

        StockCommandMessage command = new StockCommandMessage(
                UUID.randomUUID(),
                change.bookingId(),
                change.resourceId(),
                change.quantity(),
                StockCommandMessage.Action.valueOf(change.action()),
                Instant.now()
        );

        outbox.save(
                StockOutbox.pending(
                        command,
                        change.target().name()
                )
        );

        // Una reconciliación que mantiene el estado no se registra
        // como solicitud de cambio de estado.
        if (booking.getStatus() != change.target()) {
            audit.enqueue(
                    booking,
                    BookingAuditMessage.EventType.STATUS_CHANGE_REQUESTED,
                    booking.getStatus().name(),
                    change.target().name(),
                    command.operationId(),
                    "stock:" + command.operationId() + ":requested"
            );
        }
    }

    @Transactional
    public void complete(PendingChange change) {
        Booking booking = locked(change.bookingId());

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
        booking.setStockReserved(
                BookingTransitions.active(change.target())
        );
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
                        new BookingNotFoundException(id));
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
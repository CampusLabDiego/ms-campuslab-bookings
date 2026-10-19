package cl.campuslab.bookings.messaging;

import java.time.Instant;

import jakarta.validation.Validator;

import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.repository.BookingRepository;
import cl.campuslab.bookings.stock.BookingTransitions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockResultService {

    private final BookingRepository bookings;
    private final StockOutboxRepository outbox;
    private final BookingTaskService tasks;
    private final BookingAuditService audit;
    private final Validator validator;

    public StockResultService(
            BookingRepository bookings,
            StockOutboxRepository outbox,
            BookingTaskService tasks,
            BookingAuditService audit,
            Validator validator
    ) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.tasks = tasks;
        this.audit = audit;
        this.validator = validator;
    }

    @Transactional
    public boolean apply(StockResultMessage result) {
        if (
                result == null
                || !validator.validate(result).isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "El resultado de stock tiene datos inválidos."
            );
        }

        // Mantiene el orden de bloqueo:
        // primero la reserva y después la operación.
        Booking booking = bookings
                .findLockedById(result.bookingId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La reserva del resultado no existe."
                        ));

        StockOutbox operation = outbox
                .findLockedByOperationId(
                        result.operationId().toString()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La operación del resultado no existe."
                        ));

        validateCorrespondence(operation, result);

        StockOutbox.State resultingState =
                result.outcome() == StockResultMessage.Outcome.APPLIED
                        ? StockOutbox.State.APPLIED
                        : StockOutbox.State.REJECTED;

        if (operation.isTerminal()) {
            if (operation.getState() != resultingState) {
                throw new IllegalArgumentException(
                        "Se recibieron resultados contradictorios "
                                + "para la misma operación."
                );
            }

            // No repite cambios, tareas ni eventos de auditoría.
            return false;
        }

        BookingStatus target = BookingStatus.valueOf(
                operation.getTargetStatus()
        );

        if (booking.getPendingStatus() != target) {
            throw new IllegalArgumentException(
                    "El resultado no coincide con el cambio "
                            + "pendiente de la reserva."
            );
        }

        String previousStatus = booking.getStatus().name();
        boolean changed = booking.getStatus() != target;

        if (result.outcome() == StockResultMessage.Outcome.APPLIED) {
            booking.setStatus(target);
            booking.setStockReserved(
                    BookingTransitions.active(target)
            );

            operation.setLastError(null);

            // Una reconciliación que conserva el estado
            // no genera nuevas notificaciones ni cambios auditados.
            if (changed) {
                tasks.enqueue(
                        booking,
                        eventFor(target),
                        "stock:" + operation.getOperationId()
                );

                audit.enqueue(
                        booking,
                        BookingAuditMessage.EventType.STATUS_CHANGED,
                        previousStatus,
                        target.name(),
                        result.operationId(),
                        "stock:" + result.operationId() + ":applied"
                );
            }

        } else {
            // Conserva el estado anterior y su marca de stock.
            operation.setLastError(result.detail());

            if (changed) {
                audit.enqueue(
                        booking,
                        BookingAuditMessage.EventType.STATUS_CHANGE_REJECTED,
                        previousStatus,
                        target.name(),
                        result.operationId(),
                        "stock:" + result.operationId() + ":rejected"
                );
            }
        }

        booking.setPendingStatus(null);

        operation.setState(resultingState);
        operation.setUpdatedAt(Instant.now());

        // Reserva, resultado, tareas y auditoría se confirman juntos.
        bookings.save(booking);
        outbox.save(operation);

        return true;
    }

    private BookingTaskMessage.EventType eventFor(
            BookingStatus status
    ) {
        return switch (status) {
            case SOLICITADA ->
                    BookingTaskMessage.EventType.REQUESTED;

            case APROBADA ->
                    BookingTaskMessage.EventType.APPROVED;

            case EN_PREPARACION ->
                    BookingTaskMessage.EventType.PREPARATION_STARTED;

            case EN_USO ->
                    BookingTaskMessage.EventType.ARRIVAL_CONFIRMED;

            case DEVUELTA ->
                    BookingTaskMessage.EventType.RETURNED;

            case CANCELADA ->
                    BookingTaskMessage.EventType.CANCELLED;
        };
    }

    private void validateCorrespondence(
            StockOutbox operation,
            StockResultMessage result
    ) {
        boolean matches =
                operation.getBookingId().equals(result.bookingId())
                && operation.getResourceId().equals(result.resourceId())
                && operation.getAction() == result.action();

        if (!matches) {
            throw new IllegalArgumentException(
                    "El resultado no corresponde a los datos "
                            + "de la operación original."
            );
        }
    }
}
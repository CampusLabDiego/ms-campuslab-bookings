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
    private final Validator validator;

    public StockResultService(
            BookingRepository bookings,
            StockOutboxRepository outbox,
            Validator validator) {

        this.bookings = bookings;
        this.outbox = outbox;
        this.validator = validator;
    }

    @Transactional
    public boolean apply(StockResultMessage result) {
        if (result == null
                || !validator.validate(result).isEmpty()) {
            throw new IllegalArgumentException(
                    "El resultado de stock tiene datos invalidos.");
        }

        // Mantiene el mismo orden de bloqueo que el flujo de reservas:
        // primero la reserva y despues la operacion.
        Booking booking = bookings
                .findLockedById(result.bookingId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La reserva del resultado no existe."));

        StockOutbox operation = outbox
                .findLockedByOperationId(
                        result.operationId().toString())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La operacion del resultado no existe."));

        validateCorrespondence(operation, result);

        StockOutbox.State resultingState =
                result.outcome() == StockResultMessage.Outcome.APPLIED
                        ? StockOutbox.State.APPLIED
                        : StockOutbox.State.REJECTED;

        if (operation.isTerminal()) {
            if (operation.getState() != resultingState) {
                throw new IllegalArgumentException(
                        "Se recibieron resultados contradictorios "
                                + "para la misma operacion.");
            }

            // El resultado ya fue procesado.
            return false;
        }

        BookingStatus target =
                BookingStatus.valueOf(operation.getTargetStatus());

        if (booking.getPendingStatus() != target) {
            throw new IllegalArgumentException(
                    "El resultado no coincide con el cambio "
                            + "pendiente de la reserva.");
        }

        if (result.outcome() == StockResultMessage.Outcome.APPLIED) {
            booking.setStatus(target);
            booking.setStockReserved(
                    BookingTransitions.active(target));

            operation.setLastError(null);
        } else {
            // Un rechazo conserva el estado anterior de la reserva
            // y su marca actual de stock.
            operation.setLastError(result.detail());
        }

        booking.setPendingStatus(null);

        operation.setState(resultingState);
        operation.setUpdatedAt(Instant.now());

        bookings.save(booking);
        outbox.save(operation);

        return true;
    }

    private void validateCorrespondence(
            StockOutbox operation,
            StockResultMessage result) {

        boolean matches =
                operation.getBookingId().equals(result.bookingId())
                && operation.getResourceId().equals(result.resourceId())
                && operation.getAction() == result.action();

        if (!matches) {
            throw new IllegalArgumentException(
                    "El resultado no corresponde a los datos "
                            + "de la operacion original.");
        }
    }
}
package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Comando enviado a catalogo para modificar el stock.
 *
 * El mismo operationId debe conservarse al reintentar
 * una operacion.
 */
public record StockCommandMessage(

        @NotNull
        UUID operationId,

        @NotNull
        @Positive
        Long bookingId,

        @NotNull
        @Positive
        Long resourceId,

        @NotNull
        @Positive
        Integer quantity,

        @NotNull
        Action action,

        @NotNull
        Instant requestedAt

) {

    public enum Action {
        RESERVE,
        RELEASE,
        FINISH
    }
}
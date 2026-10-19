package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Resultado de una operacion de stock procesada por catalogo.
 */
public record StockResultMessage(

        @NotNull
        UUID operationId,

        @NotNull
        @Positive
        Long bookingId,

        @NotNull
        @Positive
        Long resourceId,

        @NotNull
        StockCommandMessage.Action action,

        @NotNull
        Outcome outcome,

        @NotBlank
        @Size(max = 500)
        String detail,

        @NotNull
        Instant processedAt

) {

    public enum Outcome {
        APPLIED,
        REJECTED
    }
}
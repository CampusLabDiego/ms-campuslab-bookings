package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Tarea asociada a una reserva.
 *
 * Las fechas de inicio y termino se expresan en UTC,
 * igual que en el modelo actual de reservas.
 *
 * taskId debe conservarse al reenviar la misma tarea.
 */
public record BookingTaskMessage(

        @NotNull
        UUID taskId,

        @NotNull
        TaskType taskType,

        @NotNull
        EventType eventType,

        @NotNull
        @Positive
        Long bookingId,

        @NotBlank
        String userId,

        @NotBlank
        @Email
        String userEmail,

        @NotBlank
        String resourceName,

        @NotNull
        LocalDateTime startDateTime,

        @NotNull
        LocalDateTime endDateTime,

        @NotBlank
        @Size(max = 255)
        String purpose,

        @NotNull
        @Positive
        Integer quantity,

        @NotBlank
        String status,

        @NotNull
        Instant occurredAt

) {

    public enum TaskType {
        EMAIL,
        PREPARATION,
        VOUCHER
    }

    public enum EventType {
        REQUESTED,
        APPROVED,
        PREPARATION_STARTED,
        ARRIVAL_CONFIRMED,
        RETURNED,
        CANCELLED
    }
}
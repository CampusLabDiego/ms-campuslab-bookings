package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Evento de auditoría de una reserva.
 *
 * ownerId identifica al propietario de la reserva.
 * actorId identifica a quien realizó la acción.
 *
 * Las confirmaciones automáticas utilizan SYSTEM como actor.
 * operationId permite relacionarlas con la solicitud original.
 *
 * eventId se conserva durante los reintentos.
 */
public record BookingAuditMessage(

        @NotNull
        UUID eventId,

        @NotNull
        @Positive
        Long bookingId,

        @NotNull
        EventType eventType,

        @Size(max = 30)
        String previousStatus,

        @NotBlank
        @Size(max = 30)
        String status,

        @Size(max = 30)
        String targetStatus,

        @NotBlank
        @Size(max = 100)
        String actorId,

        @Email
        @Size(max = 320)
        String actorEmail,

        @NotBlank
        @Size(max = 100)
        String ownerId,

        @NotBlank
        @Size(max = 255)
        String resourceName,

        UUID operationId,

        @NotNull
        Instant occurredAt

) {

    public enum EventType {
        RESERVATION_CREATED,
        STATUS_CHANGE_REQUESTED,
        STATUS_CHANGED,
        ARRIVAL_CONFIRMED,
        STATUS_CHANGE_REJECTED
    }
}
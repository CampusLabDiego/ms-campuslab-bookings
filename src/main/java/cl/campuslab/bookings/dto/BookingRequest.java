package cl.campuslab.bookings.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record BookingRequest(

        @NotNull
        @Positive
        Long resourceId,

        String resourceName,

        @NotNull
        @Future
        LocalDateTime startDateTime,

        @NotNull
        @Future
        LocalDateTime endDateTime,

        @NotBlank
        String purpose,

        @NotNull
        @Positive
        Integer quantity

) {
}
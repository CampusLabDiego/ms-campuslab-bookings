package cl.campuslab.bookings.dto;

import cl.campuslab.bookings.enums.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(

        Long id,
        Long userId,
        String userEmail,
        Long resourceId,
        String resourceName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String purpose,
        Integer quantity,
        BookingStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
}
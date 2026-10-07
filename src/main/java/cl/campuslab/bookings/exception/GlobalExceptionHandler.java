package cl.campuslab.bookings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<?> handleBookingNotFound(
            BookingNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        LocalDateTime.now(),
                        404,
                        exception.getMessage()
                ));
    }


    @ExceptionHandler(InvalidBookingStatusException.class)
    public ResponseEntity<?> handleInvalidStatus(
            InvalidBookingStatusException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        LocalDateTime.now(),
                        400,
                        exception.getMessage()
                ));
    }


    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String message
    ) {
    }
}
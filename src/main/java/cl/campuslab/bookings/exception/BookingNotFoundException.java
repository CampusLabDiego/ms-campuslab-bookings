package cl.campuslab.bookings.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Reserva no encontrada con id: " + id);
    }
}
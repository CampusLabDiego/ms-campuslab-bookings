package cl.campuslab.bookings.service;

import cl.campuslab.bookings.dto.BookingRequest;
import cl.campuslab.bookings.dto.BookingResponse;
import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.exception.BookingNotFoundException;
import cl.campuslab.bookings.exception.InvalidBookingStatusException;
import cl.campuslab.bookings.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;


    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }


    public List<BookingResponse> findAll() {

        return bookingRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public BookingResponse findById(Long id) {

        Booking booking = findEntityById(id);

        return toResponse(booking);
    }


    public BookingResponse create(BookingRequest request) {

        Booking booking = Booking.builder()
                .userId(request.userId())
                .userEmail(request.userEmail())
                .resourceId(request.resourceId())
                .resourceName(request.resourceName())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .purpose(request.purpose())
                .quantity(request.quantity())
                .status(BookingStatus.SOLICITADA)
                .build();


        Booking savedBooking = bookingRepository.save(booking);

        return toResponse(savedBooking);
    }


    public BookingResponse changeStatus(
            Long id,
            BookingStatus newStatus) {


        Booking booking = findEntityById(id);


        validateStatusTransition(
                booking.getStatus(),
                newStatus
        );


        booking.setStatus(newStatus);


        Booking updatedBooking = bookingRepository.save(booking);

        return toResponse(updatedBooking);
    }



    private void validateStatusTransition(
            BookingStatus currentStatus,
            BookingStatus newStatus) {


        boolean validTransition = switch (currentStatus) {

            case SOLICITADA ->
                    newStatus == BookingStatus.APROBADA
                    || newStatus == BookingStatus.CANCELADA;


            case APROBADA ->
                    newStatus == BookingStatus.EN_PREPARACION
                    || newStatus == BookingStatus.CANCELADA;


            case EN_PREPARACION ->
                    newStatus == BookingStatus.EN_USO;


            case EN_USO ->
                    newStatus == BookingStatus.DEVUELTA;


            case DEVUELTA, CANCELADA ->
                    false;
        };


        if (!validTransition) {

            throw new InvalidBookingStatusException(
                    "No se puede cambiar de "
                    + currentStatus
                    + " a "
                    + newStatus
            );
        }
    }



    private Booking findEntityById(Long id) {

        return bookingRepository.findById(id)
                .orElseThrow(() -> 
                    new BookingNotFoundException(id));
    }



    private BookingResponse toResponse(Booking booking) {

        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getUserEmail(),
                booking.getResourceId(),
                booking.getResourceName(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getPurpose(),
                booking.getQuantity(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
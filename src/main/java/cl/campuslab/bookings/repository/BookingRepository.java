package cl.campuslab.bookings.repository;

import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByUserId(String userId);

    Optional<Booking> findByIdAndUserId(Long id, String userId);

    List<Booking> findByResourceId(Long resourceId);
}
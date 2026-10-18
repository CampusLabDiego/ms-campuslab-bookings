package cl.campuslab.bookings.repository;

import cl.campuslab.bookings.entity.Booking;
import cl.campuslab.bookings.enums.BookingStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findLockedById(
            @Param("id") Long id
    );

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByUserId(String userId);

    Optional<Booking> findByIdAndUserId(
            Long id,
            String userId
    );

    List<Booking> findByResourceId(Long resourceId);
}
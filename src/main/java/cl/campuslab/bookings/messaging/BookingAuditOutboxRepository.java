package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingAuditOutboxRepository
        extends JpaRepository<BookingAuditOutbox, String> {

    List<BookingAuditOutbox>
    findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            BookingAuditOutbox.State state,
            Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT entry
            FROM BookingAuditOutbox entry
            WHERE entry.eventId = :eventId
            """)
    Optional<BookingAuditOutbox> findLockedByEventId(
            @Param("eventId") String eventId
    );
}
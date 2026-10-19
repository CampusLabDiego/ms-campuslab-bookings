package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingTaskOutboxRepository
        extends JpaRepository<BookingTaskOutbox, String> {

    List<BookingTaskOutbox>
    findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            BookingTaskOutbox.State state,
            Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from BookingTaskOutbox task
            where task.taskId = :taskId
            """)
    Optional<BookingTaskOutbox> findLockedByTaskId(
            @Param("taskId") String taskId);
}
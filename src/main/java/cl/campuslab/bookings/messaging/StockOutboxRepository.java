package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockOutboxRepository
        extends JpaRepository<StockOutbox, String> {

    List<StockOutbox>
    findTop20ByStateInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<StockOutbox.State> states,
            Instant now);

    Optional<StockOutbox>
    findFirstByBookingIdAndStateInOrderByCreatedAtDesc(
            Long bookingId,
            Collection<StockOutbox.State> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select operation
            from StockOutbox operation
            where operation.operationId = :operationId
            """)
    Optional<StockOutbox> findLockedByOperationId(
            @Param("operationId") String operationId);
}
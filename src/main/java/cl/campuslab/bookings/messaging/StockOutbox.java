package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "stock_outbox",
        indexes = {
                @Index(
                        name = "idx_stock_outbox_pending",
                        columnList = "state,next_attempt_at"),
                @Index(
                        name = "idx_stock_outbox_booking",
                        columnList = "booking_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class StockOutbox {

    @Id
    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCommandMessage.Action action;

    @Column(name = "target_status", nullable = false, length = 30)
    private String targetStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private Long version;

    public static StockOutbox pending(
            StockCommandMessage command,
            String targetStatus) {

        Instant now = Instant.now();

        StockOutbox operation = new StockOutbox();

        operation.setOperationId(command.operationId().toString());
        operation.setBookingId(command.bookingId());
        operation.setResourceId(command.resourceId());
        operation.setQuantity(command.quantity());
        operation.setAction(command.action());
        operation.setTargetStatus(targetStatus);

        operation.setState(State.PENDING);
        operation.setAttempts(0);
        operation.setNextAttemptAt(now);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);

        return operation;
    }

    public StockCommandMessage toMessage() {
        return new StockCommandMessage(
                UUID.fromString(operationId),
                bookingId,
                resourceId,
                quantity,
                action,
                createdAt
        );
    }

    public boolean isTerminal() {
        return state == State.APPLIED || state == State.REJECTED;
    }

    public enum State {
        PENDING,
        PUBLISHED,
        APPLIED,
        REJECTED
    }
}
package cl.campuslab.bookings.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "booking_audit_outbox",
        indexes = {
                @Index(
                        name = "idx_booking_audit_outbox_pending",
                        columnList = "state,next_attempt_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BookingAuditOutbox {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "LONGTEXT"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private Long version;

    public static BookingAuditOutbox pending(
            BookingAuditMessage message,
            String payload
    ) {
        if (
                message == null
                || message.eventId() == null
                || message.bookingId() == null
                || payload == null
                || payload.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "El evento de auditoría y su contenido son obligatorios."
            );
        }

        Instant now = Instant.now();

        BookingAuditOutbox entry = new BookingAuditOutbox();

        entry.setEventId(message.eventId().toString());
        entry.setBookingId(message.bookingId());
        entry.setPayload(payload);
        entry.setState(State.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(now);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        return entry;
    }

    @PrePersist
    public void beforeInsert() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }

        if (state == null) {
            state = State.PENDING;
        }

        updatedAt = now;
    }

    @PreUpdate
    public void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public enum State {
        PENDING,
        PUBLISHED
    }
}
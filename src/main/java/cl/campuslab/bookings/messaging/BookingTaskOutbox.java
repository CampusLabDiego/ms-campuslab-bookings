package cl.campuslab.bookings.messaging;

import java.time.Instant;

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
        name = "booking_task_outbox",
        indexes = {
                @Index(
                        name = "idx_booking_task_pending",
                        columnList = "state,next_attempt_at"),
                @Index(
                        name = "idx_booking_task_booking",
                        columnList = "booking_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BookingTaskOutbox {

    @Id
    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private BookingTaskMessage.TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private BookingTaskMessage.EventType eventType;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private Long version;

    public static BookingTaskOutbox pending(
            BookingTaskMessage task,
            String jsonPayload) {

        if (jsonPayload == null || jsonPayload.isBlank()) {
            throw new IllegalArgumentException(
                    "El contenido de la tarea no puede estar vacio.");
        }

        Instant now = Instant.now();

        BookingTaskOutbox operation = new BookingTaskOutbox();

        operation.setTaskId(task.taskId().toString());
        operation.setBookingId(task.bookingId());
        operation.setTaskType(task.taskType());
        operation.setEventType(task.eventType());
        operation.setPayload(jsonPayload);

        operation.setState(State.PENDING);
        operation.setAttempts(0);
        operation.setNextAttemptAt(now);
        operation.setCreatedAt(now);

        return operation;
    }

    public enum State {
        PENDING,
        PUBLISHED
    }
}
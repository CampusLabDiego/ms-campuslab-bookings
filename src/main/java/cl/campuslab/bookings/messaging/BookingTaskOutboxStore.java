package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingTaskOutboxStore {

    private final BookingTaskOutboxRepository repository;

    public BookingTaskOutboxStore(
            BookingTaskOutboxRepository repository) {

        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<String> findReadyTaskIds() {
        return repository
                .findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        BookingTaskOutbox.State.PENDING,
                        Instant.now())
                .stream()
                .map(BookingTaskOutbox::getTaskId)
                .toList();
    }

    @Transactional
    public Optional<Publication> claim(String taskId) {
        BookingTaskOutbox task = repository
                .findLockedByTaskId(taskId)
                .orElse(null);

        Instant now = Instant.now();

        if (task == null
                || task.getState() != BookingTaskOutbox.State.PENDING
                || task.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }

        int attempt = task.getAttempts() + 1;

        task.setAttempts(attempt);

        // Permite recuperar el intento si el publicador se interrumpe.
        task.setNextAttemptAt(now.plusSeconds(60));

        return Optional.of(new Publication(
                task.getTaskId(),
                attempt,
                task.getTaskType(),
                task.getPayload()
        ));
    }

    @Transactional
    public void markPublished(String taskId, int attempt) {
        BookingTaskOutbox task = repository
                .findLockedByTaskId(taskId)
                .orElse(null);

        if (!canUpdate(task, attempt)) {
            return;
        }

        task.setState(BookingTaskOutbox.State.PUBLISHED);
        task.setLastPublishedAt(Instant.now());
        task.setLastError(null);
    }

    @Transactional
    public void markFailed(
            String taskId,
            int attempt,
            String detail) {

        BookingTaskOutbox task = repository
                .findLockedByTaskId(taskId)
                .orElse(null);

        if (!canUpdate(task, attempt)) {
            return;
        }

        long delaySeconds = Math.min(
                300L,
                5L * (1L << Math.min(attempt - 1, 6))
        );

        String message = detail == null || detail.isBlank()
                ? "No se pudo confirmar la publicacion de la tarea."
                : detail;

        task.setState(BookingTaskOutbox.State.PENDING);
        task.setNextAttemptAt(
                Instant.now().plusSeconds(delaySeconds));

        task.setLastError(
                message.substring(0, Math.min(message.length(), 500)));
    }

    private boolean canUpdate(
            BookingTaskOutbox task,
            int attempt) {

        return task != null
                && task.getState() == BookingTaskOutbox.State.PENDING
                && task.getAttempts() == attempt;
    }

    public record Publication(
            String taskId,
            int attempt,
            BookingTaskMessage.TaskType taskType,
            String payload
    ) {
    }
}
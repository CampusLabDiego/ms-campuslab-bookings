package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockOutboxStore {

    private static final List<StockOutbox.State> ACTIVE_STATES =
            List.of(
                    StockOutbox.State.PENDING,
                    StockOutbox.State.PUBLISHED
            );

    private final StockOutboxRepository repository;

    public StockOutboxStore(StockOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<String> findReadyOperationIds() {
        return repository
                .findTop20ByStateInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        ACTIVE_STATES,
                        Instant.now())
                .stream()
                .map(StockOutbox::getOperationId)
                .toList();
    }

    @Transactional
    public Optional<Publication> claim(String operationId) {
        StockOutbox operation = repository
                .findLockedByOperationId(operationId)
                .orElse(null);

        Instant now = Instant.now();

        if (operation == null
                || operation.isTerminal()
                || operation.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }

        int attempt = operation.getAttempts() + 1;

        operation.setAttempts(attempt);
        operation.setUpdatedAt(now);

        // Permite recuperar la operacion si el publicador se interrumpe.
        operation.setNextAttemptAt(now.plusSeconds(60));

        return Optional.of(new Publication(
                operation.getOperationId(),
                attempt,
                operation.toMessage()
        ));
    }

    @Transactional
    public void markPublished(String operationId, int attempt) {
        StockOutbox operation = repository
                .findLockedByOperationId(operationId)
                .orElse(null);

        if (!canUpdate(operation, attempt)) {
            return;
        }

        Instant now = Instant.now();

        operation.setState(StockOutbox.State.PUBLISHED);
        operation.setLastPublishedAt(now);
        operation.setUpdatedAt(now);
        operation.setLastError(null);

        // Si no llega el resultado, reenvia la misma operacion.
        operation.setNextAttemptAt(now.plusSeconds(120));
    }

    @Transactional
    public void markFailed(
            String operationId,
            int attempt,
            String detail) {

        StockOutbox operation = repository
                .findLockedByOperationId(operationId)
                .orElse(null);

        if (!canUpdate(operation, attempt)) {
            return;
        }

        Instant now = Instant.now();

        long delaySeconds = Math.min(
                300L,
                5L * (1L << Math.min(attempt - 1, 6))
        );

        String message = detail == null || detail.isBlank()
                ? "No se pudo confirmar la publicacion."
                : detail;

        operation.setState(StockOutbox.State.PENDING);
        operation.setUpdatedAt(now);
        operation.setNextAttemptAt(now.plusSeconds(delaySeconds));
        operation.setLastError(
                message.substring(0, Math.min(message.length(), 500)));
    }

    private boolean canUpdate(StockOutbox operation, int attempt) {
        return operation != null
                && !operation.isTerminal()
                && operation.getAttempts() == attempt;
    }

    public record Publication(
            String operationId,
            int attempt,
            StockCommandMessage message
    ) {
    }
}
package cl.campuslab.bookings.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingAuditOutboxStore {

    private static final long CLAIM_SECONDS = 60;

    private final BookingAuditOutboxRepository repository;

    public BookingAuditOutboxStore(
            BookingAuditOutboxRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<String> findReadyEventIds() {
        return repository
                .findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        BookingAuditOutbox.State.PENDING,
                        Instant.now()
                )
                .stream()
                .map(BookingAuditOutbox::getEventId)
                .toList();
    }

    @Transactional
    public Optional<Publication> claim(String eventId) {
        BookingAuditOutbox entry = repository
                .findLockedByEventId(eventId)
                .orElse(null);

        Instant now = Instant.now();

        if (
                entry == null
                || entry.getState() != BookingAuditOutbox.State.PENDING
                || entry.getNextAttemptAt().isAfter(now)
        ) {
            return Optional.empty();
        }

        int attempt = entry.getAttempts() + 1;

        entry.setAttempts(attempt);
        entry.setNextAttemptAt(now.plusSeconds(CLAIM_SECONDS));

        repository.saveAndFlush(entry);

        return Optional.of(
                new Publication(
                        entry.getEventId(),
                        entry.getBookingId(),
                        entry.getPayload(),
                        attempt
                )
        );
    }

    @Transactional
    public void markPublished(
            String eventId,
            int attempt
    ) {
        BookingAuditOutbox entry = repository
                .findLockedByEventId(eventId)
                .orElse(null);

        if (!isCurrentAttempt(entry, attempt)) {
            return;
        }

        entry.setState(BookingAuditOutbox.State.PUBLISHED);
        entry.setLastPublishedAt(Instant.now());
        entry.setLastError(null);

        repository.saveAndFlush(entry);
    }

    @Transactional
    public void markFailed(
            String eventId,
            int attempt,
            String reason
    ) {
        BookingAuditOutbox entry = repository
                .findLockedByEventId(eventId)
                .orElse(null);

        if (!isCurrentAttempt(entry, attempt)) {
            return;
        }

        entry.setLastError(limit(reason));
        entry.setNextAttemptAt(
                Instant.now().plusSeconds(retryDelay(attempt))
        );

        repository.saveAndFlush(entry);
    }

    private boolean isCurrentAttempt(
            BookingAuditOutbox entry,
            int attempt
    ) {
        return entry != null
                && entry.getState() == BookingAuditOutbox.State.PENDING
                && entry.getAttempts() == attempt;
    }

    private long retryDelay(int attempt) {
        int exponent = Math.min(
                Math.max(attempt - 1, 0),
                6
        );

        return Math.min(
                300L,
                5L * (1L << exponent)
        );
    }

    private String limit(String reason) {
        if (reason == null || reason.isBlank()) {
            return "No se pudo confirmar la publicación del evento.";
        }

        return reason.length() <= 500
                ? reason
                : reason.substring(0, 500);
    }

    public record Publication(
            String eventId,
            Long bookingId,
            String payload,
            int attempt
    ) {
    }
}
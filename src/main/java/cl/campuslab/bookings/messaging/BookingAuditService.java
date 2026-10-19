package cl.campuslab.bookings.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import cl.campuslab.bookings.entity.Booking;

import jakarta.validation.Validator;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class BookingAuditService {

    private final BookingAuditOutboxRepository repository;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public BookingAuditService(
            BookingAuditOutboxRepository repository,
            JsonMapper jsonMapper,
            Validator validator
    ) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(
            Booking booking,
            BookingAuditMessage.EventType eventType,
            String previousStatus,
            String targetStatus,
            UUID operationId,
            String eventKey
    ) {
        if (
                booking == null
                || booking.getId() == null
                || booking.getStatus() == null
                || eventType == null
                || eventKey == null
                || eventKey.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La reserva y la identificación del evento son obligatorias."
            );
        }

        UUID eventId = UUID.nameUUIDFromBytes(
                (
                        "campuslab:booking-audit:v1:"
                                + booking.getId()
                                + ":"
                                + eventType.name()
                                + ":"
                                + eventKey
                ).getBytes(StandardCharsets.UTF_8)
        );

        if (repository.existsById(eventId.toString())) {
            return;
        }

        Actor actor = currentActor();

        BookingAuditMessage message = new BookingAuditMessage(
                eventId,
                booking.getId(),
                eventType,
                previousStatus,
                booking.getStatus().name(),
                targetStatus,
                actor.id(),
                actor.email(),
                booking.getUserId(),
                booking.getResourceName(),
                operationId,
                Instant.now()
        );

        if (!validator.validate(message).isEmpty()) {
            throw new IllegalArgumentException(
                    "El evento de auditoría contiene datos inválidos."
            );
        }

        String payload;

        try {
            payload = jsonMapper.writeValueAsString(message);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No se pudo serializar el evento de auditoría.",
                    exception
            );
        }

        repository.save(
                BookingAuditOutbox.pending(message, payload)
        );
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
                authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()
        ) {
            Jwt jwt = jwtAuth.getToken();

            String actorId = jwt.getClaimAsString("oid");

            if (actorId == null || actorId.isBlank()) {
                actorId = jwt.getSubject();
            }

            String email = jwt.getClaimAsString("preferred_username");

            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("email");
            }

            if (email != null && email.isBlank()) {
                email = null;
            }

            return new Actor(actorId, email);
        }

        // Los listeners de RabbitMQ procesan resultados automáticamente.
        return new Actor("SYSTEM", null);
    }

    private record Actor(
            String id,
            String email
    ) {
    }
}
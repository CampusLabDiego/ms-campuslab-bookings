package cl.campuslab.bookings.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Validator;

import cl.campuslab.bookings.entity.Booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class BookingTaskService {

    private final BookingTaskOutboxRepository repository;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public BookingTaskService(
            BookingTaskOutboxRepository repository,
            JsonMapper jsonMapper,
            Validator validator) {

        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(
            Booking booking,
            BookingTaskMessage.EventType eventType,
            String eventId) {

        if (booking == null
                || booking.getId() == null
                || eventType == null
                || eventId == null
                || eventId.isBlank()) {

            throw new IllegalArgumentException(
                    "Faltan datos para registrar las tareas de la reserva.");
        }

        Instant occurredAt = Instant.now();

        for (BookingTaskMessage.TaskType taskType
                : taskTypesFor(eventType)) {

            UUID taskId = deterministicTaskId(
                    booking.getId(),
                    eventType,
                    taskType,
                    eventId);

            if (repository.existsById(taskId.toString())) {
                continue;
            }

            BookingTaskMessage task = new BookingTaskMessage(
                    taskId,
                    taskType,
                    eventType,
                    booking.getId(),
                    booking.getUserId(),
                    booking.getUserEmail(),
                    booking.getResourceName(),
                    booking.getStartDateTime(),
                    booking.getEndDateTime(),
                    booking.getPurpose(),
                    booking.getQuantity(),
                    booking.getStatus().name(),
                    occurredAt
            );

            if (!validator.validate(task).isEmpty()) {
                throw new IllegalArgumentException(
                        "Los datos de la reserva no permiten "
                                + "generar una tarea valida.");
            }

            String payload = jsonMapper.writeValueAsString(task);

            repository.save(
                    BookingTaskOutbox.pending(task, payload));
        }
    }

    private Set<BookingTaskMessage.TaskType> taskTypesFor(
            BookingTaskMessage.EventType eventType) {

        return switch (eventType) {
            case REQUESTED, ARRIVAL_CONFIRMED, CANCELLED ->
                    EnumSet.of(
                            BookingTaskMessage.TaskType.EMAIL);

            case APPROVED, RETURNED ->
                    EnumSet.of(
                            BookingTaskMessage.TaskType.EMAIL,
                            BookingTaskMessage.TaskType.VOUCHER);

            case PREPARATION_STARTED ->
                    EnumSet.of(
                            BookingTaskMessage.TaskType.EMAIL,
                            BookingTaskMessage.TaskType.PREPARATION);
        };
    }

    private UUID deterministicTaskId(
            Long bookingId,
            BookingTaskMessage.EventType eventType,
            BookingTaskMessage.TaskType taskType,
            String eventId) {

        String identifier =
                "campuslab:booking-task:v1:"
                        + bookingId
                        + ":"
                        + eventType.name()
                        + ":"
                        + taskType.name()
                        + ":"
                        + eventId;

        return UUID.nameUUIDFromBytes(
                identifier.getBytes(StandardCharsets.UTF_8));
    }
}
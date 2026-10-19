package cl.campuslab.bookings.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingTaskPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(BookingTaskPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BookingTaskOutboxStore store;

    public BookingTaskPublisher(
            RabbitTemplate rabbitTemplate,
            BookingTaskOutboxStore store
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.store = store;
    }

    @Scheduled(
            fixedDelayString =
                    "${campuslab.messaging.task-delay-ms:5000}"
    )
    public void publishPendingTasks() {
        for (String taskId : store.findReadyTaskIds()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            store.claim(taskId).ifPresent(this::publish);
        }
    }

    private void publish(
            BookingTaskOutboxStore.Publication publication
    ) {
        String taskId = publication.taskId();
        int attempt = publication.attempt();

        String exchange = exchange(publication.taskType());
        String routingKey = routingKey(publication.taskType());

        CorrelationData correlation = new CorrelationData(
                taskId + ":task:" + attempt
        );

        try {
            MessageProperties properties = new MessageProperties();

            properties.setContentType("application/json");
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(taskId);
            properties.setCorrelationId(taskId);
            properties.setHeader("__TypeId__", "booking.task.v1");

            Message message = new Message(
                    publication.payload()
                            .getBytes(StandardCharsets.UTF_8),
                    properties
            );

            rabbitTemplate.send(
                    exchange,
                    routingKey,
                    message,
                    correlation
            );

            CorrelationData.Confirm confirmation = correlation
                    .getFuture()
                    .get(5, TimeUnit.SECONDS);

            if (correlation.getReturned() != null) {
                store.markFailed(
                        taskId,
                        attempt,
                        "La tarea no encontró una cola de destino."
                );

                log.warn(
                        "Tarea sin cola de destino: "
                                + "tarea={}, tipo={}, exchange={}, "
                                + "routingKey={}, intento={}",
                        taskId,
                        publication.taskType(),
                        exchange,
                        routingKey,
                        attempt
                );

                return;
            }

            if (!confirmation.isAck()) {
                store.markFailed(
                        taskId,
                        attempt,
                        "El broker rechazó la publicación de la tarea."
                );

                log.warn(
                        "Tarea rechazada por el broker: "
                                + "tarea={}, exchange={}, intento={}",
                        taskId,
                        exchange,
                        attempt
                );

                return;
            }

            store.markPublished(taskId, attempt);

            log.info(
                    "Tarea publicada: tarea={}, tipo={}, "
                            + "exchange={}, routingKey={}, intento={}",
                    taskId,
                    publication.taskType(),
                    exchange,
                    routingKey,
                    attempt
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            store.markFailed(
                    taskId,
                    attempt,
                    "La publicación de la tarea fue interrumpida."
            );

        } catch (Exception exception) {
            store.markFailed(
                    taskId,
                    attempt,
                    "No se pudo confirmar la publicación: "
                            + exception.getClass().getSimpleName()
            );

            log.warn(
                    "Tarea pendiente de reintento: "
                            + "tarea={}, exchange={}, intento={}, causa={}",
                    taskId,
                    exchange,
                    attempt,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String exchange(
            BookingTaskMessage.TaskType taskType
    ) {
        return switch (taskType) {
            case EMAIL, VOUCHER -> RabbitNames.TOPIC_EXCHANGE;
            case PREPARATION -> RabbitNames.COMMAND_EXCHANGE;
        };
    }

    private String routingKey(
            BookingTaskMessage.TaskType taskType
    ) {
        return switch (taskType) {
            case EMAIL -> RabbitNames.EMAIL_ROUTING_KEY;
            case PREPARATION -> RabbitNames.PREP_ROUTING_KEY;
            case VOUCHER -> RabbitNames.VOUCHER_ROUTING_KEY;
        };
    }
}
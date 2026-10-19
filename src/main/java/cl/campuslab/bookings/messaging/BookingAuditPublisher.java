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
public class BookingAuditPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(BookingAuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BookingAuditOutboxStore store;

    public BookingAuditPublisher(
            RabbitTemplate rabbitTemplate,
            BookingAuditOutboxStore store
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.store = store;
    }

    @Scheduled(
            fixedDelayString =
                    "${campuslab.messaging.audit-delay-ms:5000}"
    )
    public void publishPendingEvents() {
        for (String eventId : store.findReadyEventIds()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            store.claim(eventId).ifPresent(this::publish);
        }
    }

    private void publish(
            BookingAuditOutboxStore.Publication publication
    ) {
        String eventId = publication.eventId();
        int attempt = publication.attempt();

        CorrelationData correlation = new CorrelationData(
                eventId + ":audit:" + attempt
        );

        try {
            MessageProperties properties = new MessageProperties();

            properties.setContentType("application/json");
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(eventId);
            properties.setCorrelationId(eventId);
            properties.setHeader("__TypeId__", "booking.audit.v1");

            Message message = new Message(
                    publication.payload()
                            .getBytes(StandardCharsets.UTF_8),
                    properties
            );

            rabbitTemplate.send(
                    RabbitNames.TOPIC_EXCHANGE,
                    RabbitNames.AUDIT_ROUTING_KEY,
                    message,
                    correlation
            );

            CorrelationData.Confirm confirmation = correlation
                    .getFuture()
                    .get(5, TimeUnit.SECONDS);

            if (correlation.getReturned() != null) {
                store.markFailed(
                        eventId,
                        attempt,
                        "El evento de auditoría no encontró una cola de destino."
                );

                log.warn(
                        "Auditoría sin cola de destino: "
                                + "evento={}, reserva={}, intento={}",
                        eventId,
                        publication.bookingId(),
                        attempt
                );

                return;
            }

            if (!confirmation.isAck()) {
                store.markFailed(
                        eventId,
                        attempt,
                        "RabbitMQ rechazó la publicación del evento."
                );

                log.warn(
                        "Auditoría rechazada por el broker: "
                                + "evento={}, intento={}",
                        eventId,
                        attempt
                );

                return;
            }

            store.markPublished(eventId, attempt);

            log.info(
                    "Auditoría publicada: evento={}, reserva={}, "
                            + "exchange={}, routingKey={}, intento={}",
                    eventId,
                    publication.bookingId(),
                    RabbitNames.TOPIC_EXCHANGE,
                    RabbitNames.AUDIT_ROUTING_KEY,
                    attempt
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            store.markFailed(
                    eventId,
                    attempt,
                    "La publicación del evento fue interrumpida."
            );

        } catch (Exception exception) {
            store.markFailed(
                    eventId,
                    attempt,
                    "No se pudo confirmar la publicación: "
                            + exception.getClass().getSimpleName()
            );

            log.warn(
                    "Auditoría pendiente de reintento: "
                            + "evento={}, intento={}, causa={}",
                    eventId,
                    attempt,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
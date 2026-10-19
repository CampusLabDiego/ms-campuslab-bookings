package cl.campuslab.bookings.messaging;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class StockCommandPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(StockCommandPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final StockOutboxStore store;

    public StockCommandPublisher(
            RabbitTemplate rabbitTemplate,
            StockOutboxStore store) {

        this.rabbitTemplate = rabbitTemplate;
        this.store = store;
    }

    @Scheduled(
            fixedDelayString =
                    "${campuslab.messaging.outbox-delay-ms:5000}"
    )
    public void publishPendingOperations() {
        for (String operationId : store.findReadyOperationIds()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            store.claim(operationId).ifPresent(this::publish);
        }
    }

    private void publish(StockOutboxStore.Publication publication) {
        String operationId = publication.operationId();
        int attempt = publication.attempt();

        CorrelationData correlation = new CorrelationData(
                operationId + ":" + attempt);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitNames.COMMAND_EXCHANGE,
                    RabbitNames.STOCK_ROUTING_KEY,
                    publication.message(),
                    message -> {
                        message.getMessageProperties()
                                .setMessageId(operationId);

                        message.getMessageProperties()
                                .setCorrelationId(operationId);

                        message.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT);

                        return message;
                    },
                    correlation
            );

            CorrelationData.Confirm confirmation = correlation
                    .getFuture()
                    .get(5, TimeUnit.SECONDS);

            if (correlation.getReturned() != null) {
                store.markFailed(
                        operationId,
                        attempt,
                        "El mensaje no encontro una cola de destino.");

                log.warn(
                        "Operacion {} sin cola de destino, intento {}",
                        operationId,
                        attempt);

                return;
            }

            if (!confirmation.isAck()) {
                store.markFailed(
                        operationId,
                        attempt,
                        "El broker rechazo la publicacion.");

                log.warn(
                        "Publicacion rechazada para operacion {}, intento {}",
                        operationId,
                        attempt);

                return;
            }

            store.markPublished(operationId, attempt);

            log.info(
                    "Comando de stock publicado: operacion={}, reserva={}, intento={}",
                    operationId,
                    publication.message().bookingId(),
                    attempt);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            store.markFailed(
                    operationId,
                    attempt,
                    "La publicacion fue interrumpida.");

        } catch (Exception exception) {
            store.markFailed(
                    operationId,
                    attempt,
                    "No se pudo confirmar la publicacion: "
                            + exception.getClass().getSimpleName());

            log.warn(
                    "Publicacion pendiente de reintento: operacion={}, intento={}, causa={}",
                    operationId,
                    attempt,
                    exception.getClass().getSimpleName());
        }
    }
}
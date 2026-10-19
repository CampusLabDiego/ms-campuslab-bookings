package cl.campuslab.bookings.messaging;

import java.io.IOException;

import com.rabbitmq.client.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Component
public class StockResultListener {

    private static final Logger log =
            LoggerFactory.getLogger(StockResultListener.class);

    private static final int MAX_ATTEMPTS = 3;

    private final StockResultService service;

    public StockResultListener(StockResultService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitNames.STOCK_RESULT_QUEUE)
    public void receive(
            StockResultMessage result,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties()
                .getDeliveryTag();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean applied;

            try {
                // El servicio confirma su transaccion antes de retornar.
                applied = service.apply(result);

            } catch (RuntimeException exception) {
                boolean retry =
                        isTemporary(exception)
                                && attempt < MAX_ATTEMPTS;

                if (!retry) {
                    log.error(
                            "Resultado enviado a DLQ: mensaje={}, intento={}, causa={}",
                            message.getMessageProperties().getMessageId(),
                            attempt,
                            exception.getClass().getSimpleName());

                    channel.basicNack(deliveryTag, false, false);
                    return;
                }

                log.warn(
                        "Reintentando resultado de stock: mensaje={}, intento={}",
                        message.getMessageProperties().getMessageId(),
                        attempt);

                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    channel.basicNack(deliveryTag, false, false);
                    return;
                }

                continue;
            }

            // Si este ACK falla, RabbitMQ puede volver a entregar
            // el mensaje. El servicio reconoce los duplicados.
            channel.basicAck(deliveryTag, false);

            log.info(
                    "Resultado confirmado: operacion={}, reserva={}, resultado={}, duplicado={}",
                    result.operationId(),
                    result.bookingId(),
                    result.outcome(),
                    !applied);

            return;
        }
    }

    private boolean isTemporary(Throwable exception) {
        Throwable cause = exception;

        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof CannotCreateTransactionException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}

package cl.campuslab.bookings.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    private static final int MESSAGE_TTL_MS = 86_400_000;
    private static final int DLQ_TTL_MS = 604_800_000;

    private static final long MAX_MESSAGES = 10_000;
    private static final long MAX_BYTES = 10_485_760;

    @Bean
    public Declarables campuslabRabbitTopology() {
        DirectExchange commands = new DirectExchange(
                RabbitNames.COMMAND_EXCHANGE,
                true,
                false
        );

        TopicExchange topics = new TopicExchange(
                RabbitNames.TOPIC_EXCHANGE,
                true,
                false
        );

        DirectExchange deadLetters = new DirectExchange(
                RabbitNames.DEAD_LETTER_EXCHANGE,
                true,
                false
        );

        List<Declarable> declarations = new ArrayList<>();

        declarations.add(commands);
        declarations.add(topics);
        declarations.add(deadLetters);

        addCommandQueue(
                declarations,
                commands,
                deadLetters,
                RabbitNames.STOCK_QUEUE,
                RabbitNames.STOCK_ROUTING_KEY,
                RabbitNames.STOCK_DLQ
        );

        addCommandQueue(
                declarations,
                commands,
                deadLetters,
                RabbitNames.STOCK_RESULT_QUEUE,
                RabbitNames.STOCK_RESULT_ROUTING_KEY,
                RabbitNames.STOCK_RESULT_DLQ
        );

        Queue email = addCommandQueue(
                declarations,
                commands,
                deadLetters,
                RabbitNames.EMAIL_QUEUE,
                RabbitNames.EMAIL_ROUTING_KEY,
                RabbitNames.EMAIL_DLQ
        );

        Queue preparation = addCommandQueue(
                declarations,
                commands,
                deadLetters,
                RabbitNames.PREP_QUEUE,
                RabbitNames.PREP_ROUTING_KEY,
                RabbitNames.PREP_DLQ
        );

        Queue voucher = addCommandQueue(
                declarations,
                commands,
                deadLetters,
                RabbitNames.VOUCHER_QUEUE,
                RabbitNames.VOUCHER_ROUTING_KEY,
                RabbitNames.VOUCHER_DLQ
        );

        declarations.add(
                BindingBuilder.bind(email)
                        .to(topics)
                        .with(RabbitNames.EMAIL_TOPIC_PATTERN)
        );

        declarations.add(
                BindingBuilder.bind(preparation)
                        .to(topics)
                        .with(RabbitNames.PREP_TOPIC_PATTERN)
        );

        declarations.add(
                BindingBuilder.bind(voucher)
                        .to(topics)
                        .with(RabbitNames.VOUCHER_TOPIC_PATTERN)
        );

        Queue audit = addQueueWithDlq(
                declarations,
                deadLetters,
                RabbitNames.AUDIT_QUEUE,
                RabbitNames.AUDIT_DLQ
        );

        declarations.add(
                BindingBuilder.bind(audit)
                        .to(topics)
                        .with(RabbitNames.AUDIT_TOPIC_PATTERN)
        );

        return new Declarables(
                declarations.toArray(Declarable[]::new)
        );
    }

    private Queue addCommandQueue(
            List<Declarable> declarations,
            DirectExchange commands,
            DirectExchange deadLetters,
            String queueName,
            String routingKey,
            String deadLetterQueueName
    ) {
        Queue queue = addQueueWithDlq(
                declarations,
                deadLetters,
                queueName,
                deadLetterQueueName
        );

        declarations.add(
                BindingBuilder.bind(queue)
                        .to(commands)
                        .with(routingKey)
        );

        return queue;
    }

    private Queue addQueueWithDlq(
            List<Declarable> declarations,
            DirectExchange deadLetters,
            String queueName,
            String deadLetterQueueName
    ) {
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "classic")
                .ttl(MESSAGE_TTL_MS)
                .maxLength(MAX_MESSAGES)
                .withArgument("x-max-length-bytes", MAX_BYTES)
                .withArgument("x-overflow", "reject-publish")
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueueName)
                .build();

        Queue deadLetterQueue = QueueBuilder
                .durable(deadLetterQueueName)
                .withArgument("x-queue-type", "classic")
                .ttl(DLQ_TTL_MS)
                .maxLength(MAX_MESSAGES)
                .withArgument("x-max-length-bytes", MAX_BYTES)
                .withArgument("x-overflow", "drop-head")
                .build();

        declarations.add(queue);
        declarations.add(deadLetterQueue);

        declarations.add(
                BindingBuilder.bind(deadLetterQueue)
                        .to(deadLetters)
                        .with(deadLetterQueueName)
        );

        return queue;
    }
}
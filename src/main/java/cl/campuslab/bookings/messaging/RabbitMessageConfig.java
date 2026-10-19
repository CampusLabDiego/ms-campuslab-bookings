package cl.campuslab.bookings.messaging;

import java.util.Map;

import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        DefaultClassMapper classMapper = new DefaultClassMapper();

        classMapper.setTrustedPackages(
                "cl.campuslab.bookings.messaging");

        classMapper.setIdClassMapping(Map.of(
                "stock.command.v1", StockCommandMessage.class,
                "stock.result.v1", StockResultMessage.class
        ));

        classMapper.afterPropertiesSet();

        JacksonJsonMessageConverter converter =
                new JacksonJsonMessageConverter(
                        "cl.campuslab.bookings.messaging");

        converter.setClassMapper(classMapper);

        return converter;
    }
}
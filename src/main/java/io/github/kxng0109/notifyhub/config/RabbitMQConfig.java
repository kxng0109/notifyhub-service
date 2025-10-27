package io.github.kxng0109.notifyhub.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    public static final String DELAYED_EXCHANGE_NAME = "notifyhub_delayed_exchange";
    public static final String QUEUE_NAME = "notifications_queue";
    public static final String ROUTING_KEY = "notifications.routing.key";

    public static final String FAILURES_EXCHANGE_NAME = "notifications_failures_exchange";
    public static final String FAILURES_QUEUE_NAME = "notifications_failures_queue";

    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "topic");

        return new CustomExchange(
                DELAYED_EXCHANGE_NAME,
                "x-delayed-message",
                true,
                false,
                args
        );
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public Binding binding(Queue notificationQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(notificationQueue)
                             .to(delayedExchange)
                             .with(ROUTING_KEY)
                             .noargs();
    }

    @Bean
    public Queue failuresQueue() {
        return QueueBuilder.durable(FAILURES_QUEUE_NAME).build();
    }

    @Bean
    public FanoutExchange failuresExchange() {
        return new FanoutExchange(FAILURES_EXCHANGE_NAME);
    }

    @Bean
    public Binding failuresBinding(Queue failuresQueue, FanoutExchange failuresExchange) {
        return BindingBuilder.bind(failuresQueue).to(failuresExchange);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}

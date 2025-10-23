package io.github.kxng0109.notifyhub.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_NAME = "notifications_exchange";
    public static final String QUEUE_NAME = "notifications_queue";
    public static final String ROUTING_KEY = "notifications.routing.key";

    public static final String DEAD_LETTER_QUEUE_NAME = "notifications_dlq";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "notifications_dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "notifications.dlq.routing.key";

    @Value("${notifyhub.rabbitmq.dlq.ttl:5000}")
    private int dlqTtl;

    @Bean
    public Queue queue() {
        return QueueBuilder
                .durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME)
                           //How long to wait before retrying
                           .withArgument("x-message-ttl", dlqTtl)
                           //Send the messages to the original exchange after TTL expires
                           .withArgument("x-dead-letter-exchange", EXCHANGE_NAME)
                           //The routing key to use when sending messages to the original exchange
                           .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                           .build();
    }

    @Bean
    public FanoutExchange dlx() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE_NAME);
    }

    @Bean
    public Binding dlqBinding(Queue dlq, TopicExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(DEAD_LETTER_ROUTING_KEY);
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

package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.DELAYED_EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;

@Service
public class NotificationProducer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);
    public static final String HEADER_RETRY_COUNT = "x-retry-count";

    private final RabbitTemplate rabbitTemplate;

    public NotificationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNotification(NotificationRequest notificationRequest) {
        MessagePostProcessor postProcessor = message -> {
            message.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
            return message;
        };

        logger.info("Sending notification request to queue -> {}", notificationRequest);
        rabbitTemplate.convertAndSend(DELAYED_EXCHANGE_NAME, ROUTING_KEY, notificationRequest, postProcessor);
    }
}

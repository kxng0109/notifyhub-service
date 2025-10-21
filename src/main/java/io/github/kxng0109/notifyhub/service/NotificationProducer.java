package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;

@Service
public class NotificationProducer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNotification(NotificationRequest notificationRequest) {
        logger.info("Sending notification request to queue -> {}", notificationRequest);
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, notificationRequest);
    }
}

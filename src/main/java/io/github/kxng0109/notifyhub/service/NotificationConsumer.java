package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.QUEUE_NAME;

@Service
public class NotificationConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EmailService emailService;

    public NotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void handleNotification(NotificationRequest notificationRequest) {
        logger.info("Received notification from queue -> {}", notificationRequest);

        emailService.sendSimpleMessage(
                notificationRequest.to(),
                notificationRequest.subject(),
                notificationRequest.body()
        );
    }
}

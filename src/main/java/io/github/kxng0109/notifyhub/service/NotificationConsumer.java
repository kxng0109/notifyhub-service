package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.QUEUE_NAME;

@Service
public class NotificationConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EmailService emailService;

    @Value("${notifyhub.rabbitmq.maxRetries:3}")
    private int maxRetries;

    public NotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void handleNotification(NotificationRequest notificationRequest, Message message) {
        logger.info("Received notification from queue -> {}", notificationRequest);

        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        List<Map<String, Object>> xDeathHeader = (List<Map<String, Object>>) headers.get("x-death");

        long currentRetryCount = 0;
        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            currentRetryCount = (long) xDeathHeader.getFirst().get("count");
            logger.warn("Retrying notification (Attempt {}): {}",
                        currentRetryCount + 1,
                        notificationRequest
            );
        }

        if (currentRetryCount >= maxRetries) {
            logger.error("Maximum number of retries reached. Discarding notification: {}", notificationRequest);
            return;
        }

        try {
            emailService.sendSimpleMessage(
                    notificationRequest.to(),
                    notificationRequest.subject(),
                    notificationRequest.textBody()
            );
        } catch (MailException e) {
            logger.error("Error sending email to {}: {}", notificationRequest.to(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Email sending failed", e);
        }
    }
}

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
import org.springframework.util.StringUtils;

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
            if (StringUtils.hasText(notificationRequest.htmlBody())) {
                emailService.sendHtmlMessage(
                        notificationRequest.to(),
                        notificationRequest.subject(),
                        notificationRequest.htmlBody()
                );
            } else if (StringUtils.hasText(notificationRequest.textBody())) {
                emailService.sendSimpleMessage(
                        notificationRequest.to(),
                        notificationRequest.subject(),
                        notificationRequest.textBody()
                );
            } else {
                logger.error("Notification request has no body (text or HTML). Discarding: {}", notificationRequest);
                throw new AmqpRejectAndDontRequeueException("Notification request has no body (text or HTML).");
            }
        } catch (MailException e) {
            logger.error("Email sending failed (Attempt {}). Error: {}. Message: {}",
                         currentRetryCount + 1,
                         e.getMessage(),
                         notificationRequest,
                         e
            );
            throw new AmqpRejectAndDontRequeueException("Email sending failed", e);
        }
    }
}

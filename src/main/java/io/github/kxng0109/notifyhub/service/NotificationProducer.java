package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.DELAYED_EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;

/**
 * This service is responsible for producing and publishing notification requests to a message queue.
 * It facilitates asynchronous message delivery using a specified background executor.
 */
@Service
public class NotificationProducer {
    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);
    private final Executor publishExecutor;

    private final RabbitTemplate rabbitTemplate;

    public NotificationProducer(
            RabbitTemplate rabbitTemplate,
            @Qualifier("rabbitmqPublisherExecutor") Executor publishExecutor
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.publishExecutor = publishExecutor;
    }

    /**
     * Publishes a notification request to a message queue for delivery.
     * Processes and sends the notification using a background executor for asynchronous execution.
     *
     * @param notificationRequest the request containing recipient information, subject, body,
     *                            and optional attachments for the notification to be sent
     */
    public void sendNotification(NotificationRequest notificationRequest) {
        logger.info("Received notification request for '{}'", notificationRequest.to());
        publishExecutor.execute(() -> {
            try {
                MessagePostProcessor postProcessor = message -> {
                    message.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
                    return message;
                };

                logger.debug("Publishing notification to queue -> {}", notificationRequest);
                rabbitTemplate.convertAndSend(DELAYED_EXCHANGE_NAME, ROUTING_KEY, notificationRequest, postProcessor);
                logger.info("Successfully published notification for '{}'", notificationRequest.to());
            } catch (Exception e) {
                logger.error("Failed to publish notification: {}", notificationRequest, e);
            }
        });
    }
}

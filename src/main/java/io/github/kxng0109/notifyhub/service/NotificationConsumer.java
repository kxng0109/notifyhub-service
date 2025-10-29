package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.*;
import static io.github.kxng0109.notifyhub.service.NotificationProducer.HEADER_RETRY_COUNT;

@Service
public class NotificationConsumer {
    public static final AtomicInteger counter = new AtomicInteger(0);
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);
    private final EmailService emailService;
    private final RabbitTemplate rabbitTemplate;
    private final Executor publisherExecutor;
    private final Executor emailSenderExecutor;

    @Value("${notifyhub.rabbitmq.maxRetries:3}")
    private int maxRetries;

    @Value("${notifyhub.rabbitmq.backoff.base:5}")
    private int backoffBase;

    @Value("${notifyhub.rabbitmq.backoff.multiplier:5000}")
    private long backoffMultiplier;

    public NotificationConsumer(
            EmailService emailService,
            RabbitTemplate rabbitTemplate,
            @Qualifier("rabbitmqPublisherExecutor") Executor publisherExecutor,
            @Qualifier("emailSendingExecutor") Executor emailSenderExecutor
    ) {
        this.emailService = emailService;
        this.rabbitTemplate = rabbitTemplate;
        this.publisherExecutor = publisherExecutor;
        this.emailSenderExecutor = emailSenderExecutor;
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void handleNotification(NotificationRequest notificationRequest, Message message) {
        long startTime = System.currentTimeMillis();
        int currentCounter = counter.incrementAndGet();
        String consumerThread = Thread.currentThread().getName();

        logger.info("[CONSUMER #{}] [THREAD: {}] Received notification in {}ms -> {}",
                    currentCounter,
                    consumerThread,
                    System.currentTimeMillis() - startTime,
                    notificationRequest.subject()
        );
        if (!StringUtils.hasText(notificationRequest.body()) && !StringUtils.hasText(notificationRequest.htmlBody())) {
            logger.error("[CONSUMER #{}] Discarding notification with no body(text or HTML): {}", currentCounter,
                         notificationRequest
            );
            throw new AmqpRejectAndDontRequeueException("Notification request has no body (text or HTML).");
        }

        int retryCount = (int) message.getMessageProperties()
                                      .getHeaders()
                                      .getOrDefault(
                                              HEADER_RETRY_COUNT, 0
                                      );

        emailSenderExecutor.execute(() -> processEmail(
                notificationRequest, message, retryCount, currentCounter
        ));
    }

    private void processEmail(NotificationRequest notificationRequest, Message message, int retryCount, int counter) {
        long processStart = System.currentTimeMillis();

        try {
            logger.debug("[WORKER #{}] Starting email processing. Attempt {}", counter, retryCount + 1);

            if (StringUtils.hasText(notificationRequest.htmlBody())) {
                emailService.sendHtmlMessage(
                        notificationRequest.to(),
                        notificationRequest.subject(),
                        notificationRequest.htmlBody(),
                        notificationRequest.attachments()
                );
            } else if (StringUtils.hasText(notificationRequest.body())) {
                emailService.sendSimpleMessage(
                        notificationRequest.to(),
                        notificationRequest.subject(),
                        notificationRequest.body()
                );
            }

            long duration = System.currentTimeMillis() - processStart;
            logger.info("[WORKER #{}] Email sent successfully in {}ms.", counter, duration);
        } catch (Exception e) {
            logger.error("[WORKER #{}] Email sending failed (Attempt {}). Error: {}. Message: {}",
                         counter,
                         retryCount + 1,
                         e.getMessage(),
                         notificationRequest,
                         e
            );
            if (retryCount < maxRetries) {
                republishWithDelay(notificationRequest, message, retryCount);
            } else {
                sendToFailureQueue(notificationRequest, e);
            }
            throw new AmqpRejectAndDontRequeueException("Email sending failed", e);
        }
    }

    private long calculateDelay(int retryCount) {
        long delay = (long) (Math.pow(backoffBase, retryCount) * backoffMultiplier);
        logger.debug("Delay of {} seconds has been set to retry", delay);
        return delay;
    }

    private void republishWithDelay(
            NotificationRequest notificationRequest,
            Message message,
            int retryCount
    ) {
        int newRetryCount = retryCount + 1;
        long delay = calculateDelay(retryCount);

        logger.info("Retrying message in {}s. This is attempt {}.", delay / 1000L, newRetryCount);

        publisherExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        DELAYED_EXCHANGE_NAME,
                        message.getMessageProperties().getReceivedRoutingKey(),
                        notificationRequest,
                        msg -> {
                            msg.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, newRetryCount);
                            msg.getMessageProperties().setDelayLong(delay);
                            return msg;
                        }
                );
            } catch (Exception e) {
                logger.error("Failed to republish message", e);
            }
        });
    }

    private void sendToFailureQueue(NotificationRequest notificationRequest, Exception failureReason) {
        logger.error(
                "Max retires of {} exceeded for message. Sending to failure queue: {}.",
                maxRetries,
                notificationRequest,
                failureReason
        );

        publisherExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        FAILURES_EXCHANGE_NAME,
                        "",
                        notificationRequest,
                        msg -> {
                            msg.getMessageProperties().getHeaders().put("x-failure-reason", failureReason.getMessage());
                            return msg;
                        }
                );
            } catch (Exception e) {
                logger.error("Failed to send to failure queue", e);
            }
        });
    }
}

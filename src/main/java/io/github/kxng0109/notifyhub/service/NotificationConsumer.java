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

/**
 * NotificationConsumer is a service responsible for processing notification messages
 * received from RabbitMQ queues. It primarily handles parsing and validating incoming
 * messages, delegates email sending tasks, and manages retries in case of failures.
 */
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

    /**
     * Consumes a notification message from a RabbitMQ queue, logs the processing information,
     * validates the notification content, and delegates the processing to an email sender executor.
     *
     * @param notificationRequest Contains the details of the notification such as subject, body, and HTML body.
     * @param message The original RabbitMQ message containing additional metadata such as headers.
     * @throws AmqpRejectAndDontRequeueException if the notification content is invalid (no body or HTML body).
     */
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

    /**
     * Processes an email request by sending the appropriate email (HTML or plain text)
     * based on the content of the provided notification request. If the email sending
     * fails, retries the operation up to a maximum retry limit or sends the request to a
     * failure queue.
     *
     * @param notificationRequest The notification request containing details such as recipient(s),
     *                             subject, body, and attachments of the email.
     * @param message             The message object corresponding to the request, used for
     *                             acknowledgments or re-queuing purposes.
     * @param retryCount          The current retry attempt count for the email processing operation.
     * @param counter             The worker instance or thread identifier processing the request.
     */
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

    /**
     * Calculates the delay time for a retry attempt based on the retry count,
     * using an exponential backoff algorithm.
     *
     * @param retryCount the number of retry attempts already made
     * @return the calculated delay time in seconds
     */
    private long calculateDelay(int retryCount) {
        long delay = (long) (Math.pow(backoffBase, retryCount) * backoffMultiplier);
        logger.debug("Delay of {} seconds has been set to retry", delay);
        return delay;
    }

    /**
     * Republishes a message to a delayed exchange with a specified delay, incrementing the retry count.
     *
     * @param notificationRequest the notification request to be sent with the message
     * @param message the original message to be republished
     * @param retryCount the current retry count for the message
     */
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

    /**
     * Sends a notification request to the failure queue after the maximum retries have been exceeded.
     * Includes the failure reason in the message header for debugging purposes.
     *
     * @param notificationRequest the notification request object that failed processing
     * @param failureReason the exception that caused the failure
     */
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

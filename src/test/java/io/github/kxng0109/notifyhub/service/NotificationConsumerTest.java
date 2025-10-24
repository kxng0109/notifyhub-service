package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.AttachmentRequest;
import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
public class NotificationConsumerTest {

    private static final int MAX_RETRIES = 2;
    private static final int DLQ_TTL_MS = 100;
    private static final int EXPECTED_TOTAL_ATTEMPTS = MAX_RETRIES + 1;

    @Container
    private static final RabbitMQContainer RABBIT_MQ_CONTAINER = new RabbitMQContainer("rabbitmq:4-management");
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @MockitoSpyBean
    private NotificationConsumer notificationConsumer;
    @MockitoBean
    private EmailService emailService;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT_MQ_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_MQ_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_MQ_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_MQ_CONTAINER::getAdminPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> 1025);
        registry.add("notifyhub.mail.from", () -> "test@notifyhub.com");
        registry.add("notifyhub.rabbitmq.maxRetries", () -> MAX_RETRIES);
        registry.add("notifyhub.rabbitmq.dlq.ttl", () -> DLQ_TTL_MS);
    }

    @BeforeEach
    void resetCounterAndMocks() {
        // Resets invocation counts on spies between tests
        reset(notificationConsumer, emailService);
    }

    @Test
    public void handleNotification_should_callSendSimpleMessage_whenOnlyTextBodyIsPresent() {
        NotificationRequest notificationRequest = new NotificationRequest(
                "test@email.com",
                "This is a test subject",
                "This is a test text body",
                null,
                List.of()
        );

        rabbitTemplate.convertAndSend(
                EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest
        );

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(emailService).sendSimpleMessage(
                            eq(notificationRequest.to()),
                            eq(notificationRequest.subject()),
                            eq(notificationRequest.textBody())
                    );

                    verify(notificationConsumer)
                            .handleNotification(any(NotificationRequest.class), any(Message.class));

                    verify(emailService, never()).sendHtmlMessage(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyList()
                    );
                });
    }

    @Test
    public void handleNotification_should_callSendHtmlMessage_whenHtmlBodyIsPresent() {
        AttachmentRequest attachmentRequest = new AttachmentRequest(
                "File name",
                "text/plain",
                Base64.getEncoder().encodeToString("test".getBytes())
        );

        NotificationRequest notificationRequest = new NotificationRequest(
                "test@email.com",
                "A test subject",
                null,
                "<p>This is a test text body</p>",
                List.of(attachmentRequest)
        );

        ArgumentCaptor<List<AttachmentRequest>> captor = ArgumentCaptor.captor();

        rabbitTemplate.convertAndSend(
                EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest
        );

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(emailService).sendHtmlMessage(
                            eq(notificationRequest.to()),
                            eq(notificationRequest.subject()),
                            eq(notificationRequest.htmlBody()),
                            captor.capture()
                    );

                    verify(notificationConsumer)
                            .handleNotification(any(NotificationRequest.class), any(Message.class));

                    verify(emailService, never()).sendSimpleMessage(
                            anyString(),
                            anyString(),
                            anyString()
                    );
                });

        List<AttachmentRequest> capturedList = captor.getValue();
        assertEquals(1, capturedList.size());
        assertEquals(attachmentRequest, capturedList.getFirst());
        assertEquals(attachmentRequest.data(), capturedList.getFirst().data());
    }

    @Test
    public void handleNotification_should_retryAndSucceed_whenFirstAttemptFails() {
        NotificationRequest notificationRequest = new NotificationRequest(
                "test@email.com",
                "A sample test subject",
                "A sample test body",
                null,
                List.of()
        );

        doThrow(MailSendException.class)
                .doNothing()
                .when(emailService)
                .sendSimpleMessage(anyString(), anyString(), anyString());

        rabbitTemplate.convertAndSend(
                EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest
        );

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(emailService, times(2))
                            .sendSimpleMessage(
                                    eq(notificationRequest.to()),
                                    eq(notificationRequest.subject()),
                                    eq(notificationRequest.textBody())
                            );
                    verify(notificationConsumer, times(2))
                            .handleNotification(any(NotificationRequest.class), any(Message.class));
                });
    }

    @Test
    public void handleNotification_should_discardMessage_whenMaxRetriesExceeded() {
        NotificationRequest notificationRequest = new NotificationRequest(
                "test@email.com",
                "A sample test subject",
                "A sample test body",
                null,
                List.of()
        );

        doThrow(MailSendException.class)
                .when(emailService)
                .sendSimpleMessage(anyString(), anyString(), anyString());

        rabbitTemplate.convertAndSend(
                EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest
        );

        await()
                .atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(notificationConsumer, times(EXPECTED_TOTAL_ATTEMPTS))
                            .handleNotification(any(NotificationRequest.class), any(Message.class));
                    verify(emailService, never())
                            .sendHtmlMessage(anyString(), anyString(), anyString(), anyList());
                    verify(emailService, times(MAX_RETRIES))
                            .sendSimpleMessage(
                                    eq(notificationRequest.to()),
                                    eq(notificationRequest.subject()),
                                    eq(notificationRequest.textBody())
                            );
                });
    }
}

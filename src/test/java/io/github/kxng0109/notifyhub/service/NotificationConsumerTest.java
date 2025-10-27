package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.AttachmentRequest;
import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.DELAYED_EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;
import static io.github.kxng0109.notifyhub.service.NotificationProducer.HEADER_RETRY_COUNT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@DisplayName("NotificationConsumer Integration Tests")
public class NotificationConsumerTest {

    //We can't be waiting for long, so max retries of 2 is perfect.
    private static final int MAX_RETRIES = 2;
    private static final int AWAIT_TIMEOUT_SECONDS = 5;

    @Container
    //A docker image that has the plugin pre-installed
    private static final RabbitMQContainer RABBIT_MQ_CONTAINER = new RabbitMQContainer(
            DockerImageName.parse("heidiks/rabbitmq-delayed-message-exchange:3.13.0-management")
                           .asCompatibleSubstituteFor("rabbitmq")
    );

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
        registry.add("notifyhub.rabbitmq.backoff.base", () -> "2");        // 2 instead of 5
        registry.add("notifyhub.rabbitmq.backoff.multiplier", () -> "100");
    }

    @BeforeEach
    void resetCounterAndMocks() {
        // Resets invocation counts on spies between tests
        reset(notificationConsumer, emailService);
        NotificationConsumer.counter.set(0);
    }

    @Test
    @DisplayName("Should send simple email when only text body is present")
    public void handleNotification_should_callSendSimpleMessage_whenOnlyTextBodyIsPresent() {
        NotificationRequest notificationRequest = new NotificationRequest(
                List.of("test@email.com", "test2@email.com"),
                "This is a test subject",
                "This is a test text body",
                null,
                List.of()
        );

        rabbitTemplate.convertAndSend(
                DELAYED_EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest,
                msg -> {
                    msg.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
                    return msg;
                }
        );

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(emailService).sendSimpleMessage(
                            eq(notificationRequest.to()),
                            eq(notificationRequest.subject()),
                            eq(notificationRequest.body())
                    );

                    verify(notificationConsumer)
                            .handleNotification(any(NotificationRequest.class), any(Message.class));

                    verify(emailService, never()).sendHtmlMessage(
                            anyList(),
                            anyString(),
                            anyString(),
                            anyList()
                    );
                });
    }

    @Test
    @DisplayName("Should send HTML email with attachments when HTML body is present")
    public void handleNotification_should_callSendHtmlMessage_whenHtmlBodyIsPresent() {
        AttachmentRequest attachmentRequest = new AttachmentRequest(
                "File name",
                "text/plain",
                Base64.getEncoder().encodeToString("test".getBytes())
        );

        NotificationRequest notificationRequest = new NotificationRequest(
                List.of("test@email.com", "test2@email.com"),
                "A test subject",
                null,
                "<p>This is a test text body</p>",
                List.of(attachmentRequest)
        );

        ArgumentCaptor<List<AttachmentRequest>> captor = ArgumentCaptor.captor();

        rabbitTemplate.convertAndSend(
                DELAYED_EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest,
                msg -> {
                    msg.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
                    return msg;
                }
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
                            anyList(),
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
    @DisplayName("Should retry and succeed when first attempt fails")
    @Timeout(AWAIT_TIMEOUT_SECONDS)
    public void handleNotification_should_retryAndSucceed_whenFirstAttemptFails() {
        NotificationRequest notificationRequest = new NotificationRequest(
                List.of("test@email.com", "test2@email.com"),
                "A sample test subject",
                "A sample test body",
                null,
                List.of()
        );

        doThrow(MailSendException.class)
                .doNothing()
                .when(emailService)
                .sendSimpleMessage(anyList(), anyString(), anyString());

        rabbitTemplate.convertAndSend(
                DELAYED_EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest,
                msg -> {
                    msg.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
                    return msg;
                }
        );

        await()
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(emailService, times(2))
                            .sendSimpleMessage(
                                    eq(notificationRequest.to()),
                                    eq(notificationRequest.subject()),
                                    eq(notificationRequest.body())
                            );

                    verify(notificationConsumer, times(2))
                            .handleNotification(any(NotificationRequest.class), any(Message.class));

                    verify(emailService, never())
                            .sendHtmlMessage(anyList(), anyString(), anyString(), anyList());
                });
    }

    @Test
    @DisplayName("Should send to failure queue when max retries exceeded")
    @Timeout(AWAIT_TIMEOUT_SECONDS)
    public void handleNotification_should_sendToFailureQueue_whenMaxRetriesExceeded() {
        NotificationRequest notificationRequest = new NotificationRequest(
                List.of("test@email.com", "test2@email.com"),
                "Retry failure",
                "A sample test body",
                null,
                List.of()
        );

        doThrow(MailSendException.class)
                .when(emailService)
                .sendSimpleMessage(anyList(), anyString(), anyString());

        rabbitTemplate.convertAndSend(
                DELAYED_EXCHANGE_NAME,
                ROUTING_KEY,
                notificationRequest,
                msg -> {
                    msg.getMessageProperties().getHeaders().put(HEADER_RETRY_COUNT, 0);
                    return msg;
                }
        );

        await()
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(notificationConsumer, times(MAX_RETRIES + 1))
                            .handleNotification(any(NotificationRequest.class), any(Message.class));

                    verify(emailService, times(MAX_RETRIES + 1))
                            .sendSimpleMessage(
                                    eq(notificationRequest.to()),
                                    eq(notificationRequest.subject()),
                                    eq(notificationRequest.body())
                            );

                    verify(emailService, never())
                            .sendHtmlMessage(anyList(), anyString(), anyString(), anyList());
                });
    }
}

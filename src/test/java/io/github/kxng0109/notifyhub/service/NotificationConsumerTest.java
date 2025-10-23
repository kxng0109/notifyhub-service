package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
public class NotificationConsumerTest {

    @Container
    private static final RabbitMQContainer RABBIT_MQ_CONTAINER = new RabbitMQContainer("rabbitmq:4-management");
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @MockitoSpyBean
    private NotificationConsumer notificationConsumer;
    @MockitoSpyBean
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
    }

    @Test
    public void handleNotification_should_processMessage_whenMessageIsPublishedToQueue() {
        NotificationRequest notificationRequest = new NotificationRequest(
                "test@email.com",
                "This is a test subject",
                "This is a test body"
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
                            eq(notificationRequest.body())
                    );

                    verify(notificationConsumer).handleNotification(notificationRequest);
                });
    }
}

package io.github.kxng0109.notifyhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.DELAYED_EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;
import static io.github.kxng0109.notifyhub.service.NotificationProducer.HEADER_RETRY_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void sendNotification_should_return202Accepted_and_sendToQueueWithRetryHeader_whenRequestIsValid() throws Exception {
        NotificationRequest notificationRequest = new NotificationRequest(
                List.of("example@email.com"),
                "This is a test",
                "This is a body for a test",
                null,
                List.of()
        );

        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        mockMvc.perform(post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(notificationRequest)))
               .andExpect(status().isAccepted());

        verify(rabbitTemplate).convertAndSend(
                eq(DELAYED_EXCHANGE_NAME),
                eq(ROUTING_KEY),
                eq(notificationRequest),
                postProcessorCaptor.capture()
        );

        MessagePostProcessor capturedPostProcessor = postProcessorCaptor.getValue();
        Message testMessage = new Message(new byte[0]);
        capturedPostProcessor.postProcessMessage(testMessage);

        int retryCount = (int) testMessage.getMessageProperties().getHeaders().get(HEADER_RETRY_COUNT);
        assertEquals(0, retryCount);
    }

    @Test
    void sendNotification_should_throw400BadRequest_whenRequestIsInvalid() throws Exception {
        NotificationRequest notificationRequest = new NotificationRequest(null, null, null, null, null);
        mockMvc.perform(post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(notificationRequest)))
               .andExpect(status().isBadRequest());

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationRequest.class));
    }
}

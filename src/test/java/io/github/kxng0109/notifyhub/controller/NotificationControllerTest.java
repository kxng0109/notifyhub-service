package io.github.kxng0109.notifyhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.EXCHANGE_NAME;
import static io.github.kxng0109.notifyhub.config.RabbitMQConfig.ROUTING_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationControllerTest {
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendNotification_should_return202Accepted_whenRequestIsValid() throws Exception {
        NotificationRequest notificationRequest = new NotificationRequest(
                "example@email.com",
                "This is a test",
                "This is a body for a test"
        );

        mockMvc.perform(post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(notificationRequest)))
               .andExpect(status().isAccepted());

        Mockito.verify(rabbitTemplate, Mockito.atLeastOnce())
               .convertAndSend(EXCHANGE_NAME, ROUTING_KEY, notificationRequest);
    }

    @Test
    void sendNotification_should_throw400BadRequest_whenRequestIsInvalid() throws Exception {
        NotificationRequest notificationRequest = new NotificationRequest(null, null, null);
        mockMvc.perform(post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(notificationRequest)))
               .andExpect(status().isBadRequest());

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationRequest.class));
    }
}

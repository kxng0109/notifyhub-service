package io.github.kxng0109.notifyhub.controller;

import io.github.kxng0109.notifyhub.dto.NotificationRequest;
import io.github.kxng0109.notifyhub.service.NotificationProducer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A REST controller for handling notification-related endpoints.
 * This controller exposes an API for sending notifications to recipients
 * by accepting details such as recipient email addresses, subject, body,
 * and optional attachments. The data is forwarded asynchronously by a
 * notification producer for further processing.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationProducer notificationProducer;

    public NotificationController(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    /**
     * Handles the HTTP POST endpoint to send a notification request.
     * The method processes the provided notification details and forwards the
     * request asynchronously using the notification producer.
     *
     * @param notificationRequest the request containing recipient details, subject, body,
     *                            and optional attachments for the notification
     * @return a ResponseEntity containing a message indicating the notification request
     *         has been accepted along with an HTTP status code of ACCEPTED
     */
    @PostMapping
    public ResponseEntity<String> sendNotification(@Valid @RequestBody NotificationRequest notificationRequest) {
        notificationProducer.sendNotification(notificationRequest);
        return new ResponseEntity<>("Notification request accepted.", HttpStatus.ACCEPTED);
    }
}

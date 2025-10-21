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

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationProducer notificationProducer;

    public NotificationController(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    @PostMapping
    public ResponseEntity<String> sendNotification(@Valid @RequestBody NotificationRequest notificationRequest) {
        notificationProducer.sendNotification(notificationRequest);
        return new ResponseEntity<>("Notification request accepted.", HttpStatus.ACCEPTED);
    }
}

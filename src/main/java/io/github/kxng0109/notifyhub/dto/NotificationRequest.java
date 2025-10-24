package io.github.kxng0109.notifyhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record NotificationRequest(
        @NotBlank(message = "'To' cannot be blank")
        @Email(message = "'To' must be a valid email")
        String to,

        @NotBlank(message = "Subject cannot be blank")
        String subject,

        String textBody,

        String htmlBody,

        @Valid
        List<AttachmentRequest> attachments
) {
}

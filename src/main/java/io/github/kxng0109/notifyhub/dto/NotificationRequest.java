package io.github.kxng0109.notifyhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record NotificationRequest(
        @NotEmpty(message = "'To' list cannot be empty")
        List<@NotBlank @Email(message = "Email address must be valid") String> to,

        @NotBlank(message = "Subject cannot be blank")
        String subject,

        String body,

        String htmlBody,

        @Valid
        List<AttachmentRequest> attachments
) {
}

package io.github.kxng0109.notifyhub.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachmentRequest(
        @NotBlank(message = "Attachment filename cannot be blank")
        String filename,

        @NotBlank(message = "Attachment contentType cannot be blank")
        String contentType,

        @NotBlank(message = "Attachment data cannot be blank")
        String data
) {
}

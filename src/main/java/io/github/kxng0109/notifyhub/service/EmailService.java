package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.AttachmentRequest;

import java.util.List;

public interface EmailService {
    void sendSimpleMessage(String to, String subject, String content);

    void sendHtmlMessage(String to, String subject, String htmlContent, List<AttachmentRequest> attachmentRequests);
}

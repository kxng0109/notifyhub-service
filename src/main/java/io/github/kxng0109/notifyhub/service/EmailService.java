package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.AttachmentRequest;

import java.util.List;

/**
 * Interface for email services providing functionality to send plain text and HTML emails.
 * It supports the ability to send emails with or without attachments.
 */
public interface EmailService {
    void sendSimpleMessage(List<String> to, String subject, String content);

    void sendHtmlMessage(List<String> to, String subject, String htmlContent, List<AttachmentRequest> attachmentRequests);
}

package io.github.kxng0109.notifyhub.service;

import io.github.kxng0109.notifyhub.dto.AttachmentRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Implementation of the {@link EmailService} interface for sending email messages.
 * This service supports sending both plain text and HTML emails, with optional attachments
 * for the latter. It uses Spring's {@link JavaMailSender} to handle email dispatching.
 *
 * The class is configured as a Spring service and is excluded from execution in load-test
 * profiles, which is specified using the {@link Profile} annotation.
 */
@Service
@Profile("!load-test")
public class EmailServiceImpl implements EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender emailSender;

    @Value("${notifyhub.mail.from}")
    private String mailFromAddress;

    public EmailServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Sends a plain text email message to the specified recipients with the given subject and content.
     *
     * @param to the list of recipient email addresses to send the message to
     * @param subject the subject of the email
     * @param text the plain text content of the email
     */
    @Override
    public void sendSimpleMessage(List<String> to, String subject, String text) {
        String[] recipientAddresses = to.toArray(new String[0]);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFromAddress);
        message.setTo(mailFromAddress);
        message.setBcc(recipientAddresses);
        message.setSubject(subject);
        message.setText(text);

        emailSender.send(message);
        logger.info("Successfully dispatched plain text email to {} recipients.", to.size());
    }

    /**
     * Sends an HTML email message to the specified recipients with the given subject, content,
     * and optional attachments.
     *
     * @param to the list of recipient email addresses to send the message to
     * @param subject the subject of the email
     * @param htmlContent the HTML content of the email
     * @param attachments the list of attachments to include in the email (can be null or empty)
     */
    @Override
    public void sendHtmlMessage(List<String> to, String subject, String htmlContent, List<AttachmentRequest> attachments) {
        String[] recipientAddresses = to.toArray(new String[0]);

        MimeMessage message = emailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFromAddress);
            helper.setTo(mailFromAddress);
            helper.setBcc(recipientAddresses);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (attachments != null && !attachments.isEmpty()) {
                for (AttachmentRequest attachment : attachments) {
                    String fileName = attachment.filename();
                    String contentType = attachment.contentType();
                    String data = attachment.data();

                    byte[] decodedData = Base64.getDecoder().decode(data);
                    ByteArrayResource dataSource = new ByteArrayResource(decodedData);

                    helper.addAttachment(fileName, dataSource, contentType);
                }
            }

            emailSender.send(message);
            logger.info("Successfully dispatched HTML email to {} recipients.", to.size());
        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to {} recipients", to.size(), e);
            throw new MailSendException("Failed to send HTML email to " + to.size() + " recipients", e);
        }
    }

}

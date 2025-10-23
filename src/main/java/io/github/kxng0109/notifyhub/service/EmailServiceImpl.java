package io.github.kxng0109.notifyhub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender emailSender;

    @Value("${notifyhub.mail.from}")
    private String mailFromAddress;

    public EmailServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void sendSimpleMessage(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        emailSender.send(message);
        logger.info("Successfully dispatched plain text email to {}", to);
    }

    @Override
    public void sendHtmlMessage(String to, String subject, String htmlContent) {
        MimeMessage message = emailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            emailSender.send(message);
            logger.info("Successfully dispatched HTML email to {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to {}", to, e);
            throw new MailSendException("Failed to send HTML email to " + to, e);
        }
    }

}

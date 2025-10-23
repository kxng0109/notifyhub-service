package io.github.kxng0109.notifyhub.service;

public interface EmailService {
    void sendSimpleMessage(String to, String subject, String content);
}

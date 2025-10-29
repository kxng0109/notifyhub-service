package io.github.kxng0109.notifyhub.config;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Configuration class for configuring mail-related beans and properties.
 *
 * This class integrates mail settings specified in {@link MailProperties}
 * and provides a configurable {@link JavaMailSender} bean with additional
 * properties for performance optimization and connectivity.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
    @Bean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProperties.getHost());
        mailSender.setPort(mailProperties.getPort());
        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.putAll(mailProperties.getProperties());

        props.put("mail.smtp.connectionpool", "true");
        props.put("mail.smtp.connectionpool.size", 10);

        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        mailSender.setJavaMailProperties(props);
        return mailSender;
    }
}

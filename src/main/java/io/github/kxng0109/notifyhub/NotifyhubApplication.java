package io.github.kxng0109.notifyhub;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "NotifyHub API",
                description = "An asynchronous microservice for queuing and dispatching notifications (e.g., email) via RabbitMQ.",
                version = "v1.0",
                contact = @Contact(
                        name = "Joshua"
                )
        )
)
@SpringBootApplication
public class NotifyhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyhubApplication.class, args);
    }

}

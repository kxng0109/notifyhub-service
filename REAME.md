# NotifyHub: Asynchronous Notification Service

NotifyHub is a lightweight, asynchronous notification microservice built with Java, Spring Boot, and RabbitMQ. It provides a simple REST API endpoint to accept notification requests (e.g., for email), queues them using RabbitMQ, and processes them in the background.

This project demonstrates professional backend practices including event-driven architecture, robust error handling with Dead-Letter Queues (DLQ), full containerization with Docker Compose, automated testing with Testcontainers, and CI/CD with GitHub Actions.

## Key Features

* Asynchronous Processing: Accepts notification requests instantly (`202 Accepted`) and processes them in the background via RabbitMQ, preventing API callers from waiting for slow operations like email sending.

* RESTful API: A single, clean endpoint (`POST /api/notifications`) for submitting notification requests, validated using Spring Validation.

* Resilient Error Handling: Utilizes RabbitMQ's Dead-Letter Queue (DLQ) mechanism. Messages that fail processing (e.g., due to email sending errors) are automatically routed to a separate queue (`notifications_dlq`) for later inspection or reprocessing, preventing data loss.

* Email Integration: Includes a basic `EmailService` using `spring-boot-starter-mail`. Configured by default for MailHog for easy local development testing (emails are captured, not sent externally).

* Fully Containerized: Uses Docker Compose to define and run the entire application stack (Spring Boot App, RabbitMQ, MailHog) with a single command. Includes a multi-stage `Dockerfile` for an optimized, smaller application image.

* Interactive API Docs: Automatically generates interactive API documentation using SpringDoc OpenAPI (Swagger UI).

* Health Monitoring: Exposes a `/actuator/health` endpoint via Spring Boot Actuator for basic health checks.

* Automated CI: Includes a GitHub Actions workflow that automatically builds the application and runs all tests (including integration tests with Testcontainers) on every push/pull request to the main branch.

## Tech Stack

* Java 25
* Spring Boot 3
* Spring AMQP for RabbitMQ
* Spring Mail Sender
* Spring Boot Actuator
* Spring Validation
* Lombok
* RabbitMQ (via Docker)
* MailHog (via Docker)
* Docker & Docker Compose
* Maven
* JUnit 5, Mockito, Spring Test
* Testcontainers (for integration testing RabbitMQ)
* Awaitility (for asynchronous testing)
* SpringDoc OpenAPI (Swagger)
* GitHub Actions (CI/CD)

## API Usage

### Send a Notification

Submits a notification request to be processed asynchronously.

* ### Endpoint: `POST /api/notifications`

* ### Request Body:

```json
{
  "to": "recipient@example.com",
  "subject": "Your Subject Here",
  "body": "The content of your notification."
}
```

* ### Success Response (202 Accepted):

```
Notification request accepted.
```

* ### Failure Response (400 Bad Request):
If the request body fails validation (e.g., missing fields, invalid email):

```json
{
  "to": "'To' must be a valid email",
  "subject": "Subject cannot be blank"
  // ... other validation errors
}
```

## API Documentation (Swagger UI)

1. Once the application is running (using Docker Compose), interactive API documentation is automatically available via Swagger UI.

Navigate to: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

2. You can explore the `/api/notifications` endpoint, view the expected request schema, and use the "Try it out" feature to send requests directly from your browser. Emails sent will be captured by MailHog.

## Getting Started (Docker Compose - Recommended)

This is the simplest and recommended way to run the entire application stack (App, RabbitMQ, MailHog) locally.

### Prerequisites

* Docker Desktop installed and running.
* Git

### Setup & Run

1. ### Clone the repository:

```
git clone [https://github.com/kxng0109/notifyhub-service.git](https://github.com/kxng0109/notifyhub-service.git)
cd notifyhub-service
```

2. ### Configure Environment Variables:

   * Copy the example environment file:

       * On Linux/macOS: `cp .env.example .env`

       * On Windows (Command Prompt): `copy .env.example .env`

       * On Windows (PowerShell): `Copy-Item .env.example .env`

   * Review the `.env` file. You will need to ensure values are set for the following variables (defaults suitable for local development are provided in `.env.example`):

        * `RABBITMQ_USERNAME`: The username for RabbitMQ (default: `guest`).

        * `RABBITMQ_PASSWORD`: The password for RabbitMQ (default: `guest`).

        * `NOTIFYHUB_MAIL_FROM`: The email address used as the 'From' address by the application (default: [noreply@example.com](mailto:noreply@example.com)). (Note: The `.env` file is listed in `.gitignore` and should never be committed.)

3. ### Run with Docker Compose:

```
docker-compose up --build
```

This command will:

* Build the `notifyhub-app` Docker image using the multi-stage Dockerfile.
* Start the `rabbitmq`, `mailhog`, and `app` containers in the correct order, waiting for RabbitMQ to be healthy before starting the app.
* Create a Docker network (`notifyhub-net`) for them to communicate.

### Access Services:

* NotifyHub API: [http://localhost:8080](http://localhost:8080)
* Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* Application Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) (Should return {"status":"UP"})
* RabbitMQ Management: [http://localhost:15672](http://localhost:15672) (Login: guest / guest or credentials from .env). Check the "Queues" tab; if message processing fails, messages will appear in the notifications_dlq.
* MailHog Web UI: [http://localhost:8025](http://localhost:8025) (View captured emails here)

### Stop Services:

Press `Ctrl + C` in the terminal where `docker-compose up` is running. To remove the containers and network, run `docker-compose down`.

## Running Tests

The project includes a comprehensive test suite using JUnit 5, Mockito, Spring Test, Testcontainers, and Awaitility.

1. Ensure Docker is running (Testcontainers needs it to spin up a temporary RabbitMQ instance for the consumer test).

2. Run the tests using the Maven wrapper:

```
./mvnw test
```

(Note: The tests run completely independently and do not require the docker-compose setup to be running.)

## License

This project is licensed under the MIT License - see the [LICENSE](/LICENSE) file for details.

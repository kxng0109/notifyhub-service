# NotifyHub: A Resilient Asynchronous Notification Microservice

[![Java CI with Maven](https://github.com/kxng0109/notifyhub-service/actions/workflows/ci.yml/badge.svg)](https://github.com/kxng0109/notifyhub-service/actions/workflows/ci.yml)

## 1. What is NotifyHub?

NotifyHub is a standalone, production-grade microservice built with Spring Boot and RabbitMQ. Its single purpose is to act as a central "hub" for sending all your application's notifications (like emails) in a **fast, reliable, and asynchronous** way.

It provides a simple REST API endpoint that your other applications (e.g., a user service, an e-commerce platform) can call. The API accepts the notification request in milliseconds and returns an immediate "202 Accepted" response, while the *actual* slow work of sending the email happens in the background.

This project is a deep dive into building a truly **resilient, observable, and scalable** backend service that is designed to handle the high load and inevitable failures of a real-world production environment.

## 2. The Problem This Solves (The "Why")

Why not just send an email directly from your main application?

**The Problem (Synchronous Code):**
Imagine a user registers on your website. Your `UserController` might look like this:

1. Validate user input.
2. Save the user to the database (fast).
3. **Call an `EmailService` to send a welcome email (SLOW).**
4. Return a "Success" response to the user.

What happens if the email server (like MailHog or Gmail) is slow and takes 10 seconds to respond? The user is stuck staring at a loading spinner for 10 seconds, thinking your application is broken. What if the email server is down completely? The entire registration request fails.

**The Solution (Asynchronous Architecture):**
This project "decouples" the slow work from the fast work. The new flow is:

1. Validate user input.
2. Save the user to the database (fast).
3. **Instantly publish a "send email" job** to the `NotifyHub` API (fast, 20ms).
4. Return a "Success" response to the user (fast).

Meanwhile, in the background, `NotifyHub`'s consumer picks up the job from the RabbitMQ queue and performs the slow, 10-second email sending task. The user is completely unaware of this delay. This is the core of a scalable and professional microservice architecture.

## 3. Core Features & Architecture

This service is engineered to be reliable above all else.

* **Asynchronous Processing:** Built around a RabbitMQ message queue to decouple the API from the email-sending work, ensuring fast API response times.
* **Application-Driven Retries:** This is the core feature. When an email fails to send (e.g., the mail server is down), the service **does not lose the message**. It automatically re-publishes the message to a special **Delayed Message Exchange** with an *exponential backoff delay* (e.g., retry in 5s, then 25s, then 125s).
* **Failure "Parking Lot" (DLQ):** After a configurable number of retries (e.g., 3), the consumer stops trying and safely routes the permanently-failed message to a `failures_queue` (a "parking lot") for manual inspection by an administrator. **No messages are ever lost.**
* **Rich Content Support:** The service is designed for real-world use cases, handling bulk sends to thousands of recipients via `BCC`, rich `HTML` content, and `Base64`-encoded file `attachments`.
* **Full Observability Stack:** The entire application stack is containerized with Docker Compose and includes a complete monitoring solution out-of-the-box (Prometheus + Grafana) to visualize performance and queue depths.
* **Containerized & Portable:** The entire 5-container stack (App, RabbitMQ, MailHog, Prometheus, Grafana) is defined in a single `docker-compose.yml` file for a one-command setup.
* **CI/CD Pipeline:** The project is integrated with a GitHub Actions workflow that automatically builds and runs the full test suite (including Testcontainers) on every push to `main`.
* **Live API Documentation:** Uses SpringDoc OpenAPI to automatically generate a `Swagger UI` page for interactive API testing and documentation.

## 4. Tech Stack

This project uses a modern, professional, and comprehensive set of tools.

| Category          | Technology                          | Purpose                                                                                                      |
| :---------------- | :---------------------------------- | :----------------------------------------------------------------------------------------------------------- |
| **Application**   | Java 25                             | The core programming language.                                                                               |
|                   | Spring Boot 3                       | The main application framework for building the REST API and services.                                       |
|                   | Lombok                              | Reduces boilerplate code (getters, setters, builders).                                                       |
|                   | Spring Validation                   | For robust validation of incoming API requests.                                                              |
|                   | Spring Boot Mail                    | For connecting to and sending email via SMTP.                                                                |
| **Messaging**     | **RabbitMQ**                        | The message broker that enables our asynchronous architecture.                                               |
|                   | Spring AMQP                         | The Spring library for connecting to and interacting with RabbitMQ.                                          |
|                   | `rabbitmq_delayed_message_exchange` | The crucial RabbitMQ plugin that enables our application-driven retry logic.                                 |
| **Monitoring**    | **Spring Boot Actuator**            | Exposes critical application health (`/actuator/health`) and metrics (`/actuator/prometheus`).               |
|                   | **Prometheus**                      | The time-series database that "scrapes" (collects) and stores all the metrics from Actuator and RabbitMQ.    |
|                   | **Grafana**                         | The beautiful dashboard and visualization tool that reads from Prometheus.                                   |
| **Testing**       | **JUnit 5 & Mockito**               | The standard for writing unit and integration tests.                                                         |
|                   | **Testcontainers**                  | A powerful library that starts real Docker containers (like RabbitMQ) *inside* our automated tests.          |
|                   | **Awaitility**                      | A library for gracefully testing asynchronous systems by waiting for a condition to be true.                 |
|                   | **k6** (from Grafana Labs)          | A high-performance load testing tool for stress-testing our API.                                             |
| **Deployment**    | **Docker & Docker Compose**         | Packages and orchestrates our entire 5-container application stack.                                          |
|                   | **MailHog**                         | A local "fake" SMTP server that runs in Docker, catches all outgoing emails, and provides a UI to view them. |
| **CI/CD**         | **GitHub Actions**                  | Automatically builds and runs our full test suite on every push to the repository.                           |
| **Documentation** | **SpringDoc OpenAPI**               | Generates the live, interactive Swagger UI documentation for our API.                                        |

## 5. Getting Started (The One-Command Setup)

This project is fully containerized. The only prerequisites you need to run the entire 5-container stack are **Git** and **Docker Desktop**.

### Step 1: Clone the Repository

```bash
git clone [https://github.com/kxng0109/notifyhub-service.git](https://github.com/kxng0109/notifyhub-service.git)
cd notifyhub-service
```

### Step 2: Configure Your Environment (The .env file)

This project uses a .env file to manage all configuration and secrets. This is a professional pattern to keep sensitive data out of the docker-compose.yml file.

A template is provided. You just need to copy it.

Action: In your terminal, in the project root, run:

```bash
# For Windows (Command Prompt)
copy .env.example .env

# For macOS/Linux (Bash/Zsh)
cp .env.example .env
```

The default values in .env.example are already configured to run the full local stack (RabbitMQ, MailHog) and are perfectly fine for development. You do not need to change anything to get started.

### Step 3: Run the Entire Stack

This single command will:

* Build your Spring Boot application's Docker image from the Dockerfile.
* Start all 5 containers (App, RabbitMQ, MailHog, Prometheus, Grafana).
* Connect them all to a private network.
* Run all health checks in the correct order.

```bash
docker-compose up --build
```

Wait for the logs to settle. The notifyhub-app will patiently wait for RabbitMQ to be healthy before starting. You are ready when you see the Started NotifyhubApplication... log.

### Step 4: Access Your Running Services

Your entire professional microservice stack is now running and accessible from your browser:

| Service            | URL                                                                            | Credentials   | Purpose                        |
| :----------------- | :----------------------------------------------------------------------------- | :------------ | :----------------------------- |
| Your API (Swagger) | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | (None)        | Interact with your API.        |
| RabbitMQ UI        | [http://localhost:15672](http://localhost:15672)                               | guest / guest | See queues & message rates.    |
| MailHog (Email UI) | [http://localhost:8025](http://localhost:8025)                                 | (None)        | See the emails you send.       |
| Prometheus UI      | [http://localhost:9090](http://localhost:9090)                                 | (None)        | See the raw metrics data.      |
| Grafana UI         | [http://localhost:3000](http://localhost:3000)                                 | admin / admin | Visualize your dashboards.     |
| App Health         | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | (None)        | Check the app's health status. |

## 6. Testing the Service

This project is built with a "Test-First" mentality. You can (and should) run the full automated test suite, and you can also run the high-performance load tests.

### Automated Integration Tests (The Professional Way)

The project is configured to run its entire test suite using Testcontainers. This means the tests are fully self-contained: they automatically start their own temporary RabbitMQ container, run the tests, and then shut it down.

Action: Run the full CI-level test suite from your terminal:

```bash
# Make sure the Maven wrapper is executable (first time only)
chmod +x ./mvnw

# Run the tests
./mvnw test
```

You will see the Testcontainers logo as it starts RabbitMQ and runs the full test suite.

### Performance & Load Testing (The Fun Part)

We use k6 to simulate high-throughput scenarios. These commands should be run from a separate terminal while your docker-compose up stack is running.

(Note: These commands are for Windows cmd.exe. For Bash/MINGW64, use type load-test.js | docker run... -v "$PWD:/scripts" ...)

#### Test 1: High-Throughput Load Test (load-test.js)

This test unleashes 20 virtual users with no delay to find the system's true bottleneck. This tests the "High Throughput" use case.

```bash
type load-test.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your Grafana dashboard to see the consumer's "Queue Depth" (notifications_queue) and "Acknowledge Rate."

#### Test 2: Bulk Send & Rich Content Test (load-test-bulk.js)

This test simulates a real-world bulk send job (1,000 recipients) with HTML and attachments. This tests the "Bulk Send" use case (e.g., a newsletter).

```bash
type load-test-bulk.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your MailHog UI ([http://localhost:8025](http://localhost:8025)) to see the rich-content emails being captured.

#### Test 3: DLQ & Failure Test (load-test-dlq.js)

This test sends 10 "poison pill" messages (invalid Base64 data) to prove your error-handling and DLQ mechanism works.

```bash
type load-test-dlq.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your RabbitMQ UI ([http://localhost:15672](http://localhost:15672), Queues tab) to see the message count on notifications_dlq go up to 10.

## 7. Future Enhancements

This service is now a robust foundation. The next logical steps to make it a true enterprise platform would be:

* Multi-Channel Refactor: Architect the system to support new channels like SMS. This would involve adding an sms_queue, an SmsConsumer, and an SmsService (e.g., using Twilio), and modifying the NotificationProducer to route based on a channel field.
* DLQ Consumer: A new consumer could be built to monitor the failures_queue ("parking lot") and send an alert (e.g., to Slack) when a message fails permanently.
* Production Hardening: Replace the local MailHog settings with a production SMTP provider (like SendGrid or AWS SES) and move all secrets to a secure vault.

## License

This project is licensed under the MIT License - see the [LICENSE](/LICENSE) file for details.

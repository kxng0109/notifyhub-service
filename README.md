# NotifyHub: A Resilient Asynchronous Notification Microservice

[![Java CI with Maven](https://github.com/kxng0109/notifyhub-service/actions/workflows/ci.yml/badge.svg)](https://github.com/kxng0109/notifyhub-service/actions/workflows/ci.yml)

## **1. Project Overview**

NotifyHub is a standalone, production-grade microservice built with Spring Boot and RabbitMQ, engineered for **high-throughput, resilient, and fully asynchronous** notification dispatch.

This is not a simple demo. This service is architected to handle real-world production load, simulating the practices of top-tier technology companies. It decouples the API request from the message-sending work, and also decouples the message consumption from the slow network I/O of sending an email. This is achieved using a **multi-layered asynchronous processing model with dedicated, configurable thread pools**.

The API (Producer) can ingest thousands of requests per second, while a separate, auto-scaling pool of Consumers processes the work in the background, handing off the final, slow email-sending task to yet another dedicated thread pool. This ensures no part of the system is blocked by I/O.

It features built-in, application-driven retries with exponential backoff and a "parking lot" queue for permanent failures, ensuring **no messages are ever lost**. The entire 5-container stack (App, RabbitMQ, MailHog, Prometheus, Grafana) is fully orchestrated with Docker Compose for a one-command setup.

## **2. Core Features & Architecture**

This service is engineered for professional-grade performance and resilience.

### **High-Throughput Asynchronous Architecture**

The service is fully asynchronous from end-to-end to maximize throughput and responsiveness:

1. **Async API (Producer):** The `NotificationController` API endpoint immediately hands off the request to a dedicated `rabbitmqPublisherExecutor` thread pool. This pool's only job is to publish to RabbitMQ. This frees the API web thread in milliseconds, allowing it to return `202 Accepted` to the client almost instantly.
2. **Scalable Consumers (Listener):** The `NotificationConsumer` is configured as an **auto-scaling pool** of threads (e.g., 4-10 concurrent consumers) that listen to the `notifications_queue`.
3. **Async I/O (Email Sending):** The consumer's job is also asynchronous. It receives a message, validates it, and immediately hands off the *slow* email-sending task (which involves network I/O) to a separate, dedicated `emailSendingExecutor` thread pool. This frees the consumer thread to immediately grab the next message from the queue, enabling massive consumer throughput.
4. **Async Retries:** Even the retry and failure logic (publishing back to the delayed exchange or to the failure queue) is handled by the `rabbitmqPublisherExecutor` to avoid blocking the consumer.

### **Advanced Resilience & Error Handling**

* **Application-Driven Retries:** When an email fails to send (e.g., the mail server is down), the NotificationConsumer's background worker catches the error, calculates an **exponential backoff delay** (e.g., 5s, 25s, 125s), and re-publishes the message to a special `x-delayed-message` exchange.
* **Failure "Parking Lot" (DLQ):** After a configurable number of retries (`maxRetries`), the consumer stops trying and safely routes the permanently-failed message to a `failures_queue` (a "parking lot") for manual inspection by an administrator. **No messages are ever lost.**
* **Connection Pooling:** The `MailConfig` creates a `JavaMailSender` with a connection pool to efficiently manage and reuse SMTP connections, further enhancing performance.

### **Professional-Grade Features**

* **Rich Content:** Supports bulk sending via `BCC`, rich `HTML` content, and `Base64`-encoded file `attachments`.
* **Full Observability Stack:** The `docker-compose.yml` file launches a pre-configured monitoring stack. **Prometheus** scrapes metrics from both the Spring Boot app (`/actuator/prometheus`) and RabbitMQ. **Grafana** provides a ready-to-use dashboard for visualizing queue depths, message rates, and application health.
* **Tunable Performance:** All key performance metrics—consumer/publisher thread pools, consumer concurrency, prefetch counts, and retry logic—are fully externalized and configurable via environment variables in the `.env` file.
* **Containerized & Portable:** The entire 5-container stack (App, RabbitMQ, MailHog, Prometheus, Grafana) is defined in `docker-compose.yml` for a true one-command setup.
* **CI/CD Pipeline:** Integrated with GitHub Actions to automatically build and run the full integration test suite (using **Testcontainers**) on every push.
* **Live API Documentation:** Uses **SpringDoc OpenAPI** to provide a `Swagger UI` page for interactive API exploration.

## **3. Tech Stack**

This project uses a modern, professional, and comprehensive set of tools.

| Category | Technology | Purpose |
| :---- | :---- | :---- |
| **Application** | Java 25 & Spring Boot 3 | Core application framework. |
|  | **Spring AMQP** | For deep integration with RabbitMQ. |
|  | **`@Async` & `ThreadPoolTaskExecutor`** | **For building the high-throughput, multi-threaded asynchronous workflows.** |
|  | Spring Boot Mail & `JavaMailSender` | For sending emails, with connection pooling. |
|  | Spring Validation | For robust validation of API request DTOs. |
| **Messaging** | **RabbitMQ** (with `rabbitmq_delayed_message_exchange` plugin) | The message broker enabling our async, delayed-retry architecture. |
| **Monitoring** | **Spring Boot Actuator** | Exposes health (`/actuator/health`) & metrics (`/actuator/prometheus`). |
|  | **Prometheus** | Time-series database for collecting metrics from all services. |
|  | **Grafana** | Visualization dashboard for all application and broker metrics. |
| **Testing** | **JUnit 5, Mockito, Awaitility** | For robust asynchronous unit & integration testing. |
|  | **Testcontainers** | **Starts a real RabbitMQ container** *inside* our automated test suite to validate the full messaging flow. |
|  | **k6** (from Grafana Labs) | High-performance load-testing scripts (see `load-test.js`, etc.). |
| **Deployment** | **Docker & Docker Compose** | Packages and orchestrates the entire 5-container application stack. |
|  | **MailHog** | A "fake" SMTP server in Docker to catch and view sent emails. |
| **CI/CD** | **GitHub Actions** | Automatically builds and runs all tests (including Testcontainers) on every push. |
| **Docs** | **SpringDoc OpenAPI (Swagger)** | Generates live, interactive API documentation. |

## **4. Getting Started (The One-Command Setup)**

This project is fully containerized. The only prerequisites you need to run the entire 5-container stack are **Git** and **Docker Desktop**.

### **Step 1: Clone the Repository**

```bash
git clone https://github.com/kxng0109/notifyhub-service.git
cd notifyhub-service
```

### **Step 2: Configure Your Environment (The .env file)**

This project uses a .env file to manage all configuration, performance tuning, and secrets. A template (.env.example) is provided. This is a professional pattern to keep sensitive data out of the docker-compose.yml file.

A template is provided. You just need to copy it.

Action: In your terminal, in the project root, run:

```bash
# For Windows (Command Prompt)
copy .env.example .env

# For macOS/Linux (Bash/Zsh)
cp .env.example .env
```

The default values in `.env.example` are configured for local development (RabbitMQ, MailHog, and sensible performance defaults). You do not need to change anything to get started.

### **Step 3: Run the Entire Stack**

This single command will:

* Build your Spring Boot application's Docker image from the Dockerfile.
* Start all 5 containers (App, RabbitMQ, MailHog, Prometheus, Grafana).
* Connect them all to a private network.
* Run all health checks in the correct order.

```bash
docker-compose up --build
```

Wait for the logs to settle. The `notifyhub-app` will patiently wait for RabbitMQ to be healthy before starting. You are ready when you see the `Started NotifyhubApplication...` log.

### Step 4: Access Your Running Services

Your entire professional microservice stack is now running and accessible from your browser:

| Service            | URL                                                                                | Credentials   | Purpose                        |
| :----------------- |:-----------------------------------------------------------------------------------| :------------ | :----------------------------- |
| **Your API (Swagger)** | [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html)   | (None)        | **Interact with your API.**        |
| **RabbitMQ UI**        | [`http://localhost:15672`](http://localhost:15672)                                 | `guest` / `guest` | **See queues & message rates.**    |
| **MailHog (Email UI)** | [`http://localhost:8025`](http://localhost:8025)                                   | (None)        | **See the emails you send.**       |
| **Prometheus UI**      | [`http://localhost:9090`](http://localhost:9090)                                   | (None)        | **See the raw metrics data.**      |
| **Grafana UI**         | [`http://localhost:3000`](http://localhost:3000)                                   | `admin` / `admin` | **Visualize your dashboards.**     |
| **App Health**         | [`http://localhost:8080/actuator/health`](http://localhost:8080/actuator/health)   | (None)        | **Check the app's health status.** |

## **5, Testing the Service**

This project is built with a "Test-First" mentality. You can (and should) run the full automated test suite, and you can also run the high-performance load tests.

### **Automated Integration Tests**

The project is configured to run its entire test suite using Testcontainers. This means the tests are fully self-contained: they automatically start their own temporary RabbitMQ container, run the tests, and then shut it down.

**Action**: Run the full CI-level test suite from your terminal:

```bash
# Make sure the Maven wrapper is executable (first time only)
chmod +x ./mvnw

# Run the tests
./mvnw test
```

You will see the Testcontainers logo as it starts RabbitMQ and runs the full test suite.

### **Performance & Load Testing**

We use **k6** to simulate high-throughput scenarios. These commands should be run from a **separate terminal** while your `docker-compose up` stack is running.

*(Note: These commands are for Windows `cmd.exe`. For Bash/MINGW64, use `type load-test.js | docker run... \-v "$PWD:/scripts"` ...)*

#### Test 1: High-Throughput Load Test (`load-test.js`)

This test unleashes 20 virtual users with no delay to find the system's true bottleneck. This tests the "High Throughput" use case. (Feel free to comment the `sleep(1)` at the end of the test file for maximum load)

```bash
type load-test.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your Grafana dashboard to see the consumer's "Queue Depth" (`notifications_queue`) and "Acknowledge Rate."

#### Test 2: Bulk Send & Rich Content Test (`load-test-bulk.js`)

This test simulates a real-world bulk send job (1,000 recipients) with HTML and attachments. This tests the "Bulk Send" use case (e.g., a newsletter).

```bash
type load-test-bulk.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your MailHog UI ([`http://localhost:8025`](http://localhost:8025)) to see the rich-content emails being captured.

#### Test 3: DLQ & Failure Test (`load-test-dlq.js`)

This test sends 10 "poison pill" messages (invalid Base64 data) to prove your error-handling and DLQ mechanism works.

```bash
type load-test-dlq.js | docker run --rm -i --network=notifyhub_notifyhub-net grafana/k6:latest run -
```

Watch your RabbitMQ UI ([`http://localhost:15672`](http://localhost:15672), Queues tab) to see the message count on `notifications_dlq` go up to 10 after 3 minutes (due to the retry mechanism).

## **6. Future Enhancements**

This service is now a robust foundation. The next logical steps to make it a true enterprise platform would be:

* **Multi-Channel Refactor**: Architect the system to support new channels like SMS. This would involve adding an `sms_queue`, an `SmsConsumer`, and an `SmsService` (e.g., using Twilio), and modifying the `NotificationProducer` to route based on a channel field.
* **DLQ Consumer**: A new consumer could be built to monitor the `failures_queue` ("parking lot") and send an alert (e.g., to Slack) when a message fails permanently.
* **Production Hardening**: Replace the local MailHog settings with a production SMTP provider (like SendGrid or AWS SES) and move all secrets to a secure vault.

## License

This project is licensed under the MIT License - see the [LICENSE](/LICENSE) file for details.

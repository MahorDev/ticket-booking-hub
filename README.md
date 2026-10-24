# TicketHub — Production-Grade Event Ticketing Microservices

TicketHub is a Java 21 / Spring Boot 3 multi-service platform for movie and event ticket booking with distributed event choreography, real-time seat locks, Razorpay payments, Keycloak OIDC security, and comprehensive observability.

## Microservices Architecture

| Application | Port | Responsibility |
|---|---:|---|
| **API Gateway** | 8080 | Central entry point, Keycloak OIDC/HMAC JWT validation, role mapping, request forwarding |
| **Event Service** | 8081 | Catalog for events, venues, screens, shows, seats, and poster media upload |
| **Booking Service** | 8083 | Seat locking (optimistic/distributed), booking lifecycle, tickets, and Transactional Outbox |
| **Payment Service** | 8084 | Razorpay checkout orders, HMAC-SHA256 webhooks, idempotency, and automated refunds |
| **Notification Service** | 8087 | Kafka event consumers (`booking-confirmed`, `payment-success`), Mailpit/SMTP delivery |
| **Eureka Discovery** | 8761 | Service registration and heartbeats |
| **Config Server** | 8888 | Externalized application properties & Cloud RDS profile |
| **Kafka & Zookeeper** | 9092 / 2181 | Distributed pub/sub message broker |
| **MySQL 8.4** | 3306 | Dedicated database per microservice (`event_db`, `booking_db`, `payment_db`, `notification_db`) |
| **Mailpit** | 1025 / 8025 | Local test SMTP mail server and visual inbox web interface |
| **Jaeger Tracing** | 16686 / 4318 | Distributed tracing visualization and OpenTelemetry collector |
| **Keycloak** *(Auth profile)* | 8085 | OIDC identity provider, OAuth2 realm, roles and token issuance |
| **ELK Stack** *(Observability profile)* | 9200 / 5000 / 5601 | Elasticsearch, Logstash pipeline, and Kibana log search dashboards |
| **Prometheus & Grafana** *(Observability)* | 9090 / 3000 | Metrics scraping and pre-configured dashboards |

---

## Quick Start with Docker Compose

1. **Configure Environment**:
   ```bash
   cp .env.example .env
   ```

2. **Start Core Platform (Databases, Kafka, Core Services, Mailpit, Jaeger)**:
   ```bash
   docker compose up --build -d
   docker compose ps
   ```

3. **Start with Keycloak Authentication Provider**:
   ```bash
   docker compose --profile auth up --build -d
   ```

4. **Start with Full Observability (ELK + Prometheus + Grafana)**:
   ```bash
   docker compose --profile observability up --build -d
   ```

5. **Start Everything**:
   ```bash
   docker compose --profile auth --profile observability up --build -d
   ```

---

## Useful Endpoints & Dashboards

- **API Gateway**: http://localhost:8080
- **Eureka Service Registry**: http://localhost:8761
- **Mailpit Web Mailbox**: http://localhost:8025
- **Jaeger Distributed Tracing**: http://localhost:16686
- **Keycloak Admin Console**: http://localhost:8085 (Admin: `admin` / `admin`)
- **Kibana Dashboards**: http://localhost:5601
- **Prometheus Metrics**: http://localhost:9090
- **Grafana Dashboards**: http://localhost:3000
- **OpenAPI Swagger UIs**:
  - Event Service: http://localhost:8081/swagger-ui.html
  - Booking Service: http://localhost:8083/swagger-ui.html
  - Payment Service: http://localhost:8084/swagger-ui.html
  - Notification Service: http://localhost:8087/swagger-ui.html

---

## Building and Testing

### 1. Run Complete Multi-Module Test Suite
```bash
mvn -B clean test
```
*Executes all 29 unit and integration tests across all 8 modules, including Transactional Outbox, Razorpay signed webhooks, and email delivery.*

### 2. Package All Service Artifacts
```bash
mvn -B clean package -DskipTests
```

### 3. Automated End-to-End Customer Journey
Once containers are running, execute the end-to-end customer journey test:
- **PowerShell**:
  ```powershell
  ./infra/scripts/test-customer-journey.ps1
  ```
- **Bash**:
  ```bash
  ./infra/scripts/test-customer-journey.sh
  ```
*Tests the complete lifecycle: Authentication → Event Creation → Show Scheduling → Seat Reservation → Booking Creation → Payment Order → Razorpay Webhook → Confirmation → Ticket Issuance → Email Notification.*

### 4. Verify Razorpay Webhooks
- **PowerShell**:
  ```powershell
  ./infra/scripts/verify-razorpay-webhooks.ps1
  ```
- **Bash**:
  ```bash
  ./infra/scripts/verify-razorpay-webhooks.sh
  ```

### 5. Verify Traces & Logs
- **PowerShell**:
  ```powershell
  ./infra/scripts/verify-traces-and-logs.ps1
  ```
- **Bash**:
  ```bash
  ./infra/scripts/verify-traces-and-logs.sh
  ```

---

## Production & Cloud Deployment

- **AWS RDS & S3 Setup**: See [`infra/aws/AWS_PRODUCTION_SETUP.md`](infra/aws/AWS_PRODUCTION_SETUP.md) and Terraform configuration in [`infra/aws/rds.tf`](infra/aws/rds.tf).
- **Secrets Manager**: Run `infra/aws/secrets-manager-setup.ps1` to upload production secrets.
- **Rolling Deployment**: Run `infra/scripts/deploy.ps1`.
- **Automated Rollback**: Run `infra/scripts/rollback.ps1`.
- **CI Pipeline**: Automated GitHub Actions workflow located in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

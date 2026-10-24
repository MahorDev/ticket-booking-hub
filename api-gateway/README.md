# TicketHub API Gateway

Spring Boot 3.5.7 + Spring Cloud 2025.0.0 + Java 21.

## Routes discovered through Eureka

| Service | Eureka service name | Gateway path |
|---|---|---|
| Event Service | `event-service` | `/api/events/**`, `/api/venues/**`, `/api/screens/**`, `/api/seats/**`, `/api/shows/**`, `/api/show-seats/**` |
| Booking Service | `booking-service` | `/api/bookings/**`, `/api/booking-seats/**`, `/api/tickets/**` |
| Payment Service | `payment-service` | `/api/payments/**` |
| Notification Service | `notification-service` | `/api/notifications/**` |

Gateway: http://localhost:8080
Eureka: http://localhost:8761

## Important

Every service must register with Eureka using its matching `spring.application.name`:

```yaml
spring:
  application:
    name: event-service # change per service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
```

For the other services use `booking-service`, `payment-service`, and `notification-service` respectively.

The gateway uses `lb://service-name`, so service IP/port is no longer hard-coded in the gateway.

JWT remains at the gateway in this setup. Event, Booking, Payment and Notification services do not need to implement the gateway JWT filter themselves for client-facing authentication. Service-to-service security should be handled separately when required.

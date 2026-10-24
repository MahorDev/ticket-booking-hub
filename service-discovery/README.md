# TicketHub Service Discovery

Eureka Server for TicketHub microservices.

- Port: `8761`
- Dashboard: `http://localhost:8761`
- Spring Boot: `3.5.7`
- Spring Cloud: `2025.0.0`
- Java: `21`

## Services that should register

```text
event-service
booking-service
payment-service
notification-service
api-gateway
```

Each service must have the Eureka Client dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

And registration configuration:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
```

Also make sure each service has the correct `spring.application.name`.

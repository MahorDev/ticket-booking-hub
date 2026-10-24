# TicketHub Event Service

The Event Service is intentionally **JWT/security-free**. Authentication and authorization are handled by the TicketHub API Gateway.

- Port: `8081`
- MySQL: `tickethub_event_db`
- Database schema uses Spring SQL initialization scripts under `src/main/resources/db/migration/` (no Flyway).
- Booking Service can call the read-only show/show-seat APIs directly during local development.
- In production, expose the service only on the internal network and allow traffic through the API Gateway/service network.

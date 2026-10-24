# TicketHub Config Server

The native Config Server exposes shared, non-secret defaults from `src/main/resources/config-repo`.
Secrets remain environment variables and must never be committed to source control.

- Local URL: http://localhost:8888
- Example: http://localhost:8888/application/default
- Clients use `spring.config.import=optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`.

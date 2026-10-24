TicketHub integration notes (Phase 1)

Ports:
- API Gateway: 8080
- Event Service: 8081
- Booking Service: 8083
- Payment Service: 8084
- Notification Service: 8087
- Eureka: 8761
- Config Server: 8888
- Kafka: localhost:29092 from host; kafka:9092 inside Docker
- MySQL: localhost:3306

Booking/payment flow:
1. Create a booking. Booking Service validates the show, snapshots seat prices, and reserves seats under a local DB transaction.
2. Initiate payment explicitly with POST /api/payments after booking creation. Payment Service reads the booking total and currency from Booking Service; it does not trust client amount/currency.
3. Complete Razorpay test checkout in the client.
4. Razorpay sends payment.captured or payment.failed to POST /api/payments/webhook.
5. Payment Service verifies X-Razorpay-Signature using the configured webhook secret, records the provider event idempotently, then confirms the booking or releases reservation locks.
6. Only after Booking Service acknowledges confirmation does Payment Service publish payment-success.
7. Booking Service publishes booking-confirmed. Notification Service consumes both topics, suppresses duplicates using unique event keys, records delivery status and sends email.

Important:
- Configure RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET in .env before using Razorpay.
- Configure MAIL_USERNAME, MAIL_PASSWORD and NOTIFICATION_DEFAULT_RECIPIENT to exercise email delivery.
- The default notification recipient is a local-demo fallback, not customer identity storage. Replace the mock recipient lookup with a verified Booking/User service contract before production.
- JSON logs include correlationId; Prometheus/Grafana and Jaeger are available through Docker Compose.
- Advanced outbox/inbox, DLT/replay, Keycloak/OIDC, S3 and AWS RDS deployment remain explicitly listed as gaps in docs/PHASE1_IMPLEMENTATION_STATUS.md.

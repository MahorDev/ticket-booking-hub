TicketHub integration

Ports:
- Event Service: 8081
- Booking Service: 8083
- Payment Service: 8084
- Notification Service: 8085
- Kafka: localhost:9092

Flow:
1. Client creates booking -> Booking Service locks seats and creates PENDING_PAYMENT.
2. Payment Service receives payment webhook after successful payment.
3. Payment Service synchronously calls Booking Service POST /api/internal/payments/{bookingId}/success.
4. Booking Service confirms booking, makes seat locks permanent, creates ticket, then publishes Kafka topic booking-confirmed.
5. Payment Service publishes Kafka topic payment-success only after Booking Service acknowledgement succeeds.
6. Notification Service consumes both topics and sends two separate emails.

Resilience4j:
- Booking -> Event Service: retry + circuit breaker.
- Payment -> Booking Service: retry + circuit breaker.
- Notification email sending: retry.

Database migration:
- Booking Service uses SQL initialization (no Flyway).
- Payment Service uses Hibernate ddl-auto=update (no Flyway).

Environment variables:
- Notification: MAIL_USERNAME, MAIL_PASSWORD, NOTIFICATION_DEFAULT_RECIPIENT, NOTIFICATION_DEFAULT_NAME, KAFKA_BOOTSTRAP_SERVERS
- Payment: DB_USERNAME, DB_PASSWORD, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET

Important limitation:
The current Event Service ShowResponse supplied to Booking contains movie/event title, date, time and screen, but not venue name. Therefore the booking email cannot provide a real venue until Event Service exposes venue information. The current event uses a clear placeholder for venue.

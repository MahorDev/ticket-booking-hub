# TicketHub Booking Service

A complete Booking/Reservation Service for the TicketHub project.

## Responsibility

Booking Service owns:

- seat reservation/temporary locks
- booking lifecycle
- booking items
- booking history
- booking expiration
- cancellation state
- ticket generation
- payment-result coordination endpoint

Event Service owns the master data:

- Event/Movie/Show
- Venue
- Screen
- physical Seat
- show-seat pricing/layout

Booking Service stores only `showId` and `seatId` references; it does not create Seat/Venue/Screen entities.

## Services/ports

| Service | Port |
|---|---:|
| Event Service | 8081 |
| Booking Service | 8083 |
| MySQL | 3306 |

## Database

MySQL:

```sql
CREATE DATABASE tickethub_booking_db;
```

Configured credentials:

```text
username: root
password: admin
database: tickethub_booking_db
```

Flyway creates all tables automatically.

## Project structure

```text
booking-service
├── controller
│   ├── BookingController
│   ├── SeatController
│   ├── TicketController
│   └── InternalPaymentController
├── service
│   ├── BookingService
│   ├── SeatLockService
│   ├── TicketService
│   └── PaymentResultService
├── service/impl
├── repository
├── entity
│   ├── Booking
│   ├── BookingItem
│   ├── SeatLock
│   └── Ticket
├── client
│   └── EventServiceClient
├── dto
├── exception
├── scheduler
└── resources/db/migration
```

## Important design

### Seat locking belongs to Booking Service

```text
Event Service
    |
    | show + seat master data
    v
Booking Service
    |
    +--> SeatLock table
    +--> Booking table
    +--> BookingItem table
    +--> Ticket table
```

The Event Service is never asked to lock a seat.

### Concurrency

Seat locking uses a MySQL unique constraint:

```text
(show_id, seat_id)
```

and an atomic insert:

```sql
INSERT ... ON DUPLICATE KEY UPDATE id = id
```

Only one concurrent request can acquire a seat.

### Five-minute lock

```text
LOCKED
  |
  | 5 minutes
  v
EXPIRED
```

A scheduled job checks every 10 seconds and releases expired booking locks.

## Endpoints

### BookingController

```text
POST /api/bookings
GET  /api/bookings/{bookingId}
GET  /api/bookings/my-bookings?userId=1001
GET  /api/bookings/{bookingId}/status
POST /api/bookings/{bookingId}/cancel?userId=1001
```

### SeatController

```text
GET  /api/shows/{showId}/seats
POST /api/shows/{showId}/seats/lock
POST /api/shows/{showId}/seats/release?seatIds=101&seatIds=102
```

### TicketController

```text
GET /api/tickets/{ticketId}
GET /api/bookings/{bookingId}/ticket
GET /api/tickets/{ticketId}/qr
```

### Transitional payment-result endpoint

These are internal endpoints. They are intentionally simple until the separate Payment Service/Kafka integration is added:

```text
POST /api/internal/payments/{bookingId}/success
POST /api/internal/payments/{bookingId}/failure
```

A successful payment changes:

```text
PENDING -> CONFIRMED
```

and generates a ticket.

A failed payment changes:

```text
PENDING -> FAILED
```

and releases the seat locks.

## Create booking

Example:

```http
POST /api/bookings
Idempotency-Key: 2026-BOOKING-001
Content-Type: application/json
```

```json
{
  "userId": 1001,
  "showId": 501,
  "seatIds": [101, 102]
}
```

Flow:

```text
Validate request
      ↓
Validate show through Event Service
      ↓
Validate selected seats through Event Service
      ↓
Calculate price from Event Service
      ↓
Create PENDING booking
      ↓
Acquire/attach seat locks atomically
      ↓
Create BookingItems
      ↓
Return PENDING booking
```

## Important rules

- Maximum 6 seats per booking.
- Duplicate seat IDs are rejected.
- Client does not provide total amount.
- Price is taken from Event Service.
- Show must be `OPEN`.
- Event Service failures are not converted into `AVAILABLE`.
- Idempotency-Key is required.
- Repeating the same idempotency key returns the existing booking.
- A seat cannot have two active locks.
- Locks expire after 5 minutes.
- Pending booking expiration releases its locks.
- Confirmed booking gets a ticket and its reservation locks are converted to non-expiring reservation state.
- Payment failure releases its locks.
- Confirmed cancellation moves to `REFUND_PENDING`; the actual refund belongs to Payment Service.

## Current authentication note

JWT/Spring Security is intentionally not included yet because the current TicketHub integration does not have the Auth/User service connected.

For now `userId` is supplied by the request.

When JWT is introduced, replace:

```text
?userId=1001
```

and request-body `userId` with the authenticated principal.

## Build

```bash
mvn clean test
mvn spring-boot:run
```

## Docker

Build:

```bash
mvn clean package
docker build -t tickethub-booking-service .
```

Start MySQL:

```bash
docker compose up -d
```

Then run the application.

## What is deliberately not inside this service

These are separate services/integrations:

- actual payment provider
- refund processing
- Kafka broker/event delivery
- Notification Service
- API Gateway
- JWT/Auth Service
- Redis

The internal payment-result endpoints are a transitional integration point and can later be replaced by PaymentSucceeded/PaymentFailed Kafka events.


## Standard API response structure

All controllers use the same professional response pattern:

```java
ResponseEntity<Response<BookingResponse>>
```

The responsibilities are separated:

- `BookingResponse` = domain-specific response DTO for a booking.
- `Response<T>` = common API envelope for any payload type.
- `ResponseEntity<Response<T>>` = HTTP response wrapper, including the HTTP status code.

Examples:

```java
ResponseEntity<Response<BookingResponse>>
ResponseEntity<Response<List<BookingResponse>>>
ResponseEntity<Response<SeatLockResponse>>
ResponseEntity<Response<List<SeatAvailabilityResponse>>>
ResponseEntity<Response<TicketResponse>>
ResponseEntity<Response<Void>>
```

Example JSON:

```json
{
  "error": false,
  "message": "Booking created successfully",
  "data": {
    "bookingId": 101,
    "bookingReference": "BK-20261001-8F3A21",
    "userId": 1001,
    "showId": 501,
    "status": "PENDING",
    "totalAmount": 1000.00,
    "currency": "INR"
  }
}
```

For an HTTP error, the same `Response<T>` envelope is used while `ResponseEntity` carries the appropriate status code, for example `400`, `404`, `409`, or `503`.


## Mapper layer

The service uses a dedicated Mapper layer so entity-to-response conversion does not live inside service implementations.

```text
mapper/
├── BookingMapper
├── BookingItemMapper
├── SeatLockMapper
└── TicketMapper
```

Examples:

```java
BookingResponse response = bookingMapper.toResponse(booking);

TicketResponse response = ticketMapper.toResponse(ticket);

SeatAvailabilityResponse response =
        seatLockMapper.toAvailabilityResponse(
                showSeat,
                status,
                lockedBy,
                lockExpiresAt
        );
```

This keeps the layers separated:

```text
Controller
    ↓
Service
    ↓
Repository / Event Service
    ↓
Entity
    ↓
Mapper
    ↓
Response DTO
    ↓
Response<T>
    ↓
ResponseEntity<Response<T>>
```


## Final corrections


- Booking lifecycle uses `PENDING_PAYMENT` and `PAYMENT_FAILED`.
- Confirmed booking cancellation is rejected in Phase 1.
- Lock duration and maximum seats are configuration-driven through `BookingProperties`.
- Seat availability avoids the previous per-seat booking lookup pattern by batching active locks and bookings.
- Added a service-level concurrent acquisition test for the atomic seat-lock contract.

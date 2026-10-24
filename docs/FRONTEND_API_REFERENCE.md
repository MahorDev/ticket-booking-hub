# TicketHub — Complete React Frontend API Reference (cURL, Payloads & Responses)

Base URL (via API Gateway): `http://localhost:8080`
CORS is pre-configured on `api-gateway` (`allowedOriginPatterns: "*"`, `OPTIONS` preflight enabled) so a React app on `http://localhost:3000` or `http://localhost:5173` can call `http://localhost:8080` directly.

---

## 1. Authentication & User Service (`user-service` & `api-gateway`)

### 1.1. Register New User (Email, Password, Phone — Auto-Generated `userId`)
* **Purpose:** Register a new customer (or staff user) with email, password, phone, and full name. Auto-generates `userId` and immediately returns a signed JWT token.
* **Endpoint:** `POST /api/users/register` (Alias: `POST /auth/register`)
* **Auth Required:** None (Public)

```bash
curl -X POST "http://localhost:8080/api/users/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jaikishan@gmail.com",
    "password": "Password123!",
    "phone": "+919876543210",
    "fullName": "Jaikishan",
    "role": "CUSTOMER"
  }'
```

**Response (`201 Created`):**
```json
{
  "userId": 1,
  "email": "jaikishan@gmail.com",
  "phone": "+919876543210",
  "fullName": "Jaikishan",
  "role": "CUSTOMER",
  "authProvider": "LOCAL",
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

---

### 1.2. Login Registered User (Email & Password)
* **Purpose:** Authenticate a user registered in `user-service` using email and password.
* **Endpoint:** `POST /api/users/login` (Alias: `POST /auth/user-login`)
* **Auth Required:** None (Public)

```bash
curl -X POST "http://localhost:8080/api/users/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jaikishan@gmail.com",
    "password": "Password123!"
  }'
```

**Response (`200 OK`):**
```json
{
  "userId": 1,
  "email": "jaikishan@gmail.com",
  "phone": "+919876543210",
  "fullName": "Jaikishan",
  "role": "CUSTOMER",
  "authProvider": "LOCAL",
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

---

### 1.3. Google Sign-Up / Sign-In (OAuth2 / Google Identity Services)
* **Purpose:** Sign up or sign in a user with Google. Accepts a Google `idToken` (from `@react-oauth/google` `<GoogleLogin />`), an OAuth2 authorization `code`, or Google profile fields (`email`, `fullName`, `phone`, `googleSub`). Auto-creates the user (with auto-generated `userId`) if new, or links to an existing account with the same Gmail address.
* **Endpoint:** `POST /api/users/auth/google` (Alias: `POST /auth/google`)
* **Auth Required:** None (Public)

```bash
curl -X POST "http://localhost:8080/api/users/auth/google" \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
    "email": "jaikishan@gmail.com",
    "fullName": "Jaikishan",
    "phone": "+919876543210",
    "googleSub": "109876543210987654321"
  }'
```

**Response (`200 OK`):**
```json
{
  "userId": 1,
  "email": "jaikishan@gmail.com",
  "phone": "+919876543210",
  "fullName": "Jaikishan",
  "role": "CUSTOMER",
  "authProvider": "GOOGLE",
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

---

### 1.4. Get Google OAuth2 Authorization URL
* **Purpose:** Fetch the Google OAuth2 redirect URL if using redirect-based OAuth flow in React.
* **Endpoint:** `GET /api/users/auth/google/url` (Alias: `GET /auth/google/url`)
* **Auth Required:** None (Public)

```bash
curl -X GET "http://localhost:8080/api/users/auth/google/url"
```

**Response (`200 OK`):**
```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fgoogle%2Fcallback&response_type=code&scope=openid%20email%20profile",
  "clientId": "",
  "redirectUri": "http://localhost:8080/auth/google/callback"
}
```

---

### 1.5. Built-In Demo / Admin Gateway Login
* **Purpose:** Log in with built-in system accounts (`admin` / `admin123`, `eventmanager` / `manager123`, `customer` / `customer123`).
* **Endpoint:** `POST /auth/login`
* **Auth Required:** None (Public)

```bash
curl -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

**Response (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "role": "ADMIN",
  "userId": 1,
  "email": "admin@tickethub.com"
}
```

---

### 1.6. Get Current Logged-In User Profile (`/me`)
* **Purpose:** Fetch the logged-in user's profile on React app startup.
* **Endpoint:** `GET /api/users/me`
* **Auth Required:** `Bearer <TOKEN>` (`CUSTOMER`, `EVENT_MANAGER`, or `ADMIN`)

```bash
curl -X GET "http://localhost:8080/api/users/me" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response (`200 OK`):**
```json
{
  "userId": 1,
  "email": "jaikishan@gmail.com",
  "phone": "+919876543210",
  "fullName": "Jaikishan",
  "role": "CUSTOMER",
  "authProvider": "LOCAL",
  "createdAt": "2026-10-13T00:10:00"
}
```

---

### 1.7. Get User by ID & Update User Profile
* **Endpoint (Get):** `GET /api/users/{userId}`
* **Endpoint (Update):** `PUT /api/users/{userId}`
* **Auth Required:** `Bearer <TOKEN>` (Owner or `ADMIN`)

```bash
curl -X PUT "http://localhost:8080/api/users/1" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Jaikishan Updated",
    "phone": "+919999888877",
    "password": "NewPassword123!"
  }'
```

**Response (`200 OK`):**
```json
{
  "userId": 1,
  "email": "jaikishan@gmail.com",
  "phone": "+919999888877",
  "fullName": "Jaikishan Updated",
  "role": "CUSTOMER",
  "authProvider": "LOCAL",
  "createdAt": "2026-10-13T00:10:00"
}
```

---

## 2. Event, Venue, Screen, Seat & Show Catalogue (`event-service`)

> **Role Permissions:**
> - `GET` endpoints: Accessible to all authenticated users (`CUSTOMER`, `EVENT_MANAGER`, `ADMIN`). Note: Customers only see `PUBLISHED` events; Admins/Event Managers see `DRAFT`, `PUBLISHED`, and `CANCELLED`.
> - `POST`, `PUT`, `PATCH`, `DELETE`: Require `ADMIN` or `EVENT_MANAGER` token.

### 2.1. Venues (`/api/venues`)

#### Create Venue (`ADMIN` / `EVENT_MANAGER`)
```bash
curl -X POST "http://localhost:8080/api/venues" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "IMAX Grand Bangalore",
    "address": "Koramangala 5th Block",
    "city": "Bangalore",
    "state": "Karnataka",
    "country": "India"
  }'
```

**Response (`201 Created`):**
```json
{
  "venueId": 1,
  "name": "IMAX Grand Bangalore",
  "address": "Koramangala 5th Block",
  "city": "Bangalore",
  "state": "Karnataka",
  "country": "India",
  "createdAt": "2026-10-13T00:15:00"
}
```

#### List All Venues (Paginated)
```bash
curl -X GET "http://localhost:8080/api/venues?page=0&size=20&sort=venueId,asc" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response (`200 OK`):**
```json
{
  "content": [
    {
      "venueId": 1,
      "name": "IMAX Grand Bangalore",
      "address": "Koramangala 5th Block",
      "city": "Bangalore",
      "state": "Karnataka",
      "country": "India",
      "createdAt": "2026-10-13T00:15:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

#### Other Venue Endpoints
* `GET /api/venues/{venueId}` — Get single venue
* `GET /api/venues/{venueId}/screens?page=0&size=20` — List screens for a venue
* `PUT /api/venues/{venueId}` — Update venue (same body as `POST`)
* `DELETE /api/venues/{venueId}` — Delete venue (`204 No Content`)

---

### 2.2. Screens (`/api/screens`)

#### Create Screen (`ADMIN` / `EVENT_MANAGER`)
```bash
curl -X POST "http://localhost:8080/api/screens" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "screenName": "Audi 1 - Laser IMAX",
    "venueId": 1
  }'
```

**Response (`201 Created`):**
```json
{
  "screenId": 1,
  "screenName": "Audi 1 - Laser IMAX",
  "totalSeats": 0,
  "venueId": 1,
  "venueName": "IMAX Grand Bangalore"
}
```

#### Other Screen Endpoints
* `GET /api/screens?page=0&size=20` — List all screens
* `GET /api/screens/{screenId}` — Get screen details
* `GET /api/screens/{screenId}/seats?page=0&size=100` — List seats on a screen
* `GET /api/screens/{screenId}/shows?page=0&size=20` — List shows on a screen
* `PUT /api/screens/{screenId}` — Update screen
* `DELETE /api/screens/{screenId}` — Delete screen (`204 No Content`)

---

### 2.3. Seats (`/api/seats`)

> **Enums:**
> - `seatType`: `REGULAR` | `PREMIUM` | `VIP` | `RECLINER`
> - `section`: `LOWER` | `MIDDLE` | `UPPER`

#### Create Seat on a Screen (`ADMIN` / `EVENT_MANAGER`)
```bash
curl -X POST "http://localhost:8080/api/seats" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "screenId": 1,
    "rowName": "A",
    "seatNumber": "A1",
    "seatType": "PREMIUM",
    "section": "MIDDLE",
    "basePrice": 350.00
  }'
```

**Response (`201 Created`):**
```json
{
  "seatId": 101,
  "seatNumber": "A1",
  "rowName": "A",
  "seatType": "PREMIUM",
  "section": "MIDDLE",
  "basePrice": 350.00,
  "screenId": 1,
  "screenName": "Audi 1 - Laser IMAX"
}
```

#### Other Seat Endpoints
* `GET /api/seats?page=0&size=100` — List all seats
* `GET /api/seats/{seatId}` — Get seat by ID
* `PUT /api/seats/{seatId}` — Update seat
* `DELETE /api/seats/{seatId}` — Delete seat (`204 No Content`)

---

### 2.4. Events & Media Upload (`/api/events` & `/api/media`)

> **Enums:**
> - `eventType`: `MOVIE` | `CONCERT` | `SPORTS` | `THEATRE` | `COMEDY` | `OTHER`
> - `status`: `DRAFT` | `PUBLISHED` | `CANCELLED`

#### Create Event (`ADMIN` / `EVENT_MANAGER`)
```bash
curl -X POST "http://localhost:8080/api/events" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Oppenheimer 70mm IMAX",
    "description": "Christopher Nolan biographical thriller",
    "eventType": "MOVIE",
    "language": "English",
    "duration": 180,
    "rating": "UA",
    "genre": "Drama",
    "posterUrl": "/api/media/posters/oppenheimer.jpg"
  }'
```

**Response (`201 Created`):**
```json
{
  "eventId": 10,
  "title": "Oppenheimer 70mm IMAX",
  "description": "Christopher Nolan biographical thriller",
  "eventType": "MOVIE",
  "language": "English",
  "duration": 180,
  "rating": "UA",
  "genre": "Drama",
  "posterUrl": "/api/media/posters/oppenheimer.jpg",
  "status": "DRAFT",
  "createdAt": "2026-10-13T00:20:00",
  "updatedAt": "2026-10-13T00:20:00"
}
```

#### Publish or Cancel Event (`PATCH /api/events/{eventId}/status`)
```bash
curl -X PATCH "http://localhost:8080/api/events/10/status" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PUBLISHED"
  }'
```

**Response (`200 OK`):**
```json
{
  "eventId": 10,
  "title": "Oppenheimer 70mm IMAX",
  "status": "PUBLISHED",
  "updatedAt": "2026-10-13T00:21:00"
}
```

#### Search & Browse Events (`GET /api/events`)
* **Query Params (all optional):** `title`, `genre`, `language`, `eventType`, `status`, `page`, `size`, `sort`

```bash
curl -X GET "http://localhost:8080/api/events?title=Oppenheimer&eventType=MOVIE&page=0&size=12" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response (`200 OK`):**
```json
{
  "content": [
    {
      "eventId": 10,
      "title": "Oppenheimer 70mm IMAX",
      "description": "Christopher Nolan biographical thriller",
      "eventType": "MOVIE",
      "language": "English",
      "duration": 180,
      "rating": "UA",
      "genre": "Drama",
      "posterUrl": "/api/media/posters/oppenheimer.jpg",
      "status": "PUBLISHED",
      "createdAt": "2026-10-13T00:20:00",
      "updatedAt": "2026-10-13T00:21:00"
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

#### Upload Event Poster (`POST /api/events/{eventId}/poster`)
```bash
curl -X POST "http://localhost:8080/api/events/10/poster" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -F "file=@./poster.jpg"
```

**Response (`200 OK`):**
```json
{
  "eventId": 10,
  "posterUrl": "/api/media/posters/d9b2d63d-a233-4123-847a-poster.jpg"
}
```

#### Fetch Uploaded Poster / Media (`GET /api/media/{folder}/{filename}`)
* **Auth Required:** None (Public `GET` so `<img src="http://localhost:8080/api/media/posters/..." />` works directly in React!)

```bash
curl -X GET "http://localhost:8080/api/media/posters/d9b2d63d-a233-4123-847a-poster.jpg" --output poster.jpg
```

---

### 2.5. Shows (`/api/shows`)

> **Enums:**
> - `status`: `SCHEDULED` | `OPEN` | `SOLD_OUT` | `CANCELLED` | `COMPLETED`
> - Creating a show automatically populates `ShowSeat` records for every seat on the screen.

#### Create Show (`ADMIN` / `EVENT_MANAGER`)
```bash
curl -X POST "http://localhost:8080/api/shows" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": 10,
    "screenId": 1,
    "showDate": "2026-10-20",
    "startTime": "18:00:00",
    "endTime": "21:30:00",
    "ticketPrice": 300.00,
    "currency": "INR"
  }'
```

**Response (`201 Created`):**
```json
{
  "showId": 50,
  "showDate": "2026-10-20",
  "startTime": "18:00:00",
  "endTime": "21:30:00",
  "status": "SCHEDULED",
  "ticketPrice": 300.00,
  "currency": "INR",
  "eventId": 10,
  "eventTitle": "Oppenheimer 70mm IMAX",
  "eventStatus": "PUBLISHED",
  "screenId": 1,
  "screenName": "Audi 1 - Laser IMAX",
  "venueId": 1,
  "venueName": "IMAX Grand Bangalore",
  "venueAddress": "Koramangala 5th Block, Bangalore",
  "createdAt": "2026-10-13T00:25:00"
}
```

#### Open Show for Customer Bookings (`PATCH /api/shows/{showId}/status`)
```bash
curl -X PATCH "http://localhost:8080/api/shows/50/status" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "OPEN"
  }'
```

**Response (`200 OK`):**
```json
{
  "showId": 50,
  "status": "OPEN",
  "eventTitle": "Oppenheimer 70mm IMAX",
  "venueName": "IMAX Grand Bangalore"
}
```

#### List Shows for an Event (`GET /api/events/{eventId}/shows`)
```bash
curl -X GET "http://localhost:8080/api/events/10/shows?page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## 3. Seat Map Availability, Locking, Booking & Tickets (`booking-service`)

> **Important Note on Response Wrapper:**
> All endpoints in `booking-service` return a standard JSON envelope:
> `{ "success": true, "message": "...", "data": ... }`

### 3.1. Get Live Seat Map & Availability for a Show
* **Purpose:** Renders the interactive seat map in React. Merges seat metadata from `event-service` with live seat locks and confirmed bookings from `booking-service`.
* **Endpoint:** `GET /api/shows/{showId}/seats`
* **Auth Required:** `Bearer <TOKEN>`

```bash
curl -X GET "http://localhost:8080/api/shows/50/seats" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Seat availability fetched",
  "data": [
    {
      "seatId": 101,
      "seatNumber": "A1",
      "rowName": "A",
      "seatType": "PREMIUM",
      "categoryId": null,
      "categoryName": "MIDDLE",
      "price": 350.00,
      "currency": "INR",
      "status": "AVAILABLE",
      "lockedUntil": null
    },
    {
      "seatId": 102,
      "seatNumber": "A2",
      "rowName": "A",
      "seatType": "PREMIUM",
      "categoryId": null,
      "categoryName": "MIDDLE",
      "price": 350.00,
      "currency": "INR",
      "status": "LOCKED",
      "lockedUntil": "2026-10-13T00:35:00"
    }
  ]
}
```

---

### 3.2. Temporarily Lock Selected Seats (5-Minute Hold Before Checkout)
* **Purpose:** Optional pre-lock when a user selects seats on the seat map before clicking "Proceed to Checkout".
* **Endpoint:** `POST /api/shows/{showId}/seats/lock`
* **Auth Required:** `Bearer <CUSTOMER_TOKEN>`

```bash
curl -X POST "http://localhost:8080/api/shows/50/seats/lock" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "seatIds": [101, 102]
  }'
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Seats locked",
  "data": {
    "showId": 50,
    "userId": 1,
    "seatIds": [101, 102],
    "status": "LOCKED",
    "expiresAt": "2026-10-13T00:35:00"
  }
}
```

---

### 3.3. Release Unbooked Seat Locks
* **Purpose:** Release temporarily locked seats if the user unselects them or leaves the seat selection screen before creating a booking.
* **Endpoint:** `POST /api/shows/{showId}/seats/release?seatIds=101,102`
* **Auth Required:** `Bearer <CUSTOMER_TOKEN>`

```bash
curl -X POST "http://localhost:8080/api/shows/50/seats/release?seatIds=101,102" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1
  }'
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Seats released",
  "data": null
}
```

---

### 3.4. Create Booking (`PENDING_PAYMENT`)
* **Purpose:** Creates a `PENDING_PAYMENT` booking, locks the seats (or attaches existing locks), calculates trusted server-side pricing, and records the customer's Gmail address (`customerEmail`) and name (`customerName`) for notifications.
* **Endpoint:** `POST /api/bookings`
* **Required Header:** `Idempotency-Key: <unique-uuid>` (prevents duplicate bookings on network retry)
* **Auth Required:** `Bearer <CUSTOMER_TOKEN>`

```bash
curl -X POST "http://localhost:8080/api/bookings" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  -H "Idempotency-Key: bk-550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "showId": 50,
    "seatIds": [101, 102],
    "customerEmail": "jaikishan@gmail.com",
    "customerName": "Jaikishan"
  }'
```

**Response (`201 Created`):**
```json
{
  "success": true,
  "message": "Booking created",
  "data": {
    "bookingId": 1001,
    "userId": 1,
    "customerEmail": "jaikishan@gmail.com",
    "customerName": "Jaikishan",
    "showId": 50,
    "status": "PENDING_PAYMENT",
    "totalAmount": 700.00,
    "currency": "INR",
    "idempotencyKey": "bk-550e8400-e29b-41d4-a716-446655440000",
    "paymentReference": null,
    "expiresAt": "2026-10-13T00:35:00",
    "createdAt": "2026-10-13T00:30:00",
    "updatedAt": "2026-10-13T00:30:00",
    "items": [
      {
        "seatId": 101,
        "seatNumber": "A1",
        "seatType": "PREMIUM",
        "price": 350.00
      },
      {
        "seatId": 102,
        "seatNumber": "A2",
        "seatType": "PREMIUM",
        "price": 350.00
      }
    ]
  }
}
```

---

### 3.5. Get Booking Details, Booking Status, My Bookings & Cancel Booking

#### Get Booking by ID (`GET /api/bookings/{bookingId}`)
```bash
curl -X GET "http://localhost:8080/api/bookings/1001" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

#### Poll Booking Status (`GET /api/bookings/{bookingId}/status`)
```bash
curl -X GET "http://localhost:8080/api/bookings/1001/status" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```
**Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Booking status fetched",
  "data": {
    "bookingId": 1001,
    "status": "CONFIRMED",
    "paymentReference": "pay_Ph9x87654321",
    "expiresAt": "2026-10-13T00:35:00",
    "updatedAt": "2026-10-13T00:31:15"
  }
}
```

#### List Logged-In Customer's Bookings (`GET /api/bookings/my-bookings`)
```bash
curl -X GET "http://localhost:8080/api/bookings/my-bookings?userId=1" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

#### Cancel Booking (`POST /api/bookings/{bookingId}/cancel`)
```bash
curl -X POST "http://localhost:8080/api/bookings/1001/cancel?userId=1" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

---

### 3.6. Tickets & QR Code (`GET /api/bookings/{bookingId}/ticket` & `/api/tickets/{ticketId}/qr`)

#### Get Generated Ticket by Booking ID (`GET /api/bookings/{bookingId}/ticket`)
* **Purpose:** Display the confirmed ticket details after payment succeeds.

```bash
curl -X GET "http://localhost:8080/api/bookings/1001/ticket" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Ticket fetched",
  "data": {
    "ticketId": 501,
    "bookingId": 1001,
    "ticketNumber": "TKT-A9F4C21B",
    "qrCode": "TICKETHUB|BOOKING:1001|SHOW:50|USER:1|SEATS:A1,A2|TICKET:TKT-A9F4C21B",
    "status": "ISSUED",
    "issuedAt": "2026-10-13T00:31:15"
  }
}
```

#### Get Ticket by Ticket ID (`GET /api/tickets/{ticketId}`)
```bash
curl -X GET "http://localhost:8080/api/tickets/501" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

#### Get Ticket QR Code PNG Image (`GET /api/tickets/{ticketId}/qr`)
* **Purpose:** Returns binary `image/png` QR code image (or Base64 JSON if `Accept: application/json` header is sent).

```bash
curl -X GET "http://localhost:8080/api/tickets/501/qr" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  --output ticket-qr.png
```

---

## 4. Payments & Gmail Notification Triggers (`payment-service`)

> **Important Note on Response Wrapper:**
> All endpoints in `payment-service` return:
> `{ "message": "...", "data": ..., "error": false }`
>
> **Enums:**
> - `paymentMethod`: `CARD` | `UPI` | `NET_BANKING` | `WALLET`
> - `status`: `PENDING` | `SUCCESS` | `FAILED` | `CANCELLED` | `REFUNDED`

### 4.1. Initiate Payment / Create Razorpay Order (`POST /api/payments`)
* **Purpose:** Verifies the booking amount and status against `booking-service`, creates a Razorpay order (or simulated order `order_mock_...` when Razorpay keys are not set), and returns `razorpayOrderId` and `razorpayKeyId` for the React Razorpay Checkout modal.
* **Endpoint:** `POST /api/payments`
* **Auth Required:** `Bearer <CUSTOMER_TOKEN>`

```bash
curl -X POST "http://localhost:8080/api/payments" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": 1001,
    "paymentMethod": "UPI",
    "idempotencyKey": "pay-550e8400-e29b-41d4-a716-446655440000"
  }'
```

**Response (`201 Created`):**
```json
{
  "message": "Payment initiated successfully",
  "data": {
    "paymentId": 2001,
    "bookingId": 1001,
    "amount": 700.00,
    "currency": "INR",
    "status": "PENDING",
    "paymentMethod": "UPI",
    "razorpayOrderId": "order_mock_9f8a7b6c5d4e",
    "razorpayPaymentId": null,
    "razorpayKeyId": "rzp_test_simulated_key",
    "failureReason": null,
    "paidAt": null,
    "createdAt": "2026-10-13T00:31:00"
  },
  "error": false
}
```

---

### 4.2. Confirm / Verify Payment From React Frontend (`POST /api/payments/{paymentId}/confirm` or `POST /api/payments/verify`)
* **Purpose:** Called by your React frontend right after the user completes checkout (either via Razorpay Checkout `handler` callback or simulated checkout button).
* **What It Triggers Automatically:**
  1. Marks the payment `SUCCESS` in `payment-service`.
  2. Calls `booking-service` to mark booking `CONFIRMED`, make seat locks permanent, and generate the Ticket (`TKT-...`).
  3. **Sends 1st Gmail Notification (`payment-success`):** Sends the **Payment Transaction Receipt** email (Payment ID, Razorpay Reference, Order ID, Booking ID, Method, Amount, Timestamp) to the customer's Gmail.
  4. **Sends 2nd Gmail Notification (`booking-confirmed`):** Sends the **Complete Confirmed Ticket** email (Ticket Status `CONFIRMED`, Ticket Number, Ticket ID, QR Code Payload, Event Title, Show Date & Time, Venue Name & Address, Screen Name, Seats, Amount Paid) to the customer's Gmail.

#### Option A: Confirm by `paymentId` (`POST /api/payments/{paymentId}/confirm`)
```bash
curl -X POST "http://localhost:8080/api/payments/2001/confirm?razorpayPaymentId=pay_Ph9x87654321" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"
```

#### Option B: Verify Razorpay Checkout Payload (`POST /api/payments/verify`)
```bash
curl -X POST "http://localhost:8080/api/payments/verify" \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "razorpayOrderId": "order_mock_9f8a7b6c5d4e",
    "razorpayPaymentId": "pay_Ph9x87654321",
    "razorpaySignature": "optional-in-simulated-mode"
  }'
```

**Response (`200 OK`):**
```json
{
  "message": "Payment confirmed successfully",
  "data": {
    "paymentId": 2001,
    "bookingId": 1001,
    "amount": 700.00,
    "currency": "INR",
    "status": "SUCCESS",
    "paymentMethod": "UPI",
    "razorpayOrderId": "order_mock_9f8a7b6c5d4e",
    "razorpayPaymentId": "pay_Ph9x87654321",
    "razorpayKeyId": "rzp_test_simulated_key",
    "failureReason": null,
    "paidAt": "2026-10-13T00:31:15",
    "createdAt": "2026-10-13T00:31:00"
  },
  "error": false
}
```

---

### 4.3. Razorpay Server-to-Server Webhook (`POST /api/payments/webhook`)
* **Purpose:** Server-to-server webhook endpoint for Razorpay (`payment.captured` / `payment.failed`).
* **Auth Required:** Public on Gateway; authenticated via `X-Razorpay-Signature` (HMAC-SHA256 of raw JSON body using `RAZORPAY_WEBHOOK_SECRET`).

```bash
curl -X POST "http://localhost:8080/api/payments/webhook" \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Event-Id: evt_unique_001" \
  -H "X-Razorpay-Signature: <HMAC_SHA256_HEX>" \
  -d '{
    "entity": "event",
    "event": "payment.captured",
    "payload": {
      "payment": {
        "entity": {
          "id": "pay_Ph9x87654321",
          "order_id": "order_mock_9f8a7b6c5d4e",
          "amount": 70000,
          "currency": "INR",
          "status": "captured"
        }
      }
    }
  }'
```

---

### 4.4. Get Payment by ID, by Booking ID, Status & Cancel Payment
* `GET /api/payments/{paymentId}` — Get payment details (`CUSTOMER` owner or `ADMIN`)
* `GET /api/payments/booking/{bookingId}` — Get payment for a specific booking (`CUSTOMER` owner or `ADMIN`)
* `GET /api/payments/{paymentId}/status` — Get payment status (`CUSTOMER` owner or `ADMIN`)
* `POST /api/payments/{paymentId}/cancel` — Cancel a `PENDING` payment (`CUSTOMER` owner or `ADMIN`)
* `GET /api/payments` — List all payments (`ADMIN` only)

---

## 5. Recommended End-to-End React User Flow

1. **Auth Page (`/login`, `/register`):**
   - Email/Password Sign-Up $\rightarrow$ `POST /api/users/register`
   - Email/Password Sign-In $\rightarrow$ `POST /api/users/login`
   - "Continue with Google" $\rightarrow$ `POST /api/users/auth/google`
   - Store `{ token, userId, email, fullName, role }` in React Context / `localStorage` and attach `Authorization: Bearer ${token}` to all Axios/fetch requests.
2. **Home / Browse Events Page (`/`):**
   - `GET /api/events?page=0&size=12` (with optional search `title`, `genre`, `language`, `eventType`).
   - Render event poster using `<img src={`http://localhost:8080${event.posterUrl}`} />`.
3. **Event Details & Showtimes Page (`/events/:eventId`):**
   - `GET /api/events/${eventId}`
   - `GET /api/events/${eventId}/shows?page=0&size=20`
4. **Interactive Seat Selection Page (`/shows/:showId/seats`):**
   - `GET /api/shows/${showId}` (shows `eventTitle`, `venueName`, `venueAddress`, `screenName`, `showDate`, `startTime`)
   - `GET /api/shows/${showId}/seats` (renders seat grid grouped by `rowName` and `categoryName` with `AVAILABLE` vs `LOCKED`/`UNAVAILABLE` colors)
   - On "Proceed to Checkout" $\rightarrow$ `POST /api/bookings` with header `Idempotency-Key: crypto.randomUUID()` and body `{ userId, showId, seatIds, customerEmail, customerName }`.
5. **Payment Checkout Page (`/checkout/:bookingId`):**
   - `POST /api/payments` with `{ bookingId, paymentMethod: "UPI", idempotencyKey: crypto.randomUUID() }`.
   - On payment completion $\rightarrow$ `POST /api/payments/${paymentId}/confirm` (or `/api/payments/verify`).
   - Backend automatically sends **Email #1 (Payment Transaction Receipt)** and **Email #2 (Confirmed Ticket with QR & Seat Details)** to the user's Gmail!
6. **Ticket Confirmation Page (`/bookings/:bookingId/ticket`):**
   - `GET /api/bookings/${bookingId}`
   - `GET /api/bookings/${bookingId}/ticket`
   - Render QR code image via `GET /api/tickets/${ticketId}/qr` (with `Authorization: Bearer ${token}`).

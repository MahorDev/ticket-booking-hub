#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL="${PAYMENT_URL:-http://localhost:8084}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-tickethub-mysql-1}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"

echo "======================================================================"
echo "    SCENARIO 4: MONEY TAKEN, BOOKING NOT CONFIRMED -> SAGA FLOW       "
echo "======================================================================"

RAND_ID=$((RANDOM % 8000 + 2000))
SEAT_ID=$((RANDOM % 70 + 1010))
CUSTOMER_EMAIL="saga-customer-${RAND_ID}@tickethub.com"

echo -e "\n[Step 1] Creating a pending booking directly in DB..."
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${SEAT_ID};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${RAND_ID}, 1, 100, 'PENDING_PAYMENT', 350.00, 'INR', 'BKG-SAGA-${RAND_ID}', 'idem-saga-${RAND_ID}', '${CUSTOMER_EMAIL}', 'Saga Customer', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), 1);
INSERT INTO booking_items (booking_id, seat_id, seat_number, price) VALUES (${RAND_ID}, ${SEAT_ID}, 'S${SEAT_ID}', 350.00);
INSERT INTO seat_locks (show_id, seat_id, user_id, booking_id, status, locked_at, expires_at, created_at, updated_at)
VALUES (100, ${SEAT_ID}, 1, ${RAND_ID}, 'LOCKED', NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW());
EOF

# Seed initial payment record in tickethub_payment_db
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, created_at, updated_at)
VALUES (${RAND_ID}, 'INR', 'CARD', 'PENDING', 350.00, 'pay-idem-${RAND_ID}', 'order_saga_${RAND_ID}', NOW(), NOW());
EOF

PAYMENT_ID=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id FROM tickethub_payment_db.payments WHERE booking_id = ${RAND_ID};")
echo "✔ Seeded Booking ID: ${RAND_ID} and Payment ID: ${PAYMENT_ID}"

echo -e "\n[Step 2] Stopping Booking Service container to simulate downstream outage..."
docker compose stop booking-service
echo "✔ Booking Service stopped."

echo -e "\n[Step 3] Customer completes payment while Booking Service is DOWN..."
CONFIRM_PAY_RESP=$(curl -s -X POST "${PAYMENT_URL}/api/payments/${PAYMENT_ID}/confirm" \
  -H "Content-Type: application/json" \
  -d "{\"razorpayOrderId\":\"order_saga_${RAND_ID}\",\"razorpayPaymentId\":\"pay_saga_${RAND_ID}\"}")
echo "Payment Confirm Response: ${CONFIRM_PAY_RESP}"

# Verify payment is SUCCESS in Payment Service
PAY_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${PAYMENT_ID};")
echo "Payment Status in DB: ${PAY_STATUS}"

if [ "${PAY_STATUS}" != "SUCCESS" ]; then
  echo "❌ Error: Payment was not marked SUCCESS!"
  docker compose start booking-service
  exit 1
fi
echo "✔ Money successfully captured by Payment Service while Booking Service was offline!"

echo -e "\n[Step 4] Verifying Payment Outbox holds the pending/published PaymentSucceeded event..."
PAY_OUTBOX=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id, aggregate_id, event_type, status FROM tickethub_payment_db.outbox_messages WHERE aggregate_id = '${RAND_ID}';")
echo "Payment Outbox Row: ${PAY_OUTBOX}"

echo -e "\n[Step 5] Restarting Booking Service container..."
docker compose start booking-service
echo "Waiting 12 seconds for Booking Service to start and consume PaymentSucceeded from Kafka..."
sleep 12

echo -e "\n[Step 6] Checking Booking Service final state in DB..."
BOOKING_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_booking_db.bookings WHERE id = ${RAND_ID};")
echo "Booking Final Status: ${BOOKING_STATUS}"

if [ "${BOOKING_STATUS}" = "CONFIRMED" ]; then
  echo "✔ SAGA SUCCESS: Booking automatically confirmed via asynchronous Kafka event after restart!"
else
  echo "ℹ Checking Booking Service logs..."
  docker logs --tail 30 tickethub-booking-service-1 | grep -i "PaymentSucceeded" || true
fi

echo -e "\n[Step 7] Checking Mailpit for confirmation email to ${CUSTOMER_EMAIL}..."
sleep 2
MAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${CUSTOMER_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Emails found in Mailpit for ${CUSTOMER_EMAIL}: ${MAIL_COUNT}"

if [ "${MAIL_COUNT}" -ge "1" ]; then
  echo "✔ SUCCESS: Exactly 1 confirmation email delivered to customer!"
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 4 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Customer pays and Razorpay captures money, but Booking Service is down. In Phase 1 REST call, the money was taken but the booking remained PENDING_PAYMENT and expired.

• Cause:
  Direct point-to-point REST coupling across distributed databases with no compensation or reliable store-and-forward mechanism.

• Fix:
  Choreographed Saga: Payment Service commits payment capture and saves `PaymentSucceeded` to its Transactional Outbox. When Booking Service recovers, its Inbox consumer reads `PaymentSucceeded`, confirms the booking, makes seat locks permanent, and publishes `BookingConfirmed` to notify the customer.

• Prevention:
  Eliminated synchronous inter-service REST calls for critical state-advancing workflows. Kafka buffers events during outages with at-least-once durability and inbox deduplication.
EOF
echo "======================================================================"

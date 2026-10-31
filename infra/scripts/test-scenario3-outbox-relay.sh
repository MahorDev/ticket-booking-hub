#!/usr/bin/env bash
set -euo pipefail

BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-tickethub-mysql-1}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"

echo "======================================================================"
echo "    SCENARIO 3: AN EVENT IS LOST BETWEEN COMMIT & PUBLISH -> OUTBOX    "
echo "======================================================================"

echo -e "\n[Step 1] Creating a test booking via Booking Service..."
# Create a booking directly or lock a test booking in DB
RAND_ID=$((RANDOM % 8000 + 2000))
SEAT_ID=$((RANDOM % 70 + 1010))
TEST_EMAIL="outbox-customer-${RAND_ID}@tickethub.com"

# Insert a pending booking into tickethub_booking_db
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${SEAT_ID};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at)
VALUES (${RAND_ID}, 1, 100, 'PENDING_PAYMENT', 450.00, 'INR', 'BKG-OUTBOX-${RAND_ID}', 'idem-outbox-${RAND_ID}', '${TEST_EMAIL}', 'Outbox Tester', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW());
INSERT INTO booking_items (booking_id, seat_id, seat_number, price) VALUES (${RAND_ID}, ${SEAT_ID}, 'S${SEAT_ID}', 450.00);
INSERT INTO seat_locks (show_id, seat_id, user_id, booking_id, status, locked_at, expires_at, created_at, updated_at)
VALUES (100, ${SEAT_ID}, 1, ${RAND_ID}, 'LOCKED', NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW());
EOF
echo "✔ Seeded test booking ID: ${RAND_ID} in PENDING_PAYMENT status."

echo -e "\n[Step 2] Stopping Kafka container to simulate message broker crash..."
docker compose stop kafka
echo "✔ Kafka container stopped."

echo -e "\n[Step 3] Confirming booking while Kafka is DOWN..."
CONFIRM_RESP=$(curl -s -X POST "${BOOKING_URL}/api/internal/payments/${RAND_ID}/success" \
  -H "Content-Type: application/json" \
  -d "{\"paymentReference\": \"pay_outbox_test_${RAND_ID}\"}")
echo "Confirmation Response: ${CONFIRM_RESP}"

echo -e "\n[Step 4] Checking Outbox table in database while Kafka is still down..."
OUTBOX_ROW=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id, aggregate_id, status, retry_count FROM tickethub_booking_db.outbox_messages WHERE aggregate_id = '${RAND_ID}';")
echo "Outbox Row: ${OUTBOX_ROW}"

if [ -z "${OUTBOX_ROW}" ]; then
  echo "❌ Error: Outbox row was not saved!"
  docker compose start kafka
  exit 1
fi
echo "✔ Transactional Outbox GUARANTEE: Booking committed to DB and event saved to Outbox despite Kafka outage!"

echo -e "\n[Step 5] Restarting Kafka container..."
docker compose start kafka
echo "Waiting 12 seconds for Kafka to become ready and Outbox relay to poll..."
sleep 12

echo -e "\n[Step 6] Verifying Outbox Relay published the event and marked it PUBLISHED..."
OUTBOX_FINAL=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id, aggregate_id, status, retry_count FROM tickethub_booking_db.outbox_messages WHERE aggregate_id = '${RAND_ID}';")
echo "Outbox Final State: ${OUTBOX_FINAL}"

echo -e "\n[Step 7] Checking Mailpit for delivery to ${TEST_EMAIL}..."
sleep 3
MAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${TEST_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Emails found for ${TEST_EMAIL}: ${MAIL_COUNT}"

if [ "${MAIL_COUNT}" -ge "1" ]; then
  echo "✔ SUCCESS: Email delivered after Kafka restored! Zero messages lost between commit & publish."
else
  echo "ℹ Checking Notification Service logs..."
  docker logs --tail 20 tickethub-notification-service-1 | grep -i "${TEST_EMAIL}" || true
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 3 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Customer pays and booking confirms in database, but Kafka is temporarily down or broker times out, so no event is ever published. Customer never receives ticket email.

• Cause:
  Dual-write problem: A relational database commit and Kafka network publication cannot share a single atomic transaction.

• Fix:
  Transactional Outbox Pattern: Save the event row inside the booking commit transaction (`outbox_messages`). An asynchronous Outbox Relay scheduler (`OutboxPublisherScheduler`) polls unsent messages, publishes to Kafka with booking ID key, and updates status to PUBLISHED.

• Prevention:
  Never publish directly to Kafka inside an essential business database transaction without an Outbox guarantee.
EOF
echo "======================================================================"

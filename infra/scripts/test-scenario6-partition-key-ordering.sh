#!/usr/bin/env bash
set -euo pipefail

BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-tickethub-mysql-1}"

echo "======================================================================"
echo "    SCENARIO 6: EVENTS PROCESSED OUT OF ORDER -> PARTITION KEYS & VERSIONS "
echo "======================================================================"

BOOKING_ID=$((RANDOM % 8000 + 2000))
SEAT_ID=$((RANDOM % 70 + 1010))

echo -e "\n[Step 1] Seeding booking ID ${BOOKING_ID} at version 2 (CANCELLED)..."
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${SEAT_ID};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BOOKING_ID}, 1, 100, 'CANCELLED', 400.00, 'INR', 'BKG-ORD-${BOOKING_ID}', 'idem-ord-${BOOKING_ID}', 'order@tickethub.com', 'Ordering Tester', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), 2);
EOF

echo "✔ Booking is in final state CANCELLED at aggregate_version = 2."

echo -e "\n[Step 2] Injecting a STALE event (PaymentSucceeded with aggregateVersion = 1) out of order..."
STALE_EVENT_JSON=$(cat <<EOF
{"eventMessageId":"evt-stale-${BOOKING_ID}","eventType":"PaymentSucceeded","aggregateVersion":1,"bookingId":${BOOKING_ID},"paymentId":9999,"razorpayPaymentId":"pay_stale_${BOOKING_ID}","amount":400.00,"currency":"INR"}
EOF
)

# Publish stale event to Kafka
docker exec -i tickethub-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment-success \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${BOOKING_ID}:${STALE_EVENT_JSON}"

echo "✔ Injected stale version 1 event into Kafka topic with key=${BOOKING_ID}."
sleep 4

echo -e "\n[Step 3] Checking Booking DB to verify state did NOT regress..."
CURRENT_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_booking_db.bookings WHERE id = ${BOOKING_ID};")
CURRENT_VERSION=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT aggregate_version FROM tickethub_booking_db.bookings WHERE id = ${BOOKING_ID};")

echo "Booking Status: ${CURRENT_STATUS} (Version: ${CURRENT_VERSION})"

if [ "${CURRENT_STATUS}" = "CANCELLED" ]; then
  echo "✔ SUCCESS: Stale out-of-order event was ignored by consumer! Booking remained CANCELLED."
else
  echo "❌ Error: State regressed to ${CURRENT_STATUS}!"
  exit 1
fi

echo -e "\n[Step 4] Checking Booking Service logs for stale event rejection..."
docker logs --tail 25 tickethub-booking-service-1 | grep -i "stale" || true

echo -e "\n======================================================================"
echo "                   SCENARIO 6 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Events arrived out of order (e.g. `PaymentSucceeded` processed after `BookingCancelled`), regressing booking state backwards from CANCELLED to CONFIRMED.

• Cause:
  Kafka only guarantees ordering within a single partition. Random/null keys scatter events across partitions, and concurrent consumers can finish later events first.

• Fix:
  1. Partition Keying: Set `bookingId` as the Kafka message key on all producers (`PaymentEventProducer`, `BookingEventProducer`) so all events for a given booking serialize to the exact same partition.
  2. Optimistic Version Guard: Added `aggregateVersion` to all domain events and `aggregate_version` column to `bookings`. Consumers discard any event whose version is lower than the aggregate's current version.

• Prevention:
  Never publish domain events without an aggregate partition key, and always enforce monotonic version guards in stateful consumers.
EOF
echo "======================================================================"

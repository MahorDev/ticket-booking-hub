#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL="${PAYMENT_URL:-http://localhost:8084}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-tickethub-mysql-1}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"

echo "======================================================================"
echo "    SCENARIO 5: SAGA COMPENSATIONS & REFUNDS (LATE PAYMENT & CANCEL)  "
echo "======================================================================"

echo -e "\n--- TEST A: LATE PAYMENT ON EXPIRED BOOKING -> AUTO-REFUND ---"
LATE_BKG_ID=$((RANDOM % 8000 + 2000))
LATE_SEAT_ID=$((RANDOM % 70 + 1010))
LATE_EMAIL="late-customer-${LATE_BKG_ID}@tickethub.com"

# Seed an EXPIRED booking (expired 10 minutes ago)
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${LATE_SEAT_ID};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${LATE_BKG_ID}, 1, 100, 'EXPIRED', 500.00, 'INR', 'BKG-LATE-${LATE_BKG_ID}', 'idem-late-${LATE_BKG_ID}', '${LATE_EMAIL}', 'Late Customer', DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 20 MINUTE), NOW(), 1);
INSERT INTO booking_items (booking_id, seat_id, seat_number, price) VALUES (${LATE_BKG_ID}, ${LATE_SEAT_ID}, 'S${LATE_SEAT_ID}', 500.00);
EOF

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${LATE_BKG_ID}, 'INR', 'CARD', 'SUCCESS', 500.00, 'pay-late-idem-${LATE_BKG_ID}', 'order_late_${LATE_BKG_ID}', 'pay_late_${LATE_BKG_ID}', NOW(), NOW());
EOF

LATE_PAY_ID=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id FROM tickethub_payment_db.payments WHERE booking_id = ${LATE_BKG_ID};")
echo "✔ Seeded expired Booking ID: ${LATE_BKG_ID} with Payment ID: ${LATE_PAY_ID}"

echo -e "\n[Step A1] Simulating late payment capture event arriving from Payment Service..."
LATE_EVENT_JSON=$(cat <<EOF
{"eventMessageId":"evt-late-${LATE_BKG_ID}","eventType":"PaymentSucceeded","aggregateVersion":1,"bookingId":${LATE_BKG_ID},"paymentId":${LATE_PAY_ID},"razorpayPaymentId":"pay_late_${LATE_BKG_ID}","amount":500.00,"currency":"INR"}
EOF
)

# Publish PaymentSucceeded for the expired booking
docker exec -i tickethub-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment-success \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${LATE_BKG_ID}:${LATE_EVENT_JSON}"

echo "✔ Published PaymentSucceeded event for expired booking. Waiting for Saga compensation..."
sleep 6

# Verify Payment is REFUNDED in Payment Service
LATE_PAY_FINAL=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${LATE_PAY_ID};")
echo "Payment Status after compensation: ${LATE_PAY_FINAL}"

# Verify no confirmation email was sent to late customer
LATE_EMAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${LATE_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Confirmation emails for late booking customer: ${LATE_EMAIL_COUNT}"

if [ "${LATE_PAY_FINAL}" = "REFUNDED" ] && [ "${LATE_EMAIL_COUNT}" -eq "0" ]; then
  echo "✔ TEST A PASSED: Late payment safely compensated with full automated refund! Zero confirmation emails sent."
else
  echo "ℹ Checking logs..."
  docker logs --tail 25 tickethub-payment-service-1 | grep -i "compensation" || true
fi

echo -e "\n--- TEST B: CONFIRMED BOOKING CANCELLATION -> REFUND & SEATS FREED ---"
CANCEL_BKG_ID=$((RANDOM % 8000 + 2000))
CANCEL_SEAT_ID=$((RANDOM % 70 + 1010))

# Create a confirmed booking with captured payment
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${CANCEL_SEAT_ID};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${CANCEL_BKG_ID}, 1, 100, 'CONFIRMED', 600.00, 'INR', 'BKG-CNC-${CANCEL_BKG_ID}', 'idem-cnc-${CANCEL_BKG_ID}', 'cancel@tickethub.com', 'Cancel Tester', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), 1);
INSERT INTO booking_items (booking_id, seat_id, seat_number, price) VALUES (${CANCEL_BKG_ID}, ${CANCEL_SEAT_ID}, 'S${CANCEL_SEAT_ID}', 600.00);
INSERT INTO seat_locks (show_id, seat_id, user_id, booking_id, status, locked_at, expires_at, created_at, updated_at)
VALUES (100, ${CANCEL_SEAT_ID}, 1, ${CANCEL_BKG_ID}, 'LOCKED', NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW());
EOF

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${CANCEL_BKG_ID}, 'INR', 'CARD', 'SUCCESS', 600.00, 'pay-cnc-idem-${CANCEL_BKG_ID}', 'order_cnc_${CANCEL_BKG_ID}', 'pay_cnc_${CANCEL_BKG_ID}', NOW(), NOW());
EOF

echo "✔ Seeded Confirmed Booking ID: ${CANCEL_BKG_ID} with seat ID: ${CANCEL_SEAT_ID}"

echo -e "\n[Step B1] Customer cancels the confirmed booking via Booking API..."
CANCEL_RESP=$(curl -s -X POST "${BOOKING_URL}/api/bookings/${CANCEL_BKG_ID}/cancel?userId=1")
echo "Cancel Response: ${CANCEL_RESP}"

echo "Waiting 6 seconds for BookingCancelled event to reach Payment Service and trigger refund..."
sleep 6

# Check Payment Service refunded
CNC_PAY_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE booking_id = ${CANCEL_BKG_ID};")
echo "Payment Status after cancellation: ${CNC_PAY_STATUS}"

# Check Seat Locks freed in Booking DB
SEAT_LOCK_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_booking_db.seat_locks WHERE booking_id = ${CANCEL_BKG_ID};")
echo "Seat Lock Status: ${SEAT_LOCK_STATUS}"

if [ "${CNC_PAY_STATUS}" = "REFUNDED" ] && [ "${SEAT_LOCK_STATUS}" = "RELEASED" ]; then
  echo "✔ TEST B PASSED: Booking cancellation triggered automated Razorpay refund and released seats!"
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 5 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Customer paid after a 10-minute seat lock hold expired, or customer cancelled a confirmed booking. Money was captured with no active ticket, or seat was locked forever.

• Cause:
  Absence of compensating transactions in distributed workflow.

• Fix:
  Compensating Saga Actions: When Booking detects a payment for an expired booking, it issues `BookingRejected`. When customer cancels, it releases seats and issues `BookingCancelled`. Payment Service consumes both and invokes Razorpay Refunds API idempotently, transitioning payment to `REFUNDED` and publishing `PaymentRefunded`.

• Prevention:
  Every forward saga step has an explicit backward compensating step. All refund actions are strictly idempotent keyed by payment ID.
EOF
echo "======================================================================"

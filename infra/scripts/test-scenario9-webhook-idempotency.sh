#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 9 VERIFICATION DRILL: RAZORPAY WEBHOOK IDEMPOTENCY & STATE MACHINE
# Tests: Deduplication of event IDs, strictly forward transitions, and resolving UNKNOWN states.
# ==============================================================================
set -euo pipefail

PAYMENT_URL="${PAYMENT_URL:-http://localhost:8084}"
MYSQL_CONTAINER="tickethub-mysql-1"
WEBHOOK_SECRET="replace-with-a-random-secret-at-least-32-characters"

echo "======================================================================"
echo "    SCENARIO 9: RAZORPAY WEBHOOK DEDUP, FORWARD STATE & UNKNOWN RESOLUTION "
echo "======================================================================"

# --- SEED PAYMENT IN PENDING STATE ---
PAY_BKG_ID=$((RANDOM % 8000 + 1000))
ORDER_ID="order_s9_${PAY_BKG_ID}"
PAYMENT_ID="pay_s9_${PAY_BKG_ID}"

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${PAY_BKG_ID}, 1, 100, 'PENDING_PAYMENT', 500.00, 'INR', 'BKG-S9-${PAY_BKG_ID}', 'idem-s9-${PAY_BKG_ID}', 'webhook${PAY_BKG_ID}@tickethub.com', 'Webhook Tester', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 1);
EOF

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${PAY_BKG_ID}, 'INR', 'CARD', 'PENDING', 500.00, 'pay-s9-${PAY_BKG_ID}', '${ORDER_ID}', '${PAYMENT_ID}', NOW(), NOW());
EOF

INTERNAL_PAY_ID=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT id FROM tickethub_payment_db.payments WHERE booking_id = ${PAY_BKG_ID};")
echo "✔ Seeded Payment ID ${INTERNAL_PAY_ID} in PENDING status."

# Helper function to send signed webhook
send_webhook() {
  local event_type="$1"
  local event_id="$2"
  local payload="{\"entity\":\"event\",\"account_id\":\"acc_test\",\"event\":\"${event_type}\",\"contains\":[\"payment\"],\"payload\":{\"payment\":{\"entity\":{\"id\":\"${PAYMENT_ID}\",\"entity\":\"payment\",\"amount\":50000,\"currency\":\"INR\",\"status\":\"${event_type##*.}\",\"order_id\":\"${ORDER_ID}\"}}}}"
  local signature
  signature=$(python3 -c "import hmac, hashlib, sys; print(hmac.new(sys.argv[1].encode('utf-8'), sys.argv[2].encode('utf-8'), hashlib.sha256).hexdigest())" "${WEBHOOK_SECRET}" "${payload}")
  
  curl -s -X POST "${PAYMENT_URL}/api/payments/webhook" \
    -H "Content-Type: application/json" \
    -H "X-Razorpay-Signature: ${signature}" \
    -H "X-Razorpay-Event-Id: ${event_id}" \
    -d "${payload}"
}

# --- TEST 1: Forward transition: PENDING -> AUTHORIZED ---
echo -e "\n[Test 1] Sending 'payment.authorized' webhook..."
send_webhook "payment.authorized" "evt_auth_${PAY_BKG_ID}"
STATUS_1=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${INTERNAL_PAY_ID};")
echo "Payment Status after payment.authorized: ${STATUS_1}"

if [ "${STATUS_1}" = "AUTHORIZED" ]; then
  echo "✔ TEST 1 PASSED: Forward transition to AUTHORIZED accepted."
else
  echo "❌ TEST 1 FAILED: Expected AUTHORIZED but got ${STATUS_1}"
  exit 1
fi

# --- TEST 2: Forward transition: AUTHORIZED -> SUCCESS (CAPTURED) ---
echo -e "\n[Test 2] Sending 'payment.captured' webhook..."
send_webhook "payment.captured" "evt_cap_${PAY_BKG_ID}"
STATUS_2=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${INTERNAL_PAY_ID};")
echo "Payment Status after payment.captured: ${STATUS_2}"

if [ "${STATUS_2}" = "SUCCESS" ]; then
  echo "✔ TEST 2 PASSED: Forward transition to SUCCESS accepted."
else
  echo "❌ TEST 2 FAILED: Expected SUCCESS but got ${STATUS_2}"
  exit 1
fi

# --- TEST 3: Backward transition rejection (Out of order payment.authorized after captured) ---
echo -e "\n[Test 3] Sending out-of-order 'payment.authorized' webhook on SUCCESS payment..."
send_webhook "payment.authorized" "evt_stale_auth_${PAY_BKG_ID}"
STATUS_3=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${INTERNAL_PAY_ID};")
echo "Payment Status after stale authorized: ${STATUS_3}"

if [ "${STATUS_3}" = "SUCCESS" ]; then
  echo "✔ TEST 3 PASSED: Regressive backward transition was rejected! Payment remained SUCCESS."
else
  echo "❌ TEST 3 FAILED: Payment regressed backwards to ${STATUS_3}"
  exit 1
fi

# --- TEST 4: Duplicate Webhook Delivery (5 identical webhooks) ---
echo -e "\n[Test 4] Sending duplicate 'payment.captured' webhook 5 times with identical event ID..."
for i in {1..5}; do
  send_webhook "payment.captured" "evt_dup_burst_${PAY_BKG_ID}"
done
DUP_COUNT=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT count(*) FROM tickethub_payment_db.payment_webhook_events WHERE event_id = 'evt_dup_burst_${PAY_BKG_ID}';")
echo "Recorded webhook event entries in DB: ${DUP_COUNT}"

if [ "${DUP_COUNT}" -eq "1" ]; then
  echo "✔ TEST 4 PASSED: Webhook idempotency table stored exactly 1 entry; 4 duplicates discarded."
else
  echo "❌ TEST 4 FAILED: Expected 1 event entry but found ${DUP_COUNT}"
  exit 1
fi

# --- TEST 5: UNKNOWN State Resolution ---
echo -e "\n[Test 5] Resolving a payment in UNKNOWN status via provider inquiry..."
UNKNOWN_PAY_ID=$((RANDOM % 8000 + 4000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (id, booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${UNKNOWN_PAY_ID}, ${PAY_BKG_ID}, 'INR', 'CARD', 'UNKNOWN', 500.00, 'pay-unk-${UNKNOWN_PAY_ID}', 'order_unk_${UNKNOWN_PAY_ID}', 'pay_mock_${UNKNOWN_PAY_ID}', NOW(), NOW());
EOF

RESOLVE_RESP=$(curl -s -X POST "${PAYMENT_URL}/api/payments/${UNKNOWN_PAY_ID}/resolve-unknown")
echo "Resolve response: ${RESOLVE_RESP}"
STATUS_5=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE id = ${UNKNOWN_PAY_ID};")
echo "Payment Status after resolve-unknown: ${STATUS_5}"

if [ "${STATUS_5}" = "SUCCESS" ]; then
  echo "✔ TEST 5 PASSED: UNKNOWN state queried provider and cleanly resolved to SUCCESS."
else
  echo "❌ TEST 5 FAILED: Expected SUCCESS but got ${STATUS_5}"
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 9 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Razorpay webhooks were repeated up to 5 times, delivered out of order (authorized after captured), or client timeouts left payments in indeterminate status.

• Cause:
  Network retries by payment gateways and missing state machine guards allowing backward state regression.

• Fix:
  1. Idempotency Keying: Unique constraint on `payment_webhook_events.event_id` ignores duplicate webhooks.
  2. Monotonic State Machine: Transitions only move forward (INITIATED -> PENDING -> AUTHORIZED -> SUCCESS -> REFUNDED). Out-of-order events arriving for final states are safely discarded.
  3. UNKNOWN State & Re-query: Added `UNKNOWN` status for timed-out calls, resolved via `POST /api/payments/{id}/resolve-unknown` querying Razorpay API.

• Prevention:
  External webhooks must always be guarded by monotonic state progression and unique event persistence.
EOF

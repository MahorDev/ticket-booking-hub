#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 7 VERIFICATION DRILL: SAGA STATE TRACKING & VISIBILITY
# Answers: "What state is booking X in, and why?"
# ==============================================================================
set -euo pipefail

BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="tickethub-mysql-1"

echo "======================================================================"
echo "    SCENARIO 7: SAGA STATE MACHINE & OBSERVABILITY VERIFICATION      "
echo "======================================================================"

# --- TEST 1: PENDING PAYMENT BOOKING ---
BKG_PENDING=$((RANDOM % 8000 + 1000))
SEAT_PENDING=$((RANDOM % 70 + 1010))

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
DELETE FROM seat_locks WHERE show_id = 100 AND seat_id = ${SEAT_PENDING};
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_PENDING}, 1, 100, 'PENDING_PAYMENT', 450.00, 'INR', 'BKG-S7-${BKG_PENDING}', 'idem-s7-${BKG_PENDING}', 'customer${BKG_PENDING}@tickethub.com', 'Saga Tester', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 1);
EOF

echo "[Test 1] Querying Saga State for active PENDING_PAYMENT booking (${BKG_PENDING})..."
SAGA_RESP_1=$(curl -s "${BOOKING_URL}/api/bookings/${BKG_PENDING}/saga-state")
echo "Response: ${SAGA_RESP_1}"
STATE_1=$(echo "${SAGA_RESP_1}" | grep -o '"sagaState":"[^"]*' | cut -d'"' -f4)
EXPLANATION_1=$(echo "${SAGA_RESP_1}" | grep -o '"explanation":"[^"]*' | cut -d'"' -f4)

if [ "${STATE_1}" = "PAYMENT_PENDING" ]; then
  echo "✔ TEST 1 PASSED: Saga State = PAYMENT_PENDING. Explanation: '${EXPLANATION_1}'"
else
  echo "❌ TEST 1 FAILED: Expected PAYMENT_PENDING but got ${STATE_1}"
  exit 1
fi

# --- TEST 2: EXPIRED BOOKING ---
BKG_EXPIRED=$((RANDOM % 8000 + 1000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_EXPIRED}, 1, 100, 'PENDING_PAYMENT', 300.00, 'INR', 'BKG-EXP-${BKG_EXPIRED}', 'idem-exp-${BKG_EXPIRED}', 'exp${BKG_EXPIRED}@tickethub.com', 'Expired Tester', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1);
EOF

echo -e "\n[Test 2] Querying Saga State for EXPIRED hold booking (${BKG_EXPIRED})..."
SAGA_RESP_2=$(curl -s "${BOOKING_URL}/api/bookings/${BKG_EXPIRED}/saga-state")
echo "Response: ${SAGA_RESP_2}"
STATE_2=$(echo "${SAGA_RESP_2}" | grep -o '"sagaState":"[^"]*' | cut -d'"' -f4)
EXPLANATION_2=$(echo "${SAGA_RESP_2}" | grep -o '"explanation":"[^"]*' | cut -d'"' -f4)

if [ "${STATE_2}" = "EXPIRED" ]; then
  echo "✔ TEST 2 PASSED: Saga State = EXPIRED. Explanation: '${EXPLANATION_2}'"
else
  echo "❌ TEST 2 FAILED: Expected EXPIRED but got ${STATE_2}"
  exit 1
fi

# --- TEST 3: CONFIRMED BOOKING ---
BKG_CONFIRMED=$((RANDOM % 8000 + 1000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_CONFIRMED}, 1, 100, 'CONFIRMED', 750.00, 'INR', 'BKG-CNF-${BKG_CONFIRMED}', 'idem-cnf-${BKG_CONFIRMED}', 'cnf${BKG_CONFIRMED}@tickethub.com', 'Confirmed Tester', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), 2);
EOF

echo -e "\n[Test 3] Querying Saga State for CONFIRMED booking (${BKG_CONFIRMED})..."
SAGA_RESP_3=$(curl -s "${BOOKING_URL}/api/bookings/${BKG_CONFIRMED}/saga-state")
echo "Response: ${SAGA_RESP_3}"
STATE_3=$(echo "${SAGA_RESP_3}" | grep -o '"sagaState":"[^"]*' | cut -d'"' -f4)
EXPLANATION_3=$(echo "${SAGA_RESP_3}" | grep -o '"explanation":"[^"]*' | cut -d'"' -f4)

if [ "${STATE_3}" = "CONFIRMED" ]; then
  echo "✔ TEST 3 PASSED: Saga State = CONFIRMED. Explanation: '${EXPLANATION_3}'"
else
  echo "❌ TEST 3 FAILED: Expected CONFIRMED but got ${STATE_3}"
  exit 1
fi

# --- TEST 4: CANCELLED BOOKING ---
BKG_CANCELLED=$((RANDOM % 8000 + 1000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_CANCELLED}, 1, 100, 'CANCELLED', 750.00, 'INR', 'BKG-CNC-${BKG_CANCELLED}', 'idem-cnc-${BKG_CANCELLED}', 'cnc${BKG_CANCELLED}@tickethub.com', 'Cancelled Tester', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), 3);
EOF

echo -e "\n[Test 4] Querying Saga State for CANCELLED booking (${BKG_CANCELLED})..."
SAGA_RESP_4=$(curl -s "${BOOKING_URL}/api/bookings/${BKG_CANCELLED}/saga-state")
echo "Response: ${SAGA_RESP_4}"
STATE_4=$(echo "${SAGA_RESP_4}" | grep -o '"sagaState":"[^"]*' | cut -d'"' -f4)
EXPLANATION_4=$(echo "${SAGA_RESP_4}" | grep -o '"explanation":"[^"]*' | cut -d'"' -f4)

if [ "${STATE_4}" = "CANCELLED" ]; then
  echo "✔ TEST 4 PASSED: Saga State = CANCELLED. Explanation: '${EXPLANATION_4}'"
else
  echo "❌ TEST 4 FAILED: Expected CANCELLED but got ${STATE_4}"
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 7 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Customer or support asks "Where is my booking?", but with choreography across 3 microservices and Kafka, operators could not determine the exact state or whether a refund was pending.

• Cause:
  Decentralized event choreography lacks a single queryable orchestrator table.

• Fix:
  Built `/api/bookings/{id}/saga-state` answering "what state is booking X in, and why?". Synthesizes aggregate version, expiration timestamps, and transitions into a clean state machine with audit history.
  Authored `docs/SAGA_DECISION_NOTE.md` formalizing why TicketHub keeps Choreography with Observable State Querying over heavy Orchestration.

• Prevention:
  All stateful distributed entities must expose an observable saga-state contract for production operations and observability.
EOF

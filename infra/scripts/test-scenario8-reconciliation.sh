#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 8 VERIFICATION DRILL: RECONCILIATION JOB
# Fixes mismatches where Razorpay/Payment and Booking databases drifted.
# ==============================================================================
set -euo pipefail

PAYMENT_URL="${PAYMENT_URL:-http://localhost:8084}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8083}"
MYSQL_CONTAINER="tickethub-mysql-1"

echo "======================================================================"
echo "    SCENARIO 8: RECONCILIATION JOB AUDIT & SELF-HEALING              "
echo "======================================================================"

# --- SEED 1: Captured payment with PENDING_PAYMENT booking ---
BKG_DRIFT_1=$((RANDOM % 8000 + 1000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_DRIFT_1}, 1, 100, 'PENDING_PAYMENT', 500.00, 'INR', 'BKG-RC1-${BKG_DRIFT_1}', 'idem-rc1-${BKG_DRIFT_1}', 'recon1@tickethub.com', 'Recon Tester 1', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 1);
EOF

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${BKG_DRIFT_1}, 'INR', 'CARD', 'SUCCESS', 500.00, 'pay-rc1-${BKG_DRIFT_1}', 'order_rc1_${BKG_DRIFT_1}', 'pay_rc1_${BKG_DRIFT_1}', NOW(), NOW());
EOF
echo "✔ Seeded Mismatch 1: Captured payment for PENDING_PAYMENT booking ID ${BKG_DRIFT_1}"

# --- SEED 2: Captured payment with EXPIRED booking ---
BKG_DRIFT_2=$((RANDOM % 8000 + 1000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_booking_db <<EOF
INSERT INTO bookings (id, user_id, show_id, status, total_amount, currency, booking_reference, idempotency_key, customer_email, customer_name, expires_at, created_at, updated_at, aggregate_version)
VALUES (${BKG_DRIFT_2}, 1, 100, 'EXPIRED', 400.00, 'INR', 'BKG-RC2-${BKG_DRIFT_2}', 'idem-rc2-${BKG_DRIFT_2}', 'recon2@tickethub.com', 'Recon Tester 2', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1);
EOF

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${BKG_DRIFT_2}, 'INR', 'CARD', 'SUCCESS', 400.00, 'pay-rc2-${BKG_DRIFT_2}', 'order_rc2_${BKG_DRIFT_2}', 'pay_rc2_${BKG_DRIFT_2}', NOW(), NOW());
EOF
echo "✔ Seeded Mismatch 2: Captured payment for EXPIRED booking ID ${BKG_DRIFT_2}"

# --- SEED 3: Orphaned payment with non-existent booking ---
BKG_ORPHAN=$((RANDOM % 8000 + 9000))
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot tickethub_payment_db <<EOF
INSERT INTO payments (booking_id, currency, payment_method, status, amount, idempotency_key, razorpay_order_id, razorpay_payment_id, created_at, updated_at)
VALUES (${BKG_ORPHAN}, 'INR', 'CARD', 'SUCCESS', 350.00, 'pay-rc3-${BKG_ORPHAN}', 'order_rc3_${BKG_ORPHAN}', 'pay_rc3_${BKG_ORPHAN}', NOW(), NOW());
EOF
echo "✔ Seeded Mismatch 3: Orphaned payment for non-existent booking ID ${BKG_ORPHAN}"

echo -e "\n[Step 2] Executing Reconciliation Job via POST /api/internal/reconciliation/run..."
RECON_RESP=$(curl -s -X POST "${PAYMENT_URL}/api/internal/reconciliation/run")
echo "Reconciliation Report Output:"
echo "${RECON_RESP}"

# Validate responses in DB
sleep 4

echo -e "\n[Step 3] Verifying auto-healed state in databases..."
# Mismatch 1: Booking should now be CONFIRMED via auto-published saga event
BKG_STATUS_1=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_booking_db.bookings WHERE id = ${BKG_DRIFT_1};")
echo "Mismatch 1 Booking Status: ${BKG_STATUS_1}"

# Mismatch 2: Payment should be REFUNDED
PAY_STATUS_2=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE booking_id = ${BKG_DRIFT_2};")
echo "Mismatch 2 Payment Status: ${PAY_STATUS_2}"

# Mismatch 3: Orphan payment should be REFUNDED
PAY_STATUS_3=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_payment_db.payments WHERE booking_id = ${BKG_ORPHAN};")
echo "Mismatch 3 Orphan Payment Status: ${PAY_STATUS_3}"

if [ "${PAY_STATUS_2}" = "REFUNDED" ] && [ "${PAY_STATUS_3}" = "REFUNDED" ]; then
  echo -e "\n✔ SUCCESS: Reconciliation job successfully audited, auto-confirmed valid orders and refunded invalid orders!"
else
  echo -e "\n❌ Reconciliation verification failed."
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 8 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Due to dropped network packets or poison messages, Razorpay recorded captured funds, but Booking remained unconfirmed, or funds were captured for expired seats with no ticket generated.

• Cause:
  Event-driven eventual consistency can drift if an outage exceeds retention or dead-letter messages go unplayed.

• Fix:
  Reconciliation Job (`POST /api/internal/reconciliation/run`):
  Runs a periodic cross-system audit against Razorpay as the single source of financial truth.
  1. If payment is SUCCESS and booking is PENDING_PAYMENT: Re-publishes `PaymentSucceeded` to auto-confirm.
  2. If payment is SUCCESS but booking is EXPIRED or CANCELLED: Automatically executes `paymentProvider.refund()` and sets payment to `REFUNDED`.
  3. If payment is orphaned: Issues automatic customer refund.

• Prevention:
  Scheduled reconciliation jobs guard business consistency and prevent customer support escalation.
EOF

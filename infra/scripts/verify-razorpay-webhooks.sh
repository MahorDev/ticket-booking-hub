#!/usr/bin/env bash
set -euo pipefail

PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-http://localhost:8084}"
WEBHOOK_SECRET="${WEBHOOK_SECRET:-test_webhook_secret_12345}"
TARGET_URL="${PAYMENT_SERVICE_URL}/api/payments/webhook"

echo "=========================================================="
echo " TIKETHUB — RAZORPAY WEBHOOK VERIFICATION SUITE           "
echo "=========================================================="
echo "Target: ${TARGET_URL}"

ORDER_ID="order_test_$RANDOM"
PAYMENT_ID="pay_test_$RANDOM"
EVENT_ID="evt_captured_$RANDOM"

PAYLOAD=$(cat <<EOF
{
  "entity": "event",
  "account_id": "acc_tickethub",
  "event": "payment.captured",
  "id": "${EVENT_ID}",
  "payload": {
    "payment": {
      "entity": {
        "id": "${PAYMENT_ID}",
        "order_id": "${ORDER_ID}",
        "amount": 50000,
        "currency": "INR",
        "status": "captured"
      }
    }
  }
}
EOF
)

SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $2}')

echo -e "\n[Scenario 1] Sending valid HMAC-SHA256 signed payment.captured webhook..."
curl -s -i -X POST "$TARGET_URL" \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: $SIGNATURE" \
  -d "$PAYLOAD" || true

echo -e "\n[Scenario 2] Sending webhook with invalid signature..."
STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TARGET_URL" \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: invalid_sig" \
  -d "$PAYLOAD" || true)

if [ "$STATUS_CODE" -eq 401 ] || [ "$STATUS_CODE" -eq 403 ] || [ "$STATUS_CODE" -eq 500 ]; then
  echo "PASS: Invalid signature rejected with status $STATUS_CODE"
fi

echo -e "\nRazorpay Webhook Verification complete."

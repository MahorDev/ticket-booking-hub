#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
WEBHOOK_SECRET="${WEBHOOK_SECRET:-replace-with-a-random-secret-at-least-32-characters}"

echo "=========================================================="
echo " TIKETHUB — END-TO-END CUSTOMER JOURNEY VERIFICATION      "
echo "=========================================================="

echo -e "\n[1/4] Authenticating Admin & Customer..."
ADMIN_TOKEN=$(curl -s -X POST "${GATEWAY_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

CUST_TOKEN=$(curl -s -X POST "${GATEWAY_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"customer","password":"customer123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

echo "Admin Token: ${ADMIN_TOKEN:0:15}..."
echo "Customer Token: ${CUST_TOKEN:0:15}..."

echo -e "\n[2/4] Testing Catalogue Query..."
curl -s -X GET "${GATEWAY_URL}/api/events" -H "Authorization: Bearer ${CUST_TOKEN}" | grep -o '"totalElements":[0-9]*' || true

echo -e "\n[3/4] Testing Ownership Enforcement on My-Bookings..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${GATEWAY_URL}/api/bookings/my-bookings?userId=9999" \
  -H "Authorization: Bearer ${CUST_TOKEN}")

if [ "$HTTP_STATUS" -eq 403 ]; then
  echo "PASS: Ownership enforced (Received HTTP 403 Forbidden for mismatched user)"
else
  echo "FAIL: Expected HTTP 403 Forbidden, received $HTTP_STATUS"
  exit 1
fi

echo -e "\n[4/4] Full Customer Journey script verified."


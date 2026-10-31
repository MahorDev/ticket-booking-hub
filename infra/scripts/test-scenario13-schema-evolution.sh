#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 13 VERIFICATION DRILL: SCHEMA EVOLUTION CONTRACT (V1, V2 & V3 COMPATIBILITY)
# Tests: Backward & forward compatibility across payload versions without breaking consumers.
# ==============================================================================
set -euo pipefail

KAFKA_CONTAINER="tickethub-kafka-1"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
TOPIC="booking-confirmed"

echo "======================================================================"
echo "    SCENARIO 13: SCHEMA EVOLUTION & CONTRACT COMPATIBILITY           "
echo "======================================================================"

# --- TEST 1: V1 LEGACY PAYLOAD (Using legacy "amount" without "seatCategory") ---
BKG_V1=$((RANDOM % 8000 + 1000))
V1_EMAIL="schema-v1-${BKG_V1}@tickethub.com"
echo -e "\n[Test 1] Publishing Legacy V1 Payload (with 'amount', no 'seatCategory')..."
V1_JSON=$(cat <<EOF
{"eventMessageId":"evt-v1-${BKG_V1}","eventType":"BookingConfirmed","bookingId":${BKG_V1},"bookingReference":"BKG-V1-${BKG_V1}","recipientEmail":"${V1_EMAIL}","amount":400.00,"currency":"INR"}
EOF
)
docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}" \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${BKG_V1}:${V1_JSON}"

# --- TEST 2: V2 MODERN PAYLOAD (Using "totalAmount" and "seatCategory") ---
BKG_V2=$((RANDOM % 8000 + 1000))
V2_EMAIL="schema-v2-${BKG_V2}@tickethub.com"
echo -e "\n[Test 2] Publishing Modern V2 Payload (with 'totalAmount' and 'seatCategory')..."
V2_JSON=$(cat <<EOF
{"eventMessageId":"evt-v2-${BKG_V2}","eventType":"BookingConfirmed","bookingId":${BKG_V2},"bookingReference":"BKG-V2-${BKG_V2}","recipientEmail":"${V2_EMAIL}","totalAmount":650.00,"currency":"INR","seatCategory":"VIP_LOUNGE"}
EOF
)
docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}" \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${BKG_V2}:${V2_JSON}"

# --- TEST 3: V3 FORWARD-COMPATIBLE PAYLOAD (With brand new unknown fields) ---
BKG_V3=$((RANDOM % 8000 + 1000))
V3_EMAIL="schema-v3-${BKG_V3}@tickethub.com"
echo -e "\n[Test 3] Publishing Future V3 Payload (with additive unknown fields)..."
V3_JSON=$(cat <<EOF
{"eventMessageId":"evt-v3-${BKG_V3}","eventType":"BookingConfirmed","bookingId":${BKG_V3},"bookingReference":"BKG-V3-${BKG_V3}","recipientEmail":"${V3_EMAIL}","totalAmount":850.00,"currency":"INR","seatCategory":"IMAX_RECLINER","loyaltyPointsEarned":250,"promoVoucher":"SUPER_CINEMA_2026","deviceContext":{"os":"Android","version":"15"}}
EOF
)
docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}" \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${BKG_V3}:${V3_JSON}"

echo -e "\nWaiting 5 seconds for Notification Service to consume all versions..."
sleep 5

# Verify each received an email in Mailpit
V1_CNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${V1_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
V2_CNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${V2_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
V3_CNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${V3_EMAIL}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")

echo "V1 Email delivered: ${V1_CNT} (Expected: 1)"
echo "V2 Email delivered: ${V2_CNT} (Expected: 1)"
echo "V3 Email delivered: ${V3_CNT} (Expected: 1)"

if [ "${V1_CNT}" -eq 1 ] && [ "${V2_CNT}" -eq 1 ] && [ "${V3_CNT}" -eq 1 ]; then
  echo -e "\n✔ SCENARIO 13 PASSED: All three schema versions (V1 legacy, V2 current, V3 future) parsed and delivered seamlessly!"
else
  echo -e "\n❌ Schema evolution verification failed."
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 13 INCIDENT NOTE                          "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Product teams renamed `amount` to `totalAmount` and added `seatCategory`. Deploying without backwards compatibility crashed running consumers with deserialization exceptions.

• Cause:
  Strict schema deserialization without aliasing or ignore-unknown annotations.

• Fix:
  1. Tolerant Reader Pattern: Annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so new additive fields never break existing consumers.
  2. Field Renaming Aliases: Used `@JsonAlias({"totalAmount", "amount"})` to allow old and new field names simultaneously.
  3. Parallel Version Deprecation: New fields are published alongside old ones until downstream consumers migrate.

• Prevention:
  All shared event contracts must enforce additive schema rules and run backwards/forwards compatibility tests in CI.
EOF

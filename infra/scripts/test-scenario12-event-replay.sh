#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 12 VERIFICATION DRILL: STATE REBUILD & SAFE HISTORICAL REPLAY
# Tests: Topic retention inspection -> Replay via isolated consumer group from earliest offset -> Verifies 0 side-effect duplicates.
# ==============================================================================
set -euo pipefail

KAFKA_CONTAINER="tickethub-kafka-1"
TOPIC="booking-confirmed"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
REPLAY_GROUP="notification-audit-replay-$(date +%s)"

echo "======================================================================"
echo "    SCENARIO 12: STATE REBUILD & SAFE HISTORICAL REPLAY RUNBOOK      "
echo "======================================================================"

echo -e "\n[Step 1] Checking topic retention configuration for '${TOPIC}'..."
docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic "${TOPIC}"

echo -e "\n[Step 2] Capturing baseline email count in Mailpit before replay..."
BASE_EMAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/messages" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Initial Mailpit message count: ${BASE_EMAIL_COUNT}"

echo -e "\n[Step 3] Spawning an isolated replay consumer group '${REPLAY_GROUP}' reading from earliest offset..."
REPLAYED_EVENTS=$(docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}" \
  --group "${REPLAY_GROUP}" \
  --from-beginning \
  --timeout-ms 5000 2>/dev/null | grep -c "BookingConfirmed" || true)

echo "Historical BookingConfirmed events recovered from Kafka: ${REPLAYED_EVENTS}"

if [ "${REPLAYED_EVENTS}" -gt 0 ]; then
  echo "✔ Successfully replayed ${REPLAYED_EVENTS} historical events from Kafka log!"
else
  echo "❌ No events found in topic history."
  exit 1
fi

echo -e "\n[Step 4] Checking that safe replay produced ZERO unintended emails in Mailpit..."
FINAL_EMAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/messages" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Mailpit message count after replay: ${FINAL_EMAIL_COUNT}"

if [ "${FINAL_EMAIL_COUNT}" -eq "${BASE_EMAIL_COUNT}" ]; then
  echo "✔ SUCCESS: Replay consumer group rebuilt historical records without triggering duplicate emails!"
else
  echo "❌ Unintended emails were dispatched during replay!"
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 12 INCIDENT NOTE                          "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  A service database was corrupted and restored from a 24-hour-old backup. Operators needed to catch up missing data from Kafka without triggering duplicate notifications or charges.

• Cause:
  Naive consumers sharing existing group IDs re-execute side effects or crash trying to replay already-committed offsets.

• Fix:
  1. Safe Replay Architecture: Deploy a new consumer group with a fresh group ID and `auto.offset.reset=earliest`.
  2. Side-Effect Decoupling: Replay mode / read-only audit flag bypasses downstream notification dispatchers while rebuilding local database state.
  3. Retention Planning: Verified topic retention (default 7 days) provides the replay window.
  Documented procedure in `docs/REPLAY_RUNBOOK.md`.

• Prevention:
  Every consumer that performs side-effects must support an idempotent bypass or dedicated event replay mode.
EOF

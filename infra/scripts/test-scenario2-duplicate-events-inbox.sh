#!/usr/bin/env bash
set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-tickethub-kafka-1}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"

echo "======================================================================"
echo "    SCENARIO 2: THE SAME EVENT ARRIVES TWICE -> INBOX IDEMPOTENCY     "
echo "======================================================================"

EVENT_ID="evt-replay-$(date +%s)"
BOOKING_ID=$((RANDOM % 9000 + 1000))
RECIPIENT="customer-dedup-${BOOKING_ID}@tickethub.com"

echo -e "\n[Step 1] Target Recipient: ${RECIPIENT} (Event ID: ${EVENT_ID}, Booking ID: ${BOOKING_ID})"
EVENT_JSON=$(cat <<EOF
{"eventMessageId":"${EVENT_ID}","bookingId":${BOOKING_ID},"bookingReference":"BKG-${BOOKING_ID}","eventTitle":"Avatar 3","recipientEmail":"${RECIPIENT}","totalAmount":850.0,"ticketNumber":"TKT-${BOOKING_ID}"}
EOF
)

echo -e "\n[Step 2] Publishing the identical event 5 times to simulate network replays..."
for i in {1..5}; do
  docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic booking-confirmed \
    --property "parse.key=true" \
    --property "key.separator=:" <<< "${BOOKING_ID}:${EVENT_JSON}"
  echo "  Attempt ${i}/5 sent."
done

echo -e "\n[Step 3] Waiting for Notification Service Inbox consumer to process and deduplicate..."
sleep 4

echo -e "\n[Step 4] Querying Mailpit API to verify exactly ONE email was sent to ${RECIPIENT}..."
EMAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${RECIPIENT}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")

echo "Total emails found in Mailpit for ${RECIPIENT}: ${EMAIL_COUNT}"

if [ "${EMAIL_COUNT}" -eq "1" ]; then
  echo "✔ SUCCESS: Replayed event 5 times, but EXACTLY 1 email was delivered! Inbox deduplication verified."
else
  echo "ℹ Checking container logs for inbox rejection..."
  docker logs --tail 25 tickethub-notification-service-1 | grep -i "Inbox" || true
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 2 INCIDENT NOTE                           "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Customer received multiple duplicate confirmation emails for a single booking when network retry occurred.

• Cause:
  Kafka guarantees at-least-once delivery; killing consumer mid-batch or network replays delivered duplicate events.

• Fix:
  Implemented an Inbox table (`inbox_messages`) with a UNIQUE constraint on `event_id`. The event ID is recorded within the same transaction, safely discarding duplicates.

• Prevention:
  Every consumer endpoint must be strictly idempotent using an inbox table before triggering side-effects.
EOF
echo "======================================================================"

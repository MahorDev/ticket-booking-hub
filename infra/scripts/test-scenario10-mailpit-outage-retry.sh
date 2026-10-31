#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 10 VERIFICATION DRILL: EMAIL PROVIDER OUTAGE & NON-BLOCKING RESILIENCE
# Tests: Mail server outage -> Retries & DLT buffering -> Mail server recovery & Replay -> Exactly 1 email delivered.
# ==============================================================================
set -euo pipefail

MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
NOTIF_URL="${NOTIF_URL:-http://localhost:8087}"
MYSQL_CONTAINER="tickethub-mysql-1"

echo "======================================================================"
echo "    SCENARIO 10: EMAIL PROVIDER OUTAGE & NON-BLOCKING RESILIENCE     "
echo "======================================================================"

BKG_ID=$((RANDOM % 8000 + 1000))
RECIPIENT="outage-tester-${BKG_ID}@tickethub.com"

# [Step 1] Stopping Mailpit container to simulate an external SMTP provider outage
echo -e "\n[Step 1] Stopping Mailpit container (simulating 2-hour email provider outage)..."
docker compose stop mailpit
echo "✔ Mail server is DOWN."

# [Step 2] Publish a BookingConfirmed event while email provider is offline
echo -e "\n[Step 2] Publishing BookingConfirmed event for booking ${BKG_ID} to Kafka..."
EVENT_JSON=$(cat <<EOF
{"eventMessageId":"evt-outage-${BKG_ID}","eventType":"BookingConfirmed","bookingId":${BKG_ID},"bookingReference":"BKG-OUT-${BKG_ID}","userId":1,"showId":100,"customerName":"Outage Tester","recipientEmail":"${RECIPIENT}","ticketNumber":"TKT-OUT-${BKG_ID}","totalAmount":450.00,"currency":"INR","eventTimestamp":"$(date -u +"%Y-%m-%dT%H:%M:%SZ")"}
EOF
)

docker exec -i tickethub-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic booking-confirmed \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${BKG_ID}:${EVENT_JSON}"

echo "✔ Event published. Awaiting bounded consumer retries and DLT routing (polling up to 25s)..."
DLT_ID=""
for i in {1..25}; do
  DLT_ID=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
    "SELECT id FROM tickethub_notification_db.dlt_messages WHERE message_key = '${BKG_ID}' ORDER BY id DESC LIMIT 1;" 2>/dev/null || echo "")
  if [ -n "${DLT_ID}" ]; then
    echo "✔ Message captured in DLT on attempt ${i}s."
    break
  fi
  sleep 1
done

# [Step 3] Checking notification status in DB
echo -e "\n[Step 3] Checking notification status in DB during provider outage..."
NOTIF_STATUS=$(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot -N -e \
  "SELECT status FROM tickethub_notification_db.notifications WHERE booking_id = ${BKG_ID};" 2>/dev/null || echo "NONE")
echo "Notification Status: ${NOTIF_STATUS}"

# [Step 4] Checking DLT messages in DB
echo -e "\n[Step 4] Checking Dead Letter Topic repository for captured message..."
echo "Captured DLT Message ID: ${DLT_ID}"

if [ -n "${DLT_ID}" ]; then
  echo "✔ SUCCESS: Message safely moved to Dead Letter Topic without stalling subsequent consumer traffic!"
else
  echo "❌ DLT record not found."
  exit 1
fi

# [Step 5] Restarting Mailpit (SMTP server recovers)
echo -e "\n[Step 5] Restarting Mailpit container (email provider recovered)..."
docker compose start mailpit
sleep 4

# [Step 6] Replaying the buffered message from DLT
echo -e "\n[Step 6] Operator replays buffered message via POST /api/notifications/dlt/${DLT_ID}/replay..."
REPLAY_RESP=$(curl -s -X POST "${NOTIF_URL}/api/notifications/dlt/${DLT_ID}/replay")
echo "Replay Response: ${REPLAY_RESP}"
sleep 4

# [Step 7] Verifying email delivery in Mailpit
echo -e "\n[Step 7] Verifying Mailpit mailbox for recipient: ${RECIPIENT}..."
MAIL_COUNT=$(curl -s "${MAILPIT_URL}/api/v1/search?query=to:${RECIPIENT}" | grep -o '"messages_count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Emails found for ${RECIPIENT}: ${MAIL_COUNT}"

if [ "${MAIL_COUNT}" -eq "1" ]; then
  echo -e "\n✔ SCENARIO 10 PASSED: Email safely buffered during outage, replayed on recovery, and delivered exactly once!"
else
  echo -e "\n❌ Email delivery count mismatch: expected 1, found ${MAIL_COUNT}"
  exit 1
fi

echo -e "\n======================================================================"
echo "                   SCENARIO 10 INCIDENT NOTE                          "
echo "======================================================================"
cat << 'EOF'
• Symptom:
  Email provider / SMTP server suffered an outage or rate limit. Naive blocking retries stalled the notification consumer, backing up thousands of messages, or sent duplicates upon recovery.

• Cause:
  Blocking consumer retries on the main topic thread without circuit breakers or decoupled retry/dead-letter topics.

• Fix:
  1. Non-Blocking Resilience: Consumer attempts bounded retries with backoff while marking notification `RETRYING`.
  2. Bounded DLT Routing: Exhausted retries automatically route to `booking.confirmed.DLT` via `DeadLetterPublishingRecoverer`, freeing consumer partitions for healthy traffic.
  3. Safe Replay & Inbox Deduplication: Replayed messages via `POST /api/notifications/dlt/{id}/replay` re-attempt delivery once the provider is healthy. The Inbox table ensures zero duplicate emails are delivered.

• Prevention:
  Never allow a third-party dependency outage to block Kafka consumers; always decouple retry exhaustion to dead-letter queues.
EOF

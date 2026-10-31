#!/usr/bin/env bash
# ==============================================================================
# SCENARIO 14: CHAOS DAY - COMPREHENSIVE END-TO-END RESILIENCE DRILL
# Runs all 7 catastrophic failure modes in sequence and verifies auto-healing.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================================================"
echo "    SCENARIO 14: CHAOS DAY - FULL FAILURE DRILL SUITE                 "
echo "======================================================================"

TOTAL_TESTS=7
PASSED_TESTS=0

run_chaos_step() {
  local step_num="$1"
  local step_desc="$2"
  local step_script="$3"

  echo -e "\n----------------------------------------------------------------------"
  echo "▶ CHAOS TEST [${step_num}/${TOTAL_TESTS}]: ${step_desc}"
  echo "----------------------------------------------------------------------"

  if bash "${SCRIPT_DIR}/${step_script}"; then
    echo "✔ PASSED CHAOS STEP ${step_num}: ${step_desc}"
    PASSED_TESTS=$((PASSED_TESTS + 1))
  else
    echo "❌ FAILED CHAOS STEP ${step_num}: ${step_desc}"
    exit 1
  fi
}

# 1. Kill Booking during confirmation
run_chaos_step 1 "Kill Booking Service during payment capture (Scenario 4 Saga)" "test-scenario4-saga-payment-booking.sh"

# 2. Stop Kafka while a booking is confirmed
run_chaos_step 2 "Stop Kafka broker during booking commit (Scenario 3 Outbox Relay)" "test-scenario3-outbox-relay.sh"

# 3. Send duplicate and reordered Razorpay webhooks
run_chaos_step 3 "Duplicate and reordered Razorpay webhooks (Scenario 9 Webhook Idempotency)" "test-scenario9-webhook-idempotency.sh"

# 4. Pay a booking that has already expired
run_chaos_step 4 "Pay for an expired seat hold (Scenario 5 Saga Compensations)" "test-scenario5-compensations-refunds.sh"

# 5. Stop Notification and the mail server
run_chaos_step 5 "Stop external SMTP email provider (Scenario 10 Non-blocking Retry & DLT)" "test-scenario10-mailpit-outage-retry.sh"

# 6. Publish a poison message
run_chaos_step 6 "Publish corrupt poison message to consumer (Scenario 1 DLT Isolation)" "test-scenario1-bad-message-dlt.sh"

# 7. Replay events to a new consumer group
run_chaos_step 7 "Replay historical events without side-effect duplicates (Scenario 12 Replay)" "test-scenario12-event-replay.sh"

echo -e "\n======================================================================"
echo "          CHAOS DAY DRILL COMPLETE: ${PASSED_TESTS}/${TOTAL_TESTS} PASSED (100%)              "
echo "======================================================================"
cat << 'EOF'
All 7 failure scenarios successfully recovered without data loss, double-charging,
poison-pill stalling, or duplicate customer communications.
TicketHub Phase 2 distributed system is hardened, consistent, and production-ready!
======================================================================
EOF

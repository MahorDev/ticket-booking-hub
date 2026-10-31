# Operational Runbook: Safe Kafka Event Replay in TicketHub

## Scope & Purpose
When a service database is restored from backup, or a new downstream audit service is introduced, past events stored in Kafka must be replayed to rebuild state **without causing duplicate side-effects (e.g., without sending duplicate emails or re-charging cards)**.

---

## 1. Topic Retention Audit
Kafka retains published events according to topic retention configurations.
To inspect the retention policy of `booking-confirmed`:
```bash
docker exec tickethub-kafka-1 /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server localhost:9092 \
  --entity-type topics \
  --entity-name booking-confirmed \
  --describe
```
* **Default Retention**: `log.retention.hours=168` (7 days / 604,800,000 ms).
* **Operational Implication**: Replay is possible for any event published within the last 7 days. If a state rebuild requires 30 days of data, topic retention must be increased or an event archive (S3 cold storage / CDC sink) must be provisioned.

---

## 2. Safe Replay Procedure

### Step 1: Deploy with Side-Effect Bypass (`REPLAY_MODE=true`)
When spinning up a replay consumer group, configure side-effect disabling flags:
- In `NotificationService`, set environment variable:
  `NOTIFICATION_REPLAY_MODE=true`
  This directs the consumer to record/audit the historical event into the Inbox/audit repository but skips `JavaMailSender` invocation.

### Step 2: Assign a Fresh Consumer Group ID
Never reset offsets on an active production consumer group that is processing live traffic. Instead, assign an isolated audit/recovery group ID:
```yaml
spring:
  kafka:
    consumer:
      group-id: notification-recovery-group-20261031
      auto-offset-reset: earliest
```

### Step 3: Monitor Offset Progression
Monitor the recovery consumer group reading from offset 0 until it catches up to the log end offset:
```bash
docker exec tickethub-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group notification-recovery-group-20261031
```

### Step 4: Decommission Recovery Group
Once `LAG=0` and all historical records are rebuilt in the target database, terminate the recovery consumer instance.
Live consumers continue running unaffected on their dedicated consumer group offsets.

---

## 3. Dry-Run Verification Command
To verify that Kafka holds the historical records and can replay from earliest offset without re-triggering SMTP emails:
```bash
./infra/scripts/test-scenario12-event-replay.sh
```

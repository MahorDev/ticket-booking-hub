# Architecture Decision Note: Saga Orchestration vs Choreography in TicketHub

## Status
**Decided** | Date: 2026-10-31 | Authors: Core Architecture Team

---

## 1. Context & Problem Statement
In TicketHub Phase 2, distributed transactions between Booking and Payment services are managed via a Saga pattern across separate MySQL databases and Kafka messaging.
As the workflow grew to handle confirmations, cancellations, late payments, and refunds across 6 distinct domain events (`PaymentSucceeded`, `PaymentFailed`, `BookingConfirmed`, `BookingRejected`, `BookingCancelled`, `PaymentRefunded`), support engineers and operators face visibility and debuggability challenges:
> *"Where is booking BKG-1042 stuck and why?"*

Two architectural styles address this:
1. **Choreographed Saga**: Services listen to Kafka events and autonomously publish follow-up events.
2. **Orchestrated Saga**: A centralized coordinator process (e.g. Temporal, Camunda, or a custom Booking Saga Orchestrator) commands services through explicit commands (`AuthorizePayment`, `ReleaseSeats`, `IssueRefund`) and maintains an explicit state machine.

---

## 2. Comparison Matrix

| Evaluation Criteria | Choreography (Current) | Orchestration (Alternative) |
| :--- | :--- | :--- |
| **Visibility & State** | ⚠️ Decentralized. Requires aggregating logs, inbox/outbox tables, or dedicated query endpoints. | ✅ Centralized. The orchestrator database holds the canonical state machine of every transaction. |
| **Service Coupling** | ✅ Loose. Services only depend on Kafka topics and domain event contracts; no direct orchestrator dependency. | ⚠️ Tighter. Participating services must understand command interfaces and acknowledge back to the orchestrator. |
| **Complexity** | ✅ Low infrastructure overhead. Runs on existing Kafka brokers and local Outbox tables without extra engines. | ⚠️ High infrastructure overhead. Requires running, clustering, and monitoring dedicated workflow engines (e.g., Temporal). |
| **Failure Handling** | ✅ Independent. If Booking Service is temporarily down, Payment still captures money safely and Outbox buffers events. | ⚠️ Central point of failure. If the orchestrator fails or experiences DB lock contention, all active customer checkouts pause. |
| **Cognitive Load** | ⚠️ Harder for new developers to visualize the whole end-to-end flow without reading multiple consumer classes. | ✅ Easy to read a single workflow class (e.g., `BookingSagaWorkflow.java`). |

---

## 3. Decision for TicketHub

### **Decision: Maintain Choreography with Observable State Querying (`GET /api/bookings/{id}/saga-state`)**

TicketHub will **retain Event-Driven Choreography** as the foundational saga pattern, augmented with an **Observable State Machine Query API** in `BookingService`.

### Rationale:
1. **Flash-Sale Resilience**: During peak ticket on-sales (e.g. 50,000 requests/sec for a stadium concert), an orchestration engine's database becomes a catastrophic contention bottleneck. Choreography with partition keys (`bookingId`) partitions traffic across Kafka brokers with sub-millisecond publishing latency.
2. **Fault Isolation**: If any downstream service degrades (e.g. Notification Service or Payment refund worker), the choreographic outbox safely spools messages without failing upstream seat reservations or checkout completions.
3. **Solved the Visibility Gap**: By introducing `GET /api/bookings/{id}/saga-state`, TicketHub provides instant, 360-degree operator visibility into aggregate status, version, expiration timers, and transition history—giving teams the visibility benefits of orchestration without the architectural weight or single point of failure.

---

## 4. Consequences
- **Positive**: Zero new infrastructure components needed; linear horizontal scalability during flash sales; high availability under partial network partitions.
- **Negative**: Traceability requires distributed trace correlation (`traceId` / `correlationId`) and inspection via Grafana/Jaeger or the `saga-state` endpoint.

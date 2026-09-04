# ADR-001: Synchronous vs Asynchronous Integration

- Status: Accepted
- Date: 2026-09-04

## Context

The order processing flow requires communication with multiple enterprise systems.

Two different requirements exist:

1. Some operations require an immediate response.
2. Other operations can be processed independently after the original API request has been accepted.

Using synchronous communication for every integration would create strong temporal coupling between systems.

For example, if the ERP were temporarily unavailable, the complete order creation process could fail even when the order could safely be processed later.

On the other hand, making every interaction asynchronous would unnecessarily complicate operations that require an immediate response.

---

## Decision

The architecture will use a hybrid integration approach.

### Synchronous communication

REST will be used for interactions requiring an immediate response.

This includes:

- E-Commerce → Order API
- Order API → Integration Service
- Integration Service → CRM

### Asynchronous communication

Event-driven communication using Apache Kafka will be used for downstream order processing.

Flow:

Integration Service → Kafka → ERP

The Integration Service publishes an order event and does not wait for the ERP to complete processing.

---

## Rationale

This approach provides a balance between simplicity and resilience.

Synchronous APIs are appropriate when the caller requires an immediate result.

Asynchronous messaging is appropriate when:

- Immediate processing is not required
- Systems should remain loosely coupled
- Temporary downstream failures must not block upstream systems
- Independent scaling is desirable
- Events may need to be replayed

---

## Consequences

### Positive

- Reduced coupling between Integration Service and ERP
- ERP outages do not directly prevent event publication
- Improved scalability
- Better failure isolation
- Possibility of replaying events
- Independent evolution of producers and consumers

### Negative

- Increased infrastructure complexity
- Eventual consistency
- More complex troubleshooting
- Duplicate message handling may be required
- Message ordering must be considered
- Monitoring asynchronous flows requires additional observability

---

## Alternatives Considered

### Fully synchronous integration

Integration Service → REST → ERP

Rejected because ERP availability would directly affect the order processing flow.

### Fully asynchronous integration

All system interactions through messaging.

Rejected because some operations require immediate responses and the additional complexity would not provide enough benefit.

---

## Decision Outcome

A hybrid architecture combining REST APIs and event-driven messaging will be used.

Further ADRs will define:

- Messaging technology
- Retry strategy
- Dead-letter handling
- Idempotency
- Observability

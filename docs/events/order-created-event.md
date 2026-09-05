# Order Created Event

## Overview

The `ORDER_CREATED` event represents an order that has been accepted by the integration platform and is ready for asynchronous downstream processing.

The event is published by the Integration Service to Apache Kafka and consumed by the ERP system.

This contract follows an event-driven integration approach and is designed to support:

- Loose coupling
- Asynchronous processing
- Traceability
- Idempotent consumption
- Event versioning
- Failure isolation

---

## Event Flow

```text
Order API
    ↓
Integration Service
    ↓
orders.events.v1
    ↓
Apache Kafka
    ↓
ERP
```

The Integration Service produces the event after the required synchronous processing has completed.

The ERP consumes the event asynchronously.

---

## Kafka Topic

`orders.events.v1`

The topic contains events related to the order domain.

The initial implementation includes:

`ORDER_CREATED`

Additional order lifecycle events may be introduced in future versions.

---

## Kafka Message Key

The Kafka message key will be:

`orderId`

Example:

```text
33c58292-f36e-4ca3-a421-c22da73e93e5
```

Using the `orderId` as the message key ensures that events related to the same order are routed to the same Kafka partition.

This helps preserve ordering for events belonging to the same order.

Kafka guarantees message ordering within a partition, not globally across all partitions.

---

## Event Structure

Example:

```json
{
  "eventId": "90b9f215-c19f-4abc-a2c4-2cc7220c010d",
  "eventType": "ORDER_CREATED",
  "eventVersion": "1.0",
  "occurredAt": "2026-09-05T07:15:00Z",
  "correlationId": "b37166f4-8a39-4ffd-9599-c42ca48b83d0",
  "source": "integration-service",
  "data": {
    "orderId": "33c58292-f36e-4ca3-a421-c22da73e93e5",
    "externalOrderId": "WEB-2026-000123",
    "customerId": "CUST-10045",
    "currency": "EUR",
    "items": [
      {
        "productId": "PROD-001",
        "quantity": 2,
        "unitPrice": 29.95
      },
      {
        "productId": "PROD-002",
        "quantity": 1,
        "unitPrice": 15.50
      }
    ],
    "shippingAddress": {
      "addressLine1": "123 Example Street",
      "city": "Seville",
      "postalCode": "41001",
      "country": "ES"
    }
  }
}
```

---

## Event Metadata

### `eventId`

Unique identifier for the individual event.

Example:

`90b9f215-c19f-4abc-a2c4-2cc7220c010d`

The identifier can be used by consumers to detect duplicate event delivery.

Two deliveries of the same event will contain the same `eventId`.

---

### `eventType`

Identifies the business event represented by the message.

For this contract:

`ORDER_CREATED`

Future events may include other order lifecycle changes.

---

### `eventVersion`

Identifies the version of the event contract.

Initial version:

`1.0`

The version allows the contract to evolve while maintaining compatibility with existing consumers.

---

### `occurredAt`

Timestamp indicating when the business event occurred.

The timestamp uses ISO 8601 UTC format.

Example:

`2026-09-05T07:15:00Z`

This value represents when the event occurred, not when the consumer processed it.

---

### `correlationId`

Identifier used to trace the complete integration transaction.

The same correlation identifier is propagated across:

```text
E-Commerce
    ↓
Order API
    ↓
Integration Service
    ↓
Kafka
    ↓
ERP
```

Example:

`b37166f4-8a39-4ffd-9599-c42ca48b83d0`

This identifier should be included in application logs and observability data.

---

### `source`

Identifies the component that produced the event.

For this event:

`integration-service`

---

## Event Data

The `data` object contains the business information required by the ERP to process the order.

---

### `orderId`

Internal identifier assigned to the order.

Example:

`33c58292-f36e-4ca3-a421-c22da73e93e5`

This value is also used as the Kafka message key.

---

### `externalOrderId`

Identifier assigned by the source e-commerce platform.

Example:

`WEB-2026-000123`

This allows the internal order to be correlated with the source system.

---

### `customerId`

Identifier of the customer associated with the order.

Example:

`CUST-10045`

Only the customer information required by the ERP is included.

Customer information that is not required by the consumer should not be propagated unnecessarily.

---

### `currency`

ISO 4217 currency code associated with the order.

Example:

`EUR`

---

### `items`

Collection of products included in the order.

Each item contains:

- Product identifier
- Quantity
- Unit price

---

### `shippingAddress`

Shipping destination associated with the order.

The initial contract contains:

- Address line
- City
- Postal code
- Country

---

## Idempotency

Kafka consumers must assume that an event may be delivered more than once.

The ERP consumer must therefore process events idempotently.

The `eventId` will be used to identify previously processed events.

Example:

```text
Event received

eventId = EVENT-001

ERP processes event
        ↓
eventId stored as processed
```

If the same event is delivered again:

```text
eventId = EVENT-001

        ↓

Already processed

        ↓

Do not create the order again
```

The exact persistence and idempotency strategy will be defined in a dedicated Architecture Decision Record.

---

## Event Ordering

The Kafka message key is the `orderId`.

Events belonging to the same order should therefore be routed to the same Kafka partition.

Example:

```text
ORDER_CREATED
orderId = ORDER-123
        ↓

ORDER_PROCESSING
orderId = ORDER-123
        ↓

ORDER_COMPLETED
orderId = ORDER-123
```

This allows ordering to be preserved for a specific order.

The architecture does not require global ordering between unrelated orders.

---

## Event Versioning

The initial event version is:

`1.0`

Backward-compatible changes may include adding optional fields.

Breaking changes must be evaluated carefully and may require a new contract version.

Consumers should not depend on undocumented fields.

A dedicated Architecture Decision Record may be introduced when the versioning strategy evolves.

---

## Data Minimization

Events should contain the information required by their consumers but should avoid unnecessary data propagation.

For example, the source API contains the customer email address.

The `ORDER_CREATED` event does not include the email because the ERP does not require it for the initial business scenario.

This reduces:

- Data exposure
- Coupling between systems
- Contract complexity
- Unnecessary propagation of customer information

---

## Error Handling

If the ERP cannot successfully process an event, the event must not be silently discarded.

The architecture will progressively introduce:

- Consumer retries
- Retry backoff
- Dead-letter handling
- Structured error logging
- Correlation-based troubleshooting

The final retry and dead-letter strategy will be documented separately.

---

## Schema

The formal JSON Schema associated with this event is located at:

`docs/events/order-created-event.schema.json`

---

## Related Documentation

Architecture overview:

`docs/architecture.md`

REST API contract:

`docs/api/order-api-contract.md`

OpenAPI specification:

`docs/api/order-api.yaml`

Architecture decisions:

`docs/decisions/`

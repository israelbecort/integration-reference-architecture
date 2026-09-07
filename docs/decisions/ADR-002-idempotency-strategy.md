# ADR-002: Order Creation Idempotency Strategy

- Status: Accepted
- Date: 2026-09-05

## Context

The Order API receives order creation requests from an external e-commerce system.

Clients may retry HTTP requests because of:

- Network failures
- Client-side timeouts
- Temporary connectivity problems
- Uncertainty about whether a previous request was successfully processed

Without an idempotency mechanism, retrying the same request could create duplicate orders.

Example:

```text
POST /api/v1/orders
        ↓
Order created
        ↓
Response is lost because of a network problem
        ↓
Client retries the request
        ↓
A second order could be created
```

Duplicate order creation is not acceptable.

The API therefore requires an `Idempotency-Key` header for every order creation request.

---

## Decision

The Order API will implement persistent idempotency using:

- `Idempotency-Key`
- Request fingerprint
- PostgreSQL persistence
- Database uniqueness constraints

The `Idempotency-Key` uniquely identifies an order creation operation.

The API also calculates a SHA-256 fingerprint from the relevant request data.

Both values are stored together with the created order.

Example:

```text
Idempotency-Key
9fe2d76a-268f-470b-a47f-a3895ab3a189

        ↓

Request fingerprint
321ab9f891df4fffcfc9fce288ddf2d79ae702e8f33e021fe57885507219e30a

        ↓

Order
b243423f-8047-49ea-b79f-50027400c022
```

---

## Request Fingerprint

A deterministic fingerprint is calculated from the business-relevant request content using SHA-256.

The fingerprint allows the platform to distinguish between:

1. A legitimate retry of the same request.
2. Incorrect reuse of the same idempotency key with different request data.

The fingerprint is stored as:

`request_hash`

The SHA-256 value is represented as a 64-character hexadecimal string.

---

## Idempotency Scenarios

### New Idempotency Key

If the `Idempotency-Key` does not exist:

```text
Idempotency-Key = ABC
Request = A

        ↓

Create new order

        ↓

Persist:

ABC
Request Hash A
ORDER-123
```

The API returns:

`202 Accepted`

---

### Same Key and Same Request

If the same key is received again with the same request fingerprint:

```text
Idempotency-Key = ABC
Request = A

        ↓

Existing operation found

        ↓

Request hash matches

        ↓

Return existing order
```

No new order is created.

The API returns:

`202 Accepted`

with the same:

- `orderId`
- `externalOrderId`
- `status`
- `acceptedAt`

This allows clients to safely retry requests.

---

### Same Key and Different Request

If the same key is reused with different request content:

```text
First request

Idempotency-Key = ABC
Request = A

        ↓

ORDER-123
```

Later:

```text
Idempotency-Key = ABC
Request = B

        ↓

Request hash does not match

        ↓

409 Conflict
```

The API must not create another order.

The response uses:

`ORD-CONFLICT-001`

---

### Same External Order with Different Idempotency Key

The source e-commerce order identifier is also unique.

Example:

```text
externalOrderId = WEB-2026-000123
Idempotency-Key = ABC

        ↓

Order created
```

A later request:

```text
externalOrderId = WEB-2026-000123
Idempotency-Key = XYZ

        ↓

409 Conflict
```

This provides an additional protection against duplicate source orders.

---

## Database Constraints

The database contains unique constraints for:

- `idempotency_key`
- `external_order_id`

These constraints provide a final consistency boundary.

Application-level existence checks alone are not sufficient because concurrent requests may produce a race condition.

Example:

```text
Request A                  Request B
    │                          │
Check key                  Check key
    │                          │
Not found                  Not found
    │                          │
Insert                     Insert
    │                          │
Success              Unique constraint violation
```

The database uniqueness constraint prevents duplicate persistence even when requests are processed concurrently.

Database integrity violations related to these constraints are mapped to:

`409 Conflict`

---

## Correlation ID and Idempotency Key

The `Idempotency-Key` and `X-Correlation-Id` have different responsibilities.

### Idempotency-Key

Identifies the business operation.

Multiple retries of the same order creation request use the same idempotency key.

### X-Correlation-Id

Identifies and traces an individual integration request.

A retry may use a different correlation identifier while still representing the same idempotent business operation.

Example:

```text
Request 1

Idempotency-Key = ABC
Correlation-Id = 111

        ↓

ORDER-123
```

Retry:

```text
Request 2

Idempotency-Key = ABC
Correlation-Id = 222

        ↓

Same ORDER-123
```

The API response for the retry contains the current request correlation identifier.

The persisted order retains the correlation identifier associated with the original order creation.

---

## Persistence

The initial persistence model stores:

- `order_id`
- `external_order_id`
- `idempotency_key`
- `request_hash`
- `status`
- `correlation_id`
- `accepted_at`

Example:

```text
order_id          = b243423f-8047-49ea-b79f-50027400c022
external_order_id = WEB-2026-000123
idempotency_key   = 9fe2d76a-268f-470b-a47f-a3895ab3a189
status            = ACCEPTED
correlation_id    = 11111111-1111-4111-8111-111111111111
```

The complete order business payload is not yet persisted in the initial implementation.

Persistence requirements may evolve as additional order operations are introduced.

---

## Consequences

### Positive

- Clients can safely retry order creation requests.
- Duplicate orders are prevented.
- Idempotency survives application restarts.
- Duplicate protection does not depend on application memory.
- Incorrect reuse of an idempotency key can be detected.
- Database constraints provide protection against concurrent requests.
- Source-system order identifiers are also protected from duplication.

### Negative

- Additional database access is required for every order creation request.
- Request fingerprint generation adds implementation complexity.
- The fingerprint strategy must remain deterministic.
- Changes to the request contract may require reviewing the fingerprint strategy.
- Idempotency records require a lifecycle and retention strategy in long-lived production systems.

---

## Alternatives Considered

### In-Memory Idempotency

Store previously used idempotency keys in application memory.

Rejected because:

- Data would be lost when the application restarts.
- It would not work correctly across multiple application instances.
- It would not provide durable duplicate protection.

---

### Idempotency-Key Without Request Fingerprint

Store only the `Idempotency-Key`.

Rejected because the platform would not be able to distinguish between:

```text
Same key + same request
```

and:

```text
Same key + different request
```

This could hide incorrect client behavior.

---

### External Order ID Only

Use `externalOrderId` as the only duplicate protection mechanism.

Rejected because the source business identifier and the HTTP retry mechanism represent different concerns.

Both protections are useful and complementary.

---

## Implementation Notes

The current implementation uses:

- Spring Boot
- Spring Data JPA
- PostgreSQL
- SHA-256 request fingerprint
- Database unique constraints
- Transactional order creation

The database currently uses Hibernate schema generation during the initial development phase.

Schema management will later be migrated to version-controlled database migrations.

---

## Future Considerations

The following aspects may be introduced later:

- Idempotency record expiration
- Configurable retention period
- Dedicated idempotency persistence model
- Concurrency stress tests
- Metrics for duplicate requests
- Distributed tracing
- Database migration management
- Testcontainers for isolated database integration tests

---

## Decision Outcome

Order creation uses persistent idempotency based on:

```text
Idempotency-Key
        +
Request SHA-256 fingerprint
        +
PostgreSQL unique constraints
```

A legitimate retry returns the previously created order without creating a duplicate.

Reusing the same idempotency key with different request data returns:

`409 Conflict`
# Order API Contract

## Overview

The Order API is the entry point for order creation within the enterprise integration platform.

It receives orders from the fictional e-commerce system through a synchronous REST interface.

The API follows a contract-first approach and is responsible for:

- Request validation
- Correlation identifier handling
- Persistent idempotent order creation
- Initial order persistence
- Synchronous handoff to the Integration Service
- Consistent HTTP responses
- Consistent error responses

The Order API persists a new order before invoking the Integration Service.

A successfully processed request therefore follows this high-level flow:

```text
E-Commerce
    ↓
Order API
    ↓
Persist ACCEPTED
    ↓
Integration Service
    ↓
Processing accepted
    ↓
Persist PROCESSING
    ↓
202 Accepted
```

Downstream ERP processing will later continue asynchronously through Kafka.

For this reason, successful order creation requests return:

`202 Accepted`

rather than:

`201 Created`

The response confirms that the platform has accepted the order for continued processing.

It does not mean that all downstream business processing has completed.

---

# Create Order

## Endpoint

`POST /api/v1/orders`

## Content Type

`application/json`

---

# Request Headers

## `X-Correlation-Id`

Optional.

Used to trace an individual request across the integration platform.

The value must be a valid UUID.

Example:

```text
X-Correlation-Id: b37166f4-8a39-4ffd-9599-c42ca48b83d0
```

If the caller provides the header, the Order API propagates the same correlation identifier.

If the caller does not provide the header, the Order API generates a new correlation identifier.

The effective correlation identifier is propagated to the Integration Service.

For successful requests, the effective correlation identifier is returned both:

- In the response header
- In the response body

Example response header:

```text
X-Correlation-Id: b37166f4-8a39-4ffd-9599-c42ca48b83d0
```

---

## `Idempotency-Key`

Required.

Used to make order creation safe to retry.

The value must be a valid UUID.

Example:

```text
Idempotency-Key: 9fe2d76a-268f-470b-a47f-a3895ab3a189
```

The same idempotency key must be reused when retrying the same order creation operation.

The idempotency key must not be reused for a different request.

The `Idempotency-Key` belongs to the public Order API contract.

It is not propagated to the Integration Service.

After an order is created, the internal `orderId` becomes the stable identity used by the integration layer.

---

# Request Body

Example:

```json
{
  "externalOrderId": "WEB-2026-000123",
  "customer": {
    "customerId": "CUST-10045",
    "email": "customer@example.com"
  },
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
  "currency": "EUR",
  "shippingAddress": {
    "addressLine1": "123 Example Street",
    "city": "Seville",
    "postalCode": "41001",
    "country": "ES"
  }
}
```

---

# Request Fields

## `externalOrderId`

Required.

Identifier assigned by the source e-commerce platform.

Requirements:

- Must not be empty
- Maximum length: 100 characters
- Must be unique across orders

Example:

```text
WEB-2026-000123
```

---

## `customer`

Required.

Contains the customer information required during order creation.

---

## `customer.customerId`

Optional.

Identifier of the customer in the source system.

For registered customers, this field contains the source platform
customer identifier.

For guest orders, this field may be null or omitted because the
source platform may not have a registered customer identity.

Requirements:

- When provided, must not be empty or blank
- Maximum length: 100 characters

Example:

```text
CUST-10045
```

---

## `customer.email`

Required.

Customer email address.

Requirements:

- Must not be empty
- Must contain a valid email format
- Maximum length: 254 characters

Example:

```text
customer@example.com
```

---

## `items`

Required.

Collection of products included in the order.

Requirements:

- Must contain at least one item

---

## `items[].productId`

Required.

Identifier of the product.

Requirements:

- Must not be empty
- Maximum length: 100 characters

Example:

```text
PROD-001
```

---

## `items[].quantity`

Required.

Number of product units.

Requirements:

- Must be an integer
- Must be greater than zero

Example:

```text
2
```

---

## `items[].unitPrice`

Required.

Price per product unit.

Requirements:

- Must be greater than or equal to `0.01`

Example:

```text
29.95
```

---

## `currency`

Required.

ISO 4217 currency code.

Requirements:

- Exactly three uppercase alphabetic characters

Examples:

```text
EUR
USD
GBP
```

---

## `shippingAddress`

Required.

Contains the shipping destination associated with the order.

---

## `shippingAddress.addressLine1`

Required.

Requirements:

- Must not be empty
- Maximum length: 200 characters

Example:

```text
123 Example Street
```

---

## `shippingAddress.addressLine2`

Optional.

Maximum length:

```text
200 characters
```

Example:

```text
Apartment 4B
```

---

## `shippingAddress.city`

Required.

Requirements:

- Must not be empty
- Maximum length: 100 characters

Example:

```text
Seville
```

---

## `shippingAddress.postalCode`

Required.

Requirements:

- Must not be empty
- Maximum length: 20 characters

Example:

```text
41001
```

---

## `shippingAddress.country`

Required.

ISO 3166-1 alpha-2 country code.

Requirements:

- Exactly two uppercase alphabetic characters

Examples:

```text
ES
FR
DE
```

---

# Successful Processing

## New Order

When the request is valid and the `Idempotency-Key` has not been used before, the Order API:

1. Creates the internal order.
2. Persists it with status `ACCEPTED`.
3. Commits the database transaction.
4. Calls the Integration Service using the internal `orderId`.
5. Propagates the effective `X-Correlation-Id`.
6. Receives confirmation that integration processing has been accepted.
7. Updates the persisted order to `PROCESSING`.
8. Returns `202 Accepted`.

The HTTP call to the Integration Service is deliberately executed outside the database transaction used to create the order.

This prevents the Order API from keeping a database transaction open while waiting for a remote dependency.

Response:

`202 Accepted`

Example:

```json
{
  "orderId": "b243423f-8047-49ea-b79f-50027400c022",
  "externalOrderId": "WEB-2026-000123",
  "status": "PROCESSING",
  "correlationId": "11111111-1111-4111-8111-111111111111",
  "acceptedAt": "2026-09-05T13:40:24.946179Z"
}
```

Response header:

```text
X-Correlation-Id: 11111111-1111-4111-8111-111111111111
```

The `acceptedAt` value represents when the order was originally persisted and accepted by the Order API.

It does not represent when the Integration Service accepted the processing request.

---

# Idempotent Retry

A client may retry the same request using the same `Idempotency-Key`.

The behavior depends on the current persisted order state.

## Retry When the Order Is Already `PROCESSING`

Example:

```text
First request

Idempotency-Key = ABC
Request = A

        ↓

ORDER-123
status = PROCESSING
```

If the client retries:

```text
Idempotency-Key = ABC
Request = A

        ↓

Existing ORDER-123
status = PROCESSING
```

The platform:

- Does not create another order
- Does not invoke the Integration Service again
- Returns the existing order

Response:

`202 Accepted`

The response preserves the original:

- `orderId`
- `externalOrderId`
- `acceptedAt`

The response uses the correlation identifier associated with the current HTTP request.

Example:

```json
{
  "orderId": "b243423f-8047-49ea-b79f-50027400c022",
  "externalOrderId": "WEB-2026-000123",
  "status": "PROCESSING",
  "correlationId": "22222222-2222-4222-8222-222222222222",
  "acceptedAt": "2026-09-05T13:40:24.946179Z"
}
```

---

## Retry When the Order Is Still `ACCEPTED`

An order may remain in `ACCEPTED` when:

1. The order was successfully persisted.
2. The transaction was committed.
3. The synchronous handoff to the Integration Service failed before the order could transition to `PROCESSING`.

Example:

```text
Order API
    ↓
Persist ORDER-123
status = ACCEPTED
    ↓
COMMIT
    ↓
Integration Service unavailable
    ↓
Processing handoff fails
```

The persisted order is not deleted or rolled back.

If the client later retries using:

```text
Same Idempotency-Key
+
Same request
```

the Order API finds the existing `ACCEPTED` order and may retry the synchronous handoff to the Integration Service.

If the retry succeeds:

```text
ACCEPTED
    ↓
Integration Service accepts processing
    ↓
PROCESSING
```

The same internal `orderId` and original `acceptedAt` are preserved.

This recovery behavior is one of the reasons idempotency is persisted rather than held only in application memory.

---

# Idempotency Semantics

The API uses both:

```text
Idempotency-Key
```

and a deterministic request fingerprint.

The request fingerprint is calculated using SHA-256 from the business-relevant request content.

This allows the platform to determine whether an incoming request is:

- A legitimate retry
- An incorrect reuse of an existing idempotency key

---

## Scenario 1 — New Key

```text
Idempotency-Key = ABC
Request = A

        ↓

New order created as ACCEPTED

        ↓

Integration Service accepts processing

        ↓

Order becomes PROCESSING

        ↓

202 Accepted
```

---

## Scenario 2 — Same Key and Same Request, Already Processing

```text
Idempotency-Key = ABC
Request = A

        ↓

Existing operation found

        ↓

Request fingerprint matches

        ↓

Existing order status = PROCESSING

        ↓

Do not invoke Integration Service again

        ↓

Return existing order

        ↓

202 Accepted
```

No duplicate order is created.

No duplicate downstream processing request is initiated by the Order API.

---

## Scenario 3 — Same Key and Same Request, Still Accepted

```text
Idempotency-Key = ABC
Request = A

        ↓

Existing operation found

        ↓

Request fingerprint matches

        ↓

Existing order status = ACCEPTED

        ↓

Retry Integration Service handoff
```

If the handoff succeeds:

```text
ACCEPTED
    ↓
PROCESSING
    ↓
202 Accepted
```

The original order remains the same business operation.

---

## Scenario 4 — Same Key and Different Request

Existing operation:

```text
Idempotency-Key = ABC
Request = A
```

Later:

```text
Idempotency-Key = ABC
Request = B

        ↓

Request fingerprint differs

        ↓

409 Conflict
```

The platform rejects the request.

Error code:

```text
ORD-CONFLICT-001
```

---

## Scenario 5 — Same External Order ID with Different Key

First request:

```text
externalOrderId = WEB-2026-000123
Idempotency-Key = ABC

        ↓

Order created
```

Later:

```text
externalOrderId = WEB-2026-000123
Idempotency-Key = XYZ

        ↓

409 Conflict
```

The source order identifier must not be associated with multiple internal orders.

Error code:

```text
ORD-CONFLICT-001
```

---

# Order Status

The order lifecycle currently defines the following states.

## `ACCEPTED`

The order has been validated and persisted by the Order API.

The Integration Service has not yet successfully accepted the processing request.

An order may temporarily remain in this state when the synchronous handoff to the Integration Service fails.

---

## `PROCESSING`

The Integration Service has accepted the order for integration processing.

This does not mean that all downstream systems have completed their work.

Future processing may still include:

```text
Integration Service
        ↓
CRM
        ↓
Kafka
        ↓
ERP
```

---

## `COMPLETED`

The order has been successfully processed.

This state is part of the planned lifecycle and will become relevant when downstream completion handling is introduced.

---

## `FAILED`

The order could not be processed successfully.

This state is part of the planned lifecycle and will become relevant when failure handling and terminal processing outcomes are introduced.

---

# Error Model

The API uses a consistent error representation.

Error responses use:

```text
Content-Type: application/problem+json
```

Example:

```json
{
  "type": "https://example.com/problems/order-validation",
  "title": "Order validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/orders",
  "errorCode": "ORD-VALIDATION-001",
  "correlationId": "b37166f4-8a39-4ffd-9599-c42ca48b83d0",
  "timestamp": "2026-09-05T09:14:53.518186Z"
}
```

When a valid correlation identifier is available, the response also contains:

```text
X-Correlation-Id
```

to support request tracing.

If an incoming `X-Correlation-Id` cannot be parsed as a valid UUID, a valid correlation identifier may not be available for the error response.

---

# HTTP Status Codes

## `202 Accepted`

The request has been accepted for continued processing.

Used for:

- New order creation after the Integration Service accepts the processing request
- Legitimate retry for an order already in `PROCESSING`
- Legitimate retry for an `ACCEPTED` order when the Integration Service handoff succeeds

A legitimate idempotent retry never creates another internal order.

If the persisted order is already `PROCESSING`, the Order API does not invoke the Integration Service again.

---

## `400 Bad Request`

The request cannot be processed because its structure, headers or input data are invalid.

Possible causes include:

- Missing mandatory field
- Invalid email
- Invalid quantity
- Invalid unit price
- Invalid currency
- Invalid country
- Malformed JSON
- Missing `Idempotency-Key`
- Invalid UUID header format

Example error codes:

```text
ORD-VALIDATION-001
ORD-VALIDATION-002
ORD-VALIDATION-003
ORD-VALIDATION-004
```

---

## `409 Conflict`

The request conflicts with an existing order or idempotency constraint.

Possible causes include:

### Same Idempotency Key with Different Request

```text
Idempotency-Key already exists
+
request fingerprint is different
```

### Duplicate External Order

```text
externalOrderId already exists
+
different Idempotency-Key
```

Error code:

```text
ORD-CONFLICT-001
```

Example:

```json
{
  "type": "https://example.com/problems/order-conflict",
  "title": "Order conflict",
  "status": 409,
  "detail": "The Idempotency-Key has already been used with a different request.",
  "instance": "/api/v1/orders",
  "errorCode": "ORD-CONFLICT-001",
  "correlationId": "b37166f4-8a39-4ffd-9599-c42ca48b83d0",
  "timestamp": "2026-09-05T13:45:00Z"
}
```

---

## `422 Unprocessable Entity`

The request is structurally valid but violates a business rule.

Potential future examples include:

- Product is not available for ordering
- Customer is not eligible to place the order
- Order violates a business restriction

Business rule validation will evolve as downstream integrations are introduced.

---

## `503 Service Unavailable`

A required synchronous downstream dependency is temporarily unavailable.

For the current architecture, the primary synchronous dependency of the Order API is the Integration Service.

An important consistency characteristic is that the order may already have been persisted as:

```text
ACCEPTED
```

before this error occurs.

The persisted order is therefore not necessarily rolled back when the downstream handoff fails.

A legitimate retry using the same:

```text
Idempotency-Key
+
request
```

can locate the existing `ACCEPTED` order and attempt the Integration Service handoff again.

Intended error code:

```text
ORD-DEPENDENCY-001
```

---

## `500 Internal Server Error`

An unexpected technical error occurred.

Internal implementation details must not be exposed to API consumers.

Error code:

```text
ORD-INTERNAL-001
```

---

# Correlation and Traceability

Every successfully handled request has an effective correlation identifier.

If the caller provides:

```text
X-Correlation-Id
```

the platform propagates it.

If the caller does not provide one, the Order API generates a new UUID.

The correlation identifier is propagated across the current synchronous boundary:

```text
E-Commerce
    ↓
Order API
    ↓
Integration Service
```

It will progressively also be propagated across:

```text
Integration Service
    ↓
CRM
    ↓
Kafka
    ↓
ERP
```

The correlation identifier will be used in:

- HTTP headers
- API responses
- Application logs
- Internal HTTP communication
- Events
- Metrics
- Distributed tracing

---

# Correlation ID vs Idempotency Key

These identifiers serve different purposes.

## `Idempotency-Key`

Identifies the public business operation being retried.

Example:

```text
Create order operation
        ↓
Idempotency-Key = ABC
```

Retries of the same operation reuse:

```text
ABC
```

The idempotency key is consumed by the Order API and is not propagated as the identity of the internal integration operation.

---

## `orderId`

Identifies the persisted internal order.

After creation, the Integration Service receives:

```text
orderId
```

as the stable internal order identity.

Example:

```text
Idempotency-Key = ABC
        ↓
Order API
        ↓
orderId = ORDER-123
        ↓
Integration Service
```

---

## `X-Correlation-Id`

Identifies and traces an individual request attempt.

Example:

```text
Attempt 1
Correlation-Id = 111

Attempt 2
Correlation-Id = 222
```

Both attempts may belong to:

```text
Idempotency-Key = ABC
```

and therefore resolve to the same:

```text
orderId = ORDER-123
```

---

# Processing Model

The API follows a hybrid synchronous and asynchronous architecture.

## Current Synchronous Responsibilities

The Order API currently performs:

1. HTTP request parsing
2. Header validation
3. Request validation
4. Correlation ID handling
5. Idempotency verification
6. Initial order persistence as `ACCEPTED`
7. Transaction commit
8. Internal request transformation
9. Synchronous HTTP call to the Integration Service
10. Correlation ID propagation
11. Integration response validation
12. Order transition from `ACCEPTED` to `PROCESSING`
13. Final `202 Accepted` response

The database transaction used to create the order is completed before the remote Integration Service call begins.

The transition to `PROCESSING` is persisted in a separate transaction after the Integration Service successfully accepts the processing request.

---

## Current Integration Boundary

```text
Order API
    ↓
POST /internal/v1/orders/{orderId}/process
    ↓
Integration Service
```

The Order API sends:

- `orderId` as the internal operation identity
- `X-Correlation-Id` for request traceability
- Order business data
- Original `acceptedAt` timestamp

The Order API does not propagate:

```text
Idempotency-Key
```

to the Integration Service.

---

## Future Synchronous Responsibilities

The Integration Service will progressively introduce required synchronous enterprise interactions such as:

```text
Integration Service
        ↓
CRM
```

These interactions will introduce additional resilience concerns including:

- Timeouts
- Retry policies
- Circuit breaking
- Dependency error classification

---

## Future Asynchronous Responsibilities

The architecture will later introduce:

```text
Integration Service
        ↓
Kafka
        ↓
ERP
```

ERP processing will therefore not block the original HTTP request.

This is why the API returns:

`202 Accepted`

rather than indicating that all downstream processing has completed.

---

# Transaction Boundaries

The Order API deliberately separates database transactions from remote HTTP communication.

The flow is:

```text
Transaction 1
    ↓
Create or retrieve order
    ↓
Persist ACCEPTED
    ↓
COMMIT

No database transaction
    ↓
HTTP call to Integration Service

Transaction 2
    ↓
Persist PROCESSING
    ↓
COMMIT
```

The architecture avoids:

```text
BEGIN DATABASE TRANSACTION
        ↓
HTTP call to remote service
        ↓
wait for network/dependency
        ↓
COMMIT
```

This reduces transaction duration and prevents database resources from being held while waiting for a remote dependency.

---

# Persistence and Idempotency

Idempotency is persistent.

It does not depend on application memory.

The platform currently stores information including:

- Internal order identifier
- External order identifier
- Idempotency key
- SHA-256 request fingerprint
- Order status
- Original correlation identifier
- Acceptance timestamp

Database uniqueness constraints protect:

```text
idempotency_key
```

and:

```text
external_order_id
```

These constraints provide a final consistency boundary against duplicate order creation.

The persisted order status also participates in retry behavior.

For example:

```text
PROCESSING
    ↓
do not invoke Integration Service again
```

while:

```text
ACCEPTED
    ↓
Integration Service handoff may be retried
```

---

# Design Principles

The Order API follows these principles:

- Contract-first API design
- Idempotent order creation
- Persistent duplicate protection
- Explicit transaction boundaries
- No remote HTTP call inside the order creation database transaction
- Request traceability
- Correlation propagation
- Consistent error responses
- Explicit HTTP semantics
- Separation between public API contracts and internal integration contracts
- Separation between API exposure and integration orchestration
- Separation between persistence and remote communication
- Database-enforced consistency
- Defensive validation of downstream responses
- Resilience by design
- Observability by design
- No exposure of internal implementation details

---

# Future Operations

The following operations may be introduced later.

## Get Order Status

```text
GET /api/v1/orders/{orderId}
```

Potential response states:

```text
ACCEPTED
PROCESSING
COMPLETED
FAILED
```

---

## Cancel Order

```text
POST /api/v1/orders/{orderId}/cancel
```

---

## Search Orders

```text
GET /api/v1/orders
```

These operations are outside the scope of the current implementation phase.

---

# Related Documentation

Architecture overview:

`docs/architecture.md`

OpenAPI specification:

`docs/api/order-api.yaml`

Integration Service contract:

`docs/api/integration-service-contract.md`

Integration Service OpenAPI specification:

`docs/api/integration-service.yaml`

Event documentation:

`docs/events/order-created-event.md`

AsyncAPI specification:

`docs/events/asyncapi.yaml`

Architecture Decision Records:

`docs/decisions/`

Synchronous and asynchronous communication decision:

`docs/decisions/ADR-001-synchronous-vs-asynchronous-integration.md`

Order creation idempotency decision:

`docs/decisions/ADR-002-idempotency-strategy.md`
# Order API Contract

## Overview

The Order API is the entry point for order creation within the enterprise integration platform.

It receives orders from the fictional e-commerce system through a synchronous REST interface.

The API follows a contract-first approach and is responsible for:

- Request validation
- Correlation identifier handling
- Idempotent order creation
- Initial order acceptance
- Consistent HTTP responses
- Consistent error responses

Downstream ERP processing will be performed asynchronously by other components of the integration architecture.

For this reason, successful order creation requests return:

`202 Accepted`

rather than:

`201 Created`

The response confirms that the order has been accepted by the platform, not that all downstream business processing has completed.

---

## Create Order

### Endpoint

`POST /api/v1/orders`

### Content Type

`application/json`

---

## Request Headers

### `X-Correlation-Id`

Optional.

Used to trace an individual request across the integration platform.

The value must be a valid UUID.

Example:

```text
X-Correlation-Id: b37166f4-8a39-4ffd-9599-c42ca48b83d0
```

If the caller provides the header, the Order API propagates the same correlation identifier.

If the caller does not provide the header, the Order API generates a new correlation identifier.

The effective correlation identifier is returned both:

- In the response header
- In the response body

Example response header:

```text
X-Correlation-Id: b37166f4-8a39-4ffd-9599-c42ca48b83d0
```

---

### `Idempotency-Key`

Required.

Used to make order creation safe to retry.

The value must be a valid UUID.

Example:

```text
Idempotency-Key: 9fe2d76a-268f-470b-a47f-a3895ab3a189
```

The same idempotency key must be reused when retrying the same order creation operation.

The idempotency key must not be reused for a different request.

---

## Request Body

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

## Request Fields

### `externalOrderId`

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

### `customer`

Required.

Contains the customer information required during order creation.

---

### `customer.customerId`

Required.

Identifier of the customer in the source or enterprise ecosystem.

Requirements:

- Must not be empty
- Maximum length: 100 characters

Example:

```text
CUST-10045
```

---

### `customer.email`

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

### `items`

Required.

Collection of products included in the order.

Requirements:

- Must contain at least one item

---

### `items[].productId`

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

### `items[].quantity`

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

### `items[].unitPrice`

Required.

Price per product unit.

Requirements:

- Must be greater than or equal to `0.01`

Example:

```text
29.95
```

---

### `currency`

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

### `shippingAddress`

Required.

Contains the shipping destination associated with the order.

---

### `shippingAddress.addressLine1`

Required.

Requirements:

- Must not be empty
- Maximum length: 200 characters

Example:

```text
123 Example Street
```

---

### `shippingAddress.addressLine2`

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

### `shippingAddress.city`

Required.

Requirements:

- Must not be empty
- Maximum length: 100 characters

Example:

```text
Seville
```

---

### `shippingAddress.postalCode`

Required.

Requirements:

- Must not be empty
- Maximum length: 20 characters

Example:

```text
41001
```

---

### `shippingAddress.country`

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

When the request is valid and the `Idempotency-Key` has not been used before, the platform creates and accepts a new order.

Response:

`202 Accepted`

Example:

```json
{
  "orderId": "b243423f-8047-49ea-b79f-50027400c022",
  "externalOrderId": "WEB-2026-000123",
  "status": "ACCEPTED",
  "correlationId": "11111111-1111-4111-8111-111111111111",
  "acceptedAt": "2026-09-05T13:40:24.946179Z"
}
```

Response header:

```text
X-Correlation-Id: 11111111-1111-4111-8111-111111111111
```

---

## Idempotent Retry

A client may retry the same request using the same `Idempotency-Key`.

Example:

```text
First request

Idempotency-Key: ABC
Request: A

        ↓

ORDER-123
```

If the client retries:

```text
Idempotency-Key: ABC
Request: A

        ↓

Same ORDER-123
```

The platform must not create another order.

The retry returns:

`202 Accepted`

with the same:

- `orderId`
- `externalOrderId`
- `status`
- `acceptedAt`

The retry may use a different `X-Correlation-Id`.

Example:

```text
First request

Idempotency-Key = ABC
Correlation-Id = 111

        ↓

ORDER-123
```

Retry:

```text
Idempotency-Key = ABC
Correlation-Id = 222

        ↓

Same ORDER-123
```

The retry response contains the correlation identifier associated with the current request.

Example:

```json
{
  "orderId": "b243423f-8047-49ea-b79f-50027400c022",
  "externalOrderId": "WEB-2026-000123",
  "status": "ACCEPTED",
  "correlationId": "22222222-2222-4222-8222-222222222222",
  "acceptedAt": "2026-09-05T13:40:24.946179Z"
}
```

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

New order created

        ↓

202 Accepted
```

---

## Scenario 2 — Same Key and Same Request

```text
Idempotency-Key = ABC
Request = A

        ↓

Existing operation found

        ↓

Request fingerprint matches

        ↓

Return existing order

        ↓

202 Accepted
```

No duplicate order is created.

---

## Scenario 3 — Same Key and Different Request

```text
Existing operation:

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

## Scenario 4 — Same External Order ID with Different Key

Example:

```text
First request:

externalOrderId = WEB-2026-000123
Idempotency-Key = ABC

        ↓

202 Accepted
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

The order has been validated and accepted by the platform.

Downstream processing may not yet have completed.

---

## `PROCESSING`

The order is being processed by downstream systems.

---

## `COMPLETED`

The order has been successfully processed.

---

## `FAILED`

The order could not be processed successfully.

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

The response also contains:

```text
X-Correlation-Id
```

to support request tracing.

---

# HTTP Status Codes

## `202 Accepted`

The request has been accepted for processing.

Used for:

- New order creation
- Legitimate retry using the same `Idempotency-Key` and equivalent request

A legitimate idempotent retry returns the previously created order instead of creating another one.

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

This status will become relevant when the Order API is connected to the Integration Service and required synchronous integrations.

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

Every request has an effective correlation identifier.

If the caller provides:

```text
X-Correlation-Id
```

the platform propagates it.

If the caller does not provide one, the Order API generates a new UUID.

The identifier will progressively be propagated across:

```text
E-Commerce
    ↓
Order API
    ↓
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

Identifies the business operation being retried.

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

and therefore resolve to the same order.

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
6. Initial order persistence
7. Order acceptance

---

## Future Synchronous Responsibilities

When the Integration Service is introduced, required synchronous enterprise interactions may occur before the order is accepted for downstream processing.

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

---

# Design Principles

The Order API follows these principles:

- Contract-first API design
- Idempotent order creation
- Persistent duplicate protection
- Request traceability
- Consistent error responses
- Explicit HTTP semantics
- Separation between API and persistence models
- Separation between API exposure and integration orchestration
- Database-enforced consistency
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
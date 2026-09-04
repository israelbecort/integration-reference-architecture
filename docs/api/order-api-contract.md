# Order API Contract

## Overview

The Order API provides the entry point for creating orders in the integration ecosystem.

The API follows a contract-first approach.

Orders are accepted synchronously through a REST API while downstream ERP processing is performed asynchronously.

---

## Create Order

### Endpoint

`POST /api/v1/orders`

### Content Type

`application/json`

---

## Headers

### X-Correlation-Id

Optional.

Used to trace the request across the complete integration flow.

If the client does not provide a correlation identifier, the Order API will generate one.

Example:

`X-Correlation-Id: b37166f4-8a39-4ffd-9599-c42ca48b83d0`

### Idempotency-Key

Required.

Used to prevent duplicate order creation when clients retry the same request.

Example:

`Idempotency-Key: 9fe2d76a-268f-470b-a47f-a3895ab3a189`

---

## Request

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

## Request Validation

### `externalOrderId`

Must be provided and must not be empty.

### `customer.customerId`

Must be provided.

### `customer.email`

Must contain a valid email address.

### `items`

Must contain at least one item.

### `items[].productId`

Must be provided.

### `items[].quantity`

Must be greater than zero.

### `items[].unitPrice`

Must be greater than zero.

### `currency`

Must contain a valid three-character ISO currency code.

### `shippingAddress`

Must contain the required address information.

---

## Successful Response

Because downstream order processing is asynchronous, the API returns:

`202 Accepted`

Example:

```json
{
  "orderId": "33c58292-f36e-4ca3-a421-c22da73e93e5",
  "externalOrderId": "WEB-2026-000123",
  "status": "ACCEPTED",
  "correlationId": "b37166f4-8a39-4ffd-9599-c42ca48b83d0",
  "acceptedAt": "2026-09-04T20:32:15Z"
}
```

---

## Order Status

The order lifecycle may contain the following states:

### `ACCEPTED`

The request has been successfully validated and accepted for processing.

### `PROCESSING`

Downstream systems are processing the order.

### `COMPLETED`

The order has been successfully processed.

### `FAILED`

The order could not be processed successfully.

---

## Error Responses

The API uses a consistent error model.

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
  "timestamp": "2026-09-04T20:32:15Z"
}
```

---

## HTTP Status Codes

### `400 Bad Request`

The request structure or input data is invalid.

Examples:

- Missing mandatory fields
- Invalid email format
- Invalid quantity
- Invalid currency format
- Malformed JSON

### `409 Conflict`

The request conflicts with an existing order or idempotency constraint.

Examples:

- The same `Idempotency-Key` has already been processed
- The same external order has already been registered

### `422 Unprocessable Entity`

The request is structurally valid but violates a business rule.

Examples:

- Product cannot be ordered
- Customer is not eligible to place the order
- Unsupported business condition

### `503 Service Unavailable`

A required downstream dependency is temporarily unavailable.

Example:

- CRM is unavailable and the synchronous operation cannot be completed

### `500 Internal Server Error`

An unexpected technical error occurred.

Internal implementation details must not be exposed to API consumers.

---

## Idempotency

Order creation must be idempotent.

The client must provide an `Idempotency-Key` header for every order creation request.

If the same request is retried using the same idempotency key, the platform must prevent duplicate order creation.

Example:

```text
Request 1

POST /api/v1/orders
Idempotency-Key: 9fe2d76a-268f-470b-a47f-a3895ab3a189

→ Order accepted
```

If the client retries:

```text
Request 2

POST /api/v1/orders
Idempotency-Key: 9fe2d76a-268f-470b-a47f-a3895ab3a189

→ Existing operation detected
→ Duplicate order must not be created
```

The exact implementation strategy will be defined in a dedicated Architecture Decision Record.

---

## Correlation and Traceability

Every request must have a correlation identifier.

The correlation identifier will be propagated across the complete integration flow:

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

If the caller provides an `X-Correlation-Id`, the platform will propagate it.

If the caller does not provide one, the Order API will generate a new identifier.

The correlation identifier must also be included in:

- Application logs
- Error responses
- Internal HTTP calls
- Published events
- Downstream processing

---

## Processing Model

The Order API uses a hybrid processing model.

### Synchronous processing

The following operations happen before the API returns a response:

1. Request validation
2. Correlation ID handling
3. Idempotency validation
4. Forwarding the request to the Integration Service
5. Required synchronous CRM interaction
6. Acceptance of the order for asynchronous processing

### Asynchronous processing

The following operations happen after the order has been accepted:

1. Order event publication
2. Kafka message delivery
3. ERP consumption
4. ERP order processing
5. Final order status update

For this reason, the API returns:

`202 Accepted`

instead of:

`201 Created`

The response confirms that the order has been accepted for processing, not that all downstream business processing has completed.

---

## Design Principles

The Order API follows these principles:

- Contract-first API design
- Consistent error responses
- Request traceability
- Idempotent order creation
- Separation between API exposure and integration orchestration
- Asynchronous downstream processing
- Clear responsibility boundaries
- No exposure of internal implementation details
- Resilience by design
- Observability by design

---

## Future Extensions

Future versions of the API may include:

### Get Order Status

`GET /api/v1/orders/{orderId}`

Possible response states:

- `ACCEPTED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

### Cancel Order

`POST /api/v1/orders/{orderId}/cancel`

### Order Search

`GET /api/v1/orders`

These operations are outside the scope of the first implementation phase.

---

## Related Documentation

Architecture overview:

`docs/architecture.md`

Architecture decisions:

`docs/decisions/`

The synchronous and asynchronous communication strategy is documented in:

`docs/decisions/ADR-001-synchronous-vs-asynchronous-integration.md`

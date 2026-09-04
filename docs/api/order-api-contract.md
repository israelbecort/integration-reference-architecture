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

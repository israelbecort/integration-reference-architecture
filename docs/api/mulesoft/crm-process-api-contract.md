# CRM Process API Contract

## 1. Purpose

The CRM Process API is the business orchestration layer responsible for synchronously processing canonical orders into the CRM domain.

It sits between the Integration Service and the Salesforce System API.

```text
Integration Service
        |
        | REST
        v
CRM Process API
        |
        | REST
        v
Salesforce System API
        |
        v
Salesforce
```

The API owns CRM-specific orchestration rules but does not expose Salesforce implementation details to upstream services.

Its main responsibilities are:

- receive a canonical order from the Integration Service;
- preserve the end-to-end correlation identifier;
- provide business-level idempotency using the canonical `orderId`;
- resolve registered and guest customers differently;
- orchestrate customer, order and order-line synchronization;
- translate System API errors into CRM-domain errors;
- return a synchronous CRM processing result.

The API must not contain Saleor-specific logic.

---

## 2. Architectural Role

The CRM Process API represents the **Process API layer** of the API-led architecture.

```text
Experience / Source boundary
        |
        v
Order API
        |
        v
Integration Service
        |
        +----------------------+
        |                      |
        v                      v
CRM Process API             Kafka
        |
        v
Salesforce System API
        |
        v
Salesforce
```

Responsibilities are deliberately separated:

### Integration Service

Owns cross-domain orchestration.

It decides which downstream domains must receive the order.

### CRM Process API

Owns CRM business orchestration.

It decides how a canonical order must be represented and processed in the CRM domain.

### Salesforce System API

Owns Salesforce-specific access.

It encapsulates:

- Salesforce authentication;
- SOQL;
- Salesforce object names;
- Salesforce field names;
- Salesforce IDs;
- connector-specific errors;
- Salesforce-specific retry behavior.

The CRM Process API must not execute SOQL directly.

---

## 3. Endpoint

### Process an order in CRM

```http
POST /api/v1/orders/{orderId}/process
```

The operation is synchronous.

A successful response means that the CRM synchronization required by this API has completed successfully.

---

## 4. Request Headers

### X-Correlation-Id

Required.

```http
X-Correlation-Id: 18bdb3ad-1075-4b65-b42e-6ee7a27d1e99
```

Format:

```text
UUID
```

The value is created upstream and must be propagated unchanged through:

```text
Integration Service
        ↓
CRM Process API
        ↓
Salesforce System API
```

The CRM Process API must return the same value in its response.

### Idempotency-Key

The public `Idempotency-Key` used by the Order API is **not propagated** to this API.

CRM processing uses the canonical `orderId` as its business idempotency identity.

This prevents infrastructure concerns from the public API boundary from leaking into internal integration layers.

---

## 5. Path Parameters

### orderId

Required.

```text
UUID
```

Example:

```text
e30c6941-c229-4234-b4a2-971176e54c20
```

The value identifies the canonical order created by the Order API.

It is stable across retries.

---

## 6. Request Body

Example:

```json
{
  "externalOrderId": "T3JkZXI6NjlmZTA4NjYtY2U3Ni00ODI1LTlkNTYtMzAyODhlMTUyODE0",
  "customer": {
    "customerId": "VXNlcjox",
    "email": "customer@example.com"
  },
  "items": [
    {
      "productId": "UHJvZHVjdFZhcmlhbnQ6MzYy",
      "quantity": 1,
      "unitPrice": 45.00
    }
  ],
  "currency": "USD",
  "shippingAddress": {
    "addressLine1": "813 Howard Street",
    "addressLine2": "",
    "city": "OSWEGO",
    "postalCode": "13126",
    "country": "US"
  },
  "acceptedAt": "2026-09-10T20:03:58.857816Z"
}
```

---

## 7. Request Model

### externalOrderId

Required.

```text
string
max length: 100
```

Stable identifier of the order in the source commerce platform.

The CRM Process API must not use this value as a substitute for the canonical `orderId`.

---

### customer

Required.

Contains the canonical customer identity.

#### customer.customerId

Optional.

```text
string | null
max length: 100
```

When present, the order belongs to a registered commerce customer.

When absent or `null`, the order belongs to a guest customer.

The CRM Process API must not generate or invent a commerce customer identifier for guest customers.

#### customer.email

Required.

```text
valid email
max length: 254
```

Used as customer contact information and as part of guest customer resolution.

---

### items

Required.

Must contain at least one item.

Each item contains:

#### productId

Required.

```text
string
max length: 100
```

Stable canonical product identity received from the source adapter.

#### quantity

Required.

```text
positive integer
```

#### unitPrice

Required.

```text
decimal >= 0.01
```

---

### currency

Required.

```text
ISO-4217 three-letter uppercase currency code
```

Example:

```text
USD
```

---

### shippingAddress

Required.

Fields:

```text
addressLine1
addressLine2
city
postalCode
country
```

`country` must contain a two-letter uppercase country code.

---

### acceptedAt

Required.

```text
ISO-8601 timestamp
```

Represents when the canonical Order API originally accepted the order.

---

## 8. Registered Customer Processing

A registered customer is identified by:

```text
customer.customerId != null
```

Example:

```json
{
  "customerId": "VXNlcjox",
  "email": "customer@example.com"
}
```

The CRM Process API must use the commerce customer identifier as the primary external business identity.

Conceptual flow:

```text
customerId present
      |
      v
Resolve CRM customer
by external customer identity
      |
      +---- exists ----+
      |                |
      |                v
      |          update if needed
      |
      +---- missing ---+
                       |
                       v
                 create customer
```

The Process API delegates the actual Salesforce lookup and persistence to the Salesforce System API.

---

## 9. Guest Customer Processing

A guest customer is identified by:

```text
customer.customerId == null
```

Example:

```json
{
  "customerId": null,
  "email": "guest@example.com"
}
```

The CRM Process API must not generate a fake commerce customer ID.

Guest resolution uses the normalized email address as the available customer identity.

Conceptual flow:

```text
customerId == null
       |
       v
normalize email
       |
       v
resolve guest CRM customer
       |
       +---- exists ----+
       |                |
       |                v
       |          reuse / update
       |
       +---- missing ---+
                       |
                       v
                 create guest
                 CRM customer
```

Registered and guest customer resolution are intentionally different business paths.

---

## 10. CRM Order Processing Flow

The high-level orchestration is:

```text
Receive canonical order
        |
        v
Validate request
        |
        v
Check business idempotency
        |
        v
Resolve customer
        |
        v
Upsert CRM order
        |
        v
Upsert order lines
        |
        v
Persist successful idempotency result
        |
        v
Return CRM processing result
```

The Process API must not directly manipulate Salesforce objects.

All Salesforce operations are performed through the Salesforce System API.

---

## 11. Idempotency Strategy

The CRM Process API provides a second idempotency boundary independent from the public Order API.

Layers:

```text
Order API
    |
    | HTTP Idempotency-Key
    v
Canonical order
    |
    | canonical orderId
    v
CRM Process API
    |
    | business idempotency
    v
Salesforce
    |
    | external identifiers / upsert
    v
CRM records
```

### Idempotency identity

Key:

```text
orderId
```

The canonical `orderId` is stable across retries.

Example:

```text
e30c6941-c229-4234-b4a2-971176e54c20
```

### Object Store

The CRM Process API uses Mule Object Store to persist successful processing information.

Conceptually:

```text
crm-order:{orderId}
```

Example:

```text
crm-order:e30c6941-c229-4234-b4a2-971176e54c20
```

The stored value contains at least:

```json
{
  "orderId": "e30c6941-c229-4234-b4a2-971176e54c20",
  "requestHash": "...",
  "status": "COMPLETED",
  "result": {
    "crmOrderId": "...",
    "customerReference": "..."
  }
}
```

### Same orderId + same request

If an already completed order is received again with the same canonical request:

```text
same orderId
+
same deterministic request hash
```

the CRM Process API must not recreate the CRM order.

It returns the previously persisted result.

### Same orderId + different request

If the same `orderId` is received with a different canonical payload:

```text
same orderId
+
different request hash
```

the API returns:

```http
409 Conflict
```

This represents an idempotency integrity violation.

### Failed transient processing

A transient downstream failure must not be stored as a successfully completed idempotency result.

The same `orderId` may therefore be retried later.

Salesforce external identifiers and upsert operations provide an additional protection against duplicate downstream records if a previous attempt partially completed.

---

## 12. Request Fingerprint

A deterministic SHA-256 fingerprint must be generated from the business-relevant canonical request.

The fingerprint must not depend on:

- JSON property order;
- whitespace;
- correlationId;
- transient infrastructure metadata.

Its purpose is to distinguish:

```text
legitimate retry
```

from:

```text
same order identity with different business content
```

The exact canonicalization algorithm will be documented with the implementation.

---

## 13. Salesforce System API Operations

The CRM Process API is expected to orchestrate operations equivalent to:

```text
Resolve / upsert registered customer
Resolve / upsert guest customer
Upsert CRM order
Upsert CRM order lines
```

The exact System API endpoints are defined by the Salesforce System API contract.

The Process API must not depend directly on Salesforce connector semantics.

---

## 14. Successful Response

### HTTP 200

Example:

```http
HTTP/1.1 200 OK
X-Correlation-Id: 18bdb3ad-1075-4b65-b42e-6ee7a27d1e99
Content-Type: application/json
```

```json
{
  "orderId": "e30c6941-c229-4234-b4a2-971176e54c20",
  "externalOrderId": "T3JkZXI6NjlmZTA4NjYtY2U3Ni00ODI1LTlkNTYtMzAyODhlMTUyODE0",
  "status": "COMPLETED",
  "crmOrderId": "a01XXXXXXXXXXXXXXX",
  "customerReference": "003XXXXXXXXXXXXXXX",
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99",
  "processedAt": "2026-09-11T10:30:00Z"
}
```

### status

Successful synchronous CRM processing returns:

```text
COMPLETED
```

This status refers to completion of the CRM branch only.

It does not mean that all other enterprise integrations for the canonical order have completed.

For example:

```text
CRM       → COMPLETED
ERP       → may still be processing asynchronously
WMS       → may still be pending
```

---

## 15. Error Response

All API errors use a consistent CRM-domain response.

Example:

```json
{
  "errorCode": "CRM-DEPENDENCY-001",
  "message": "Salesforce System API is temporarily unavailable",
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99",
  "timestamp": "2026-09-11T10:31:00Z"
}
```

The Process API must not expose raw Salesforce connector errors to the Integration Service.

---

## 16. Error Taxonomy

### CRM-VALIDATION-001

HTTP:

```text
400 Bad Request
```

Used when the canonical request does not satisfy the CRM Process API contract.

Examples:

- invalid UUID;
- missing required field;
- malformed email;
- empty item collection;
- invalid quantity;
- invalid currency.

---

### CRM-IDEMPOTENCY-001

HTTP:

```text
409 Conflict
```

Used when the same `orderId` is received with a different request fingerprint.

Example:

```text
Existing:
orderId = abc
hash    = A

Incoming:
orderId = abc
hash    = B
```

---

### CRM-CUSTOMER-001

HTTP:

```text
422 Unprocessable Entity
```

Used when the customer cannot be processed according to CRM business rules.

Examples:

- registered customer identity cannot be reconciled;
- guest customer data is insufficient;
- inconsistent CRM customer identity.

---

### CRM-ORDER-001

HTTP:

```text
422 Unprocessable Entity
```

Used when the canonical order cannot be represented in the CRM domain.

---

### CRM-DEPENDENCY-001

HTTP:

```text
503 Service Unavailable
```

Used when the Salesforce System API or Salesforce is temporarily unavailable.

Examples:

- connection timeout;
- HTTP 5xx;
- Salesforce service unavailable;
- transient rate limiting after permitted retry attempts are exhausted.

The error is considered retryable by the upstream Integration Service.

---

### CRM-PROTOCOL-001

HTTP:

```text
502 Bad Gateway
```

Used when the Salesforce System API returns a response that violates its agreed contract.

Examples:

- required response identifier missing;
- malformed JSON;
- incompatible response schema.

---

### CRM-INTERNAL-001

HTTP:

```text
500 Internal Server Error
```

Used for unexpected internal processing failures.

---

## 17. Retry Policy

Retry ownership must be explicit to avoid retry multiplication across integration layers.

The CRM Process API does **not** own Salesforce connector-level retries.

Those retries belong to the Salesforce System API.

```text
CRM Process API
        |
        | single System API invocation
        v
Salesforce System API
        |
        | bounded Salesforce-specific retry
        v
Salesforce
```

### Initial Process API policy

In the initial implementation, the CRM Process API does not automatically retry a failed Salesforce System API invocation.

If the System API returns a transient dependency error such as:

```text
503 Service Unavailable
```

the CRM Process API translates it to:

```text
CRM-DEPENDENCY-001
```

and returns the failure upstream.

A later retry of the complete CRM operation is safe because the CRM processing flow is protected by:

```text
canonical orderId
+
Object Store business idempotency
+
Salesforce external IDs / upsert
```

### Salesforce retry ownership

The Salesforce System API may perform bounded retries for Salesforce-specific transient failures such as:

```text
connection timeout
HTTP 429 / rate limiting
temporary connectivity failure
Salesforce 5xx
```

The CRM Process API must not duplicate that retry policy.

### Non-retryable examples

The Process API must not automatically retry domain or contract failures such as:

```text
400 validation errors
404 meaningful business absence
409 conflicts
422 domain validation failures
```

This separation prevents uncontrolled retry multiplication and keeps retry behavior owned by the layer that understands the failing dependency.

---

## 18. Correlation

### Valid inbound correlation

When `X-Correlation-Id` is present and contains a valid UUID, the same value must be used across the complete synchronous CRM call chain.

```text
Integration Service
 correlationId=A
        |
        v
CRM Process API
 correlationId=A
        |
        v
Salesforce System API
 correlationId=A
```

All relevant log messages must include the correlation ID.

The correlation ID is an observability identifier.

It is not an idempotency identifier.

### Missing or invalid correlation

`X-Correlation-Id` is mandatory and must contain a valid UUID.

If the header is missing or malformed, the CRM Process API rejects the request with:

```text
400 Bad Request
CRM-VALIDATION-001
```

Because no valid upstream correlation identifier exists in this case, the CRM Process API generates a new UUID only for local error observability.

That generated identifier is returned in:

```text
X-Correlation-Id
```

and:

```text
errorResponse.correlationId
```

The generated identifier is used only to trace the rejected request.

It must not be interpreted as the original end-to-end correlation identifier.

---

## 18.1 Salesforce System API Error Translation

The CRM Process API translates System API errors into its own CRM-domain taxonomy.

The upstream Integration Service must never depend directly on `SF-*` error codes.

Initial translation rules:

| Salesforce System API error | CRM Process API error | HTTP | Meaning |
| --- | --- | ---: | --- |
| `SF-VALIDATION-001` | `CRM-PROTOCOL-001` | 502 | The Process API produced a request that violated the agreed System API contract. |
| `SF-AUTH-001` | `CRM-DEPENDENCY-001` | 503 | Salesforce authentication or integration configuration is unavailable. |
| `SF-RATE-LIMIT-001` | `CRM-DEPENDENCY-001` | 503 | Salesforce remains rate limited after bounded System API retries. |
| `SF-DEPENDENCY-001` | `CRM-DEPENDENCY-001` | 503 | Salesforce is temporarily unavailable. |
| `SF-PROTOCOL-001` | `CRM-PROTOCOL-001` | 502 | The System API or Salesforce returned an unusable response. |
| `SF-INTERNAL-001` | `CRM-DEPENDENCY-001` | 503 | The downstream Salesforce integration boundary failed unexpectedly. |

Business errors are translated according to the orchestration stage:

```text
customer operation
SF-CONFLICT-001
SF-BUSINESS-001
SF-NOT-FOUND-001
        ↓
CRM-CUSTOMER-001
```

```text
order / order-line operation
SF-CONFLICT-001
SF-BUSINESS-001
SF-NOT-FOUND-001
        ↓
CRM-ORDER-001
```

`CRM-IDEMPOTENCY-001` is reserved exclusively for the CRM Process API's own business-idempotency conflict:

```text
same canonical orderId
+
different deterministic request fingerprint
```

A Salesforce conflict must therefore never be converted into `CRM-IDEMPOTENCY-001`.

---

## 19. Logging

Structured logs should include safe technical identifiers such as:

```text
correlationId
orderId
externalOrderId
customer type
processing stage
downstream operation
error code
duration
```

Logs should avoid unnecessarily exposing:

```text
customer email
postal address
authentication tokens
Salesforce credentials
access tokens
full request payloads
```

Sensitive payload logging must never be enabled by default.

---

## 20. DataWeave Responsibilities

Transformation logic must be separated into reusable DataWeave modules.

Expected modules include:

```text
dw/
├── common.dwl
├── customer.dwl
├── order.dwl
├── order-lines.dwl
└── error.dwl
```

Responsibilities:

### common.dwl

Shared normalization and helper functions.

### customer.dwl

Registered and guest customer transformations.

### order.dwl

Canonical order to CRM-domain order transformation.

### order-lines.dwl

Order line transformations.

### error.dwl

Normalized CRM error response generation.

Large transformation expressions should not be embedded directly inside Mule flows when they can be expressed as reusable modules.

---

## 21. Mule Flow Responsibilities

The implementation should remain readable at orchestration level.

Conceptually:

```text
POST /orders/{orderId}/process
        |
        v
validate headers/path/body
        |
        v
calculate request fingerprint
        |
        v
check Object Store
        |
        v
Choice
  |
  +-- registered customer
  |       |
  |       v
  |   resolve/upsert registered customer
  |
  +-- guest customer
          |
          v
      resolve/upsert guest customer
        |
        v
upsert CRM order
        |
        v
process order lines
        |
        v
store idempotency result
        |
        v
return COMPLETED
```

The flow must remain orchestration-oriented.

Salesforce-specific transformation and transport concerns belong in the System API.

---

## 22. Error Handling Strategy

Salesforce System API errors are translated into CRM-domain errors.

Example:

```text
SF:TIMEOUT
      ↓
CRM-DEPENDENCY-001
```

```text
SF:RATE_LIMIT
      ↓
bounded retry
      ↓
if exhausted
      ↓
CRM-DEPENDENCY-001
```

```text
SF:VALIDATION
      ↓
CRM-ORDER-001
```

Raw Mule connector exceptions must not cross the Process API boundary.

---

## 23. Out of Scope

The CRM Process API does not own:

- Saleor webhook verification;
- Saleor payload mapping;
- public Order API idempotency;
- canonical order persistence;
- Kafka publication;
- ERP synchronization;
- IBM MQ;
- WMS integration;
- Salesforce authentication details;
- direct SOQL execution from the Process layer;
- global order completion status.

Those responsibilities belong to other architectural components.

---

## 24. Initial Implementation Goals

The first implementation milestone should demonstrate:

- APIKit-based REST contract;
- mandatory correlation propagation;
- registered vs guest customer branching;
- Object Store business idempotency;
- deterministic request fingerprinting;
- Salesforce System API abstraction;
- modular DataWeave transformations;
- explicit error taxonomy;
- selective retry handling;
- MUnit coverage;
- secure property handling;
- structured logging.

The goal is not merely to make Salesforce calls.

The goal is to demonstrate a maintainable enterprise integration boundary with clear separation of responsibilities.
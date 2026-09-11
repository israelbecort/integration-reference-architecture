# Salesforce System API Contract

## 1. Purpose

The Salesforce System API provides a controlled integration boundary around Salesforce.

It is consumed by the CRM Process API and encapsulates Salesforce-specific implementation details.

```text
CRM Process API
        |
        | REST
        v
Salesforce System API
        |
        | Salesforce Connector
        v
Salesforce
```

The System API is responsible for:

- Salesforce connectivity and authentication;
- Salesforce object and field mappings;
- SOQL execution;
- external ID based upsert operations;
- Salesforce-specific error normalization;
- bounded retry for transient Salesforce connector failures;
- correlation propagation;
- shielding upstream APIs from Salesforce implementation details.

The API must not contain cross-domain business orchestration.

---

## 2. Architectural Role

The Salesforce System API represents the **System API layer** of the API-led architecture.

```text
Integration Service
        |
        v
CRM Process API
        |
        v
Salesforce System API
        |
        v
Salesforce
```

Responsibilities are deliberately separated.

### CRM Process API

Owns CRM business orchestration.

Examples:

- registered vs guest customer decision;
- order processing sequence;
- business idempotency;
- request fingerprinting;
- CRM-domain error translation.

### Salesforce System API

Owns Salesforce access.

Examples:

- SOQL;
- Salesforce object names;
- Salesforce field names;
- connector configuration;
- external IDs;
- Salesforce IDs;
- Salesforce connector errors;
- Salesforce-specific retry handling.

The CRM Process API must not execute Salesforce queries directly.

---

## 3. Base Path

```text
/api/v1
```

Initial resources:

```text
POST /api/v1/customers/upsert

PUT  /api/v1/orders/{orderId}

PUT  /api/v1/orders/{orderId}/lines
```

The API is internal.

It is not exposed directly to the commerce platform or public consumers.

---

## 4. Common Request Headers

### X-Correlation-Id

Required on every request.

Example:

```http
X-Correlation-Id: 18bdb3ad-1075-4b65-b42e-6ee7a27d1e99
```

Format:

```text
UUID
```

The value is received from the CRM Process API and must be propagated unchanged through all Salesforce operations and logs.

The System API must return the same value in its response.

The correlation ID is not an idempotency key.

### Missing or invalid correlation

`X-Correlation-Id` is mandatory and must contain a valid UUID.

If it is missing or malformed, the Salesforce System API rejects the request with:

```text
400 Bad Request
SF-VALIDATION-001
```

Because no valid inbound correlation identifier exists, the System API generates a new UUID only for local error observability.

That generated value is returned in both:

```text
X-Correlation-Id
```

and:

```text
errorResponse.correlationId
```

It must not be interpreted as an end-to-end correlation identifier originating from the caller.

---


## 5. Customer Upsert

### Endpoint

```http
POST /api/v1/customers/upsert
```

The operation resolves and, when required, creates or updates a CRM customer.

The CRM Process API explicitly communicates whether the customer is registered or guest.

The System API owns the Salesforce-specific persistence strategy.

---

## 6. Customer Upsert Request

### Registered customer

```json
{
  "customerType": "REGISTERED",
  "externalCustomerId": "VXNlcjox",
  "email": "customer@example.com"
}
```

### Guest customer

```json
{
  "customerType": "GUEST",
  "email": "guest@example.com"
}
```

---

## 7. Customer Upsert Rules

### customerType

Required.

Allowed values:

```text
REGISTERED
GUEST
```

### externalCustomerId

Required for:

```text
REGISTERED
```

Must be omitted for:

```text
GUEST
```

The System API must reject inconsistent combinations.

Examples:

```text
REGISTERED + null externalCustomerId
→ validation error
```

```text
GUEST + externalCustomerId present
→ validation error
```

### email

Required.

The email address must be normalized before CRM persistence.

At minimum:

```text
trim whitespace
lowercase
```

The original canonical customer identity must not be replaced with a generated commerce identifier.

---

## 8. Registered Customer Persistence

Registered customers use the commerce customer ID as their external CRM business identity.

Conceptually:

```text
externalCustomerId
        |
        v
Salesforce external ID
        |
        v
upsert customer
```

Initial Salesforce implementation:

```text
Contact.CommerceCustomerId__c
```

Properties:

```text
External ID = true
Unique      = true
```

Example:

```text
CommerceCustomerId__c = VXNlcjox
```

A retry with the same registered customer identity must update or reuse the existing Salesforce customer instead of creating a duplicate.

---

## 9. Guest Customer Persistence

Guest customers do not have a commerce customer ID.

They are resolved using their normalized email identity.

The System API may derive an internal deterministic guest key from the normalized email.

Conceptually:

```text
guest@example.com
       |
       v
normalize
       |
       v
SHA-256
       |
       v
GuestKey__c
```

The guest key is a CRM integration identity.

It is **not** a fabricated commerce customer ID.

Initial Salesforce implementation:

```text
Contact.GuestKey__c
```

Properties:

```text
External ID = true
Unique      = true
```

This enables safe retry behavior even if a previous Salesforce create/upsert completed but the response was lost.

The raw email address should not be used as an external identifier when a deterministic non-PII key can be used instead.

---

## 10. Initial Salesforce Customer Model

The initial implementation uses:

```text
Contact
```

Suggested integration fields:

```text
CommerceCustomerId__c
GuestKey__c
CustomerType__c
```

Standard field:

```text
Email
```

Conceptual examples:

### Registered

```text
Contact
├── CommerceCustomerId__c = VXNlcjox
├── GuestKey__c           = null
├── CustomerType__c       = REGISTERED
└── Email                 = customer@example.com
```

### Guest

```text
Contact
├── CommerceCustomerId__c = null
├── GuestKey__c           = <deterministic hash>
├── CustomerType__c       = GUEST
└── Email                 = guest@example.com
```

These Salesforce field names are System API implementation details and must not leak into the CRM Process API contract.

---

## 11. Customer Upsert Response

### HTTP 200

```json
{
  "customerReference": "003XXXXXXXXXXXXXXX",
  "customerType": "REGISTERED",
  "created": false,
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99"
}
```

For a newly created customer:

```json
{
  "customerReference": "003XXXXXXXXXXXXXXX",
  "customerType": "GUEST",
  "created": true,
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99"
}
```

### customerReference

Salesforce customer identifier.

The Process API may use this value during the current orchestration.

It must not interpret its internal Salesforce format.

---

## 12. Order Upsert

### Endpoint

```http
PUT /api/v1/orders/{orderId}
```

The operation creates or updates the Salesforce representation of a canonical order.

`orderId` is the canonical Order API UUID.

Example:

```text
e30c6941-c229-4234-b4a2-971176e54c20
```

The operation is idempotent.

---

## 13. Order Upsert Request

Example:

```json
{
  "externalOrderId": "T3JkZXI6NjlmZTA4NjYtY2U3Ni00ODI1LTlkNTYtMzAyODhlMTUyODE0",
  "customerReference": "003XXXXXXXXXXXXXXX",
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

## 14. Order Persistence

Initial Salesforce object:

```text
Order__c
```

Suggested integration fields:

```text
CanonicalOrderId__c
ExternalOrderId__c
Customer__c
Currency__c
AcceptedAt__c
ShippingAddressLine1__c
ShippingAddressLine2__c
ShippingCity__c
ShippingPostalCode__c
ShippingCountry__c
```

### CanonicalOrderId__c

Configured as:

```text
External ID = true
Unique      = true
```

The canonical `orderId` is the Salesforce order upsert key.

Example:

```text
CanonicalOrderId__c =
e30c6941-c229-4234-b4a2-971176e54c20
```

The source platform `externalOrderId` must not be used as a replacement for the canonical order identity.

---

## 15. Order Upsert Response

### HTTP 200

```json
{
  "orderId": "e30c6941-c229-4234-b4a2-971176e54c20",
  "crmOrderId": "a01XXXXXXXXXXXXXXX",
  "created": true,
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99"
}
```

On subsequent idempotent upsert:

```json
{
  "orderId": "e30c6941-c229-4234-b4a2-971176e54c20",
  "crmOrderId": "a01XXXXXXXXXXXXXXX",
  "created": false,
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99"
}
```

---

## 16. Order Lines Upsert

### Endpoint

```http
PUT /api/v1/orders/{orderId}/lines
```

The operation synchronizes the canonical order lines into Salesforce.

Example request:

```json
{
  "crmOrderId": "a01XXXXXXXXXXXXXXX",
  "items": [
    {
      "productId": "UHJvZHVjdFZhcmlhbnQ6MzYy",
      "quantity": 1,
      "unitPrice": 45.00
    }
  ]
}
```

The request must contain at least one item.

---

## 17. Order Line Identity

The current canonical contract does not expose a source order-line identifier.

Therefore the System API must derive a deterministic CRM integration identity for each line.

Initial strategy:

```text
orderId
+
canonical item position
+
productId
```

The resulting value is hashed deterministically.

Conceptually:

```text
SHA-256(
  orderId
  + "|"
  + itemPosition
  + "|"
  + productId
)
```

The derived value is stored in:

```text
OrderLine__c.ExternalLineKey__c
```

Properties:

```text
External ID = true
Unique      = true
```

This strategy is safe for legitimate retries because the CRM Process API rejects a different canonical payload for an already-known `orderId`.

If the canonical order model later introduces an explicit order-line identity, that identifier should replace the derived line key.

---

## 18. Initial Salesforce Order Line Model

Object:

```text
OrderLine__c
```

Suggested integration fields:

```text
ExternalLineKey__c
Order__c
ProductExternalId__c
Quantity__c
UnitPrice__c
```

The System API owns mapping these fields to Salesforce.

The CRM Process API must not know these field names.

---

## 19. Order Lines Response

### HTTP 200

Example:

```json
{
  "orderId": "e30c6941-c229-4234-b4a2-971176e54c20",
  "crmOrderId": "a01XXXXXXXXXXXXXXX",
  "processedLines": 1,
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99"
}
```

`processedLines` represents the number of order lines successfully synchronized by the operation.

---

## 20. Salesforce Idempotency Strategy

The System API provides the final idempotency boundary before Salesforce.

The complete architecture therefore uses layered identities:

```text
Saleor / Adapter
        |
        | deterministic HTTP Idempotency-Key
        v
Order API
        |
        | canonical orderId
        v
CRM Process API
        |
        | business idempotency / Object Store
        v
Salesforce System API
        |
        | Salesforce External IDs
        v
Salesforce
```

Salesforce external IDs protect against duplicate records when:

- a request is retried;
- a network response is lost;
- the Process API repeats a downstream operation;
- a Mule runtime restarts between operations.

---

## 21. Error Response

All System API errors use a normalized structure.

Example:

```json
{
  "errorCode": "SF-DEPENDENCY-001",
  "message": "Salesforce is temporarily unavailable",
  "correlationId": "18bdb3ad-1075-4b65-b42e-6ee7a27d1e99",
  "timestamp": "2026-09-11T11:30:00Z"
}
```

Raw Salesforce connector exceptions must never be returned to the caller.

---

## 22. Error Taxonomy

### SF-VALIDATION-001

HTTP:

```text
400 Bad Request
```

Examples:

- invalid request;
- missing required field;
- inconsistent customer type;
- malformed UUID;
- invalid email;
- empty order line collection.

---

### SF-AUTH-001

HTTP:

```text
503 Service Unavailable
```

Represents a Salesforce authentication or integration configuration failure.

Examples:

- expired or unusable integration credentials;
- Salesforce authentication unavailable;
- OAuth configuration failure.

The raw credential or authentication response must not be exposed.

Operationally this requires intervention even though it is represented as a dependency availability failure to upstream services.

---

### SF-NOT-FOUND-001

HTTP:

```text
404 Not Found
```

Used only when the requested Salesforce resource is expected to exist and cannot be resolved.

A normal upsert lookup returning no existing record is not an error.

---

### SF-CONFLICT-001

HTTP:

```text
409 Conflict
```

Examples:

- conflicting Salesforce external identities;
- multiple records unexpectedly match a unique business identity;
- uniqueness constraint conflict that cannot be resolved safely.

---

### SF-BUSINESS-001

HTTP:

```text
422 Unprocessable Entity
```

Used when Salesforce rejects otherwise well-formed data because of a non-transient CRM rule.

Examples:

- validation rule;
- required CRM business relationship missing;
- incompatible CRM record state.

---

### SF-RATE-LIMIT-001

HTTP:

```text
503 Service Unavailable
```

Used when Salesforce rate limiting remains unresolved after the permitted bounded retry strategy.

---

### SF-DEPENDENCY-001

HTTP:

```text
503 Service Unavailable
```

Used for transient Salesforce availability failures.

Examples:

- connector timeout;
- connection failure;
- Salesforce temporary service failure;
- transient Salesforce server error.

---

### SF-PROTOCOL-001

HTTP:

```text
502 Bad Gateway
```

Used when Salesforce returns an unexpected or unusable response.

Examples:

- required Salesforce ID missing;
- connector response cannot be interpreted;
- inconsistent upsert result.

---

### SF-INTERNAL-001

HTTP:

```text
500 Internal Server Error
```

Used for unexpected internal System API failures.

---

## 23. Error Translation

Salesforce connector errors must be translated into the System API taxonomy.

Conceptual examples:

```text
SALESFORCE:CONNECTIVITY
        ↓
SF-DEPENDENCY-001
```

```text
SALESFORCE:TIMEOUT
        ↓
SF-DEPENDENCY-001
```

```text
Salesforce rate limit
        ↓
bounded retry
        ↓
retry exhausted
        ↓
SF-RATE-LIMIT-001
```

```text
Salesforce validation rule
        ↓
SF-BUSINESS-001
```

```text
unexpected duplicate identity
        ↓
SF-CONFLICT-001
```

Upstream services must not depend on raw Mule Salesforce connector error types.

---

## 24. Retry Ownership

Retry behavior must avoid retry multiplication across layers.

The Salesforce System API is the **primary owner of Salesforce connector-level retries**.

It may apply bounded retry to clearly transient Salesforce failures such as:

```text
connection timeout
temporary connectivity failure
HTTP 429 / Salesforce rate limiting
temporary Salesforce 5xx
```

Retries must have:

```text
bounded attempts
controlled delay
clear logging
correlation preservation
```

Infinite retry is forbidden.

The CRM Process API must not independently repeat the same Salesforce-specific retry policy.

It may retry a System API transport failure only when explicitly justified and bounded.

This avoids patterns such as:

```text
Process API 3 retries
        ×
System API 3 retries
        =
9 Salesforce attempts
```

---

## 25. Partial Failure Safety

A CRM order synchronization consists of multiple operations:

```text
customer
   ↓
order
   ↓
order lines
```

A failure may happen after some Salesforce writes have already completed.

Example:

```text
customer upsert       ✅
order upsert          ✅
line synchronization  ❌
```

A retry must therefore be safe.

This is achieved through:

```text
Contact external identity
Order__c.CanonicalOrderId__c
OrderLine__c.ExternalLineKey__c
```

The retry reuses or updates previously written records rather than blindly creating duplicates.

No distributed transaction is attempted across Mule and Salesforce.

---

## 26. Transaction Boundaries

The System API must not pretend that the complete CRM workflow is one distributed ACID transaction.

Each Salesforce operation has its own persistence boundary.

The architecture prefers:

```text
idempotent operations
+
external identifiers
+
safe retries
```

over distributed transactions.

The CRM Process API owns orchestration and recovery semantics.

---

## 27. Logging

Structured System API logs should include:

```text
correlationId
operation
customerType
canonical orderId
Salesforce record reference
result
duration
normalized error code
retry attempt
```

Avoid logging:

```text
access tokens
refresh tokens
passwords
Salesforce secrets
full request payloads
customer email unless strictly required
postal addresses
```

---

## 28. Secure Configuration

Salesforce credentials must never be committed to the repository.

Configuration must support secure externalization.

Expected categories:

```text
Salesforce username / client identity
authentication secrets
token configuration
Salesforce endpoint
API version
timeouts
retry configuration
```

Production-oriented Mule configuration should use secure properties or platform-managed secrets.

Local development secrets must remain outside version control.

---

## 29. DataWeave Responsibilities

Salesforce-specific transformations belong to the System API.

Expected modules may include:

```text
dw/
├── common.dwl
├── customer-to-salesforce.dwl
├── order-to-salesforce.dwl
├── order-lines-to-salesforce.dwl
└── salesforce-error.dwl
```

### customer-to-salesforce.dwl

Maps the internal customer contract to Salesforce Contact fields.

### order-to-salesforce.dwl

Maps the internal order contract to `Order__c`.

### order-lines-to-salesforce.dwl

Maps canonical items to `OrderLine__c`.

### salesforce-error.dwl

Produces normalized System API errors.

Raw Salesforce field mappings should not be embedded throughout orchestration flows.

---

## 30. Mule Flow Responsibilities

Conceptual structure:

```text
APIKit Router
     |
     +-- POST /customers/upsert
     |        |
     |        v
     |   validate identity
     |        |
     |        v
     |   registered / guest strategy
     |        |
     |        v
     |   Salesforce upsert
     |
     +-- PUT /orders/{orderId}
     |        |
     |        v
     |   map Order__c
     |        |
     |        v
     |   upsert by CanonicalOrderId__c
     |
     +-- PUT /orders/{orderId}/lines
              |
              v
         derive line identities
              |
              v
         map OrderLine__c
              |
              v
         Salesforce upsert
```

Common concerns should be extracted into reusable subflows.

---

## 31. APIKit

The System API should be contract-first and implemented through APIKit.

The machine-readable API contract will define:

- resources;
- HTTP methods;
- headers;
- schemas;
- response codes;
- examples.

The Mule implementation must conform to that contract.

---

## 32. MUnit Coverage

Initial MUnit coverage should include at least:

### Customer

```text
registered customer upsert success
guest customer upsert success
registered customer without external ID rejected
guest customer with external ID rejected
Salesforce transient failure translated correctly
Salesforce business validation translated correctly
```

### Order

```text
new order upsert
existing order upsert
invalid canonical order ID
Salesforce conflict
```

### Order lines

```text
single line
multiple lines
deterministic line identity
empty line collection rejected
partial Salesforce failure
```

### Common concerns

```text
correlation propagation
normalized errors
retry exhaustion
secure logging behavior
```

---

## 33. Out of Scope

The Salesforce System API does not own:

- Saleor webhook processing;
- public HTTP idempotency;
- canonical order persistence;
- CRM business workflow decisions;
- registered vs guest business policy;
- CRM Process API Object Store;
- Kafka;
- ERP;
- IBM MQ;
- WMS;
- global order status;
- cross-domain orchestration.

Those responsibilities belong to other architectural components.

---

## 34. Initial Implementation Goal

The first implementation should demonstrate a maintainable Salesforce integration boundary with:

- APIKit;
- Salesforce Connector;
- external ID based upsert;
- registered and guest customer persistence;
- deterministic order-line identity;
- modular DataWeave;
- normalized System API errors;
- bounded transient retries;
- correlation propagation;
- secure configuration;
- MUnit tests.

The objective is not simply to connect Mule to Salesforce.

The objective is to isolate Salesforce-specific complexity behind a stable internal integration contract.
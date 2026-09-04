# Enterprise Integration Reference Architecture

Reference architecture for enterprise integrations using APIs, asynchronous messaging, resilience patterns and observability.

The objective of this project is to demonstrate how heterogeneous enterprise systems can be integrated using modern integration patterns and technologies.

> This is a fictional project created for learning and portfolio purposes.  
> It does not contain code, information or architecture from any real company or client.

---

## 🎯 Project goals

This project focuses on:

- API-based system integration
- Synchronous and asynchronous communication
- Event-driven architecture
- Resilient integration patterns
- Error handling and retry strategies
- Message traceability
- API documentation
- Observability
- Containerized local environments
- Architecture decision documentation

---

## 🏢 Business scenario

A fictional retail company needs to integrate its e-commerce platform with several enterprise systems.

When a customer creates an order:

1. The e-commerce application sends the order through a REST API.
2. The integration layer validates and processes the request.
3. Customer information is synchronized with the CRM.
4. The order is asynchronously sent to the ERP.
5. Integration events are published through a message broker.
6. Every request can be traced across the different systems.
7. Failures are handled using retries and dead-letter mechanisms.

---

## 🏗 High-level architecture

```text
                         ┌─────────────────┐
                         │   E-Commerce    │
                         │    Web / App    │
                         └────────┬────────┘
                                  │
                               REST API
                                  │
                         ┌────────▼────────┐
                         │    Order API    │
                         └────────┬────────┘
                                  │
                         ┌────────▼─────────┐
                         │ Integration      │
                         │ Service          │
                         └──────┬─────┬─────┘
                                │     │
                             REST     │ Event
                                │     │
                         ┌──────▼──┐  │
                         │ CRM Mock│  │
                         └─────────┘  │
                                     ▼
                                ┌──────────┐
                                │  Kafka   │
                                └────┬─────┘
                                     │
                                ┌────▼─────┐
                                │ ERP Mock │
                                └──────────┘

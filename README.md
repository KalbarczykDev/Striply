# Striply

Striply is simulated payment infrastructure for developers, technical founders, and small SaaS teams that need to build and test payment flows before integrating a real payment provider. It lets merchants create an organization, define products and prices, generate hosted checkout sessions, run predefined simulator scenarios, inspect payment outcomes and refunds, and receive webhook notifications. Striply never collects or stores real card data; each payment uses a controlled scenario such as success, decline, insufficient funds, delayed processing, or provider timeout.

## Project Status

**Phase 1 — Basic platform**

Phase 0 architecture and requirements are complete. 
PostgreSQL persistence and database migrations are implemented; 
authentication and organization functionality are next.

## Planned First Vertical Slice

1. Create a product and price.
2. Create a hosted checkout session.
3. Complete a simulated payment.
4. Inspect the payment outcome.
5. Deliver a signed webhook event.
6. Issue a full or partial refund.

## Engineering Goals

The project is designed to demonstrate:

- Explicit payment state transitions
- Tenant isolation and authorization
- Idempotent mutation handling
- Concurrency-safe payments and refunds
- Transactional event delivery
- Retryable webhook delivery
- Testing across unit, integration, and end-to-end levels
- Security and observability designed from the beginning

## Architecture Approach

Striply begins as a modular monolith. Infrastructure and distributed components will only be introduced when they solve an identified reliability or scaling problem.

## Technology Direction

Java, Spring Boot, React, TypeScript, PostgreSQL, Docker, and Flyway form the initial stack. Redis, messaging, AWS, Terraform, Kubernetes, and distributed tracing are planned for later phases.

Every new infrastructure component must solve an identified reliability, operational, or scaling problem. Striply will begin as a modular monolith and will not claim scalability that has not been measured.

## Documentation

- [Product scope](docs/architecture/product-scope.md)
- [Functional requirements](docs/architecture/requirements)
- [Local PostgreSQL setup](docs/operations/local-database.md)

## Disclaimer

Striply is an educational portfolio project. It does not process real payments or collect real card or banking information.

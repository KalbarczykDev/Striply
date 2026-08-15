# ADR 0001: Begin with a Modular Monolith

## Status

Accepted

## Date

2026-08-05

## Architecture Version

Phase 0, version 0.1

## Context

Striply is a simulated payment-infrastructure platform being designed and implemented by one developer. Its early domain model and workflows will change as the first checkout-to-refund vertical slice is implemented and tested.

The core flow crosses catalog, checkout, payment, refund, webhook, and audit concerns. Several operations require strong transactional guarantees:

- a successful confirmation must transition the payment intent, create one payment, complete the checkout session, and record its event consistently;
- a refund must reserve available value and create its record without allowing concurrent refunds to exceed the payment amount;
- organization ownership must remain consistent across related records;
- later outbox records must commit atomically with business-state changes.

The accepted workload targets do not demonstrate a need for independent service scaling. There is one development team, no stable organizational ownership boundary between domains, and no operational platform for reliably deploying and observing multiple distributed services.

An ordinary unstructured monolith would keep transactions simple but would allow domain boundaries to erode. Starting with microservices would introduce network failure, distributed consistency, deployment, testing, security, and observability costs before those costs solve an identified problem.

## Decision

Striply will begin as a modular monolith implemented as one Spring Boot deployable application.

The backend will be divided into explicit domain modules:

```text
identity
organization
customer
catalog
checkout
payment
refund
webhook
developer
audit
shared
```

Each feature uses layered package structure such as:

```text
web
service
repository
model
security
config
api
```

Packages are created only when required. `web` owns controllers and transport DTOs,
`service` owns use-case orchestration and transactions,
`repository` owns Spring Data JPA repositories,
and `model` owns domain models.
`security`, `config`, and cross-feature `api` packages are optional.
This structure is a guideline, not a requirement to create identical empty layers in every feature.

### Boundary Rules

1. A module owns its domain behavior, persistence mappings, repositories, and internal implementation.
2. Other modules interact through documented application interfaces, stable shared identifiers, or explicit events.
3. A module must not call another module's internal service, repository, model, security, configuration, or web implementation.
4. Cross-module database reads are not a substitute for an application interface.
5. Shared code is limited to genuinely cross-cutting primitives such as money, identifiers, clocks, and error contracts. `shared` must not become a location for unrelated domain behavior.
6. Synchronous cross-module orchestration is allowed when the use case needs an immediate result or one local transaction.
7. Asynchronous events are introduced only when their delivery, ordering, duplication, failure, and schema-evolution behavior has been defined.
8. Automated architecture tests will verify permitted package dependencies as modules are implemented.

### Data and Transactions

Striply will initially use one PostgreSQL database. Each module owns its tables and persistence access even though the tables share a physical database.

The single application and database allow local ACID transactions across module-owned records when a business invariant requires them. Cross-module transaction boundaries must still be explicit at the application-use-case level; arbitrary transactions spanning unrelated modules are prohibited.

Database foreign keys and composite tenant constraints may cross module-owned tables when they protect a required invariant. Such constraints must be documented as coupling and reviewed before any module extraction.

### Deployment

The API begins as one runtime process. During the reliability phase, background work may use the same codebase with a separate worker runtime profile and deployment. This does not require extracting domain services.

The React application is a separate frontend container but does not change the backend modular-monolith decision.

## Alternatives Considered

### Unstructured Monolith

One deployable application without enforced module boundaries would minimize initial setup.

**Rejected because:** It would make it easy for controllers, services, and repositories to depend on unrelated domains. The resulting coupling would weaken reasoning, testing, tenant isolation, and any later extraction path.

### Microservices from the Beginning

Each major domain could be deployed as an independent service with its own API and data ownership.

**Rejected because:** The current workload, team structure, and deployment needs do not require independent services. The core workflow would immediately need remote calls, partial-failure handling, distributed tracing, service authentication, contract versioning, and eventual-consistency strategies. These costs would make financial invariants harder to implement without providing measured value.

### Modular Monolith with a Separate Database Schema per Module

Each module could own a distinct PostgreSQL schema from the start.

**Deferred because:** Schema separation can reinforce ownership but adds migration, cross-schema constraint, testing, and tooling decisions before package and repository boundaries have been proven. Module-owned tables in one initial schema are sufficient if access rules are enforced. Schema separation may be reconsidered when persistence boundaries stabilize.

### Serverless Functions per Use Case

Individual functions could implement checkout, payment, refund, and webhook operations.

**Rejected because:** This would fragment transaction boundaries and introduce deployment, cold-start, local-development, and observability complexity without a workload-driven reason.

## Consequences

### Positive

- Core payment and refund invariants can use local database transactions.
- The application is straightforward to run, test, debug, and deploy during early development.
- Refactoring module boundaries does not initially require network contract migrations.
- Integration tests can exercise real cross-module workflows in one process with PostgreSQL.
- Explicit modules still demonstrate architecture, cohesion, and dependency management.
- Background workers and selective service extraction remain possible later.

### Negative

- A process-level failure may affect the entire backend.
- Modules share deployment cadence, runtime resources, and application startup.
- One database creates opportunities for accidental cross-module persistence access.
- Poorly enforced boundaries can degrade into an unstructured monolith.
- Extracting a module later may require replacing local transactions and foreign keys with asynchronous consistency mechanisms.
- Independent scaling is limited until a runtime or service boundary is introduced.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Boundary erosion | Package conventions, explicit module APIs, architecture tests, and review of dependency changes |
| `shared` becomes a dumping ground | Admit only stable cross-cutting primitives; keep domain behavior in its owning module |
| Direct access to another module's tables | Repository ownership, code review, architecture tests where possible, and documented database ownership |
| Large cross-module transactions | Put transaction boundaries at named application use cases and record coupling in design documentation |
| One module consumes disproportionate resources | Measure per-module workloads and extract a worker or service only when evidence justifies it |
| Future extraction is difficult | Use public identifiers, explicit contracts, events with versioned schemas, and minimal internal-type leakage |
| False scalability claims | Publish performance results only with workload, environment, dataset, duration, latency, throughput, and error-rate evidence |

## Conditions That Would Cause Reconsideration

This decision must be reconsidered when one or more of the following is demonstrated:

- a module requires independently measured scaling that cannot be handled by stateless application replication or a separate worker;
- a module's failure must be isolated beyond what in-process resilience can provide;
- independent deployment cadence is operationally necessary;
- separate teams own stable domain boundaries and need independent delivery;
- compliance, security, or data-residency requirements demand a stronger process or data boundary;
- performance testing identifies the modular-monolith runtime as the limiting factor;
- a module uses a materially different availability or resource profile;
- database coupling prevents an otherwise valuable extraction and has a justified replacement consistency model.

Reconsideration does not automatically require microservices. Possible responses include a separate worker, schema isolation, a read model, a queue-backed component, or selective extraction of one bounded capability.

## Validation

This ADR is considered followed when:

- implemented domain packages have explicit ownership;
- module interactions use documented interfaces or events;
- architecture tests reject forbidden package dependencies;
- transaction boundaries are named and tested around business invariants;
- documentation distinguishes the modular monolith from later worker or service deployments;
- extraction proposals include measurements and a failure-consistency analysis.

## Related Documentation

- [Product scope](../architecture/product-scope.md)
- [System context](../architecture/system-context.md)
- [Container architecture](../architecture/container-diagram.md)
- [Initial data model](../architecture/initial-erd.md)
- [Non-functional requirements](../architecture/non-functional-requirements.md)

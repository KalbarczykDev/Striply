# Container Architecture

## Status

Review

## Date

15-08-2026

## Title and Scope

**Title:** Striply Container Diagram  
**Scope:** Applications and data stores that make up Striply, their responsibilities and communication, and the external systems they directly contact.

In C4 terminology, a container is a separately runnable or deployable application or data store. Java packages and React feature folders are not containers.

## Current Repository Reality

- **Partially implemented:** A minimal Spring Boot application skeleton exists.
- **Planned for Phase 1:** React web application, modular-monolith backend behavior, and PostgreSQL persistence.
- **Planned for Phase 3 or later:** Background worker, Redis, and message broker.
- **Planned for Phase 4:** Prometheus and Grafana monitoring stack.
- **Planned after the first vertical slice:** External email integration.

No container in this diagram should be interpreted as production-ready merely because it is shown.

## Container Diagram

Render [`container-diagram.puml`](../diagrams/container-diagram.puml) with PlantUML and its C4 standard library. The following is a readable fallback:

```text
Merchant member ──HTTPS──> React Web Application <──HTTPS── Customer
                                  │
                                  │ HTTPS/JSON
                                  ▼
Merchant application ─────> Spring Boot Application
                                  │
                                  │ JDBC transactions
                                  ▼
                              PostgreSQL

Spring Boot Application ──HTTPS/HMAC──> Webhook receiver [initial]

Background Worker [Phase 3] ──polls outbox──> PostgreSQL
Background Worker [Phase 3] ──HTTPS/HMAC──> Webhook receiver
Background Worker [Phase 3+] <──publish/consume──> Message Broker [planned]
Spring Boot Application ───────> Redis [planned]
Monitoring Stack [Phase 4] ──scrapes──> Application and Worker
Background Worker ──HTTPS──> Email Service [planned]
```

The C4-PlantUML source is authoritative.

## Containers

### React Web Application

- **Technology:** React and TypeScript
- **Status:** Planned for Phase 1
- **Responsibilities:** Serve the authenticated merchant dashboard and public hosted checkout as separate route and feature modules; manage presentation state; perform accessible form validation; call the backend through typed API contracts.
- **Security boundary:** Contains no trusted authorization logic or reusable secrets. Dashboard and checkout routes have separate authentication assumptions even though they share one deployment.
- **Scaling:** Served as static assets. Separate dashboard and checkout deployments require evidence of independent release, security, or scaling needs.

### Spring Boot Application

- **Technology:** Java, Spring Boot, Spring MVC, Spring Security
- **Status:** Minimal bootstrap exists; business behavior is planned
- **Responsibilities:** Expose APIs; authenticate users and API clients; enforce organization authorization; execute catalog, checkout, payment, refund, webhook, developer, and audit use cases; run the internal payment simulator; coordinate PostgreSQL transactions.
- **Architecture:** Modular monolith with explicit domain-module boundaries.
- **Initial asynchronous limitation:** Before the worker exists, basic webhook handling may run from the application. Any event-loss or request-coupling limitation must be documented and removed during the reliability phase.

### PostgreSQL

- **Technology:** PostgreSQL with Flyway-managed schema
- **Status:** Planned for Phase 1
- **Responsibilities:** Authoritative storage for identities, organizations, catalog, checkout sessions, payment intents, payments, refunds, webhooks, audit entries, idempotency records, and outbox events as each capability is introduced.
- **Reliability role:** Enforce referential, uniqueness, and concurrency-sensitive invariants in addition to application validation.
- **Access rule:** Only Striply backend processes access the database. Browsers and merchant applications never connect directly.

### Background Worker

- **Technology:** Java and Spring Boot using the backend codebase with a dedicated runtime profile
- **Status:** Planned for Phase 3 after the synchronous vertical slice
- **Responsibilities:** Poll durable pending work, publish outbox events, execute webhook deliveries and retries, and later perform other bounded asynchronous jobs.
- **Deployment:** Separately runnable from the API so it can scale and fail independently while reusing domain and application contracts.
- **Delivery guarantee:** At least once. Handlers and external receivers must tolerate duplicates.

### Redis

- **Technology:** Redis
- **Status:** Planned for Phase 3 only after a concrete use case is measured
- **Candidate responsibilities:** Distributed rate-limiting counters, short-lived coordination, or carefully selected cache entries.
- **Constraint:** PostgreSQL remains authoritative. Payment correctness, refund limits, and durable idempotency results must not depend solely on Redis availability.

### Message Broker

- **Technology:** Undecided; evaluate SQS, RabbitMQ, or Kafka only against identified requirements
- **Status:** Planned for Phase 3 or later; not yet accepted as necessary
- **Candidate responsibilities:** Buffer asynchronous work and decouple publishers from consumers after the database-backed outbox and worker expose a concrete throughput or fan-out need.
- **Constraint:** Introducing a broker does not create exactly-once processing. Consumers remain idempotent.

### Monitoring Stack

- **Technology:** Prometheus and Grafana, with OpenTelemetry integration evaluated separately
- **Status:** Planned for Phase 4
- **Responsibilities:** Collect metrics, visualize operational behavior, support alerting, and help correlate payment and webhook failures with application health.
- **Constraint:** Monitoring unavailability must not alter payment correctness.

## External Systems Shown

- **Merchant application:** Calls the organization-scoped API using an API key.
- **External webhook receiver:** Accepts signed event deliveries and may be slow, unavailable, or return untrusted responses.
- **External email service:** Planned provider for invitations and transactional messages; provider selection is deferred.

Human actors remain outside the Striply system boundary and access Striply through the React application or operational tooling.

## Communication and Data Flow

| Source | Destination | Direction and mode | Protocol | Data or purpose | Status |
| --- | --- | --- | --- | --- | --- |
| Merchant member | React application | Inbound, synchronous | HTTPS | Dashboard pages and user input | Planned Phase 1 |
| Customer | React application | Inbound, synchronous | HTTPS | Hosted checkout and simulator selection | Planned Phase 1 |
| React application | Spring Boot application | Inbound, synchronous | HTTPS/JSON | Typed API requests and responses | Planned Phase 1 |
| Merchant application | Spring Boot application | Inbound, synchronous | HTTPS/JSON | API-key-authenticated merchant operations | Planned Phase 1–2 |
| Spring Boot application | PostgreSQL | Synchronous transaction | JDBC/TLS where supported | Authoritative business state | Planned Phase 1 |
| Spring Boot application | Webhook receiver | Outbound synchronous attempt | HTTPS/JSON with HMAC | Basic event delivery before worker extraction | Initial vertical slice |
| Background worker | PostgreSQL | Polling and transactions | JDBC/TLS where supported | Claim outbox and delivery work, store outcomes | Planned Phase 3 |
| Background worker | Webhook receiver | Outbound attempt, asynchronous lifecycle | HTTPS/JSON with HMAC | Retried webhook delivery | Planned Phase 3 |
| Background worker | Message broker | Asynchronous publish and consume | Broker protocol | Optional buffered event or job delivery | Planned Phase 3+ |
| Spring Boot application | Redis | Synchronous, non-authoritative | TLS-enabled Redis protocol | Rate limits or selected ephemeral data | Planned Phase 3 |
| Monitoring stack | Application and worker | Pull and receive telemetry | HTTP metrics and telemetry protocols | Metrics, health, and traces | Planned Phase 4 |
| Background worker | Email service | Outbound asynchronous job | HTTPS provider API | Minimal transactional email data | Planned later |

## Trust Boundaries

1. **Browser boundary:** React executes on an untrusted client. Backend authorization never relies on hidden UI controls.
2. **Merchant integration boundary:** Merchant applications and webhook receivers are external and untrusted despite organization credentials or signatures.
3. **Application-to-data boundary:** Only backend containers connect to PostgreSQL, Redis, or a broker. Credentials are unique per runtime role and least-privileged.
4. **Asynchronous boundary:** Messages, outbox rows, jobs, and webhook events may be delivered more than once. Processing must be idempotent.
5. **Observability boundary:** Telemetry may contain identifiers needed for diagnosis but must exclude credentials and unnecessary personal data.

## Key Decisions

1. The merchant dashboard and hosted checkout share one React deployment initially but remain separate feature and route modules.
2. The backend begins as one Spring Boot modular monolith.
3. PostgreSQL is the first authoritative data store and transaction boundary.
4. The worker uses the backend codebase but becomes a separately runnable container during the reliability phase.
5. Redis, a broker, and the monitoring stack are introduced only when their prerequisite phases and concrete use cases exist.

## Assumptions

- Browser-to-backend communication uses HTTPS and JSON APIs.
- Local development may serve React through a development server while production-like environments serve static assets separately; this does not change the logical container responsibility.
- PostgreSQL can support the accepted initial workload before caches or read replicas are justified.
- The first worker deployment can use database-backed work claiming before a broker is required.
- The internal simulator remains inside the Spring Boot application until evidence supports a different boundary.

## Known Limitations

- This is a target container architecture, not a depiction of completed software.
- Deployment nodes, subnets, load balancers, secrets managers, and cloud services belong to deployment diagrams.
- Backend module dependencies require later component diagrams and automated boundary tests.
- Broker technology and event topology remain undecided.
- The diagram has not been render-tested because PlantUML is not installed in the current workspace.

## Conditions for Reconsideration

- Split dashboard and checkout deployments if independent security headers, release cadence, ownership, or scaling materially justify the operational cost.
- Extract a backend module only after measured scaling, failure-isolation, deployment, or organizational needs cannot be handled within the modular monolith.
- Introduce Redis or a broker only after defining failure behavior, authoritative data ownership, operational cost, and measurable success criteria.

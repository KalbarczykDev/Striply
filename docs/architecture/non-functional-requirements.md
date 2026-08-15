# Non-Functional Requirements

## Status

Review

## Date

15-08-2026

## Workload Assumptions

- Registered organizations: up to 1,000
- Active organizations per day: up to 100
- Products per organization: up to 100
- Customers per organization: up to 10,000
- Checkout sessions per day: up to 10,000
- Peak checkout-session creation rate: 20 requests per second
- Peak simultaneous payment confirmations: 50
- Webhook endpoints per organization: up to 5
- Webhook deliveries per day: up to 25,000
- Data-retention period: 2 years for payment and audit history

These values are initial design limits for a portfolio-scale deployment, not measured production capacity.

## Performance and Capacity

### NFR-PERF-01 — Read latency

Under the defined workload, authenticated catalog and payment read requests must complete within 300 ms at p95 and 750 ms at p99, measured at the server boundary.

### NFR-PERF-02 — Mutation latency

Product, price, and checkout-session creation requests must complete within 500 ms at p95, excluding network time outside Striply.

### NFR-PERF-03 — Payment confirmation latency

Immediate simulator scenarios must return a final result within 1,000 ms at p95. Delayed-processing and provider-timeout scenarios are excluded from this target and must be measured separately.

### NFR-PERF-04 — Peak capacity

Striply must sustain 20 checkout-session creation requests per second and 50 simultaneous payment confirmations for 15 minutes without violating monetary or tenant-isolation invariants.

### NFR-PERF-05 — Error rate

During a performance test at the defined workload, unexpected server errors must remain below 1% of requests. Deliberate simulator failures, authorization denials, and client validation errors do not count as server errors.

## Availability and Reliability

### NFR-AVL-01 — Service availability

When Striply is deployed to a production-like environment, its public API and hosted checkout should achieve 99.5% monthly availability, excluding announced maintenance. This is a future operational target and is not considered achieved until monitored for a complete month.

### NFR-REL-01 — Payment uniqueness

A checkout session must never produce more than one successful payment, including under concurrent confirmation attempts, client retries, or application restarts.

### NFR-REL-02 — Refund limits

The sum of accepted refunds for a payment must never exceed the original payment amount, including under concurrent refund requests.

### NFR-REL-03 — Atomic business changes

Business state changes and the events representing those changes must eventually be stored atomically in one PostgreSQL transaction. Until the transactional outbox is implemented, documentation must identify event-loss windows explicitly.

### NFR-REL-04 — Webhook delivery

Webhook delivery is at least once. Striply may deliver the same event more than once, and every payload must contain a stable event identifier that receivers can use for deduplication.

### NFR-REL-05 — Webhook retries

Failed webhook attempts must use bounded exponential backoff and stop after a documented maximum number of attempts. A webhook receiver's failure must not roll back an already committed payment, refund, or checkout change.

### NFR-REL-06 — Idempotent mutations

When idempotency support is introduced, repeated mutation requests with the same key and equivalent payload must replay the original result. Reuse of the key with a different payload must be rejected, and concurrent requests using the same key must not create duplicate business operations.

## Security and Privacy

### NFR-SEC-01 — Tenant isolation

Every protected operation must verify organization ownership from authenticated context and persisted relationships. Cross-organization access is a critical defect and must be covered by automated negative tests.

### NFR-SEC-02 — Authentication secrets

Passwords, API keys, refresh tokens, and webhook signing secrets must never be stored or logged in plaintext when a one-way hash is sufficient. Secrets that must be recoverable for cryptographic operations must be encrypted using managed key material.

### NFR-SEC-03 — Public identifiers

External APIs must expose non-sequential prefixed identifiers rather than sequential database identifiers.

### NFR-SEC-04 — Payment data boundary

Striply must not request, collect, transmit, or store real card numbers, bank-account details, or security codes. The hosted checkout accepts only documented simulator scenarios.

### NFR-SEC-05 — Transport security

Production-style external traffic must use TLS. Webhook endpoints must use HTTPS except for explicitly configured loopback addresses in local development.

### NFR-SEC-06 — Input and output protection

All external input must be validated at the trust boundary. Error responses must not expose stack traces, credentials, internal database identifiers, or another organization's resource data.

### NFR-SEC-07 — Auditability

Authentication events, organization membership changes, API-key lifecycle events, webhook configuration changes, refunds, and security-relevant authorization denials must create audit records containing the actor, organization, action, target, outcome, and timestamp without storing secrets.

### NFR-SEC-08 — Personal data minimization

Striply must collect only personal data required for the simulated payment workflow. Access to customer email addresses must be organization-scoped, and logs and metrics must not contain full email addresses or authentication secrets.

## Data Integrity and Recovery

### NFR-DATA-01 — Monetary representation

Monetary amounts must use integer minor units and an explicit supported ISO 4217 currency code. Floating-point types must not represent persisted or calculated monetary values.

### NFR-DATA-02 — Referential integrity

Database constraints must enforce required relationships and uniqueness invariants. Application validation alone is insufficient for invariants vulnerable to concurrent requests.

### NFR-DATA-03 — Historical consistency

Checkout sessions, payments, refunds, events, and delivery attempts must retain immutable historical values needed to explain the transaction even if catalog or endpoint configuration later changes.

### NFR-DR-01 — Recovery point objective

For a future production-like deployment, the target recovery point objective is 15 minutes. The achieved value must be demonstrated through backup configuration and a restore exercise.

### NFR-DR-02 — Recovery time objective

For a future production-like deployment, the target recovery time objective is 4 hours. A documented recovery exercise must verify the restore procedure before this target is considered achieved.

### NFR-DR-03 — Backup verification

Backups are not considered reliable merely because a backup job reports success. Restore tests must verify database readability, required schema, and representative payment and audit records.

## Observability and Operations

### NFR-OBS-01 — Structured logs

Application logs must be structured and include timestamp, severity, service or module, environment, correlation identifier, and organization identifier where safe. Secrets and full personal data must be excluded.

### NFR-OBS-02 — Request correlation

Every inbound request must receive or generate a correlation identifier that is propagated through synchronous calls, background processing, and webhook-delivery records.

### NFR-OBS-03 — Metrics

Striply must expose metrics for request latency and errors, payment outcomes, refund creation, webhook attempts and failures, pending asynchronous work, and idempotency replays. Each alert must document the operator action expected when it fires.

### NFR-OBS-04 — Health reporting

The application must distinguish liveness from readiness. Readiness must report unavailable when a required dependency prevents safe request handling, while optional dependency degradation must be reported without unnecessarily restarting the process.

### NFR-OBS-05 — Traceability

An operator must be able to trace a checkout session through payment confirmation, payment or failure outcome, refund creation, event creation, and webhook-delivery attempts using public resource and correlation identifiers.

## Maintainability and Testing

### NFR-MNT-01 — Module boundaries

The modular monolith must keep domain modules explicit. Cross-module access must use documented application interfaces or events rather than direct access to another module's internal implementation.

### NFR-MNT-02 — Database migrations

All persistent schema changes must be versioned, repeatable in a clean environment, and applied through Flyway migrations. Existing migration files must not be edited after they have been shared.

### NFR-MNT-03 — Automated verification

Every change must pass automated tests appropriate to its risk. Domain invariants require unit tests; persistence, constraints, transactions, authentication, and authorization require integration tests; and the complete checkout-to-refund workflow requires end-to-end tests.

### NFR-MNT-04 — Documentation accuracy

Documentation and diagrams must label capabilities as implemented, partially implemented, planned, rejected, or experimental. A change is incomplete when it makes an affected requirement, diagram, ADR, API description, or operational procedure inaccurate.

### NFR-MNT-05 — Reproducible local development

A new contributor must eventually be able to start the required local services and run the verification suite using documented commands and repository-controlled configuration, without relying on undocumented machine-specific setup.

## Accessibility

### NFR-ACC-01 — Keyboard and assistive technology

Merchant and hosted-checkout interfaces must use semantic controls, support keyboard navigation, provide visible focus indicators, and expose accessible names and validation errors to assistive technologies.

### NFR-ACC-02 — Accessibility target

User-facing interfaces should conform to WCAG 2.2 Level AA for the implemented flows. Automated accessibility checks must be supplemented by keyboard and screen-reader-oriented manual checks for the checkout flow.

## Validation Status

All requirements in this document remain planned until verified. Performance reports, security reviews, failure tests, recovery exercises, and observability evidence must be linked here as they are produced.

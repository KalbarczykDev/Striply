# First Vertical-Slice Backlog

## Status

Review

## Date

15-08-2026

## Goal

Deliver the smallest coherent Striply workflow in which a merchant can authenticate, operate one organization, create a product and price, create a hosted checkout session, complete a deterministic simulated payment, inspect the result, deliver a basic signed webhook, and issue a full or partial refund.

The slice proves domain behavior and local transactional consistency before introducing idempotency records, a transactional outbox, background workers, Redis, a message broker, cloud deployment, or Kubernetes.

## Priority and Dependencies

```text
VS-001 Module boundaries
    ↓
VS-002 PostgreSQL and migrations
    ↓
VS-003 Authentication
    ↓
VS-004 Organizations and authorization
    ↓
VS-005 React shell and organization context
    ↓
VS-006 Products and prices
    ↓
VS-007 Checkout sessions
    ↓
VS-008 Hosted checkout
    ↓
VS-009 Payment-intent state machine
    ↓
VS-010 Payment simulator
    ↓
VS-011 Concurrent payment confirmation
    ↓
VS-012 Payment dashboard
    ↓
VS-013 Basic signed webhooks
    ↓
VS-014 Full and partial refunds
    ↓
VS-015 End-to-end verification
```
---

## VS-001 — Establish Backend Module Boundaries

**Priority:** P0

**Depends on:** Accepted Phase 0 architecture documents

### Problem

The Spring Boot skeleton has no enforced domain structure. Without boundaries, early features can couple controllers, services, repositories, and entities across unrelated domains.

### Learning Objective

Learn how a modular monolith differs from an unstructured monolith and how automated architecture checks protect dependency direction.

### Functional Requirements

- Establish top-level packages for the accepted backend modules.
- Provide one minimal application entry point without implementing payment features.
- Define which packages are public module interfaces and which are internal.

### Non-Functional Requirements

- Package dependencies follow ADR 0001.
- Architecture checks fail on prohibited cross-module dependencies.
- The application starts and the existing verification suite passes.

### Constraints

- One Spring Boot deployable application.
- Do not introduce microservices, messaging, Redis, or speculative abstractions.
- Do not create empty layers or base classes without an immediate use.

### Acceptance Criteria

- Module packages and dependency rules are documented.
- At least one architecture test demonstrates an allowed dependency and one prohibited dependency rule.
- No business feature is implemented as part of this ticket.

### Required Tests

- Application-context smoke test.
- Automated package or architecture-boundary tests.

### Required Documentation

- Add a backend module map and naming conventions.
- Update ADR 0001 only if implementation exposes a decision mismatch.

### Relevant Diagram Updates

- No diagram change unless container responsibilities change.

### Definition of Done

The application builds, tests pass, boundaries are executable rather than aspirational, and documentation matches the implemented package structure.

---

## VS-002 — Add PostgreSQL, Flyway, and Local Docker Compose

**Priority:** P0

**Depends on:** VS-001

### Problem

The first slice requires transactional persistence, but no reproducible PostgreSQL environment or migration process exists.

### Learning Objective

Learn schema migration discipline, database-backed integration testing, and the difference between application validation and database constraints.

### Functional Requirements

- Start PostgreSQL locally through Docker Compose.
- Connect Spring Boot through environment-based configuration.
- Introduce Flyway and an initial migration required by the next identity ticket only.

### Non-Functional Requirements

- A clean database can be created entirely from versioned migrations.
- Production or shared-environment credentials are never committed.
- Clearly identified local-only credentials may be committed for reproducible development.

### Constraints

- PostgreSQL is the only database.
- Do not create every ERD table in advance.
- Shared migrations are immutable after merge.

### Acceptance Criteria

- Documented commands start and stop local PostgreSQL.
- Starting the application against a clean local PostgreSQL database applies all pending Flyway migrations successfully.
- Invalid explicitly supplied database configuration fails with a useful diagnostic; missing environment variables use documented local defaults.

### Required Tests

- No standalone migration or datasource smoke test is required.
- Database constraints introduced by later feature tickets must be verified through feature-specific PostgreSQL integration tests.

### Required Documentation

- Local database setup, environment variables, migration rules, and troubleshooting.

### Relevant Diagram Updates

- Update the container document only if actual communication differs from the accepted design.

### Definition of Done

- A new contributor can create the local database, start the application, and apply all Flyway migrations using documented commands without machine-specific configuration.
---

## VS-003 — Implement User Authentication and Token Lifecycle

**Priority:** P0

**Depends on:** VS-002

### Problem

Merchant operations require an authenticated human identity, but registration, login, password storage, and token lifecycle do not exist.

### Learning Objective

Learn password hashing, short-lived access tokens, refresh-token rotation, credential revocation, secure error responses, and authentication auditing.

### Functional Requirements

- Register a user with normalized unique email and password.
- Authenticate valid credentials.
- Issue a short-lived access token and a rotatable refresh token.
- Rotate refresh tokens on use and reject replay of an invalidated token family.
- Log out by revoking the applicable refresh-token state.

### Non-Functional Requirements

- Passwords and refresh-token secrets are stored only as secure hashes.
- Authentication errors do not reveal whether an email exists.
- Tokens and credentials are excluded from logs.
- Brute-force and rate-limit requirements are documented even if enforcement is deferred.

### Constraints

- Do not add social login, password reset, email verification, or external identity providers.
- Local development may use the documented default signing key. Shared and deployed environments supply their signing key through external configuration.
- Organization authorization is handled in VS-004, not inferred here.

### Acceptance Criteria

- A user can register, log in, refresh once, and log out.
- Duplicate normalized email registration is rejected.
- Old refresh tokens cannot be reused after rotation.
- Invalid credentials return a stable, non-revealing error contract.

### Required Tests

- Unit tests for token-expiration and rotation decisions.
- PostgreSQL integration tests for email uniqueness and refresh-token replay handling.
- Security integration tests for protected and public endpoints.

### Required Documentation

- Authentication flow, token lifetimes, storage boundary, threat assumptions, and deferred protections.

### Relevant Diagram Updates

- Add a login and refresh sequence diagram.
- Update the ERD with refresh-token entities before the migration is written.

### Definition of Done

Authentication behavior and failure paths are tested, secrets are protected, and documentation describes the implemented—not aspirational—token lifecycle.

---

## VS-004 — Implement Organizations, Membership, and Organization Context

**Priority:** P0

**Depends on:** VS-003

### Problem

Authentication identifies a user but does not establish which tenant they may operate or what they may do.

### Learning Objective

Learn tenant isolation, membership modeling, role-based permission decisions, organization switching, and defense-in-depth ownership checks.

### Functional Requirements

- Let an authenticated user create an organization and become its `OWNER`.
- List organizations in which the user has active membership.
- Select a current organization through the accepted server-validated request context.
- Enforce an initial permission matrix for `OWNER`, `ADMIN`, `DEVELOPER`, and `ANALYST`.
- Record organization and membership security events in the available audit mechanism.

### Non-Functional Requirements

- A caller cannot establish authority using an arbitrary organization identifier.
- Missing and foreign resources use non-enumerating error behavior where required.
- Tenant-isolation defects are treated as critical.

### Constraints

- Member invitations may be deferred if organization creation and one-member operation are sufficient for the first slice; the deferral must be explicit.
- Do not encode authorization solely in the React UI.

### Acceptance Criteria

- Organization creation atomically creates owner membership.
- A user can switch only to an organization with active membership.
- Permission checks are centralized and testable.
- Cross-organization reads and mutations are rejected without foreign data leakage.

### Required Tests

- Unit tests for the permission matrix.
- Integration tests for membership uniqueness and owner creation transaction.
- Negative cross-tenant tests for every organization endpoint.

### Required Documentation

- Permission matrix, organization-context contract, protected-resource checklist, and invitation deferral if applicable.

### Relevant Diagram Updates

- Update the ERD if membership fields change.
- Add an identity and authorization component diagram when implementation stabilizes.

### Definition of Done

Every protected request has authenticated identity, validated organization context, explicit permission, negative tenant tests, and appropriate audit behavior.

---

## VS-005 — Create the React Shell and Authenticated Organization Context

**Priority:** P0

**Depends on:** VS-003 and VS-004 API contracts

### Problem

There is no frontend structure for authenticated merchant workflows or public checkout, creating a risk of scattered API calls and inconsistent authorization handling.

### Learning Objective

Learn typed API boundaries, authentication state, organization-context propagation, permission-aware routing, and accessible application-shell design.

### Functional Requirements

- Create one React and TypeScript application with separate dashboard and hosted-checkout feature areas.
- Implement registration, login, token refresh, logout, and organization selection against accepted contracts.
- Provide dashboard navigation placeholders only for implemented routes.
- Centralize API transport and typed error handling.

### Non-Functional Requirements

- Include loading, empty, validation, unauthenticated, unauthorized, and unexpected-error states.
- Provide accessible labels, keyboard navigation, and visible focus.
- Do not store long-lived secrets in unsafe browser storage without an explicit threat analysis.

### Constraints

- One frontend deployment initially.
- Do not implement product, checkout, or payment features in this ticket.
- UI permission checks improve experience but never replace backend authorization.

### Acceptance Criteria

- A merchant can authenticate, select an organization, refresh the page according to the chosen session design, and log out.
- The API client uses one typed transport boundary.
- Protected routes handle expired authentication and forbidden access consistently.

### Required Tests

- Component tests for authentication and organization-context states.
- Accessibility checks for shell navigation and forms.
- One browser test covering login, organization selection, and logout.

### Required Documentation

- Frontend folder structure, API-contract strategy, authentication storage decision, and error-state conventions.

### Relevant Diagram Updates

- Update the container document only if the frontend becomes more than one deployment.

### Definition of Done

The frontend shell supports the authenticated organization context with typed contracts, tested state handling, and no feature-specific shortcuts.

---

## VS-006 — Create Products and One-Time Prices

**Priority:** P0

**Depends on:** VS-004 and VS-005

### Problem

Checkout needs an organization-owned immutable monetary offer, but products and prices do not exist.

### Learning Objective

Learn tenant-scoped CRUD boundaries, immutable value modeling, minor-unit money, and database-enforced ownership relationships.

### Functional Requirements

- Implement FR-01 and FR-02 through backend APIs and merchant UI.
- Create active products with `prod_` identifiers.
- Create immutable one-time prices with `price_` identifiers in `PLN`, `EUR`, or `USD`.
- List products and their prices for the current organization.

### Non-Functional Requirements

- Enforce field, monetary, currency, permission, and tenant rules from the accepted requirements.
- Use integer minor units end to end.
- Do not expose database identifiers.

### Constraints

- No recurring prices, tax, discounts, inventory, or price mutation.
- A new amount or currency requires a new price.

### Acceptance Criteria

- Authorized merchants can create and view organization-owned products and prices.
- Foreign-product price creation is rejected.
- Inactive products cannot receive new prices.
- UI provides loading, empty, validation, success, and error feedback.

### Required Tests

- Unit tests for money and catalog validation.
- PostgreSQL integration tests for constraints and cross-tenant relationships.
- Controller security tests and frontend component tests.

### Required Documentation

- Product and price API contracts, example minor-unit values, and catalog ownership rules.

### Relevant Diagram Updates

- Update ERD and catalog component documentation if implementation differs.

### Definition of Done

FR-01 and FR-02 are demonstrable through UI and API, all ownership rules have negative tests, and documentation matches the final contracts.

---

## VS-007 — Create Checkout Sessions

**Priority:** P0

**Depends on:** VS-006

### Problem

Merchants need a stable, time-limited handoff from mutable catalog data to public checkout.

### Learning Objective

Learn snapshot modeling, expiration semantics, redirect allowlisting, public identifiers, and transactional creation.

### Functional Requirements

- Implement FR-03 for one price and quantity from `1` to `100`.
- Snapshot product name, unit amount, total amount, currency, quantity, and optional customer email.
- Create an `OPEN` session with `cs_` identifier and 30-minute expiration.
- Return the hosted-checkout URL and approved success and cancellation destinations.

### Non-Functional Requirements

- Verify price and product are active and owned by the current organization.
- Prevent open redirects through an accepted organization URL policy.
- Preserve snapshot values after catalog changes.

### Constraints

- No multi-item cart, tax, discounts, shipping, or idempotency record yet.
- Duplicate successful requests may create separate sessions and must be documented.

### Acceptance Criteria

- An authorized merchant creates a session and receives its URL and expiration.
- Invalid quantity, inactive catalog, foreign tenant, malformed email, and unapproved redirect URLs are rejected.
- Later product or price changes do not change the session snapshot.

### Required Tests

- Unit tests for totals, expiration, and redirect policy.
- Integration tests for snapshots and tenant constraints.
- API security and validation tests.

### Required Documentation

- Checkout-creation API, snapshot rationale, duplicate-request limitation, and redirect security policy.

### Relevant Diagram Updates

- Add a checkout-session creation sequence diagram.

### Definition of Done

FR-03 is tested and documented, snapshot invariants are persisted, and rejected requests leave no session.

---

## VS-008 — Build the Public Hosted Checkout

**Priority:** P0

**Depends on:** VS-007 and VS-005

### Problem

Customers cannot inspect or act on the session created by a merchant.

### Learning Objective

Learn capability-style public access, strict data minimization, expiration UX, accessible forms, and separation from authenticated merchant routes.

### Functional Requirements

- Implement FR-04 in the public checkout route.
- Display only snapshotted merchant and product information.
- Collect or confirm customer email and expose supported simulator scenarios without real payment fields.
- Show open, expired, completed, canceled, missing, and unavailable states.

### Non-Functional Requirements

- A checkout URL grants access only to one session.
- Public responses expose no organization-private data, credentials, or internal identifiers.
- Form and status content meets accepted accessibility requirements.

### Constraints

- No customer account, real card form, saved payment method, or merchant-dashboard navigation.
- Payment submission remains disabled until VS-010 and VS-011 provide the contract.

### Acceptance Criteria

- Open sessions render their exact snapshot.
- Expired or terminal sessions cannot show an active submission control.
- Unknown and inaccessible sessions do not leak merchant existence.
- Keyboard and screen-reader-oriented checks cover the form.

### Required Tests

- Component tests for every session state.
- API tests for public response minimization.
- Browser test for opening an active and expired session.

### Required Documentation

- Public checkout contract, capability-boundary assumptions, and accessibility states.

### Relevant Diagram Updates

- Update checkout component documentation when its route and API boundary stabilize.

### Definition of Done

FR-04 is demonstrable without payment submission, terminal states are safe, and public data exposure has negative tests.

---

## VS-009 — Implement the Payment-Intent State Machine

**Priority:** P0

**Depends on:** VS-007

### Problem

Payment processing cannot be represented safely through arbitrary status mutation.

### Learning Objective

Learn explicit state-machine modeling, domain invariants, transition failures, retry semantics, and terminal versus unresolved states.

### Functional Requirements

- Model `REQUIRES_PAYMENT_METHOD`, `REQUIRES_CONFIRMATION`, `PROCESSING`, `SUCCEEDED`, `FAILED`, and `CANCELED`.
- Define valid transitions, triggers, side effects, retry behavior, and terminal states.
- Associate sequential payment intents with one checkout session.
- Reject invalid transitions through domain behavior.

### Non-Functional Requirements

- Domain tests cover every valid and representative invalid transition.
- Status fields cannot be changed through unrestricted public setters.
- Transition failures return stable application errors without leaking internals.

### Constraints

- Do not call the simulator or create a payment yet.
- Provider timeout remains unresolved rather than immediately failed.

### Acceptance Criteria

- A transition table and state diagram are accepted before implementation.
- Domain behavior is the only supported transition path.
- Terminal failed intents permit a later sequential intent; unresolved intents prevent unsafe immediate retry.

### Required Tests

- Unit tests for all valid transitions, invalid transitions, terminal behavior, and retry eligibility.

### Required Documentation

- State-transition table with trigger, guard, side effect, failure behavior, and retry rule.

### Relevant Diagram Updates

- Add a payment-intent Mermaid state diagram.

### Definition of Done

The state machine is fully specified, implemented, and unit-tested without simulator or controller coupling.

---

## VS-010 — Implement the Deterministic Payment Simulator

**Priority:** P0

**Depends on:** VS-009

### Problem

Striply needs repeatable payment-provider behavior without accepting real financial credentials.

### Learning Objective

Learn provider-port design, deterministic failure simulation, timeout boundaries, and mapping external-style outcomes into domain transitions.

### Functional Requirements

- Implement named success, decline, insufficient-funds, delayed-processing, and provider-timeout scenarios.
- Return a documented provider-neutral result to the payment application service.
- Record safe outcome codes without real payment data.

### Non-Functional Requirements

- Identical scenario inputs produce controlled outcomes suitable for repeatable tests.
- Simulator delays are bounded and configurable for testing.
- No endpoint accepts card numbers, security codes, or bank details.

### Constraints

- Simulator implementation remains behind a payment-provider port.
- Do not add a Stripe adapter or external network call.
- The simulator does not decide organization authorization or transaction boundaries.

### Acceptance Criteria

- Every documented scenario maps to an accepted payment-intent outcome.
- Delayed and timeout scenarios are distinguishable from ordinary declines.
- Unknown scenarios are rejected safely.

### Required Tests

- Unit tests for deterministic outcome mapping and configuration bounds.
- Contract tests between simulator adapter and provider port.

### Required Documentation

- Scenario catalog, intended use, outcome semantics, and real-payment-data prohibition.

### Relevant Diagram Updates

- Add or update the payment component diagram when the provider port exists.

### Definition of Done

The simulator is deterministic, bounded, isolated behind a port, and incapable of accepting real payment credentials.

---

## VS-011 — Confirm Payments Safely Under Concurrency

**Priority:** P0

**Depends on:** VS-009 and VS-010

### Problem

Simultaneous confirmation or retry can otherwise create duplicate successful payments or invalid checkout transitions.

### Learning Objective

Learn transaction design, optimistic or pessimistic locking, unique constraints, ambiguous timeout handling, and concurrency-focused integration testing.

### Functional Requirements

- Implement FR-05 payment submission for an open, unexpired checkout session.
- Create and process a payment intent using the chosen simulator scenario.
- On success, create one payment and mark the checkout session `COMPLETED` atomically.
- On terminal failure, leave the checkout eligible for a later sequential intent.
- Prevent unresolved timeout outcomes from allowing an unsafe immediate retry.

### Non-Functional Requirements

- One checkout session can produce at most one successful payment.
- Concurrent confirmation attempts preserve valid state transitions.
- The implementation meets accepted immediate-scenario latency targets under the defined test workload.

### Constraints

- Choose and justify locking, uniqueness, and transaction mechanisms before coding.
- No idempotency-key record or asynchronous provider reconciliation yet.
- A known timeout limitation must be visible and documented.

### Acceptance Criteria

- Success creates exactly one payment, one successful intent, and one completed checkout state.
- Declines create no successful payment.
- Concurrent confirmation tests cannot create duplicate successful payments.
- A frontend timeout after commit can be reconciled through a safe status read.

### Required Tests

- Unit tests for application decisions and error mapping.
- PostgreSQL integration tests with real concurrent transactions.
- Tests for duplicate confirmation, terminal retry, unresolved timeout, expiration race, and response loss after commit.

### Required Documentation

- Transaction boundary, selected locking strategy, alternatives, failure windows, and retry guidance.

### Relevant Diagram Updates

- Add successful, failed, timeout, and concurrent-confirmation sequence diagrams.
- Update ERD or state diagram if implementation reveals a mismatch.

### Definition of Done

FR-05 is demonstrable through hosted checkout, concurrency invariants hold in PostgreSQL tests, and failure behavior is documented honestly.

---

## VS-012 — Display Payments in the Merchant Dashboard

**Priority:** P0

**Depends on:** VS-011 and VS-005

### Problem

Merchants cannot inspect the outcomes produced by checkout.

### Learning Objective

Learn tenant-scoped read models, pagination, typed frontend contracts, permission-aware UI, and operationally useful payment detail design.

### Functional Requirements

- Implement FR-06 payment listing and detail APIs.
- Display public identifiers, amount, currency, status, simulator outcome, checkout reference, customer email, refundable amount, and timestamps according to permissions.
- Provide list, empty, loading, filtered, detail, not-found, forbidden, and unexpected-error states.

### Non-Functional Requirements

- Queries are scoped to organization before filtering or pagination.
- Read endpoints meet accepted latency targets under the initial workload.
- Internal identifiers and secrets are excluded.

### Constraints

- No analytics warehouse, export, advanced search, or cross-organization view.
- Add indexes only for demonstrated query paths.

### Acceptance Criteria

- Authorized roles can inspect only current-organization payments.
- Foreign and missing payment identifiers use the accepted non-enumerating behavior.
- Pagination is stable and documented.
- Dashboard state remains understandable for failed and unresolved intents.

### Required Tests

- Repository integration tests for tenant-scoped pagination and ordering.
- Controller authorization and cross-tenant tests.
- Frontend component and browser tests for list and detail states.

### Required Documentation

- Read API contracts, pagination rules, exposed fields, and permission behavior.

### Relevant Diagram Updates

- Update payment component documentation if a read model is introduced.

### Definition of Done

FR-06 is accessible from the dashboard, tenant isolation has negative tests, and query indexes correspond to documented access paths.

---

## VS-013 — Configure Webhook Endpoints and Deliver Basic Signed Events

**Priority:** P0

**Depends on:** VS-011 and VS-004

### Problem

Merchant systems need machine-readable payment notifications, but no endpoint configuration, immutable event, signature, or delivery history exists.

### Learning Objective

Learn secret generation and encryption, SSRF-aware URL validation, HMAC signing, immutable event payloads, timeouts, response bounds, and the limitations of synchronous delivery.

### Functional Requirements

- Implement FR-08 endpoint creation, one-time secret display, disablement, and event-type selection.
- Create immutable payment events after committed business changes.
- Perform one basic signed HTTPS delivery attempt and record its outcome.
- Display endpoint and delivery history to authorized merchants.

### Non-Functional Requirements

- Webhook signing includes stable event identifier and timestamp.
- Network calls have strict connection and request timeouts and bounded response capture.
- Delivery failure never rolls back committed payment state.
- Endpoint URLs receive explicit SSRF threat analysis and validation.

### Constraints

- Automatic exponential retries, outbox publication, worker claiming, dead-letter handling, and secret rotation are deferred to Phase 3.
- FR-09 remains partially implemented until durable retry behavior exists.
- Do not hold the payment database transaction open during the external HTTP call.

### Acceptance Criteria

- Authorized merchants can create and disable endpoints.
- The full secret is shown only once and stored encrypted.
- A receiver can verify payload signature and timestamp.
- Failed, slow, oversized, and non-`2xx` responses produce bounded delivery records without changing payment outcome.
- Documentation clearly labels delivery guarantees as incomplete.

### Required Tests

- Unit tests for signature generation and timestamp validation examples.
- Integration tests using a controlled webhook receiver for success, timeout, failure, and oversized response.
- Authorization and cross-tenant tests for endpoints and deliveries.

### Required Documentation

- Endpoint API, signature procedure, event schema, timeout and response limits, current failure window, and Phase 3 retry plan.

### Relevant Diagram Updates

- Add basic webhook-publication and failure sequence diagrams.
- Add a webhook component diagram when responsibilities stabilize.

### Definition of Done

Basic signed delivery is demonstrable and observable, secrets are protected, external failures are bounded, and deferred reliability is labeled rather than implied.

---

## VS-014 — Issue Concurrency-Safe Full and Partial Refunds

**Priority:** P0

**Depends on:** VS-011, VS-012, and VS-013 event contract

### Problem

Merchants need to reverse simulated payments without allowing duplicate or concurrent refunds to exceed the original amount.

### Learning Objective

Learn monetary reservation, concurrency-safe aggregate updates, refund state transitions, transaction boundaries, and tenant-scoped authorization.

### Functional Requirements

- Implement FR-07 for a positive partial amount or the full remaining refundable balance.
- Create `PENDING`, `SUCCEEDED`, `FAILED`, or `CANCELED` refunds according to the accepted simulator behavior.
- Reserve pending refund value and move or release the reservation atomically on resolution.
- Display refund history and remaining refundable balance in payment details.
- Create the applicable immutable refund event.

### Non-Functional Requirements

- Pending plus successful refunds never exceed payment amount.
- Currency always matches the payment.
- Concurrent full and partial requests preserve the invariant.
- Cross-tenant refund attempts disclose no foreign payment data.

### Constraints

- Choose and justify the locking or atomic-update strategy before coding.
- Idempotency keys and durable webhook retries remain Phase 3 work.
- No real provider refund or banking operation occurs.

### Acceptance Criteria

- Full refund uses exactly the remaining available amount.
- Multiple partial refunds update completed and reserved totals correctly.
- Zero, negative, excessive, wrong-currency, unsuccessful-payment, and foreign-payment requests are rejected with no state change.
- Concurrent tests cannot over-refund.

### Required Tests

- Unit tests for refund amount and state decisions.
- PostgreSQL integration tests for simultaneous partial and full refunds, success, failure, cancellation, and reservation release.
- API authorization tests and frontend payment-detail tests.

### Required Documentation

- Refund API, state table, monetary reservation model, transaction boundary, concurrency strategy, and current duplicate-request limitation.

### Relevant Diagram Updates

- Add refund state and sequence diagrams.
- Update ERD if aggregate handling changes.

### Definition of Done

FR-07 is demonstrable through the dashboard and API, concurrency tests protect monetary limits, events are recorded, and limitations are explicit.

---

## VS-015 — Complete and Verify the Checkout-to-Refund Flow

**Priority:** P0

**Depends on:** VS-001 through VS-014

### Problem

Individually working features do not prove that the complete merchant and customer journey remains coherent across boundaries and failure cases.

### Learning Objective

Learn end-to-end verification, failure-oriented acceptance testing, documentation reconciliation, observability baselines, and evidence-based readiness assessment.

### Functional Requirements

Verify the complete flow:

```text
Merchant registers and creates organization
    → creates product and price
    → creates checkout session
Customer opens hosted checkout
    → completes successful or failed simulation
Merchant inspects payment
    → observes basic webhook delivery
    → issues full or partial refund
    → inspects updated payment and refund
```

### Non-Functional Requirements

- Preserve tenant, payment-uniqueness, and refund-limit invariants throughout the flow.
- Emit structured correlation identifiers and the initial required metrics available at this phase.
- Record the environment and workload for any performance claim.
- Document all partially implemented reliability requirements.

### Constraints

- Do not add idempotency, outbox workers, Redis, brokers, AWS, or Kubernetes merely to make this ticket pass.
- Do not hide known failure windows behind optimistic documentation.
- This ticket integrates and verifies; it does not absorb unfinished acceptance criteria from earlier tickets without explicit re-planning.

### Acceptance Criteria

- A Playwright test completes the successful checkout-to-refund journey.
- Additional end-to-end tests cover decline, expired checkout, authorization denial, foreign-resource access, webhook failure, and excessive refund.
- The application starts from documented clean-environment commands.
- All unit, integration, architecture, frontend, and end-to-end suites pass in CI.
- Requirements and diagrams accurately label implemented, partial, and planned behavior.
- A Phase 2 completion review identifies evidence, known limitations, and the specific reliability problem addressed next.

### Required Tests

- Playwright successful and failed business journeys.
- Controlled webhook-receiver fixture.
- Cross-tenant negative journey.
- Concurrent confirmation and refund integration suites retained from earlier tickets.
- A small documented baseline performance run against the accepted workload subset.

### Required Documentation

- Root setup and verification instructions.
- API examples for the complete flow.
- Testing report and known limitations.
- Updated architecture, state, sequence, ERD, security, and operational documents affected by implementation.
- A Phase 2 review and proposed Phase 3 reliability backlog.

### Relevant Diagram Updates

- Reconcile context, container, ERD, payment, refund, checkout, and webhook diagrams with implemented behavior.
- Add missing failure paths discovered by end-to-end testing.

### Definition of Done

The complete slice is reproducible, tested across layers, demonstrable through UI and API, accurately documented, and stable enough to justify beginning the reliability phase.

## Explicitly Deferred from This Backlog

- organization invitations unless required to validate the chosen membership model;
- API-key creation and management UI unless an API-client demonstration is explicitly pulled into the slice;
- idempotency records and replayed responses;
- transactional outbox publication;
- separate background-worker deployment;
- automatic webhook backoff and dead-letter handling;
- Redis and distributed rate limiting;
- message broker;
- full audit-log UI;
- subscriptions, invoices, usage billing, tax, discounts, and payouts;
- AWS, Terraform, Kubernetes, and production deployment guarantees.

Deferred items require their own tickets, prerequisites, failure analysis, acceptance criteria, tests, and documentation. They must not enter this slice through incidental refactoring.

## Phase 0 Exit Criteria

Phase 0 is complete when:

- product definition, functional requirements, non-functional requirements, exclusions, actors, and terminology are accepted;
- system context, container architecture, and initial ERD are accepted and stored as code;
- ADR 0001 is accepted;
- this backlog is accepted and prioritized;
- documentation distinguishes implemented, partially implemented, planned, rejected, and experimental capabilities;
- the first implementation ticket has enough reasoning and acceptance criteria to begin without unresolved essential domain rules.

# Initial Entity-Relationship Design

## Status

Accepted — Phase 1 evolving logical data model. This is design input; only entities backed by committed migrations are implemented.

- **Architecture version:** Phase 1, version 0.2
- **Date:** 2026-08-08
- **Diagram source:** [`../diagrams/initial-erd.mmd`](../diagrams/initial-erd.mmd)

## Title and Scope

**Title:** Striply Initial Entity-Relationship Diagram  
**Scope:** Identity and refresh-token lifecycle, tenant ownership, catalog, checkout, simulated payments, refunds, webhooks, audit, idempotency, and transactional outbox concepts required by the planned first vertical slice and reliability phase.

The model is intentionally logical. Exact PostgreSQL types, column lengths, checks, generated values, and migration ordering must be finalized immediately before each module is implemented.

## Diagram

The authoritative Mermaid source is stored in [`initial-erd.mmd`](../diagrams/initial-erd.mmd). GitHub and Mermaid-compatible editors can render it directly.

The diagram uses:

- `PK` for primary keys;
- `FK` for foreign keys;
- `UK` for unique keys represented at diagram level;
- `nullable` in attribute names for optional relationships;
- relationship cardinality for expected domain ownership.

Some PostgreSQL constraints—partial unique indexes, composite tenant foreign keys, and monetary checks—cannot be expressed completely in Mermaid ER syntax. They are specified below.

## Identifier Strategy

Each externally addressable resource uses:

1. an internal UUID primary key for database relationships; and
2. a unique, immutable, prefixed public identifier for API exposure.

Initial prefixes are:

| Resource | Prefix |
| --- | --- |
| User | `usr_` |
| Organization | `org_` |
| API key metadata | `key_` |
| Customer | `cus_` |
| Product | `prod_` |
| Price | `price_` |
| Checkout session | `cs_` |
| Payment intent | `pi_` |
| Payment | `pay_` |
| Refund | `re_` |
| Webhook endpoint | `wh_` |
| Webhook event | `evt_` |

Join records, delivery attempts, audit entries, idempotency records, and outbox records need not expose their internal UUIDs. If an API later addresses one directly, a prefixed public identifier must be added deliberately.

## Tenant Ownership

`organization_id` is stored directly on every tenant-owned record, including records whose ownership could be inferred through another foreign key. This supports mandatory query scoping, indexing, auditing, and database-level same-tenant constraints.

For a tenant-owned parent table, migrations should add `UNIQUE (id, organization_id)`. Child tables should use composite foreign keys such as:

```text
(product_id, organization_id)
    REFERENCES product (id, organization_id)
```

This prevents a valid product identifier from one organization being combined with another organization's `organization_id`. Application authorization remains required; database constraints provide defense in depth.

## Entity Notes

### Identity and Organization

#### `app_user`

- Human authentication identity.
- Email comparison is case-insensitive and unique after normalization.
- Password hashes are never returned or logged.
- Organization access is granted only through active membership.

#### `refresh_token_family`

- Represents one login session on one browser or device; it is not exposed through the API.
- Belongs to exactly one user and retains its original `absolute_expires_at` through every rotation.
- `revoked_at` invalidates every token in the family after logout, replay detection, or another security action.
- `revocation_reason` stores a bounded internal reason code such as `LOGOUT`, `TOKEN_REUSE`, or `SECURITY_ACTION`; it must not contain credentials or raw request data.
- Revoking one compromised family does not revoke the user's other login sessions. A separate future "log out everywhere" operation may revoke all families for the user.

#### `refresh_token`

- Represents one refresh-token generation within a family and is never exposed as a database identifier.
- Stores only a deterministic cryptographic hash of a high-entropy random token. The raw token exists only in the `HttpOnly` cookie returned to the client.
- `expires_at` is no later than seven days after creation and must never exceed the family's 30-day absolute expiry.
- `consumed_at` is set exactly once when rotation succeeds. A consumed token can never issue another token.
- Reuse of a consumed token revokes the complete family and requires a new login.
- Rotation must atomically consume the presented token and create exactly one replacement in the same family.

#### `organization`

- Root tenant record.
- Organization status controls whether organization-owned operations may proceed.
- Deactivation does not delete financial or audit history.

#### `organization_member`

- Joins one user to one organization with one role and membership status.
- `UNIQUE (organization_id, user_id)` prevents duplicate memberships.
- Role values initially include `OWNER`, `ADMIN`, `DEVELOPER`, and `ANALYST`.

#### `api_key`

- Machine credential owned by one organization.
- Stores a one-way `secret_hash`, safe display hint, scopes, lifecycle status, and creator.
- The plaintext secret is returned once and is never persisted.

### Customer and Catalog

#### `customer`

- Optional merchant-owned customer profile.
- The same normalized email may exist in different organizations.
- Checkout retains its own email snapshot even when linked to a customer.

#### `product`

- Mutable merchant catalog description.
- Active status controls new price and checkout use without changing history.

#### `price`

- Immutable `amount_minor` and `currency` associated with one product.
- Initial price type is one-time only.
- Deactivation prevents new checkout sessions but preserves references.

### Checkout, Payment, and Refund

#### `checkout_session`

- References one price and optionally one customer.
- Stores product name, unit amount, total amount, currency, quantity, and customer email snapshots.
- Stores approved success and cancellation URLs and a 30-minute expiration.
- Uses optimistic locking because payment confirmation and expiration may race.

#### `payment_intent`

- Represents one processing attempt for a checkout session.
- A session may own multiple sequential intents after terminal failures.
- Provider-timeout outcomes remain unresolved and prevent unsafe immediate retry.
- Uses optimistic locking to enforce state transitions under concurrency.

#### `payment`

- Exists only for a successful payment intent.
- `payment_intent_id` is unique so one intent produces at most one payment.
- A partial unique index on `checkout_session_id` enforces at most one successful payment per checkout session.
- `refunded_amount_minor` tracks successfully completed refunds, while `refund_reserved_amount_minor` reserves value for pending refunds.
- Both aggregates are concurrency-controlled, and their sum must never exceed the original payment amount.

#### `refund`

- References one successful payment and uses its currency.
- States are `PENDING`, `SUCCEEDED`, `FAILED`, and `CANCELED`.
- Payment locking or an equivalent atomic database operation must prevent pending and successful refunds together from exceeding the original payment amount.
- Creating a refund moves value into the reserved aggregate. Success moves that value from reserved to refunded; failure or cancellation releases the reservation.

### Webhooks

#### `webhook_endpoint`

- Organization-owned destination and subscription configuration.
- `secret_ciphertext` is encrypted, not hashed, because Striply must recover the secret to sign requests.
- Initial event-type subscriptions are stored as validated JSON until subscription-query requirements justify a normalized table.

#### `webhook_event`

- Immutable merchant-facing business event with stable payload and schema version.
- Payload changes require a new event rather than mutation of the stored event.

#### `webhook_delivery`

- One event's delivery lifecycle for one endpoint.
- `UNIQUE (webhook_event_id, webhook_endpoint_id)` prevents duplicate lifecycle rows.
- Version and scheduling fields support safe worker claiming and manual retry.

#### `webhook_delivery_attempt`

- One bounded HTTP attempt for a delivery.
- `UNIQUE (webhook_delivery_id, attempt_number)` preserves attempt order.
- Stores only bounded response excerpts and safe diagnostic information.

### Reliability and Audit

#### `audit_entry`

- Append-only organization-scoped record of sensitive actions and outcomes.
- Actor and target references are polymorphic public identifiers because actors may be users, API keys, or operators.
- Metadata must exclude credentials and unnecessary personal data.

#### `idempotency_record`

- **Status:** Planned Phase 3.
- Uniqueness scope is `(organization_id, operation, idempotency_key)`.
- Stores a request fingerprint, processing state, replayable response, and expiration.
- Optimistic locking alone is insufficient for first-creator selection; the unique constraint is authoritative.

#### `outbox_event`

- **Status:** Planned Phase 3.
- Written in the same PostgreSQL transaction as the associated business change.
- Stores immutable publication payload, availability, attempts, claim state, and publication time.
- Supports at-least-once publication; consumers remain idempotent.

## Required Database Constraints

The implementation must include, at minimum:

| Table | Constraint | Purpose |
| --- | --- | --- |
| `app_user` | Unique normalized email | Prevent duplicate login identities |
| `refresh_token_family` | `absolute_expires_at > created_at` | Prevent invalid login-session lifetime |
| `refresh_token` | Unique `token_hash`; `expires_at > created_at` | Support safe token lookup and prevent invalid token lifetime |
| `refresh_token` | Partial unique active token per `family_id` | Prevent multiple unconsumed descendants in one token family |
| `organization` | Unique `public_id` | Stable external organization identity |
| `organization_member` | Unique `(organization_id, user_id)` | One membership per user and organization |
| `api_key` | Unique `public_id`; unique secret fingerprint where used | Identify and safely reject duplicate credentials |
| Tenant-owned public resources | Unique `(organization_id, public_id)` and global unique `public_id` where lookup is global | Enforce stable public addressing |
| `price` | Checks for `amount_minor > 0`, supported currency, and one-time type | Enforce monetary and release-scope rules |
| `checkout_session` | Quantity `BETWEEN 1 AND 100`; total amount positive; expiration after creation | Enforce checkout bounds |
| `payment` | Unique `payment_intent_id`; partial unique successful payment per checkout | Prevent duplicate successful payments |
| `payment` refund totals | Both totals non-negative and `(refunded_amount_minor + refund_reserved_amount_minor) <= amount_minor` | Protect completed and pending refund limits |
| `refund` | `amount_minor > 0` and currency equal to payment currency through transactional validation | Protect refund invariants |
| `webhook_delivery` | Unique `(webhook_event_id, webhook_endpoint_id)` | One delivery lifecycle per event and endpoint |
| `webhook_delivery_attempt` | Unique `(webhook_delivery_id, attempt_number)` | Stable attempt ordering |
| `idempotency_record` | Unique `(organization_id, operation, idempotency_key)` | Select one owner for concurrent duplicate requests |
| Tenant child relationships | Composite foreign key including `organization_id` | Prevent cross-tenant relationships |

The cumulative refund limit cannot be enforced by a simple row check because it spans multiple refund rows. It requires locking or an atomic conditional update on the payment within the refund transaction.

## Initial Index Strategy

Indexes must be justified by an API query, constraint, or worker access path. Initial candidates are:

- unique indexes for all public identifiers;
- `refresh_token_family (user_id, revoked_at)` for active-session lookup and user-wide revocation;
- unique `refresh_token (token_hash)` for presented-token lookup;
- `refresh_token (family_id, created_at DESC)` for rotation history and security investigation;
- `organization_member (user_id, status)` for organization selection;
- `product (organization_id, status, created_at DESC)`;
- `price (organization_id, product_id, status)`;
- `checkout_session (organization_id, created_at DESC)`;
- `checkout_session (status, expires_at)` for expiration processing;
- `payment_intent (organization_id, checkout_session_id, created_at DESC)`;
- `payment (organization_id, created_at DESC)` for dashboard listing;
- `refund (organization_id, payment_id, created_at)`;
- `webhook_delivery (status, next_attempt_at)` for worker claiming;
- `webhook_event (organization_id, created_at DESC)`;
- `audit_entry (organization_id, created_at DESC)`;
- `idempotency_record (expires_at)` for expiration cleanup;
- `outbox_event (status, available_at, created_at)` for publication claiming.

Candidate indexes must be verified with realistic data and query plans. Redundant indexes should not be added solely because a column is a foreign key.

## Concurrency-Control Fields

The initial design includes `version` on:

- checkout sessions;
- payment intents;
- payments;
- refunds;
- webhook deliveries;
- idempotency records;
- outbox events.

Version columns support optimistic conflict detection but do not replace uniqueness constraints or atomic monetary updates. The final concurrency mechanism is selected per invariant and documented with tests.

## Transaction Boundaries Implied by the Model

- Login and successful registration create one refresh-token family and its first token in the same transaction.
- Refresh rotation atomically verifies the family lifetime and revocation state, consumes the presented token, and inserts one replacement token. Concurrent use of the same token permits at most one successful rotation.
- Presentation of a consumed refresh token revokes its family in a transaction. The public response does not reveal whether replay detection occurred.
- Logout idempotently revokes the identifiable family; failure to find an active family does not reveal token state.
- Checkout-session creation persists the session and any associated initial audit or event record atomically when those capabilities exist.
- Successful confirmation transitions the intent, creates the payment, completes the checkout session, and records the associated event in one transaction.
- Refund creation validates and reserves the requested balance, creates the refund, and records its event in one transaction. Resolution moves or releases the reservation atomically.
- Webhook attempt completion updates the attempt and delivery scheduling state atomically.
- Idempotency response persistence occurs in the same transactional protocol as the protected mutation.
- Outbox insertion occurs in the same transaction as its business-state change.

## Security and Privacy Notes

- Database roles must prevent browser or merchant-application access to tables.
- Passwords and API-key secrets are one-way hashed; recoverable webhook signing secrets are encrypted.
- Raw refresh tokens are never persisted or logged. Only cryptographic token hashes are stored, and database identifiers for token rows are never exposed.
- Customer email is personal data and must be scoped, minimized in logs, and retained according to the accepted policy.
- Public identifiers reduce enumeration risk but do not provide authorization.
- Audit and webhook metadata must be size-bounded and scrubbed of secrets.

## Assumptions

- PostgreSQL is the authoritative store for all entities in this diagram.
- UUIDs are generated using an implementation and version chosen before the first migration.
- Public identifiers are immutable and generated independently from client input.
- Every tenant-owned mutation knows its authenticated organization before persistence.
- Webhook event payloads and outbox payloads use explicit schema versions.
- Subscription and invoice entities remain outside this initial ERD.

## Known Limitations

- Organization invitations, password-reset tokens, and email verification need separate identity modeling before implementation.
- Event-type subscriptions use JSON in this initial model and may later require normalization.
- Mermaid ER syntax does not show partial indexes, checks, composite foreign keys, or PostgreSQL exclusion rules fully.
- Exact status storage—PostgreSQL enum, check-constrained text, or lookup table—remains undecided.
- Data archival, partitioning, deletion, and anonymization procedures require a separate retention design.
- This ERD has not been validated through migrations, integration tests, or concurrent transaction tests.

## Conditions for Reconsideration

- Normalize webhook subscriptions when queries, validation, or schema evolution become awkward with JSON.
- Add a dedicated customer-to-checkout relationship requirement if reusable customer management enters the vertical slice.
- Partition high-volume event, audit, or delivery-attempt tables only after measurements demonstrate a need.
- Revisit direct `organization_id` duplication only if database-enforced tenant consistency is preserved by an equally strong alternative.

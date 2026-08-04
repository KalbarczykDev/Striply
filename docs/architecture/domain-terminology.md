# Domain Terminology

## Status

Accepted — Phase 0 ubiquitous language and input to the initial ERD and API design.

## Conventions

- **Merchant** describes an organization using Striply to offer simulated checkout flows. It is a business concept, not a separate persisted entity in the initial model.
- **Organization-owned** means a resource belongs to exactly one organization and must be accessed only through an authenticated context authorized for that organization.
- **Public identifier** means a non-sequential, externally visible identifier with a resource-specific prefix. Internal database identifiers are never exposed by external APIs.
- **Money** is represented by a positive or zero integer amount in a currency's minor unit together with an explicit supported ISO 4217 currency code. Floating-point values are not used for monetary storage or calculation.
- Timestamps are stored in UTC. API representations must include an unambiguous offset or use the `Z` UTC designator.
- Status changes for behavior-rich financial concepts occur through explicit domain operations, not arbitrary status setters.

## Identity and Organization

### User (`usr_`)

A human identity that can authenticate to the merchant-facing application. A user owns login credentials and profile information but does not gain access to organization data merely by existing.

### Organization (`org_`)

The tenant boundary for merchant-owned data. Catalog, customer, checkout, payment, refund, webhook, API-key, audit, idempotency, and event records are scoped directly or transitively to one organization.

### Organization Member

The association between a user and an organization. It grants one organization role—`OWNER`, `ADMIN`, `DEVELOPER`, or `ANALYST`—and records membership status. A user may belong to multiple organizations, and an organization may contain multiple members.

An organization member is not a duplicate user account. Removing or disabling one membership must not remove the user or their memberships in other organizations.

### Organization Context

The organization within which an authenticated user request is evaluated. Striply validates the selected organization against active membership on every request. A client-supplied organization identifier never establishes authority by itself.

### API Key

An organization-scoped machine credential used by a merchant application. The presented secret is shown only when created, while Striply stores a one-way hash and safe identifying metadata. Disabling or rotating an API key does not change historical records created with it.

## Customer and Catalog

### Customer (`cus_`)

A merchant-owned representation of a person or business participating in checkout. Customers are organization-scoped; the same email address used with two organizations represents two independent customer records. A customer is not a Striply user and cannot authenticate to the merchant dashboard.

### Product (`prod_`)

An organization-owned description of what a merchant offers. A product has mutable descriptive attributes and an active or inactive lifecycle. Deactivating a product prevents new prices or checkout sessions from using it but does not alter historical checkout or payment data.

### Price (`price_`)

An organization-owned monetary offer associated with exactly one product. The first release supports one-time prices in `PLN`, `EUR`, and `USD`. Amount and currency are immutable after creation; a merchant creates another price when either value changes. An inactive price cannot be used for a new checkout session.

### Money

A value object containing an integer minor-unit amount and currency. Two money values may be added or compared only when their currencies match. For the initial supported currencies, `1099` represents `10.99` units of that currency.

## Checkout and Payment

### Checkout Session (`cs_`)

A time-limited, customer-facing workflow created from one active price and a quantity. It stores an immutable snapshot of the product name, unit amount, currency, and quantity so later catalog changes cannot change what the customer sees or pays.

An initial checkout session is `OPEN`, expires 30 minutes after creation, and can reach a terminal outcome such as `COMPLETED`, `EXPIRED`, or `CANCELED`. A checkout session may produce at most one successful payment.

### Simulator Scenario

A named, non-sensitive test input that instructs the internal payment simulator to produce a controlled outcome. Initial scenarios represent success, decline, insufficient funds, delayed processing, and provider timeout. A simulator scenario is not a payment method and contains no real card or bank data.

### Payment Intent (`pi_`)

The stateful attempt to collect a specific amount and currency for a checkout session. It owns the payment-processing state machine and records the requested amount, simulator interaction, outcome, and transition history needed to explain processing.

Expected states include `REQUIRES_PAYMENT_METHOD`, `REQUIRES_CONFIRMATION`, `PROCESSING`, `SUCCEEDED`, `FAILED`, and `CANCELED`. Exact transitions, retry semantics, and terminal-state rules require a separate state-machine design before implementation.

### Payment (`pay_`)

The immutable record of a successful simulated charge produced when a payment intent succeeds. A payment establishes the original amount against which refunds are calculated. Failed payment intents do not create successful payment records.

A payment is not the same as a payment intent: the intent represents processing and state transitions; the payment represents the successful financial result.

### Refund (`re_`)

An organization-owned request to reverse all or part of a successful simulated payment. A refund uses the payment's currency and a positive minor-unit amount. The total of accepted refunds must never exceed the original payment amount, including under concurrency.

A full refund means refunding the entire remaining refundable balance. A partial refund reduces that balance but leaves a positive amount available for later refunds.

## Webhooks and Events

### Webhook Endpoint (`wh_`)

An organization-owned HTTPS destination and set of subscribed event types. An endpoint has a signing secret, can be enabled or disabled, and retains its historical delivery records when disabled.

### Webhook Event (`evt_`)

An immutable organization-owned description of a business fact, such as a payment succeeding or refund being accepted. It contains a stable event identifier, type, creation time, schema version, and immutable payload. One event may be delivered to multiple endpoints.

### Webhook Delivery

The endpoint-specific delivery lifecycle for one webhook event. It records attempts, scheduling, success, exhaustion, and bounded response metadata. A delivery is not the event itself: retrying a delivery must not create or mutate the original business event.

### Webhook Delivery Attempt

One HTTP request made for a webhook delivery. It records attempt number, request time, duration, result category, HTTP status when available, and bounded diagnostic metadata. Secrets and unbounded response bodies are not retained.

## Reliability and Audit

### Idempotency Key

A client-generated token that identifies one intended mutation within an organization and endpoint scope. It has meaning only when combined with the authenticated organization, operation, and normalized request payload.

### Idempotency Record

The persisted result of processing an idempotent mutation. It associates an organization, operation, idempotency key, request fingerprint, processing status, stored response, and expiration. Reusing the same key with a different request is a conflict, not a replay.

### Outbox Event

A durable record written in the same PostgreSQL transaction as a business-state change. A background publisher later processes it at least once. Outbox events support reliable asynchronous publication but do not guarantee exactly-once delivery.

An outbox event is an infrastructure publication record; a webhook event is the merchant-facing immutable business event. Their relationship will be defined when the transactional outbox is designed.

### Audit Entry

An append-only record of a security-relevant or business-sensitive action. It records organization, actor type and identifier, action, target, outcome, timestamp, and safe request context. It must not contain passwords, API-key secrets, webhook signing secrets, or unnecessary personal data.

## Terms Not Used Interchangeably

| Terms | Required distinction |
| --- | --- |
| User / Customer | A user authenticates to operate a merchant organization; a customer participates in checkout. |
| User / Organization Member | A user is an identity; membership grants access to one organization. |
| Product / Price | A product describes an offering; a price defines an immutable amount and currency for that offering. |
| Checkout Session / Payment Intent | A checkout session is the customer workflow; a payment intent controls one payment-processing attempt. |
| Payment Intent / Payment | An intent tracks processing; a payment records a successful result. |
| Payment / Refund | A payment establishes the original successful amount; refunds reverse portions of its refundable balance. |
| Webhook Event / Webhook Delivery | An event is an immutable fact; a delivery tracks sending that fact to one endpoint. |
| Webhook Delivery / Attempt | A delivery owns the retry lifecycle; an attempt is one HTTP request. |
| Idempotency Record / Outbox Event | Idempotency protects client mutations; an outbox event protects asynchronous publication after a committed change. |

## Accepted Decisions

1. A checkout session may create multiple sequential payment intents after terminal failures but may never produce more than one successful payment.
2. A customer record is optional in the first vertical slice; checkout may retain the customer email snapshot directly.
3. Decline and insufficient-funds outcomes make the current payment intent terminally `FAILED`, and retrying creates a new intent. A provider-timeout outcome remains unresolved and must not permit immediate retry until its outcome is known.
4. Refunds use `PENDING`, `SUCCEEDED`, `FAILED`, and `CANCELED` states.

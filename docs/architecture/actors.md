# Main Actors

## Status

Accepted

## Date

15-08-2026

## Merchant Organization Member

- **Type:** Human
- **Status:** Current scope
- **Goal:** Configure and operate Striply for an organization, inspect simulated payment activity, and manage developer integrations.
- **Interactions with Striply:** Registers and signs in; creates or joins organizations; manages products, prices, checkout sessions, refunds, webhook endpoints, API keys, members, and organization settings; inspects payments, delivery attempts, and audit logs. Available actions depend on the member's `OWNER`, `ADMIN`, `DEVELOPER`, or `ANALYST` role.
- **Authentication or trust boundary:** Authenticates through the merchant-facing identity flow. Every request uses an explicit current organization context derived from authenticated membership. A member may belong to multiple organizations but acts within one organization context at a time.
- **Data accessed or exchanged:** Account profile, organization membership, catalog data, customer contact data, checkout sessions, payments, refunds, webhook configuration and history, API-key metadata, and audit records permitted by role.
- **Must not be allowed to:** Access another organization's resources; grant permissions beyond their own authority; retrieve stored password hashes, API-key hashes, or full webhook secrets after creation; change immutable financial history; or use dashboard authentication as unrestricted platform administration.

### Organization Roles

- `OWNER` controls organization ownership and the most sensitive organization settings.
- `ADMIN` manages members and operational settings within permissions delegated by the owner.
- `DEVELOPER` manages integrations and technical resources without ownership authority.
- `ANALYST` has read-oriented access to business and payment information.

The exact permission matrix is intentionally deferred to the identity and authorization design. Role names alone must not replace endpoint-level authorization rules.

## Merchant Application / API Client

- **Type:** External system controlled by a merchant
- **Status:** Current scope
- **Goal:** Integrate Striply's simulated payment workflow into the merchant's software.
- **Interactions with Striply:** Uses the API to create and retrieve permitted resources, create checkout sessions, inspect payment results, and initiate supported mutations using organization-scoped credentials.
- **Authentication or trust boundary:** Authenticates with an API key associated with exactly one organization and a defined permission scope. It operates outside Striply's trust boundary, and request parameters are untrusted even when authentication succeeds.
- **Data accessed or exchanged:** Public resource identifiers, catalog data, checkout-session data, customer email addresses required by the flow, payment outcomes, refund data, and idempotency keys when that capability is introduced.
- **Must not be allowed to:** Act outside its organization or credential scope; use a public checkout token as an API credential; retrieve API-key hashes or unrelated secrets; select another organization using an untrusted request parameter; or bypass validation, idempotency, or monetary invariants.

## Customer

- **Type:** Human
- **Status:** Current scope
- **Goal:** Review a merchant's checkout and complete a simulated payment.
- **Interactions with Striply:** Opens a hosted-checkout URL, reviews the snapshotted product and amount, supplies an email address, selects a predefined simulator scenario, and sees the resulting checkout or payment status.
- **Authentication or trust boundary:** Does not use merchant authentication. Possession of an unexpired checkout-session URL grants narrowly scoped access to that session only. The customer and their browser are outside Striply's trust boundary.
- **Data accessed or exchanged:** Public merchant display name, snapshotted product details, amount, currency, checkout status, customer email, selected simulator scenario, and the minimum payment-result information required by the hosted checkout.
- **Must not be allowed to:** Access the merchant dashboard, organization data, other checkout sessions, internal identifiers, API credentials, webhook secrets, audit logs, or real card and banking input fields; alter the checkout amount or currency; or pay an expired, canceled, or completed session.

## Striply Operator

- **Type:** Human
- **Status:** Planned operational role; no operator interface is implemented
- **Goal:** Operate, diagnose, secure, and recover the Striply platform without becoming a merchant user.
- **Interactions with Striply:** Reviews health signals, logs, metrics, traces, alerts, deployment state, backup results, and security events; follows documented recovery and incident procedures. Exceptional data access must use an audited support procedure.
- **Authentication or trust boundary:** Uses a separate administrative identity and least-privilege operational access. Operator authority is not derived from organization membership and must not be exposed through merchant-facing endpoints.
- **Data accessed or exchanged:** Operational metadata, redacted logs, aggregate metrics, trace and correlation identifiers, health information, deployment configuration, and narrowly scoped merchant data only when an approved support or incident process requires it.
- **Must not be allowed to:** Retrieve merchant passwords, plaintext API keys, or historical full webhook secrets; silently impersonate a merchant; modify payment or audit history outside an explicit recovery procedure; or access customer personal data merely for convenience.

## External Webhook Receiver

- **Type:** External system controlled by a merchant
- **Status:** Current scope
- **Goal:** Receive machine-readable notifications about events belonging to its merchant organization.
- **Interactions with Striply:** Accepts HTTPS webhook requests, validates their HMAC signatures and timestamps, returns an HTTP outcome, and tolerates duplicate deliveries by deduplicating on the stable event identifier.
- **Authentication or trust boundary:** Lies outside Striply's trust boundary. Striply authenticates outgoing payloads with an endpoint-specific signing secret but cannot trust receiver availability, behavior, response content, or processing claims.
- **Data accessed or exchanged:** Event identifier, event type, creation time, public organization and resource context, documented event payload, request signature and timestamp, HTTP status, bounded response metadata, and delivery timing.
- **Must not be allowed to:** Query Striply merely because it receives webhooks; access events from another organization; influence committed business state through its response; receive secrets or internal database identifiers in payloads; or cause unbounded waits, retries, or response storage.

## External Email Service

- **Type:** External system
- **Status:** Planned; not part of the first vertical slice
- **Goal:** Deliver transactional account, invitation, and operational email when email workflows are introduced.
- **Interactions with Striply:** Accepts narrowly scoped delivery requests and returns provider message identifiers and delivery outcomes. Exact email workflows and provider selection are deferred.
- **Authentication or trust boundary:** Lies outside Striply's trust boundary and uses a dedicated service credential. Requests must be authenticated, encrypted in transit, timeout-bounded, and safe to retry according to the future provider contract.
- **Data accessed or exchanged:** Recipient email address, template identifier, minimal template variables, provider message identifier, and delivery status.
- **Must not be allowed to:** Receive passwords, API keys, webhook signing secrets, complete payment records, or unrelated customer data; determine authorization decisions; or block committed payment operations when email delivery fails.

## Internal Components That Are Not Actors

The payment simulator, Spring Boot modules, background workers, PostgreSQL, Redis, message brokers, monitoring components, AWS services, and Kubernetes resources are internal containers, components, or infrastructure. They may appear in later architecture diagrams but are not actors in the system context.

## Accepted Decisions

1. Organization switching uses a server-validated organization selector on each request and does not require issuing a new access token.
2. API keys combine fixed scopes with endpoint-level permission checks.
3. Striply operators may access organization-specific data only through an audited support or incident procedure; routine access is forbidden.

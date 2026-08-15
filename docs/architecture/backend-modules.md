# Backend Modules

## Status

Partially implemented — VS-001

## Package Convention

`dev.kalbarczyk.striply.<module>`

## Internal Layers

- `web` — HTTP controllers, with request and response DTOs in `web.dto`
- `service` — use-case orchestration, transaction boundaries, application results and failures
- `repository` — persistence access owned by the feature
- `model` — feature-owned state, JPA entities, enums and business behavior
- `security` — feature-owned security mechanisms such as token hashing, signing and validation
- `config` — optional feature-specific Spring configuration and validated properties
- `api` — optional public contracts used by another feature

Packages are created only when a feature has classes that require them. Empty placeholder packages are not maintained.

## Dependency Rules

### Between modules

- Features may use another feature only through its `api` package.
- Other features must not access another feature's `web`, `service`, `repository`, `model`, `security`, or `config` packages.
- Module dependency cycles are forbidden.

### Inside a module

Striply uses a pragmatic feature-first layered structure:

```text
web → service → repository
         │          │
         ├→ security│
         └→ model ←─┘
```

Controllers call services rather than repositories. JPA annotations are permitted in `model`; Striply does not maintain a separate persistence-entity hierarchy. Application-wide Spring wiring remains in the root `configuration` package. Spring Security's maintained resource-server support owns bearer-token filtering; Striply does not implement a custom JWT authentication filter without a concrete unmet requirement.

## Modules

| Package          | Owned concepts and behavior                                                                                               | Permitted public responsibility                                                                                            |
|------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `identity`       | User identity, password credentials, access tokens, refresh-token lifecycle, authentication outcomes                      | Authenticate human users and expose the authenticated user identity without exposing credential storage or token internals |
| `organization`   | Organizations, memberships, roles, membership status, organization context, permission decisions                          | Validate organization membership and answer explicit organization-authorization questions                                  |
| `customer`       | Organization-scoped customer profiles and customer lifecycle                                                              | Create and retrieve customer information permitted for the current organization                                            |
| `catalog`        | Products, one-time prices, catalog activation, supported currencies, immutable price amounts                              | Manage catalog resources and provide validated immutable price information required by checkout                            |
| `checkout`       | Checkout sessions, catalog snapshots, expiration, public checkout access, completion and cancellation rules               | Create checkout sessions, expose safe hosted-checkout views, and coordinate permitted checkout-state changes               |
| `payment`        | Payment intents, payment-intent state machine, successful payments, simulator-provider port and outcomes                  | Confirm simulated payments and expose payment results without leaking simulator or persistence internals                   |
| `refund`         | Refund requests, refund lifecycle, pending reservations, completed refund totals, refund limits                           | Create and inspect authorized refunds while protecting the payment's refundable balance                                    |
| `webhook`        | Webhook endpoints, signing secrets, immutable events, deliveries, attempts, signatures and retry lifecycle                | Configure endpoints, accept versioned business-event requests, and expose safe delivery history                            |
| `developer`      | API-key creation, hashing, scopes, rotation, revocation and developer integration tooling                                 | Manage organization-scoped machine credentials and expose safe API-key metadata                                            |
| `audit`          | Append-only audit entries, actor and target metadata, security-sensitive action history                                   | Record safe audit facts and provide organization-scoped audit queries                                                      |
| `shared`         | Stable cross-domain primitives such as money, public identifiers, clocks and common error contracts when proven necessary | Supply dependency-light primitives only; it exposes no business workflow or persistence access                             |
| `configuration`  | Application-wide framework wiring, observability setup and cross-cutting configuration                                    | Assemble and operate the application without owning or exposing feature behavior                                           |

The table defines ownership, not a commitment to create every listed class during VS-001. A public responsibility
becomes an implemented contract only when a ticket defines its inputs, outputs, failure behavior and authorization
boundary.

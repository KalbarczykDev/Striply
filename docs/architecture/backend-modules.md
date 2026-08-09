# Backend Modules

## Status

Partially implemented — VS-001

## Package Convention

`dev.kalbarczyk.striply.<module>`

## Internal Layers

- `api` — public inter-module contracts
- `api.web` — HTTP controllers and request/response DTOs; other modules must not depend on this package
- `application` — use-case orchestration and transaction boundaries
- `domain` — business rules without Spring or persistence dependencies
- `infrastructure` — persistence, security mechanisms, provider integrations, and other technical adapters owned by the module

## Dependency Rules

### Between modules

- Modules may use another module only through its `api` package.
- Other modules must not access another module's `application`, `domain`, `infrastructure`, or `api.web` packages.
- Module dependency cycles are forbidden.

### Inside a module

Striply uses a pragmatic layered structure inside each module:

```text
api.web → application → infrastructure
              │               │
              └──→ domain ←───┘
```

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
| `infrastructure` | Application configuration, framework wiring, observability setup and cross-cutting technical adapters                     | Assemble and operate the application without owning or exposing domain behavior                                            |

The table defines ownership, not a commitment to create every listed class during VS-001. A public responsibility
becomes an implemented contract only when a ticket defines its inputs, outputs, failure behavior and authorization
boundary.

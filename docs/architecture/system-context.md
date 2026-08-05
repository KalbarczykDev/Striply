# System Context

## Status

Accepted — Phase 0 logical system context based on the accepted actor model and boundary description.

- **Architecture version:** Phase 0, version 0.1
- **Date:** 2026-08-05
- **Diagram source:** [`../diagrams/system-context.puml`](../diagrams/system-context.puml)

## Title and Scope

**Title:** Striply System Context  
**Scope:** Striply as one logical software system, its human users, external merchant-controlled systems, planned third-party email service, and future AWS hosting boundary.

This diagram intentionally does not show frontend applications, backend modules, databases, caches, workers, or monitoring components. Those belong to the container diagram or later deployment diagrams.

## Context Diagram

The source uses C4-PlantUML. Render [`system-context.puml`](../diagrams/system-context.puml) with PlantUML and its C4 standard library to generate an image.

```text
Merchant member ──HTTPS──> Striply <──HTTPS── Customer
Merchant application ──HTTPS/JSON API──> Striply
Striply ──HTTPS/JSON + HMAC──> External webhook receiver
Striply ──HTTPS API──> External email service [planned]
Striply operator ──restricted operational access──> Striply [planned]
AWS ──hosts──> Striply [planned deployment relationship]
```

The text view is a readable fallback. The C4-PlantUML source is the authoritative diagram.

## System Boundary

Striply includes all software owned as part of the payment-platform project:

- merchant dashboard;
- hosted checkout;
- public and merchant-facing APIs;
- identity and organization authorization;
- catalog, checkout, payment, refund, webhook, developer, and audit behavior;
- internal payment simulator;
- background processing introduced in later phases;
- Striply-owned persistence and operational components.

At the system-context level, these elements are represented as one system. The boundary expresses ownership and trust, not the eventual deployment topology.

## Relationships

| Source | Destination | Direction | Purpose | Protocol | Authentication or trust mechanism | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Merchant organization member | Striply | Inbound, synchronous | Operate an organization through the dashboard | HTTPS | User authentication plus server-validated organization context and role permissions | Current scope |
| Customer | Striply | Inbound, synchronous | Open hosted checkout and submit a simulator scenario | HTTPS | Unexpired checkout-session URL grants access to one session | Current scope |
| Merchant application | Striply | Inbound, synchronous | Create checkout sessions and use permitted APIs | HTTPS/JSON | Organization-scoped API key and endpoint permission checks | Current scope |
| Striply | External webhook receiver | Outbound, synchronous attempt with asynchronous retry lifecycle | Deliver organization events | HTTPS/JSON | HMAC signature and timestamp using an endpoint-specific secret | Current scope |
| Striply operator | Striply | Inbound, restricted | Observe, diagnose, secure, and recover the platform | Administrative HTTPS and infrastructure access | Separate least-privilege operator identity and audited procedure | Planned |
| Striply | External email service | Outbound | Send invitations and transactional email | HTTPS provider API | Dedicated service credential | Planned after the first vertical slice |
| AWS hosting environment | Striply | Hosting relationship | Provide future compute, network, storage, and managed-service boundaries | Infrastructure APIs and runtime networking | IAM and workload identities, to be designed in Phase 5 | Planned Phase 5 |

## Trust Boundaries

1. **Public-user boundary:** Merchant and customer browsers are untrusted clients. Authentication does not make request data trusted.
2. **Merchant-system boundary:** Merchant applications and webhook receivers are controlled by merchants and remain outside Striply. They may be unavailable, compromised, slow, or incorrectly implemented.
3. **Third-party boundary:** The planned email provider receives only the minimum data required for delivery and must never receive payment credentials or platform secrets.
4. **Operator boundary:** Operational authority is separate from merchant membership and does not imply routine access to organization-specific data.
5. **Future cloud boundary:** AWS will host Striply in a later phase. IAM, network segmentation, secrets, and managed-service boundaries belong to the AWS deployment design, not this logical context.

## Security Consequences

- Every organization-owned API operation verifies both permission and tenant ownership.
- Public checkout access is restricted to one unexpired session and exposes no merchant-dashboard data.
- API keys are organization-scoped and stored as hashes.
- Webhook receivers verify HMAC signatures and tolerate duplicate event delivery.
- External failures do not roll back already committed payment or refund state.
- Logs, metrics, email requests, and webhook payloads exclude credentials and unnecessary personal data.

## Assumptions

- The merchant dashboard and hosted checkout are owned and delivered as parts of Striply.
- The internal payment simulator is inside the Striply boundary and contacts no real financial network.
- A merchant application and webhook receiver may be operated by the same merchant but are separate external systems with different credentials and trust mechanisms.
- The first local deployment does not require AWS or the external email service.
- The detailed operator-access mechanism will be designed during the security and operations phases.

## Known Limitations

- The diagram describes logical ownership, not current implementation or deployment topology.
- AWS is shown only to record its planned hosting relationship; detailed services and network zones are intentionally omitted.
- No real payment provider, bank, card network, fraud service, or settlement system exists in the planned system context.
- Email-provider selection and message flows remain undecided.
- Container, component, data-flow, and deployment boundaries require separate diagrams.

## Decision Explained

The central decision is that the dashboard, hosted checkout, API, simulator, processing logic, and storage collectively form Striply. Merchant software, webhook receivers, and third-party services remain external even when a merchant controls them. This prevents external clients from being treated as trusted internal components and establishes the tenant and network boundaries required by later security design.

C4-PlantUML is used because context, container, and component diagrams require explicit C4 architectural semantics. Mermaid remains the preferred notation for sequence, state, entity-relationship, and general flow diagrams.

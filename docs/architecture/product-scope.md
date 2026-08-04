# Product Scope

## Product Definition

Striply is simulated payment infrastructure for developers,
technical founders,
and small SaaS teams that need to build and test payment flows before integrating a real payment provider.
It lets merchants create an organization,
define products and prices,
generate hosted checkout sessions,
run predefined simulator scenarios, inspect payment outcomes and refunds, and receive webhook notifications. Striply
never collects or stores real card data; each payment uses a controlled scenario such as success, decline, insufficient
funds, delayed processing, or provider timeout. The project goes beyond ordinary CRUD by modeling payment state
transitions, duplicate requests, concurrent operations, tenant isolation, refund limits, and reliable webhook delivery.
The first release focuses on the complete simulated checkout-to-refund workflow. Real payment processing, subscriptions,
invoicing, fraud detection, multi-region deployment, and PCI-regulated card processing are deferred.

## Project Exclusions

### Excluded From the Planned Project

Striply is not intended to provide:

- real authorization, capture, settlement, or movement of money;
- collection, transmission, or storage of real card numbers, security codes, bank-account details, or payment credentials;
- merchant balances, payouts, transfers, or reconciliation with banking networks;
- PCI DSS certification or claims of compliance with financial-services regulations;
- production banking, card-network, or acquiring-bank integrations;
- real 3-D Secure or other cardholder-authentication flows;
- chargeback, representment, or payment-dispute processing;
- production service-level guarantees for external users.

These exclusions prevent Striply from being represented or used as a real payment processor.

### Deferred Beyond the First Release

The following capabilities are outside the first checkout-to-refund vertical slice:

- subscriptions, invoicing, billing cycles, proration, dunning, and usage-based billing are deferred to Phase 7 and require a stable one-time payment flow first;
- a Stripe test-mode adapter is deferred until the internal simulator and payment-provider boundary are stable;
- fraud-detection experiments are unscheduled and require stable payment data and an explicit threat model first;
- Redis, background workers, and a message broker are deferred until the synchronous core flow exposes a concrete reliability or throughput need;
- AWS deployment and Terraform are deferred to Phase 5 and require a reliable Docker Compose deployment first;
- Kubernetes is deferred to Phase 6 and requires a stable cloud deployment, health checks, resource measurements, and operational procedures first;
- multi-region deployment is unscheduled and requires measured single-region limitations, recovery exercises, and a justified availability requirement first.

Deferred capabilities must not complicate the first-release design unless an explicit current requirement depends on a compatible extension point.

### Simulator Limitations

The initial payment provider intentionally differs from a real payment provider:

- it accepts named simulator scenarios rather than card or bank credentials;
- it produces controlled success, decline, insufficient-funds, delayed-processing, and provider-timeout outcomes;
- it does not contact card networks, banks, acquirers, or real payment providers;
- it performs no real authorization, capture, settlement, payout, chargeback, or 3-D Secure operation;
- its outcomes are deterministic for repeatable development and testing;
- its configured delays and failures demonstrate application behavior but do not represent real-provider latency or availability;
- a successful simulated payment records application state only and has no monetary value outside Striply.

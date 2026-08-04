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
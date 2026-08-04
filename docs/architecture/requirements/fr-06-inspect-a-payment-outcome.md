# FR-06 — Inspect a payment outcome

## Actor

An authenticated organization member with permission to view payments, or an authenticated API client authorized for the current organization.

## Precondition

The requested payment or payment intent exists and belongs to the current organization.

## Trigger

The actor requests a payment list or the details of a payment or payment intent.

## Required behavior

Striply returns only records owned by the current organization. Payment details include public identifiers, amount, currency, status, simulator outcome, customer email, relevant checkout-session identifier, refundable and refunded amounts, and creation and update timestamps. The response excludes internal database identifiers, secrets, and records owned by other organizations.

## Successful outcome

The actor can inspect the current outcome and monetary history of the organization's simulated payments.

## Failure or alternate outcomes

- Unauthenticated callers are rejected.
- Callers without payment-view permission are rejected.
- Missing or foreign-organization identifiers produce the same generic not-found response.
- Invalid filtering or pagination parameters are rejected without returning partial cross-tenant data.

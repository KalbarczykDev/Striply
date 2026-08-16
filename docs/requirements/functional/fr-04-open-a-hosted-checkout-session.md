# FR-04 — Open a hosted checkout session

## Actor

A customer who possesses a valid hosted-checkout URL.

## Precondition

The checkout session exists and is associated with an active merchant organization.

## Trigger

The customer opens the hosted-checkout URL.

## Required behavior

Striply displays the snapshotted product name, quantity, total amount, currency, and the merchant's display name. If the session is `OPEN` and has not expired, Striply displays the simulated-payment form and pre-fills any customer email stored on the session. The public page must not expose private merchant data, internal database identifiers, API credentials, or resources from another checkout session.

## Successful outcome

The customer can review the purchase and submit one of the supported simulator scenarios.

## Failure or alternate outcomes

- An unknown session identifier returns a generic not-found response.
- An expired session is marked `EXPIRED` and cannot accept payment.
- A completed or canceled session displays its final status and cannot accept another payment.
- A session belonging to an inactive organization is unavailable.

The response must not reveal whether an inaccessible session belongs to another merchant.

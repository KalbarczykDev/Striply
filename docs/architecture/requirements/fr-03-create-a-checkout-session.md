# FR-03 — Create a checkout session

## Actor

An authenticated merchant user or API client authorized to create checkout sessions for the current organization.

## Precondition

The referenced one-time price exists, is active, belongs to an active product, and both resources belong to the current organization.

## Trigger

The actor submits a price identifier, a quantity from `1` to `100`, an optional customer email address, and approved success and cancellation URLs.

## Required behavior

Striply creates an `OPEN` checkout session with a non-sequential public identifier beginning with `cs_`. The session stores an immutable snapshot of the product name, unit amount, currency, and quantity so later catalog changes cannot alter the checkout. The session expires 30 minutes after creation. Success and cancellation URLs must use an approved scheme and host configured for the organization.

## Successful outcome

The actor receives the session identifier, hosted-checkout URL, status, and expiration timestamp.

## Failure or alternate outcomes

Striply rejects the request when:

- the caller is unauthenticated or unauthorized;
- the product or price is missing, inactive, or owned by another organization;
- the price does not belong to the product or is not a one-time price;
- the quantity is outside the permitted range;
- the customer email is malformed;
- either redirect URL is missing or not approved for the organization.

A rejected request creates no session. Until idempotency is introduced, repeating a successful request may create a separate checkout session.

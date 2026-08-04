# FR-02 — Create a price

## Actor

An authenticated organization member with permission to manage the catalog.

## Precondition

The referenced product exists, is `ACTIVE`, and belongs to the actor's current active organization.

## Trigger

The actor submits the product identifier, a strictly positive integer amount expressed in the currency's minor unit, and a supported three-letter ISO 4217 currency code.

## Required behavior

Striply creates an `ACTIVE`, one-time price under the referenced product and current organization. The price receives a non-sequential public identifier beginning with `price_`. Its amount and currency cannot be changed after creation; the actor must create a new price to offer a different amount or currency. The first release supports `PLN`, `EUR`, and `USD`.

## Successful outcome

The actor receives the created price and can use its public identifier when creating checkout sessions.

## Failure or alternate outcomes

Striply rejects the request when:

- the caller is unauthenticated or lacks catalog-management permission;
- the current organization is missing or inactive;
- the product does not exist, is inactive, or belongs to another organization;
- the amount is zero, negative, or not an integer minor-unit value;
- the currency is malformed or unsupported.

A rejected request does not create a price.

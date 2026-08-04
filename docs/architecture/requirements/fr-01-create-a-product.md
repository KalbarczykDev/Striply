# FR-01 — Create a product

## Actor

An authenticated organization member with permission to manage the catalog.

## Precondition

The actor belongs to the current active organization.

## Trigger

The actor submits a product name and an optional description.

## Required behavior

Striply creates an `ACTIVE` product owned by the current organization and assigns it a non-sequential public identifier beginning with `prod_`. The normalized product name must contain between 1 and 200 characters. The optional description must not exceed 1,000 characters.

## Successful outcome

The actor receives the created product and can use its public identifier when defining prices.

## Failure or alternate outcomes

Striply rejects the request when:

- the caller is unauthenticated or lacks catalog-management permission;
- the current organization is missing or inactive;
- the name is blank or exceeds 200 characters;
- the description exceeds 1,000 characters;
- the request attempts to create the product for another organization.

A rejected request does not create a product.

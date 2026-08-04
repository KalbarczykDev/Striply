# FR-07 — Issue a refund

## Actor

An authenticated organization member with refund permission, or an authenticated API client authorized to create refunds for the current organization.

## Precondition

The referenced payment exists, belongs to the current organization, succeeded, and has a positive refundable balance.

## Trigger

The actor submits the payment identifier and either a positive partial amount or a request to refund the full remaining balance.

## Required behavior

Striply creates a refund with a non-sequential public identifier beginning with `re_`. The refund uses the payment's currency and records its amount and status. The sum of successful and in-progress refunds must never exceed the original payment amount, including when multiple refund requests execute concurrently. A full refund uses the entire remaining refundable balance; a partial refund reduces that balance by the accepted amount.

## Successful outcome

The actor receives the refund, updated refunded amount, and remaining refundable balance. Striply records an event for the accepted refund outcome.

## Failure or alternate outcomes

Striply rejects the request when the caller is unauthorized; the payment is missing, unsuccessful, or owned by another organization; the requested amount is zero, negative, uses another currency, or exceeds the remaining balance; or no refundable balance remains. A rejected request does not alter the payment balance or create a refund. Until idempotency is introduced, identical sequential requests are separate refund attempts, but monetary invariants still apply.

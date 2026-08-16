# FR-09 — Deliver a webhook event

## Actor

Striply, acting after an organization-owned event reaches a deliverable outcome.

## Precondition

An enabled webhook endpoint belonging to the same organization subscribes to the event type.

## Trigger

A supported payment, refund, or checkout event is recorded for delivery after its business-state change has committed.

## Required behavior

Striply creates an immutable webhook event with a public identifier beginning with `evt_` and a separate delivery record for each matching endpoint. It sends a JSON payload containing the event identifier, event type, creation time, organization context, and public resource data. Every request includes an HMAC signature and timestamp generated from the endpoint's signing secret. Each attempt records its time, outcome, duration, and bounded response metadata.

Non-success responses, network errors, and timeouts are retried using bounded exponential backoff. Delivery is at least once, so receivers may receive the same event more than once and can deduplicate it using the event identifier. Retries never change the original event payload.

## Successful outcome

A `2xx` response within the configured timeout marks the delivery successful and stops automatic retries.

## Failure or alternate outcomes

- Failures remain visible in delivery history with no signing secrets or sensitive response bodies exposed.
- Automatic retries stop after the configured maximum attempt count and mark the delivery exhausted.
- Disabled endpoints receive no new automatic deliveries.
- A failure to deliver a webhook does not roll back the committed payment, refund, or checkout change.
- An authorized merchant may manually retry an unsuccessful or exhausted delivery.

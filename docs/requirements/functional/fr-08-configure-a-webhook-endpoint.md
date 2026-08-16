# FR-08 — Configure a webhook endpoint

## Actor

An authenticated organization member with permission to manage developer integrations.

## Precondition

The actor belongs to the current active organization.

## Trigger

The actor submits an endpoint URL and selects one or more supported event types.

## Required behavior

Striply creates an organization-owned webhook endpoint with a non-sequential public identifier beginning with `wh_` and generates a signing secret. The full signing secret is returned only at creation; later views show only a safe identifying fragment. The endpoint can be disabled without deleting its delivery history. Production-style configuration accepts HTTPS URLs; explicitly configured local-development environments may accept loopback HTTP URLs.

## Successful outcome

The actor receives the endpoint identifier, URL, subscribed event types, status, and one-time signing secret.

## Failure or alternate outcomes

Striply rejects the request when the caller lacks permission; the URL is malformed, uses embedded credentials, or uses a prohibited scheme or host; no supported event type is selected; or the request attempts to configure another organization's endpoint. Rejected requests do not create an endpoint or disclose a secret.

# FR-10 — Enforce organization isolation

## Actor

Any authenticated merchant user or API client accessing an organization-owned resource.

## Precondition

The caller has an authenticated identity and a current organization context.

## Trigger

The caller attempts to create, list, view, change, or act upon an organization-owned resource.

## Required behavior

Striply verifies both the caller's permission and the resource's organization ownership for every protected operation. Organization ownership is derived from authenticated context and persisted relationships, never trusted solely from a client-supplied organization identifier. Lists and searches are scoped to the current organization before filtering or pagination. Relationships between resources may only be created when every referenced resource belongs to the same organization.

## Successful outcome

An authorized caller can access only the permitted resources belonging to the current organization.

## Failure or alternate outcomes

- Unauthenticated callers are rejected without resource data.
- Authenticated callers lacking the required role or permission are denied.
- Direct references to resources owned by another organization return the same generic not-found response as missing resources when necessary to prevent identifier enumeration.
- Rejected cross-organization operations make no state change and return no foreign resource attributes.
- Security-relevant denied operations are recorded in an audit trail without storing credentials or secrets.

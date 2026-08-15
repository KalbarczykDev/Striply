# Authentication and Refresh-Token Flow

## Status

Accepted design for VS-003; implementation is not yet complete.

- **Architecture version:** Phase 1, version 0.1
- **Date:** 2026-08-08
- **Diagram source:** [`../diagrams/authentication-sequence.mmd`](../diagrams/authentication-sequence.mmd)

## Scope

This document defines login, access-token issuance, refresh-token rotation, replay detection, and concurrent refresh behavior for authenticated human users. Registration follows the same token-issuance transaction as successful login after the user is created. Password reset, email verification, social login, and organization authorization are outside VS-003.

## Participants and Trust Boundaries

- The user and browser are outside the backend trust boundary.
- The browser calls the Spring Boot API synchronously over HTTPS.
- The React application keeps the access token in memory.
- The refresh token is sent only as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie outside local development.
- PostgreSQL stores password hashes, refresh-token hashes, token-family state, and security events. It never stores raw passwords or raw refresh tokens.
- Token signing keys and any token-hashing key material come from external configuration and are not stored in the repository.

## Login Decisions

- Login accepts email and password.
- Email is normalized before lookup.
- Unknown email, incorrect password, and a disabled account return the same `401 INVALID_CREDENTIALS` contract.
- Password verification uses the stored password hash when a user exists and a fixed dummy hash otherwise, reducing account enumeration through obvious timing differences.
- A successful login creates a new refresh-token family representing that browser or device session.
- The access token expires after five minutes.
- Each refresh token expires after seven days, capped by the family's absolute expiry.
- A token family expires 30 days after the original login and rotation never extends that absolute deadline.

## Access-Token Contract

Access tokens are signed JWTs. They identify the authenticated user but do not contain credentials, personal data, organization context, or authorization roles.

Required claims are:

| Claim | Striply value                                     | Purpose                                                              |
|-------|---------------------------------------------------|----------------------------------------------------------------------|
| `iss` | `striply`                                         | Identifies Striply as the issuer                                     |
| `sub` | User public identifier such as `usr_...`          | Identifies the authenticated user without exposing the database ID   |
| `aud` | `striply-api`                                     | Restricts the intended recipient to the Striply API                  |
| `iat` | Issuance time                                     | Records when the token was created                                   |
| `exp` | Five minutes after issuance                       | Prevents use after the short access-token lifetime                   |
| `jti` | Cryptographically unpredictable unique identifier | Distinguishes individual access tokens for safe security correlation |

The token must not include email, password information, refresh-token hashes, refresh-token family identifiers, internal database identifiers, organization identifiers, or roles. Organization membership and permissions can change while an access token remains valid; VS-004 will establish organization context through current server-side authorization.

JWT payloads are signed but not encrypted. Claims must therefore be treated as readable by the token holder and must contain no secrets.

## JWT Signing and Validation

VS-003 uses HMAC-SHA-256 (`HS256`) because the modular monolith is currently the only component issuing and validating access tokens.

- The signing secret contains at least 32 cryptographically random bytes.
- The configured secret is Base64 encoded for transport and decoded before use.
- Validation accepts exactly `HS256`; it does not select an algorithm based only on an untrusted token header.
- Validation requires the expected issuer, audience, signature, and expiration.
- Only a small documented clock-skew allowance may be accepted.
- Striply uses Spring Security's maintained JWT encoder and decoder rather than implementing JWT serialization or signature verification itself.

Asymmetric signing should be reconsidered if independently deployed services need to validate tokens without receiving authority to issue them. In that design, the identity component would retain the private key and validators would receive only the public key.

## Signing-Key Configuration

The application reads JWT settings from external configuration:

| Environment variable        | Required value                                              |
|-----------------------------|-------------------------------------------------------------|
| `STRIPLY_JWT_SECRET_BASE64` | Base64-encoded secret containing at least 32 random bytes   |
| `STRIPLY_JWT_ISSUER`        | Expected issuer; `striply` for the initial deployment       |
| `STRIPLY_JWT_AUDIENCE`      | Expected audience; `striply-api` for the initial deployment |

There is no default signing secret in the main application configuration. Application startup must fail with a useful diagnostic when the secret is absent, malformed, or too short. Tests provide a dedicated test-only key. Signing secrets and complete JWT values must never be committed, logged, placed in error responses, or exposed through metrics. Production deployment will eventually obtain the signing secret from AWS Secrets Manager or an equivalent secret store.

Key rotation is deferred. When implemented, tokens will carry a `kid` header and validation will temporarily support the active and retiring keys for a bounded overlap period.

## Password Policy

The initial release has no multi-factor authentication, so registration requires passwords between 15 and 128 characters.

- All Unicode characters, spaces, and password-manager-generated values are allowed.
- Striply does not require uppercase letters, lowercase letters, digits, or special characters.
- Passwords are never silently trimmed, normalized, or truncated.
- Maximum length is validated before expensive hashing to reduce denial-of-service risk.
- Periodic password changes are not required without evidence of compromise.
- Breached-password screening and a password-strength meter are recommended protections but are deferred and must remain documented as such.

## Password Storage

Passwords are hashed with Argon2id using the initial minimum parameters:

| Parameter   | Value                                                |
|-------------|------------------------------------------------------|
| Memory      | 19 MiB (`19456` KiB)                                 |
| Iterations  | `2`                                                  |
| Parallelism | `1`                                                  |
| Salt        | Unique random salt generated by the password encoder |

The stored value includes an algorithm identifier, such as `{argon2}`, so a delegating password encoder can recognize existing hashes and support future parameter or algorithm upgrades. Hash parameters must be benchmarked on the actual deployment class before production use; security is not improved if authentication becomes an easy resource-exhaustion target.

Fast general-purpose hashes such as SHA-256 must not be used for passwords. They remain appropriate for lookup of independently generated, high-entropy refresh-token secrets because those secrets are not human chosen and cannot be feasibly guessed like passwords.

Current guidance used for these decisions:

- [RFC 7519 — JSON Web Token registered claims](https://datatracker.ietf.org/doc/rfc7519/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security password storage documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

## Refresh-Token Physical Schema

Migration `V2` will introduce `refresh_token_family` and `refresh_token`. These records are internal authentication state and do not receive public identifiers.

### `refresh_token_family`

| Column                | PostgreSQL type | Nullability | Ownership and meaning                                         |
|-----------------------|-----------------|-------------|---------------------------------------------------------------|
| `id`                  | `UUID`          | `NOT NULL`  | Application-generated internal primary key                    |
| `user_id`             | `UUID`          | `NOT NULL`  | References the owning `app_user.id`                           |
| `created_at`          | `TIMESTAMPTZ`   | `NOT NULL`  | Family creation time, application supplied                    |
| `absolute_expires_at` | `TIMESTAMPTZ`   | `NOT NULL`  | Fixed deadline 30 days after login; rotation never extends it |
| `revoked_at`          | `TIMESTAMPTZ`   | nullable    | Time at which the complete family was revoked                 |
| `revocation_reason`   | `VARCHAR(32)`   | nullable    | Bounded internal reason code, present only when revoked       |

Required constraints:

- Primary key on `id`.
- Foreign key from `user_id` to `app_user(id)` using `ON DELETE RESTRICT`.
- `absolute_expires_at > created_at`.
- `revoked_at IS NULL OR revoked_at >= created_at`.
- `revoked_at` and `revocation_reason` are either both null or both non-null.
- `revocation_reason` is limited to `LOGOUT`, `TOKEN_REUSE`, and `SECURITY_ACTION` when present.

Required index:

```text
(user_id) WHERE revoked_at IS NULL
```

This supports listing or revoking a user's active login sessions. Expiry remains part of the query because PostgreSQL partial-index predicates must not depend on the changing current time.

### `refresh_token`

| Column        | PostgreSQL type | Nullability | Ownership and meaning                                  |
|---------------|-----------------|-------------|--------------------------------------------------------|
| `id`          | `UUID`          | `NOT NULL`  | Application-generated internal primary key             |
| `family_id`   | `UUID`          | `NOT NULL`  | References the owning refresh-token family             |
| `token_hash`  | `BYTEA`         | `NOT NULL`  | SHA-256 hash of a 32-byte random refresh secret        |
| `created_at`  | `TIMESTAMPTZ`   | `NOT NULL`  | Token creation time, application supplied              |
| `expires_at`  | `TIMESTAMPTZ`   | `NOT NULL`  | Seven-day token deadline capped by the family deadline |
| `consumed_at` | `TIMESTAMPTZ`   | nullable    | Set exactly once by successful rotation                |

Required constraints:

- Primary key on `id`.
- Foreign key from `family_id` to `refresh_token_family(id)` using `ON DELETE RESTRICT`.
- Unique constraint on `token_hash`.
- `octet_length(token_hash) = 32` for a SHA-256 result.
- `expires_at > created_at`.
- `consumed_at IS NULL OR consumed_at >= created_at`.

Required indexes:

```text
UNIQUE (family_id) WHERE consumed_at IS NULL
(family_id, created_at DESC)
```

The partial unique index enforces at most one unconsumed token in a family. The history index supports inspection of rotations within one family. The unique constraint on `token_hash` already creates the index used to find a presented token, so a duplicate standalone hash index must not be added.

### Invariants Enforced by the Application Transaction

PostgreSQL `CHECK` constraints cannot compare a token row with its family row. The refresh transaction must therefore enforce:

- `refresh_token.expires_at <= refresh_token_family.absolute_expires_at`;
- the family is not revoked or absolutely expired;
- the presented token is not expired;
- exactly one unconsumed token is changed to consumed;
- exactly one replacement is inserted after successful consumption;
- replay detection revokes the family containing the consumed token.

The application supplies all UUIDs and timestamps from an injected clock. PostgreSQL constraints remain the final defense against invalid relationships and malformed stored values. Entity callbacks are not responsible for security-critical timestamps because retry and concurrency tests require explicit, controllable time.

### Deletion and Retention

Authentication records are not cascade-deleted in VS-003. `ON DELETE RESTRICT` avoids silently erasing security history if user deletion is introduced accidentally. Account deletion, anonymization, and token-retention cleanup require a separate explicit policy.

Expired and consumed token rows may be cleaned up later by a bounded maintenance job, but cleanup is deferred until retention requirements exist. Until then, they remain available for replay detection and security investigation.

## Refresh Rotation

A valid refresh request consumes the presented token and creates exactly one replacement in the same database transaction. The raw replacement is returned only in the response cookie; PostgreSQL stores its cryptographic hash.

The rotation transaction must ensure that two requests cannot both consume the same token. The persistence implementation must use a row lock or an equivalent conditional atomic update and verify that exactly one row was changed before inserting the replacement.

## Invalid and Expired Tokens

Malformed, unknown, expired, or family-revoked refresh tokens return the same public `401 INVALID_REFRESH_TOKEN` response. The response clears the refresh cookie. Internal logs and audit data may record a bounded reason code but must not contain the raw token, its cookie value, or its stored hash.

## Replay Detection

Presenting a previously consumed refresh token is treated as suspected token theft. Striply revokes only the affected token family, records a security event, clears the browser cookie, and requires a new login. Other token families belonging to the same user remain active.

The public response does not reveal that replay detection occurred. A future explicit password-change or "log out everywhere" operation may revoke every active family for the user.

## Concurrent Refresh Behavior

If two requests present the same refresh token concurrently, only one request may rotate it. The second request observes that the token was consumed and triggers the strict replay policy. Consequently, the replacement created by the first request is also invalid because the complete family becomes revoked.

This policy favors compromise containment over session availability. The browser client must coordinate access-token renewal so that only one refresh request is in flight at a time. A future implementation may reconsider a narrowly bounded concurrency grace mechanism only if production evidence shows legitimate duplicate refreshes are common and the security implications are documented.

## Failure Behavior

- A database failure before commit produces no usable replacement token.
- A database commit followed by a lost HTTP response leaves the old token consumed. Retrying it triggers replay detection and revokes the family; the user must log in again.
- A token-generation failure rolls back the rotation transaction.
- An invalid request origin is rejected before token rotation.
- Tokens and credentials are excluded from application logs, metrics, and error responses.

## Logout Behavior

Refresh-token logout is implemented at the application-service boundary. A known unconsumed token revokes its family with the `LOGOUT` reason. The token is not consumed or deleted, preserving authentication history.

Logout with an unknown token is an idempotent no-op. This avoids revealing whether a presented refresh token exists.

Logout with a previously consumed token is treated internally as replay. The family is revoked with `TOKEN_REUSE`, and any replacement token in that family becomes unusable. An already-revoked family retains its original revocation timestamp and reason.

The HTTP logout endpoint and refresh-cookie clearing behavior are not yet implemented.

## Assumptions

- Browser and API deployment remain same-site so `SameSite=Strict` is usable.
- HTTPS is mandatory outside local development.
- Access tokens are signed and independently validated by the backend.
- Refresh tokens contain enough cryptographic entropy to resist offline guessing.
- Registration automatically authenticates the newly created user.

## Known Limitations and Deferred Protections

- Rate limiting and brute-force enforcement are required but may be implemented in a later ticket; the risk must remain documented until then.
- Email verification, password reset, session-management UI, and "log out everywhere" are deferred.
- A lost response after a successful rotation can force another login under the strict replay policy.
- Cookie behavior for cross-site frontend deployment would require revisiting `SameSite` and CSRF protection.

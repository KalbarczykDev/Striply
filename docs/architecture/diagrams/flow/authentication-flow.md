# Authentication and Refresh-Token Flow

## Status

Review

## Date

15-08-2026

## Scope

This document defines login, access-token issuance, refresh-token rotation, replay detection, and concurrent refresh behavior for authenticated human users. Registration follows the same token-issuance transaction as successful login after the user is created. Password reset, email verification, social login, and organization authorization are outside VS-003.

## Participants and Trust Boundaries

- The user and browser are outside the backend trust boundary.
- The browser calls the Spring Boot API synchronously over HTTPS.
- The React application keeps the access token in memory.
- The refresh token is sent only as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie outside local development.
- PostgreSQL stores password hashes, refresh-token hashes, token-family state, and security events. It never stores raw passwords or raw refresh tokens.
- Local development uses a checked-in JWT signing key. Shared and deployed environments provide their own key through external configuration.

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
| `sub` | User UUID                                         | Identifies the authenticated user with a non-sequential identifier   |
| `aud` | `striply-api`                                     | Restricts the intended recipient to the Striply API                  |
| `iat` | Issuance time                                     | Records when the token was created                                   |
| `exp` | Five minutes after issuance                       | Prevents use after the short access-token lifetime                   |
| `jti` | Cryptographically unpredictable unique identifier | Distinguishes individual access tokens for safe security correlation |

The token must not include email, password information, refresh-token hashes, refresh-token family identifiers, organization identifiers, roles, or identifiers for unrelated database records. Organization membership and permissions can change while an access token remains valid; VS-004 will establish organization context through current server-side authorization.

JWT payloads are signed but not encrypted. Claims must therefore be treated as readable by the token holder and must contain no secrets.

## JWT Signing and Validation

VS-003 uses HMAC-SHA-256 (`HS256`) because the modular monolith is currently the only component issuing and validating access tokens.

- The signing secret contains at least 32 UTF-8 bytes. Keys used outside local development must be generated randomly.
- Validation accepts exactly `HS256`; it does not select an algorithm based only on an untrusted token header.
- Validation requires the expected issuer, audience, signature, and expiration.
- Only a small documented clock-skew allowance may be accepted.
- Striply uses Spring Security's maintained JWT encoder and decoder rather than implementing JWT serialization or signature verification itself.

Asymmetric signing should be reconsidered if independently deployed services need to validate tokens without receiving authority to issue them. In that design, the identity component would retain the private key and validators would receive only the public key.

## Signing-Key Configuration

The application reads the JWT signing key from this environment variable:

| Environment variable | Value                                           |
|----------------------|-------------------------------------------------|
| `SECRET_KEY`         | JWT signing secret containing at least 32 bytes |

When `SECRET_KEY` is absent, local development uses `local-development-signing-key-change-me`. This default is public and provides no protection if reused elsewhere. Shared, staging, and production environments must override it with a randomly generated secret. The application rejects configured keys shorter than 32 bytes.

The issuer is `striply` and the audience is `striply-api`. Signing keys and complete JWT values must never be logged, placed in error responses, or exposed through metrics. Production deployment will eventually obtain its key from AWS Secrets Manager or an equivalent secret store.

Key rotation is deferred. When implemented, tokens will carry a `kid` header and validation will temporarily support the active and retiring keys for a bounded overlap period.

## Password Policy

The initial release has no multi-factor authentication, so registration requires at least 6 characters. A password must not exceed 72 bytes when encoded as UTF-8 because bcrypt cannot safely process a longer value.

- All Unicode characters, spaces, and password-manager-generated values are allowed.
- Striply does not require uppercase letters, lowercase letters, digits, or special characters.
- Passwords are never silently trimmed, normalized, or truncated.
- The UTF-8 byte limit is validated before expensive hashing.
- Periodic password changes are not required without evidence of compromise.
- Breached-password screening and a password-strength meter are recommended protections but are deferred and must remain documented as such.

## Password Storage

Passwords are hashed with bcrypt using strength `12`. Spring Security generates a unique random salt for each password. The stored value includes the `{bcrypt}` identifier so the application can recognize old hashes if the algorithm changes later.

Strength `12` must be benchmarked on the hardware used for deployment. A higher strength slows password guessing, but it also increases registration and login latency. Login rate limiting is still required because password verification intentionally consumes CPU.

Fast general-purpose hashes such as SHA-256 must not be used for passwords. They remain appropriate for lookup of independently generated, high-entropy refresh-token secrets because those secrets are not human chosen and cannot be feasibly guessed like passwords.

Current guidance used for these decisions:

- [RFC 7519 — JSON Web Token registered claims](https://datatracker.ietf.org/doc/rfc7519/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security password storage documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [ADR 0002: Use bcrypt for password storage](../../decisions/0002-use-bcrypt-for-password-storage.md)

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

## CSRF and Refresh-Cookie Scope

Spring Security's CSRF protection is currently disabled globally. The refresh endpoint relies on the refresh cookie being `Secure`, `HttpOnly`, and `SameSite=Strict`, and on the browser application and API remaining same-site. Under those constraints, the browser does not attach the refresh cookie to cross-site requests, which is the current protection against cross-site refresh and logout requests. `HttpOnly` prevents JavaScript from reading the token but does not itself provide CSRF protection.

This decision must be reviewed before any change that broadens when or where the cookie is sent, including:

- changing `SameSite` to `Lax` or `None`;
- broadening the cookie `Domain` or `Path`;
- deploying the browser application and API in a cross-site configuration; or
- adding another state-changing endpoint authenticated by cookies.

If any of those conditions change, do not continue relying on global CSRF disablement. Re-enable Spring Security CSRF protection for cookie-authenticated operations and define an explicit browser-compatible token strategy before deployment. Origin validation may be added as defense in depth, but CORS configuration alone is not CSRF protection.

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

## Assumptions

- Browser and API deployment remain same-site so `SameSite=Strict` is usable and provides the current CSRF boundary for refresh-token requests.
- HTTPS is mandatory outside local development.
- Access tokens are signed and independently validated by the backend.
- Refresh tokens contain enough cryptographic entropy to resist offline guessing.
- Registration automatically authenticates the newly created user.

## Known Limitations and Deferred Protections

- Rate limiting and brute-force enforcement are required but may be implemented in a later ticket; the risk must remain documented until then.
- Email verification, password reset, session-management UI, and "log out everywhere" are deferred.
- A lost response after a successful rotation can force another login under the strict replay policy.
- Any broader refresh-cookie scope or cross-site frontend deployment requires revisiting `SameSite` and the global CSRF disablement before deployment.

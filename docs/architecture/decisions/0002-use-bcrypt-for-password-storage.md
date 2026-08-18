# ADR 0002: Use bcrypt for password storage

## Status

Accepted

## Date

18-08-2026

## Context

Striply stores passwords for human users. If someone gets a copy of the database, the password hashes should make guessing the original passwords slow and expensive.

We need an algorithm that works well with Spring Security and is easy to run in the current application. It should be costly enough to slow down an attacker without making registration and login too slow.

## Decision

Striply uses bcrypt through Spring Security's `PasswordEncoder`.

New passwords use strength `12` and are stored with the `{bcrypt}` prefix. Spring Security generates a random salt for each password.

Striply passes the password directly to the encoder. It does not trim, normalize, pre-hash, or manually salt it. Because bcrypt accepts at most 72 bytes, registration rejects passwords longer than 72 bytes when encoded as UTF-8.

Login checks passwords with `PasswordEncoder.matches`. It never compares encoded strings directly.

We will benchmark strength `12` on the hardware used for deployment. If login is too fast or too slow, we will adjust the strength and record the result.

## Alternatives considered

### Argon2id

Argon2id is memory-hard and better protects against cracking with GPUs and other specialized hardware. OWASP currently prefers it for new systems. We chose bcrypt for the first version because Spring Security supports it without an extra cryptography provider and it is enough for the current scope. We should reconsider Argon2id if Striply needs stronger protection against offline attacks.

### scrypt

scrypt is also memory-hard. It has similar tuning and operational costs to Argon2id, with no clear benefit for this project.

### PBKDF2

PBKDF2 is widely supported and useful when FIPS compliance is required. Striply has no such requirement.

### SHA-256

SHA-256 is too fast for password storage. An attacker with the hashes could test guesses cheaply.

## Consequences

Bcrypt is mature, supported by Spring Security, and does not need another cryptography provider. Each hash has its own salt, and the `{bcrypt}` prefix gives us a way to support another algorithm later.

The tradeoff is that bcrypt is not memory-hard and limits passwords to 72 bytes. Registration and login also use noticeable CPU by design. If we replace bcrypt later, the application will need to recognize old hashes until users log in again or reset their passwords.

## Risks

Strength `12` will become less effective as hardware improves. Raising it too far could make authentication slow or help an attacker exhaust server resources. Login rate limiting is still required.

The 72-byte limit must be checked using the UTF-8 representation, not the number of Java characters.

## When to reconsider

Revisit this decision if benchmarks show that strength `12` is unsuitable, security guidance changes, Striply needs stronger protection against offline attacks, or FIPS compliance becomes a requirement.

## References

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

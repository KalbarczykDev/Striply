# AGENTS.md

## Project

Striply is a simulated payment platform built with Java, Spring Boot, React, TypeScript, PostgreSQL, and Docker.
Inspired by stripe build as portfolio project.
Never process or store real payment-card data.

## Architecture

- Build a modular monolith.
- Organize backend code by business module.
- Keep business logic out of controllers and repositories.
- Respect module boundaries.
- Avoid premature microservices and abstractions.
- Read relevant ADRs before changing architecture.

## Security

- Enforce authentication, organization ownership, and permissions on every protected operation.
- Treat cross-tenant access as a critical defect.
- Never commit or log secrets, passwords, tokens, API keys, or webhook secrets.
- Never expose sequential database IDs.
- Validate all external input.
- Return safe errors without internal details.

## Data

- Store monetary amounts as integers in the currency’s smallest unit.
- Use explicit currency codes.
- Use Flyway for all schema changes.
- Do not modify previously applied migrations.
- Use database constraints for enforceable invariants.
- Consider transactions and concurrency for payments and refunds.

## Testing

- Follow Test Driven Development 
- Add tests for changed behavior and bug fixes.
- Test domain rules with unit tests.
- Use Testcontainers with PostgreSQL for persistence, transactions, constraints, and concurrency.
- Tests should verify Striply behavior, not framework behavior.

## Frontend

- Use strict TypeScript and typed API contracts.
- Keep API calls in a dedicated client layer.
- Include loading, empty, error, and permission-aware states.
- Use accessible controls and validated forms.

## Workflow

Before making changes:

1. Check `git status`.
2. Read relevant code, tests, and documentation.
3. Preserve unrelated user changes.
4. Make the smallest coherent change.

After making changes:

1. Review the diff.
2. Run relevant tests and checks.
3. Update documentation when behavior or architecture changes.
4. Report what changed, what was tested, and any remaining risks.

Do not commit, push, delete files, or make broad refactors unless explicitly requested.
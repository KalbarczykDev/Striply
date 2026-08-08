# Local PostgreSQL Setup

This document explains how to run the PostgreSQL database used by Striply during local development and how to verify the database migrations with Testcontainers.

## Prerequisites

- Docker with Docker Compose support
- Java 25
- The Gradle wrapper included in this repository

Docker must be running before starting PostgreSQL or executing integration tests that use Testcontainers.

## Start PostgreSQL

Start the local PostgreSQL container:

```bash
docker compose up -d postgres
```

Check whether the container is running and healthy:

```bash
docker compose ps
```

## Stop PostgreSQL

Stop the container without deleting its persisted data:

```bash
docker compose stop postgres
```

The database data is stored in the `striply_postgres_data` Docker volume and remains available the next time the container starts.

## Database Configuration

Spring Boot reads the database connection from these environment variables:

| Environment variable | Local default | Purpose |
| --- | --- | --- |
| `STRIPLY_DB_URL` | `jdbc:postgresql://localhost:5432/striply` | JDBC connection URL |
| `STRIPLY_DB_USERNAME` | `app_user` | Database username |
| `STRIPLY_DB_PASSWORD` | `app_password` | Database password |

The defaults match the PostgreSQL service in `compose.yaml`, so no environment variables are required for the standard local setup. To override them for the current terminal session:

```bash
export STRIPLY_DB_URL=jdbc:postgresql://localhost:5432/striply
export STRIPLY_DB_USERNAME=app_user
export STRIPLY_DB_PASSWORD=app_password
```

These credentials are local-development defaults, not production secrets. They must not be reused in shared, staging, or production environments. Non-local credentials must be supplied through environment variables or an appropriate secret-management system.

## Run Integration Tests

Run the complete test suite:

```bash
./gradlew test
```

The database integration tests use Testcontainers. They start an isolated PostgreSQL container automatically and do not use or modify the persistent local development database. Docker must still be running.

## Migration Rules

- Add migrations under `src/main/resources/db/migration`.
- Use Flyway versioned names such as `V2__add_organization.sql`.
- Never modify a migration after it has been shared or merged.
- Create a new migration for every subsequent schema change.
- Keep each migration focused on the schema required by the current ticket.
- Verify migrations against PostgreSQL through Testcontainers; do not substitute H2.
- Do not rely on Hibernate to create or update the schema. Hibernate validates the schema managed by Flyway.

## Troubleshooting

### Docker is unavailable

Ensure Docker Desktop or the Docker daemon is running. Both Docker Compose and Testcontainers require access to Docker.

### Port 5432 is already occupied

If Docker reports that port `5432` is already allocated, stop the other PostgreSQL service or container using that port before starting Striply's PostgreSQL container.

### Authentication or connection failure

Verify that `STRIPLY_DB_URL`, `STRIPLY_DB_USERNAME`, and `STRIPLY_DB_PASSWORD` agree with the database being used. Unset incorrect overrides to return to the local defaults.

### Flyway reports an unsupported PostgreSQL version

Ensure the project includes the PostgreSQL-specific Flyway runtime dependency, `flyway-database-postgresql`, and refresh the Gradle dependencies in the IDE.

### Testcontainers cannot start PostgreSQL

Confirm Docker is running and that the current user can communicate with it. The persistent Compose database does not need to be running because Testcontainers creates a separate temporary database.

### Migration validation fails

Do not edit a previously applied migration to repair the failure. Correct the local configuration if it is wrong, or create a new versioned migration when the schema genuinely needs to change.

# Striply

Striply is simulated payment infrastructure inspired by Stripe build as portfolio project.
Its purpose is to display skills in Java, Spring Boot, React, PostgreSQL, AWS, Microservices, Docker.


## Run Locally

Requirements:

- Java 25
- Docker with Docker Compose

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Start the application:

```bash
./gradlew bootRun
```

Run the test suite:

```bash
./gradlew test
```

See [Local PostgreSQL Setup](docs/operations/local-database.md) for configuration and troubleshooting.

## Documentation

- [Product scope](docs/architecture/product-scope.md)
- [Backend modules](docs/architecture/backend-modules.md)
- [Architecture decisions](docs/architecture/decisions)
- [System context](docs/architecture/system-context.md)
- [Container architecture](docs/architecture/container-diagram.md)
- [Authentication flow](docs/security/authentication-flow.md)
- [First vertical-slice backlog](docs/backlog/first-vertical-slice.md)

## License

[MIT](LICENSE).

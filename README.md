# Workshop Management System

Backend REST API for an automotive workshop, implemented as a Java 21/Spring Boot 4 modular monolith.

## Architecture

The Spring Modulith modules follow the project context map:

- Registrations
- Service Lifecycle
- Stock & Procurement

See [Project Structure](docs/PROJECT-STRUCTURE.md) and [AGENTS.md](AGENTS.md) before changing the application.

## Run locally

Requirements: Java 21 and Docker Compose.

```bash
make docker-up
```

The local Docker environment uses the `dev` profile and loads idempotent demonstration Customer and Stock Item records.
Copy `.env.example` to `.env` to override this behavior. Seeds are disabled in the default application profile.

Useful URLs:

- API base: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Development commands

```bash
make test
make coverage
make verify
make run-dev
```

Run `make help` for Docker and database commands. `make docker-reset` explicitly deletes the local database volume and is
needed once when adopting the initial Flyway baseline over a database previously created by Hibernate.

## Feature workflow

Feature specifications live under `docs/features/`. HTTP contract changes must update the generated OpenAPI expectations
and the collection at `docs/api/postman/workshop-management-system.postman_collection.json`.

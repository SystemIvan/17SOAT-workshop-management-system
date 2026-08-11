# Docker Development Environment

## Start

```bash
cp .env.example .env
make docker-up
make docker-logs
```

The compose stack exposes the application on port `8080` and MySQL on port `3306` by default. MySQL data is retained in
the `mysql_data` volume.

The local defaults enable `SPRING_PROFILES_ACTIVE=dev` and `APP_SEED_ENABLED=true`. Change either value in `.env` to run
without demonstration records. Flyway applies schema migrations before Hibernate validates the mappings.

## Commands

```bash
make docker-ps
make docker-logs
make db-shell
make docker-down
```

After migrating a database previously managed by `ddl-auto=update`, recreate it once:

```bash
make docker-reset
make docker-up
```

`docker-reset` deletes the local MySQL volume. It is never executed by build, test or startup targets.

## Configuration

See `.env.example` for database, port, profile and seed settings. Production-like environments must use
`DDL_AUTO=validate`, leave demonstration seeds disabled and manage credentials externally.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` after startup.

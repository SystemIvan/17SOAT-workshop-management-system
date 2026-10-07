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

If port `3306` is already in use on the host (for example by a local MySQL server), publish the container on another
port with `DB_PORT`, e.g. `DB_PORT=3307` in `.env` or `DB_PORT=3307 docker compose up -d --build`. The application
keeps reaching MySQL as `mysql:3306` inside the Docker network.

## Health probes

The application listens on a second, management port, `8081`, which serves only three anonymous `GET` endpoints:
`/actuator/health/liveness`, `/actuator/health/readiness` and `/actuator/health`. Every other management path answers
`401`, even with a valid JWT. Port `8081` is not published to the host; the `app` healthcheck runs
`/app/healthcheck.sh` inside the container against the readiness probe, so `make docker-ps` shows `healthy` once Flyway
has finished and MySQL is reachable.

- Liveness reflects only the application state and stays `UP` while MySQL is down, so the container is not restarted.
- Readiness includes the database: with MySQL stopped it answers `503` `{"status":"DOWN"}` and the `app` service
  becomes `unhealthy`; it returns to `healthy` without a restart once MySQL is back.

Check readiness manually (exit code `0` means ready):

```bash
docker exec workshop-app /app/healthcheck.sh && echo ready
```

`JAVA_OPTS` is passed to the JVM. When it is unset or empty in `.env`, the compose file and the image default to
`-XX:MaxRAMPercentage=75.0`, which sizes the heap from the container memory limit. `docker stop` sends `SIGTERM`
straight to the JVM, which runs a graceful shutdown before exiting. The `app` service sets `stop_grace_period: 35s`,
so Docker waits longer than Spring's 30-second shutdown phase timeout before sending `SIGKILL`.

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

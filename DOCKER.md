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

### E-mail Notifications (RF52)

The compose stack includes **Mailpit**, a local SMTP simulator, for demo and testing. The UI is available at
`http://localhost:8025` for inspecting e-mails sent by the application.

| Configuration | Environment Variable | Default | Note |
| --- | --- | --- | --- |
| **Channel** | `APP_NOTIFICATION_EMAIL_CHANNEL` | `smtp` | `log` uses simulated logging instead of SMTP. |
| **From address** | `APP_NOTIFICATION_EMAIL_FROM` | `no-reply@workshop.local` | Sender address in all outbound e-mails. |
| **Mail host** | `SPRING_MAIL_HOST` | `mailpit` | `mailpit` in docker-compose; update for cloud environments. |
| **Mail port** | `SPRING_MAIL_PORT` | `1025` | Mailpit SMTP port. For cloud (Kubernetes), use ConfigMap. |
| **Mail username** | `SPRING_MAIL_USERNAME` | *(empty)* | Mailpit requires no authentication. For production, store in Secret. |
| **Mail password** | `SPRING_MAIL_PASSWORD` | *(empty)* | Mailpit requires no authentication. For production, store in Secret. |

To disable SMTP notifications and use log-only mode, override `.env`:

```bash
APP_NOTIFICATION_EMAIL_CHANNEL=log docker compose up -d --build
```

For Kubernetes deployments:
- Add `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` and `APP_NOTIFICATION_EMAIL_FROM` to the application **ConfigMap**.
- Add `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` to a **Secret** (if required by your SMTP provider).
- Mailpit is not deployed to Kubernetes; use a production SMTP service and credentials instead.

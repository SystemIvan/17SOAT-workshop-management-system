# Technical Specification: Context Alignment and Project Standards

| Field | Value |
|---|---|
| Feature | `context-alignment-and-project-standards` |
| Status | Approved |
| Owner | Workshop team |
| Updated at | 2026-08-11 |
| Functional spec | `./functional-spec.md` |

## Context and design

The direct application modules become `registration`, `servicelifecycle` and `stockprocurement`. Existing aggregates move
below their owner without changing behavior. Nested documented packages reserve the remaining aggregates from the board.

Technician remains an internal Service Lifecycle capability because it supports assignment and execution but is not a
separate context in the map. Notification delivery follows the consumer-owned port decision in ADR 002.

## Interfaces and data flow

Existing `/api/customers`, `/api/technicians`, `/api/parts` and `/api/service-orders` paths and DTOs remain unchanged.
Springdoc adds `/v3/api-docs` and `/swagger-ui.html`. The Postman collection uses `baseUrl` and resource-ID variables.

## Persistence and bootstrap data

Flyway `V1` creates the schema currently represented by JPA. Hibernate uses `validate`. Existing local databases must be
recreated once because the project has no production data migration requirement.

Customer and Stock use development seeds. Seeders require the `dev` profile and `app.seed.enabled=true`, detect an
existing document/SKU and create domain-valid records only when absent. Test data remains fixture-owned.

## Security and operations

Seeds contain synthetic `.test` contact data, no credentials and are disabled by default. Existing endpoint exposure is
unchanged. Dependency and input/error exposure are reviewed before completion. `docker-reset` is the only destructive
local target and must be invoked explicitly.

## Test strategy

- Existing domain tests move with their packages.
- Modulith verification asserts the exact modules and internal-access rules.
- Application startup validates Flyway plus Hibernate against H2 in MySQL mode.
- Seeder tests verify idempotency.
- MockMvc verifies OpenAPI contains every current operation.

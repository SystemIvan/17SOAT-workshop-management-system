# Functional Specification: Context Alignment and Project Standards

| Field | Value |
|---|---|
| Feature | `context-alignment-and-project-standards` |
| Status | Approved |
| Owner | Workshop team |
| Updated at | 2026-08-11 |
| References | Miro Context Map and `AGENTS.md` |

## Problem and outcome

The source tree models Customer, Technician, Parts and Service Order as independent bounded contexts, diverging from the
agreed domain map. Development work also lacks a repeatable specification, API documentation and schema-evolution flow.

The outcome is a three-context modular monolith with executable boundaries, feature-oriented documentation, repeatable
local data, versioned schema and synchronized API tooling.

## Actors and scenarios

- Developers can locate behavior by bounded context and start features from standard specs.
- Reviewers can verify architecture, tests, security, migrations and HTTP documentation before completion.
- API consumers can explore Swagger and import a Postman collection.
- Developers can start a local environment with safe example customers and stock items.

## Business rules

- Existing REST behavior remains compatible.
- Example business data is never loaded outside an explicitly enabled development environment.
- Placeholder aggregates receive no speculative behavior.
- Notifications remain an outbound capability until they have an independent domain lifecycle.

## Out of scope

- Implementing Vehicle, Service Catalog, Estimate, Purchase Order or notification delivery.
- Adding authentication/authorization or CI infrastructure.
- Renaming the existing Part API and aggregate to Stock Item.

## Acceptance criteria

- [x] Exactly three Spring Modulith modules match the Miro contexts.
- [x] Existing HTTP contracts remain available and are present in OpenAPI/Postman.
- [x] Flyway owns schema creation and Hibernate validates it.
- [x] Development seeds are opt-in and idempotent.
- [x] Feature specs, test and security gates are documented and usable.

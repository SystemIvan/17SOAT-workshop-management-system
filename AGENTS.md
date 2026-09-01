# AGENTS.md — Workshop Management System

Project instructions for humans and coding agents working on the FIAP Workshop Management System MVP.

## Project context

- Backend REST API for an automotive workshop.
- Java 21, Spring Boot 4.1, Spring Modulith 2.1, Spring Data JPA and MySQL.
- Modular monolith organized around the bounded contexts defined in the project Miro board.
- Package root: `br.com.fiap.workshop_management_system`.
- Use `./mvnw`; do not depend on a globally installed Maven.

## Bounded contexts

Only direct packages below the application root are Spring Modulith modules:

- `registration`: Customer, Vehicle and Service Catalog.
- `servicelifecycle`: Service Order, Estimate and supporting Technician capabilities.
- `stockprocurement`: Stock and Purchase Order.
- `identity`: user accounts, credentials and the role-to-domain-ID mapping used for JWT authentication
  (AD-016). Customer and Technician stay pure domain references; this module never imports their types.

Some aggregates are intentionally represented only by documented package placeholders. Do not invent CRUD, entities,
repositories or tables for a placeholder without an approved feature specification.

`Notifications` is not currently a bounded context. Notification delivery is an outbound effect owned by the consuming
module. Introduce a consumer-owned port only when a real use case needs delivery. See
`docs/adr/ADR-004-notifications-boundary.md`.

## Architecture rules

- Keep the domain model free from Spring, JPA and transport concerns.
- Put aggregate roots, entities and value objects in `domain/model`; repository contracts belong to `domain/repository`.
- Application use cases orchestrate domain behavior and define transaction boundaries.
- Infrastructure contains persistence adapters, HTTP controllers and external integrations.
- Never expose domain or JPA entities as HTTP contracts. Use request/response records and Bean Validation at the
  boundary.
- Prefer constructor injection. Do not use field injection.
- Change aggregate state through intention-revealing business methods, not public setters.
- Use `BigDecimal` for monetary values and validate invariants when value objects are created.
- Do not import another module's internal packages. Communicate through public APIs, stable IDs, domain events or a
  consumer-owned port and adapter.
- Keep cross-module dependencies acyclic and run the Modulith verification for every structural change.
- Keep truly cross-cutting HTTP concerns in the application root; do not create a generic shared domain module.

## Feature specification workflow

Every non-trivial feature starts in `docs/features/<feature-slug>/`, copied from `docs/features/_template/`.

Write SDD documents (`functional-spec.md`, `technical-spec.md` and `implementation-plan.md`) in Brazilian Portuguese.
Keep code identifiers, file names, endpoint paths and the allowed status values in their canonical form when translating
them would reduce technical precision.

An agent must never infer approval or approve a specification on behalf of a human reviewer. Record the approver and
approval date in the document. Follow these gates in order:

1. Write only `functional-spec.md`, with the problem, behaviors, rules, exclusions and acceptance criteria.
2. Obtain explicit human approval of the functional spec and mark it `Approved` before creating `technical-spec.md`.
3. Write `technical-spec.md` with context impact, contracts, persistence, failures, security and test strategy.
4. Obtain explicit human approval of the technical spec and mark it `Approved` before creating
   `implementation-plan.md`.
5. Write `implementation-plan.md` with ordered checkpoints and verification evidence.
6. Implement checkpoint by checkpoint and keep the plan status current.
7. Run tests, perform the security review and update API/database documentation.
8. Mark the feature `Implemented` only after all completion gates pass.

If an approved upstream spec changes materially, return it to `Draft`. Any downstream spec or plan becomes stale and
must not be used until the upstream document is explicitly reapproved and the downstream document is reviewed again.

Small typo-only or documentation-only changes may use a single concise plan, but must still satisfy relevant checks.

## Persistent data and seeds

Every feature that creates or changes persistent data must classify it in the technical spec as one of:

- no seed required;
- mandatory reference data;
- local demonstration data;
- test fixture.

Rules:

- Flyway is the only schema and mandatory reference-data mechanism.
- Production uses `spring.jpa.hibernate.ddl-auto=validate`; never restore `update` or `create`.
- Versioned migrations live in `src/main/resources/db/migration` and use the Flyway-compatible format
  `VyyyyMMddHHmmss__nome_da_migration_em_lowercase.sql`, with a UTC timestamp and a lowercase `snake_case` name.
- A migration is immutable after it has become part of an operational baseline or has been applied to a shared
  environment. Before the first operational baseline, an approved technical spec may explicitly authorize replacing a
  scaffolding migration when every affected environment can be rebuilt from an empty database.
- `scripts/` is reserved for container bootstrap and operational scripts, not application schema evolution.
- Business examples such as customers and stock items must never be inserted automatically in production.
- Demonstration seeders require both the `dev` profile and `app.seed.enabled=true`.
- Seeders are module-owned, idempotent, contain no real personal data or secrets, and create valid domain objects.
- Automated tests use dedicated fixtures/builders and must not depend on development seeds.

## HTTP contracts and documentation

- Preserve backward compatibility unless the functional spec explicitly approves a breaking change.
- Any endpoint, request, response, validation or status-code change must update in the same feature:
  - Springdoc/OpenAPI annotations and the generated contract expectations;
  - `docs/api/postman/workshop-management-system.postman_collection.json`;
  - affected functional and technical specs.
- Whenever a feature changes the Postman collection, update `README.md` in the same feature with executable manual-test
  instructions for the corresponding flow, including prerequisites, request order, variables and expected outcomes.
- OpenAPI generated by the application is the source of truth. Do not maintain a duplicate handwritten YAML.
- Swagger UI is available at `/swagger-ui.html`; JSON OpenAPI is available at `/v3/api-docs`.
- Map business and validation errors through the global exception handler using stable error codes.

## Testing and quality gates

- Domain rules require fast unit tests.
- Use-case orchestration requires unit or module integration tests.
- HTTP contract changes require MockMvc integration tests.
- Persistence changes require migration/startup coverage against the test database.
- Module interactions should use `@ApplicationModuleTest` where isolation or events matter.
- Cover business-rule failures and end-to-end use-case flows when they are changed.
- `ModuleStructureTest` must remain green.
- Run `make test` while developing and `make verify` before completion.
- `make coverage` generates the JaCoCo report. The project target is at least 80%; do not reduce coverage of changed
  code.
- Do not disable, ignore or weaken tests merely to make a build pass.

## Security review

Every feature plan contains a security checkpoint. Review, as applicable:

- input validation and unsafe mass assignment;
- authentication and authorization boundaries;
- exposure of customer, vehicle or operational data;
- secrets, credentials and sensitive log content;
- SQL, persistence and migration safety;
- error responses and information disclosure;
- new dependencies and known vulnerabilities;
- abuse cases for new or changed endpoints.

Record findings and mitigations in the implementation plan. If an item is not applicable, record `N/A` with a short
reason. A feature cannot be marked implemented while a critical/high finding remains unresolved.

## Code style

- Four-space indentation, maximum 120 characters per line and no wildcard imports.
- Use English names and comments; comments should explain non-obvious decisions rather than restate code.
- Classes use PascalCase, methods/variables camelCase and constants UPPER_SNAKE_CASE.
- Prefer small cohesive methods, explicit names and early validation over nested conditionals.
- Avoid speculative abstractions, unused ports, generic utility packages and premature bounded contexts.

## Transactions and error handling

- Put `@Transactional` on public application use-case methods that change aggregate state; use `readOnly = true` for
  query-only use cases when applicable.
- Translate expected business, validation and not-found failures in `GlobalExceptionHandler` using `ErrorResponse` and a
  stable error code. Do not expose stack traces, SQL details or internal exception types in HTTP responses.
- Let unexpected technical failures propagate to the platform's standard error handling and log them without secrets or
  personal data.

## Git and pull requests

- Create branches as `{type}/{bounded-context}-{feature}`; use `platform` as the scope for cross-cutting work.
- Use Conventional Commits: `<type>(<scope>): <subject>`. Allowed types are `feat`, `fix`, `refactor`, `test`, `docs`
  and `chore`.
- Keep commits focused on a cohesive, reviewable step. Do not mix formatting, unrelated cleanup and behavior changes.
- Every pull request needs at least one team review and must describe the context, behavior, risks and verification.
- Use the following checklist in the PR description, removing items that are clearly not applicable and explaining why:

```markdown
## Resumo

## Tipo de mudança
- [ ] Feature
- [ ] Correção
- [ ] Refatoração
- [ ] Documentação / manutenção

## Validação
- [ ] Testes relevantes criados ou atualizados
- [ ] `make verify` executado
- [ ] Cobertura revisada (meta do projeto: 80%)
- [ ] Fronteiras Modulith verificadas, quando aplicável

## Contratos e dados
- [ ] OpenAPI e Postman atualizados, quando aplicável
- [ ] Migração Flyway e classificação de seed incluídas, quando aplicável
- [ ] Compatibilidade de contrato avaliada

## Segurança e qualidade
- [ ] Revisão de segurança registrada
- [ ] Sem segredos, dados pessoais reais ou logs sensíveis
- [ ] Convenções de código e arquitetura respeitadas
```

## Completion checklist

- Approved specs and completed checkpoints.
- `make verify` passes without inappropriate skipped tests.
- Modulith boundaries remain valid.
- Security review is recorded and actionable findings are resolved.
- Flyway migration and seed classification are present when persistence changed.
- OpenAPI and Postman are updated when HTTP contracts changed.
- README manual-test instructions are updated whenever the Postman collection changed.
- README/architecture documentation reflects structural changes.
- Commit messages follow Conventional Commits: `<type>(<scope>): <subject>`.

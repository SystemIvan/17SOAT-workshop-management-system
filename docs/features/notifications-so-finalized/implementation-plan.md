# Implementation Plan: Service Order Finalized Notification (Customer)

| Field | Value |
|---|---|
| Feature | `notifications-so-finalized` |
| Status | Implemented |
| Owner | `Leandro Nascimento` |
| Updated at | `2026-08-13` |
| Technical spec | `./technical-spec.md` |

## Checkpoints

- [x] **Architecture and contracts.** Added `CustomerNotificationPort` in
      `servicelifecycle.serviceorder.application.port` and `SimulatedEmailCustomerNotificationAdapter` in
      `servicelifecycle.serviceorder.infrastructure.notification`; wired `FinalizeServiceOrderUseCase` to the port.
      `ModuleStructureTest` initially failed with `Module 'servicelifecycle' depends on non-exposed type ...
      CustomerRepository/Customer/ContactInfo within module 'registration'`, exactly as anticipated in
      `technical-spec.md`. Fixed with the documented fallback: added `@NamedInterface("customer-repository")` on
      `registration.customer.domain.repository` and `@NamedInterface("customer-model")` on
      `registration.customer.domain.model` (new `package-info.java` files) — the only two sub-packages of
      Registrations now exposed to other modules. `ModuleStructureTest` is green after this change.
- [x] **Persistence/migrations and seed classification.** N/A — no schema change; no new tables/columns; no seed
      required (confirmed unchanged from `technical-spec.md`).
- [x] **Domain and application behavior.** `FinalizeServiceOrderUseCase.execute()` calls
      `CustomerNotificationPort.notifyServiceOrderFinalized(serviceOrder.id(), serviceOrder.customerId())` after
      `repository.save(...)`, inside a `try/catch (RuntimeException)` that logs a `WARN` and never rethrows —
      verified by test (see below) that the transaction still commits and the response is still returned when the
      port throws.
- [x] **Automated tests and `make verify` pass.**
      - `FinalizeServiceOrderUseCaseTest` (3 tests): notifies on success; skips notification when finalize
        preconditions fail; finalize still succeeds when the port throws.
      - `SimulatedEmailCustomerNotificationAdapterTest` (2 tests): logs an `INFO` simulated e-mail without raw
        PII when the customer is found; logs a `WARN` and never throws when not found.
      - `FinalizeServiceOrderFlowApplicationModuleTest` (1 test, `@ApplicationModuleTest(DIRECT_DEPENDENCIES)`):
        boots the real `servicelifecycle` + `registration` Spring context (H2 + Flyway, no mocks) and proves the
        cross-module wiring works end-to-end.
      - Full `./mvnw verify` (equivalent to `make verify`) passed: 13 test classes, 0 failures, 0 errors.
- [x] **Security review completed.** See findings below.
- [x] **OpenAPI, Postman and project documentation.** N/A for OpenAPI/Postman confirmed — no HTTP contract change.
      Updated `docs/PROJECT-STRUCTURE.md` to document the new Notifications outbound-port pattern and the two new
      `@NamedInterface` exposures on Registrations, since this is the first cross-module dependency in the codebase.

## Verification evidence

```text
$ ./mvnw -q test -Dtest=FinalizeServiceOrderUseCaseTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

$ ./mvnw -q test -Dtest=SimulatedEmailCustomerNotificationAdapterTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

$ ./mvnw -q test -Dtest=ModuleStructureTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   (green only after the two @NamedInterface additions)

$ ./mvnw -q test -Dtest=FinalizeServiceOrderFlowApplicationModuleTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

$ ./mvnw -q verify
Exit code: 0
13 surefire test-class reports, all "Failures: 0, Errors: 0, Skipped: 0"
(ModuleStructureTest, WorkshopManagementSystemApplicationTests, OpenApiContractTest, CustomerTest,
CustomerDevelopmentDataSeederTest, FinalizeServiceOrderFlowApplicationModuleTest,
FinalizeServiceOrderUseCaseTest, ServiceExecutionTest, ServiceOrderTest,
SimulatedEmailCustomerNotificationAdapterTest, TechnicianTest, PartTest, StockDevelopmentDataSeederTest)
```

JaCoCo (`target/site/jacoco/jacoco.csv`) for the new/changed classes:

- `FinalizeServiceOrderUseCase`: 100% instruction, line, branch and method coverage.
- `SimulatedEmailCustomerNotificationAdapter`: 95.6% instruction coverage, 94.4% line coverage; the one uncovered
  branch is the defensive `atIndex <= 0` guard in `maskEmail`, unreachable in practice because `ContactInfo`'s
  constructor already rejects any e-mail without an `@`.

Project-wide coverage (`target/site/jacoco/jacoco.csv`, all classes) is 63.1% instruction / 70.8% line — below the
80% project target, but this is a **pre-existing** condition unrelated to this feature (large parts of the
codebase are still placeholder packages or untested controllers/mappers predating this story). Per `AGENTS.md`
("do not reduce coverage of changed code"), the relevant check is that this feature's own code is well covered,
which it is; closing the project-wide gap is out of scope for this story.

## Security review

| Item | Finding |
|---|---|
| Input validation / mass assignment | N/A — no new endpoint, request or DTO. |
| AuthN/authZ boundaries | Unchanged — `FinalizeServiceOrderUseCase`'s existing access control is untouched. |
| Exposure of customer/vehicle/operational data | The simulated-e-mail log line does **not** contain the raw customer e-mail or name — only `customerId`, `serviceOrderId` and a masked e-mail (`j***@e***`), verified by `SimulatedEmailCustomerNotificationAdapterTest`. |
| Secrets, credentials, sensitive logs | None introduced. |
| SQL/persistence/migration safety | N/A — no schema change, no new queries beyond the existing `CustomerRepository.findById`. |
| Error responses / info disclosure | N/A — notification failures are caught and logged server-side; nothing is surfaced in the HTTP response. |
| New dependencies / known vulnerabilities | None — no new third-party dependency added. |
| Abuse cases for new/changed endpoints | N/A — no new or changed endpoint. |
| Module boundary widening | New, first-ever `servicelifecycle → registration` dependency, deliberately narrowed to two `@NamedInterface` packages (`customer-repository`, `customer-model`) rather than opening the whole `registration` module; `CustomerRepository` also exposes `save`/`findAll`, which `servicelifecycle` does not use but is technically reachable — accepted as a minor, documented tradeoff rather than splitting the repository into read/write ports for a single story (would be speculative for this scope). |

No critical/high findings. Nothing blocks marking this feature `Implemented`.

## Rollback or recovery

`N/A` — the change is purely additive (new port/adapter/use-case wiring plus two `@NamedInterface`
`package-info.java` files), introduces no persisted state, no migration and no external side effect beyond a log
line. Recovery from a bad deploy is a plain code revert.

# Implementation Plan: New Service Order Notification (Technician)

| Field | Value |
|---|---|
| Feature | `notifications-technician-new-so` |
| Status | Implemented |
| Owner | `Leandro Nascimento` |
| Updated at | `2026-08-13` |
| Technical spec | `./technical-spec.md` |

## Checkpoints

- [x] **Architecture and contracts.** Added `TechnicianNotificationPort` in
      `servicelifecycle.serviceorder.application.port` and `LoggedTechnicianNotificationAdapter` in
      `servicelifecycle.serviceorder.infrastructure.notification`; wired `CreateServiceOrderUseCase` to
      `TechnicianRepository` and the new port. Re-ran `ModuleStructureTest`: green on the first try, confirming
      the `technical-spec.md` prediction — `serviceorder → technician` is an intra-module dependency (both are
      plain packages under the single `servicelifecycle` `@ApplicationModule`), so no `@NamedInterface` was
      needed, unlike story #7's `registration.customer` crossing.
- [x] **Persistence/migrations and seed classification.** N/A — no schema change; no seed required (confirmed
      unchanged from `technical-spec.md`).
- [x] **Domain and application behavior.** `CreateServiceOrderUseCase.execute()` fans out to every Technician
      with `status != INACTIVE` after `repository.save(...)`, one `TechnicianNotificationPort` call per
      Technician, each wrapped in its own `try/catch (RuntimeException)` — verified by test that one Technician's
      delivery failure doesn't stop the loop and doesn't fail/roll back Service Order creation.
- [x] **Automated tests and `make verify` pass.**
      - `CreateServiceOrderUseCaseTest` (3 tests): notifies every non-`INACTIVE` Technician and skips the
        `INACTIVE` one; creates the Service Order successfully with zero active Technicians and sends nothing;
        a failure notifying one Technician doesn't prevent notifying the other.
      - `LoggedTechnicianNotificationAdapterTest` (1 test): logs an `INFO` line containing both
        `serviceOrderId` and `technicianId`.
      - Full `./mvnw clean verify` passed: 12 test classes, 0 failures, 0 errors. (Note: the first `verify` run
        was done without `clean` and picked up stale compiled test classes left over from an earlier, unrelated
        session on this machine — re-ran with `clean` to get a trustworthy result; see evidence below.)
- [x] **Security review completed.** See findings below.
- [x] **OpenAPI, Postman and project documentation.** N/A for OpenAPI/Postman confirmed — no HTTP contract
      change. No `docs/PROJECT-STRUCTURE.md` update needed — confirmed no new module-level dependency was
      introduced (unlike story #7).

## Verification evidence

```text
$ ./mvnw -q test -Dtest=CreateServiceOrderUseCaseTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

$ ./mvnw -q test -Dtest=LoggedTechnicianNotificationAdapterTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

$ ./mvnw -q test -Dtest=ModuleStructureTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   (green on first try, no @NamedInterface needed)

$ ./mvnw -q clean verify
Exit code: 0
12 surefire test-class reports, all "Failures: 0, Errors: 0, Skipped: 0"
(ModuleStructureTest, WorkshopManagementSystemApplicationTests, OpenApiContractTest, CustomerTest,
CustomerDevelopmentDataSeederTest, CreateServiceOrderUseCaseTest, ServiceExecutionTest, ServiceOrderTest,
LoggedTechnicianNotificationAdapterTest, TechnicianTest, PartTest, StockDevelopmentDataSeederTest)
```

JaCoCo (`target/site/jacoco/jacoco.csv`) for the new/changed classes:

- `CreateServiceOrderUseCase`: 97% instruction coverage, 100% line coverage; the one uncovered branch is the
  pre-existing `request.priority() != null ? ... : Priority.NORMAL` ternary's "priority provided" path (test
  fixtures use `null`), unrelated to the notification logic added by this feature.
- `LoggedTechnicianNotificationAdapter`: 100% instruction, line and method coverage.

Project-wide coverage on this branch (`clean` build, no story #7 code present here) is 48.1% instruction /
52.3% line — below the 80% target, consistent with `AGENTS.md`'s "do not reduce coverage of changed code": this
feature's own code is at 97–100%, and the project-wide gap is pre-existing/out of this story's scope (same
reasoning recorded in story #7's implementation plan).

## Security review

| Item | Finding |
|---|---|
| Input validation / mass assignment | N/A — no new endpoint, request or DTO. |
| AuthN/authZ boundaries | Unchanged — `CreateServiceOrderUseCase`'s existing access control is untouched. |
| Exposure of customer/vehicle/operational data | The log line contains only `serviceOrderId` and `technicianId` — no contact or personal data exists on `Technician` to expose in the first place. |
| Secrets, credentials, sensitive logs | None introduced. |
| SQL/persistence/migration safety | N/A — no schema change; only additional read via `TechnicianRepository.findAll()`. |
| Error responses / info disclosure | N/A — notification failures are caught and logged server-side per Technician; nothing is surfaced in the HTTP response. |
| New dependencies / known vulnerabilities | None — no new third-party dependency added. |
| Abuse cases for new/changed endpoints | N/A — no new or changed endpoint. |
| Module boundary | No boundary widened — `TechnicianRepository` was already part of the same `servicelifecycle` module as `CreateServiceOrderUseCase`; `ModuleStructureTest` confirms no violation. |

No critical/high findings. Nothing blocks marking this feature `Implemented`.

## Rollback or recovery

`N/A` — the change is purely additive (new port/adapter/use-case wiring), introduces no persisted state, no
migration and no external side effect beyond one log line per notified Technician. Recovery from a bad deploy is
a plain code revert.

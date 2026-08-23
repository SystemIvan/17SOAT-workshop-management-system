# Technical Specification: New Service Order Notification (Technician)

| Field | Value |
|---|---|
| Feature | `notifications-technician-new-so` |
| Status | Approved |
| Owner | `Leandro Nascimento` |
| Updated at | `2026-08-13` |
| Functional spec | `./functional-spec.md` |

## Context and design

**Owning module:** `servicelifecycle` (`servicelifecycle.serviceorder`), same as story #7. Per
`../../../adr/ADR-004-notifications-boundary.md`, `servicelifecycle` defines the outbound port in its **application
layer** and the adapter in its **infrastructure layer**.

**No cross-module dependency this time.** Unlike story #7 (which had to reach into `registration.customer`),
`Technician` lives in `servicelifecycle.technician` — a plain nested package under the same single
`@ApplicationModule` as `serviceorder` (`servicelifecycle/package-info.java` is the only `@ApplicationModule`
annotation in that module tree; `technician` and `serviceorder` are both just internal packages of it). Spring
Modulith's boundary check (`ApplicationModules.verify()`) only restricts *cross-module* access to another
module's non-exposed types — it does not restrict one internal package of a module from using another internal
package of the *same* module. So `CreateServiceOrderUseCase` can inject
`servicelifecycle.technician.domain.repository.TechnicianRepository` directly, with no `@NamedInterface` needed
and no risk of tripping `ModuleStructureTest`. This will be verified empirically at the first checkpoint anyway
(see "Test strategy"), the same way story #7's assumption was verified and, in that case, turned out to need a
fix — this one is expected to just pass.

**Fan-out, not a single broadcast record.** Per the functional spec's Decision record A and business rules, one
notification is generated per notified Technician (`status != INACTIVE` at creation time), not one shared
"broadcast" notification. `CreateServiceOrderUseCase` therefore loops over the active Technicians and calls the
port once per recipient, isolating each call's failure from the others (same try/catch-per-call pattern as story
#7, applied per iteration here).

**Channel:** per Decision record B, the channel needs no contact information — recipients are addressed only by
`technicianId`. Consistent with story #7's channel precedent (a channel that avoids inventing infrastructure),
this uses the same **structured log line** approach, just without any e-mail/masking concern since there is no
contact data involved at all.

## Interfaces and data flow

**Port** — new interface, `servicelifecycle.serviceorder.application.port.TechnicianNotificationPort`:

```java
public interface TechnicianNotificationPort {
    void notifyServiceOrderCreated(UUID serviceOrderId, UUID technicianId);
}
```

One recipient per call (mirrors `CustomerNotificationPort` from story #7) — this is what lets the use case retry
the next Technician independently after one call fails.

**Adapter** — new class, `servicelifecycle.serviceorder.infrastructure.notification.LoggedTechnicianNotificationAdapter`,
implementing `TechnicianNotificationPort`:

- Logs a single `INFO` line per call: `serviceOrderId` and `technicianId` only — no lookup, no other dependency,
  since nothing beyond these two IDs is needed for this channel.
- Registered as a Spring bean (`@Component`), no constructor dependencies.

**Use case change** — `CreateServiceOrderUseCase` gets two new constructor dependencies,
`servicelifecycle.technician.domain.repository.TechnicianRepository` and `TechnicianNotificationPort`, and
fans out to every non-`INACTIVE` Technician after `repository.save(...)`:

```java
@Transactional
public ServiceOrderResponse execute(CreateServiceOrderRequest request) {
    VehicleSnapshot vehicleSnapshot = ServiceOrderMapper.toVehicleSnapshot(request.vehicleSnapshot());
    Priority priority = request.priority() != null ? request.priority() : Priority.NORMAL;
    ServiceOrder serviceOrder = ServiceOrder.create(request.customerId(), request.vehicleId(), vehicleSnapshot, priority);
    repository.save(serviceOrder);
    notifyActiveTechnicians(serviceOrder);
    return ServiceOrderMapper.toResponse(serviceOrder);
}

private void notifyActiveTechnicians(ServiceOrder serviceOrder) {
    technicianRepository.findAll().stream()
            .filter(technician -> technician.status() != TechnicianStatus.INACTIVE)
            .forEach(technician -> notifyTechnician(serviceOrder, technician));
}

private void notifyTechnician(ServiceOrder serviceOrder, Technician technician) {
    try {
        technicianNotificationPort.notifyServiceOrderCreated(serviceOrder.id(), technician.id());
    } catch (RuntimeException ex) {
        log.warn("Failed to notify technician {} about created service order {}",
                technician.id(), serviceOrder.id(), ex);
    }
}
```

**Failure mapping:** the per-call `try/catch` is what guarantees one Technician's delivery failure never stops
the loop for the remaining Technicians (functional spec acceptance criterion) and never fails or rolls back
`CreateServiceOrderUseCase`'s `@Transactional` method — same reasoning as story #7's `notifyCustomer`.

**HTTP contract:** unchanged. `CreateServiceOrderUseCase`'s existing endpoint, request and response shapes are
not modified — no OpenAPI/Postman updates required.

**`TechnicianRepository.findAll()` cost:** loads every Technician on every Service Order creation. Acceptable
for the current MVP scale (no pagination or indexing concerns raised elsewhere in the codebase for this
repository); flagged here rather than silently accepted in case Technician volume becomes large enough to
matter later — not a blocker for this story.

## Persistence and bootstrap data

None. No new tables, columns or persisted fields — classification: **no seed required**. Same as story #7, the
notification is a stateless, best-effort side effect (one log line per recipient); no delivery history is
stored, per the functional spec's explicit exclusion.

## Security and operations

- **Authorization:** unchanged — no new endpoint, no change to who can call `CreateServiceOrderUseCase`.
- **Sensitive data in logs:** none to protect — the log line only contains `serviceOrderId` and `technicianId`
  (both opaque identifiers already used elsewhere in application logs). Unlike story #7, there is no contact
  data to mask or omit, since `Technician` doesn't expose any.
- **New dependency:** `TechnicianRepository` (read-only, `findAll()`) is now reachable from
  `CreateServiceOrderUseCase` — no write access, no new attack surface, and no module-boundary widening (see
  "Context and design").
- **Rollout/recovery:** purely additive and backward compatible; no migration, no feature flag needed. Rollback
  is a plain revert of the adapter/use-case change — `N/A` beyond normal git revert.

## Test strategy

- **Application (use case), `CreateServiceOrderUseCaseTest` — new, none exists today:**
  - Happy path: with a mix of `AVAILABLE`, `BUSY` and `INACTIVE` Technicians, verify
    `TechnicianNotificationPort.notifyServiceOrderCreated(serviceOrderId, technicianId)` is called exactly once
    for each non-`INACTIVE` Technician and never for the `INACTIVE` one.
  - Zero active Technicians: `TechnicianRepository.findAll()` returns only `INACTIVE` Technicians (or an empty
    list); assert the Service Order is still created and returned normally, and the port is never called.
  - Partial notification failure: mock the port to throw for one Technician and succeed for another; assert
    both calls are still attempted (the loop doesn't stop early) and `execute(...)` still returns normally.
  - Mocking via Mockito, matching the convention established by `FinalizeServiceOrderUseCaseTest`.
- **Infrastructure (adapter), `LoggedTechnicianNotificationAdapterTest` — new:**
  - Verify a single `INFO` log line is emitted per call (via a Logback `ListAppender`, same technique as story
    #7's adapter test) containing `serviceOrderId` and `technicianId`.
- **Module boundary:** no cross-module dependency is introduced (see "Context and design"), so unlike story #7
  this does not need a new `@ApplicationModuleTest` to prove module wiring — `ModuleStructureTest` staying green
  is the only boundary check required, and it isn't expected to need any `@NamedInterface` change. Re-run it as
  part of checkpoint 1 to confirm this assumption in practice rather than only on paper.
- **HTTP:** no contract change, so no new/updated MockMvc tests are required.
- **Coverage:** maintain the project's 80% target on all changed/new code (`make coverage`).

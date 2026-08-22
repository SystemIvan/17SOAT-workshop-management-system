# Functional Specification: New Service Order Notification (Technician)

| Field | Value |
|---|---|
| Feature | `notifications-technician-new-so` |
| Status | Approved |
| Owner | `Leandro Nascimento` |
| Updated at | `2026-08-13` |
| References | Epic 5 plan item #1 (`docs/EPIC-5-notifications-plan-v2.md`, sections 2 and 3; no official RF), `docs/adr/ADR-003-notifications-boundary.md`, `docs/features/notifications-so-finalized/` (pattern reference) |

## Problem and outcome

Today, when a new Service Order is created (`CreateServiceOrderUseCase`), no Technician receives any indication
that new work has entered the queue. Technicians (or whoever dispatches work to them) currently have no
automatic signal and must actively check for new Service Orders. The outcome: the moment a Service Order is
successfully created, every active Technician is automatically notified that a new Service Order exists in the
queue.

### Decision record A — recipient scope (resolved 2026-08-13)

Investigated: `CreateServiceOrderUseCase`, `CreateServiceOrderRequest`, `ServiceOrder.create(...)`,
`DiagnosisItem`, `Technician`, `TechnicianRepository`, `AssignTechnicianUseCase`.

Findings:

- `CreateServiceOrderRequest` carries only `customerId`, `vehicleId`, `vehicleSnapshot` and `priority`. At the
  moment `CreateServiceOrderUseCase.execute()` runs, the created `ServiceOrder` has **zero `ServiceExecution`s
  and zero `DiagnosisItem`s** — those are only added later by the separate `PerformDiagnosisUseCase`. There is no
  service-type or specialty information available at creation time.
- `DiagnosisItem` (`catalogServiceId`, `name`, `price`, `stockRequirements`) has no `Specialty` field either —
  even after diagnosis runs, nothing in the current domain model maps a requested service to a required
  `Specialty`.
- `Technician` has `specialties: Set<Specialty>` (`MECHANICAL`, `ELECTRICAL`, `BODYWORK`, `PAINTING`,
  `DIAGNOSTICS`) and `status: AVAILABLE | BUSY | INACTIVE`. `TechnicianRepository.findAll()` can enumerate every
  Technician, so filtering by status is possible today without new domain data.
- `AssignTechnicianUseCase` (RF19) already exists and confirms one specific `technicianId` for one specific
  `ServiceExecution` (`serviceOrder.confirmTechnicianAssignment(serviceExecutionId, technicianId)`) — but that
  only becomes possible after diagnosis has produced executions, and it is a distinct, later step, not part of
  `CreateServiceOrderUseCase`.

Three possible readings of "notify Technician on SO creation" were identified:

1. **Broadcast to every active Technician** — buildable today with no new domain data.
2. **Notify only Technicians with a matching specialty** — not feasible with the current domain model (no
   service-type/specialty information exists at creation time; would require expanding scope beyond this
   notification).
3. **Trigger at Technician assignment instead of Service Order creation** — technically simpler (an exact
   `technicianId` already exists there), but is really a different, later notification about a different event
   ("you were assigned"), not item #1 as scoped by the epic plan.

**Decision: Option 1.** Every Technician with `status != INACTIVE` (i.e. both `AVAILABLE` and `BUSY`, excluding
only deactivated Technicians) is notified when a Service Order is created. This matches the Event Storming
board, which places this notification before any Technician assignment, and requires no new domain data. If a
narrower `AVAILABLE`-only scope is actually intended, that should be corrected during spec review — this
document currently encodes "active" as "not inactive," not "currently free."

### Decision record B — Technician contact information (resolved 2026-08-13)

`Technician` (`servicelifecycle.technician.domain.model.Technician`) currently exposes only `id`, `name`,
`specialties` and `status` — **no e-mail, phone or any other deliverable contact field**, unlike `Customer`
(which has `ContactInfo`). This is a separate gap from the delivery-channel choice made for story #7 (simulated
e-mail via log), since that choice assumed an e-mail address existed to mask and log — Technician has none.

**Decision: pick a delivery channel that does not require contact information.** Recipients are identified only
by `technicianId` (e.g. a structured log entry per notified Technician, keyed by `technicianId`, following the
same "no PII, no raw contact data" principle as story #7's channel — trivially satisfied here since there is no
contact data to begin with). The `Technician` aggregate is **not** changed by this feature; adding contact
fields to `Technician` is explicitly out of scope. The exact channel/Adapter shape is deferred to
`technical-spec.md`.

## Actors and scenarios

- **Technician(s)** — every Technician with `status != INACTIVE` at the moment the Service Order is created
  (Decision record A).
- **System (`servicelifecycle`)** — triggers the notification as a side effect of successfully creating a
  Service Order.

**Scenario 1 — Happy path.** A Service Order is successfully created via `CreateServiceOrderUseCase`. Every
Technician with `status != INACTIVE` at that moment is notified that a new Service Order exists in the queue.

**Scenario 2 — Creation fails validation.** If `CreateServiceOrderUseCase.execute()` fails (currently only
possible via Bean Validation on `CreateServiceOrderRequest` — unlike `FinalizeServiceOrderUseCase`, there is no
additional domain-level rejection path today), no `ServiceOrder` is created and no notification is attempted.

**Scenario 3 — No active Technicians exist.** If there are zero Technicians with `status != INACTIVE` at
creation time, the Service Order is still created successfully; simply no one is notified (not treated as a
failure).

**Scenario 4 — Notification cannot be delivered to one or more Technicians.** Same pattern established in
story #7 (`docs/features/notifications-so-finalized/`): Service Order creation and persistence must succeed
regardless of whether the notification is successfully delivered to any/all recipients.

## Business rules

- The notification is triggered only after a Service Order is successfully created and persisted.
- Recipients are every Technician with `status != INACTIVE`, evaluated at the moment of creation (Decision
  record A). A Technician created or reactivated after this point is not retroactively notified.
- One notification is generated per notified Technician (a fan-out from a single creation event), each
  identifying only the Technician receiving it — not a single shared broadcast record.
- The notification identifies the created Service Order by `id` and includes the vehicle snapshot/priority
  already known at creation time (no additional data fetch required).
- The delivery channel does not require Technician contact information (Decision record B); the concrete
  channel/format is decided in `technical-spec.md`.
- A failure to deliver the notification to one Technician must not prevent delivery attempts to the others, and
  must never fail, roll back or block `CreateServiceOrderUseCase`.
- No notification history or delivery state is persisted by this feature.

## Out of scope

- Specialty-based recipient matching — infeasible with the current domain model (Decision record A, option 2);
  would require a separate spec if the domain model is extended to carry service-type/specialty at intake.
- A "Technician assigned" notification triggered from `AssignTechnicianUseCase` (Decision record A, option 3) —
  a distinct, not-yet-scoped notification, not this one.
- Adding contact info to the `Technician` aggregate (Decision record B).
- Choosing or implementing the delivery channel/Adapter itself.
- Retry policies, delivery guarantees, read receipts.
- Any notification history/audit persistence (no new table).
- Notifying anyone other than Technicians (this is not the Customer-facing story #7).
- The other 5 remaining notifications from Epic 5.

## Acceptance criteria

- [ ] Creating a Service Order successfully triggers one notification per Technician with `status != INACTIVE`
      at that moment.
- [ ] A Service Order creation request that fails validation triggers no notification.
- [ ] Creating a Service Order when there are zero active Technicians still succeeds, with no notification sent
      to anyone.
- [ ] Service Order creation succeeds (Service Order persisted, response returned) regardless of whether the
      notification is successfully delivered to any/all recipients.
- [ ] A delivery failure to one Technician does not prevent delivery attempts to the remaining notified
      Technicians.
- [ ] Each notification references the created Service Order's `id` and identifies exactly one recipient
      Technician.
- [ ] No new persisted state (tables/columns) is introduced by this feature.

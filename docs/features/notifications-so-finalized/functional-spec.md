# Functional Specification: Service Order Finalized Notification (Customer)

| Field | Value                                                                                                  |
|---|--------------------------------------------------------------------------------------------------------|
| Feature | `notifications-so-finalized`                                                                           |
| Status | Approved                                                                                               |
| Owner | `Leandro Nascimento`                                                                                   |
| Updated at | `2026-08-13`                                                                                           |
| References | `RF33`, `docs/EPIC-5-notifications-plan-v2.md` (item #7), `../../adr/ADR-004-notifications-boundary.md` |

## Problem and outcome

Today, when a Service Order is finalized (`FinalizeServiceOrderUseCase`, RF24) and the vehicle is marked as
delivered, the Customer who owns that Service Order receives no automatic communication that their vehicle is
ready. They only find out through the physical pickup itself or by manually checking the Service Order status.

The outcome: the moment a Service Order is successfully finalized (status transitions to `DELIVERED`), the
owning Customer is automatically notified that their vehicle is ready for pickup, closing the loop for RF33
("Notificar o Customer quando a Service Order for finalizada").

## Actors and scenarios

- **Customer** — the owner of the finalized Service Order; recipient of the notification.
- **System (`servicelifecycle`)** — triggers the notification as a side effect of successfully finalizing a
  Service Order.

**Scenario 1 — Happy path.** An employee finalizes a Service Order whose status is `COMPLETED`, marking
`vehicleDelivered = true`. The Service Order transitions to `DELIVERED`. The Customer identified by the
Service Order's `customerId` is notified that their vehicle is ready for pickup.

**Scenario 2 — Finalize preconditions not met.** An attempt to finalize a Service Order that is not
`COMPLETED`, or with `vehicleDelivered = false`, fails with the existing business rule (the use case rejects
it before any state change). No notification is attempted.

**Scenario 3 — Notification cannot be delivered.** The finalize operation and status transition succeed and
are persisted even if the notification itself cannot be delivered — the Customer being unreachable or
un-notifiable never rolls back or blocks the finalize use case.

## Business rules

- The notification is triggered only after a Service Order finalize succeeds (state transitions to
  `DELIVERED` and the new state is persisted).
- Exactly one notification is triggered per successful finalize call.
- The notification identifies the finalized Service Order and is targeted at the Customer identified by the
  Service Order's `customerId`.
- The delivery channel/format is not decided by this spec (see Out of scope): the business behavior only
  requires that the responsible module attempts to inform the Customer, not a specific medium.
- A failure to deliver the notification must not fail, roll back or block the finalize transaction.
- No notification history or delivery state is persisted by this feature.

## Out of scope

- Choosing or implementing the delivery channel (e.g. endpoint simulating push vs. e-mail vs. log) — this is
  explicitly open per `docs/EPIC-5-notifications-plan-v2.md` (sections 6 and 9) and will be decided separately,
  before the infrastructure Adapter is implemented.
- Retry policies, delivery guarantees, or read receipts for the notification.
- Any notification history/audit persistence (no new table).
- Notifying the Technician or any actor other than the Customer.
- The other 6 notifications from Epic 5 (each tracked as its own story/spec).
- Any change to `FinalizeServiceOrderUseCase`'s existing validation rules (`COMPLETED` + `vehicleDelivered`).

## Acceptance criteria

- [ ] Finalizing a Service Order that is `COMPLETED` with `vehicleDelivered = true` triggers exactly one
      notification for the owning Customer.
- [ ] Attempting to finalize a Service Order that is not `COMPLETED`, or with `vehicleDelivered = false`, does
      not trigger any notification (existing rejection behavior is preserved unchanged).
- [ ] The Service Order finalize operation (status transition + persistence) succeeds regardless of whether the
      notification is successfully delivered.
- [ ] The notification references the finalized Service Order and identifies the Customer to notify by the
      Service Order's `customerId`.
- [ ] No new persisted state (tables/columns) is introduced by this feature beyond what already exists for
      `ServiceOrder`.

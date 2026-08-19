# Technical Specification: Service Order Finalized Notification (Customer)

| Field | Value |
|---|---|
| Feature | `notifications-so-finalized` |
| Status | Approved |
| Owner | `Leandro Nascimento` |
| Updated at | `2026-08-13` |
| Functional spec | `./functional-spec.md` |

## Context and design

**Owning module:** `servicelifecycle` (specifically `servicelifecycle.serviceorder`). Per
`docs/ADR-003-notifications-boundary.md`, Notifications is not a bounded context — `servicelifecycle` defines an
outbound port in its **application layer** for this notification and an infrastructure adapter implements the
chosen channel. No shared/generic notification module or abstraction is introduced.

**New cross-module dependency:** delivering the notification requires the Customer's e-mail, which lives in
`registration.customer` (`ContactInfo.email()` on the `Customer` aggregate) — a different Spring Modulith module.
Today there is **no existing dependency** from `servicelifecycle` to `registration` anywhere in the codebase, so
this feature introduces the first one. Per `AGENTS.md` ("Do not import another module's internal packages.
Communicate through public APIs, stable IDs, domain events or a consumer-owned port and adapter"), the design
keeps this dependency narrow and one-directional:

- `servicelifecycle.serviceorder` defines a **consumer-owned port** (`CustomerNotificationPort`) that only knows
  about `UUID` identifiers — it never references any `registration` type in its signature.
- The **adapter** implementing that port (in `servicelifecycle.serviceorder.infrastructure`) is the only place
  that depends on `registration.customer.domain.repository.CustomerRepository` (already a public interface) to
  resolve `customerId` into a name/e-mail. This keeps the module-crossing dependency confined to infrastructure,
  where wiring concerns belong, and out of `servicelifecycle`'s domain/application code.
- No new abstraction is added on the `registration` side — `CustomerRepository` already exists and is public;
  this feature only adds a new caller to it.

Because this widens the module boundary for the first time, this is called out as its own checkpoint (see
"Interfaces and data flow" below) — `ModuleStructureTest` (`ApplicationModules.verify()`) must be re-run early
during implementation to confirm the dependency is accepted as-is. If Spring Modulith flags
`registration.customer.domain.repository` as an internal package invisible to other modules, the fallback is to
annotate that package with `@org.springframework.modulith.NamedInterface` to explicitly publish `CustomerRepository`
— a minimal, targeted exposure, not a new generic API.

**Communication style:** synchronous, in-process call from the use case to the port, inside the same transaction
boundary as the finalize operation (see failure handling below) — not a domain event. The codebase has no existing
`ApplicationEventPublisher`/`@ApplicationModuleListener` usage anywhere; introducing event-driven notification
infrastructure for a single, best-effort, one-directional read is a disproportionate change for this story and is
left for a future ADR if/when Notifications needs to react to activity across many modules (per ADR-003's own
"reconsider as a bounded context" trigger).

## Interfaces and data flow

**Port** — new interface, `servicelifecycle.serviceorder.application.port.CustomerNotificationPort`:

```java
public interface CustomerNotificationPort {
    void notifyServiceOrderFinalized(UUID serviceOrderId, UUID customerId);
}
```

Deliberately takes only IDs — matches the functional spec's business rule ("targeted at the Customer identified
by the Service Order's `customerId`") and keeps the application layer decoupled from any delivery-specific
payload shape.

**Adapter** — new class, `servicelifecycle.serviceorder.infrastructure.notification.SimulatedEmailCustomerNotificationAdapter`,
implementing `CustomerNotificationPort` (channel confirmed with the epic owner: **simulated e-mail written to the
application log**, not a real SMTP send and not an HTTP endpoint):

- Looks up the `Customer` via `registration.customer.domain.repository.CustomerRepository.findById(customerId)`.
- If found: builds a simulated e-mail (subject: vehicle ready for pickup; body references `serviceOrderId`) and
  logs it at `INFO`. See "Security and operations" for what the log line may and may not contain.
- If not found: logs a `WARN` (customer reference is inconsistent) and returns normally — never throws.
- Registered as a Spring bean (`@Component`), constructor injection of `CustomerRepository`, matching the
  project's existing port/adapter wiring convention (interface with no Spring annotations, adapter is the only
  Spring-aware class — same pattern as `ServiceOrderRepository`/`ServiceOrderRepositoryImpl`).

**Use case change** — `FinalizeServiceOrderUseCase` (`servicelifecycle.serviceorder.application.usecase`) gets a
second constructor dependency, `CustomerNotificationPort`, and calls it after the existing `repository.save(...)`:

```java
@Transactional
public ServiceOrderResponse execute(UUID serviceOrderId, FinalizeServiceOrderRequest request) {
    ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
    serviceOrder.finalize(request.vehicleDelivered());
    repository.save(serviceOrder);
    notifyCustomer(serviceOrder);
    return ServiceOrderMapper.toResponse(serviceOrder);
}

private void notifyCustomer(ServiceOrder serviceOrder) {
    try {
        customerNotificationPort.notifyServiceOrderFinalized(serviceOrder.id(), serviceOrder.customerId());
    } catch (RuntimeException ex) {
        log.warn("Failed to notify customer {} about finalized service order {}",
                serviceOrder.customerId(), serviceOrder.id(), ex);
    }
}
```

**Failure mapping:** `notifyCustomer` is deliberately the only place that catches exceptions from the port. This
satisfies the functional spec's rule that a notification failure must never fail or roll back the finalize
transaction — the `@Transactional` method still commits the `ServiceOrder` state change and returns its normal
response even if the port throws. No new error codes or `GlobalExceptionHandler` entries are needed since nothing
here is surfaced to the HTTP layer as a failure.

**HTTP contract:** unchanged. `FinalizeServiceOrderUseCase`'s existing endpoint, request and response shapes are
not modified — this feature has no HTTP-visible effect, so no OpenAPI/Postman updates are required.

## Persistence and bootstrap data

None. No new tables, columns or persisted fields are introduced — classification: **no seed required**. The
notification is a stateless, best-effort side effect (a log line); no delivery history is stored, per the
functional spec's explicit exclusion.

## Security and operations

- **Authorization:** unchanged — this feature adds no new endpoint and does not alter who can call
  `FinalizeServiceOrderUseCase`.
- **Sensitive data in logs:** the chosen channel logs a simulated e-mail. `AGENTS.md`'s security review checklist
  explicitly flags "secrets, credentials and sensitive log content," and the platform-wide error-handling rule
  says to "log without secrets or personal data." A raw customer e-mail address (and full name) is personal data,
  so the `INFO` log line **must not contain the raw e-mail address or full name** — it identifies the customer
  only by `customerId` (an opaque identifier already used elsewhere in application logs) and the `serviceOrderId`.
  If a masked form of the e-mail is useful to demonstrate the lookup succeeded (e.g. for manual verification during
  the tech challenge demo), mask it (e.g. `j***@e***.com`) rather than logging the address in full.
- **New dependency:** `CustomerRepository` (read-only) is now reachable from `servicelifecycle`'s infrastructure
  layer — no write access, no new attack surface beyond an additional read of already-existing data.
- **Rollout/recovery:** purely additive and backward compatible; no migration, no feature flag needed. Rollback is
  a plain revert of the adapter/use-case change — `N/A` beyond normal git revert, since no persisted state or
  external system state is created.

## Test strategy

- **Application (use case), `FinalizeServiceOrderUseCaseTest` — new, none exists today:**
  - Happy path: `CustomerNotificationPort.notifyServiceOrderFinalized(serviceOrderId, customerId)` is invoked
    exactly once when finalize succeeds.
  - Precondition failure (status not `COMPLETED`, or `vehicleDelivered = false`): the port is never invoked
    (existing `IllegalStateException` behavior from `ServiceOrder.finalize()` is preserved unchanged).
  - Notification failure: mock the port to throw a `RuntimeException`; assert `execute(...)` still returns
    normally and `repository.save(...)` was already called — the notification failure must not propagate.
  - Mocking via Mockito (already on the test classpath via `spring-boot-starter-test`); this is the first
    use-case-level test in the module, so it also establishes the convention for future use-case tests.
- **Infrastructure (adapter), `SimulatedEmailCustomerNotificationAdapterTest` — new:**
  - Found customer: verify a log line is emitted (e.g. via a Logback `ListAppender`) and assert it does **not**
    contain the raw e-mail address or full name — only `customerId`/`serviceOrderId` (and a masked e-mail, if
    included).
  - Customer not found: verify a `WARN` log is emitted and the method returns normally (no exception).
- **Module boundary:** re-run `ModuleStructureTest` (`ApplicationModules.verify()`) after adding the
  `servicelifecycle → registration.customer` dependency to confirm it doesn't introduce a cycle or violate
  boundaries; add the `@NamedInterface` fallback described above if verification fails. Per `AGENTS.md`, module
  interactions should use `@ApplicationModuleTest` where isolation matters — add one covering the finalize flow
  end-to-end (real adapter, real `CustomerRepository`, in-memory/test persistence) to prove the two modules wire
  together correctly in a Spring context, complementing the mocked unit tests above.
- **HTTP:** no contract change, so no new/updated MockMvc tests are required for this feature.
- **Coverage:** maintain the project's 80% target on all changed/new code (`make coverage`).

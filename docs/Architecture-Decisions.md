# Architecture Decision Register

## Purpose

This register separates architectural decisions from ordinary missing implementation and documentation drift. It
exists so work can proceed within the correct ownership boundary without silently turning one developer's local
choice into a team-wide architecture decision.

Sources cross-referenced:

1. The official Miro text associated with the exact title **Tech Challenge**.
2. The group's Miro DDD documents, Event Storming, Context Map, C4 diagrams, RFC and ADRs.
3. `docs/Architecture.md`, including its current/target/requirements comparison.
4. `docs/PROJECT-STRUCTURE.md`, authoritative for intended repository organization.
5. The current repository, authoritative only for what is implemented now.
6. The existing root file `AGENTS.md`, operational guidance for Codex rather than architecture authority.

The Miro refinement assigns **Epic 1 — Cadastros (RF01–RF08)** to Ivan: Customer, Vehicle and ServiceCatalog.
That assignment defines Ivan's scope in this register. Decisions outside it remain with the relevant owner or the
whole team even when they affect Ivan through integration contracts.

During the decision discussion on 11 August 2026, Ivan explicitly selected **Option A** for AD-002, AD-003,
AD-004 and AD-005. No recommendation for any other entry is treated as approval.

## Decision Status Definitions

| Status | Meaning |
|---|---|
| **Pending** | A real choice remains and the named owner may decide it. |
| **Resolved** | Evidence shows that the decision was explicitly made and adopted. |
| **Team Decision Required** | The choice crosses ownership or module boundaries and must not be made unilaterally. |
| **Not Blocking** | The question is valid but no current implementation needs its answer. |
| **Deferred** | The decision is intentionally postponed to a later phase or until a trigger occurs. |
| **Superseded** | A newer accepted decision replaces an older alternative. |

Status describes decision state, not implementation state. A resolved decision can still be unimplemented.

## Decision Scope Definitions

| Scope | Meaning |
|---|---|
| **Ivan / my assigned scope** | Affects Epic 1 — Cadastros and can be decided by Ivan without redefining another epic. |
| **Shared architecture** | A cross-cutting technical choice used by several contexts. |
| **Another team member's scope** | Primarily belongs to another assigned epic; Ivan should not decide it alone. |
| **Whole-team decision** | Changes system boundaries, shared contracts, delivery scope or architecture policy. |

## Source Conflicts

### Conflict classification

| Difference | Classification | Why it matters |
|---|---|---|
| Miro contexts (`Cadastros`, `Service Lifecycle`, `Stock & Procurement`, `Notification`) versus repository/`PROJECT-STRUCTURE.md` modules (`customer`, `technician`, `parts`, `serviceorder`) | Architectural decision required + repository organization mismatch | Placement and public contracts for missing aggregates cannot be inferred safely. |
| Technician as Miro actor versus implemented/planned aggregate and module | Architectural decision required | Ownership, persistence and authorization model differ. |
| Miro Stock aggregate versus implemented/planned Part aggregate | Architectural decision required | Atomic reservation and PurchaseOrder boundaries change. |
| Older Estimate-wide approval versus newer per-line decisions | Documentation inconsistency with an implementation-blocking decision | Newer refinement is explicit, but older diagrams remain contradictory. |
| Status calculated on read versus stored `statusSnapshot` | Documentation inconsistency + team ratification required | Newer refinement and current code use the materialized snapshot, but Ivan did not approve this shared choice. |
| OHS/REST in Context Map versus in-process Java port in RFC | Architectural decision required | It defines coupling and contracts between modules. |
| Missing Vehicle, ServiceCatalog, Estimate, Notification, PurchaseOrder, JWT and Swagger code | Implementation not completed yet, except where placement/ownership is undecided | Absence alone is not an architecture debate. |
| Java 17 and dependency examples in `AGENTS.md` versus Java 21/current `pom.xml` | Stale `AGENTS.md` instruction | Operational guidance could cause incorrect changes. |
| Simplified `PENDING → IN_PROGRESS → COMPLETED/CANCELLED` in `AGENTS.md` versus current rich execution state machine | Stale `AGENTS.md` instruction + domain conflict | It contradicts the implemented and refined invariants. |
| Polling/cache declared sufficient in `AGENTS.md`, while local ADR has no accepted status and cache is absent | Stale assumption / unresolved architecture | Codex must not treat it as a ratified decision. |
| Payment Gateway only in C4 | Documentation inconsistency | No official requirement, aggregate, RF or integration contract supports it. |
| Private versus public repository in the official brief | Documentation/operational inconsistency | It affects submission procedure, not system architecture. |
| English code names versus Portuguese business terms | Harmless difference when mappings stay explicit | The tactical document intentionally translates names for code. |

### Source authority applied

- **Tech Challenge** determines mandatory outcomes, not the internal module design.
- **Miro** determines the group's domain intent unless a newer accepted Miro artifact supersedes an older one.
- **Architecture.md** summarizes evidence and gaps but does not resolve them.
- **PROJECT-STRUCTURE.md** determines intended package/layer organization until the team explicitly changes it.
- **Current repository** proves only current implementation.
- **AGENTS.md** governs agent behavior only where it is consistent with the above sources and current code.

## Decision Register

### AD-001 — Map Miro bounded contexts to authoritative repository modules

**Status:** Team Decision Required

**Scope:** Whole-team decision

**Blocking:**

- Blocks activation of Ivan's conditionally selected Vehicle and ServiceCatalog placement in code.
- Blocks the future location of Estimate, PurchaseOrder and Notification.
- Does not block isolated improvements inside the existing Customer aggregate.

**Related Epic / responsibility:** All epics; immediately Epic 1 — Cadastros.

**Problem:** Decide how the Miro contexts map to `customer`, `technician`, `parts` and `serviceorder`, which
`PROJECT-STRUCTURE.md` defines as the intended modules.

**Why this is a decision rather than an implementation gap:** Adding missing classes without an ownership mapping
would encode bounded-context boundaries and dependencies that the sources currently contradict.

**Conflicting evidence:** Miro **6. Refinamento Técnico**, **Context Map** and accepted RFC define Cadastros,
Service Lifecycle, Stock & Procurement and Notification. `PROJECT-STRUCTURE.md`, `AGENTS.md` and current packages
define Customer, Technician, Parts and Service Order.

**Options:**

Option A — Preserve the four physical modules and publish an explicit conceptual mapping.

- Advantages: no restructuring; matches authoritative repository direction and implemented code.
- Disadvantages: `customer` may contain more than its name implies; Notification has no obvious home.

Option B — Refactor packages to match the four Miro contexts.

- Advantages: direct alignment between ubiquitous language, Context Map and module boundaries.
- Disadvantages: broad repository change, merge risk and redesign outside Ivan's authority.

**Recommended option:** Option A for the MVP, followed by a team-approved mapping table and targeted naming
clarifications. It minimizes redesign while preserving DDD boundaries through public ports and IDs.

**Impact of the decision:** Module names, aggregate placement, public interfaces, Spring Modulith rules,
persistence packages, tests, every epic's Jira stories, `PROJECT-STRUCTURE.md`, `Architecture.md` and `AGENTS.md`.

**Can work continue without resolving it?** Partially. Customer-local work can continue; Vehicle and
ServiceCatalog should not be placed until the mapping is confirmed.

**Temporary safe assumption, if any:** Keep existing modules untouched and implement nothing that requires a new
top-level package. This preserves the current structure but is not a mapping decision.

### AD-002 — Model and validate Customer CPF/CNPJ

**Status:** Resolved

**Scope:** Ivan / my assigned scope

**Decision:** Option A — introduce an immutable `TaxId` value object in the Customer domain.

**Resolved on:** 11 August 2026.

**Blocking:**

- No longer blocks RF01 at the architectural level.
- Implemented for Customer RF01 in the API, persistence and automated tests.

**Related Epic / responsibility:** Epic 1 — Cadastros (Ivan), RF01.

**Problem:** The current Customer stores a raw `String document`; the official challenge requires CPF/CNPJ
validation and Miro defines a `TaxId` value object with type and check-digit invariant.

**Why this is a decision rather than an implementation gap:** The location and representation of the invariant
determine the domain model, equality, persistence mapping and API validation boundary.

**Conflicting evidence:** Official **Tech Challenge** requires CPF/CNPJ validation; Miro **7. Detalhamento...**
defines `TaxId`; current `Customer.java` stores an unvalidated string; `AGENTS.md` mentions validation but its
example DTO/model still uses primitive IDs and generic strings.

**Options:**

Option A — Introduce immutable `TaxId` in the Customer domain.

- Advantages: invariant is always enforced; matches DDD and Miro; easy focused unit tests.
- Disadvantages: requires JPA mapper/entity changes and an API serialization decision.

Option B — Keep `String document` and validate in DTO/use case.

- Advantages: smaller immediate change.
- Disadvantages: invalid Customers can be created outside that path; duplicates rules across entry points.

**Recommended option:** Option A, selected by Ivan. It directly satisfies the official sensitive-data validation
requirement and matches the existing repository's preference for domain value objects.

**Rationale for the selected option:** CPF/CNPJ validity is a Customer invariant, not merely an HTTP-input rule.
Keeping it in an immutable value object makes every construction path enforce the same rule and matches the Miro
`TaxId` model.

**Impact of the decision:** Customer domain, request/response DTOs, persistence mapper/entity, repository queries
for uniqueness, validation errors, unit/integration tests and RF01 Jira acceptance criteria.

**Consequences:** `Customer` will use `TaxId` instead of a raw document string; adapters must serialize and persist
its value without moving validation into JPA or controllers; uniqueness remains a repository/application check.

**Likely Jira/story implications:** RF01 should include CPF/CNPJ check-digit tests, duplicate-document handling,
DTO/persistence mapping and API error acceptance criteria. This is implementation work, not another decision.

**Can work continue without resolving it?** Resolved. A future implementation may proceed with `TaxId` as the
domain invariant when feature work is authorized.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved.

### AD-003 — Place and own the Vehicle aggregate

**Status:** Resolved

**Scope:** Ivan / my assigned scope

**Decision:** Option A — model Vehicle as an independent aggregate root with its own repository inside the
existing `customer` module.

**Resolved on:** 11 August 2026.

**Dependency:** This decision is conditional on AD-001 confirming that the physical `customer` module hosts the
conceptual Cadastros context. It does not resolve AD-001. If the team selects an incompatible mapping, AD-003 must
be superseded or reopened before implementation.

**Blocking:**

- The placement choice no longer blocks RF03–RF06.
- AD-001 still blocks creating the package and finalizing the cross-context contract.

**Related Epic / responsibility:** Epic 1 — Cadastros (Ivan).

**Problem:** Vehicle is a Miro aggregate inside Cadastros, but neither the current repository nor
`PROJECT-STRUCTURE.md` defines a Vehicle module or package location.

**Why this is a decision rather than an implementation gap:** Placement determines the aggregate's module,
repository ownership and what ServiceOrder may import or call.

**Conflicting evidence:** Miro assigns Vehicle to Cadastros and ServiceOrder holds `vehicleId` plus snapshot.
`PROJECT-STRUCTURE.md` defines only `customer`, `technician`, `parts`, `serviceorder`.

**Options:**

Option A — Make Vehicle a separate aggregate inside the existing `customer` module.

- Advantages: obeys current physical structure; keeps Customer/Vehicle master data together; no new module.
- Disadvantages: package/module name is narrower than its responsibility.

Option B — Add a new top-level `vehicle` module.

- Advantages: clear package name and independent API.
- Disadvantages: changes authoritative structure and splits the Miro Cadastros context.

**Recommended option:** Option A, selected by Ivan for the MVP, conditional on AD-001 confirming that `customer`
physically hosts the conceptual Cadastros context. Vehicle remains its own aggregate root and repository, not a
Customer child.

**Rationale for the selected option:** It preserves the agreed four-module repository shape and keeps registration
master data together while retaining Vehicle's own identity and lifecycle. The condition prevents Ivan's local
choice from silently deciding the team-wide context mapping.

**Impact of the decision:** Package layout, Spring Modulith exposure, Vehicle repository/JPA schema, Customer
relationship, ServiceOrder port/snapshot contract, API routes, tests and RF03–RF06 stories.

**Consequences:** If AD-001 confirms the premise, Vehicle belongs under `customer` but remains an independent
aggregate root with a separate domain repository. ServiceOrder references it by ID and freezes `VehicleSnapshot`;
Customer does not own a mutable collection of Vehicle entities inside its aggregate boundary.

**Likely Jira/story implications:** RF03–RF06 may be planned now with an explicit AD-001 dependency. Implementation
stories must cover plate/chassis invariants, Customer association, mileage monotonicity, snapshots, persistence,
REST DTOs and logical removal under AD-005.

**Can work continue without resolving it?** Resolved within Ivan's scope, but Vehicle implementation must wait for
AD-001 to validate the decision's module-mapping premise.

**Temporary safe assumption, if any:** Until AD-001 is resolved, define the conceptual Vehicle contract in planning
only and create no package.

### AD-004 — Place and own ServiceCatalog

**Status:** Resolved

**Scope:** Ivan / my assigned scope

**Decision:** Option A — model ServiceCatalog as an independent aggregate root with its own repository inside the
existing `customer` module, while consumers preserve service descriptions and prices as historical snapshots.

**Resolved on:** 11 August 2026.

**Dependency:** This decision is conditional on AD-001 confirming that the physical `customer` module hosts the
conceptual Cadastros context. It does not resolve AD-001. If the team selects an incompatible mapping, AD-004 must
be superseded or reopened before implementation.

**Blocking:**

- The catalog placement and ownership choice no longer blocks RF07–RF08.
- AD-001 still blocks creating the package and finalizing its public lookup contract.

**Related Epic / responsibility:** Epic 1 — Cadastros (Ivan); dependency for Epic 2.

**Problem:** Miro places ServiceCatalog in Cadastros, while the authoritative repository structure has no catalog
module or stated destination.

**Why this is a decision rather than an implementation gap:** Ownership controls who sets prices, exposes the
lookup API and guarantees that historical ServiceExecution/Estimate snapshots are not rewritten.

**Conflicting evidence:** Miro **3. Ubiquitous Language**, **4. Aggregates** and RF07–RF08 define ServiceCatalog.
Current ServiceOrder only receives `catalogServiceId`, name and price snapshots. `PROJECT-STRUCTURE.md` is silent.

**Options:**

Option A — Put ServiceCatalog as a separate aggregate in the existing `customer`/conceptual Cadastros module.

- Advantages: respects Miro's language boundary and current module count.
- Disadvantages: physical package name is misleading.

Option B — Add a top-level `servicecatalog` module.

- Advantages: clear ownership and public interface.
- Disadvantages: changes the agreed structure and fragments Cadastros.

**Recommended option:** Option A, selected by Ivan for the MVP, conditional on AD-001. Preserve snapshot-by-value
at the consumer.

**Rationale for the selected option:** Miro places the catalog in Cadastros, while a new top-level module would
change the intended repository structure. Independent aggregate ownership plus consumer snapshots protects price
history without sharing mutable domain objects.

**Impact of the decision:** Package ownership, Money representation, catalog repository/API, public lookup port,
ServiceOrder/Estimate DTO contracts, persistence, tests and RF07–RF08 stories.

**Consequences:** If AD-001 confirms the premise, ServiceCatalog belongs under `customer` with its own repository.
Price changes affect only future selections; ServiceExecution and Estimate retain copied name/price values. The
cross-module lookup mechanism remains subject to AD-011.

**Likely Jira/story implications:** RF07–RF08 may be planned now with AD-001 and, for integration, AD-011 noted as
dependencies. Stories should separate catalog CRUD from consumer integration and test snapshot immutability.

**Can work continue without resolving it?** Resolved within Ivan's scope, but ServiceCatalog implementation must
wait for AD-001 to validate the decision's module-mapping premise.

**Temporary safe assumption, if any:** Until AD-001 is resolved, continue using opaque `catalogServiceId` and
snapshots in ServiceOrder; do not create a package or live domain dependency.

### AD-005 — Define deletion and deactivation semantics for registration data

**Status:** Resolved

**Scope:** Ivan / my assigned scope

**Decision:** Option A — use logical deactivation/archival for Customer, Vehicle and ServiceCatalog records,
preventing their use in new operations while preserving historical references.

**Resolved on:** 11 August 2026.

**Blocking:**

- No longer blocks deletion/deactivation acceptance criteria.
- Implemented for Customer; Vehicle and ServiceCatalog remain pending.

**Related Epic / responsibility:** Epic 1 — Cadastros (Ivan).

**Problem:** Decide whether Customer, Vehicle and ServiceCatalog records can be physically deleted after being
referenced by historical Service Orders and snapshots.

**Why this is a decision rather than an implementation gap:** Deletion changes invariants, auditability,
referential integrity and API semantics; a normal missing DELETE endpoint cannot answer those questions.

**Conflicting evidence:** At decision time, the official brief said CRUD; Miro required historical snapshots and
explicitly listed Vehicle removal, but did not define Customer/catalog deletion. Controllers then exposed no delete
operations.

**Options:**

Option A — Logical deactivation/archival, preventing new use while preserving history.

- Advantages: safe audit trail and references; compatible with snapshots.
- Disadvantages: requires status/filter semantics rather than simple delete.

Option B — Physical deletion when no active/historical reference exists.

- Advantages: literal CRUD and simpler active dataset.
- Disadvantages: reference checks and race conditions; inconsistent behavior after use.

**Recommended option:** Option A, selected by Ivan. It preserves history and avoids coupling deletion to every
consumer. The API can still expose DELETE semantics that perform deactivation if documented.

**Rationale for the selected option:** Registration data is referenced by historical Service Orders and commercial
snapshots. Logical deactivation prevents new use while preserving auditability and avoids cross-module checks before
every deletion.

**Impact of the decision:** Domain status, repository filters, unique constraints, DELETE responses, audit data,
tests, RF06 and administrative CRUD stories.

**Consequences:** Customer, Vehicle and ServiceCatalog require an active/archived lifecycle; ordinary queries used
for new work exclude inactive records, while historical reads remain valid. Physical deletion is not the business
meaning of removal. Identifier reuse and HTTP response details still belong in story-level API acceptance criteria.

**Likely Jira/story implications:** Customer completion and RF06/RF07–RF08 administration stories should include
deactivation commands, active-list filtering, historical-read behavior and persistence/integration tests. They must
not introduce hard deletion of referenced registration data.

**Can work continue without resolving it?** Resolved. Future delete/deactivation acceptance criteria can use
logical deactivation when feature implementation is authorized.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved.

### AD-006 — Decide whether Technician is an aggregate/module or only an identity actor

**Status:** Resolved

**Scope:** Whole-team decision

**Decision:** Option A — Technician stays a rich aggregate/module (name, specialties, availability status),
as already implemented. Assignment (`AssignTechnicianUseCase`, `AssignDiagnosisAssigneeUseCase`) validates
only that the `technicianId` exists for now; specialty/availability validation against that data is not
implemented yet and is tracked as technical debt, not as a blocked implementation gap — see
`docs/tech-debt/TD-002-technician-assignment-does-not-validate-specialty-or-availability.md`.

**Resolved on:** 23 August 2026, ratified by the team.

**Blocking:**

- No longer blocks the definitive technician ownership model.
- Does not block Ivan's master-data work.

**Related Epic / responsibility:** Epic 3 and security; existing Technician module.

**Problem:** Reconcile Miro's actor-only Technician with the implemented aggregate containing name, specialties
and availability.

**Why this is a decision rather than an implementation gap:** It changes bounded contexts, persistence ownership
and whether assignment validates domain capabilities or only an authenticated user ID.

**Conflicting evidence:** Miro requirements refinement says Technician is not an aggregate. `PROJECT-STRUCTURE.md`,
`AGENTS.md` and current code define a Technician aggregate/module.

**Options:**

Option A — Keep Technician aggregate/module for workshop resource management.

- Advantages: preserves implemented work; supports specialty and availability rules.
- Disadvantages: diverges from newer Miro and expands beyond explicit official CRUD.

Option B — Reduce Technician to an authenticated identity referenced by ID.

- Advantages: matches newer Miro and simplifies the domain.
- Disadvantages: discards implemented behavior and loses resource-management rules.

**Recommended option:** Option A, selected by the team. Existing behavior is coherent and removing it would
have created needless redesign.

**Impact of the decision:** Technician module/domain, auth user mapping, assignment port, ServiceExecution,
persistence, APIs, tests, Miro and `AGENTS.md`.

**Can work continue without resolving it?** Resolved. Technician assignment may proceed under
existence-only validation; specialty/availability validation is deferred as technical debt (TD-002), not
as a blocked decision.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved. Continue referencing
Technician only by UUID across modules, as before.

### AD-007 — Reconcile Part with Stock/StockItem/PurchaseOrder boundaries

**Status:** Team Decision Required

**Scope:** Another team member's scope

**Blocking:**

- Blocks atomic reservation and procurement design in Epic 4.
- Does not block Ivan's Epic 1.

**Related Epic / responsibility:** Epic 4 — Stock & Procurement.

**Problem:** Decide whether current Part remains an aggregate per item or becomes StockItem inside a Stock aggregate,
and where PurchaseOrder belongs.

**Why this is a decision rather than an implementation gap:** Aggregate boundaries determine atomicity,
repository transactions and domain-event ownership.

**Conflicting evidence:** Current code/`PROJECT-STRUCTURE.md` define Part aggregate. Miro defines Stock aggregate
containing StockItems and separate PurchaseOrder, with all-or-nothing reservation.

**Options:**

Option A — Keep Part aggregates and implement atomic reservation in an application/domain service transaction.

- Advantages: preserves code and module structure.
- Disadvantages: reservation spans multiple aggregates and weakens the stated Stock invariant.

Option B — Refactor to Stock aggregate + StockItem entities, with PurchaseOrder aggregate in `parts`.

- Advantages: matches Miro invariants and centralized reservation.
- Disadvantages: substantial model/persistence rewrite and potentially large aggregate.

**Recommended option:** Team review required; for MVP, prefer the least rewrite that can prove atomic reservation
under concurrency. The final choice must document the transaction boundary.

**Impact of the decision:** Parts domain, database, locking, repositories, events, ACL, APIs, tests and RF25–RF30.

**Can work continue without resolving it?** Basic Part CRUD can; reservation/procurement cannot safely finalize.

**Temporary safe assumption, if any:** Keep current Part API stable and avoid cross-item reservation logic.

### AD-008 — Confirm Estimate approval granularity and lifecycle

**Status:** Resolved

**Scope:** Another team member's scope

**Decision:** Option A — per-ServiceExecution line approval/rejection, with the Estimate itself tracked by status
(`draft`, `sent`, `closed`, `expired`) rather than a single approve/reject flag on the whole Estimate.

**Resolved on:** 23 August 2026, ratified by the team.

**Blocking:**

- No longer blocks Estimate aggregate implementation or Epic 3 authorization integration.
- Does not block Ivan except the future catalog snapshot contract.

**Related Epic / responsibility:** Epic 2 — Diagnosis and Estimate.

**Problem:** Ratify per-ServiceExecution line decisions and the Estimate states `draft`, `sent`, `closed`, `expired`
as the replacement for whole-Estimate approve/reject.

**Why this is a decision rather than an implementation gap:** Approval granularity changes aggregate methods,
events, stock reservation scope and completion rules.

**Conflicting evidence:** Older Miro aggregate material uses Estimate approved/rejected as a whole. Newer
**Levantamento...** and **7. Detalhamento...** explicitly use per-line decisions and `closed`.

**Options:**

Option A — Per-line approval/rejection, Estimate closes when all service lines are decided.

- Advantages: newest refinement; supports partial authorization and official additional repairs.
- Disadvantages: more events and edge cases.

Option B — Whole-Estimate approval/rejection.

- Advantages: simpler model and UI.
- Disadvantages: contradicts newer documented decision and loses partial approval.

**Recommended option:** Option A, selected by the team. Older whole-Estimate approve/reject artifacts are
superseded.

**Impact of the decision:** Estimate, ServiceExecution states/events, stock reservation, tracking, persistence,
APIs, tests, Jira acceptance criteria and Miro cleanup.

**Can work continue without resolving it?** Resolved. Estimate implementation may proceed under per-line
approval with `draft`/`sent`/`closed`/`expired` Estimate states.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved.

### AD-009 — Associate part lines with service decisions

**Status:** Team Decision Required

**Scope:** Another team member's scope

**Blocking:**

- Blocks correct pricing and reservation after partial approval.
- Does not block Ivan's catalog CRUD.

**Related Epic / responsibility:** Epics 2 and 4.

**Problem:** Decide how `EstimateLinePart` identifies the ServiceExecution whose approval controls that material.

**Why this is a decision rather than an implementation gap:** Without ownership semantics, partial approval can
reserve or charge parts for rejected work.

**Conflicting evidence:** Newer Miro explicitly marks this as an open nuance; ServiceExecution already owns
StockRequirements, while Estimate part lines currently carry only stockItemId, quantity and price.

**Options:**

Option A — Add `serviceExecutionId` to every EstimateLinePart.

- Advantages: explicit authorization, pricing and audit relationship.
- Disadvantages: duplicates association already present through StockRequirement snapshots.

Option B — Nest part snapshots inside each EstimateLineService.

- Advantages: structural ownership is unambiguous.
- Disadvantages: changes line model and serialization; shared parts need allocation rules.

**Recommended option:** Option A for minimal change and explicit traceability.

**Impact of the decision:** Estimate VOs, API payloads, totals, stock events, persistence, tests and Epics 2/4.

**Can work continue without resolving it?** Service-only Estimate work can; part-inclusive approval cannot.

**Temporary safe assumption, if any:** Do not approve/reserve part lines independently of their service.

### AD-010 — Materialize ServiceOrder status as statusSnapshot

**Status:** Resolved

**Scope:** Shared architecture

**Decision:** Option B — store `statusSnapshot` on `ServiceOrder` and recompute it after every relevant command,
as already implemented. Queries only read the stored field; they do not recompute status on the fly.

**Resolved on:** 24 August 2026, ratified by the team.

**Blocking:**

- No longer blocks Epic 3 tracking/read-model work built on `statusSnapshot`.

**Related Epic / responsibility:** Epic 3 — Execution and Tracking.

**Problem:** Choose on-read calculation or command-time recalculation of the derived status.

**Why this is a decision rather than an implementation gap:** It controls consistency, query performance and when
state must be updated.

**Conflicting evidence:** Older Miro says status is never stored. Newer refinement explicitly resolves the risk in
favor of `statusSnapshot`; current ServiceOrder implements that design.

**Options:**

Option A — Recompute on every query.

- Advantages: no stored derived field.
- Disadvantages: query cost/N+1 risk and cross-aggregate reads.

Option B — Store and recompute `statusSnapshot` after relevant commands.

- Advantages: efficient reads; matches current code and newer refinement.
- Disadvantages: every state-changing path must maintain the snapshot.

**Recommended option:** Option B, selected by the team. It is implemented and matches the newer Miro refinement.

**Impact of the decision:** ServiceOrder invariants, persistence, command tests, tracking queries and docs.

**Can work continue without resolving it?** Resolved. `statusSnapshot` is confirmed as the durable status
representation; every command that changes ServiceOrder state must keep maintaining it.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved.

### AD-011 — Choose in-process module integration contracts

**Status:** Team Decision Required

**Scope:** Whole-team decision

**Blocking:**

- Blocks final cross-module integration contracts.
- Does not block isolated aggregate/use-case development with mocks.

**Related Epic / responsibility:** All epics.

**Problem:** Decide whether modules call public Java interfaces in-process, invoke internal REST/OHS endpoints, or
use events depending on interaction type.

**Why this is a decision rather than an implementation gap:** It defines coupling, failure semantics, transaction
scope and public interfaces.

**Conflicting evidence:** Context Map says Registrations exposes OHS REST. Accepted RFC says consumer-owned port
implemented by a direct Java adapter in the same process. `PROJECT-STRUCTURE.md` says port + adapter if needed.

**Options:**

Option A — In-process Java ports for synchronous queries and domain events for reactions.

- Advantages: fits modular monolith; no HTTP overhead; consumer controls the port.
- Disadvantages: requires disciplined named/public interfaces.

Option B — Internal REST calls between modules.

- Advantages: resembles future service extraction.
- Disadvantages: unnecessary network-style failure/serialization inside one process.

**Recommended option:** Option A. It best matches the accepted RFC, current structure and MVP simplicity.

**Impact of the decision:** Application ports, infrastructure adapters, DTOs, events, tests, module exposure and
Miro Context Map wording.

**Can work continue without resolving it?** Yes with mocks; integration stories cannot finalize.

**Temporary safe assumption, if any:** Consumers declare their own interfaces and depend only on UUID/value DTOs.

### AD-012 — Define domain-event delivery guarantees

**Status:** Deferred

**Scope:** Shared architecture

**Blocking:**

- Does not block standalone MVP domain work.
- Will block reliable cross-context behavior before release.

**Related Epic / responsibility:** Epics 2–5.

**Problem:** Decide synchronous versus asynchronous publication and whether outbox, retries, idempotency and event
versioning are required in Phase 1.

**Why this is a decision rather than an implementation gap:** Delivery guarantees alter consistency, failure
recovery, persistence and testing strategy.

**Conflicting evidence:** Miro requires eventual consistency/domain events conceptually but defines no delivery
mechanism. The current repository has no event bus.

**Options:**

Option A — Synchronous in-process Spring events for the MVP.

- Advantages: simplest implementation and testing in a monolith.
- Disadvantages: weak recovery and tighter temporal coupling.

Option B — Transactional outbox with asynchronous consumers.

- Advantages: reliable delivery and extraction path.
- Disadvantages: significant MVP complexity and operational work.

**Recommended option:** Defer outbox; when integration starts, team should explicitly approve Option A for Phase 1
with idempotent handlers and document the reliability limitation.

**Impact of the decision:** Event publisher, database, transactions, handlers, retries, tests and operations.

**Can work continue without resolving it?** Yes while contracts are mocked. Resolve before cross-context release.

**Temporary safe assumption, if any:** Define immutable event payload contracts without choosing transport.

### AD-013 — Define Estimate expiration duration and mechanism

**Status:** Team Decision Required

**Scope:** Another team member's scope

**Blocking:**

- Blocks RF17 and Estimate expiry tests.
- Does not block Ivan.

**Related Epic / responsibility:** Epic 2.

**Problem:** Choose the business deadline and technical trigger for automatic expiry.

**Why this is a decision rather than an implementation gap:** Both the duration rule and scheduler semantics are
business/architecture choices, not missing boilerplate.

**Conflicting evidence:** Miro contains 24 h, 48 h and 48 h + estimated restock time. Refinement marks scheduling
as pending (job, delayed messaging, etc.).

**Options:**

Option A — Fixed deadline with a Spring scheduled job.

- Advantages: simple, testable and adequate for the MVP.
- Disadvantages: scan latency and multi-instance coordination considerations.

Option B — Availability-dependent deadline with delayed messages.

- Advantages: matches richer Miro rules and scales asynchronously.
- Disadvantages: depends on ETA and messaging not otherwise required.

**Recommended option:** Option A with one team-approved duration for Phase 1; record richer timing as deferred.

**Impact of the decision:** Estimate fields/invariants, clock abstraction, scheduler, persistence query, events,
tests, API display and Jira criteria.

**Can work continue without resolving it?** Estimate draft/generation can; send/expire behavior cannot finalize.

**Temporary safe assumption, if any:** Inject a clock and store `expiresAt`; do not hard-code the duration yet.

### AD-014 — Define Notification boundary and MVP channel

**Status:** Team Decision Required

**Scope:** Another team member's scope

**Blocking:**

- Blocks RF31–RF33 delivery behavior.
- Does not block Ivan or publishers if event contracts are mockable.

**Related Epic / responsibility:** Epic 5 — Notifications.

**Problem:** Decide whether Notification is a module/context and whether the MVP sends email, exposes a simulated
push endpoint, or only records notifications.

**Why this is a decision rather than an implementation gap:** Channel and module ownership define external
dependencies, delivery guarantees and public behavior.

**Conflicting evidence:** Miro calls Notification a context/generic subdomain; `PROJECT-STRUCTURE.md` has no module.
Event Storming leaves the channel as a hotspot; C4 shows Email System; a sticky suggests simulated push.

**Options:**

Option A — Add an in-process Notification module with a persisted/readable simulated channel.

- Advantages: demonstrable without external credentials; clear event consumer.
- Disadvantages: requires team approval to add a module or map it elsewhere.

Option B — Integrate a real email provider.

- Advantages: realistic delivery.
- Disadvantages: external configuration, failure modes and testing overhead.

**Recommended option:** Option A for the MVP after AD-001 defines its physical home.

**Impact of the decision:** Module map, event handlers, persistence/API, external config, tests, Docker and C4.

**Can work continue without resolving it?** Publishers can define events; notification delivery cannot.

**Temporary safe assumption, if any:** Publish channel-neutral notification intents.

### AD-015 — Ratify the tracking update strategy

**Status:** Resolved

**Scope:** Shared architecture

**Decision:** Option A — plain client polling of the existing REST status endpoint for MVP, without an
application cache layer.

**Resolved on:** 23 August 2026, ratified by the team (item 1 of the `ADR-002-realtime-updates-strategy.md`
Approval Checklist, "Time concorda com Polling para MVP").

**Blocking:**

- No longer blocks tracking strategy for Epic 3.
- Cache/WebSocket-specific implementation (Option B and beyond) remains out of scope for the MVP unless a new
  decision reopens it.

**Related Epic / responsibility:** Epic 3.

**Problem:** Decide whether Phase 1 uses plain polling, polling with cache, SSE/WebSocket, or no explicit update
mechanism beyond the REST status endpoint.

**Why this is a decision rather than an implementation gap:** It affects runtime dependencies, consistency,
client contract and invalidation.

**Conflicting evidence (at the time the decision was open):** `AGENTS.md` declared polling with cache sufficient;
C4 showed SimpleCache/5 s; local `ADR-002-realtime-updates-strategy.md` had no accepted status; current code
exposes GET status but no cache.

**Options:**

Option A — Plain client polling of the existing REST status endpoint for MVP.

- Advantages: minimal scope, satisfies API tracking, no invalidation risk.
- Disadvantages: repeated database reads.

Option B — Polling plus application cache.

- Advantages: lower repeated read cost.
- Disadvantages: invalidation/staleness complexity without demonstrated need.

**Recommended option:** Option A, selected by the team. It matches the current implementation
(`GET /api/service-orders/{id}/status`, no cache) and defers caching until measurements justify it.

**Impact of the decision:** API documentation, caching dependencies/config, invalidation, tests, C4 and AGENTS.

**Can work continue without resolving it?** Resolved. Future work may extend `track-execution` under the plain
polling contract without a caching layer.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved. Introducing a cache, SSE or
WebSocket mechanism requires a new decision, not a reopening of AD-015.

### AD-016 — Define identity ownership and authorization policy

**Status:** Resolved

**Scope:** Whole-team decision

**Decision:** Option A — a small internal Identity/Auth module owns credentials and the role-to-domain-ID
mapping. Customer and Technician remain domain references, not credential owners.

**Resolved on:** 24 August 2026, ratified by the team.

**Blocking:**

- No longer blocks starting JWT implementation and endpoint security tests.
- Does not block Ivan's pure domain/use-case work.

**Related Epic / responsibility:** Security across all epics.

**Problem:** The JWT technology is accepted, but the system still needs a user/credential owner, mapping between
users and Customer/Technician IDs, and final endpoint permissions.

**Why this is a decision rather than an implementation gap:** Identity boundaries and ownership determine access
control and data relationships, not just library configuration.

**Conflicting evidence:** Official brief requires JWT only for administrative APIs. Miro ADR proposes four roles.
`AGENTS.md` has a matrix where Manager approves SO, conflicting with Miro/official Customer approval. No auth code
or identity model exists.

**Options:**

Option A — A small internal Identity/Auth module with credentials and role-to-domain-ID mapping.

- Advantages: clear separation and testable ownership rules.
- Disadvantages: requires physical module mapping under AD-001.

Option B — Embed credentials/roles in Customer and Technician modules.

- Advantages: fewer components.
- Disadvantages: duplicates security concerns and complicates Manager/Admin identities.

**Recommended option:** Option A, selected by the team, while keeping Customer/Technician as domain references
rather than credential owners. The approval actor for commercial decisions is Customer, not Manager, correcting
the earlier mismatch between `AGENTS.md` and Miro/the official brief.

**Impact of the decision:** Security config, login API, database, JWT claims, role matrix, controllers, tests,
Docker secrets, Miro and AGENTS.

**Can work continue without resolving it?** Resolved. Identity/Auth module and JWT implementation may proceed
under Option A; Customer and Technician stay pure domain references.

**Temporary safe assumption, if any:** Not applicable; the decision is resolved. Do not put passwords or roles in
Customer/Technician aggregates.

### AD-017 — Adopt schema migration and DDL policy

**Status:** Team Decision Required

**Scope:** Shared architecture

**Blocking:**

- Does not block local domain modeling.
- Blocks a repeatable shared/release database process.

**Related Epic / responsibility:** Persistence for every epic.

**Problem:** Decide when to replace `ddl-auto=update` with versioned migrations and which tool owns schema changes.

**Why this is a decision rather than an implementation gap:** It establishes the team's database change protocol
and production safety guarantees.

**Conflicting evidence:** MySQL ADR mentions Flyway/Liquibase compatibility but chooses neither. Current config uses
`ddl-auto=update`; the official challenge requires simple repeatable local execution.

**Options:**

Option A — Adopt Flyway now and use `validate` outside disposable local development.

- Advantages: repeatable schema, reviewable changes, safer collaboration.
- Disadvantages: requires baseline/migration work during rapid model changes.

Option B — Keep `ddl-auto=update` through Phase 1.

- Advantages: fastest prototyping.
- Disadvantages: non-repeatable changes and environment drift.

**Recommended option:** Option A before multiple epics add tables. Flyway is a modest cost with high integration
value, but it must be a team policy.

**Impact of the decision:** `pom.xml`, application profiles, SQL migrations, Docker init, CI and persistence stories.

**Can work continue without resolving it?** Yes briefly; resolve before shared schema integration.

**Temporary safe assumption, if any:** Do not depend on destructive automatic schema changes or rename columns yet.

### AD-018 — Confirm external-system scope for Phase 1

**Status:** Team Decision Required

**Scope:** Whole-team decision

**Blocking:**

- Blocks external adapters only.
- Does not block Ivan or core domain work.

**Related Epic / responsibility:** Epic 4 and delivery scope.

**Problem:** Decide whether Phase 1 includes External Supplier integration, Payment Gateway, both, or neither as a
real integration.

**Why this is a decision rather than an implementation gap:** These systems add scope, credentials, contracts and
failure handling beyond the official minimum.

**Conflicting evidence:** Context Map/refinement define supplier ACL; C4 omits supplier but adds Payment Gateway.
The official challenge requires neither integration explicitly.

**Options:**

Option A — Supplier adapter contract/mock only; exclude Payment Gateway.

- Advantages: preserves Miro procurement boundary without unsupported payment scope.
- Disadvantages: not a real external demonstration.

Option B — Implement real supplier and/or payment integrations.

- Advantages: richer demonstration.
- Disadvantages: unnecessary risk and expanded scope for a one-month MVP.

**Recommended option:** Option A. Remove or label Payment Gateway as future/unconfirmed in C4.

**Impact of the decision:** C4, ACL/gateway, Docker/config, tests, Epic 4 stories and delivery demonstration.

**Can work continue without resolving it?** Yes, using an interface and fake adapter if needed.

**Temporary safe assumption, if any:** No payment behavior is part of the MVP until explicitly approved.

### AD-019 — Define average service-execution time semantics

**Status:** Team Decision Required

**Scope:** Whole-team decision

**Blocking:**

- Blocks the official performance-monitoring story.
- Does not block Ivan.

**Related Epic / responsibility:** Management/reporting; likely Epic 3 or a cross-cutting story.

**Problem:** Define what interval is averaged, grouping/filtering rules and whether rejected/waiting time counts.

**Why this is a decision rather than an implementation gap:** Different definitions produce materially different
business metrics and required timestamps.

**Conflicting evidence:** Official brief requires monitoring average execution time. Miro only names Performance
Analytics generically; current model records progress notes but no explicit start/completion timestamps.

**Options:**

Option A — Average active execution time from `startedAt` to `completedAt` for completed ServiceExecutions.

- Advantages: clear and directly attributable to workshop work.
- Disadvantages: excludes diagnosis, approval and waiting for parts.

Option B — Average end-to-end SO duration from received to completed/delivered.

- Advantages: reflects customer experience.
- Disadvantages: mixes operational work with approval/stock/customer delays.

**Recommended option:** Option A for the literal “tempo médio de execução dos serviços”; expose end-to-end lead
time later as a distinct metric.

**Impact of the decision:** ServiceExecution timestamps, persistence, queries/read model, API, tests and Jira story.

**Can work continue without resolving it?** Yes outside analytics; timestamps should not be guessed first.

**Temporary safe assumption, if any:** Preserve domain events for start/completion so either metric remains possible.

## Decisions Affecting My Work

Priority reflects blocking power, downstream impact and dependencies—not decision ownership.

1. **AD-001 — Context-to-module mapping**: team-owned; blocks implementing the conditionally selected placement of
   Vehicle and ServiceCatalog, but not their Jira refinement.
2. **AD-002 — TaxId modeling**: resolved and implemented for Customer with an immutable domain value object.
3. **AD-003 — Vehicle placement**: resolved as an independent aggregate in `customer`, conditional on AD-001.
4. **AD-004 — ServiceCatalog placement**: resolved as an independent aggregate in `customer`, conditional on
   AD-001; consumers retain historical snapshots.
5. **AD-005 — Registration deletion semantics**: resolved with logical deactivation/archival and implemented for
   Customer; Vehicle and ServiceCatalog remain pending.
6. **AD-011 — Cross-module integration**: team-owned; contracts can be mocked meanwhile.
7. **AD-016 — Identity/authorization ownership**: resolved as an internal Identity/Auth module (Option A);
   Customer/Technician remain domain references, not credential owners.
8. **AD-017 — Schema migration policy**: shared; resolve before several epics alter the database concurrently.

All Ivan-owned decisions in this register are resolved. AD-003 and AD-004 remain conditional on the team-owned
AD-001 and must not be treated as approval of the shared context mapping.

## Ivan Scope Readiness

### Ready

- **Jira planning for RF01–RF08:** the approved domain choices, consequences and dependencies are sufficiently clear
  to create and refine stories without inventing architecture.
- **RF01 — Customer CPF/CNPJ:** implemented with the domain invariant, API, repository, persistence and tests defined
  by AD-002.
- **RF02 — Customer contact maintenance:** implemented in the existing aggregate and use-case boundaries.
- **Registration lifecycle stories:** AD-005 defines logical deactivation/archival for Customer, Vehicle and
  ServiceCatalog. Acceptance criteria can be planned now.
- **RF03–RF08 domain and acceptance-criteria refinement:** Vehicle and ServiceCatalog are independent aggregates,
  their invariants are documented in Miro, and snapshot consequences are explicit. Package creation remains gated
  as described below.

### Pending but non-blocking

- **AD-011 — cross-module contracts:** does not block catalog CRUD or Jira planning; it blocks only finalizing the
  ServiceCatalog lookup adapter used by Service Lifecycle.
- **AD-016 — identity and authorization ownership:** resolved (Option A, internal Identity/Auth module); does
  not block pure domain/use-case work while the module is implemented.
- **AD-017 — schema migration policy:** does not block model/story planning or short-lived local work; it must be
  resolved before several epics integrate shared schema changes.
- **AD-010 — status computation:** does not block Ivan; preserve the implemented `statusSnapshot` behavior while
  team ratification remains pending.
- **AD-006 through AD-009, AD-013 through AD-015, AD-018 and AD-019:** belong to the team or other owners and do not
  block Ivan's registration backlog except through the specific integration dependencies already identified.
- **AD-012 — event delivery guarantees:** remains deferred and does not block registration work.

### Blocking

- **AD-001 — context-to-module mapping:** blocks creating Vehicle and ServiceCatalog packages and developing
  RF03–RF08 in their selected physical location. AD-003 and AD-004 assume `customer` is the physical home of the
  broader Cadastros context; only the team can validate that premise. Implementing before confirmation could force
  package moves, change Spring Modulith boundaries and invalidate public contracts. AD-001 does **not** block Jira
  planning, Customer-local RF01/RF02 work, or defining domain-level acceptance criteria.

**Readiness conclusion:** Ivan's scope is sufficiently defined for Jira planning. It is partially ready for
implementation: Customer-local work may proceed, while Vehicle and ServiceCatalog code must wait for AD-001.

## Team Decisions

The following decisions must not be made by Ivan alone:

| Decision | Why it belongs to the team/other owner | Can Ivan continue? |
|---|---|---|
| AD-001 | Defines every bounded-context/module mapping | Jira planning and Customer-local work can continue; Vehicle/ServiceCatalog code cannot |
| AD-007 | Owns Epic 4 aggregate/transaction boundary | Yes |
| AD-009 | Couples Epic 2 pricing to Epic 4 stock | Yes |
| AD-011 | Establishes all inter-module contracts | Yes with mocks |
| AD-013 | Owns Estimate business time and scheduling | Yes |
| AD-014 | Owns Notification module/channel | Yes |
| AD-017 | Establishes shared database change policy | Yes briefly |
| AD-018 | Changes external-integration/delivery scope | Yes |
| AD-019 | Defines an official cross-cutting business metric | Yes |

AD-012 is shared but intentionally deferred; it does not need immediate team resolution while event contracts are
only being designed and mocked.

## Implementation Gaps

These items do not need a new architecture decision once their related decision, if any, is resolved:

- Implement Vehicle CRUD/validation after AD-001/AD-003.
- Implement ServiceCatalog registration and price update after AD-001/AD-004.
- Add ServiceOrder list endpoint and administrative filters.
- Implement Estimate code under the resolved AD-008 per-line/`draft`-`sent`-`closed`-`expired` model, after
  AD-009/AD-013.
- Implement PurchaseOrder and stock reservation after AD-007.
- Implement Notification handlers after AD-014.
- Add Spring Security/JWT dependencies and filters after AD-016; the technology choice itself is already accepted.
- Add Swagger/OpenAPI documentation to existing REST APIs.
- Add use-case, controller, persistence and end-to-end integration tests.
- Configure JaCoCo and enforce the official 80% critical-domain threshold in active CI.
- Enable/fix CI; the current workflow is fully commented out.
- Complete `README.md` with local execution and project objectives.
- Run and document the required vulnerability scan.
- Correct Docker documentation examples after comparing them with actual dependencies/endpoints.
- Add the average-time query/read model after AD-019.

## Documentation Gaps

- Mark the old whole-Estimate approval and on-read status documents as superseded (AD-008 resolved in favor of
  per-line approval with `draft`/`sent`/`closed`/`expired` Estimate states; AD-010 resolved in favor of persisted
  `statusSnapshot`, recomputed on command).
- Align C4 bounded-context names with the eventual AD-001 mapping.
- Remove or mark Payment Gateway as future/unconfirmed after AD-018.
- Add External Supplier System to the relevant C4 view if retained.
- Give the polling ADR an explicit status after AD-015.
- Document exact public ports, REST contracts and event payloads after AD-011/AD-012.
- Add Vehicle and ServiceCatalog to `PROJECT-STRUCTURE.md` only after team-owned AD-001 validates the conditional
  AD-003/AD-004 placement; other missing aggregates remain subject to their owners' decisions.
- Resolve the official private/public repository wording with FIAP/course guidance.
- Correct `DOCKER.md` references to `.env.example`, Actuator and a nonexistent ServiceOrder list endpoint.
- Keep `Architecture.md` traceability updated as decisions move from Pending to Resolved and code is implemented.

## AGENTS.MD Findings

The existing file is exactly `AGENTS.md` at the repository root. No duplicate file should be created.

### Synchronization result

- Preserves the valid current Spring Modulith, layered/hexagonal, DTO, repository and boundary-test guidance.
- Defines source precedence and distinguishes resolved, planned and implemented architecture.
- Adds traceable rules for AD-002 and AD-005 and conditional guardrails for AD-003/AD-004.
- Explicitly prevents new top-level modules or Vehicle/ServiceCatalog packages until AD-001 is resolved.
- Removes stale Java 17/dependency samples, obsolete state-machine examples, Manager approval, assumed polling
  cache, unsupported PostgreSQL readiness and nonexistent model shapes.
- States current Java 21/dependency/implementation evidence without presenting missing capabilities as complete.
- Keeps unresolved integration, events, security ownership, migrations and other-owner decisions out of operational
  rules.

### Remaining guidance dependencies

- Add the final Miro-context-to-package mapping only after AD-001.
- Add definitive cross-module and event-delivery rules only after AD-011/AD-012.
- Add final authorization, tracking and migration rules only after AD-015/AD-016/AD-017.

## Consistency Check After Synchronization

- `Architecture.md` now distinguishes the four Ivan-approved decisions from current implementation and from
  team-owned dependencies.
- `PROJECT-STRUCTURE.md` continues to omit Vehicle/ServiceCatalog packages because adding them under `customer` would
  assert the unresolved AD-001 mapping, even though AD-003/AD-004 selected that destination conditionally.
- `AGENTS.md` permits AD-002 and AD-005 implementation guidance but prevents activating AD-003/AD-004 before
  AD-001. It no longer presents pending team recommendations as accepted architecture.
- The current repository contains `TaxId` and logical archival for Customer. Vehicle and ServiceCatalog remain planned
  and their lifecycle implementations are still pending.
- No contradiction was introduced between the approved decisions and Miro's Cadastros aggregates/snapshot rules.
  The unresolved contradiction is physical module naming/ownership (`customer` versus Cadastros), tracked by AD-001.
- Existing unrelated contradictions—Technician ownership, Stock aggregate shape, Estimate decisions, internal
  integration style, notification channel, tracking strategy, authorization ownership and migration policy—remain
  recorded and unresolved under their respective team/other-owner entries.

## Register Summary

- **Actual architectural decisions identified:** 19.
- **Ivan / my assigned scope:** 4 (AD-002 through AD-005).
- **Explicitly approved by Ivan:** 4 (Option A for AD-002 through AD-005).
- **Team Decision Required:** 9.
- **Currently blocking Ivan's Jira planning:** 0.
- **Currently blocking part of Ivan's implementation:** 1 (AD-001), which gates the conditionally resolved AD-003
  and AD-004.
- **Resolved:** 9 (AD-002, AD-003, AD-004 and AD-005, approved by Ivan; AD-006, AD-008 and AD-015, ratified by the
  team on 23 August 2026; AD-010, ratified by the team on 24 August 2026; AD-016, ratified by the team on
  24 August 2026).
- **Deferred:** 1 (AD-012).

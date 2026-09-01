# Project Structure

The application is a Spring Modulith modular monolith. Its module boundaries follow the
[Miro Context Map](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679674975757).

## Modules

```text
br.com.fiap.workshop_management_system
├── registration
│   ├── customer                 # Implemented aggregate
│   ├── vehicle                  # Registration, queries, lifecycle and monotonic mileage implemented
│   └── servicecatalog           # Catalog CRUD, logical archive and active queries implemented
├── servicelifecycle
│   ├── serviceorder             # Implemented aggregate
│   ├── estimate                 # Planned aggregate
│   └── technician               # Supporting capability for assignment/execution
└── stockprocurement
    ├── stock                    # Implemented StockItem catalog
    └── purchaseorder            # Planned aggregate
```

Only `registration`, `servicelifecycle` and `stockprocurement` are application modules. Nested packages are internal
parts of their owning bounded context.

## Context responsibilities

| Context | Responsibilities | Current state |
|---|---|---|
| Registrations | Identify and register customers and vehicles; maintain the service catalog | Customer, Vehicle lifecycle/mileage and Service Catalog management implemented |
| Service Lifecycle | Create, diagnose, estimate, authorize and execute service orders | Service Order and Technician implemented |
| Stock & Procurement | Maintain the StockItem catalog; inventory, reservations and procurement are future work | StockItem catalog implemented |

Registrations provides stable customer/vehicle identities and catalog-service eligibility to Service Lifecycle. A new
Diagnosis accepts only active Catalog Services; the availability check and Diagnosis commit share the consumer transaction
so an archive cannot invalidate work while it is being registered. Service Lifecycle stores snapshots where historical
service-order data must not change with later registration edits or catalog archives. Stock & Procurement owns inventory
and future purchase orders; Service Lifecycle refers to stock items by ID and snapshots. A future supplier integration
belongs behind an anti-corruption layer owned by Stock & Procurement.

Vehicle queries distinguish historical and operational views: lookup by ID includes archived records, while the list
returns active records only. Archiving is logical, irreversible and idempotent. Before creating a new Service Order,
Service Lifecycle checks the referenced Vehicle through its consumer-owned `VehicleEligibilityPort`; the in-process
adapter calls the minimal `registration.vehicle.application.api` named interface. That public API locks the Vehicle row
inside the consumer transaction, preventing a new order from racing with archive. It validates only existence and active
state: the request snapshot remains historical input and is not reconciled with registration ownership or descriptive data.

Notifications is not a bounded context (see `docs/adr/ADR-004-notifications-boundary.md`): a module that needs to notify
someone defines a consumer-owned outbound port in its own `application` layer and an adapter in its own
`infrastructure` layer. Service Lifecycle's Service-Order-finalized notification reads Customer contact data live from
Registrations through `CustomerRepository`, published via `@NamedInterface` on
`registration.customer.domain.repository` and `registration.customer.domain.model`. Vehicle eligibility uses the narrow
`registration.vehicle.application.api` named interface and does not expose Vehicle domain or persistence types. The
Service Order capability also owns `CatalogServiceEligibilityPort`; its Registration adapter calls the producer-owned
`registration.servicecatalog.application.api` named interface without importing internal catalog packages.

## Internal layers

Each implemented aggregate follows:

- `domain/model`: framework-free aggregate, entities and value objects;
- `domain/repository`: persistence contracts;
- `application/usecase`: orchestration and transaction boundaries;
- `application/api`: minimal public contracts explicitly exposed as named interfaces when another module needs them;
- `application/dto`: external request/response contracts;
- `infrastructure/persistence`: JPA projection, mapper and repository adapter;
- `infrastructure/web`: REST controllers;
- `infrastructure/bootstrap`: optional development-only seeders.

Cross-module imports into internal packages are forbidden. `ModuleStructureTest` runs `ApplicationModules.verify()` and
also asserts the exact module set.

## Supporting project structure

- `docs/features/`: functional specs, technical specs and implementation plans organized by feature.
- `docs/api/postman/`: versioned Postman collection.
- `src/main/resources/db/migration/`: Flyway schema/reference-data migrations.
- `scripts/`: container bootstrap and operational scripts only.
- `AGENTS.md`: concise development and completion rules.

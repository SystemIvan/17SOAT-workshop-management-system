# Project Structure

The application is a Spring Modulith modular monolith. Its module boundaries follow the
[Miro Context Map](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679674975757).

## Modules

```text
br.com.fiap.workshop_management_system
├── registration
│   ├── customer                 # Implemented aggregate
│   ├── vehicle                  # Planned aggregate
│   └── servicecatalog           # Planned aggregate
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
| Registrations | Identify and register customers and vehicles; maintain the service catalog | Customer implemented |
| Service Lifecycle | Create, diagnose, estimate, authorize and execute service orders | Service Order and Technician implemented |
| Stock & Procurement | Maintain the StockItem catalog; inventory, reservations and procurement are future work | StockItem catalog implemented |

Registrations provides stable customer/vehicle identities to Service Lifecycle. Service Lifecycle stores snapshots where
historical service-order data must not change with later registration edits. Stock & Procurement owns inventory and future
purchase orders; Service Lifecycle refers to stock items by ID and snapshots. A future supplier integration belongs behind
an anti-corruption layer owned by Stock & Procurement.

Notifications is not a bounded context (see `docs/ADR-003-notifications-boundary.md`): a module that needs to notify
someone defines a consumer-owned outbound port in its own `application` layer and an adapter in its own
`infrastructure` layer. As the first case of this, Service Lifecycle's Service-Order-finalized notification reads
Customer contact data live from Registrations through `CustomerRepository`, published via `@NamedInterface` on
`registration.customer.domain.repository` and `registration.customer.domain.model` — the only Registrations
sub-packages currently exposed to other modules.

## Internal layers

Each implemented aggregate follows:

- `domain/model`: framework-free aggregate, entities and value objects;
- `domain/repository`: persistence contracts;
- `application/usecase`: orchestration and transaction boundaries;
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

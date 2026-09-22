# Especificação Técnica: Ordenação operacional e exclusão lógica na listagem de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders-sorting` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-19 |
| Especificação funcional | `docs/features/servicelifecycle/list-service-orders-sorting/functional-spec.md` (`Approved` em 2026-09-19) |

## Objetivo técnico

`GET /api/service-orders` (`ListServiceOrdersUseCase` → `ServiceOrderRepositoryImpl.search`) já existe e
não muda de contrato (mesmos query params, mesmo payload). Esta feature adiciona, por cima do
comportamento já aprovado: (1) exclusão lógica de `COMPLETED`/`DELIVERED` quando `status` não é informado,
e (2) ordenação por prioridade operacional de status + antiguidade. O achado principal, já registrado na
`functional-spec.md`, é que (2) exige um campo `createdAt` que não existe hoje em `ServiceOrder` nem em
`service_orders` — esta spec desenha esse campo novo e sua introdução com o menor raio de impacto possível
sobre os 25 call sites existentes de `ServiceOrder.create(...)` e o único call site de `reconstitute(...)`.

## Contexto e desenho

Implementação inteira em `servicelifecycle.serviceorder`. Nenhuma importação de pacote interno de outro
módulo é necessária; nenhuma mudança de contrato de evento de domínio.

### Decisão de design: `createdAt` via overloads compatíveis, não parâmetro obrigatório em `create`

`ServiceOrder.create(...)` tem 25 call sites no repositório (produção + fixtures de teste); `reconstitute`
tem apenas 1 (`ServiceOrderPersistenceMapper.toDomain`). Seguindo o mesmo raciocínio já usado em
`list-service-orders` para o método `search` (ver "Decisão de design" daquela `technical-spec.md`): tornar
`createdAt` um parâmetro obrigatório nas duas sobrecargas existentes de `create` quebraria a compilação dos
25 call sites só para um campo que a maioria deles não precisa controlar. Em vez disso:

- As duas sobrecargas públicas de `create(...)` já existentes continuam exatamente com a mesma assinatura e
  passam a delegar para uma terceira sobrecarga privada/nova que recebe `Instant createdAt` e usa
  `Instant.now()` como valor — nenhum call site existente muda.
- Uma nova sobrecarga pública `create(..., Instant createdAt)` é adicionada para quem precisa de controle
  explícito do timestamp: `CreateServiceOrderUseCase`, seguindo o padrão de `Clock` já usado em
  `StartExecutionUseCase`/`PerformDiagnosisUseCase` (campo `Clock clock`, construtor público com
  `Clock.systemUTC()`, construtor package-private para injeção de `Clock` fixo em teste).
- `reconstitute(...)` ganha `Instant createdAt` como parâmetro obrigatório (sem overload) — só 1 call site
  de produção a ajustar (`ServiceOrderPersistenceMapper`); é dado restaurado de estado persistido, não faz
  sentido ter um valor default aqui.

### Decisão de design: ranking de status como método de domínio, ordenação aplicada em memória

O ranking de status (regra de negócio 1 da `functional-spec.md`) é uma regra de negócio genuína (prioridade
operacional), não um detalhe de apresentação HTTP — por isso vira um método puro em
`ServiceOrderStatus` (`listingRank()`), sem dependência de Spring/JPA. A ordenação final (por `listingRank`
e depois por `createdAt`) é aplicada em memória sobre a `List<ServiceOrder>` já retornada por
`jpaRepository.findAll(specification)`, não via `ORDER BY` no banco:

- Evita expressar `CASE WHEN status = ... THEN 1 ...` em Criteria API só para um `ORDER BY` — mais difícil
  de ler e testar do que um `Comparator` Java.
- O projeto já decidiu não paginar `/api/service-orders` (`list-service-orders`, "Fora de escopo"); o
  volume de dados de um MVP não justifica mover a ordenação para o banco.
- Mantém a ordenação testável isoladamente contra `ServiceOrderStatus.listingRank()` sem precisar de um
  banco real.

## Estrutura proposta

- `serviceorder/domain/model/ServiceOrderStatus.java` — adicionar método `listingRank()` (alteração).
- `serviceorder/domain/model/ServiceOrder.java` — adicionar campo `createdAt`, nova sobrecarga de `create`,
  `reconstitute` com `createdAt` obrigatório, getter `createdAt()` (alteração).
- `serviceorder/application/usecase/CreateServiceOrderUseCase.java` — injetar `Clock`, passar
  `Instant.now(clock)` para a nova sobrecarga de `create` (alteração).
- `serviceorder/infrastructure/persistence/ServiceOrderJpaEntity.java` — adicionar campo `createdAt`
  (alteração).
- `serviceorder/infrastructure/persistence/ServiceOrderPersistenceMapper.java` — propagar `createdAt` nos
  dois sentidos (alteração).
- `serviceorder/infrastructure/persistence/ServiceOrderRepositoryImpl.java` — exclusão lógica por padrão no
  `Specification` + ordenação em memória do resultado (alteração).
- `src/main/resources/db/migration/V<timestamp>__add_created_at_to_service_orders.sql` (novo).
- `src/test/java/.../application/usecase/ListServiceOrdersUseCaseTest.java` — novos casos de ordenação e
  exclusão (alteração do teste já existente de `list-service-orders`).
- `src/test/java/.../infrastructure/web/ServiceOrderControllerListTest.java` — novos casos HTTP (alteração
  do teste já existente).
- Nenhuma mudança em OpenAPI/Postman/`OpenApiContractTest` — o endpoint e seus query params não mudam,
  só o comportamento de exclusão/ordenação, que não é uma anotação de contrato HTTP nova.

## Domínio

### `ServiceOrderStatus` — novo método `listingRank()`

```java
public int listingRank() {
    return switch (this) {
        case IN_PROGRESS -> 1;
        case AWAITING_APPROVAL -> 2;
        case IN_DIAGNOSIS -> 3;
        case RECEIVED -> 4;
        case AWAITING_ITEMS -> 5;
        case COMPLETED -> 6;
        case DELIVERED -> 7;
    };
}
```

Números literais, não `ordinal()` — a ordem de declaração do enum não deve acoplar a ordem de listagem
(mudar a ordem de declaração do enum, por qualquer outro motivo, não pode silenciosamente mudar esta regra
de negócio).

### `ServiceOrder` — campo `createdAt` e sobrecargas de `create`

```java
private final Instant createdAt;

public static ServiceOrder create(
        UUID customerId, UUID vehicleId, VehicleSnapshot vehicleSnapshot, String initialAssessment) {
    return create(customerId, vehicleId, vehicleSnapshot, Priority.NORMAL, initialAssessment, Instant.now());
}

public static ServiceOrder create(
        UUID customerId,
        UUID vehicleId,
        VehicleSnapshot vehicleSnapshot,
        Priority priority,
        String initialAssessment) {
    return create(customerId, vehicleId, vehicleSnapshot, priority, initialAssessment, Instant.now());
}

public static ServiceOrder create(
        UUID customerId,
        UUID vehicleId,
        VehicleSnapshot vehicleSnapshot,
        Priority priority,
        String initialAssessment,
        Instant createdAt) {
    ServiceOrder serviceOrder = new ServiceOrder(
            UUID.randomUUID(),
            customerId,
            vehicleId,
            vehicleSnapshot,
            priority,
            requireInitialAssessment(initialAssessment),
            Objects.requireNonNull(createdAt, "createdAt must not be null"));
    serviceOrder.statusSnapshot = ServiceOrderStatus.RECEIVED;
    return serviceOrder;
}
```

O construtor privado ganha o parâmetro `createdAt` e o atribui a `this.createdAt`. `reconstitute(...)`
ganha `Instant createdAt` na assinatura (sem overload) e o repassa ao construtor privado da mesma forma.
Getter novo:

```java
public Instant createdAt() {
    return createdAt;
}
```

### `CreateServiceOrderUseCase` — `Clock` injetado (mesmo padrão de `StartExecutionUseCase`)

```java
private final Clock clock;

public CreateServiceOrderUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository,
        TechnicianNotificationPort technicianNotificationPort, VehicleEligibilityPort vehicleEligibilityPort) {
    this(repository, technicianRepository, technicianNotificationPort, vehicleEligibilityPort, Clock.systemUTC());
}

CreateServiceOrderUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository,
        TechnicianNotificationPort technicianNotificationPort, VehicleEligibilityPort vehicleEligibilityPort,
        Clock clock) {
    this.repository = repository;
    this.technicianRepository = technicianRepository;
    this.technicianNotificationPort = technicianNotificationPort;
    this.vehicleEligibilityPort = vehicleEligibilityPort;
    this.clock = clock;
}
```

E em `execute(...)`:

```java
ServiceOrder serviceOrder = ServiceOrder.create(
        request.customerId(), request.vehicleId(), vehicleSnapshot, priority, request.initialAssessment(),
        Instant.now(clock));
```

## Repository — exclusão lógica e ordenação

```java
@Override
public List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
    Specification<ServiceOrderJpaEntity> specification = (root, query, builder) -> {
        query.distinct(true);
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.status() != null) {
            predicates.add(builder.equal(root.get("statusSnapshot"), criteria.status()));
        } else {
            predicates.add(root.get("statusSnapshot").in(
                    ServiceOrderStatus.COMPLETED, ServiceOrderStatus.DELIVERED).not());
        }
        // ... customerId/priority/technicianId inalterados
        return builder.and(predicates.toArray(Predicate[]::new));
    };
    List<ServiceOrder> results = jpaRepository.findAll(specification).stream().map(mapper::toDomain).toList();
    return results.stream()
            .sorted(Comparator.<ServiceOrder>comparingInt(so -> so.status().listingRank())
                    .thenComparing(ServiceOrder::createdAt))
            .toList();
}
```

Pontos relevantes:

- A exclusão lógica entra como mais um predicado do `Specification` (filtrada no banco, não em memória) —
  só a ordenação é feita em Java, pelo motivo já explicado em "Decisão de design" acima.
- Quando `criteria.status()` é informado (qualquer valor, incluindo `COMPLETED`/`DELIVERED`), o predicado
  de exclusão não entra — comportamento idêntico ao já existente, só filtrando por igualdade.
- `ServiceOrderStatus.COMPLETED`/`DELIVERED` como constantes explícitas no `.in(...).not()` — nenhum "status
  ativo" implícito calculado por exclusão de uma lista mutável em outro lugar; se um novo status for
  adicionado ao enum no futuro, ele é incluído na listagem padrão por default (fail-open para visibilidade
  operacional, não fail-closed escondendo status desconhecido).

## Interfaces e fluxo de dados

Nenhuma mudança de assinatura HTTP: `GET /api/service-orders?status=...&customerId=...&technicianId=...&priority=...`
continua igual. A diferença é só no corpo da resposta (quais itens aparecem, em que ordem) para a mesma
requisição. Nenhuma mudança em `ServiceOrderController`, `ServiceOrderResponse` ou `ServiceOrderMapper`.

## Tratamento de erros

Nenhuma mudança: os mesmos casos de erro de `list-service-orders` (`400 VALIDATION_ERROR` para `status`/
`priority` fora do enum ou UUIDs inválidos) continuam valendo, sem alteração no `GlobalExceptionHandler`.

## Persistência e dados de bootstrap

Migration nova, classificada como **mudança de schema** (não seed, `AGENTS.md` §"Persistent data and
seeds"):

`src/main/resources/db/migration/V<timestamp>__add_created_at_to_service_orders.sql`

```sql
ALTER TABLE service_orders ADD COLUMN created_at TIMESTAMP(6) NULL;

UPDATE service_orders SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;

ALTER TABLE service_orders MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL;

CREATE INDEX idx_service_orders_status_created_at ON service_orders (status_snapshot, created_at);
```

**Estratégia de backfill** (decisão registrada, resolvendo o ponto deixado em aberto na
`functional-spec.md`): ordens de serviço já persistidas antes desta migração recebem
`CURRENT_TIMESTAMP(6)` (o instante em que a migração roda) como `created_at` — não há como recuperar a
data de criação real retroativamente, já que o campo nunca existiu. Isso é aceitável para o MVP: o efeito
prático é que todas as OS pré-existentes empatam no timestamp de backfill e a ordenação por antiguidade
entre elas fica indefinida (estável por PK, não por data real) até que novas OS sejam criadas depois da
migração — não há requisito de negócio pedindo ordenação retroativa correta para dados históricos.

`ServiceOrderJpaEntity` ganha `private Instant createdAt;` (sem `@Column` explícito — mesmo padrão sem
anotação já usado para `diagnosedAt`/`startedAt`/`completedAt` em `ServiceExecutionJpaEntity`, resolvido
por Hibernate para `created_at` via a naming strategy padrão do projeto), mais o parâmetro correspondente
no construtor e o getter `getCreatedAt()`. `ServiceOrderPersistenceMapper.toEntity`/`toDomain` propagam o
campo nos dois sentidos.

Nenhum dado de referência obrigatória, seed de desenvolvimento ou fixture de teste é introduzido — é
puramente schema + backfill operacional.

## Segurança e operação

- Nenhuma mudança de autorização: mesma regra já existente em `SecurityConfig` para
  `/api/service-orders/**`.
- Nenhum dado sensível novo exposto: `createdAt` não é adicionado ao payload `ServiceOrderResponse` nesta
  feature (confirmado como fora de escopo na `functional-spec.md`) — é usado só internamente para ordenar.
- Rollout: a migration é aditiva (nova coluna `NULL` → backfill → `NOT NULL`), compatível com o deploy
  padrão do projeto (Flyway roda antes da aplicação servir tráfego); nenhuma janela de indisponibilidade
  adicional além do já existente para qualquer migration.
- Recuperação: se a migration falhar no meio (ex. instância derruba entre o `ADD COLUMN` e o
  `MODIFY COLUMN NOT NULL`), Flyway marca a migration como falha e bloqueia o próximo boot até correção
  manual — mesmo comportamento padrão já aceito pelo projeto para qualquer migration multi-statement.
- Nenhuma superfície de abuso nova: é uma mudança de ordenação/filtro de uma consulta somente-leitura já
  existente.

## Estratégia de testes

### Domínio (novo — `ServiceOrderStatusTest`, alteração)

- `listingRank()` retorna a ordem esperada para os 7 valores (teste parametrizado ou tabela literal:
  `IN_PROGRESS` < `AWAITING_APPROVAL` < `IN_DIAGNOSIS` < `RECEIVED` < `AWAITING_ITEMS` < `COMPLETED` <
  `DELIVERED`).

### Domínio (alteração — `ServiceOrderTest`, se existir, ou teste novo)

- `ServiceOrder.create(...)` sem `createdAt` explícito preenche `createdAt()` com um valor não nulo
  (aproximadamente "agora").
- A sobrecarga com `createdAt` explícito preserva exatamente o valor passado (usada para os testes de
  ordenação abaixo, que precisam de datas determinísticas).

### Aplicação (alteração — `CreateServiceOrderUseCaseTest`)

- Com um `Clock` fixo injetado (construtor package-private), `execute(...)` produz uma `ServiceOrder` cujo
  `createdAt()` é exatamente o instante do `Clock` — mesmo padrão de asserção já usado em
  `StartExecutionUseCaseTest`/`PerformDiagnosisUseCaseTest` para `startedAt`/`diagnosedAt`.

### Aplicação (alteração — `ListServiceOrdersUseCaseTest`)

Usando o fake `ServiceOrderRepository` já existente (que passa a sobrescrever `search` retornando os dados
já ordenados/filtrados, simulando o `ServiceOrderRepositoryImpl` real):

- `execute` sem filtro de `status` não retorna OS `COMPLETED`/`DELIVERED` (teste de orquestração — a regra
  em si é validada contra banco real no teste HTTP abaixo, este teste só confirma que o use case não filtra
  de novo por cima do que o repositório já devolve).

### Web (alteração — `ServiceOrderControllerListTest`, `@SpringBootTest` com banco real)

Novos cenários, além dos já existentes de `list-service-orders`:

- Popular OS em `IN_PROGRESS`, `AWAITING_APPROVAL`, `IN_DIAGNOSIS`, `RECEIVED`, `AWAITING_ITEMS`,
  `COMPLETED` e `DELIVERED` (reaproveitando os use cases já existentes —
  `AssignTechnicianUseCase`/`StartExecutionUseCase`/`CompleteExecutionUseCase`/`FinalizeServiceOrderUseCase`
  — para levar cada uma ao status desejado, mesmo padrão de setup de `ServiceOrderControllerAssignTechnicianTest`).
- `GET /api/service-orders` sem `status`: `200` sem `COMPLETED`/`DELIVERED`, na ordem
  `IN_PROGRESS, AWAITING_APPROVAL, IN_DIAGNOSIS, RECEIVED, AWAITING_ITEMS`.
- `GET /api/service-orders?status=COMPLETED`: `200` retornando as `COMPLETED` (exclusão desativada pelo
  filtro explícito).
- `GET /api/service-orders?status=DELIVERED`: idem para `DELIVERED`.
- Duas OS `RECEIVED` criadas em sequência (com pelo menos alguns milissegundos de diferença garantidos, ou
  criadas com `createdAt` explícito via acesso direto ao repositório de teste, se necessário evitar
  flakiness): a mais antiga aparece primeiro.
- Regressão: os filtros combinados (`status`+`priority`, `customerId`, `technicianId`) continuam aplicando
  AND como antes.

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova entre módulos.

## Contratos e documentação

- OpenAPI/Postman: nenhuma mudança — o contrato HTTP (params, formato de resposta) é idêntico ao já
  documentado para `list-service-orders`. Nenhuma alteração em `OpenApiContractTest` é necessária.
- Se a descrição do endpoint (`@Operation(summary = ...)`) merece um ajuste textual mencionando a exclusão/
  ordenação por padrão, isso é opcional e não bloqueia a feature — decisão de implementação, não de
  contrato.

## Fora de escopo técnico

- Expor `createdAt` em `ServiceOrderResponse` — confirmado fora de escopo na `functional-spec.md`.
- Mover a ordenação para o banco (`ORDER BY` via Criteria/JPQL) — decisão explícita de manter em memória
  (ver "Decisão de design").
- Qualquer parâmetro de ordenação customizável pelo cliente da API.
- Qualquer mudança em `GET /api/service-orders/{id}` ou `GET /api/service-orders/{id}/status`.
- O mapeamento de `AWAITING_ITEMS` (e dos demais status) para os 6 nomes nominais do enunciado — RF39, spec
  própria; `listingRank()` é só ordenação interna, não nome exposto.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada (2026-09-19).
- [x] Technical Spec revisada e aprovada (2026-09-19).

Antes do PR:

- [ ] testes unitários passando (`ServiceOrderStatusTest`, `ServiceOrderTest`,
      `CreateServiceOrderUseCaseTest`, `ListServiceOrdersUseCaseTest`);
- [ ] teste de integração HTTP passando (`ServiceOrderControllerListTest` contra o banco de teste);
- [ ] `make verify` passando;
- [ ] migration nova aplicada e revisada (`V<timestamp>__add_created_at_to_service_orders.sql`);
- [ ] nenhuma mudança de OpenAPI/Postman necessária (contrato HTTP inalterado);
- [ ] nenhuma fronteira do Spring Modulith violada (`ModuleStructureTest` verde);
- [ ] revisão de segurança registrada (ver seção "Segurança e operação" acima).

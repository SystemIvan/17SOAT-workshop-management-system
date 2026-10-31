# Plano de Implementação: Ordenação operacional e exclusão lógica na listagem de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders-sorting` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-19 |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-09-19) |

## Objetivo

Por cima de `GET /api/service-orders` (já implementado, contrato inalterado): (1) excluir por padrão
`COMPLETED`/`DELIVERED` quando `status` não é informado, (2) ordenar por prioridade operacional de status
(`IN_PROGRESS` > `AWAITING_APPROVAL` > `IN_DIAGNOSIS` > `RECEIVED` > `AWAITING_ITEMS` > `COMPLETED` >
`DELIVERED`) e, dentro do mesmo status, por antiguidade (`createdAt`, campo novo).

## Checkpoint 1 — `ServiceOrderStatus.listingRank()`

- Adicionar `listingRank()` a `ServiceOrderStatus` (switch explícito, não `ordinal()`).
- Teste de domínio (`ServiceOrderStatusTest`, novo): os 7 valores retornam o rank esperado, com a ordem
  relativa correta entre eles.

## Checkpoint 2 — `createdAt` no domínio `ServiceOrder`

- Adicionar campo `createdAt` (`Instant`, final) e getter `createdAt()`.
- Nova sobrecarga `create(..., Instant createdAt)`; as duas sobrecargas existentes passam a delegar para
  ela com `Instant.now()` — nenhum dos 25 call sites existentes muda.
- `reconstitute(...)` ganha `Instant createdAt` obrigatório (único call site: `ServiceOrderPersistenceMapper`).
- Teste de domínio (`ServiceOrderTest`, alteração ou novo): `create` sem `createdAt` explícito preenche um
  valor não nulo; a sobrecarga explícita preserva exatamente o valor passado.

## Checkpoint 3 — Persistência: migration + `ServiceOrderJpaEntity` + mapper

- Migration `V<timestamp>__add_created_at_to_service_orders.sql`: `ADD COLUMN created_at TIMESTAMP(6) NULL`
  → `UPDATE ... SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL` → `MODIFY COLUMN ... NOT
  NULL` → índice `idx_service_orders_status_created_at (status_snapshot, created_at)`.
- `ServiceOrderJpaEntity`: campo `createdAt` (sem `@Column` explícito, mesmo padrão de
  `diagnosedAt`/`startedAt`/`completedAt`), parâmetro no construtor, getter.
- `ServiceOrderPersistenceMapper`: propagar `createdAt` em `toEntity`/`toDomain`.
- Verificação: aplicação sobe localmente contra o banco de teste com a migration nova sem erro
  (`./mvnw test` já cobre isso via Flyway no contexto de teste).

## Checkpoint 4 — `CreateServiceOrderUseCase` com `Clock`

- Injetar `Clock clock` (construtor público com `Clock.systemUTC()`, construtor package-private para
  injeção em teste — mesmo padrão de `StartExecutionUseCase`).
- `execute(...)` passa `Instant.now(clock)` para a nova sobrecarga de `create`.
- Teste (`CreateServiceOrderUseCaseTest`, alteração): com `Clock` fixo injetado, `createdAt()` da
  `ServiceOrder` resultante é exatamente o instante do `Clock`.

## Checkpoint 5 — `ServiceOrderRepositoryImpl.search`: exclusão + ordenação

- Predicado de exclusão lógica: quando `criteria.status() == null`, adicionar
  `root.get("statusSnapshot").in(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.DELIVERED).not()`.
- Após `findAll(specification)` + mapeamento para domínio: ordenar com
  `Comparator.comparingInt(so -> so.status().listingRank()).thenComparing(ServiceOrder::createdAt)`.
- Sem teste de repositório isolado (mesmo padrão de `list-service-orders`: a lógica de filtro/ordenação é
  validada contra banco real no teste HTTP do checkpoint 6).

## Checkpoint 6 — Testes HTTP de ponta a ponta

`ServiceOrderControllerListTest` (alteração), populando OS em todos os 7 status via os use cases já
existentes (`AssignTechnicianUseCase`, `StartExecutionUseCase`, `CompleteExecutionUseCase`,
`FinalizeServiceOrderUseCase`):

- sem `status`: `200`, sem `COMPLETED`/`DELIVERED`, na ordem
  `IN_PROGRESS, AWAITING_APPROVAL, IN_DIAGNOSIS, RECEIVED, AWAITING_ITEMS`;
- `?status=COMPLETED` e `?status=DELIVERED`: retornam normalmente (exclusão desativada pelo filtro
  explícito);
- duas OS no mesmo status: a mais antiga (`createdAt` menor) aparece primeiro;
- regressão: filtros combinados (`status`+`priority`, `customerId`, `technicianId`) continuam com AND.

## Checkpoint 7 — Validação final

Executar:
- `./mvnw test -Dtest=ServiceOrderStatusTest,ServiceOrderTest,CreateServiceOrderUseCaseTest,ListServiceOrdersUseCaseTest,ServiceOrderControllerListTest`;
- `./mvnw test` (suíte completa, checar ausência de regressão — especialmente qualquer teste que dependa
  da ordem/quantidade de itens de `GET /api/service-orders`);
- `./mvnw verify` (`make verify`);
- `./mvnw test -Dtest=ModuleStructureTest`.

Revisar:
- nenhuma mudança de contrato HTTP (OpenAPI/Postman não precisam de atualização, conforme
  `technical-spec.md`);
- nenhuma fronteira do Spring Modulith violada.

## Definition of Done

- [x] `ServiceOrderStatus.listingRank()` implementado e testado.
- [x] `ServiceOrder.createdAt` implementado (novas sobrecargas, `reconstitute` atualizado) e testado.
- [x] Migration `created_at` aplicada, `ServiceOrderJpaEntity`/mapper atualizados.
- [x] `CreateServiceOrderUseCase` com `Clock` injetado, testado.
- [x] `ServiceOrderRepositoryImpl.search` com exclusão lógica + ordenação.
- [x] Testes HTTP cobrindo exclusão, ordenação de status e antiguidade, sem regressão nos filtros
      existentes.
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada.
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: nenhum parâmetro novo; os filtros existentes (`status`, `customerId`,
  `technicianId`, `priority`) continuam validados exatamente como antes. OK.
- **Autenticação/autorização**: nenhuma mudança — mesma regra já existente em `SecurityConfig` para
  `/api/service-orders/**`. OK.
- **Exposição de dados**: `createdAt` não foi adicionado a `ServiceOrderResponse` — confirmado que nenhum
  dado novo é exposto ao cliente HTTP; o campo é usado só internamente para ordenar. OK.
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo introduzido. OK.
- **SQL/persistência/migration**: migration aditiva (`ADD COLUMN` → backfill `UPDATE` → `MODIFY ... NOT
  NULL` → `CREATE INDEX`), sem concatenação de entrada do usuário (nenhum parâmetro de request entra na
  migration). Rodada com sucesso contra o banco de teste (H2 modo MySQL) via Flyway em `./mvnw verify`,
  sem deixar nenhuma linha com `created_at` nulo (a suíte completa, incluindo testes que inserem
  `ServiceOrder`s via HTTP e via SQL bruto de teste — corrigidos neste checkpoint, ver abaixo — passou sem
  violação de `NOT NULL`). OK.
- **Erros e disclosure**: nenhum código de erro novo; nenhum comportamento de erro mudou. OK.
- **Dependências novas**: nenhuma. OK.
- **Abuso**: nenhuma superfície nova — mesma consulta somente-leitura já existente, agora com um
  `Comparator` em memória sobre o resultado já filtrado pelo banco; volume aceitável para o MVP (mesma
  decisão já registrada em `list-service-orders`).
- **Achado corrigido durante a implementação**: 3 testes que inseriam `ServiceOrder`s via SQL bruto
  (`JdbcTemplate`, bypassando o mapper JPA) quebraram com `NULL not allowed for column "CREATED_AT"`
  depois que a coluna virou `NOT NULL` — `ServiceExecutionTimeMetricsQueryAdapterTest` e
  `ServiceOrderControllerAverageExecutionTimeTest`. Corrigido adicionando `created_at` ao `INSERT` de
  teste com `Instant.now()`. Os outros 3 arquivos de teste com `INSERT` bruto em `service_orders`
  (`ServiceExecutionTimestampMigrationTest`, `StockReservationMigrationTest`,
  `EstimateStatusMigrationTest`) migram para uma versão de schema **anterior** a esta feature antes de
  inserir, então não foram afetados — confirmado lendo cada um antes de descartar a necessidade de ajuste.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw -o test -Dtest=ServiceOrderStatusTest,ServiceOrderTest,CreateServiceOrderUseCaseTest,ListServiceOrdersUseCaseTest,ServiceOrderControllerListTest,ServiceOrderRepositoryImplTest`
  — 2026-09-19, 58 testes, 0 falhas, 0 erros.
- `./mvnw -o test` (suíte completa) — 2026-09-19, 692 testes, 0 falhas, 0 erros, 0 skipped (após corrigir
  os 2 arquivos de teste com `INSERT` SQL bruto que não incluíam `created_at`).
- `./mvnw -o verify` (equivalente a `make verify`) — 2026-09-19, `BUILD SUCCESS`; `jacoco:check` — "All
  coverage checks have been met."; `ModuleStructureTest` — 2 testes, 0 falhas, nenhuma fronteira de módulo
  violada.
- Nenhuma mudança em OpenAPI/Postman necessária (contrato HTTP inalterado, confirmado em
  `technical-spec.md`), portanto nenhuma regeneração/validação adicional desses artefatos foi necessária
  para esta feature.

## Rollback ou recuperação

Migration aditiva (`NULL` → backfill → `NOT NULL`). Se falhar no meio, Flyway marca a versão como falha e
bloqueia o próximo boot — mesmo tratamento padrão já aceito pelo projeto para qualquer migration
multi-statement; correção é manual (consertar a linha travada no histórico Flyway) antes de reiniciar.
Reverter o PR não desfaz uma migration já aplicada em um ambiente compartilhado — se necessário, uma
migration de compensação (`DROP COLUMN created_at`, `DROP INDEX ...`) deve ser adicionada como nova
versão, nunca editando a migration original (imutabilidade de migration, `AGENTS.md`).

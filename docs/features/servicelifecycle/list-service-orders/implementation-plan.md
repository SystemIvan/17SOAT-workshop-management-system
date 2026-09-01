# Plano de Implementação: Listagem e detalhamento de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo

Implementar `GET /api/service-orders`, opcionalmente filtrado por `status`, `customerId`,
`technicianId` e `priority` (combináveis com AND), reaproveitando o `ServiceOrderResponse` completo já
usado pelo detalhamento (`GET /api/service-orders/{id}`, que não muda). Sem paginação, sem DTO novo, sem
migration.

## Checkpoint 1 — Porta do repositório e critério de busca

- Criar `serviceorder/domain/repository/ServiceOrderSearchCriteria.java` (record:
  `status`, `customerId`, `technicianId`, `priority`).
- `ServiceOrderRepository`: adicionar `search(ServiceOrderSearchCriteria)` como **default method**
  lançando `UnsupportedOperationException` (não abstrato — evita quebrar as 12 implementações fake já
  existentes em `src/test`, ver "Decisão de design" em `technical-spec.md`).

Sem teste de domínio dedicado: o record não tem invariante/normalização própria.

## Checkpoint 2 — Adaptador JPA

- `ServiceOrderJpaRepository`: estender também `JpaSpecificationExecutor<ServiceOrderJpaEntity>`.
- `ServiceOrderRepositoryImpl`: implementar `search(...)` com `Specification` — predicados para
  `statusSnapshot`, `customerId`, `priority`; para `technicianId`, `OR` entre `diagnosisAssigneeId` e
  `executions.assignedTechnicianId` via `LEFT JOIN`; `query.distinct(true)` sempre aplicado.

Sem migration: todas as colunas lidas já existem.

## Checkpoint 3 — Caso de uso

- Criar `ListServiceOrdersUseCase` (`@Transactional(readOnly = true)`), delegando para
  `repository.search(criteria)` e mapeando com `ServiceOrderMapper.toResponse` (já existente).

Testes (`ListServiceOrdersUseCaseTest`, fake `ServiceOrderRepository` sobrescrevendo `search`):
- `execute` repassa o critério recebido para `repository.search` sem alterá-lo;
- `execute` mapeia cada `ServiceOrder` retornado para `ServiceOrderResponse`;
- `execute` retorna lista vazia quando o repositório não encontra nada.

## Checkpoint 4 — API

- `ServiceOrderController`: adicionar `@GetMapping` (raiz de `/api/service-orders`) com
  `@RequestParam(required = false)` para os quatro filtros; `@Operation`/`@ApiResponses` (`200`, `400`).
- Nenhuma mudança em `SecurityConfig` (regra `/api/service-orders/**` já cobre a rota).
- Nenhum handler de exceção novo (`MethodArgumentTypeMismatchException` já mapeado globalmente para
  `400`/`VALIDATION_ERROR`).

Testes HTTP (`ServiceOrderControllerListTest`, `@SpringBootTest`, banco real, reaproveitando
`ServiceOrderHttpTestFixture`/`TestAuth`):
- sem filtros → `200` com todas as SOs criadas no teste;
- sem SOs no banco (ou filtro sem match) → `200` com `[]`;
- `?status=` → só o status pedido (cenário com ≥ 2 status distintos);
- `?customerId=` → só daquele cliente;
- `?technicianId=` → casa via `diagnosisAssigneeId` **e**, em sub-cenário separado, via
  `executions[].assignedTechnicianId` (usando `AssignDiagnosisAssigneeUseCase`/`AssignTechnicianUseCase`
  já existentes para preparar o estado);
- `?priority=` → só daquela prioridade;
- `status` + `priority` combinados → AND, não OR;
- `?status=NAO_EXISTE` → `400`/`VALIDATION_ERROR`;
- `?customerId=nao-e-uuid` → `400`/`VALIDATION_ERROR`;
- sem token → `401`;
- token `CUSTOMER` (fora de `MANAGER`/`TECHNICIAN`/`ADMIN`) → `403`;
- regressão: `GET /api/service-orders/{id}` inalterado.

## Checkpoint 5 — Contratos e documentação

- `OpenApiContractTest`: adicionar `.andExpect(jsonPath("$.paths['/api/service-orders'].get").exists())`.
- Postman: novo request "List service orders" na pasta "Service Orders", com exemplos de cada filtro.
- `docs/Architecture.md` §"Gaps": atualizar a linha "Listar/detalhar Service Orders" de
  `Partially covered` para `Covered`, apontando para esta feature.

## Checkpoint 6 — Validação final

Executar:
- testes de aplicação e web desta feature;
- `./mvnw test` (suíte completa) para garantir ausência de regressão;
- `make verify` / `./mvnw verify`.

Revisar:
- OpenAPI e Postman refletem exatamente o contrato descrito em `technical-spec.md`;
- nenhuma mudança fora do escopo desta feature (detalhamento por ID intocado);
- nenhuma violação de fronteira do Spring Modulith (`ModuleStructureTest` continua verde).

## Definition of Done

- [x] `ServiceOrderSearchCriteria` criado.
- [x] `ServiceOrderRepository.search` (default method) e `ServiceOrderRepositoryImpl.search`
      implementados.
- [x] `ListServiceOrdersUseCase` implementado e testado.
- [x] Endpoint `GET /api/service-orders` implementado e testado (filtros, combinação AND, erros de
      validação, autorização).
- [x] OpenAPI atualizado (`OpenApiContractTest` verde).
- [x] Postman atualizado.
- [x] `docs/Architecture.md` §"Gaps" atualizado.
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada.
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: `status`/`priority` são `enum`s e `customerId`/`technicianId` são `UUID`,
  convertidos automaticamente pelo Spring a partir do `@RequestParam`; valor inválido lança
  `MethodArgumentTypeMismatchException`, já mapeada globalmente para `400`/`VALIDATION_ERROR`
  (`GlobalExceptionHandler`, sem handler novo). Coberto por `rejectsAnInvalidStatusValue`,
  `rejectsAnInvalidPriorityValue`, `rejectsANonUuidCustomerId`, `rejectsANonUuidTechnicianId` em
  `ServiceOrderControllerListTest`. OK.
- **Autenticação/autorização**: reaproveita a regra já existente em `SecurityConfig` para
  `/api/service-orders/**` (`MANAGER`/`TECHNICIAN`/`ADMIN`); nenhuma regra nova adicionada. Coberto por
  `serviceOrderListingRejectsCustomerRole` em `SecurityAuthorizationTest` (`403` para `CUSTOMER`) — o
  filtro JWT já rejeita requisições sem token para qualquer rota (comportamento uniforme verificado em
  `rejectsAnAdministrativeEndpointWithoutAToken`, não duplicado por endpoint). OK.
- **Exposição de dados**: nenhum dado novo exposto — cada item da listagem é o mesmo `ServiceOrderResponse`
  já retornado hoje por `GET /{id}`; a listagem só agrega múltiplos desses payloads. `customerId`/
  `technicianId` não são validados contra a existência real de um Customer/Technician (mesmo padrão de
  referência por UUID já usado no resto do módulo) — um filtro com UUID inexistente simplesmente não casa
  com nada, sem vazar informação sobre existência. OK.
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo introduzido por este fluxo. OK.
- **SQL/persistência/migration**: filtro implementado via `Specification`/JPA Criteria API (parametrizado
  pelo provider, sem concatenação manual de SQL); nenhuma migration nova, nenhuma coluna nova. OK.
- **Erros e disclosure**: `400`/`VALIDATION_ERROR` via handler genérico já existente, sem stack trace nem
  detalhe de SQL na resposta. OK.
- **Dependências novas**: nenhuma — `JpaSpecificationExecutor` já é parte do `spring-boot-starter-data-jpa`
  já usado pelo projeto (mesmo mecanismo de `StockItemRepository.search`). OK.
- **Abuso**: superfície somente-leitura, sem paginação — mesma escolha já aceita em `GET /api/vehicles`/
  `GET /api/customers` para o volume de um MVP; nenhum vetor novo além do já existente em todo endpoint de
  leitura autenticado do módulo.

- **Performance (achado do code-review, corrigido)**: `search(...)` mapeia cada `ServiceOrder` retornado
  via `mapper::toDomain`, que acessa as coleções `LAZY` `executions`/`approvedEstimateIds` (e
  `stockRequirements` de cada execução) — sem mitigação, isso gerava uma query extra por linha por
  coleção (N+1), agravado pela listagem não ser paginada. Corrigido com
  `spring.jpa.properties.hibernate.default_batch_fetch_size=25` (`application.properties` e
  `src/test/resources/application.properties`), que faz o Hibernate agrupar o carregamento dessas
  coleções em lotes (`IN (...)`) em vez de uma query por linha. Correção global (beneficia qualquer
  coleção lazy do projeto), preferida a `JOIN FETCH` manual para evitar `MultipleBagFetchException` ao
  buscar mais de uma coleção do tipo `List` na mesma query.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=ListServiceOrdersUseCaseTest` — 3 testes, 0 falhas (repasse do critério, mapeamento
  de resultados, lista vazia).
- `./mvnw test -Dtest=ServiceOrderControllerListTest` — 13 testes, 0 falhas (sem filtro, banco vazio, cada
  filtro isoladamente, `technicianId` via `diagnosisAssigneeId` e via execução, combinação AND,
  `400`/`VALIDATION_ERROR` para `status`/`priority`/`customerId`/`technicianId` inválidos, regressão de
  `GET /{id}`).
- `./mvnw test -Dtest=SecurityAuthorizationTest` — 11 testes, 0 falhas (10 existentes +
  `serviceOrderListingRejectsCustomerRole`, novo).
- `./mvnw test -Dtest=OpenApiContractTest` — 13 testes, 0 falhas, incluindo o novo
  `documentServiceOrderListingContract` (responses `200`/`400` e os 4 query params documentados) e a nova
  asserção de existência de `$.paths['/api/service-orders'].get` no teste geral.
- `./mvnw test` (suíte completa) — 2026-08-25, 572 testes, 0 falhas, 0 erros, 0 skipped.
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-25, `BUILD SUCCESS` (exit code 0), JaCoCo
  executado (`target/site/jacoco/index.html` gerado).
- `./mvnw test -Dtest=ModuleStructureTest` — 2026-08-25, 2 testes, 0 falhas; nenhuma fronteira de módulo
  violada pela feature.
- Code review (`/code-review medium`) sobre o diff da feature — achado de N+1 em `search(...)` corrigido
  com `hibernate.default_batch_fetch_size=25`; suíte completa e `./mvnw verify` reexecutados após a
  correção, 572 testes, 0 falhas, `BUILD SUCCESS`.
- Postman: 5 requests adicionados em "Service Orders" ("List service orders" + um por filtro), JSON
  validado (`node -e "JSON.parse(...)"` sem erro).
- OpenAPI: endpoint documentado via `@Operation`/`@ApiResponses`/`@RequestParam` em
  `ServiceOrderController`, gerado automaticamente pelo springdoc, sem YAML manual.

## Rollback ou recuperação

N/A — endpoint somente-leitura, sem migration, sem mudança de estado. Reverter o commit/PR é suficiente
em caso de problema; nenhum dado é escrito ou migrado por esta feature.

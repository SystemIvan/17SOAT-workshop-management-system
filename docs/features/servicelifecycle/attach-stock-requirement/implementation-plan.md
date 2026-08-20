# Plano de Implementação: Anexar Necessidade de Estoque a uma ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `attach-stock-requirement` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Especificação técnica | `./technical-spec.md` |

## Objetivo

Implementar uma fatia vertical completa (domínio, aplicação, API, testes) para expor
`ServiceOrder.attachStockRequirement` via
`POST /api/service-orders/{id}/executions/{executionId}/stock-requirements`, com bloqueio em
`ServiceExecution` `COMPLETED`/`REJECTED` e rebaixamento de `READY` para `AWAITING_PART` quando o novo
item não estiver reservado.

## Checkpoint 1 — Domínio

- `ServiceExecution.attachStockRequirement`: adicionar guarda que lança `IllegalStateException` quando
  `status` é `COMPLETED` ou `REJECTED`; ao final, chamar `recomputeReadiness()`.
- `ServiceExecution.recomputeReadiness`: incluir `READY` na condição de status elegível para recálculo
  (hoje só `AUTHORIZED`/`AWAITING_PART`).
- `ServiceOrder.attachStockRequirement`: chamar `recomputeStatusSnapshot(false)` ao final.

Testes (`ServiceExecutionTest`/`ServiceOrderTest`, conforme já organizados no projeto):
- adiciona o item quando o status permite (`PENDING`, `AUTHORIZED`, `READY`, `AWAITING_PART`,
  `IN_PROGRESS`);
- lança `IllegalStateException` quando `COMPLETED`;
- lança `IllegalStateException` quando `REJECTED`;
- rebaixa `READY` para `AWAITING_PART` quando o novo item não está reservado;
- não altera o status quando `PENDING` ou `IN_PROGRESS`;
- `ServiceOrder.attachStockRequirement` recalcula `statusSnapshot` (cenário `READY` → `AWAITING_PART`);
- `ServiceOrder.attachStockRequirement` lança `NoSuchElementException` para `executionId` inexistente.

## Checkpoint 2 — Caso de uso

Criar `AttachStockRequirementUseCase` (sem novo DTO de request — reaproveita `StockRequirementRequest`
já existente).

Fluxo: carregar `ServiceOrder` (`ServiceOrderFinder.getOrThrow`) →
`ServiceOrderMapper.toStockRequirement(request)` → `attachStockRequirement(executionId, requirement)` →
`save(...)` → retornar `ServiceOrderResponse`.

Testes (`AttachStockRequirementUseCaseTest`):
- fluxo válido (`StockRequirement` persistido e refletido na resposta, `reserved = false`);
- Service Order inexistente propaga `NoSuchElementException`;
- `executionId` inexistente propaga `NoSuchElementException`;
- `ServiceExecution` `COMPLETED`/`REJECTED` propaga `IllegalStateException`.

## Checkpoint 3 — API

Adicionar `POST /api/service-orders/{id}/executions/{executionId}/stock-requirements` em
`ServiceOrderController`, usando `AttachStockRequirementUseCase`.

Resultado esperado:
- `200 OK` com `ServiceOrderResponse` refletindo o novo `StockRequirement`;
- `404`/`NOT_FOUND` para Service Order ou `ServiceExecution` inexistente;
- `409`/`INVALID_STATE_TRANSITION` para `ServiceExecution` `COMPLETED`/`REJECTED`;
- `400`/`VALIDATION_ERROR` para campos ausentes ou `quantity <= 0`.

Atualizar:
- OpenAPI (via `@Operation`/`@ApiResponses`, gerado automaticamente pelo springdoc a partir do
  controller);
- collection Postman (`docs/api/postman/workshop-management-system.postman_collection.json`), seguindo
  o padrão das entradas existentes de `service-orders`/`executions`.

## Checkpoint 4 — Validação final

Executar:
- testes de domínio, application e web desta feature;
- `./mvnw test` (suíte completa) para garantir ausência de regressão;
- `make verify` / `./mvnw verify`.

Revisar:
- OpenAPI e Postman refletem exatamente o contrato descrito em `technical-spec.md`;
- nenhuma mudança fora do escopo desta feature;
- nenhuma violação de fronteira do Spring Modulith (`ModuleStructureTest` continua verde).

## Definition of Done

- [x] `ServiceExecution.attachStockRequirement`/`recomputeReadiness` com guarda e recálculo
  implementados e testados.
- [x] `ServiceOrder.attachStockRequirement` recalculando `statusSnapshot`, testado.
- [x] `AttachStockRequirementUseCase` implementado e testado.
- [x] Endpoint `POST /api/service-orders/{id}/executions/{executionId}/stock-requirements` implementado.
- [x] OpenAPI atualizado (via `@Operation`/`@ApiResponses` no controller, springdoc gera o contrato
  automaticamente a partir deles — mesmo mecanismo já usado por todos os demais endpoints do projeto).
- [x] Postman atualizado (`docs/api/postman/workshop-management-system.postman_collection.json`).
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada (ver abaixo).
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: contrato `StockRequirementRequest` já validado via Bean Validation
  (`@NotNull`, `@Positive`, `@NotBlank`), mesmo usado por `perform-diagnosis`; `400`/`VALIDATION_ERROR`
  para os demais casos. OK.
- **Autenticação/autorização**: nenhum mecanismo existe no projeto hoje; este endpoint segue o mesmo
  padrão dos demais. Risco pré-existente de plataforma, não introduzido por esta feature.
- **Exposição de dados**: nenhum dado novo exposto — `stockRequirements` já é campo público de
  `ServiceExecutionResponse` (usado hoje via `perform-diagnosis`).
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo introduzido por este fluxo.
- **SQL/persistência/migration**: nenhuma migration nova; persistência via Spring Data JPA já
  existente (`@ElementCollection`).
- **Erros e disclosure**: `404`/`409`/`400` mapeados por handlers já existentes
  (`GlobalExceptionHandler`, `ServiceLifecycleExceptionHandler`), sem stack trace nem detalhe de SQL.
- **Dependências novas**: nenhuma.
- **Abuso**: qualquer chamador pode anexar um `StockRequirement` a qualquer `ServiceExecution` não
  terminal (sem autenticação), mesmo padrão de risco já presente em todos os outros endpoints de mutação
  do projeto. `quantity` não tem teto superior, consistente com `perform-diagnosis`.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=ServiceExecutionTest,ServiceOrderTest` — 28 testes, 0 falhas (11 + 17, incluindo os
  7 novos casos do RF12: guarda de status `COMPLETED`/`REJECTED`, rebaixamento `READY` →
  `AWAITING_PART`, status inalterado em `PENDING`/`IN_PROGRESS`, `statusSnapshot` recalculado e
  `executionId` inexistente).
- `./mvnw test -Dtest=AttachStockRequirementUseCaseTest` — 4 testes, 0 falhas (fluxo válido, Service
  Order inexistente, `ServiceExecution` inexistente, `ServiceExecution` `COMPLETED`).
- `./mvnw test -Dtest=ServiceOrderControllerAttachStockRequirementTest` — 7 testes, 0 falhas (`200` com o
  `StockRequirement` refletido, `404` para Service Order e execução inexistentes, `409`/
  `INVALID_STATE_TRANSITION` para `COMPLETED`/`REJECTED`, `400`/`VALIDATION_ERROR` para `quantity=0` e
  campos ausentes).
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-20, 252 testes no total, 0 falhas, 0 erros,
  JaCoCo executado, `BUILD SUCCESS`.
- `./mvnw test -Dtest=ModuleStructureTest` — 2026-08-20, 2 testes, 0 falhas; nenhuma fronteira de módulo
  violada pela feature.
- Postman: entrada "Attach stock requirement" adicionada em `Service Orders`, JSON validado
  (`JSON.parse` via Node sem erro).
- OpenAPI: endpoint documentado via `@Operation`/`@ApiResponses` em `ServiceOrderController`, mesmo
  padrão já usado pelos demais endpoints (gerado automaticamente pelo springdoc, sem YAML manual).

## Rollback ou recuperação

Reversível via `git revert` do commit da feature — nenhuma migration, nenhum dado persistido além da
coleção `stockRequirements` já existente no schema. Sem efeito colateral em outros agregados.

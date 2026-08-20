# Plano de Implementação: Alterar Prioridade da Service Order

| Campo | Valor |
|---|---|
| Feature | `change-service-order-priority` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Especificação técnica | `./technical-spec.md` |

## Objetivo

Implementar uma fatia vertical completa (domínio, aplicação, API, testes) para expor
`ServiceOrder.definePriority` via `PATCH /api/service-orders/{id}/priority`, com bloqueio em Service
Orders `COMPLETED`/`DELIVERED`.

## Checkpoint 1 — Domínio

Alterar `ServiceOrder.definePriority(Priority newPriority)` para lançar `IllegalStateException` quando
`statusSnapshot` for `COMPLETED` ou `DELIVERED`, antes de aplicar a mudança.

Testes:
- altera a prioridade quando o status permite (todos os demais valores de `ServiceOrderStatus`);
- lança `IllegalStateException` quando `COMPLETED`;
- lança `IllegalStateException` quando `DELIVERED`.

## Checkpoint 2 — Caso de uso

Criar `ChangeServiceOrderPriorityUseCase` e `ChangeServiceOrderPriorityRequest`.

Fluxo: carregar `ServiceOrder` (`ServiceOrderFinder.getOrThrow`) → `definePriority(...)` → `save(...)`
→ retornar `ServiceOrderResponse`.

Testes:
- fluxo válido (prioridade persistida e refletida na resposta);
- Service Order inexistente propaga `NoSuchElementException` (→ `404` na camada web).

## Checkpoint 3 — API

Adicionar `PATCH /api/service-orders/{id}/priority` em `ServiceOrderController`, usando
`ChangeServiceOrderPriorityUseCase`.

Resultado esperado:
- `200 OK` com `ServiceOrderResponse` refletindo a nova prioridade;
- `404`/`NOT_FOUND` para Service Order inexistente;
- `409`/`INVALID_STATE_TRANSITION` para Service Order `COMPLETED`/`DELIVERED`;
- `400`/`VALIDATION_ERROR` para `priority` ausente ou fora do enum.

Atualizar:
- OpenAPI (via `@Operation`/`@ApiResponses`, gerado automaticamente pelo springdoc a partir do
  controller);
- collection Postman (`docs/api/postman/workshop-management-system.postman_collection.json`), seguindo
  o padrão das entradas existentes de `service-orders`.

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

- [x] `ServiceOrder.definePriority` com guarda de status implementada e testada.
- [x] `ChangeServiceOrderPriorityUseCase` implementado e testado.
- [x] Endpoint `PATCH /api/service-orders/{id}/priority` implementado.
- [x] OpenAPI atualizado (via `@Operation`/`@ApiResponses` no controller, springdoc gera o contrato
  automaticamente a partir deles — mesmo mecanismo já usado por todos os demais endpoints do projeto).
- [x] Postman atualizado (`docs/api/postman/workshop-management-system.postman_collection.json`).
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada (ver abaixo).
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: `priority` obrigatório e restrito ao enum `Priority` via Jackson/Bean
  Validation; `400`/`VALIDATION_ERROR` para os demais casos. OK.
- **Autenticação/autorização**: nenhum mecanismo existe no projeto hoje; este endpoint segue o mesmo
  padrão dos demais. Risco pré-existente de plataforma, não introduzido por esta feature.
- **Exposição de dados**: nenhum dado novo exposto — `priority` já é campo público de
  `ServiceOrderResponse`.
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo introduzido por este fluxo.
- **SQL/persistência/migration**: nenhuma migration nova; persistência via Spring Data JPA já
  existente.
- **Erros e disclosure**: `404`/`409`/`400` mapeados por handlers já existentes
  (`GlobalExceptionHandler`, `ServiceLifecycleExceptionHandler`), sem stack trace nem detalhe de SQL.
- **Dependências novas**: nenhuma.
- **Abuso**: qualquer chamador pode alterar a prioridade de qualquer Service Order (sem autenticação),
  mesmo padrão de risco já presente em todos os outros endpoints de mutação do projeto.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=ServiceOrderTest` — 15 testes, 0 falhas (3 novos cobrindo `definePriority`:
  sucesso, rejeição em `COMPLETED`, rejeição em `DELIVERED`).
- `./mvnw test -Dtest=ChangeServiceOrderPriorityUseCaseTest` — 2 testes, 0 falhas.
- `./mvnw test -Dtest=ServiceOrderControllerChangePriorityTest` — 5 testes, 0 falhas (`200` com
  prioridade refletida, `404` para Service Order inexistente, `400` para `priority` ausente/inválida,
  `409`/`INVALID_STATE_TRANSITION` para Service Order `COMPLETED`).
- `./mvnw test` (suíte completa) — 2026-08-20, sem falhas, sem regressão nos demais módulos.
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-20, sem falhas, JaCoCo executado.
- `ModuleStructureTest` — verde (2 testes, 0 falhas); nenhuma fronteira de módulo violada pela feature.
- Postman: entrada "Change service order priority" adicionada em `Service Orders`, JSON validado
  (`json.load`/`JSON.parse` sem erro).
- OpenAPI: endpoint documentado via `@Operation`/`@ApiResponses` em `ServiceOrderController`, mesmo
  padrão já usado pelos demais endpoints (gerado automaticamente pelo springdoc, sem YAML manual).

## Rollback ou recuperação

Reversível via `git revert` do commit da feature — nenhuma migration, nenhum dado persistido além do
próprio valor de `priority` (já um campo mutável existente no schema). Sem efeito colateral em outros
agregados.

# Plano de Implementação: Decidir Linhas de uma Estimate (Aprovar/Rejeitar por ServiceExecution)

| Campo | Valor |
|---|---|
| Feature | `decide-estimate-lines` |
| Status | Stale |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Especificação técnica | `./technical-spec.md` |

> Stale desde 2026-08-20: a especificação funcional foi devolvida a `Draft` pela feature
> `stock-item-reservation`. O plano histórico não deve ser usado para nova implementação até nova revisão do SDD.

## Objetivo

Implementar uma fatia vertical completa (aplicação, API, testes) para expor
`ServiceOrder.authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate` via
`POST /api/estimates/{estimateId}/decisions`, aceitando decisões em lote (uma ou mais linhas), com
validação de pertencimento à Estimate e comportamento tudo-ou-nada. Nenhuma mudança de domínio é
necessária — os métodos e guardas já existem e já cobrem as regras da feature.

## Checkpoint 1 — DTOs e caso de uso

Criar:
- `DecideEstimateLinesRequest` (com `LineDecisionRequest` aninhado) e `EstimateLineDecision`
  (`estimate/application/dto`);
- `DecideEstimateLinesUseCase` (`estimate/application/usecase`).

Fluxo: carregar `Estimate` (`EstimateRepository.findById`) → validar ausência de `serviceExecutionId`
duplicado na requisição → validar que cada `serviceExecutionId` pertence a uma `EstimateLine` da
Estimate → carregar `ServiceOrder` (`ServiceOrderFinder.getOrThrow`) → aplicar cada decisão
(`authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`) → `save(...)` → retornar
`ServiceOrderResponse` (`ServiceOrderMapper.toResponse`, reaproveitado do módulo `serviceorder`).

Testes (`DecideEstimateLinesUseCaseTest`):
- fluxo válido com uma decisão `APPROVED` (execução vira `AUTHORIZED`/`READY` conforme suas
  `StockRequirement`);
- fluxo válido com uma decisão `REJECTED`;
- fluxo válido com múltiplas decisões mistas em uma única chamada;
- Estimate inexistente propaga `NoSuchElementException`;
- `serviceExecutionId` que não pertence à Estimate propaga `NoSuchElementException`, sem persistir
  nenhuma decisão da chamada;
- `serviceExecutionId` repetido na mesma chamada propaga `IllegalArgumentException`;
- `ServiceExecution` não `PENDING` propaga `IllegalStateException`, sem persistir nenhuma decisão da
  chamada (tudo-ou-nada: testar com uma decisão válida + uma inválida na mesma lista, confirmando que a
  válida não foi persistida).

## Checkpoint 2 — API

Adicionar `POST /api/estimates/{estimateId}/decisions` em `EstimateController`, usando
`DecideEstimateLinesUseCase`.

Resultado esperado:
- `200 OK` com `ServiceOrderResponse` refletindo o novo status de cada `ServiceExecution` decidida;
- `404`/`NOT_FOUND` para Estimate inexistente ou `serviceExecutionId` fora da Estimate;
- `409`/`INVALID_STATE_TRANSITION` para `ServiceExecution` não `PENDING`;
- `400`/`VALIDATION_ERROR` para `decisions` vazio/ausente ou campos obrigatórios ausentes;
- `400`/`INVALID_STOCK_ITEM` para `serviceExecutionId` duplicado (código herdado do handler genérico
  existente — ver nota em `technical-spec.md` §"Tratamento de erros").

Atualizar:
- OpenAPI (via `@Operation`/`@ApiResponses`, gerado automaticamente pelo springdoc a partir do
  controller);
- collection Postman (`docs/api/postman/workshop-management-system.postman_collection.json`), seguindo
  o padrão das entradas existentes de `Estimates`.

Testes (`EstimateControllerDecideLinesTest`):
- `200` com `AUTHORIZED`/`REJECTED` refletidos na resposta;
- `404` para Estimate inexistente;
- `404` para `serviceExecutionId` fora da Estimate;
- `409`/`INVALID_STATE_TRANSITION` chamando o endpoint duas vezes para a mesma linha;
- `400`/`VALIDATION_ERROR` para `decisions` vazio/ausente;
- `400` para `serviceExecutionId` duplicado na mesma requisição.

## Checkpoint 3 — Validação final

Executar:
- testes de aplicação e web desta feature;
- `./mvnw test` (suíte completa) para garantir ausência de regressão;
- `make verify` / `./mvnw verify`.

Revisar:
- OpenAPI e Postman refletem exatamente o contrato descrito em `technical-spec.md`;
- nenhuma mudança fora do escopo desta feature (em particular, nenhuma alteração em `ServiceOrder`/
  `ServiceExecution`/`Estimate`);
- nenhuma violação de fronteira do Spring Modulith (`ModuleStructureTest` continua verde).

## Definition of Done

- [x] `DecideEstimateLinesRequest`/`EstimateLineDecision` implementados.
- [x] `DecideEstimateLinesUseCase` implementado e testado.
- [x] Endpoint `POST /api/estimates/{estimateId}/decisions` implementado.
- [x] OpenAPI atualizado (via `@Operation`/`@ApiResponses` no controller, springdoc gera o contrato
  automaticamente a partir deles).
- [x] Postman atualizado (`docs/api/postman/workshop-management-system.postman_collection.json`).
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada (ver abaixo).
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: `decisions` obrigatório e não vazio, cada `serviceExecutionId`/`decision`
  obrigatórios via Bean Validation, mesmo padrão de `PerformDiagnosisRequest`. Duplicidade validada
  explicitamente no use case. OK.
- **Autenticação/autorização**: nenhum mecanismo existe no projeto hoje; este endpoint segue o mesmo
  padrão dos demais. Em particular, não valida que o chamador é o Customer dono da Estimate — risco
  pré-existente de plataforma, não introduzido por esta feature.
- **Exposição de dados**: nenhum dado novo exposto — `ServiceOrderResponse` já expõe o status de cada
  `ServiceExecution` hoje.
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo introduzido por este fluxo.
- **SQL/persistência/migration**: nenhuma migration nova; persistência via Spring Data JPA já
  existente, reaproveitando o `save()` de `ServiceOrder`.
- **Erros e disclosure**: `404`/`409`/`400` mapeados por handlers já existentes
  (`GlobalExceptionHandler`, `ServiceLifecycleExceptionHandler`), sem stack trace nem detalhe de SQL.
- **Dependências novas**: nenhuma.
- **Abuso**: qualquer chamador pode decidir qualquer linha de qualquer Estimate (sem autenticação),
  mesmo padrão de risco já presente em todos os outros endpoints de mutação do projeto.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=DecideEstimateLinesUseCaseTest` — 7 testes, 0 falhas (aprovação simples, rejeição
  simples, decisões mistas em lote, Estimate inexistente, linha fora da Estimate sem persistir decisão,
  `serviceExecutionId` duplicado, execução não `PENDING` confirmando `save()` nunca chamado nesse
  caminho).
- `./mvnw test -Dtest=EstimateControllerDecideLinesTest` — 7 testes, 0 falhas (`200` aprovação/rejeição,
  `404` Estimate inexistente e linha fora da Estimate, `409`/`INVALID_STATE_TRANSITION` para execução já
  decidida, `400` para lista vazia e para `serviceExecutionId` duplicado).
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-20, 266 testes no total, 0 falhas, 0 erros,
  JaCoCo executado, `BUILD SUCCESS`.
- `./mvnw test -Dtest=ModuleStructureTest` — 2026-08-20, 2 testes, 0 falhas; nenhuma fronteira de módulo
  violada pela feature.
- Postman: entrada "Decide estimate lines" adicionada em `Estimates`, JSON validado (`JSON.parse` via
  Node sem erro).
- OpenAPI: endpoint documentado via `@Operation`/`@ApiResponses` em `EstimateController`, mesmo padrão
  já usado pelos demais endpoints (gerado automaticamente pelo springdoc, sem YAML manual).

## Rollback ou recuperação

Reversível via `git revert` do commit da feature — nenhuma migration, nenhum dado persistido além do
status já existente de `ServiceExecution`. Sem efeito colateral em outros agregados.

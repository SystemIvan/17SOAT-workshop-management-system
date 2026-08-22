# Especificação Funcional: Rastrear progresso da execução

| Campo | Valor |
|---|---|
| Feature | `track-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Referências | RF23 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/Architecture.md` §6.4 (Tracking), §11 (traceability — "Consulta do progresso pelo Customer via API"); `docs/features/servicelifecycle/complete-execution/functional-spec.md` (RF22); `docs/Architecture-Decisions.md` (AD-006, AD-010, AD-015, AD-016); `.claude/rules/epic-3-service-lifecycle.md`; código atual: `GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase`, `ServiceOrder.status()`/`recomputeStatusSnapshot`, `ServiceOrderStatusResponse`, `ServiceOrderResponse` |

## Delta proposto por `stock-item-reservation`

O tracking passa a expor `AWAITING_ITEMS`, em vez de `AWAITING_PART`, e o detalhe de uma
Service Execution inclui somente o `stockReservationId` quando existente. Linhas, estado e timestamps
da reserva permanecem propriedade de Stock & Procurement e são consultados pelos endpoints próprios.

## Problema e resultado esperado

Depois que uma `ServiceOrder` entra em execução (RF19–RF22), um interessado (Customer, Service
Advisor/Manager, Technician) precisa consultar o progresso sem esperar a entrega (RF24): o status
geral da `ServiceOrder` (`statusSnapshot`) e o status individual de cada `ServiceExecution`.

Resultado esperado: dado o ID de uma `ServiceOrder`, o sistema retorna seu status derivado atual
(`RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL → AWAITING_ITEMS/IN_PROGRESS → COMPLETED → DELIVERED`,
conforme a precedência de `recomputeStatusSnapshot`) e, quando o detalhe completo é pedido, o status
de cada `ServiceExecution` associada.

**Nota sobre o estado atual do código:** este comportamento já está implementado e exposto por dois
endpoints somente-leitura:
- `GET /api/service-orders/{id}/status` (`GetServiceOrderStatusUseCase` → `ServiceOrderStatusResponse`
  com `id` + `status` derivado), e
- `GET /api/service-orders/{id}` (`GetServiceOrderUseCase` → `ServiceOrderResponse` completo,
  incluindo a lista de `ServiceExecution`s com status individual).

Nenhum dos dois use cases tem teste dedicado (`GetServiceOrderStatusUseCaseTest`/
`GetServiceOrderUseCaseTest` não existem) nem teste HTTP de controller, e nenhum dos dois endpoints
tem `@ApiResponses` (diferente de `assignTechnician`/`startExecution`/`updateExecutionProgress`/
`completeExecution`). A cobertura de domínio da regra de precedência do `statusSnapshot` já existe
(`ServiceOrderTest`, cobrindo os efeitos de cada comando sobre o status). Esta spec documenta o
comportamento esperado de RF23 para validá-lo formalmente contra o requisito e servir de base para a
cobertura de teste que falta — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Customer | Consulta o progresso da própria `ServiceOrder` (status geral e, potencialmente, das execuções) |
| Service Advisor / Manager | Consulta o status de qualquer `ServiceOrder` para responder o Customer ou acompanhar operação |
| Technician | Indiretamente: o status que ele reflete via `startExecution`/`updateExecutionProgress`/`completeExecution` (RF20–RF22) é o que este RF23 expõe em consulta |

### Cenário principal

1. A `ServiceOrder` existe.
2. O ator informa o `serviceOrderId`.
3. O sistema retorna o `statusSnapshot` atual (via `GET /{id}/status`) e, se o detalhe completo for
   consultado (`GET /{id}`), a lista de `ServiceExecution`s com o status individual de cada uma.

### Cenário alternativo — ServiceOrder inexistente

1. O ator informa um `serviceOrderId` que não existe.
2. O sistema retorna erro `404 NOT_FOUND`.

## Regras de negócio

- O `statusSnapshot` retornado é somente leitura: nenhum comando é executado por esses endpoints,
  apenas o valor já recalculado por comandos anteriores é lido (AD-010, Option B — comportamento
  preservado, decisão de time ainda pendente).
- A precedência do `statusSnapshot` (`DELIVERED → COMPLETED → IN_PROGRESS → AWAITING_ITEMS →
  AWAITING_APPROVAL → IN_DIAGNOSIS → RECEIVED`) é a já implementada em
  `ServiceOrder.recomputeStatusSnapshot` — esta spec não a redefine, apenas documenta o contrato de
  consulta sobre ela.
- `GET /{id}/status` retorna somente `id` + `status` (resumo); `GET /{id}` retorna o agregado
  completo, incluindo `executions` com o status individual e, quando existente, `stockReservationId` de
  cada `ServiceExecution`. Não há,
  hoje, um endpoint que agrupe execuções por Estimate como descrito em `Architecture.md` §6.4 — ver
  lacuna abaixo.
- Consultar uma `ServiceOrder`/`ServiceExecution` inexistente retorna `404 NOT_FOUND`, no mesmo
  padrão dos demais endpoints do épico.

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

- **Quem pode consultar o quê:** `Architecture.md` §11 registra explicitamente "Falta ... regra de
  acesso ao próprio Customer" — nenhuma fonte restringe o Customer a ver apenas suas próprias
  `ServiceOrder`s, nem define autenticação/autorização para os dois endpoints de consulta. Mesma
  lacuna de AD-016 já registrada em RF19–RF22; esta spec não implementa controle de acesso.
- **Agrupamento por Estimate:** `Architecture.md` §6.4 descreve a visão do Customer como "status-resumo
  da SO com o estado individual das execuções agrupadas por Estimate", mas o código atual
  (`ServiceOrderResponse`) lista as execuções sem agrupá-las por Estimate. Ampliar o contrato para
  agrupar por Estimate seria uma mudança de comportamento, não uma cobertura de lacuna — fora de
  escopo aqui.
- **Polling/cache/tempo real:** a estratégia de atualização de tracking (polling puro vs.
  polling+cache vs. SSE/WebSocket) depende de AD-015 e do `ADR-001-realtime-updates-strategy.md`
  (Polling aceito por Santiago, mas AD-015 ainda `Team Decision Required` compartilhada). Esta spec
  cobre apenas a consulta síncrona já implementada; nenhum mecanismo de push/cache é adicionado.
- **Efeito sobre `TechnicianStatus`:** não aplicável — este RF é somente-leitura e não altera nenhum
  aggregate.

## Fora de escopo

- Autenticação/autorização de quem pode consultar (Customer restrito à própria `ServiceOrder`,
  etc.) — depende de AD-016.
- Agrupamento das execuções por Estimate no contrato de resposta — mudança de comportamento, não
  fechamento de lacuna de cobertura.
- Qualquer mecanismo de polling com cache, SSE ou WebSocket — depende de AD-015 e
  `ADR-001-realtime-updates-strategy.md`, ambos ainda pendentes de ratificação pelo time.
- Redefinir a precedência de `recomputeStatusSnapshot` — comportamento já implementado e já coberto
  por teste de domínio; esta feature apenas fecha a lacuna de cobertura no nível de use
  case/HTTP e a lacuna de documentação Swagger dos dois endpoints de consulta.
- RF24 (finalizar/entregar a `ServiceOrder`) — feature separada.

## Critérios de aceite

- [x] Consultar `GET /{id}/status` de uma `ServiceOrder` existente retorna `200` com o `statusSnapshot`
      atual, refletindo os efeitos dos comandos já aplicados (diagnóstico, autorização, execuções em
      cada status). Evidência: `GetServiceOrderStatusUseCaseTest.returnsIdAndStatusForANewlyCreatedServiceOrder`,
      `...returnsIdAndStatusForAServiceOrderInDiagnosis` e
      `ServiceOrderControllerGetStatusTest.returnsStatusForAnExistingServiceOrder`.
- [x] Consultar `GET /{id}` de uma `ServiceOrder` existente retorna `200` com o agregado completo,
      incluindo o status individual de cada `ServiceExecution`. Evidência:
      `GetServiceOrderUseCaseTest.returnsTheFullServiceOrderIncludingExecutionStatus` e
      `ServiceOrderControllerGetStatusTest.returnsTheFullServiceOrderIncludingExecutionStatus`.
- [x] Consultar qualquer um dos dois endpoints para uma `ServiceOrder` inexistente retorna `404
      NOT_FOUND`. Evidência: `GetServiceOrderStatusUseCaseTest.rejectsGettingStatusWhenServiceOrderDoesNotExist`,
      `GetServiceOrderUseCaseTest.rejectsGettingWhenServiceOrderDoesNotExist`,
      `ServiceOrderControllerGetStatusTest.returnsNotFoundWhenGettingStatusOfAServiceOrderThatDoesNotExist`
      e `...returnsNotFoundWhenGettingAServiceOrderThatDoesNotExist`.
- [x] Os dois endpoints passam a documentar seus códigos de resposta via `@ApiResponses`, no mesmo
      padrão de `assignTechnician`/`startExecution`/`updateExecutionProgress`/`completeExecution`.
      Evidência: `ServiceOrderController.get`/`getStatus`; `OpenApiContractTest` continua passando.
- [ ] O tracking usa `AWAITING_ITEMS` em seus contratos e retorna somente `stockReservationId` para
      relacionar uma execução à reserva, sem replicar linhas ou estado de Stock & Procurement.

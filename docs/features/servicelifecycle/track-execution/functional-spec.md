# Especificação Funcional: Rastrear progresso da execução

| Campo | Valor |
|---|---|
| Feature | `track-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | RF23 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/Architecture.md` §6.4 (Tracking), §11 (traceability — "Consulta do progresso pelo Customer via API"); `docs/features/servicelifecycle/complete-execution/functional-spec.md` (RF22); `docs/Architecture-Decisions.md` (AD-006, AD-010, AD-015, AD-016); `.claude/rules/epic-3-service-lifecycle.md`; features `service-order-initial-assessment`, `assign-diagnosis-assignee`, `diagnosis-authorship` e `service-order-status-projection` |

## Delta proposto por `stock-item-reservation`

O tracking passa a expor `AWAITING_ITEMS`, em vez de `AWAITING_PART`, e o detalhe de uma
Service Execution inclui somente o `stockReservationId` quando existente. Linhas, estado e timestamps
da reserva permanecem propriedade de Stock & Procurement e são consultados pelos endpoints próprios.

## Deltas materiais aprovados pela RFC-002 — pendentes de nova aprovação desta spec

As respostas detalhadas de Service Order passam a incluir `initialAssessment`, `diagnosisAssigneeId` e, em cada
Service Execution, `diagnosedByTechnicianId` e `diagnosedAt` (valores anuláveis apenas em registros legados). A
projeção passa a expor `statusSnapshot` como nome canônico e mantém `status` como alias compatível e deprecated;
`READY` mapeia para o snapshot `IN_PROGRESS`, e SOs com ao menos uma execução e todas em `COMPLETED` ou `REJECTED`
ficam `COMPLETED`.

Classificação: **material** — muda a semântica documentada da projeção e amplia o response detalhado. As quatro
features citadas são as fontes de verdade; esta spec não duplica seus contratos, migrations ou regras. A revisão foi
aprovada por humano em 2026-08-22 e a especificação técnica revisada foi aprovada na sequência. O plano histórico
permanece `Stale`, pois não cobre a implementação dos deltas.

## Problema e resultado esperado

Depois que uma `ServiceOrder` entra em execução (RF19–RF22), um interessado (Customer, Service
Advisor/Manager, Technician) precisa consultar o progresso sem esperar a entrega (RF24): o status
geral da `ServiceOrder` (`statusSnapshot`) e o status individual de cada `ServiceExecution`.

Resultado esperado: dado o ID de uma `ServiceOrder`, o sistema retorna seu status derivado atual
(`RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL → AWAITING_ITEMS/IN_PROGRESS → COMPLETED → DELIVERED`,
conforme a precedência de `recomputeStatusSnapshot`) e, quando o detalhe completo é pedido, o status
de cada `ServiceExecution` associada, a triagem, o responsável planejado e a auditoria do Diagnosis quando existirem.

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
- A precedência do `statusSnapshot` é `DELIVERED → COMPLETED → IN_PROGRESS → AWAITING_ITEMS →
  AWAITING_APPROVAL → IN_DIAGNOSIS → RECEIVED`. `COMPLETED` exige ao menos uma execução e que todas estejam
  `COMPLETED` ou `REJECTED`; `READY` ou `IN_PROGRESS` projetam `IN_PROGRESS`. A definição é da feature
  `service-order-status-projection`.
- `GET /{id}/status` retorna somente `id` + `status` (resumo); `GET /{id}` retorna o agregado
  completo, incluindo `statusSnapshot` e `status` compatível, `initialAssessment`, `diagnosisAssigneeId`, e
  `executions` com estado individual, autoria/instante quando existentes e, quando existente, `stockReservationId`.
  Não há,
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
- **Polling/cache/tempo real:** AD-015 foi resolvida em 2026-08-23 — o time ratificou Option A
  (polling puro, sem cache) para o MVP, conforme `docs/Architecture-Decisions.md` e
  `../../../adr/ADR-002-realtime-updates-strategy.md`. Esta spec cobre apenas a consulta síncrona já
  implementada; nenhum mecanismo de push/cache é adicionado, e introduzi-lo exigiria uma nova decisão
  do time.
- **Efeito sobre `TechnicianStatus`:** não aplicável — este RF é somente-leitura e não altera nenhum
  aggregate.

## Fora de escopo

- Autenticação/autorização de quem pode consultar (Customer restrito à própria `ServiceOrder`,
  etc.) — depende de AD-016.
- Agrupamento das execuções por Estimate no contrato de resposta — mudança de comportamento, não
  fechamento de lacuna de cobertura.
- Qualquer mecanismo de polling com cache, SSE ou WebSocket — AD-015 resolvida a favor de polling puro
  (2026-08-23); adicionar cache/push exigiria nova decisão do time.
- Implementar os quatro deltas da RFC-002 — são responsabilidades das features referenciadas. Esta spec somente
  reconcilia seu contrato de leitura com as decisões aprovadas.
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
- [ ] O response detalhado mantém `status` como alias de `statusSnapshot` e apresenta os campos aditivos aprovados;
      a evidência será atualizada após reaprovação e implementação das features dependentes.

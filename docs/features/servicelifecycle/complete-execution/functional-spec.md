# Especificação Funcional: Concluir execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `complete-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-19 |
| Referências | RF22 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/features/servicelifecycle/update-progress/functional-spec.md` (RF21); `docs/Architecture-Decisions.md` (AD-006, AD-010, AD-015); `.claude/rules/epic-3-service-lifecycle.md`; código atual: `CompleteExecutionUseCase`, `ServiceOrder.completeExecution`, `ServiceExecution.complete` |

## Problema e resultado esperado

Depois que uma `ServiceExecution` está `in_progress` e o trabalho termina, a oficina precisa marcar a
execução como concluída, para que o tracking (RF23) reflita o avanço e a `ServiceOrder` só fique
elegível para entrega (RF24) quando todas as suas execuções não-rejeitadas estiverem concluídas.

Resultado esperado: dado o ID de uma `ServiceOrder` e de uma `ServiceExecution` dela que esteja no
status `in_progress`, o sistema marca essa execução como `completed`, recalcula o `statusSnapshot` da
`ServiceOrder` e retorna a Service Order atualizada.

**Nota sobre o estado atual do código:** este comportamento já está implementado
(`CompleteExecutionUseCase`, endpoint `POST /api/service-orders/{id}/executions/{executionId}/complete`,
`ServiceExecution.complete()`), mas foi escrito antes do gate de SDD adotado pelo projeto — sem spec
dedicada e com cobertura de teste parcial: `ServiceOrderTest` já cobre o efeito da conclusão sobre o
`statusSnapshot` (incluindo o caso de execuções rejeitadas sendo ignoradas), e `ServiceExecutionTest`
já cobre a rejeição de `complete()` numa execução que não iniciou — mas não existe
`CompleteExecutionUseCaseTest` nem teste HTTP para o endpoint, e o endpoint não tem `@ApiResponses`
(diferente de `assignTechnician`/`startExecution`/`updateExecutionProgress`). Esta spec documenta o
comportamento esperado de RF22 para validá-lo formalmente contra o requisito, identificar lacunas e
servir de base para a cobertura de teste que falta — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Technician | Marca como concluída uma `ServiceExecution` em que estava trabalhando (`in_progress`) |
| Service Advisor / Manager | Pode disparar a conclusão em nome do Technician (nenhuma fonte restringe o ator — mesma lacuna de AD-016 já registrada em RF20/RF21) |
| Customer | Indiretamente: pode ver (via tracking, RF23) que a `ServiceOrder` passou a `COMPLETED` quando todas as execuções não-rejeitadas terminarem |

### Cenário principal

1. A `ServiceOrder` existe e possui uma `ServiceExecution` no status `in_progress`.
2. O ator informa o `serviceOrderId` e o `serviceExecutionId`.
3. O sistema muda o status da `ServiceExecution` para `completed` e recalcula o `statusSnapshot` da
   `ServiceOrder`: se todas as execuções não-rejeitadas estiverem `completed`, o `statusSnapshot` vira
   `COMPLETED`; caso contrário, permanece o que a precedência de `recomputeStatusSnapshot` determinar
   (ex.: `IN_PROGRESS` se ainda houver outra execução em andamento).

## Regras de negócio

- Uma `ServiceExecution` só pode ser concluída (`complete`) quando seu status atual é exatamente
  `in_progress`. **Comportamento atual do código**, preservado por esta spec: qualquer outro status
  (`pending`, `authorized`, `awaiting_part`, `ready`, `completed`, `rejected`) rejeita a transição.
- Concluir a execução recalcula o `statusSnapshot` da `ServiceOrder` (AD-010, Option B: recomputado em
  comando, não em leitura — comportamento preservado, decisão de time ainda pendente).
- O `statusSnapshot` só vira `COMPLETED` quando **todas** as execuções não-rejeitadas da
  `ServiceOrder` estão `completed` (`allNonRejectedExecutionsCompleted`); execuções `rejected` são
  ignoradas nesse cálculo — comportamento já coberto por
  `ServiceOrderTest.rejectedExecutionsAreIgnoredWhenComputingCompletion`.
- A operação não é idempotente: concluir uma execução já `completed` falha (não é um no-op silencioso),
  pois `requireStatus(IN_PROGRESS)` compara igualdade estrita.

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

- **Quem pode concluir a execução:** mesma lacuna de AD-016 (identidade e autorização) já registrada em
  RF20/RF21 — nenhuma fonte restringe o ator a um Technician autenticado especificamente atribuído
  àquela execução.
- **Efeito sobre disponibilidade do Technician (`TechnicianStatus`):** concluir uma execução não altera
  o `TechnicianStatus` (`AVAILABLE`/`BUSY`/`INACTIVE`) do Technician atribuído no código atual. Ampliar
  esse comportamento tocaria AD-006 (Technician: aggregate vs. ator), ainda `Team Decision Required`; a
  regra local proíbe ampliar o domínio de Technician enquanto isso não for resolvido. Não decidido
  nesta spec.
- **Registrar timestamp/duração de execução:** não modelado no domínio atual e não pedido pelo Miro
  para RF22.
- **Notificar o Customer quando a `ServiceOrder` inteira fica `COMPLETED`:** depende de AD-015
  (estratégia de tracking) e do `ADR-001-realtime-updates-strategy.md`, ambos ainda pendentes de
  ratificação pelo time; esta spec não implementa notificação alguma.

## Fora de escopo

- Autorização de quem pode concluir — depende de AD-016.
- Alterar `TechnicianStatus` ao concluir a execução — depende de AD-006.
- Registrar timestamp/duração de execução — não modelado no domínio atual.
- Notificações em tempo real de conclusão — depende de AD-015 e `ADR-001-realtime-updates-strategy.md`.
- Qualquer mudança na regra de `allNonRejectedExecutionsCompleted` ou na precedência de
  `recomputeStatusSnapshot` — comportamento já implementado e já coberto por teste; esta feature apenas
  fecha a lacuna de cobertura no nível de use case/HTTP e a lacuna de documentação Swagger.
- RF24 (finalizar/entregar a `ServiceOrder`) — feature separada, já com sua própria cobertura em
  `ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered`.

## Critérios de aceite

- [x] Concluir uma `ServiceExecution` existente cujo status é `in_progress` muda seu status para
      `completed` e é refletido na resposta da `ServiceOrder`. Evidência:
      `CompleteExecutionUseCaseTest.completesAnInProgressExecutionAndMovesServiceOrderToCompleted` e
      `ServiceOrderControllerCompleteExecutionTest.completesAnInProgressExecutionAndReturns200`.
- [x] Tentar concluir uma `ServiceExecution` em qualquer status diferente de `in_progress` falha com
      erro de negócio explícito, mapeado para `409 INVALID_STATE_TRANSITION`. Evidência:
      `ServiceExecutionTest.cannotCompleteAnExecutionThatHasNotStarted`,
      `CompleteExecutionUseCaseTest.rejectsCompletingAnExecutionThatIsNotInProgress` e
      `ServiceOrderControllerCompleteExecutionTest.returnsConflictWhenExecutionHasNotStartedYet`.
- [x] Concluir a execução de uma `ServiceExecution`/`ServiceOrder` inexistente retorna erro `not-found`
      estável (`404 NOT_FOUND`). Evidência:
      `CompleteExecutionUseCaseTest.rejectsCompletingWhenServiceOrderDoesNotExist` e dois testes em
      `ServiceOrderControllerCompleteExecutionTest`
      (`returnsNotFoundWhenServiceOrderDoesNotExist`, `...WhenServiceExecutionDoesNotExist`).
- [x] Quando a `ServiceExecution` concluída é a última não-rejeitada pendente, o `statusSnapshot` da
      `ServiceOrder` retornado na resposta HTTP é `COMPLETED`. Evidência: já existente no nível de
      domínio (`ServiceOrderTest.rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone`,
      `rejectedExecutionsAreIgnoredWhenComputingCompletion`); evidência de aplicação/HTTP:
      `CompleteExecutionUseCaseTest.completesAnInProgressExecutionAndMovesServiceOrderToCompleted` e
      `ServiceOrderControllerCompleteExecutionTest.completesAnInProgressExecutionAndReturns200`.
- [x] O endpoint `POST /api/service-orders/{id}/executions/{executionId}/complete` passa a documentar
      seus códigos de resposta via `@ApiResponses`, no mesmo padrão de `assignTechnician`/
      `startExecution`/`updateExecutionProgress`.

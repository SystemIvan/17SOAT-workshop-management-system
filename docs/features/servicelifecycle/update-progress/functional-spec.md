# Especificação Funcional: Atualizar progresso de uma execução em andamento

| Campo | Valor |
|---|---|
| Feature | `update-progress` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-19 |
| Referências | RF21 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/features/servicelifecycle/start-execution/functional-spec.md` (RF20); `docs/Architecture-Decisions.md` (AD-006, AD-010, AD-015); `docs/adr/ADR-001-realtime-updates-strategy.md`; `.claude/rules/epic-3-service-lifecycle.md`; código atual: `UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress`, `ServiceExecution.updateProgress` |

## Problema e resultado esperado

Depois que uma `ServiceExecution` está `in_progress`, o Technician precisa registrar avanços do
trabalho para que o tracking (RF23) e o Customer tenham visibilidade do que está acontecendo, sem
que isso, por si só, mude o status da execução.

Resultado esperado: dado o ID de uma `ServiceOrder`, de uma `ServiceExecution` dela que esteja no
status `in_progress`, e uma nota de progresso não vazia, o sistema aceita o registro e retorna a
Service Order atualizada.

**Nota sobre o estado atual do código:** este comportamento já está implementado
(`UpdateExecutionProgressUseCase`, endpoint `PATCH /api/service-orders/{id}/executions/{executionId}/progress`,
`ServiceExecution.updateProgress()`), mas foi escrito antes do gate de SDD adotado pelo projeto —
sem spec e sem nenhuma cobertura de teste dedicada (não há `UpdateExecutionProgressUseCaseTest` nem
teste HTTP para o endpoint). Esta spec documenta o comportamento esperado de RF21 para validá-lo
formalmente contra o requisito, identificar lacunas e servir de base para a cobertura de teste que
falta — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Technician | Registra uma nota de progresso em uma `ServiceExecution` que está `in_progress` |
| Service Advisor / Manager | Pode disparar o registro em nome do Technician (nenhuma fonte restringe o ator — mesma lacuna de AD-016 já registrada em RF20) |
| Customer | Indiretamente: nenhuma fonte hoje expõe a nota via tracking (RF23) — ver lacuna abaixo |

### Cenário principal

1. A `ServiceOrder` existe e possui uma `ServiceExecution` no status `in_progress`.
2. O ator informa `serviceOrderId`, `serviceExecutionId` e uma `note` não vazia.
3. O sistema valida a transição (execução precisa estar `in_progress`) e retorna a `ServiceOrder`
   atualizada. O status da `ServiceExecution` permanece `in_progress`.

## Regras de negócio

- Registrar progresso só é permitido quando o status atual da `ServiceExecution` é exatamente
  `in_progress`. **Comportamento atual do código**, preservado por esta spec: qualquer outro status
  (`pending`, `authorized`, `awaiting_part`, `ready`, `completed`, `rejected`) rejeita a operação.
- A `note` é obrigatória e não pode ser vazia/branco (`@NotBlank` no request), mas **não é
  persistida em nenhum lugar do domínio** — `ServiceExecution.updateProgress` apenas valida o status
  e descarta a nota (comentário no código: "progress notes are not modeled as domain state yet -
  guard is what matters here"). A resposta (`ServiceOrderResponse`/`ServiceExecutionResponse`) não
  expõe a nota de volta.
- Diferente de `startExecution`/`completeExecution`, `updateExecutionProgress` **não chama**
  `recomputeStatusSnapshot`. Isso é consistente com o fato de que o status da `ServiceExecution` não
  muda nesta operação (então o `statusSnapshot` da `ServiceOrder` não teria como mudar), mas é uma
  assimetria em relação aos outros comandos que vale documentar explicitamente, não assumir como
  intencional além do que o resultado observável já garante.
- A operação não é idempotente no sentido de acumular histórico: como nada é persistido, chamadas
  repetidas com notas diferentes produzem o mesmo efeito observável (nenhum).

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

- **Onde a nota de progresso deveria ficar visível:** nem o Miro (RF21) nem o código atual definem
  se a nota deve ser armazenada (histórico de progresso), exposta no tracking (RF23) ou é
  puramente transiente/de auditoria externa (ex.: log). O código atual a descarta. **Pendente:**
  confirmar com o time se isso é uma lacuna de MVP a fechar ou comportamento aceitável; esta spec
  não decide.
- **Quem pode registrar progresso:** mesma lacuna de AD-016 (identidade e autorização) já registrada
  em RF20 — nenhuma fonte restringe o ator a um Technician autenticado especificamente atribuído
  àquela execução.
- **Múltiplas notas por execução / histórico:** não modelado; não há suporte a mais de um registro
  "atual" porque nada é persistido.

## Fora de escopo

- Persistir ou expor a nota de progresso (ver lacuna acima) — pendente de decisão de time, não
  implementado nem proibido explicitamente por esta spec.
- Qualquer alteração no `statusSnapshot` da `ServiceOrder` como efeito desta operação.
- Autorização de quem pode registrar progresso — depende de AD-016.
- Estratégia de tracking em tempo real (polling/cache/WebSocket) — depende de AD-015 e do
  `docs/adr/ADR-001-realtime-updates-strategy.md`; esta feature não implementa notificação alguma.

## Critérios de aceite

- [x] Registrar progresso em uma `ServiceExecution` cujo status é `in_progress` é aceito (200) e
      retorna a `ServiceOrder` com a execução ainda em `IN_PROGRESS`. Evidência:
      `ServiceExecutionTest.canUpdateProgressOfAnInProgressExecution`,
      `UpdateExecutionProgressUseCaseTest.updatesProgressOfAnInProgressExecution` e
      `ServiceOrderControllerUpdateProgressTest.updatesProgressOfAnInProgressExecutionAndReturns200`.
- [x] Tentar registrar progresso em uma `ServiceExecution` em qualquer status diferente de
      `in_progress` falha com erro de negócio explícito, mapeado para `409 INVALID_STATE_TRANSITION`.
      Evidência (subconjunto exercitado: `pending` e `ready`, cobertos por
      `ServiceExecution.requireStatus` para os demais):
      `ServiceExecutionTest.cannotUpdateProgressOfAnExecutionThatHasNotStarted`,
      `ServiceExecutionTest.cannotUpdateProgressOfAReadyExecutionThatHasNotStartedYet`,
      `UpdateExecutionProgressUseCaseTest.rejectsUpdatingProgressOfAnExecutionThatIsNotInProgress` e
      `ServiceOrderControllerUpdateProgressTest.returnsConflictWhenExecutionHasNotStartedYet`.
- [x] Registrar progresso com `note` vazia/em branco falha com `400` (validação de request).
      Evidência: `ServiceOrderControllerUpdateProgressTest.returnsBadRequestWhenNoteIsBlank`.
- [x] Registrar progresso em uma `ServiceOrder` ou `ServiceExecution` inexistente retorna erro
      `not-found` estável (`404 NOT_FOUND`). Evidência:
      `UpdateExecutionProgressUseCaseTest.rejectsUpdatingProgressWhenServiceOrderDoesNotExist` e dois
      testes em `ServiceOrderControllerUpdateProgressTest`
      (`returnsNotFoundWhenServiceOrderDoesNotExist`, `...WhenServiceExecutionDoesNotExist`).
- [x] A decisão sobre persistir/expor a nota de progresso continua explicitamente pendente e não foi
      resolvida por esta implementação — nenhum campo novo foi adicionado ao domínio ou à resposta.

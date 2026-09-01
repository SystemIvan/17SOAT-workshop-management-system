# Especificação Funcional: Iniciar execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `start-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-26 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-26 |
| Referências | RF20 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/features/servicelifecycle/assign-technician/functional-spec.md` (RF19, seção "Regras que a Ubiquitous Language e o código atual NÃO definem"); `docs/Architecture-Decisions.md` (AD-006, AD-010); `.claude/rules/epic-3-service-lifecycle.md`; código atual: `StartExecutionUseCase`, `ServiceOrder.startExecution`, `ServiceExecution.start` |

## Revisão aprovada: Technician obrigatório

Além de `READY`, uma execução deve possuir `assignedTechnicianId` para poder iniciar. O estado
`AWAITING_ITEMS` substitui `AWAITING_PART` para a espera de reserva, e atraso na separação ou retirada
física jamais regride uma execução `READY` para `AWAITING_ITEMS`.

## Problema e resultado esperado

Depois que uma `ServiceExecution` fica pronta para trabalho (peças reservadas, quando aplicável), a
oficina precisa marcar o início efetivo do serviço, para que o tracking (RF23) reflita que o Technician
está atuando na execução e para habilitar o próximo passo do ciclo (registrar progresso, RF21).

Resultado esperado: dado o ID de uma `ServiceOrder` e de uma `ServiceExecution` dela que esteja no
status `ready` e possua Technician atribuído, o sistema marca essa execução como `in_progress` e retorna
a Service Order atualizada.

**Nota sobre o estado atual do código:** este comportamento já está implementado
(`StartExecutionUseCase`, endpoint `POST /api/service-orders/{id}/executions/{executionId}/start`,
`ServiceExecution.start()`), mas foi escrito antes do gate de SDD adotado pelo projeto — sem spec e sem
cobertura de teste dedicada (só é exercitado indiretamente em
`ServiceOrderControllerAssignTechnicianTest.authorizeAndComplete`). Esta spec documenta o comportamento
esperado de RF20 para validá-lo formalmente contra o requisito, identificar lacunas e servir de base
para a cobertura de teste que falta — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Technician | Inicia o trabalho em uma `ServiceExecution` que está pronta (`ready`) |
| Service Advisor / Manager | Pode disparar o início em nome do Technician (nenhuma fonte restringe o ator ao próprio Technician — ver AD-016) |
| Customer | Indiretamente: pode ver (via tracking, RF23) que uma execução passou a `in_progress` |

### Cenário principal

1. A `ServiceOrder` existe e possui uma `ServiceExecution` no status `ready`, com um Technician
   atribuído (autorizada e, se exigir peças, com todos os `StockRequirement` reservados).
2. O ator informa o `serviceOrderId` e o `serviceExecutionId`.
3. O sistema muda o status da `ServiceExecution` para `in_progress` e retorna a `ServiceOrder`
   atualizada, com o `statusSnapshot` recalculado.

## Regras de negócio

- Uma `ServiceExecution` só pode iniciar (`start`) quando seu status atual é exatamente `ready` e
  `assignedTechnicianId` está preenchido. Qualquer outro status (`pending`, `authorized`,
  `awaiting_items`, `in_progress`, `completed`, `rejected`) ou a ausência de Technician atribuído
  rejeita a transição com erro de negócio.
- `diagnosisAssigneeId` identifica a responsabilidade pelo diagnóstico e não substitui
  `assignedTechnicianId` como responsável pela execução.
- Iniciar a execução recalcula o `statusSnapshot` da `ServiceOrder` (AD-010, Option B: recomputado em
  comando, não em leitura — comportamento preservado, decisão de time ainda pendente).
- A operação é idempotente apenas no sentido de rejeitar reinício: chamar `start` numa execução já
  `in_progress` falha (não é um no-op silencioso), pois `requireStatus(READY)` compara igualdade estrita.

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

Estas questões ficam registradas como avaliação necessária, sem virar requisito nem decisão nesta spec:

- **Quem pode iniciar a execução:** nenhuma fonte (Miro RF20, código, testes) restringe o ator a um
  Technician autenticado especificamente atribuído àquela execução. Depende de AD-016 (identidade e
  autorização), ainda `Team Decision Required`. Esta spec não assume nem implementa tal restrição.
- **Efeito sobre disponibilidade do Technician (`TechnicianStatus`):** iniciar uma execução não altera
  o `TechnicianStatus` (`AVAILABLE`/`BUSY`/`INACTIVE`) do Technician atribuído no código atual. Ampliar
  esse comportamento tocaria AD-006 (Technician: aggregate vs. ator), ainda `Team Decision Required`; a
  regra local (`.claude/rules/epic-3-service-lifecycle.md`) proíbe ampliar o domínio de Technician
  enquanto isso não for resolvido. Não decidido nesta spec.

## Fora de escopo

- Qualquer mudança em `AssignTechnicianUseCase` (RF19, feature separada).
- Alterar `TechnicianStatus` ao iniciar a execução — depende de AD-006.
- Autorização de quem pode iniciar — depende de AD-016.
- Registrar timestamp de início (`startedAt`) ou qualquer auditoria de tempo — não modelado no domínio
  atual e não pedido pelo Miro para RF20.

## Critérios de aceite

- [x] Iniciar uma `ServiceExecution` existente cujo status é `ready` e que possui Technician atribuído
      muda seu status para `in_progress` e é refletido na resposta da `ServiceOrder`. Evidência:
      `StartExecutionUseCaseTest.startsAReadyExecutionAndMovesItToInProgress` e
      `ServiceOrderControllerStartExecutionTest.startsAReadyExecutionAndReturns200`.
- [x] Tentar iniciar uma `ServiceExecution` sem Technician atribuído falha com erro de negócio explícito,
      mapeado para `409 INVALID_STATE_TRANSITION`, sem alterar o status `ready`. Evidência:
      `ServiceExecutionTest.cannotStartAReadyExecutionWithoutAnAssignedTechnician`,
      `StartExecutionUseCaseTest.rejectsStartingAReadyExecutionWithoutAnAssignedTechnician` e
      `ServiceOrderControllerStartExecutionTest.returnsConflictWhenReadyExecutionHasNoAssignedTechnician`.
- [x] Tentar iniciar uma `ServiceExecution` em qualquer status diferente de `ready` falha com erro de
      negócio explícito, mapeado para `409 INVALID_STATE_TRANSITION`. Evidência:
      `StartExecutionUseCaseTest.rejectsStartingAnExecutionThatIsNotReady` e
      `ServiceOrderControllerStartExecutionTest.returnsConflictWhenExecutionIsNotAuthorizedYet`.
- [x] Iniciar a execução de uma `ServiceExecution`, `ServiceOrder` inexistente retorna erro `not-found`
      estável (`404 NOT_FOUND`). Evidência:
      `ServiceOrderControllerStartExecutionTest.returnsNotFoundWhenServiceOrderDoesNotExist`.
- [ ] Atraso na separação ou retirada física dos materiais não regride uma execução `READY` para
      `AWAITING_ITEMS` nem muda sua pré-condição de início.

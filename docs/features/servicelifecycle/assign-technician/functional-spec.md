# Especificação Funcional: Confirmar atribuição de Technician a ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `assign-technician` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-16 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-16 |
| Referências | RF19 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/Architecture-Decisions.md` (AD-006); `.claude/rules/epic-3-service-lifecycle.md`; código atual: `AssignTechnicianUseCase`, `ServiceOrder.confirmTechnicianAssignment`, `ServiceExecution.confirmTechnicianAssignment` |

## Problema e resultado esperado

Depois que uma `ServiceExecution` é autorizada (linha da Estimate aprovada), a oficina precisa registrar
qual Technician ficará responsável por executá-la, para que o trabalho possa começar e para que o
Customer e o Service Advisor saibam quem está atuando em cada serviço.

Resultado esperado: dado o ID de uma `ServiceOrder` e de uma `ServiceExecution` dela, e o ID de um
Technician, o sistema registra essa atribuição na execução e a expõe nas consultas da Service Order.

**Nota sobre o estado atual do código:** este comportamento já está implementado
(`AssignTechnicianUseCase`, endpoint `POST /api/service-orders/{id}/executions/{executionId}/assign-technician`).
Esta spec documenta o comportamento esperado de RF19 para validá-lo formalmente contra o requisito e
identificar lacunas — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Service Advisor / Manager | Atribui um Technician a uma ServiceExecution já diagnosticada |
| Technician | É atribuído a uma execução; passa a aparecer como responsável por ela |
| Customer | Indiretamente: pode ver (via tracking, RF23) que uma execução tem um Technician responsável |

### Cenário principal

1. A `ServiceOrder` existe e possui ao menos uma `ServiceExecution` em um status que ainda admite
   trabalho (ver "Regras de negócio").
2. O ator informa o `serviceOrderId`, o `serviceExecutionId` e o `technicianId`.
3. O sistema associa o `technicianId` à `ServiceExecution` e retorna a `ServiceOrder` atualizada.

## Regras de negócio

- Uma `ServiceExecution` pode ter seu Technician atribuído (ou reatribuído) enquanto seu status não for
  `completed` nem `rejected`. **Comportamento atual do código**, preservado por esta spec: nenhum status
  específico (`pending`, `authorized`, `awaiting_part`, `ready`, `in_progress`) é exigido além de excluir
  os dois terminais.
- A atribuição registra apenas o `technicianId` (`UUID`) na `ServiceExecution`; não há cópia de nome ou
  outros dados do Technician (sem snapshot), consistente com a referência por ID exigida entre módulos.
- Reatribuir um Technician diferente ao mesmo `serviceExecutionId` sobrescreve a atribuição anterior sem
  histórico. Nenhuma fonte (Miro RF19, código, testes) define um comportamento diferente.

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

Estas questões ficam registradas como avaliação necessária, sem virar requisito nem decisão nesta spec:

- **Existência do Technician:** ~~pendente~~ — resolvido na `technical-spec.md` (`Approved` em
  2026-08-16) e implementado: `AssignTechnicianUseCase` agora valida via `TechnicianRepository.findById`
  antes de gravar a atribuição, sem inspecionar `status()`/`specialties()`. Isso não decide AD-006
  (é só checagem de existência, não de disponibilidade/especialidade).
- **Disponibilidade/especialidade do Technician:** `Technician` já modela `TechnicianStatus`
  (`AVAILABLE`/`BUSY`/`INACTIVE`) e `Specialty`, mas `confirmTechnicianAssignment` não os consulta.
  Validar disponibilidade ou especialidade nesta feature ampliaria o uso do domínio de Technician além
  do que já existe — a regra local (`.claude/rules/epic-3-service-lifecycle.md`) proíbe isso enquanto
  **AD-006** (Technician: aggregate/módulo vs. ator autenticado) permanecer `Team Decision Required`.
  Esta spec não decide essa questão; ela é whole-team.
- **Pré-requisito de atribuição para iniciar a execução:** `StartExecutionUseCase`/`ServiceOrder.startExecution`
  hoje exigem apenas status `READY`; não verificam se `assignedTechnicianId` foi preenchido. RF19 e RF20
  são requisitos distintos no Miro e nada indica que um bloqueia o outro. **Pendente:** confirmar com o
  time se iniciar uma execução sem Technician atribuído é um comportamento aceitável do MVP ou uma
  lacuna a fechar.

## Fora de escopo

- Validação de disponibilidade ou especialidade do Technician (ver seção acima) — depende de AD-006.
  (Validação de existência foi implementada — ver seção acima.)
- Qualquer mudança em `StartExecutionUseCase` para exigir atribuição prévia — é RF20, feature separada.
- Histórico de reatribuições (quem atribuiu, quando, atribuição anterior).
- Notificar o Technician ou o Customer sobre a atribuição (fora do escopo de Notification, RF31–RF33,
  que hoje só cobre Estimate gerada, baixo estoque e SO finalizada).
- Autorização/permissão de quem pode atribuir (depende de AD-016, identidade/autorização, ainda Team
  Decision Required).

## Critérios de aceite

- [x] Atribuir um Technician a uma `ServiceExecution` existente, em status diferente de `completed` ou
      `rejected`, associa o `technicianId` informado e é refletido na resposta da `ServiceOrder`.
      Evidência: `AssignTechnicianUseCaseTest.assignsExistingTechnicianToAnAuthorizedExecution` e
      `ServiceOrderControllerAssignTechnicianTest.assignsTechnicianToAPendingExecutionAndReturns200`.
- [x] Tentar atribuir um Technician a uma `ServiceExecution` em status `completed` ou `rejected` falha
      com erro de negócio explícito, mapeado para `409 INVALID_STATE_TRANSITION` em
      `GlobalExceptionHandler`. Evidência: `ServiceOrderControllerAssignTechnicianTest
      .returnsConflictWhenExecutionIsRejected` e `...returnsConflictWhenExecutionIsCompleted`.
- [x] Reatribuir um Technician diferente a uma execução já atribuída substitui o `technicianId` anterior.
      Evidência: comportamento preservado de `ServiceExecution.confirmTechnicianAssignment` (sem mudança);
      coberto indiretamente pelo teste de domínio existente, não reexplicitado nesta feature.
- [x] Atribuir a uma `ServiceExecution`, `ServiceOrder` ou Technician inexistente retorna erro `not-found`
      estável (`404 NOT_FOUND`). Evidência: três testes em `ServiceOrderControllerAssignTechnicianTest`
      (`returnsNotFoundWhenServiceOrderDoesNotExist`, `...WhenServiceExecutionDoesNotExist`,
      `...WhenTechnicianDoesNotExist`).
- [x] A decisão sobre validar disponibilidade/especialidade do Technician continua explicitamente
      pendente (AD-006) e não foi resolvida por esta implementação — apenas existência foi validada, sem
      inspecionar `status()`/`specialties()` (ver `technical-spec.md`).

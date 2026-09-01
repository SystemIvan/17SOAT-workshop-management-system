# Especificação Funcional: Atribuir responsável planejado pelo diagnóstico

| Campo | Valor |
|---|---|
| Feature | `assign-diagnosis-assignee` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | RFC-002; features `perform-diagnosis` e `assign-technician` |

> Esta draft propõe exigir `diagnosisAssigneeId` antes de cada Diagnosis. A aprovação desta especificação confirma a
> decisão e autoriza bloquear o registro de diagnóstico em Service Orders sem responsável planejado.

## Problema e resultado esperado

Antes da inspeção técnica, o Service Advisor precisa indicar qual Technician está planejado para realizar o próximo
Diagnosis da Service Order. Hoje existe apenas `assignedTechnicianId` em cada Service Execution, mas essas execuções
ainda não existem no momento do planejamento.

Ao final da atribuição, a Service Order guarda `diagnosisAssigneeId`, expõe o planejamento nas consultas e pode receber
o próximo Diagnosis. O responsável por executar cada serviço continua sendo definido separadamente em cada Service
Execution.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Service Advisor | Atribui ou reatribui um Technician para realizar o próximo Diagnosis |
| Technician | Consulta que está planejado para diagnosticar a Service Order |

### Cenário principal

1. A Service Order existe e não possui um Diagnosis aberto.
2. O Service Advisor informa um Technician existente como `diagnosisAssigneeId`.
3. O sistema registra a atribuição planejada e devolve a Service Order atualizada.
4. O próximo Diagnosis pode ser registrado.

### Cenário alternativo — reatribuição antes do diagnóstico

1. A Service Order já possui `diagnosisAssigneeId`, mas o próximo Diagnosis ainda não foi iniciado.
2. O Service Advisor escolhe outro Technician existente.
3. O sistema substitui o planejamento anterior sem alterar Service Executions existentes.

### Cenário de erro — diagnóstico sem atribuição

1. Um ator tenta registrar um Diagnosis sem `diagnosisAssigneeId` definido para o ciclo.
2. O sistema rejeita a operação e não cria Service Executions.

## Regras de negócio

- `diagnosisAssigneeId` pertence à Service Order porque o planejamento antecede a criação das Service Executions.
- O Technician informado deve existir; um identificador desconhecido rejeita a atribuição sem mudar a Service Order.
- A atribuição ou reatribuição é permitida somente quando não há Diagnosis aberto.
- O campo representa o responsável planejado para o próximo Diagnosis e pode ser substituído entre ciclos de
  diagnóstico, depois que o ciclo anterior deixar de estar aberto.
- Registrar um Diagnosis exige que `diagnosisAssigneeId` esteja preenchido.
- A divergência entre o responsável planejado e o Technician que efetivamente realiza o Diagnosis não bloqueia o
  registro. A autoria efetiva é auditada separadamente por `diagnosedByTechnicianId`.
- `diagnosisAssigneeId` não preenche nem altera `assignedTechnicianId` de nenhuma Service Execution.
- A atribuição planejada não altera `statusSnapshot`; iniciar o Diagnosis continua sendo a ação que leva a SO a
  `IN_DIAGNOSIS` quando nenhuma fase de maior precedência estiver ativa.
- Esta feature não cria histórico das atribuições planejadas. A autoria efetiva de cada ciclo permanece preservada nas
  Service Executions correspondentes.

## Fora de escopo

- atribuir o Technician que executará cada serviço, responsabilidade de `assign-technician`;
- exigir que o autor efetivo do Diagnosis seja igual ao responsável planejado;
- validar disponibilidade, agenda ou especialidade do Technician;
- notificar o Technician atribuído;
- autenticação e autorização de quem pode atribuir ou reatribuir;
- alterar `start-execution`, Estimate ou Stock Reservation.

## Critérios de aceite

- [ ] Atribuir um Technician existente a uma Service Order sem Diagnosis aberto registra e retorna
      `diagnosisAssigneeId`.
- [ ] Reatribuir antes do próximo Diagnosis substitui `diagnosisAssigneeId` sem modificar Service Executions.
- [ ] Atribuir um Technician inexistente falha com erro de não encontrado e preserva o estado anterior.
- [ ] Tentar atribuir ou reatribuir enquanto existe Diagnosis aberto falha com erro de estado e preserva o estado
      anterior.
- [ ] Tentar registrar Diagnosis sem `diagnosisAssigneeId` falha sem criar Service Executions.
- [ ] Um autor efetivo diferente do `diagnosisAssigneeId` não impede o Diagnosis e não sobrescreve o planejamento.
- [ ] A atribuição planejada não altera `assignedTechnicianId` nem o `statusSnapshot` da Service Order.

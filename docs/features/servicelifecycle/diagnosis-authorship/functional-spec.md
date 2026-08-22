# Especificação Funcional: Registrar autoria efetiva do diagnóstico

| Campo | Valor |
|---|---|
| Feature | `diagnosis-authorship` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | `docs/rfc/RFC-002-service-order-intake-diagnosis-status-plan.md`; feature `perform-diagnosis` |

> Como o sistema ainda não possui uma identidade autenticada consolidada, esta draft propõe receber provisoriamente
> `diagnosedByTechnicianId` no comando de Diagnosis e validar a existência do Technician. A aprovação confirma essa
> origem temporária e sua limitação de auditoria até que autenticação e autorização sejam especificadas.

## Problema e resultado esperado

As Service Executions criadas por um Diagnosis compartilham hoje apenas `diagnosisId`. Isso permite identificar o lote,
mas não registra qual Technician efetivamente fez a avaliação nem quando ela foi registrada. Usar apenas
`diagnosisAssigneeId` perderia a divergência entre planejamento e execução real.

Ao registrar um Diagnosis, cada Service Execution criada no lote recebe o mesmo `diagnosisId`, o mesmo
`diagnosedByTechnicianId` e o mesmo `diagnosedAt`. Esses dados permanecem imutáveis como evidência da autoria efetiva.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Technician | Realiza o Diagnosis e é registrado como seu autor efetivo |
| Service Advisor | Registra provisoriamente o Diagnosis em nome do Technician que efetivamente o realizou |

### Cenário principal

1. A Service Order possui um `diagnosisAssigneeId` definido e não possui Diagnosis aberto.
2. O ator informa um Technician existente como `diagnosedByTechnicianId` e um ou mais itens diagnosticados.
3. O sistema registra um único instante `diagnosedAt` para a operação.
4. O sistema cria uma Service Execution por item, repetindo nas execuções o mesmo `diagnosisId`, autor e instante.

### Cenário alternativo — autor diferente do planejamento

1. O Technician que realizou o Diagnosis é diferente de `diagnosisAssigneeId`.
2. O sistema aceita o registro e preserva o ID informado como autoria efetiva.
3. A atribuição planejada não é usada para substituir ou inferir a autoria.

### Cenário de erro — autor inexistente

1. O ator informa um `diagnosedByTechnicianId` que não identifica um Technician existente.
2. O sistema rejeita todo o Diagnosis e não cria nenhuma Service Execution.

## Regras de negócio

- `diagnosedByTechnicianId` identifica o Technician que efetivamente realizou o Diagnosis.
- Enquanto não houver identidade autenticada aplicável, `diagnosedByTechnicianId` é obrigatório no comando de
  Diagnosis e sua existência é validada antes da criação das Service Executions.
- O valor informado não é inferido de `diagnosisAssigneeId` e pode ser diferente dele.
- `diagnosedAt` representa o instante em que o sistema aceitou o Diagnosis, é gerado pelo sistema e não pode ser
  escolhido pelo chamador.
- Todas as Service Executions do mesmo Diagnosis compartilham exatamente o mesmo `diagnosisId`,
  `diagnosedByTechnicianId` e `diagnosedAt`.
- Autoria e instante são imutáveis depois da criação da Service Execution.
- `diagnosedByTechnicianId` não preenche `assignedTechnicianId`; o responsável pela execução de cada serviço é uma
  atribuição independente e pode variar entre as execuções do mesmo Diagnosis.
- Uma falha de validação em qualquer dado do Diagnosis preserva atomicidade: nenhuma execução do lote é criada.

## Fora de escopo

- autenticação, autorização e obtenção da autoria a partir da identidade autenticada;
- comprovação externa de que o Technician informado realmente realizou a inspeção;
- assinatura digital, revisão ou correção posterior da autoria;
- atribuição do responsável planejado pelo Diagnosis;
- atribuição do Technician que executará cada Service Execution;
- mudanças em Estimate, Stock Reservation ou `start-execution`.

## Critérios de aceite

- [ ] Registrar um Diagnosis válido cria uma ou mais Service Executions com o mesmo `diagnosisId`,
      `diagnosedByTechnicianId` e `diagnosedAt`.
- [ ] `diagnosedAt` é definido pelo sistema uma única vez para o lote e não é aceito como entrada do chamador.
- [ ] O autor efetivo pode ser diferente de `diagnosisAssigneeId`, e ambos os valores permanecem distinguíveis.
- [ ] `diagnosedByTechnicianId` ausente ou associado a Technician inexistente rejeita todo o Diagnosis sem
      persistência parcial.
- [ ] Consultar a Service Order expõe autor e instante em cada Service Execution.
- [ ] Nenhuma Service Execution recebe `assignedTechnicianId` como efeito do registro da autoria.
- [ ] Autoria e instante de uma execução existente não podem ser alterados por operações posteriores.

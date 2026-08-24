# Especificação Funcional: Corrigir projeção de status da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-status-projection` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | `docs/rfc/RFC-002-service-order-intake-diagnosis-status-plan.md`; feature `track-execution` |

> Esta draft propõe mapear Service Execution `READY` para `statusSnapshot` `IN_PROGRESS` e considerar `COMPLETED` a
> Service Order cujas execuções foram todas rejeitadas. A aprovação confirma essas duas decisões funcionais.

## Problema e resultado esperado

Uma Service Order pode conter Service Executions em fases diferentes. O status geral precisa resumir a fase ativa mais
avançada sem esconder o detalhe operacional de cada execução. Hoje uma execução `READY` não produz `IN_PROGRESS`, e uma
SO cujas execuções foram todas rejeitadas pode voltar a `RECEIVED`, embora o ciclo já tenha sido decidido.

Ao consultar uma Service Order, o sistema apresenta uma projeção coerente em `statusSnapshot` e também as Service
Executions com seus estados individuais. A projeção segue uma única ordem de precedência para cenários simultâneos.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Customer | Consulta um resumo compreensível do andamento e o detalhe de cada serviço |
| Service Advisor / Manager | Acompanha a fase geral da SO sem perder situações operacionais simultâneas |
| Technician | Consulta quais execuções estão prontas, em andamento, bloqueadas ou encerradas |

### Cenário principal — estados simultâneos

1. A Service Order possui uma execução `IN_PROGRESS` e outra `AWAITING_ITEMS`.
2. O sistema apresenta `statusSnapshot` `IN_PROGRESS`, a fase ativa de maior precedência.
3. A resposta detalhada mantém cada execução em seu estado próprio, inclusive `AWAITING_ITEMS`.

### Cenário alternativo — trabalho pronto

1. A Service Order não possui execução em andamento, mas possui ao menos uma execução `READY`.
2. O sistema apresenta `statusSnapshot` `IN_PROGRESS` porque já existe trabalho liberado para execução.

### Cenário alternativo — todas as linhas rejeitadas

1. A Service Order possui uma ou mais Service Executions e todas estão `REJECTED`.
2. Não existe Diagnosis aberto nem decisão pendente.
3. O sistema apresenta `statusSnapshot` `COMPLETED`, permitindo que o fluxo explícito de entrega encerre a SO.

## Regras de negócio

### Precedência

Quando mais de uma condição for verdadeira, a primeira condição da lista prevalece:

1. `DELIVERED`, depois que a entrega do veículo for confirmada pelo fluxo de finalização;
2. `COMPLETED`, quando todas as execuções estiverem em estado terminal `COMPLETED` ou `REJECTED`, incluindo o caso em
   que todas foram rejeitadas;
3. `IN_PROGRESS`, quando existir ao menos uma execução `READY` ou `IN_PROGRESS`;
4. `AWAITING_ITEMS`, quando não houver execução `READY`/`IN_PROGRESS` e existir execução aguardando itens;
5. `AWAITING_APPROVAL`, quando houver linhas de Estimate enviadas e ainda sem decisão;
6. `IN_DIAGNOSIS`, enquanto existir Diagnosis aberto;
7. `RECEIVED`, quando nenhuma condição anterior for aplicável.

### Projeção e detalhe

- `statusSnapshot` é um resumo da Service Order e não substitui o status individual das Service Executions.
- A resposta detalhada da Service Order deve incluir `statusSnapshot` e a lista de execuções com seus status próprios.
- Para preservar clientes existentes, o campo atual `status` não é removido nesta feature. A estratégia de transição
  para o nome `statusSnapshot` será definida na especificação técnica sem alterar a semântica desta projeção.
- O endpoint resumido de status continua apresentando a mesma projeção geral.
- Operações que mudam uma condição da precedência devem refletir o novo snapshot na resposta e nas consultas
  posteriores.
- A correção da projeção não inicia uma Service Execution: `start-execution` continua sendo comando explícito e exige
  as próprias regras. `READY` é apenas mapeado para o resumo genérico `IN_PROGRESS`.
- Uma SO sem Service Executions não satisfaz `COMPLETED` e permanece `RECEIVED`, salvo condição de maior precedência.

## Fora de escopo

- mudar o status individual ou as transições permitidas de uma Service Execution;
- iniciar automaticamente uma execução `READY`;
- alterar geração ou decisão de Estimate;
- alterar Stock Reservation ou Purchase Order;
- agrupar execuções por Estimate;
- introduzir polling, cache, SSE ou WebSocket;
- autenticação e autorização dos endpoints de consulta;
- remover o campo HTTP existente `status` em uma mudança incompatível.

## Critérios de aceite

- [ ] `DELIVERED` prevalece sobre qualquer outro estado presente na Service Order.
- [ ] Uma SO com todas as execuções em `COMPLETED` ou `REJECTED`, inclusive todas rejeitadas, apresenta `COMPLETED`.
- [ ] Uma SO sem execuções não é considerada `COMPLETED`.
- [ ] Existência de execução `READY` ou `IN_PROGRESS` apresenta `IN_PROGRESS`, mesmo que outra execução esteja
      `AWAITING_ITEMS`.
- [ ] `AWAITING_ITEMS` prevalece sobre `AWAITING_APPROVAL`, `IN_DIAGNOSIS` e `RECEIVED` quando não há execução
      `READY`/`IN_PROGRESS`.
- [ ] `AWAITING_APPROVAL` prevalece sobre `IN_DIAGNOSIS` e `RECEIVED` quando não há fase mais avançada.
- [ ] Diagnosis aberto apresenta `IN_DIAGNOSIS` quando nenhuma fase de maior precedência está ativa.
- [ ] A ausência de qualquer condição anterior apresenta `RECEIVED`.
- [ ] A resposta detalhada apresenta `statusSnapshot` e os estados individuais das execuções sem remover o campo
      `status` existente.
- [ ] Mapear `READY` para o snapshot `IN_PROGRESS` não inicia a execução nem substitui o endpoint `start-execution`.

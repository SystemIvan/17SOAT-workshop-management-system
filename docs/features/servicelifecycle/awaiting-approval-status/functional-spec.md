# Especificação Funcional: OS em "Aguardando Aprovação" enquanto houver orçamento enviado sem decisão

| Campo | Valor |
|---|---|
| Feature | `awaiting-approval-status` (correção) |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-05 |
| Referências | `../service-order-status-projection/functional-spec.md` (regra 5, não implementada); `../status-nominal-mapping/functional-spec.md` (RF39); `../estimate-generation/`; `../decide-estimate-lines/`; `../estimate-lifecycle-status/functional-spec.md`; `../external-status-update/functional-spec.md` (RF40); `../notifications-so-status-change/implementation-plan.md` (Checkpoint 5, onde o bug foi encontrado) |

## Origem

Bug encontrado em 2026-10-05 pelo teste de módulo da RF52 (`notifications-so-status-change`, Checkpoint 5). A
spec aprovada `service-order-status-projection` define, na regra 5, que a OS fica em `AWAITING_APPROVAL` "quando
houver linhas de Estimate enviadas e ainda sem decisão". O aggregate `ServiceOrder` tem esse estado e a flag que o
controla (`hasSentEstimateWithPendingLines`, ligada por `markEstimateSentWithPendingLines()` e desligada por
`markEstimateFullyDecided()`). Porém, desde o commit inicial do modelo (`61b44da`), nenhum caso de uso chama esses
métodos. As "políticas reagindo a eventos de Estimate", citadas no comentário do aggregate, nunca foram
implementadas.

Decisão do responsável (Santiago Silvestre, 2026-10-05): corrigir numa branch própria, antes da RF52, e um
orçamento expirado devolve a OS para "Diagnóstico".

## Problema e resultado esperado

Hoje, ao gerar e enviar o orçamento, a OS continua em "Diagnóstico". Ao aprovar, ela pula direto para
"Execução". O estado "Aguardando Aprovação", um dos 6 nominais do RF39, nunca aparece:
- nem em `GET /api/service-orders/{id}/status`;
- nem na listagem;
- nem no e-mail de mudança de status da RF52.

Resultado esperado: enquanto existir orçamento enviado com linha pendente, a OS fica em "Aguardando Aprovação". Ela
sai desse estado quando o orçamento é totalmente decidido, por qualquer canal, ou quando expira.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Manager / Service Advisor | Gera o orçamento; a OS passa a "Aguardando Aprovação". |
| Customer (canal interno ou RF40) | Decide todas as linhas; a OS sai de "Aguardando Aprovação" para o estado que as decisões produzem (ex.: "Execução"). |
| Customer / oficina | Decide só parte das linhas; a OS continua em "Aguardando Aprovação" enquanto restar linha pendente. |
| Sistema (agendador de expiração) | Expira o orçamento enviado; a OS volta para "Diagnóstico". |

### Cenário principal

1. A OS está em "Diagnóstico" com um diagnóstico aberto.
2. O orçamento é gerado e enviado. A OS passa para "Aguardando Aprovação".
3. Todas as linhas são aprovadas. A OS passa para "Execução", ou para "Execução" com `AWAITING_ITEMS` se faltar
   estoque.

### Cenário alternativo — decisão parcial

1. A OS está em "Aguardando Aprovação" com um orçamento de duas linhas.
2. Uma linha é decidida. A OS continua em "Aguardando Aprovação", salvo se uma fase mais avançada prevalecer pela
   precedência já definida (ex.: a linha aprovada ficou `READY`, o que leva a "Execução").
3. A segunda linha é decidida. O orçamento fecha e a flag é desligada.

### Cenário alternativo — orçamento expirado

1. A OS está em "Aguardando Aprovação".
2. O prazo do orçamento termina e o agendador o marca como `EXPIRED`.
3. A OS volta para "Diagnóstico".

## Regras de negócio

1. **Entrada.** Quando um orçamento da OS passa a `SENT` com ao menos uma linha pendente, a OS passa a ter
   "orçamento enviado com linha pendente". Pela precedência já existente, isso resulta em `AWAITING_APPROVAL`,
   salvo se houver uma fase mais avançada (`AWAITING_ITEMS`, `IN_PROGRESS`, `COMPLETED`, `DELIVERED`).
2. **Saída por decisão.** Quando o orçamento deixa de ter linha pendente (fecha), a condição é reavaliada.
3. **Saída por expiração.** Quando o orçamento expira, a condição também é reavaliada. Com o diagnóstico ainda
   aberto, a OS volta para `IN_DIAGNOSIS` ("Diagnóstico"), conforme decisão do responsável.
4. **Mais de um orçamento enviado.** A condição vale enquanto existir **algum** orçamento `SENT` da OS com linha
   pendente. Fechar ou expirar um orçamento não tira a OS de "Aguardando Aprovação" se outro orçamento `SENT` ainda
   tiver linha pendente.
5. **Sem mudança na precedência.** A ordem de precedência de `statusSnapshot` e o mapeamento nominal do RF39 não
   mudam. Esta correção só passa a alimentar a condição que já existe.
6. **Efeito na RF52.** As transições "Diagnóstico → Aguardando Aprovação", "Aguardando Aprovação → Execução" e
   "Aguardando Aprovação → Diagnóstico" (expiração) passam a acontecer. Quando a RF52 estiver integrada, cada uma
   gera o e-mail de mudança de status correspondente.

## Decisões registradas

Ambas confirmadas por Santiago Silvestre em 2026-10-05, conforme as propostas abaixo.

1. **OS presa em "Diagnóstico" depois da expiração.** Depois de expirado o orçamento, a OS volta para
   "Diagnóstico", mas hoje não há como seguir adiante:
   - `GenerateEstimateUseCase` recusa um novo orçamento para o mesmo diagnóstico ("Estimate already exists for
     diagnosis");
   - `performDiagnosis` recusa um novo diagnóstico enquanto o anterior estiver aberto, e as execuções continuam
     `PENDING`.

   **Decisão: fora do escopo desta correção**, registrada como dívida técnica `TD-004` em `docs/tech-debt/`, com
   o fluxo de "reorçar após expiração" a ser especificado à parte.
2. **Dados já existentes.** OSs que já têm orçamento `SENT` no banco estão com a flag desligada e continuariam em
   "Diagnóstico". **Decisão: sem migration de backfill.** Os ambientes de demonstração são recriados
   (`make docker-reset`) e não há ambiente compartilhado com dados a preservar.

## Fora de escopo

- reorçar ou rediagnosticar uma OS cujo orçamento expirou (ver decisão pendente 1);
- alterar a precedência de `statusSnapshot`, o mapeamento do RF39 ou qualquer contrato HTTP;
- alterar as regras de decisão de linhas (RF15/RF16), do RF40 ou da expiração (AD-013);
- notificações por e-mail (RF52, outra branch).

## Critérios de aceite

- [ ] Dada uma OS em "Diagnóstico", quando o orçamento é gerado e enviado, então `statusSnapshot` passa a
      `AWAITING_APPROVAL` e o status nominal exposto é "Aguardando Aprovação".
- [ ] Quando todas as linhas pendentes são decididas pelo canal interno, a OS sai de `AWAITING_APPROVAL` para o
      estado que as decisões produzem pela precedência existente.
- [ ] O mesmo vale quando a decisão chega pelo RF40 (`external-status-updates`).
- [ ] Com decisão parcial, a OS permanece em `AWAITING_APPROVAL` enquanto restar linha pendente e nenhuma fase mais
      avançada prevalecer.
- [ ] Quando o orçamento enviado expira, a OS volta para `IN_DIAGNOSIS` ("Diagnóstico").
- [ ] Com dois orçamentos `SENT` com linhas pendentes, fechar ou expirar um deles mantém a OS em
      `AWAITING_APPROVAL`.
- [ ] Nenhuma mudança de contrato HTTP; a precedência e o mapeamento RF39 continuam os mesmos.

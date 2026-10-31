# Especificação Técnica: OS em "Aguardando Aprovação" enquanto houver orçamento enviado sem decisão

| Campo | Valor |
|---|---|
| Feature | `awaiting-approval-status` (correção) |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-05 |
| Especificação funcional | `./functional-spec.md` (Approved, 2026-10-05) |

## Contexto e desenho

Tudo acontece dentro do módulo `servicelifecycle` (`estimate` e `serviceorder` são o mesmo módulo Modulith). Não há
travessia de fronteira nova, evento novo nem mudança de schema.

O aggregate já tem o necessário: a flag `hasSentEstimateWithPendingLines`, persistida desde
`V20260815000000__initial_schema.sql`, e a precedência em `recomputeStatusSnapshot`. Falta quem liga e desliga a
flag. O comentário do aggregate fala em "policies reacting to Estimate domain events", mas os três pontos que
mudam o orçamento já carregam a OS na mesma transação. Por isso a correção chama o aggregate **diretamente nesses
use cases**, sem criar listeners:

| Use case | Momento | Chamada no aggregate |
|---|---|---|
| `GenerateEstimateUseCase` | depois de `estimate.markSent()` | `serviceOrder.markEstimateSentWithPendingLines()` |
| `DecideEstimateLinesUseCase` | quando o orçamento fecha (`estimate.close()`) | `serviceOrder.markEstimateFullyDecided()`, se não restar outro orçamento `SENT` da OS |
| `ExpireEstimatesUseCase` | depois de expirar os orçamentos de uma OS | `serviceOrder.markSentEstimateExpired()` (novo), se não restar outro orçamento `SENT` da OS |

O RF40 (`ApplyExternalStatusUpdateUseCase`) delega a `DecideEstimateLinesUseCase` e é coberto sem mudança.

**Por que chamada direta e não listener de evento:**
- `GenerateEstimateUseCase` e `DecideEstimateLinesUseCase` já carregam e salvam a OS na mesma transação. Um
  listener `@ApplicationModuleListener` rodaria depois do commit, em outra transação, e deixaria a OS com status
  defasado por um instante, além de abrir uma corrida com o próximo comando.
- A flag é parte do estado da OS que muda junto com o orçamento: deve ser atômica com ele.
- Não existe hoje evento de "orçamento fechado" nem "expirado"; criá-los só para isso seria abstração especulativa.

### Regra "outro orçamento `SENT`" (regra 4 da functional-spec)

Um orçamento `SENT` sempre tem ao menos uma linha pendente: quando a última linha é decidida, ele vira `CLOSED`. Por
isso, "existe outro orçamento `SENT` da OS" equivale a "existe outro orçamento enviado com linha pendente". A
verificação usa `EstimateRepository.findByServiceOrderIdAndStatus(serviceOrderId, SENT)`, já implementado pelo
RF40, e **filtra pelo id**, ignorando o orçamento que acabou de fechar ou expirar. Assim o resultado não depende de
o `save` ter sido descarregado no banco antes da consulta.

### Novo método de domínio

`ServiceOrder.markSentEstimateExpired()`: mesma mudança de estado de `markEstimateFullyDecided()` (desliga a flag e
recalcula), mas com nome que revela a intenção (`AGENTS.md`). Com o diagnóstico ainda aberto, o recálculo leva a OS
a `IN_DIAGNOSIS`, como decidido.

### `ExpireEstimatesUseCase`

- Passa a receber `ServiceOrderRepository` (construtor `@Autowired` e o construtor de teste com `Clock`).
- Fluxo:
  1. expira e salva cada orçamento candidato, como hoje;
  2. agrupa por `serviceOrderId`;
  3. para cada OS, se não restar orçamento `SENT`, carrega a OS com `findByIdForUpdate`, chama
     `markSentEstimateExpired()` e salva.
- O agrupamento evita reprocessar a mesma OS quando dois orçamentos dela expiram na mesma execução.
- Se a OS não existir, o caso é inconsistente: registra `WARN` com IDs e segue para as próximas, sem derrubar o job.

## Interfaces e fluxo de dados

- **Contrato HTTP:** nenhuma mudança de forma (campos, tipos, códigos). Muda o **valor observado** de
  `statusSnapshot`/`statusLabel` entre a geração e a decisão do orçamento: `AWAITING_APPROVAL` /
  `AGUARDANDO_APROVACAO`, que já são valores documentados e válidos desses campos. É a correção de um comportamento
  que contrariava a spec aprovada `service-order-status-projection`, não uma quebra de contrato. OpenAPI não muda.
- **Postman:** nenhuma asserção depende do status entre a geração e a decisão (verificado). A coleção não muda.
- **README:** o parágrafo "Embora `AWAITING_APPROVAL` exista..., a geração do orçamento preserva o diagnóstico
  aberto no contrato atual" passa a ser falso e será reescrito, junto com o roteiro de status da seção
  "Bifurcações e acompanhamento de status".
- **Listagem:** `AWAITING_APPROVAL` já tem `listingRank` e é aceito no filtro `status`; nada muda no código.

## Persistência e dados de bootstrap

Nenhuma mudança de schema. A flag já existe e já é mapeada. Classificação: **nenhum seed necessário**. Sem
migration de backfill (decisão 2 da functional-spec): OSs com orçamento `SENT` criadas antes da correção continuam
com a flag desligada até o orçamento fechar ou expirar.

## Segurança e operação

- Autorização, validação e exposição de dados: inalteradas. Nenhum endpoint novo, nenhum campo novo.
- Concorrência: `GenerateEstimateUseCase` e `DecideEstimateLinesUseCase` já carregam a OS com
  `findByIdForUpdate` (verificado). `ExpireEstimatesUseCase` passa a usar o mesmo bloqueio, para que a flag não seja
  sobrescrita por uma decisão concorrente.
- Logs: o `WARN` de OS inexistente no job de expiração traz só IDs.
- Rollback: `git revert`. Não há estado novo; a flag volta a ficar sempre desligada em novas OSs.

## Estratégia de testes

- **Domínio (`ServiceOrderTest`):** `markSentEstimateExpired()` leva de `AWAITING_APPROVAL` a `IN_DIAGNOSIS` com o
  diagnóstico aberto.
- **Use cases (unitários, com mocks):**
  - `GenerateEstimateUseCaseTest`: a OS salva fica `AWAITING_APPROVAL`.
  - `DecideEstimateLinesUseCaseTest`:
    - decisão total sem outro `SENT` desliga a flag;
    - decisão total com outro `SENT` mantém a flag;
    - decisão parcial mantém a flag.
  - `ExpireEstimatesUseCaseTest`:
    - expiração devolve a OS a `IN_DIAGNOSIS`;
    - com outro `SENT` da mesma OS, ela continua `AWAITING_APPROVAL`;
    - dois orçamentos da mesma OS expirando juntos geram um único recálculo;
    - OS inexistente gera `WARN` e o job segue.
  - `ApplyExternalStatusUpdateUseCaseTest`: continua verde, porque a delegação não muda.
- **Integração (`@SpringBootTest`, H2 + Flyway):** fluxo real de geração → `AWAITING_APPROVAL` persistido →
  decisão (interna e RF40) → `IN_PROGRESS`; e geração → expiração (`ExpireEstimatesUseCase` com `Clock` adiantado)
  → `IN_DIAGNOSIS`.
- **Testes existentes que assumem `IN_DIAGNOSIS` depois da geração:** ajustar ao comportamento correto, registrando
  cada um no plano. Não enfraquecer asserções.
- `ModuleStructureTest` e `OpenApiContractTest` verdes; `make verify`.
- **Efeito na RF52:** depois do merge deste fix, o teste de módulo da RF52 guardado em stash deve passar sem
  alteração.

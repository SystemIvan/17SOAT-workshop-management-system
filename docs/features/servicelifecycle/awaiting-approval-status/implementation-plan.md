# Plano de Implementação: OS em "Aguardando Aprovação" enquanto houver orçamento enviado sem decisão

| Campo | Valor |
|---|---|
| Feature | `awaiting-approval-status` (correção) |
| Status | In Progress |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Especificação técnica | `./technical-spec.md` (Approved, 2026-10-05) |

## Objetivo

Ligar a flag `hasSentEstimateWithPendingLines` de `ServiceOrder` nos três use cases que mudam o orçamento, para
que a OS fique em `AWAITING_APPROVAL` entre a geração e a decisão (ou expiração) do orçamento. Sem schema novo, sem
evento novo, sem mudança de forma no contrato HTTP.

Branch: `fix/servicelifecycle-awaiting-approval-status`, criada a partir de `dev` (`a00fec5`). Um commit por
checkpoint (Conventional Commits). Ao final, abrir PR para `dev` e, depois do merge, retomar o Checkpoint 5 da RF52
(`../notifications-so-status-change/implementation-plan.md`).

## Checkpoint 1 — Domínio ✅ (2026-10-06)

- `ServiceOrder.markSentEstimateExpired()`: desliga a flag e recalcula o status; intenção distinta de
  `markEstimateFullyDecided()`.
- Atualizar o comentário do aggregate que cita "policies reacting to Estimate domain events" para apontar os use
  cases que fazem a chamada.

Testes (`ServiceOrderTest`):
- com diagnóstico aberto e orçamento enviado (`AWAITING_APPROVAL`), `markSentEstimateExpired()` leva a
  `IN_DIAGNOSIS`;
- `markSentEstimateExpired()` não rebaixa fase mais avançada (ex.: execução `IN_PROGRESS` continua `IN_PROGRESS`).

Commit: `fix(servicelifecycle): add intention-revealing expiry transition to the service order`

## Checkpoint 2 — Geração e decisão do orçamento ✅ (2026-10-06)

- `GenerateEstimateUseCase`: chamar `serviceOrder.markEstimateSentWithPendingLines()` depois de
  `estimate.markSent()` e antes de `serviceOrderRepository.save(serviceOrder)`.
- `DecideEstimateLinesUseCase`: no ramo `allLinesDecided`, depois de `estimate.close()`, chamar
  `serviceOrder.markEstimateFullyDecided()` somente se `findByServiceOrderIdAndStatus(serviceOrderId, SENT)`,
  filtrado sem o id do orçamento atual, estiver vazio.
- Conferir os fakes/implementações de teste de `EstimateRepository` que não implementam
  `findByServiceOrderIdAndStatus` (o método default lança `UnsupportedOperationException`).

Testes:
- `GenerateEstimateUseCaseTest`: a OS salva fica `AWAITING_APPROVAL`, e `hasSentEstimateWithPendingLines()` é
  verdadeiro;
- `DecideEstimateLinesUseCaseTest`:
  - decisão total sem outro `SENT` desliga a flag (status pela precedência; ex.: `IN_PROGRESS`);
  - decisão total com outro `SENT` mantém a flag;
  - decisão parcial mantém a flag;
- `ApplyExternalStatusUpdateUseCaseTest` continua verde.

Commit: `fix(servicelifecycle): move service order to awaiting approval while an estimate is pending`

## Checkpoint 3 — Expiração ✅ (2026-10-06)

- `ExpireEstimatesUseCase` recebe `ServiceOrderRepository` (nos dois construtores):
  1. expira e salva os candidatos, como hoje;
  2. agrupa por `serviceOrderId`;
  3. para cada OS sem outro `SENT`, carrega com `findByIdForUpdate`, chama `markSentEstimateExpired()` e salva;
  4. se a OS não existir, registra `WARN` com IDs e continua.
- O retorno (`int`, quantidade de orçamentos expirados) não muda.

Testes (`ExpireEstimatesUseCaseTest`, `EstimateExpirationSchedulerTest` se depender do construtor):
- orçamento expirado devolve a OS a `IN_DIAGNOSIS`;
- com outro `SENT` da mesma OS, ela continua `AWAITING_APPROVAL`;
- dois orçamentos da mesma OS expirando juntos geram um único `findByIdForUpdate`/`save` da OS;
- OS inexistente gera `WARN` e o job segue para as demais.

Commit: `fix(servicelifecycle): return service order to diagnosis when its estimate expires`

## Checkpoint 4 — Integração e testes existentes

- Novo `AwaitingApprovalStatusIntegrationTest` (`@SpringBootTest`, H2 + Flyway, dados criados pelo próprio teste):
  - geração → `AWAITING_APPROVAL` persistido;
  - decisão interna (`DecideEstimateLinesUseCase`) → `IN_PROGRESS`;
  - decisão via RF40 (`ApplyExternalStatusUpdateUseCase`) → `IN_PROGRESS`;
  - geração → `ExpireEstimatesUseCase` com `Clock` adiantado → `IN_DIAGNOSIS`.
- Rodar a suíte completa e ajustar os testes que assumiam `IN_DIAGNOSIS` depois da geração ao comportamento
  correto, listando cada ajuste nas evidências. Não enfraquecer asserções.

Commit: `test(servicelifecycle): cover awaiting approval status across estimate generation, decision and expiry`

## Checkpoint 5 — Documentação

- `README.md`, seção "Bifurcações e acompanhamento de status":
  - incluir `AWAITING_APPROVAL` no roteiro de status (depois da geração do orçamento até a decisão);
  - informar que a expiração devolve a OS a `IN_DIAGNOSIS`;
  - remover o parágrafo "Embora `AWAITING_APPROVAL` exista..., a geração do orçamento preserva o diagnóstico
    aberto".
- `docs/tech-debt/TD-004-...md` (a partir de `TD-template.md`): OS presa em "Diagnóstico" depois da expiração do
  orçamento. Não é possível gerar novo orçamento para o mesmo diagnóstico nem abrir um novo diagnóstico.
- `docs/features/servicelifecycle/service-order-status-projection/`: nota apontando que a regra 5 passou a ser
  alimentada por esta correção.
- Conferir se `docs/features/servicelifecycle/external-status-update/manual-test-guide.md` cita status entre a
  geração e a decisão; ajustar se citar.
- OpenAPI e Postman: **N/A**. A forma do contrato não muda e nenhuma asserção do Postman depende do status nesse
  intervalo (verificado na technical-spec).

Commit: `docs(servicelifecycle): document awaiting approval status and register TD-004`

## Checkpoint 6 — Verificação e revisão de segurança

- `make test`, `make verify`, `make coverage` (código alterado ≥ 80%), `ModuleStructureTest` e
  `OpenApiContractTest` verdes.
- Observação de ambiente: nesta máquina o `./mvnw` está quebrado (ver o plano da RF52). Os comandos rodam com o
  Maven 3.9.16 extraído do zip do wrapper, e o equivalente de cada alvo do `Makefile` é registrado nas evidências.
- Validação manual: `make docker-reset`, gerar um orçamento pelo Postman e conferir `GET .../status` →
  `AWAITING_APPROVAL` / `AGUARDANDO_APROVACAO`, depois decidir e conferir `IN_PROGRESS`.

## Revisão de segurança

| Item | Achado / mitigação | Status |
|---|---|---|
| Validação de entrada e mass assignment | N/A: nenhum endpoint ou request novo. | — |
| Autenticação e autorização | N/A: nenhuma regra de acesso muda. | — |
| Exposição de dados | N/A: nenhum campo novo; só o valor de `statusSnapshot` muda entre a geração e a decisão. | — |
| Segredos e logs | `WARN` do job de expiração só com IDs. | Pendente |
| SQL, persistência e migração | Sem schema novo; coluna existente. Sem backfill (decisão registrada). | — |
| Concorrência | `ExpireEstimatesUseCase` passa a usar `findByIdForUpdate`, como geração e decisão. | Pendente |
| Respostas de erro | N/A: nenhuma resposta muda. | — |
| Dependências novas | N/A. | — |

## Evidências de verificação

### Checkpoint 1

- `ServiceOrder.markSentEstimateExpired()` adicionado. O comentário da flag agora aponta os três use cases que a
  alternam, no lugar de "policies reacting to Estimate domain events", que nunca existiram.
- `ServiceOrderTest`: 27 testes, 2 novos:
  - orçamento expirado com diagnóstico aberto volta a `IN_DIAGNOSIS`;
  - expiração não rebaixa uma OS em `IN_PROGRESS`.
- `mvn test` (suíte completa): 765 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`.
- **Falha intermitente que já existia (fora deste fix):** na primeira execução completa,
  `ServiceOrderControllerCompleteExecutionTest.completesAnInProgressExecutionAndReturns200` falhou com `409` em vez
  de `200`. Isolado, passou 3 de 3 vezes, e a execução completa seguinte passou. A mudança deste checkpoint só
  acrescenta um método de domínio que nenhum código chama ainda, então não pode causar a falha. Suspeita não
  confirmada: diferença de precisão entre `startedAt`, gravado pelo teste com `Instant.now()` sem truncar numa
  coluna `TIMESTAMP(6)` que arredonda, e `completedAt`, truncado para micros pelo use case. A regra violada seria
  "completedAt must not be before startedAt". Sem o corpo da resposta não dá para confirmar. Registrado para
  acompanhamento; não tratado neste fix.

### Checkpoint 2

- `GenerateEstimateUseCase`: `serviceOrder.markEstimateSentWithPendingLines()` logo depois de `estimate.markSent()`,
  antes de salvar a OS.
- `DecideEstimateLinesUseCase`: ao fechar o orçamento, chama `markEstimateFullyDecided()` só se não houver outro
  orçamento `SENT` da OS (`findByServiceOrderIdAndStatus`, filtrando o id do orçamento fechado).
- Fakes: o `InMemoryEstimateRepository` de `DecideEstimateLinesUseCaseTest` não implementava
  `findByServiceOrderIdAndStatus`, cujo método default lança `UnsupportedOperationException`. Passou a implementar.
  Os outros dois fakes (`GenerateEstimateUseCaseTest` e `ExpireEstimatesUseCaseTest`) não chamam esse caminho neste
  checkpoint.
- Testes novos:
  - `GenerateEstimateUseCaseTest`: a OS salva fica `AWAITING_APPROVAL`, com a flag ligada;
  - `DecideEstimateLinesUseCaseTest`:
    - fechar o único orçamento `SENT` desliga a flag (linha recusada → `COMPLETED`);
    - outro orçamento `SENT` mantém a flag;
    - decisão parcial mantém `AWAITING_APPROVAL`.

  Quando uma linha aprovada leva a `IN_PROGRESS`, que tem precedência sobre `AWAITING_APPROVAL`, a flag é afirmada
  diretamente.
- Nenhum teste existente dependia da OS ficar em `IN_DIAGNOSIS` depois da geração: nenhum ajuste foi necessário.
  `ApplyExternalStatusUpdateUseCaseTest` (7 testes) segue verde.
- `mvn test` (suíte completa): 769 testes, 0 falhas, 0 erros, 0 ignorados; `ModuleStructureTest` verde.

### Checkpoint 3

- `ExpireEstimatesUseCase` recebe `ServiceOrderRepository` (construtor `@Autowired` e construtor de teste com
  `Clock`). Depois de expirar os candidatos, reavalia cada OS afetada uma única vez:
  - se não houver outro orçamento `SENT` (os expirados nesta execução são filtrados pelo id), carrega a OS com
    `findByIdForUpdate`, chama `markSentEstimateExpired()` e salva;
  - se a OS não existir, registra `WARN` só com o id e segue.

  O retorno (quantidade de orçamentos expirados) não mudou. `EstimateExpirationScheduler` mocka o use case e não
  foi afetado.
- `ExpireEstimatesUseCaseTest`: 6 testes, sendo 2 que já existiam (adaptados ao construtor novo, mesma asserção) e
  4 novos:
  - orçamento expirado devolve a OS a `IN_DIAGNOSIS`;
  - outro `SENT` válido mantém `AWAITING_APPROVAL` e a OS não é salva;
  - dois orçamentos da mesma OS expirando juntos geram 1 bloqueio e 1 save;
  - OS inexistente gera `WARN` com o id e o job segue atualizando a outra OS.
- `mvn test` (suíte completa): 773 testes, 0 falhas, 0 erros, 0 ignorados; `ModuleStructureTest` verde.

## Rollback ou recuperação

`git revert` dos commits do fix. Sem migration nem estado novo. Depois do revert, novas OSs voltam a nunca entrar em
`AWAITING_APPROVAL`, como antes.

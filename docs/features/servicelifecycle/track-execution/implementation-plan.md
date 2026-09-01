# Plano de Implementação: Rastrear progresso da execução

| Campo | Valor |
|---|---|
| Feature | `track-execution` |
| Status | Stale |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-22 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-19) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-19) |

> Stale desde 2026-08-20: a especificação funcional foi devolvida a `Draft` pela feature
> `stock-item-reservation`. O plano histórico não deve ser usado para nova implementação até nova revisão do SDD.

> A reconciliação de 2026-08-22 também registra os deltas materiais de `service-order-initial-assessment`,
> `assign-diagnosis-assignee`, `diagnosis-authorship` e `service-order-status-projection`. Este plano não os cobre
> nem autoriza implementá-los.

## Objetivo da execução

Fechar a lacuna aprovada na spec técnica sobre dois endpoints somente-leitura de RF23 que já existem
em produção (`GetServiceOrderStatusUseCase` → `GET /{id}/status`; `GetServiceOrderUseCase` →
`GET /{id}`): não há `GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest` nem teste HTTP
para nenhum dos dois, e nenhum dos dois endpoints documenta `@ApiResponses` no Swagger. Nenhuma
mudança de comportamento de domínio, contrato HTTP ou tratamento de erro é feita — o
`ServiceOrderFinder.getOrThrow` já cobre o `404 NOT_FOUND` de RF23.

A execução termina com `GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest`,
`ServiceOrderControllerGetStatusTest` e as anotações Swagger dos dois endpoints — sem alterar
`GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase` nem nenhum outro caso de uso.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não alterar `GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase`,
  `ServiceOrder.recomputeStatusSnapshot`/`allNonRejectedExecutionsCompleted` ou
  `ServiceLifecycleExceptionHandler` — nenhuma mudança de comportamento está aprovada nesta feature.
- Não implementar autenticação/autorização por ator (Customer restrito à própria `ServiceOrder`) —
  depende de AD-016, fora de escopo.
- Não agrupar execuções por Estimate no contrato de resposta — fora de escopo.
- Não implementar polling com cache, SSE ou WebSocket — AD-015 resolvida a favor de polling puro
  (2026-08-23), fora de escopo enquanto não houver nova decisão do time.
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Confirmar cobertura de domínio existente (`ServiceOrderTest`) | Completed |
| 2 | Testes de caso de uso (`GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest`) | Completed |
| 3 | Testes HTTP (`ServiceOrderControllerGetStatusTest`) | Completed |
| 4 | Anotações Swagger e critérios de aceite | Completed |
| 5 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch atual e `git status --short` sem mudanças inesperadas.
- Reler `GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase`, `ServiceOrderMapper.toStatusResponse`/
  `toResponse`, `ServiceOrderFinder.getOrThrow` e `ServiceOrderController` (`getStatus`, `get`) e
  confirmar que não há nenhuma cobertura de teste de aplicação ou HTTP existente para os dois use
  cases, para confirmar que nada mudou desde a spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- Branch atual: `feature/servicelifecycle-track-execution`. `git status --short`: `M .gitignore`
  (pré-existente), `?? EPIC3-TESTING.md` (pré-existente), `?? PR-RF22-complete-execution.md`
  (artefato da feature anterior, ainda não commitado/removido), `?? docs/features/servicelifecycle/track-execution/`
  (specs desta própria feature) — nenhuma mudança inesperada.
- `GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase`, `ServiceOrderMapper.toStatusResponse`/
  `toResponse` e `ServiceOrderController` (`getStatus`, `get`) foram relidos nesta sessão e continuam
  exatamente como descritos na spec técnica: ambos os use cases delegam a
  `ServiceOrderFinder.getOrThrow` e nenhum dos dois métodos do controller tem `@ApiResponses`.
- Confirmado por busca que não existe `GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest`
  nem teste HTTP para os dois endpoints de leitura.

## Checkpoint 1 — Confirmar cobertura de domínio existente (`ServiceOrderTest`)

### Alterações

Nenhuma alteração de produção nem de teste nesta etapa. Reexecutar e confirmar que a cobertura de
domínio já existente sobre a precedência de `statusSnapshot` continua verde e é suficiente (nenhum
teste novo de domínio é necessário — RF23 é somente leitura sobre um estado já testado por RF19–RF22).

### Verificação

- `./mvnw test -Dtest=ServiceOrderTest`.

### Evidência

- `./mvnw -q test -Dtest=ServiceOrderTest`: **BUILD SUCCESS, 0 falhas** — a cobertura de domínio da
  precedência de `statusSnapshot` continua verde e é suficiente para RF23, que é somente leitura sobre
  esse estado. Nenhum caso novo adicionado.

## Checkpoint 2 — Testes de caso de uso (`GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest`)

### Alterações

Nenhuma alteração de produção. Dois novos arquivos de teste em
`src/test/java/.../serviceorder/application/usecase/`, no mesmo estilo de
`CompleteExecutionUseCaseTest` (repository fake in-memory):

- `GetServiceOrderStatusUseCaseTest`: retorna `id` + `status` corretos para uma `ServiceOrder`
  existente em pelo menos dois status distintos (ex.: recém-criada `RECEIVED` e diagnosticada
  `AWAITING_APPROVAL`/`IN_DIAGNOSIS`); `NoSuchElementException` quando a `ServiceOrder` não existe.
- `GetServiceOrderUseCaseTest`: retorna o agregado completo com `executions[].status` correto
  refletindo o estado de cada `ServiceExecution`; `NoSuchElementException` quando a `ServiceOrder` não
  existe.

### Verificação

- `./mvnw test -Dtest=GetServiceOrderStatusUseCaseTest,GetServiceOrderUseCaseTest`.

### Evidência

- Novo `GetServiceOrderStatusUseCaseTest` (3 casos): `id`+`status` corretos para `ServiceOrder`
  recém-criada (`RECEIVED`) e diagnosticada (`IN_DIAGNOSIS`); `NoSuchElementException` quando a
  `ServiceOrder` não existe.
- Novo `GetServiceOrderUseCaseTest` (2 casos): agregado completo com `executions[0].status =
  PENDING` correto após diagnóstico; `NoSuchElementException` quando a `ServiceOrder` não existe.
  Repository fake em memória, mesmo estilo de `CompleteExecutionUseCaseTest`.
- `./mvnw -q test -Dtest=GetServiceOrderStatusUseCaseTest,GetServiceOrderUseCaseTest`: **5 testes, 0
  falhas, BUILD SUCCESS**.

## Checkpoint 3 — Testes HTTP (`ServiceOrderControllerGetStatusTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/infrastructure/web/ServiceOrderControllerGetStatusTest.java`, no
mesmo estilo de `ServiceOrderControllerCompleteExecutionTest` (`@SpringBootTest` + `MockMvc`,
preparando estado via chamadas diretas ao aggregate dentro de `TransactionTemplate`), cobrindo os
dois endpoints:

- `GET /{id}/status`: `200 OK` com `status` correto para uma `ServiceOrder` existente; `404 NOT_FOUND`
  para uma `ServiceOrder` inexistente.
- `GET /{id}`: `200 OK` com o payload completo (incluindo `executions[].status`) para uma
  `ServiceOrder` existente; `404 NOT_FOUND` para uma `ServiceOrder` inexistente.

### Verificação

- `./mvnw test -Dtest=ServiceOrderControllerGetStatusTest`.

### Evidência

- Novo `ServiceOrderControllerGetStatusTest` (4 testes): `GET /{id}/status` retorna `200` com `id` e
  `status = "RECEIVED"` para uma `ServiceOrder` recém-criada, e `404`/`NOT_FOUND` para inexistente;
  `GET /{id}` retorna `200` com `executions[0].status = "PENDING"` após diagnóstico, e
  `404`/`NOT_FOUND` para inexistente. Sem preparação de estado via aggregate — os dois endpoints são
  somente-leitura e as fixtures HTTP existentes (`createServiceOrder`/`diagnoseWithOneExecution`,
  mesmo padrão de `ServiceOrderControllerCompleteExecutionTest`) já bastam.
- `./mvnw -q test -Dtest=ServiceOrderControllerGetStatusTest`: **4 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 4 — Anotações Swagger e critérios de aceite

### Alterações

- Adicionar `@ApiResponses` aos métodos `getStatus` e `get` de `ServiceOrderController`, documentando
  `200` e `404` — mesmo padrão dos métodos `assignTechnician`/`startExecution`/
  `updateExecutionProgress`/`completeExecution`.
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (nome do teste),
  substituindo os `[ ]` genéricos.

### Verificação

- `./mvnw test -Dtest=OpenApiContractTest` (se aplicável) continua passando.

### Evidência

- `ServiceOrderController.get` e `.getStatus` ganharam `@ApiResponses` documentando `200` e `404`,
  mesmo padrão (código + descrição curta) já usado em `assignTechnician`/`startExecution`/
  `updateExecutionProgress`/`completeExecution`.
- `functional-spec.md`: os 4 critérios de aceite foram marcados `[x]` com evidência (nome dos testes)
  em vez de `[ ]` genérico.
- `./mvnw -q test -Dtest=OpenApiContractTest`: **BUILD SUCCESS, 0 falhas**.

## Checkpoint 5 — Executar gates finais e concluir a feature

### Gates

- Executar `./mvnw test -Dtest='!ModuleStructureTest'` (mesma exclusão documentada em RF20/RF21/RF22,
  caso o seeder local não versionado ainda cause a falha conhecida) e, separadamente, tentar
  `ModuleStructureTest` isolado para confirmar se a causa raiz mudou.
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `AssignTechnicianUseCase`, `StartExecutionUseCase`,
  `UpdateExecutionProgressUseCase`, `CompleteExecutionUseCase` ou `ServiceLifecycleExceptionHandler`).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | N/A | Nenhum dos dois endpoints recebe corpo de requisição. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoints permanecem públicos até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ServiceOrderResponse`/`ServiceOrderStatusResponse` expõem exatamente os mesmos campos já usados por outros endpoints do controller (IDs, status, snapshot de veículo); nenhum campo novo foi adicionado. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; nenhum dado sensível é manipulado por este fluxo. |
| Erros e information disclosure | Resolved | `404 NOT_FOUND` usa apenas a mensagem de domínio já existente via `ServiceOrderFinder.getOrThrow`. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada. |

**Achado durante a execução, fora do escopo desta feature:** `./mvnw test -Dtest=ModuleStructureTest`
(isolado) falha com 1 erro — mesma causa raiz já documentada em `start-execution/`,
`update-progress/` e `complete-execution/implementation-plan.md`: `ServiceOrderDevelopmentDataSeeder`
(arquivo local de Santiago, não versionado, ignorado pelo `.gitignore` via
`*DevelopmentDataSeeder.java`) importa `CustomerRepository`/`Customer` do módulo `registration`,
violando a fronteira Modulith. Confirmado que não é causado por esta feature: `git diff --stat` mostra
apenas `.gitignore` e `ServiceOrderController.java`; nenhum arquivo desta feature toca
`ServiceOrderDevelopmentDataSeeder`. `./mvnw test -Dtest='!ModuleStructureTest'` confirma que toda a
suíte restante passa sem regressão.

### Verificação

- `git diff --stat`: `.gitignore` (mudança pré-existente/local, não desta feature) e
  `ServiceOrderController.java` (+8, só as anotações `@ApiResponses` dos endpoints `get`/`getStatus`).
  Nenhuma mudança em `GetServiceOrderStatusUseCase`, `GetServiceOrderUseCase`,
  `ServiceOrder.recomputeStatusSnapshot`, `ServiceOrderFinder` ou qualquer outro caso de uso do épico.
- `git status --short`: três arquivos de teste novos (`GetServiceOrderStatusUseCaseTest`,
  `GetServiceOrderUseCaseTest`, `ServiceOrderControllerGetStatusTest`) e as três specs desta feature —
  nada fora do escopo.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test -Dtest='!ModuleStructureTest'` | Passou: BUILD SUCCESS, 0 falhas |
| `./mvnw test -Dtest=ServiceOrderTest` | Passou (cobertura de domínio já existente, sem mudança) |
| `./mvnw test -Dtest=GetServiceOrderStatusUseCaseTest,GetServiceOrderUseCaseTest` | Passou: 5 testes |
| `./mvnw test -Dtest=ServiceOrderControllerGetStatusTest` | Passou: 4 testes |
| `./mvnw test -Dtest=OpenApiContractTest` | Passou: 1 teste |
| `ModuleStructureTest` (isolado) | Falha pré-existente e fora do escopo (ver nota acima) |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum (apenas `.gitignore` pré-existente/local e `@ApiResponses` em `ServiceOrderController`) |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter e nenhum código de produção de
domínio/aplicação é alterado (apenas testes e anotações Swagger). Se algum teste novo revelar um
comportamento inesperado em `GetServiceOrderStatusUseCase`/`GetServiceOrderUseCase`, reverter apenas o
arquivo de teste correspondente é suficiente — não há efeito colateral em outro caso de uso.

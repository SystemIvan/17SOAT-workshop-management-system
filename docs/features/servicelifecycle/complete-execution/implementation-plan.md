# Plano de Implementação: Concluir execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `complete-execution` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-19) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-19) |

## Objetivo da execução

Fechar a lacuna aprovada na spec técnica sobre um fluxo de RF22 que já existe em produção
(`CompleteExecutionUseCase`, `ServiceOrder.completeExecution`, `ServiceExecution.complete`, endpoint
`POST /api/service-orders/{id}/executions/{executionId}/complete`): não há `CompleteExecutionUseCaseTest`
nem teste HTTP para este fluxo (a cobertura de domínio já existe em `ServiceOrderTest`/
`ServiceExecutionTest`), e o endpoint não documenta `@ApiResponses` no Swagger. Nenhuma mudança de
comportamento de domínio, contrato HTTP ou tratamento de erro é feita — o
`ServiceLifecycleExceptionHandler` já cobre o `IllegalStateException` de RF22.

A execução termina com `CompleteExecutionUseCaseTest`, `ServiceOrderControllerCompleteExecutionTest` e
as anotações Swagger do endpoint `completeExecution` — sem alterar `CompleteExecutionUseCase`,
`ServiceOrder.completeExecution`, `ServiceExecution.complete` nem nenhum outro caso de uso.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não alterar `CompleteExecutionUseCase`, `ServiceOrder.completeExecution`,
  `ServiceExecution.complete`, `allNonRejectedExecutionsCompleted`/`recomputeStatusSnapshot` ou
  `ServiceLifecycleExceptionHandler` — nenhuma mudança de comportamento está aprovada nesta feature.
- Não alterar `TechnicianStatus` ao concluir a execução — depende de AD-006, fora de escopo.
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Confirmar cobertura de domínio existente (`ServiceExecutionTest`/`ServiceOrderTest`) | Completed |
| 2 | Testes de caso de uso (`CompleteExecutionUseCaseTest`) | Completed |
| 3 | Testes HTTP (`ServiceOrderControllerCompleteExecutionTest`) | Completed |
| 4 | Anotações Swagger e critérios de aceite | Completed |
| 5 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch atual e `git status --short` sem mudanças inesperadas.
- Reler `CompleteExecutionUseCase`, `ServiceOrder.completeExecution`, `ServiceExecution.complete`,
  `ServiceLifecycleExceptionHandler` e confirmar que não há nenhuma cobertura de teste de aplicação ou
  HTTP existente (domínio já parcialmente coberto), para confirmar que nada mudou desde a spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- Branch nova criada a partir da tip local pós-merge de RF21 (`ada6379`, mesmo commit de
  `origin/main`): `feature/servicelifecycle-complete-execution`. A branch local
  `feature/servicelifecycle-assign-technician` estava reaproveitada apontando para esse mesmo commit,
  mas com nome que não corresponde a esta feature; a nova branch evita confusão.
- `git status --short` antes desta feature: `M .gitignore` (pré-existente), `?? EPIC3-TESTING.md`
  (pré-existente) e `?? docs/features/servicelifecycle/complete-execution/` (specs desta própria
  feature) — nenhuma mudança inesperada.
- `CompleteExecutionUseCase`, `ServiceOrder.completeExecution` (linha 163-166),
  `ServiceExecution.complete` (linha 115-118) e `ServiceLifecycleExceptionHandler` foram relidos nesta
  sessão e continuam exatamente como descritos na spec técnica.
- Confirmado por busca que não existe `CompleteExecutionUseCaseTest` nem teste HTTP para o endpoint
  `complete`; a cobertura de domínio (`ServiceExecutionTest.cannotCompleteAnExecutionThatHasNotStarted`,
  `ServiceOrderTest.rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone`,
  `ServiceOrderTest.rejectedExecutionsAreIgnoredWhenComputingCompletion`) já existia antes desta
  feature.

## Checkpoint 1 — Confirmar cobertura de domínio existente (`ServiceExecutionTest`/`ServiceOrderTest`)

### Alterações

Nenhuma alteração de produção nem de teste nesta etapa. Reexecutar e confirmar que a cobertura de
domínio já existente continua verde e é suficiente:

- `ServiceExecutionTest.cannotCompleteAnExecutionThatHasNotStarted`;
- `ServiceOrderTest.rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone`;
- `ServiceOrderTest.rejectedExecutionsAreIgnoredWhenComputingCompletion`.

Se um status intermediário sem cobertura direta for identificado (ex.: `READY`, análogo ao par
`PENDING`/`READY` de RF21), adicionar um caso pontual em `ServiceExecutionTest` seguindo o mesmo
padrão — só nesse cenário.

### Verificação

- `./mvnw test -Dtest=ServiceExecutionTest,ServiceOrderTest`.

### Evidência

- `./mvnw test -Dtest=ServiceExecutionTest,ServiceOrderTest`: **BUILD SUCCESS, 0 falhas** — confirma
  que `cannotCompleteAnExecutionThatHasNotStarted`,
  `rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone` e
  `rejectedExecutionsAreIgnoredWhenComputingCompletion` continuam verdes.
- Nenhum status intermediário adicional foi identificado como faltando cobertura direta (o único
  cenário de rejeição relevante — a partir de `PENDING`/não iniciado — já está coberto; os demais
  status seguem o mesmo `requireStatus` já validado nos demais fluxos de RF20/RF21). Nenhum caso novo
  adicionado a `ServiceExecutionTest` nesta feature.

## Checkpoint 2 — Testes de caso de uso (`CompleteExecutionUseCaseTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/application/usecase/CompleteExecutionUseCaseTest.java`, no mesmo estilo
de `UpdateExecutionProgressUseCaseTest` (repository fake in-memory), cobrindo:

- conclusão bem-sucedida de uma `ServiceExecution` `IN_PROGRESS` (reaproveitar o padrão de fixture que
  autoriza + inicia a execução, análogo a `UpdateExecutionProgressUseCaseTest`);
- `IllegalStateException` ao tentar concluir uma execução ainda `PENDING`;
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### Verificação

- `./mvnw test -Dtest=CompleteExecutionUseCaseTest`.

### Evidência

- Novo `CompleteExecutionUseCaseTest` (3 casos): conclusão bem-sucedida a partir de `IN_PROGRESS` com
  `ServiceOrderStatus.COMPLETED` no `statusSnapshot` retornado; `IllegalStateException` a partir de
  `PENDING`; `NoSuchElementException` quando a `ServiceOrder` não existe. Repository fake em memória,
  mesmo estilo de `UpdateExecutionProgressUseCaseTest`.
- `./mvnw test -Dtest=CompleteExecutionUseCaseTest`: **3 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 3 — Testes HTTP (`ServiceOrderControllerCompleteExecutionTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/infrastructure/web/ServiceOrderControllerCompleteExecutionTest.java`,
no mesmo estilo de `ServiceOrderControllerUpdateProgressTest` (`@SpringBootTest` + `MockMvc`, preparando
estado `IN_PROGRESS` via `authorizeExecutionFromEstimate` + `startExecution` chamados diretamente no
aggregate dentro de `TransactionTemplate`), cobrindo:

- `200 OK` com `executions[0].status = "COMPLETED"` e `status = "COMPLETED"` da `ServiceOrder` ao
  concluir com sucesso a única execução;
- `404 NOT_FOUND` para `ServiceOrder` inexistente;
- `404 NOT_FOUND` para `ServiceExecution` inexistente;
- `409 INVALID_STATE_TRANSITION` ao concluir uma execução ainda `PENDING`.

### Verificação

- `./mvnw test -Dtest=ServiceOrderControllerCompleteExecutionTest`.

### Evidência

- Novo `ServiceOrderControllerCompleteExecutionTest` (4 testes): `200` com `executions[0].status =
  "COMPLETED"` e `status = "COMPLETED"` da `ServiceOrder` ao concluir com sucesso a única execução;
  `404`/`NOT_FOUND` para `ServiceOrder` e `ServiceExecution` inexistentes (2 variações);
  `409`/`INVALID_STATE_TRANSITION` para execução ainda `PENDING` (não iniciada). Estado `IN_PROGRESS`
  preparado chamando `authorizeExecutionFromEstimate` + `startExecution` diretamente no aggregate via
  `ServiceOrderRepository` dentro de `TransactionTemplate` — mesmo padrão de
  `ServiceOrderControllerUpdateProgressTest`.
- `./mvnw test -Dtest=ServiceOrderControllerCompleteExecutionTest`: **4 testes, 0 falhas, BUILD
  SUCCESS**. Primeira tentativa falhou por disco cheio em `C:` (fork do Surefire não conseguiu
  escrever o arquivo de propriedades); usuário liberou espaço e a reexecução passou sem nenhuma
  mudança de código.

## Checkpoint 4 — Anotações Swagger e critérios de aceite

### Alterações

- Adicionar `@ApiResponses` ao método `completeExecution` de `ServiceOrderController`, documentando
  `200`, `404` e `409` — mesmo padrão dos métodos `assignTechnician`/`startExecution`/
  `updateExecutionProgress`.
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (nome do teste),
  substituindo os `[ ]` genéricos.

### Verificação

- `./mvnw test -Dtest=OpenApiContractTest` (se aplicável) continua passando.

### Evidência

- `ServiceOrderController.completeExecution` ganhou `@ApiResponses` documentando `200`, `404` e `409`,
  mesmo padrão (código + descrição curta) já usado em `assignTechnician`/`startExecution`/
  `updateExecutionProgress`.
- `functional-spec.md`: os 5 critérios de aceite foram marcados `[x]` com evidência (nome dos testes)
  em vez de `[ ]` genérico.
- `./mvnw test -Dtest=OpenApiContractTest`: **1 teste, 0 falhas, BUILD SUCCESS**.

## Checkpoint 5 — Executar gates finais e concluir a feature

### Gates

- Executar `./mvnw test -Dtest='!ModuleStructureTest'` (mesma exclusão documentada em RF20/RF21, caso
  o seeder local não versionado ainda cause a falha conhecida) e, separadamente, tentar
  `ModuleStructureTest` isolado para confirmar se a causa raiz mudou.
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `AssignTechnicianUseCase`, `StartExecutionUseCase`,
  `UpdateExecutionProgressUseCase` ou `ServiceLifecycleExceptionHandler`).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | N/A | Endpoint não recebe corpo de requisição. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoint permanece público até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ErrorResponse(code, message)` reutiliza o handler já existente, sem stack trace, SQL ou nome de classe/pacote. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; nenhum dado sensível é manipulado por este fluxo. |
| Erros e information disclosure | Resolved | `409 INVALID_STATE_TRANSITION` e `404 NOT_FOUND` usam apenas mensagens de domínio já existentes. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada. |

**Achado durante a execução, fora do escopo desta feature:** `./mvnw test -Dtest=ModuleStructureTest`
(isolado) falha com 1 erro — mesma causa raiz já documentada em `start-execution/implementation-plan.md`
e `update-progress/implementation-plan.md`: `ServiceOrderDevelopmentDataSeeder` (arquivo local de
Santiago, não versionado, ignorado pelo `.gitignore` via `*DevelopmentDataSeeder.java`) importa
`CustomerRepository`/`Customer` do módulo `registration`, violando a fronteira Modulith. Confirmado que
não é causado por esta feature: `git diff --stat` mostra apenas `.gitignore` e
`ServiceOrderController.java`; nenhum arquivo desta feature toca `ServiceOrderDevelopmentDataSeeder`.
`./mvnw test -Dtest='!ModuleStructureTest'` confirma que toda a suíte restante passa sem regressão.

**Nota adicional:** a primeira tentativa de rodar `ServiceOrderControllerCompleteExecutionTest` falhou
por disco `C:` cheio (0 bytes livres), impedindo o fork do Surefire de escrever seu arquivo de
propriedades — não relacionado ao código desta feature. Após o usuário liberar espaço, a reexecução
passou sem nenhuma mudança de código.

### Verificação

- `git diff --stat`: `.gitignore` (mudança pré-existente/local, não desta feature) e
  `ServiceOrderController.java` (+5, só as anotações `@ApiResponses` do endpoint `completeExecution`).
  Nenhuma mudança em `CompleteExecutionUseCase`, `ServiceOrder.completeExecution`,
  `ServiceExecution.complete`, `AssignTechnicianUseCase`, `StartExecutionUseCase`,
  `UpdateExecutionProgressUseCase` ou `ServiceLifecycleExceptionHandler`.
- `git status --short`: dois arquivos de teste novos (`CompleteExecutionUseCaseTest`,
  `ServiceOrderControllerCompleteExecutionTest`) e as três specs desta feature — nada fora do escopo.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test -Dtest='!ModuleStructureTest'` | Passou: BUILD SUCCESS, 0 falhas |
| `./mvnw test -Dtest=ServiceExecutionTest,ServiceOrderTest` | Passou (cobertura de domínio já existente, sem mudança) |
| `./mvnw test -Dtest=CompleteExecutionUseCaseTest` | Passou: 3 testes |
| `./mvnw test -Dtest=ServiceOrderControllerCompleteExecutionTest` | Passou: 4 testes |
| `./mvnw test -Dtest=OpenApiContractTest` | Passou: 1 teste |
| `ModuleStructureTest` (isolado) | Falha pré-existente e fora do escopo (ver nota acima) |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum (apenas `.gitignore` pré-existente/local e `@ApiResponses` em `ServiceOrderController`) |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter e nenhum código de produção de
domínio/aplicação é alterado (apenas testes e anotações Swagger). Se algum teste novo revelar um
comportamento inesperado em `CompleteExecutionUseCase`, reverter apenas o arquivo de teste
correspondente é suficiente — não há efeito colateral em outro caso de uso.

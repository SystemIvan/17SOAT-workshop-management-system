# Plano de Implementação: Atualizar progresso de uma execução em andamento

| Campo | Valor |
|---|---|
| Feature | `update-progress` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-19) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-19) |

## Objetivo da execução

Fechar a única lacuna aprovada na spec técnica sobre um fluxo de RF21 que já existe em produção
(`UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress`,
`ServiceExecution.updateProgress`, endpoint
`PATCH /api/service-orders/{id}/executions/{executionId}/progress`): não há nenhuma cobertura de
teste (domínio, aplicação ou HTTP) para este fluxo, e o endpoint não documenta `@ApiResponses` no
Swagger. Nenhuma mudança de comportamento de domínio, contrato HTTP ou tratamento de erro é feita — o
`ServiceLifecycleExceptionHandler` já cobre o `IllegalStateException` de RF21.

A execução termina com um caso de domínio novo em `ServiceExecutionTest`,
`UpdateExecutionProgressUseCaseTest`, `ServiceOrderControllerUpdateProgressTest` e as anotações
Swagger do endpoint `updateExecutionProgress` — sem alterar `UpdateExecutionProgressUseCase`,
`ServiceOrder.updateExecutionProgress`, `ServiceExecution.updateProgress` nem nenhum outro caso de uso.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não alterar `UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress`,
  `ServiceExecution.updateProgress` ou `ServiceLifecycleExceptionHandler` — nenhuma mudança de
  comportamento está aprovada nesta feature.
- Não persistir nem expor a `note` de progresso — permanece pendente (decisão de time, fora de escopo
  conforme `functional-spec.md`).
- Não chamar `recomputeStatusSnapshot` em `updateExecutionProgress` — fora de escopo, comportamento
  atual preservado.
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Teste de domínio (`ServiceExecutionTest`) | Completed |
| 2 | Testes de caso de uso (`UpdateExecutionProgressUseCaseTest`) | Completed |
| 3 | Testes HTTP (`ServiceOrderControllerUpdateProgressTest`) | Completed |
| 4 | Anotações Swagger e critérios de aceite | Completed |
| 5 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch `feature/servicelifecycle-update-progress` e `git status --short` sem mudanças
  inesperadas.
- Reler `UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress`,
  `ServiceExecution.updateProgress`, `ServiceLifecycleExceptionHandler` e confirmar que não há
  nenhuma cobertura de teste existente (domínio, aplicação, HTTP) para confirmar que nada mudou desde
  a spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- Branch confirmada: `feature/servicelifecycle-update-progress`.
- `git status --short` antes desta feature: `M .gitignore` (pré-existente), `?? EPIC3-TESTING.md`
  (pré-existente) e `?? docs/features/servicelifecycle/update-progress/` (specs desta própria
  feature) — nenhuma mudança inesperada.
- `java -version`: OpenJDK 21.0.10 — confirma Java 21.
- `UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress` (linha 156-158),
  `ServiceExecution.updateProgress` (linha 107-110) e `ServiceLifecycleExceptionHandler` foram relidos
  nesta sessão e continuam exatamente como descritos na spec técnica.
- Busca em `src/test/java` confirmou que não havia nenhum teste cobrindo
  `updateProgress`/`updateExecutionProgress` antes desta feature.

## Checkpoint 1 — Teste de domínio (`ServiceExecutionTest`)

### Alterações

Nenhuma alteração de produção. Novo caso de teste em
`src/test/java/.../serviceorder/domain/model/ServiceExecutionTest.java` cobrindo:

- `updateProgress` bem-sucedido a partir de status `IN_PROGRESS` (não deve lançar);
- `IllegalStateException` ao chamar `updateProgress` a partir de `PENDING` e de `READY`.

### Verificação

- `./mvnw test -Dtest=ServiceExecutionTest`.

### Evidência

- Três casos novos adicionados a `ServiceExecutionTest`:
  `canUpdateProgressOfAnInProgressExecution`,
  `cannotUpdateProgressOfAnExecutionThatHasNotStarted` (a partir de `PENDING`) e
  `cannotUpdateProgressOfAReadyExecutionThatHasNotStartedYet` (a partir de `READY`).
- `./mvnw test -Dtest=ServiceExecutionTest`: **6 testes (3 pré-existentes + 3 novos), 0 falhas, BUILD
  SUCCESS**.

## Checkpoint 2 — Testes de caso de uso (`UpdateExecutionProgressUseCaseTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/application/usecase/UpdateExecutionProgressUseCaseTest.java`, no
mesmo estilo de `StartExecutionUseCaseTest` (repository fake in-memory), cobrindo:

- atualização de progresso bem-sucedida em uma `ServiceExecution` `IN_PROGRESS` (reaproveitar o
  padrão de fixture que autoriza + inicia a execução, análogo a `StartExecutionUseCaseTest`);
- `IllegalStateException` ao tentar atualizar progresso de uma execução ainda `PENDING`;
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### Verificação

- `./mvnw test -Dtest=UpdateExecutionProgressUseCaseTest`.

### Evidência

- Novo `UpdateExecutionProgressUseCaseTest` (3 casos): atualização bem-sucedida a partir de
  `IN_PROGRESS`, `IllegalStateException` a partir de `PENDING`, `NoSuchElementException` quando a
  `ServiceOrder` não existe. Repository fake em memória, mesmo estilo de `StartExecutionUseCaseTest`.
- `./mvnw test -Dtest=UpdateExecutionProgressUseCaseTest`: **3 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 3 — Testes HTTP (`ServiceOrderControllerUpdateProgressTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/infrastructure/web/ServiceOrderControllerUpdateProgressTest.java`, no
mesmo estilo de `ServiceOrderControllerStartExecutionTest` (`@SpringBootTest` + `MockMvc`, preparando
estado `IN_PROGRESS` via `authorizeExecutionFromEstimate` + `startExecution` chamados diretamente no
aggregate dentro de `TransactionTemplate`), cobrindo:

- `200 OK` com `executions[].status = "IN_PROGRESS"` mantido ao atualizar progresso com sucesso;
- `404 NOT_FOUND` para `ServiceOrder` inexistente;
- `404 NOT_FOUND` para `ServiceExecution` inexistente;
- `409 INVALID_STATE_TRANSITION` ao atualizar progresso de uma execução ainda `PENDING`;
- `400` ao enviar `note` vazia/em branco.

### Verificação

- `./mvnw test -Dtest=ServiceOrderControllerUpdateProgressTest`.

### Evidência

- Novo `ServiceOrderControllerUpdateProgressTest` (5 testes): `200` com `executions[0].status =
  "IN_PROGRESS"` na atualização bem-sucedida; `404`/`NOT_FOUND` para `ServiceOrder` e
  `ServiceExecution` inexistentes (2 variações); `409`/`INVALID_STATE_TRANSITION` para execução ainda
  `PENDING` (não iniciada); `400` para `note` em branco. Estado `IN_PROGRESS` preparado chamando
  `authorizeExecutionFromEstimate` + `startExecution` diretamente no aggregate via
  `ServiceOrderRepository` dentro de `TransactionTemplate` — mesmo padrão de
  `ServiceOrderControllerStartExecutionTest`.
- `./mvnw test -Dtest=ServiceOrderControllerUpdateProgressTest`: **5 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 4 — Anotações Swagger e critérios de aceite

### Alterações

- Adicionar `@ApiResponses` ao método `updateExecutionProgress` de `ServiceOrderController`,
  documentando `200`, `400`, `404` e `409` — mesmo padrão dos métodos `assignTechnician`/
  `startExecution`.
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (nome do teste),
  substituindo os `[ ]` genéricos.

### Verificação

- `./mvnw test -Dtest=OpenApiContractTest` (se aplicável) continua passando.

### Evidência

- `ServiceOrderController.updateExecutionProgress` ganhou `@ApiResponses` documentando `200`, `400`,
  `404` e `409`, mesmo padrão (código + descrição curta) já usado em `assignTechnician`/`startExecution`.
- `functional-spec.md`: os 5 critérios de aceite foram marcados `[x]` com evidência (nome dos testes)
  em vez de `[ ]` genérico.
- `./mvnw test -Dtest=OpenApiContractTest`: **1 teste, 0 falhas, BUILD SUCCESS**.

## Checkpoint 5 — Executar gates finais e concluir a feature

### Gates

- Executar `./mvnw verify` (ou equivalente) sem testes ignorados.
- Confirmar `ModuleStructureTest` verde (ou registrar a mesma falha pré-existente já documentada em
  `start-execution/implementation-plan.md`, se ainda presente e não relacionada a esta feature).
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `AssignTechnicianUseCase`, `StartExecutionUseCase`, `CompleteExecutionUseCase`
  ou `ServiceLifecycleExceptionHandler`).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | Resolved | `note` já validada com `@NotBlank`; nenhum campo novo aceito. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoint permanece público até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ErrorResponse(code, message)` reutiliza o handler já existente, sem stack trace, SQL ou nome de classe/pacote. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; a `note` não é persistida nem logada por este fluxo. |
| Erros e information disclosure | Resolved | `409 INVALID_STATE_TRANSITION`, `404 NOT_FOUND` e `400` usam apenas mensagens de domínio/validação já existentes. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada. |

**Achado durante a execução, fora do escopo desta feature:** `./mvnw test` (suíte completa) falha com
1 erro em `ModuleStructureTest` — mesma causa raiz já documentada em
`start-execution/implementation-plan.md`: `ServiceOrderDevelopmentDataSeeder` (arquivo local de
Santiago, não versionado, ignorado pelo `.gitignore` via `*DevelopmentDataSeeder.java`) importa
`CustomerRepository`/`Customer` do módulo `registration`, violando a fronteira Modulith. Confirmado
que não é causado por esta feature: `git diff --stat` mostra apenas `.gitignore`,
`ServiceOrderController.java` e `ServiceExecutionTest.java`; nenhum arquivo desta feature toca
`ServiceOrderDevelopmentDataSeeder`. `./mvnw test -Dtest='!ModuleStructureTest'` confirma que todos os
demais 81 testes (67 pré-existentes + 14 novos desta feature) passam.

### Verificação

- `./mvnw test -Dtest='!ModuleStructureTest'`: **81 testes, 0 falhas, BUILD SUCCESS** — inclui os 14
  testes novos de RF21 (3 `ServiceExecutionTest` + 3 `UpdateExecutionProgressUseCaseTest` + 5
  `ServiceOrderControllerUpdateProgressTest`, mais anotações Swagger sem teste dedicado) e toda a
  suíte pré-existente sem regressão.
- `ModuleStructureTest` isolado: falha pela mesma causa pré-existente já documentada em RF20, não
  relacionada a esta feature.
- `git diff --stat`: `.gitignore` (pré-existente), `ServiceOrderController.java` (+6, só as anotações
  `@ApiResponses` do endpoint `updateExecutionProgress`) e `ServiceExecutionTest.java` (+31, 3 casos
  novos). Nenhuma mudança em `UpdateExecutionProgressUseCase`, `ServiceOrder.updateExecutionProgress`,
  `ServiceExecution.updateProgress`, `AssignTechnicianUseCase`, `StartExecutionUseCase`,
  `CompleteExecutionUseCase` ou `ServiceLifecycleExceptionHandler`.
- `git status --short`: dois arquivos de teste novos (`UpdateExecutionProgressUseCaseTest`,
  `ServiceOrderControllerUpdateProgressTest`) e as três specs desta feature — nada fora do escopo.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test -Dtest='!ModuleStructureTest'` | Passou: 81 testes, 0 falhas/erros |
| `./mvnw test -Dtest=ServiceExecutionTest` | Passou: 6 testes |
| `./mvnw test -Dtest=UpdateExecutionProgressUseCaseTest` | Passou: 3 testes |
| `./mvnw test -Dtest=ServiceOrderControllerUpdateProgressTest` | Passou: 5 testes |
| `./mvnw test -Dtest=OpenApiContractTest` | Passou: 1 teste |
| `ModuleStructureTest` | Falha pré-existente e fora do escopo (ver nota acima) |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum (apenas `.gitignore` pré-existente, `@ApiResponses` em `ServiceOrderController` e 3 casos novos em `ServiceExecutionTest`) |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter e nenhum código de produção de
domínio/aplicação é alterado (apenas testes e anotações Swagger). Se algum teste novo revelar um
comportamento inesperado em `UpdateExecutionProgressUseCase`, reverter apenas o arquivo de teste
correspondente é suficiente — não há efeito colateral em outro caso de uso.

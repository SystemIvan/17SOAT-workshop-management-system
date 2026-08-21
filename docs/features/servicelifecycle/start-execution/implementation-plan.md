

# Plano de Implementação: Iniciar execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `start-execution` |
| Status | Stale |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-18 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-18) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-18) |

> Stale desde 2026-08-20: a especificação funcional foi devolvida a `Draft` pela feature
> `stock-item-reservation`. O plano histórico não deve ser usado para nova implementação até nova revisão do SDD.

## Objetivo da execução

Fechar a única lacuna aprovada na spec técnica sobre um fluxo de RF20 que já existe em produção
(`StartExecutionUseCase`, `ServiceOrder.startExecution`, `ServiceExecution.start`, endpoint
`POST /api/service-orders/{id}/executions/{executionId}/start`): não há teste de caso de uso nem HTTP
dedicado a este fluxo, e o endpoint não documenta `@ApiResponses` no Swagger. Nenhuma mudança de
comportamento de domínio, contrato HTTP ou tratamento de erro é feita — o `ServiceLifecycleExceptionHandler`
já criado durante RF19 já cobre o `IllegalStateException` de RF20.

A execução termina com `StartExecutionUseCaseTest`, `ServiceOrderControllerStartExecutionTest` e as
anotações Swagger do endpoint `start` — sem alterar `StartExecutionUseCase`,
`ServiceOrder.startExecution`, `ServiceExecution.start` nem nenhum outro caso de uso.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não alterar `StartExecutionUseCase`, `ServiceOrder.startExecution`, `ServiceExecution.start` ou
  `ServiceLifecycleExceptionHandler` — nenhuma mudança de comportamento está aprovada nesta feature.
- Não exigir `assignedTechnicianId` como pré-condição para iniciar — permanece pendente (decisão de
  time, fora de escopo conforme `functional-spec.md`).
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Testes de caso de uso (`StartExecutionUseCaseTest`) | Completed |
| 2 | Testes HTTP (`ServiceOrderControllerStartExecutionTest`) | Completed |
| 3 | Anotações Swagger e critérios de aceite | Completed |
| 4 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch `feature/servicelifecycle-start-execution` e `git status --short` sem mudanças
  inesperadas.
- Reler `StartExecutionUseCase`, `ServiceOrder.startExecution`, `ServiceExecution.start`,
  `ServiceLifecycleExceptionHandler` e a cobertura de domínio existente (`ServiceOrderTest
  .rf20_startingExecutionRequiresReadyStatusAndMovesServiceOrderToInProgress`, `ServiceExecutionTest`)
  para confirmar que nada mudou desde a spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- Branch confirmada: `feature/servicelifecycle-start-execution`.
- `git status --short`: `M .gitignore` (pré-existente, não desta feature), `?? EPIC3-TESTING.md`
  (pré-existente) e `?? docs/features/servicelifecycle/start-execution/` (as três specs desta própria
  feature) — nenhuma mudança inesperada.
- `java -version`: OpenJDK 21.0.10 — confirma Java 21.
- `StartExecutionUseCase`, `ServiceOrder.startExecution` (linha 148-151), `ServiceExecution.start`
  (linha 99-102) e `ServiceLifecycleExceptionHandler` foram relidos nesta sessão e continuam exatamente
  como descritos na spec técnica.
- Cobertura de domínio confirmada: `ServiceOrderTest
  .rf20_startingExecutionRequiresReadyStatusAndMovesServiceOrderToInProgress` já cobre `start()`
  bem-sucedido a partir de `READY` e a rejeição (`IllegalStateException`) a partir de `PENDING` — nenhum
  caso de domínio novo é necessário, conforme a spec técnica previa como possibilidade.

## Checkpoint 1 — Testes de caso de uso (`StartExecutionUseCaseTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/application/usecase/StartExecutionUseCaseTest.java`, no mesmo estilo de
`AssignTechnicianUseCaseTest` (repository fake in-memory), cobrindo:

- início bem-sucedido de uma `ServiceExecution` em status `READY` (reaproveitar o padrão
  `newAuthorizedServiceOrder` de `AssignTechnicianUseCaseTest` — autorizar sem `StockRequirement` já
  produz `READY`);
- `IllegalStateException` ao iniciar uma execução ainda `PENDING` (sem autorizar);
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### Verificação

- `./mvnw test -Dtest=StartExecutionUseCaseTest`.

### Evidência

- Novo `StartExecutionUseCaseTest` (3 casos: início bem-sucedido a partir de `READY`,
  `IllegalStateException` a partir de `PENDING`, `NoSuchElementException` quando a `ServiceOrder` não
  existe), repository fake em memória, mesmo estilo de `AssignTechnicianUseCaseTest`.
- `./mvnw test -Dtest=StartExecutionUseCaseTest`: **3 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 2 — Testes HTTP (`ServiceOrderControllerStartExecutionTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/infrastructure/web/ServiceOrderControllerStartExecutionTest.java`, no
mesmo estilo de `ServiceOrderControllerAssignTechnicianTest` (`@SpringBootTest` + `MockMvc`, preparando
estado de `READY` via `authorizeExecutionFromEstimate` chamado diretamente no aggregate dentro de
`TransactionTemplate`, já que RF19/Épico 2 authorize/reject não têm endpoint HTTP), cobrindo:

- `200 OK` com `executions[].status = "IN_PROGRESS"` no início bem-sucedido a partir de `READY`;
- `404 NOT_FOUND` para `ServiceOrder` inexistente;
- `404 NOT_FOUND` para `ServiceExecution` inexistente;
- `409 INVALID_STATE_TRANSITION` ao iniciar uma execução ainda `PENDING` (diagnosticada, não autorizada);
- `409 INVALID_STATE_TRANSITION` ao iniciar uma execução já `IN_PROGRESS` (iniciar duas vezes).

### Verificação

- `./mvnw test -Dtest=ServiceOrderControllerStartExecutionTest`.

### Evidência

- Novo `ServiceOrderControllerStartExecutionTest` (5 testes): `200` com `executions[0].status =
  "IN_PROGRESS"` no início bem-sucedido; `404`/`NOT_FOUND` para `ServiceOrder` e `ServiceExecution`
  inexistentes (2 variações); `409`/`INVALID_STATE_TRANSITION` para execução ainda `PENDING`
  (não autorizada) e para execução já `IN_PROGRESS` (segunda chamada ao endpoint). Como
  `authorizeExecutionFromEstimate` (Épico 2) ainda não tem endpoint HTTP, o estado `READY` é preparado
  chamando o aggregate diretamente via `ServiceOrderRepository` dentro de `TransactionTemplate` — mesmo
  padrão e mesma ressalva documentada em `ServiceOrderControllerAssignTechnicianTest`.
- `./mvnw test -Dtest=ServiceOrderControllerStartExecutionTest`: **5 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 3 — Anotações Swagger e critérios de aceite

### Alterações

- Adicionar `@ApiResponses` ao método `startExecution` de `ServiceOrderController`, documentando `200`,
  `404` e `409` — mesmo padrão do método `assignTechnician` (único outro endpoint do controller com
  `@ApiResponses`).
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (nome do teste), substituindo
  os `[ ]` genéricos.

### Verificação

- `OpenApiContractTest` (se aplicável) continua passando.

### Evidência

- `ServiceOrderController.startExecution` ganhou `@ApiResponses` documentando `200`, `404` e `409`,
  mesmo padrão (código + descrição curta) já usado em `assignTechnician`.
- `functional-spec.md`: os 4 critérios de aceite foram marcados `[x]` com evidência (nome dos testes)
  em vez de `[ ]` genérico.
- `./mvnw test -Dtest=OpenApiContractTest`: **1 teste, 0 falhas, BUILD SUCCESS**.

## Checkpoint 4 — Executar gates finais e concluir a feature

### Gates

- Executar `make verify` (ou `./mvnw verify`) sem testes ignorados.
- Confirmar `ModuleStructureTest` verde.
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `AssignTechnicianUseCase`, `UpdateExecutionProgressUseCase`,
  `CompleteExecutionUseCase` ou `ServiceLifecycleExceptionHandler`).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

**Achado durante a execução, fora do escopo desta feature:** `./mvnw verify` falha com 1 erro em
`ModuleStructureTest` — `ServiceOrderDevelopmentDataSeeder` (arquivo local de Santiago, não versionado,
ignorado de propósito pelo `.gitignore` via `*DevelopmentDataSeeder.java`) importa
`CustomerRepository`/`Customer` do módulo `registration`, violando a fronteira Modulith. Confirmado que
não é causado por esta feature: `git diff` mostra que nenhum arquivo desta feature toca
`ServiceOrderDevelopmentDataSeeder`, e o próprio arquivo não existe no histórico do Git (`git log` vazio
para o path). Revisado com Santiago, que optou por documentar e seguir sem corrigir o seeder nesta
feature. `./mvnw test -Dtest='!ModuleStructureTest'` confirma que todos os demais 68 testes passam.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | N/A | Endpoint não recebe body (`POST .../start` sem payload); nada a validar. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoint permanece público como os demais até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ErrorResponse(code, message)` reutiliza o handler já existente (`ServiceLifecycleExceptionHandler`), sem stack trace, SQL ou nome de classe/pacote. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; apenas IDs `UUID` opacos são manipulados. |
| Erros e information disclosure | Resolved | `409 INVALID_STATE_TRANSITION` e `404 NOT_FOUND` usam apenas mensagens de domínio já existentes (reaproveitadas de RF19), sem stack trace, SQL ou classe interna — confirmado pelos testes HTTP novos. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada; `@ApiResponses` já faz parte do Springdoc existente. |

### Verificação

- `./mvnw test -Dtest='!ModuleStructureTest'`: **68 testes, 0 falhas, BUILD SUCCESS** — inclui os 8
  testes novos de RF20 (3 `StartExecutionUseCaseTest` + 5 `ServiceOrderControllerStartExecutionTest`) e
  toda a suíte pré-existente sem regressão.
- `ModuleStructureTest` isolado: falha por causa do achado acima (`ServiceOrderDevelopmentDataSeeder`),
  não relacionado a esta feature — ver nota acima. Nenhum arquivo de produção ou teste desta feature
  introduz nova dependência entre módulos Modulith.
- `git diff --stat`: `.gitignore` (+11, pré-existente desde antes desta sessão) e
  `ServiceOrderController.java` (+5, só as anotações `@ApiResponses` do endpoint `start`). Nenhuma
  mudança em `AssignTechnicianUseCase`, `UpdateExecutionProgressUseCase`, `CompleteExecutionUseCase`,
  `ServiceLifecycleExceptionHandler`, domínio ou persistência.
- `git status --short`: dois arquivos de teste novos
  (`StartExecutionUseCaseTest`, `ServiceOrderControllerStartExecutionTest`) e as três specs desta
  feature — nada fora do escopo.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test -Dtest='!ModuleStructureTest'` | Passou: 68 testes, 0 falhas/erros |
| `./mvnw test -Dtest=StartExecutionUseCaseTest` | Passou: 3 testes |
| `./mvnw test -Dtest=ServiceOrderControllerStartExecutionTest` | Passou: 5 testes |
| `./mvnw test -Dtest=OpenApiContractTest` | Passou: 1 teste |
| `ModuleStructureTest` | Falha pré-existente e fora do escopo (ver nota acima); Santiago optou por documentar e seguir |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum (apenas `.gitignore` pré-existente e `@ApiResponses` em `ServiceOrderController`) |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter e nenhum código de produção é
alterado (apenas testes e anotações Swagger). Se algum teste novo revelar um comportamento inesperado em
`StartExecutionUseCase`, reverter apenas o arquivo de teste correspondente é suficiente — não há efeito
colateral em outro caso de uso.

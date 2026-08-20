# Plano de Implementação: Finalizar e entregar a Service Order

| Campo | Valor |
|---|---|
| Feature | `finalize-service-order` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-19) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-19) |

## Objetivo da execução

Fechar a lacuna aprovada na spec técnica sobre um fluxo de RF24 que já existe em produção
(`FinalizeServiceOrderUseCase`, `ServiceOrder.finalize`, endpoint `POST /api/service-orders/{id}/finalize`):
não há `FinalizeServiceOrderUseCaseTest` nem teste HTTP para este fluxo (a cobertura de domínio já
existe em `ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered`), e o endpoint
não documenta `@ApiResponses` no Swagger. Nenhuma mudança de comportamento de domínio, contrato HTTP
ou tratamento de erro é feita — o `ServiceLifecycleExceptionHandler` já cobre o `IllegalStateException`
de RF24.

A execução termina com `FinalizeServiceOrderUseCaseTest`, `ServiceOrderControllerFinalizeTest` e as
anotações Swagger do endpoint `finalize` — sem alterar `FinalizeServiceOrderUseCase`,
`ServiceOrder.finalize` nem nenhum outro caso de uso.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não alterar `FinalizeServiceOrderUseCase`, `ServiceOrder.finalize`, `recomputeStatusSnapshot` ou
  `ServiceLifecycleExceptionHandler` — nenhuma mudança de comportamento está aprovada nesta feature.
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Confirmar cobertura de domínio existente (`ServiceOrderTest`) | Completed |
| 2 | Testes de caso de uso (`FinalizeServiceOrderUseCaseTest`) | Completed |
| 3 | Testes HTTP (`ServiceOrderControllerFinalizeTest`) | Completed |
| 4 | Anotações Swagger e critérios de aceite | Completed |
| 5 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch atual e `git status --short` sem mudanças inesperadas.
- Reler `FinalizeServiceOrderUseCase`, `ServiceOrder.finalize`, `ServiceLifecycleExceptionHandler` e
  confirmar que não há nenhuma cobertura de teste de aplicação ou HTTP existente (domínio já coberto),
  para confirmar que nada mudou desde a spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- `git status --short` antes desta feature: `M .gitignore` (pré-existente), `?? EPIC3-TESTING.md`,
  `?? PR-RF22-complete-execution.md`, `?? PR-RF23-track-execution.md` (pré-existentes) e
  `?? docs/features/servicelifecycle/finalize-service-order/` (specs desta própria feature) — nenhuma
  mudança inesperada.
- `FinalizeServiceOrderUseCase`, `FinalizeServiceOrderRequest`, `ServiceOrder.finalize` (linha
  168-177) e `ServiceLifecycleExceptionHandler` foram relidos nesta sessão e continuam exatamente como
  descritos na spec técnica.
- Confirmado por busca que não existia `FinalizeServiceOrderUseCaseTest` nem teste HTTP para o
  endpoint `finalize`; a cobertura de domínio
  (`ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered`) já existia antes desta
  feature.

## Checkpoint 1 — Confirmar cobertura de domínio existente (`ServiceOrderTest`)

### Alterações

Nenhuma alteração de produção nem de teste nesta etapa. Reexecutar e confirmar que
`ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered` continua verde e é
suficiente para os dois casos de rejeição e o caso de sucesso.

### Verificação

- `./mvnw test -Dtest=ServiceOrderTest`.

### Evidência

- `./mvnw test -Dtest=ServiceOrderTest`: **BUILD SUCCESS, 0 falhas** — confirma que
  `rf24_finalizeRequiresCompletedStatusAndVehicleDelivered` continua verde e cobre os dois casos de
  rejeição e o caso de sucesso. Nenhum caso novo adicionado a `ServiceOrderTest` nesta feature.

## Checkpoint 2 — Testes de caso de uso (`FinalizeServiceOrderUseCaseTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/application/usecase/FinalizeServiceOrderUseCaseTest.java`, no mesmo
estilo de `CompleteExecutionUseCaseTest` (repository fake in-memory), cobrindo:

- finalização bem-sucedida de uma `ServiceOrder` `COMPLETED` com `vehicleDelivered = true`
  (`statusSnapshot = DELIVERED` na resposta);
- `IllegalStateException` ao tentar finalizar uma `ServiceOrder` que ainda não é `COMPLETED`;
- `IllegalStateException` ao tentar finalizar uma `ServiceOrder` `COMPLETED` com
  `vehicleDelivered = false`;
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### Verificação

- `./mvnw test -Dtest=FinalizeServiceOrderUseCaseTest`.

### Evidência

- Novo `FinalizeServiceOrderUseCaseTest` (4 casos): finalização bem-sucedida a partir de `COMPLETED`
  com `vehicleDelivered = true`, resultando em `ServiceOrderStatus.DELIVERED` na resposta;
  `IllegalStateException` quando a `ServiceOrder` ainda não é `COMPLETED`; `IllegalStateException`
  quando `vehicleDelivered = false`; `NoSuchElementException` quando a `ServiceOrder` não existe.
  Repository fake em memória, mesmo estilo de `CompleteExecutionUseCaseTest`.
- `./mvnw test -Dtest=FinalizeServiceOrderUseCaseTest`: **4 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 3 — Testes HTTP (`ServiceOrderControllerFinalizeTest`)

### Alterações

Nenhuma alteração de produção. Novo arquivo de teste
`src/test/java/.../serviceorder/infrastructure/web/ServiceOrderControllerFinalizeTest.java`, no mesmo
estilo de `ServiceOrderControllerCompleteExecutionTest` (`@SpringBootTest` + `MockMvc`, preparando
estado `COMPLETED` via `authorizeExecutionFromEstimate` + `startExecution` + `completeExecution`
chamados diretamente no aggregate dentro de `TransactionTemplate`), cobrindo:

- `200 OK` com `status = "DELIVERED"` ao finalizar com `vehicleDelivered: true` uma `ServiceOrder`
  `COMPLETED`;
- `409 CONFLICT` ao finalizar uma `ServiceOrder` que ainda não é `COMPLETED`;
- `409 CONFLICT` ao finalizar com `vehicleDelivered: false` uma `ServiceOrder` `COMPLETED`;
- `404 NOT_FOUND` para `ServiceOrder` inexistente.

### Verificação

- `./mvnw test -Dtest=ServiceOrderControllerFinalizeTest`.

### Evidência

- Novo `ServiceOrderControllerFinalizeTest` (4 testes): `200` com `status = "DELIVERED"` ao finalizar
  com sucesso uma `ServiceOrder` `COMPLETED` e `vehicleDelivered: true`; `409`/
  `INVALID_STATE_TRANSITION` quando a `ServiceOrder` ainda não é `COMPLETED`; `409`/
  `INVALID_STATE_TRANSITION` quando `vehicleDelivered: false`; `404`/`NOT_FOUND` para `ServiceOrder`
  inexistente. Estado `COMPLETED` preparado chamando `authorizeExecutionFromEstimate` +
  `startExecution` + `completeExecution` diretamente no aggregate via `ServiceOrderRepository` dentro
  de `TransactionTemplate` — mesmo padrão de `ServiceOrderControllerCompleteExecutionTest`.
- `./mvnw test -Dtest=ServiceOrderControllerFinalizeTest`: **4 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 4 — Anotações Swagger e critérios de aceite

### Alterações

- Adicionar `@ApiResponses` ao método `finalize` de `ServiceOrderController`, documentando `200`,
  `404` e `409` — mesmo padrão dos métodos `assignTechnician`/`startExecution`/
  `updateExecutionProgress`/`completeExecution`.
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (nome do teste),
  substituindo os `[ ]` genéricos.

### Verificação

- `./mvnw test -Dtest=OpenApiContractTest` (se aplicável) continua passando.

### Evidência

- `ServiceOrderController.finalize` ganhou `@ApiResponses` documentando `200`, `404` e `409`, mesmo
  padrão (código + descrição curta) já usado em `assignTechnician`/`startExecution`/
  `updateExecutionProgress`/`completeExecution`.
- `functional-spec.md`: os 5 critérios de aceite foram marcados `[x]` com evidência (nome dos testes)
  em vez de `[ ]` genérico.
- `./mvnw test -Dtest=OpenApiContractTest`: **1 teste, 0 falhas, BUILD SUCCESS**.

## Checkpoint 5 — Executar gates finais e concluir a feature

### Gates

- Executar `./mvnw test -Dtest='!ModuleStructureTest'` (mesma exclusão documentada em RF20–RF23, caso
  o seeder local não versionado ainda cause a falha conhecida) e, separadamente, tentar
  `ModuleStructureTest` isolado para confirmar se a causa raiz mudou.
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `AssignTechnicianUseCase`, `StartExecutionUseCase`,
  `UpdateExecutionProgressUseCase`, `CompleteExecutionUseCase` ou `ServiceLifecycleExceptionHandler`).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | N/A | O único campo do corpo (`vehicleDelivered`) é `boolean` primitivo; não há campo adicional a validar. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoint permanece público até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ServiceOrderResponse` expõe exatamente os mesmos campos já usados por outros endpoints do controller; nenhum campo novo foi adicionado. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; nenhum dado sensível é manipulado por este fluxo. |
| Erros e information disclosure | Resolved | `409 INVALID_STATE_TRANSITION` e `404 NOT_FOUND` usam apenas mensagens de domínio já existentes. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada. |

**Achado durante a execução, fora do escopo desta feature:** `./mvnw test -Dtest=ModuleStructureTest`
(isolado) falha com 1 erro — mesma causa raiz já documentada em `complete-execution/implementation-plan.md`
e `track-execution/implementation-plan.md`: `ServiceOrderDevelopmentDataSeeder` (arquivo local de
Santiago, não versionado, ignorado pelo `.gitignore` via `*DevelopmentDataSeeder.java`) importa
`CustomerRepository`/`Customer` do módulo `registration`, violando a fronteira Modulith. Confirmado
que não é causado por esta feature: `git diff --stat` mostra apenas `.gitignore` (pré-existente/local)
e `ServiceOrderController.java` (+5, só as anotações `@ApiResponses`); nenhum arquivo desta feature
toca `ServiceOrderDevelopmentDataSeeder`. `./mvnw test -Dtest='!ModuleStructureTest'` confirma que
toda a suíte restante (103 testes) passa sem regressão.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test -Dtest='!ModuleStructureTest'` | Passou: 103 testes, 0 falhas, BUILD SUCCESS |
| `./mvnw test -Dtest=ServiceOrderTest` | Passou (cobertura de domínio já existente, sem mudança) |
| `./mvnw test -Dtest=FinalizeServiceOrderUseCaseTest` | Passou: 4 testes |
| `./mvnw test -Dtest=ServiceOrderControllerFinalizeTest` | Passou: 4 testes |
| `./mvnw test -Dtest=OpenApiContractTest` | Passou: 1 teste |
| `ModuleStructureTest` (isolado) | Falha pré-existente e fora do escopo (ver nota acima) |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum (apenas `.gitignore` pré-existente/local e `@ApiResponses` em `ServiceOrderController`) |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter e nenhum código de produção de
domínio/aplicação é alterado (apenas testes e anotações Swagger). Se algum teste novo revelar um
comportamento inesperado em `FinalizeServiceOrderUseCase`, reverter apenas o arquivo de teste
correspondente é suficiente — não há efeito colateral em outro caso de uso.

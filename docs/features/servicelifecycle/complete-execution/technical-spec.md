# Especificação Técnica: Concluir execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `complete-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-19 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-19) |

## Gate de aprovação

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação/teste pode começar antes da
aprovação humana explícita desta especificação.

## Objetivo técnico e escopo

O comportamento de RF22 já está implementado (`CompleteExecutionUseCase`,
`ServiceOrder.completeExecution`, `ServiceExecution.complete`, endpoint
`POST /api/service-orders/{id}/executions/{executionId}/complete`), escrito antes do gate de SDD do
projeto. Assim como RF20/RF21, esta feature **não** propõe nenhuma mudança de comportamento de
domínio, contrato HTTP ou tratamento de erro — tudo o que RF22 precisa já existe e já está correto:

- `ServiceExecution.complete()` já rejeita qualquer status diferente de `IN_PROGRESS` com
  `IllegalStateException`;
- `ServiceLifecycleExceptionHandler` já mapeia esse `IllegalStateException` para
  `409 INVALID_STATE_TRANSITION` — nenhum handler novo é necessário;
- `ServiceOrderFinder`/`findExecution` já mapeiam `ServiceOrder`/`ServiceExecution` inexistentes para
  `404 NOT_FOUND`;
- `ServiceOrder.completeExecution` já chama `recomputeStatusSnapshot(false)`, e
  `allNonRejectedExecutionsCompleted()` já determina corretamente quando o `statusSnapshot` vira
  `COMPLETED` (ignorando execuções `REJECTED`) — já coberto por `ServiceOrderTest`.

Esta especificação cobre exclusivamente a lacuna real: **não existe `CompleteExecutionUseCaseTest` nem
teste HTTP** para este fluxo. A cobertura de domínio já é parcial mas não completa:
`ServiceOrderTest.rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone` e
`rejectedExecutionsAreIgnoredWhenComputingCompletion` já exercitam o caminho feliz e o efeito sobre o
`statusSnapshot`; `ServiceExecutionTest.cannotCompleteAnExecutionThatHasNotStarted` já cobre a rejeição
a partir de `PENDING`. O que falta:

- teste de caso de uso (`CompleteExecutionUseCaseTest`) — não existe;
- teste HTTP do endpoint (`ServiceOrderControllerCompleteExecutionTest`) — não existe;
- documentação Swagger (`@ApiResponses`) no endpoint — `ServiceOrderController.completeExecution` hoje
  só tem `@Operation`, diferente de `assignTechnician`/`startExecution`/`updateExecutionProgress`.

Esta feature não implementará:

- qualquer mudança na regra `allNonRejectedExecutionsCompleted` ou na precedência de
  `recomputeStatusSnapshot` — já implementada e já coberta por teste de domínio;
- alterar `TechnicianStatus` ao concluir a execução — depende de AD-006, `Team Decision Required`;
- autorização por papel — depende de AD-016, `Team Decision Required`;
- registrar timestamp/duração de execução — não modelado no domínio atual;
- qualquer mudança em `AssignTechnicianUseCase`, `StartExecutionUseCase` ou
  `UpdateExecutionProgressUseCase`;
- RF24 (`FinalizeServiceOrderUseCase`) — feature separada, já com sua própria cobertura de domínio.

## Contexto e desenho

Nenhuma mudança de bounded context, módulo ou aggregate. `CompleteExecutionUseCase` já depende só de
`ServiceOrderRepository` (mesmo padrão de `StartExecutionUseCase`/`UpdateExecutionProgressUseCase`).
`ServiceOrder.completeExecution` localiza a `ServiceExecution` via `findExecution` (mesmo helper de
RF19/RF20/RF21) e delega a ela, depois recalcula o `statusSnapshot`.

Nenhum pacote de `registration` ou `stockprocurement` é tocado. `ModuleStructureTest` deve continuar
verde sem exceções adicionais — não há dependência nova a adicionar.

## Interfaces e fluxo de dados

Endpoint já existe e não muda de path, verbo ou payload:

```
POST /api/service-orders/{id}/executions/{executionId}/complete
Body: nenhum
Sucesso: 200 OK com ServiceOrderResponse
  - executions[].status da execução concluída = "COMPLETED"
  - status (statusSnapshot) da ServiceOrder = "COMPLETED" se essa era a última execução não-rejeitada
    pendente; caso contrário, reflete a precedência normal de recomputeStatusSnapshot
```

Contratos de erro (já implementados, sem mudança):

| Situação | Comportamento atual |
|---|---|
| `ServiceOrder` ou `ServiceExecution` inexistente | `404` `NOT_FOUND` (`ServiceOrderFinder`/`findExecution`) |
| `ServiceExecution` em status diferente de `IN_PROGRESS` | `409` `INVALID_STATE_TRANSITION` (`ServiceLifecycleExceptionHandler`) |

Não há corpo de requisição, logo não há validação `@NotBlank`/`400` aplicável a este endpoint (diferente
de RF21).

Único gap de interface, não funcional: o endpoint não tem anotações `@ApiResponses` no Swagger, ao
contrário de `assignTechnician`/`startExecution`/`updateExecutionProgress`. Esta feature adiciona as
anotações refletindo a tabela acima, mesmo padrão já usado nos outros métodos de
`ServiceOrderController`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema e nenhuma migration nova é necessária. Nenhum dado novo é criado; os testes
reutilizam os mesmos builders/fixtures de `ServiceOrderTest`/`ServiceExecutionTest`/
`UpdateExecutionProgressUseCaseTest`/`ServiceOrderControllerStartExecutionTest`.

## Segurança e operação

- Sem mudança de autorização: o endpoint continua público, mesma limitação conhecida de todo
  `servicelifecycle`, dependente de AD-016.
- Nenhum dado pessoal ou segredo é manipulado; os IDs são `UUID` opacos. Não há corpo de requisição.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje existe cobertura parcial de domínio (via `ServiceOrderTest`/`ServiceExecutionTest`), mas nenhuma
cobertura de aplicação ou HTTP. Esta feature adiciona:

### Domínio (`ServiceExecution.complete`/`ServiceOrder.completeExecution`)

Já coberto — nenhum teste novo necessário nesta camada:

- `ServiceExecutionTest.cannotCompleteAnExecutionThatHasNotStarted` (rejeição a partir de `PENDING`);
- `ServiceOrderTest.rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone`
  (caminho feliz + efeito sobre `statusSnapshot`);
- `ServiceOrderTest.rejectedExecutionsAreIgnoredWhenComputingCompletion` (execuções `REJECTED`
  ignoradas no cálculo de `COMPLETED`).

Caso a revisão de implementação identifique um status intermediário sem cobertura direta em
`ServiceExecutionTest` (ex.: `READY`, análogo ao par `PENDING`/`READY` já usado em RF21), um teste
adicional pode ser incluído por completude, seguindo o mesmo padrão dos testes de RF21.

### Aplicação (`CompleteExecutionUseCase`, novo — `CompleteExecutionUseCaseTest`)

Testes com repository fake cobrindo, no mesmo estilo de `UpdateExecutionProgressUseCaseTest`:

- conclusão bem-sucedida de uma `ServiceExecution` `IN_PROGRESS` (helper que autoriza + inicia a
  execução, reaproveitando o padrão de fixture já usado em RF21);
- `IllegalStateException` ao tentar concluir uma execução em status diferente de `IN_PROGRESS` (ex.:
  `PENDING`, recém-diagnosticada);
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### HTTP (`ServiceOrderController`, novo — `ServiceOrderControllerCompleteExecutionTest`)

Testes MockMvc cobrindo, no mesmo estilo de `ServiceOrderControllerUpdateProgressTest`:

- `200 OK` com `executions[].status = "COMPLETED"` e `status = "COMPLETED"` da `ServiceOrder` ao
  concluir com sucesso a única execução;
- `404 NOT_FOUND` para `ServiceOrder` ou `ServiceExecution` inexistentes;
- `409 INVALID_STATE_TRANSITION` ao concluir uma execução ainda `PENDING` (sem autorizar/iniciar).

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova é introduzida.

## Decisões propostas para aprovação técnica

- [ ] Nenhuma mudança de comportamento de domínio, contrato HTTP ou tratamento de erro é feita nesta
      feature — escopo é 100% cobertura de teste (aplicação + HTTP) + anotações Swagger.
- [ ] Nenhum teste de domínio novo é obrigatório nesta feature (cobertura já existe); um teste adicional
      só é incluído se a revisão de implementação identificar um status intermediário sem cobertura.
- [ ] `CompleteExecutionUseCaseTest` e `ServiceOrderControllerCompleteExecutionTest` seguem o mesmo
      padrão estrutural (fakes in-memory, nomes de método) de `UpdateExecutionProgressUseCaseTest` e
      `ServiceOrderControllerUpdateProgressTest`.
- [ ] Nenhuma migration nova é necessária.

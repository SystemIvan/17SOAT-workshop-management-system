# Especificação Técnica: Atualizar progresso de uma execução em andamento

| Campo | Valor |
|---|---|
| Feature | `update-progress` |
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

O comportamento de RF21 já está implementado (`UpdateExecutionProgressUseCase`,
`ServiceOrder.updateExecutionProgress`, `ServiceExecution.updateProgress`, endpoint
`PATCH /api/service-orders/{id}/executions/{executionId}/progress`), escrito antes do gate de SDD do
projeto. Assim como RF20, esta feature **não** propõe nenhuma mudança de comportamento de domínio,
contrato HTTP ou tratamento de erro — tudo o que RF21 precisa já existe e já está correto:

- `ServiceExecution.updateProgress(String note)` já rejeita qualquer status diferente de
  `IN_PROGRESS` com `IllegalStateException`;
- `ServiceLifecycleExceptionHandler` já mapeia esse `IllegalStateException` para
  `409 INVALID_STATE_TRANSITION` — nenhum handler novo é necessário;
- `ServiceOrderFinder`/`findExecution` já mapeiam `ServiceOrder`/`ServiceExecution` inexistentes para
  `404 NOT_FOUND`;
- `UpdateExecutionProgressRequest.note()` já é `@NotBlank`, então `note` vazia/em branco já produz
  `400` via o handler de validação padrão do Spring (`MethodArgumentNotValidException`), sem handler
  dedicado.

Esta especificação cobre exclusivamente a lacuna real: **não existe nenhum teste dedicado** a este
fluxo (nem de caso de uso, nem HTTP, nem de domínio) — hoje `updateProgress`/`updateExecutionProgress`
não são exercitados por nenhum teste existente (confirmado por busca em `src/test/java`). Esta feature
adiciona essa cobertura e a documentação Swagger (`@ApiResponses`) do endpoint, sem alterar
comportamento.

Esta feature não implementará:

- persistir ou expor a `note` de progresso — a `functional-spec.md` registra essa lacuna como pendente
  de decisão de time; nenhum campo novo é adicionado a `ServiceExecution`, `ServiceExecutionJpaEntity`
  ou `ServiceExecutionResponse`;
- chamar `recomputeStatusSnapshot` em `updateExecutionProgress` — o status da `ServiceExecution` não
  muda nesta operação, então não há snapshot para recalcular; comportamento atual preservado;
- autorização por papel — depende de AD-016, `Team Decision Required`;
- qualquer mudança em `AssignTechnicianUseCase`, `StartExecutionUseCase` ou `CompleteExecutionUseCase`.

## Contexto e desenho

Nenhuma mudança de bounded context, módulo ou aggregate. `UpdateExecutionProgressUseCase` já depende
só de `ServiceOrderRepository` (mesmo padrão de `StartExecutionUseCase`). `ServiceOrder.updateExecutionProgress`
localiza a `ServiceExecution` via `findExecution` (mesmo helper de RF19/RF20/RF22) e delega a ela.

Nenhum pacote de `registration` ou `stockprocurement` é tocado. `ModuleStructureTest` deve continuar
verde sem exceções adicionais — não há dependência nova a adicionar.

## Interfaces e fluxo de dados

Endpoint já existe e não muda de path, verbo ou payload:

```
PATCH /api/service-orders/{id}/executions/{executionId}/progress
Body: { "note": "<string não vazia>" }
Sucesso: 200 OK com ServiceOrderResponse (executions[].status permanece "IN_PROGRESS" para a execução)
```

Contratos de erro (já implementados, sem mudança):

| Situação | Comportamento atual |
|---|---|
| `ServiceOrder` ou `ServiceExecution` inexistente | `404` `NOT_FOUND` (`ServiceOrderFinder`/`findExecution`) |
| `ServiceExecution` em status diferente de `IN_PROGRESS` | `409` `INVALID_STATE_TRANSITION` (`ServiceLifecycleExceptionHandler`) |
| `note` ausente/vazia/em branco | `400` (validação padrão do Spring via `@NotBlank`) |

Único gap de interface, não funcional: o endpoint não tem anotações `@ApiResponses` no Swagger, ao
contrário de `assignTechnician`/`startExecution` (após RF20). Esta feature adiciona as anotações
refletindo a tabela acima, mesmo padrão já usado nos outros métodos de `ServiceOrderController`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema. A `note` não é persistida (comportamento atual, documentado como lacuna
aberta na `functional-spec.md` — não resolvido nesta feature). Nenhuma migration nova é necessária.
Nenhum dado novo é criado; os testes reutilizam os mesmos builders/fixtures de
`ServiceOrderTest`/`ServiceExecutionTest`/`AssignTechnicianUseCaseTest`.

## Segurança e operação

- Sem mudança de autorização: o endpoint continua público, mesma limitação conhecida de todo
  `servicelifecycle`, dependente de AD-016.
- Nenhum dado pessoal ou segredo é manipulado; os IDs são `UUID` opacos. A `note` é texto livre
  fornecido pelo ator, mas não é logada nem persistida por este fluxo.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje não existe nenhuma cobertura, direta ou indireta, para `updateProgress`/`updateExecutionProgress`.
Esta feature adiciona:

### Domínio (`ServiceExecution.updateProgress`, novo em `ServiceExecutionTest`)

- `updateProgress` bem-sucedido a partir de status `IN_PROGRESS` (não deve lançar);
- `IllegalStateException` ao chamar `updateProgress` a partir de um status diferente de `IN_PROGRESS`
  (cobrir ao menos `PENDING` e `READY`, análogo à cobertura de `start()` em RF20).

### Aplicação (`UpdateExecutionProgressUseCase`, novo — `UpdateExecutionProgressUseCaseTest`)

Testes com repository fake cobrindo, no mesmo estilo de `AssignTechnicianUseCaseTest`/
`StartExecutionUseCaseTest`:

- atualização de progresso bem-sucedida em uma `ServiceExecution` `IN_PROGRESS` (helper que
  autoriza + inicia a execução, reaproveitando o padrão de fixture já usado em RF20);
- `IllegalStateException` ao tentar atualizar progresso de uma execução em status diferente de
  `IN_PROGRESS` (ex.: `PENDING`, recém-diagnosticada);
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### HTTP (`ServiceOrderController`, novo — `ServiceOrderControllerUpdateProgressTest`)

Testes MockMvc cobrindo, no mesmo estilo de `ServiceOrderControllerStartExecutionTest`:

- `200 OK` com `executions[].status = "IN_PROGRESS"` mantido ao atualizar progresso com sucesso;
- `404 NOT_FOUND` para `ServiceOrder` ou `ServiceExecution` inexistentes;
- `409 INVALID_STATE_TRANSITION` ao atualizar progresso de uma execução ainda `PENDING` (sem
  autorizar/iniciar);
- `400` ao enviar `note` vazia/em branco.

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova é introduzida.

## Decisões propostas para aprovação técnica

- [ ] Nenhuma mudança de comportamento de domínio, contrato HTTP ou tratamento de erro é feita nesta
      feature — escopo é 100% cobertura de teste + anotações Swagger.
- [ ] `UpdateExecutionProgressUseCaseTest` e `ServiceOrderControllerUpdateProgressTest` seguem o mesmo
      padrão estrutural (fakes in-memory, nomes de método) de `StartExecutionUseCaseTest` e
      `ServiceOrderControllerStartExecutionTest`.
- [ ] Nenhuma persistência ou exposição da `note` é adicionada nesta feature (permanece pendente,
      decisão de time, conforme `functional-spec.md`).
- [ ] Nenhuma migration nova é necessária.

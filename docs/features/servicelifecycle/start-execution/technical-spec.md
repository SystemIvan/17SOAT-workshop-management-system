# Especificação Técnica: Iniciar execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `start-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-20) |

## Revisão proposta por `stock-item-reservation`

`ServiceExecution.start()` continua a exigir exatamente `READY`. Esta revisão substitui o valor persistido
e contratual `AWAITING_PART` por `AWAITING_ITEMS`, cuja migration, JPA, OpenAPI e Postman são executados
pelo plano `stock-item-reservation`. Não há alias JSON para o status antigo.

Separação e retirada física podem demorar, mas não mudam uma execução `READY` para `AWAITING_ITEMS`; logo,
não introduzem condição adicional no endpoint de início. Os testes de RF20 devem cobrir `READY` estável e a
rejeição em `AWAITING_ITEMS`, sem criar migration própria ou dependência interna de Stock & Procurement.

## Gate de aprovação

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação/teste pode começar antes da
aprovação humana explícita desta especificação.

## Objetivo técnico e escopo

O comportamento de RF20 já está implementado (`StartExecutionUseCase`, `ServiceOrder.startExecution`,
`ServiceExecution.start`, endpoint `POST /api/service-orders/{id}/executions/{executionId}/start`),
escrito antes do gate de SDD do projeto. Diferente de RF19, esta feature **não** propõe nenhuma mudança
de comportamento de domínio, contrato HTTP ou tratamento de erro — tudo o que RF20 precisa já existe e
já está correto:

- `ServiceExecution.start()` já rejeita qualquer status diferente de `READY` com `IllegalStateException`;
- `ServiceLifecycleExceptionHandler` (criado durante RF19, `servicelifecycle/ServiceLifecycleExceptionHandler.java`)
  já mapeia esse `IllegalStateException` para `409 INVALID_STATE_TRANSITION` — nenhum handler novo é
  necessário, ele já cobre "transições de estado inválidas em RF20-RF22" por desenho;
  `ServiceOrderFinder` já mapeia `ServiceOrder`/`ServiceExecution` inexistentes para `404 NOT_FOUND`.

Esta especificação cobre exclusivamente a lacuna real: **não existe nenhum teste dedicado** a este fluxo
(nem de caso de uso, nem HTTP) — RF20 hoje só é exercitado como efeito colateral de
`ServiceOrderControllerAssignTechnicianTest.authorizeAndComplete`, que existe para preparar uma execução
`COMPLETED`, não para validar RF20 em si. Esta feature adiciona essa cobertura e a documentação Swagger
do endpoint, sem alterar comportamento.

Esta feature não implementará:

- exigir `assignedTechnicianId` preenchido como pré-condição para iniciar — permanece pendente conforme
  a `functional-spec.md`, decisão de time;
- qualquer verificação/alteração de `TechnicianStatus` — depende de AD-006, `Team Decision Required`;
- autorização por papel — depende de AD-016, `Team Decision Required`;
- qualquer mudança em `AssignTechnicianUseCase`, `UpdateExecutionProgressUseCase` ou `CompleteExecutionUseCase`.

## Contexto e desenho

Nenhuma mudança de bounded context, módulo ou aggregate. `StartExecutionUseCase` já depende só de
`ServiceOrderRepository` (nenhuma dependência de `technician` é necessária para RF20, ao contrário de
RF19). `ServiceOrder.startExecution(UUID serviceExecutionId)` localiza a `ServiceExecution` via
`findExecution` (mesmo helper usado por RF19/RF21/RF22) e delega a ela, depois recalcula o
`statusSnapshot` (AD-010, Option B — comportamento preservado, decisão de time ainda pendente).

Nenhum pacote de `registration` ou `stockprocurement` é tocado. `ModuleStructureTest` deve continuar
verde sem exceções adicionais — não há dependência nova a adicionar.

## Interfaces e fluxo de dados

Endpoint já existe e não muda de path, verbo ou payload:

```
POST /api/service-orders/{id}/executions/{executionId}/start
Body: nenhum
Sucesso: 200 OK com ServiceOrderResponse (executions[].status = "IN_PROGRESS" para a execução iniciada)
```

Contratos de erro (já implementados, sem mudança):

| Situação | Comportamento atual |
|---|---|
| `ServiceOrder` ou `ServiceExecution` inexistente | `404` `NOT_FOUND` (`ServiceOrderFinder`/`findExecution`) |
| `ServiceExecution` em status diferente de `READY` | `409` `INVALID_STATE_TRANSITION` (`ServiceLifecycleExceptionHandler`) |

Único gap de interface, não funcional: o endpoint não tem anotações `@ApiResponses` no Swagger, ao
contrário de `assignTechnician`, `performDiagnosis` etc. Esta feature adiciona as anotações refletindo a
tabela acima, seguindo o padrão já usado no método `assignTechnician` de `ServiceOrderController`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema. O status da `ServiceExecution` já é persistido via `ServiceExecutionJpaEntity`
e recarregado corretamente — comportamento exercitado indiretamente por
`ServiceOrderControllerAssignTechnicianTest.authorizeAndComplete`, que chama o endpoint `/start` como
passo intermediário. Nenhuma migration nova é necessária. Nenhum dado novo é criado; os testes reutilizam
os mesmos builders/fixtures de `ServiceOrderTest`/`ServiceExecutionTest`.

## Segurança e operação

- Sem mudança de autorização: o endpoint continua público, mesma limitação conhecida de todo
  `servicelifecycle`, dependente de AD-016.
- Nenhum dado pessoal ou segredo é manipulado; os IDs são `UUID` opacos.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje só existe cobertura indireta (ver acima) e o teste de domínio de `ServiceExecution.start()`
implícito em `ServiceExecutionTest`/`ServiceOrderTest` (a confirmar na implementação: se já cobre `READY`
→ `IN_PROGRESS` e a rejeição em outros status, não é reescrito). Esta feature adiciona:

### Domínio (revisar cobertura existente, sem mudança de comportamento)

- Confirmar que `ServiceExecutionTest`/`ServiceOrderTest` já cobrem `start()` bem-sucedido a partir de
  `READY` e a rejeição (`IllegalStateException`) a partir de pelo menos um status não-`READY`; se não
  cobrirem, adicionar caso de domínio mínimo — sem alterar `ServiceExecution.start()`.

### Aplicação (`StartExecutionUseCase`, novo — `StartExecutionUseCaseTest`)

Testes com repository fake cobrindo, no mesmo estilo de `AssignTechnicianUseCaseTest`:

- início bem-sucedido de uma `ServiceExecution` em status `READY` (helper que autoriza sem
  `StockRequirement`, como `newAuthorizedServiceOrder` de `AssignTechnicianUseCaseTest`, já produz `READY`
  porque `allMatch` numa lista vazia é `true`);
- `IllegalStateException` ao tentar iniciar uma execução em status diferente de `READY` (ex.: `PENDING`,
  recém-diagnosticada sem autorizar);
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### HTTP (`ServiceOrderController`, novo — `ServiceOrderControllerStartExecutionTest`)

Testes MockMvc cobrindo, no mesmo estilo de `ServiceOrderControllerAssignTechnicianTest`:

- `200 OK` com `executions[].status = "IN_PROGRESS"` no início bem-sucedido a partir de `READY`;
- `404 NOT_FOUND` para `ServiceOrder` ou `ServiceExecution` inexistentes;
- `409 INVALID_STATE_TRANSITION` ao iniciar uma execução ainda `PENDING` (sem autorizar) e uma já
  `IN_PROGRESS` (iniciar duas vezes).

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova é introduzida.

## Decisões propostas para aprovação técnica

- [ ] Nenhuma mudança de comportamento de domínio, contrato HTTP ou tratamento de erro é feita nesta
      feature — escopo é 100% cobertura de teste + anotações Swagger.
- [ ] `StartExecutionUseCaseTest` e `ServiceOrderControllerStartExecutionTest` seguem o mesmo padrão
      estrutural (fakes in-memory, nomes de método) de `AssignTechnicianUseCaseTest` e
      `ServiceOrderControllerAssignTechnicianTest`.
- [ ] Nenhuma verificação de `assignedTechnicianId` é adicionada a `StartExecutionUseCase` nesta feature
      (permanece pendente, decisão de time).
- [ ] Nenhuma migration nova é necessária.

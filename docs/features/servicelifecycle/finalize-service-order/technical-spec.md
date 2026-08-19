# Especificação Técnica: Finalizar e entregar a Service Order

| Campo | Valor |
|---|---|
| Feature | `finalize-service-order` |
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

O comportamento de RF24 já está implementado (`FinalizeServiceOrderUseCase` → `POST /{id}/finalize`),
escrito antes do gate de SDD do projeto. Assim como RF20/RF21/RF22/RF23, esta feature **não** propõe
nenhuma mudança de comportamento, contrato HTTP ou tratamento de erro — tudo o que RF24 precisa já
existe e já está correto:

- `FinalizeServiceOrderUseCase.execute` já usa `ServiceOrderFinder.getOrThrow` (mesmo helper de
  RF19–RF23) e chama `serviceOrder.finalize(request.vehicleDelivered())`, devolvendo
  `ServiceOrderResponse`;
- `ServiceOrder.finalize(boolean vehicleDelivered)` já valida `statusSnapshot == COMPLETED &&
  vehicleDelivered`, lançando `IllegalStateException` caso contrário, e já chama
  `recomputeStatusSnapshot(true)` no caso de sucesso;
- `IllegalStateException` já é mapeada por `ServiceLifecycleExceptionHandler.handleInvalidState` para
  `409 CONFLICT` — mesmo handler já usado por `completeExecution`/`startExecution`; nenhum handler
  novo é necessário;
- `ServiceOrderFinder.getOrThrow` já mapeia `ServiceOrder` inexistente para `404 NOT_FOUND`.

Esta especificação cobre exclusivamente a lacuna real: **não existe `FinalizeServiceOrderUseCaseTest`
nem teste HTTP** para o endpoint. O que falta:

- teste de caso de uso para `FinalizeServiceOrderUseCase` — não existe (o domínio já é coberto por
  `ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered`);
- teste HTTP para `POST /{id}/finalize` — não existe;
- documentação Swagger (`@ApiResponses`) no endpoint — `ServiceOrderController.finalize` hoje só tem
  `@Operation`, diferente de `assignTechnician`/`startExecution`/`updateExecutionProgress`/
  `completeExecution`.

Esta feature não implementará:

- autenticação/autorização de quem pode finalizar — depende de AD-016, `Team Decision Required`, e é
  a lacuna já registrada em `Architecture.md` §11 e nas specs de RF20–RF23;
- qualquer mecanismo de confirmação de entrega além do booleano `vehicleDelivered` já existente —
  mudança de comportamento, fora do escopo de fechamento de lacuna de cobertura;
- qualquer notificação em tempo real da entrega — depende de AD-015 (`Team Decision Required`) e do
  `ADR-001-realtime-updates-strategy.md`;
- qualquer mudança na regra de `finalize` ou na precedência de `recomputeStatusSnapshot` — já
  implementada e já coberta por teste de domínio.

## Contexto e desenho

Nenhuma mudança de bounded context, módulo ou aggregate. `FinalizeServiceOrderUseCase` já é
`@Transactional` e depende só de `ServiceOrderRepository` (mesmo padrão dos demais use cases do
épico). Nenhum pacote de `registration` ou `stockprocurement` é tocado. `ModuleStructureTest` deve
continuar verde sem exceções adicionais.

## Interfaces e fluxo de dados

Endpoint já existe e não muda de path, verbo ou payload:

```
POST /api/service-orders/{id}/finalize
Body: { "vehicleDelivered": boolean }
Sucesso: 200 OK com ServiceOrderResponse (statusSnapshot = DELIVERED)
```

Contratos de erro (já implementados, sem mudança):

| Situação | Comportamento atual |
|---|---|
| `ServiceOrder` inexistente | `404` `NOT_FOUND` (`ServiceOrderFinder.getOrThrow`) |
| `statusSnapshot != COMPLETED` ou `vehicleDelivered == false` | `409` `CONFLICT` (`ServiceLifecycleExceptionHandler.handleInvalidState`, mesmo padrão de `completeExecution`) |

Não há campos de validação `@NotBlank`/`@NotNull` aplicáveis: `vehicleDelivered` é `boolean`
primitivo (não `Boolean`), logo sempre presente no corpo desserializado.

Único gap de interface, não funcional: o endpoint não tem anotações `@ApiResponses` no Swagger. Esta
feature adiciona as anotações refletindo a tabela acima, mesmo padrão já usado nos outros métodos de
`ServiceOrderController`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema e nenhuma migration nova é necessária. Nenhum dado novo é criado; os testes
reutilizam os mesmos builders/fixtures de `ServiceOrderTest`/`CompleteExecutionUseCaseTest`/
`ServiceOrderControllerCompleteExecutionTest`, levando a `ServiceOrder` até `COMPLETED` (diagnóstico →
autorização → início → conclusão de execução) antes de finalizar.

## Segurança e operação

- Sem mudança de autorização: o endpoint continua público, mesma limitação conhecida de todo
  `servicelifecycle`, dependente de AD-016 — explicitamente registrada como lacuna também em
  `Architecture.md` §11 e nas specs de RF20–RF23.
- Nenhum dado pessoal ou segredo é manipulado; o ID é `UUID` opaco e o corpo é um único booleano.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje não existe nenhuma cobertura de aplicação ou HTTP para o endpoint de finalização. Esta feature
adiciona:

### Domínio (`ServiceOrder.finalize`)

Já coberto — nenhum teste novo necessário nesta camada:
`ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered` já exercita os dois casos
de rejeição (status diferente de `COMPLETED`; `vehicleDelivered = false`) e o caso de sucesso
(`DELIVERED`).

### Aplicação (novo — `FinalizeServiceOrderUseCaseTest`)

Testes com repository fake cobrindo, no mesmo estilo de `CompleteExecutionUseCaseTest`:

- finaliza com sucesso uma `ServiceOrder` `COMPLETED` com `vehicleDelivered = true`, resultando em
  `statusSnapshot = DELIVERED` na resposta;
- rejeita finalizar uma `ServiceOrder` que ainda não é `COMPLETED` (`IllegalStateException`);
- rejeita finalizar uma `ServiceOrder` `COMPLETED` com `vehicleDelivered = false`
  (`IllegalStateException`);
- `NoSuchElementException` quando a `ServiceOrder` não existe.

### HTTP (novo — `ServiceOrderControllerFinalizeTest`)

Testes MockMvc cobrindo, no mesmo estilo de `ServiceOrderControllerCompleteExecutionTest`:

- `POST /{id}/finalize` com `vehicleDelivered: true` numa `ServiceOrder` `COMPLETED`: `200 OK` com
  `status = "DELIVERED"`;
- `POST /{id}/finalize` numa `ServiceOrder` que ainda não é `COMPLETED`: `409 CONFLICT`;
- `POST /{id}/finalize` com `vehicleDelivered: false` numa `ServiceOrder` `COMPLETED`: `409 CONFLICT`;
- `POST /{id}/finalize` numa `ServiceOrder` inexistente: `404 NOT_FOUND`.

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova é introduzida.

## Decisões propostas para aprovação técnica

- [ ] Nenhuma mudança de comportamento, contrato HTTP ou tratamento de erro é feita nesta feature —
      escopo é 100% cobertura de teste (aplicação + HTTP) + anotações Swagger no endpoint de
      finalização já existente.
- [ ] Nenhum teste de domínio novo é obrigatório nesta feature (cobertura já existe via
      `ServiceOrderTest`).
- [ ] O erro de estado inválido (`statusSnapshot != COMPLETED` ou `vehicleDelivered = false`) é
      documentado como `409 CONFLICT`, mesmo mapeamento já usado por `completeExecution`/
      `startExecution` via `ServiceLifecycleExceptionHandler`.
- [ ] Nenhuma migration nova é necessária.

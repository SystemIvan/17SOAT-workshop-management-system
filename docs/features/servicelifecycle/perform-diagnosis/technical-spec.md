# Especificação Técnica: Registrar Diagnóstico

| Campo | Valor |
|---|---|
| Feature | `perform-diagnosis` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `docs/features/servicelifecycle/perform-diagnosis/functional-spec.md` |

> **Nota:** este documento é retroativo. Descreve a arquitetura já implementada em produção, não uma
> proposta nova. Onde a implementação atual diverge do que seria a escolha ideal, isso é registrado
> explicitamente em vez de omitido.

## Objetivo técnico

Documentar a implementação existente de registro de diagnóstico (`ServiceOrder.performDiagnosis`,
`PerformDiagnosisUseCase`, `POST /api/service-orders/{id}/diagnosis`) no módulo
`servicelifecycle.serviceorder`, cobrindo domínio, caso de uso e contrato HTTP, para fechar o gap de
gate SDD identificado no mesmo levantamento que originou a documentação retroativa de RF09/RF10.

## Contexto e fronteiras

A implementação pertence a `servicelifecycle.serviceorder`. `ServiceExecution` é uma Entity que vive
dentro da fronteira do aggregate `ServiceOrder` — não é acessada nem persistida isoladamente.
`catalogServiceId` é uma referência por ID para `registration.serviceCatalog` (placeholder de pacote,
ver `AGENTS.md` §"Bounded contexts"); nenhum pacote interno de outro módulo é importado por
`serviceorder`.

## Estrutura existente

- `serviceorder/domain/model/ServiceOrder.java` (`performDiagnosis(List<DiagnosisItem>)`,
  `openDiagnosisId`, `recomputeStatusSnapshot`)
- `serviceorder/domain/model/ServiceExecution.java` (`start(...)`, `attachStockRequirement`,
  `ServiceExecutionStatus`)
- `serviceorder/domain/model/DiagnosisItem.java` (record de entrada do use case para o domínio)
- `serviceorder/application/dto/PerformDiagnosisRequest.java`, `DiagnosisItemRequest.java`,
  `StockRequirementRequest.java`, `ServiceOrderMapper.java` (`toDiagnosisItems`)
- `serviceorder/application/usecase/PerformDiagnosisUseCase.java`
- `serviceorder/infrastructure/web/ServiceOrderController.java`
  (`POST /api/service-orders/{id}/diagnosis`)

## Domínio

### `ServiceOrder.performDiagnosis(List<DiagnosisItem>)`

```java
public UUID performDiagnosis(List<DiagnosisItem> items) {
    if (openDiagnosisId != null) {
        throw new IllegalStateException("A diagnosis is already open without an Estimate generated for it");
    }
    UUID diagnosisId = UUID.randomUUID();
    for (DiagnosisItem item : items) {
        ServiceExecution execution = ServiceExecution.start(diagnosisId, item.catalogServiceId(), item.name(), item.price());
        item.stockRequirements().forEach(execution::attachStockRequirement);
        serviceExecutions.add(execution);
    }
    this.openDiagnosisId = diagnosisId;
    recomputeStatusSnapshot(false);
    return diagnosisId;
}
```

- Rejeita (`IllegalStateException`) quando já existe um `openDiagnosisId` não nulo — só há um
  diagnóstico aberto por vez, até que um Estimate seja gerado sobre ele (fechamento de `openDiagnosisId`
  é responsabilidade de `estimate-generation`, fora do escopo desta feature).
- Gera um `diagnosisId` novo (`UUID.randomUUID()`), compartilhado por todos os `ServiceExecution`
  criados nesta chamada.
- Cada `DiagnosisItem` vira um `ServiceExecution` via `ServiceExecution.start(...)`, que começa em
  `ServiceExecutionStatus.PENDING`.
- `stockRequirements` de cada item são anexados ao `ServiceExecution` correspondente via
  `attachStockRequirement` — sem validação de existência do `stockItemId` em `stockprocurement` neste
  ponto (o `StockRequirement` é armazenado como snapshot, mesmo padrão de referência por ID + valor
  copiado já usado no `VehicleSnapshot` de RF09).
- `recomputeStatusSnapshot(false)` deriva `statusSnapshot = ServiceOrderStatus.IN_DIAGNOSIS` sempre que
  `openDiagnosisId != null` (guarda anterior na cadeia de prioridade do método, não alterada por esta
  feature).

Método de domínio relacionado não coberto por este documento: `addServiceExecution(diagnosisId, ...)`
adiciona um único `ServiceExecution` a um diagnóstico já aberto — existe no código mas não tem nenhum
caller em `src/main` hoje; permanece fora de escopo (ver `functional-spec.md`).

## Caso de uso: `PerformDiagnosisUseCase`

Fluxo:

1. carregar `ServiceOrder` (`ServiceOrderFinder.getOrThrow`, mesmo padrão dos demais use cases de
   `serviceorder`);
2. mapear `PerformDiagnosisRequest.items()` → `List<DiagnosisItem>` via
   `ServiceOrderMapper.toDiagnosisItems`;
3. `serviceOrder.performDiagnosis(items)`;
4. persistir via `ServiceOrderRepository.save(...)`;
5. retornar `ServiceOrderResponse` (via `ServiceOrderMapper.toResponse`, já existente).

```java
@Service
public class PerformDiagnosisUseCase {

    private final ServiceOrderRepository repository;

    public PerformDiagnosisUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, PerformDiagnosisRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        List<DiagnosisItem> items = ServiceOrderMapper.toDiagnosisItems(request.items());
        serviceOrder.performDiagnosis(items);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
```

## Repository

Nenhuma mudança — `ServiceOrderRepository` já expõe `findById`/`save`, suficientes para este fluxo. Os
`ServiceExecution` criados são persistidos como parte do agregado `ServiceOrder` (mesma transação,
mesmo `save`), não têm tabela ou repository próprios.

## Persistência

Nenhuma migration nova. `service_executions` já existe desde a migration de baseline
(`V20260815000000__initial_schema.sql`) e já é usada pelas features de RF19+ (atribuição, início,
progresso, conclusão) — este documento não introduz nem modifica schema.

## API

`POST /api/service-orders/{id}/diagnosis`

Request (`PerformDiagnosisRequest`):
```json
{
  "items": [
    {
      "catalogServiceId": "<UUID>",
      "name": "<string>",
      "price": {"value": "<decimal >= 0>", "currency": "<string>"},
      "stockRequirements": [
        {
          "stockItemId": "<UUID>",
          "type": "<StockItemType>",
          "quantity": "<int positivo>",
          "nameSnapshot": "<string>",
          "priceSnapshot": {"value": "<decimal >= 0>", "currency": "<string>"}
        }
      ]
    }
  ]
}
```

Validação via Bean Validation: `items` é `@NotEmpty`; cada item exige `catalogServiceId` (`@NotNull`),
`name` (`@NotBlank`) e `price` (`@NotNull @Valid`); `stockRequirements` é opcional, mas cada elemento,
quando presente, exige `stockItemId`, `type`, `quantity` (`@Positive`), `nameSnapshot` e
`priceSnapshot`.

Resposta de sucesso: `200 OK` com `ServiceOrderResponse`, refletindo os novos `executions` (um por item
informado, todos `PENDING`) e `status = IN_DIAGNOSIS`.

## Tratamento de erros

- `404 Not Found`, código `NOT_FOUND` — Service Order inexistente (`ServiceOrderFinder.getOrThrow`,
  `GlobalExceptionHandler`).
- `409 Conflict`, código `INVALID_STATE_TRANSITION` — já existe um diagnóstico aberto para a Service
  Order (`ServiceLifecycleExceptionHandler`, mesmo mapeamento de `IllegalStateException` já usado por
  RF10 e pelas transições de `ServiceExecution`).
- `400 Bad Request`, código `VALIDATION_ERROR` — `items` ausente/vazio ou campos obrigatórios de algum
  item ausentes/inválidos.

## Segurança e operação

- Nenhum mecanismo de autenticação/autorização no projeto hoje; este endpoint segue o mesmo padrão dos
  demais — risco pré-existente de plataforma, não introduzido por esta feature.
- Nenhum dado pessoal exposto — `catalogServiceId`/`stockItemId` são IDs opacos; `name`/`nameSnapshot`
  são nomes de serviço/peça, não de pessoas.
- IDs tratados como UUID; nenhuma concatenação manual de entrada em SQL.
- Superfície de abuso: qualquer chamador pode registrar diagnóstico para qualquer Service Order (sem
  autenticação) e informar `catalogServiceId`/`stockItemId` arbitrários, não validados contra
  `registration`/`stockprocurement` no momento do diagnóstico — mesmo padrão de risco já registrado em
  RF09 (`service-order-creation/technical-spec.md`), não uma lacuna introduzida por esta feature.

## Estratégia de testes

### Domínio
- `ServiceOrderTest` (já existente) cobre: `performDiagnosis` move o status para `IN_DIAGNOSIS`;
  registrar diagnóstico com um diagnóstico já aberto lança `IllegalStateException`.

### Application
- `PerformDiagnosisUseCaseTest` (novo, fechando o gap desta feature): fluxo válido com múltiplos itens
  (persiste e retorna um `ServiceExecution` por item); Service Order inexistente propaga
  `NoSuchElementException`.

### Web
- `ServiceOrderControllerDiagnosisTest` (novo, fechando o gap desta feature): `200` com os
  `ServiceExecution` refletidos na resposta; `404` para Service Order inexistente; `400` para `items`
  vazio; `409`/`INVALID_STATE_TRANSITION` para diagnóstico já aberto.

## Fora de escopo técnico

- geração de Estimate a partir do diagnóstico e fechamento de `openDiagnosisId` (feature
  `estimate-generation`, já especificada separadamente);
- `addServiceExecution` (adicionar execução avulsa a diagnóstico já aberto);
- validação de existência de `catalogServiceId`/`stockItemId` em outros módulos;
- execução dos serviços diagnosticados (RF19+).

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada.
- [x] Technical Spec revisada e aprovada.

Antes do PR:

- [ ] testes unitários passando (novos: `PerformDiagnosisUseCaseTest`,
  `ServiceOrderControllerDiagnosisTest`);
- [ ] testes de integração aplicáveis passando;
- [ ] `make verify` executado após esta spec ser aprovada;
- [x] nenhuma migration nova necessária (schema já existente);
- [x] OpenAPI já reflete o endpoint (`@Operation` em `performDiagnosis`, já presente antes deste gate);
- [x] Postman já reflete o endpoint (`POST .../diagnosis` já presente na collection antes deste gate);
- [x] nenhuma fronteira do Spring Modulith violada.

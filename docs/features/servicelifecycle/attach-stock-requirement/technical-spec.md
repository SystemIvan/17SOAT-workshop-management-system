# Especificação Técnica: Anexar Necessidade de Estoque a uma ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `attach-stock-requirement` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `docs/features/servicelifecycle/attach-stock-requirement/functional-spec.md` |

## Objetivo técnico

Expor `ServiceOrder.attachStockRequirement(UUID, StockRequirement)` — já público, mas hoje sem nenhum
caller de produção (só é atingido indiretamente via `performDiagnosis`) — através de um caso de uso e um
endpoint HTTP dedicado, com a guarda de status e o recálculo de prontidão confirmados na
`functional-spec.md`.

## Contexto e fronteiras

A implementação pertence inteiramente a `servicelifecycle.serviceorder`, reaproveitando o aggregate
`ServiceOrder`/entidade `ServiceExecution` e o VO `StockRequirement` já existentes. Nenhuma importação
de pacote interno de outro módulo é necessária: `stockItemId` continua sendo referenciado apenas por
UUID, como já ocorre em `perform-diagnosis` — nenhuma leitura viva do `stockprocurement`.

## Estrutura proposta

- `serviceorder/domain/model/ServiceExecution.java` — alterar `attachStockRequirement` (método
  package-private existente): adicionar guarda de status e chamar `recomputeReadiness()` ao final.
- `serviceorder/domain/model/ServiceOrder.java` — `attachStockRequirement` (método público existente)
  não muda de assinatura; passa a se beneficiar da guarda/recálculo movidos para
  `ServiceExecution.attachStockRequirement`. Precisa passar a chamar `recomputeStatusSnapshot(false)`
  ao final, mesmo padrão de `applyStockReservation`.
- `serviceorder/application/dto/AttachStockRequirementRequest.java` (novo) — wrapper fino sobre
  `StockRequirementRequest` já existente (ver "Interfaces e fluxo de dados").
- `serviceorder/application/usecase/AttachStockRequirementUseCase.java` (novo).
- `serviceorder/infrastructure/web/ServiceOrderController.java` — adicionar endpoint (alteração de
  arquivo existente).

Nenhum arquivo de persistência (`ServiceExecutionJpaEntity`, `StockRequirementEmbeddable`,
`ServiceOrderPersistenceMapper`) muda: a lista de `StockRequirement` por `ServiceExecution` já é
persistida integralmente a cada `save()` do aggregate `ServiceOrder`, mesmo caminho que
`performDiagnosis` já usa hoje.

## Domínio

### `ServiceExecution.attachStockRequirement` — guarda de status e recálculo de prontidão

```java
void attachStockRequirement(StockRequirement requirement) {
    if (status == ServiceExecutionStatus.COMPLETED || status == ServiceExecutionStatus.REJECTED) {
        throw new IllegalStateException(
                "Cannot attach a stock requirement to a ServiceExecution in status " + status);
    }
    stockRequirements.add(requirement);
    recomputeReadiness();
}
```

`recomputeReadiness()` já existe e só age quando `status` é `AUTHORIZED` ou `AWAITING_PART` (ver
`ServiceExecution.java:120-126`). Isso cobre a regra funcional "`READY` recua para `AWAITING_PART`":
`READY` não é um dos dois status ali testados hoje — é preciso incluí-lo na condição para que o recálculo
realmente rebaixe um `ServiceExecution` `READY` ao anexar um item não reservado:

```java
private void recomputeReadiness() {
    if (status != ServiceExecutionStatus.AUTHORIZED
            && status != ServiceExecutionStatus.AWAITING_PART
            && status != ServiceExecutionStatus.READY) {
        return;
    }
    boolean allReserved = stockRequirements.stream().allMatch(StockRequirement::reserved);
    this.status = allReserved ? ServiceExecutionStatus.READY : ServiceExecutionStatus.AWAITING_PART;
}
```

Para `PENDING` e `IN_PROGRESS` (também permitidos pela `functional-spec.md`), a guarda acima não barra a
chamada, e `recomputeReadiness()` retorna sem alterar `status` — exatamente o comportamento "anexar não
altera o status" descrito na spec funcional.

Este é o mesmo padrão de guarda já usado em `confirmTechnicianAssignment` (bloqueia `COMPLETED`/
`REJECTED` com `IllegalStateException`) e em `startExecution`/`complete` (`requireStatus`).

`performDiagnosis` continua funcionando sem mudança: todo `ServiceExecution` criado ali começa em
`PENDING`, que segue permitido.

### `ServiceOrder.attachStockRequirement` — recomputar `statusSnapshot`

```java
public void attachStockRequirement(UUID serviceExecutionId, StockRequirement requirement) {
    findExecution(serviceExecutionId).attachStockRequirement(requirement);
    recomputeStatusSnapshot(false);
}
```

Mesmo padrão de todo outro comando do aggregate que pode afetar o status derivado (`applyStockReservation`,
`startExecution`, `completeExecution`).

## Caso de uso: `AttachStockRequirementUseCase`

Fluxo:

1. carregar `ServiceOrder` por `ServiceOrderRepository` (`ServiceOrderFinder.getOrThrow`, mesmo padrão
   dos demais use cases de `serviceorder`);
2. converter `StockRequirementRequest` em `StockRequirement` via `ServiceOrderMapper.toStockRequirement`
   (já existente, já usado por `toDiagnosisItem` — nenhuma duplicação de lógica de mapeamento);
3. chamar `serviceOrder.attachStockRequirement(executionId, requirement)` — `NoSuchElementException` se
   `executionId` não existir dentro do aggregate (mesmo padrão de `findExecution`, já tratado hoje pelo
   `GlobalExceptionHandler` genérico como `404`);
4. persistir via `ServiceOrderRepository.save(...)`;
5. retornar `ServiceOrderResponse` (via `ServiceOrderMapper.toResponse`, já existente — nenhuma mudança
   no DTO de resposta, `stockRequirements` já é exposto em `ServiceExecutionResponse`).

```java
@Service
public class AttachStockRequirementUseCase {

    private final ServiceOrderRepository repository;

    public AttachStockRequirementUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID executionId, StockRequirementRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.attachStockRequirement(executionId, ServiceOrderMapper.toStockRequirement(request));
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
```

`StockRequirementRequest` já é o contrato de entrada usado por `perform-diagnosis`; não é necessário um
DTO `AttachStockRequirementRequest` novo — o use case recebe `StockRequirementRequest` diretamente. Isso
simplifica a "Estrutura proposta" acima: não criar `AttachStockRequirementRequest.java`.

## Repository

Nenhuma mudança — `ServiceOrderRepository` já expõe `findById`/`save`, suficientes para este fluxo.

## Interfaces e fluxo de dados

`POST /api/service-orders/{id}/executions/{executionId}/stock-requirements`

Escolhido `POST` (não `PATCH`) por consistência com os outros endpoints que adicionam algo a um
`ServiceExecution` (`.../assign-technician`, `.../start`, `.../complete`) — anexar um novo
`StockRequirement` é uma adição, não uma atualização parcial de um campo escalar como `priority` ou
`progress`.

Request (`StockRequirementRequest`, já existente, reaproveitado sem alteração):
```json
{
  "stockItemId": "b6e6c6b0-...-...",
  "type": "PART",
  "quantity": 2,
  "nameSnapshot": "Filtro de óleo",
  "priceSnapshot": { "value": 45.90, "currency": "BRL" }
}
```

Resposta de sucesso: `200 OK` com `ServiceOrderResponse` (mesmo formato de todo endpoint que muta a
Service Order), incluindo o `ServiceExecution` afetado com o novo `StockRequirement` (sempre
`reserved: false`) na lista `stockRequirements`.

```java
@PostMapping("/{id}/executions/{executionId}/stock-requirements")
@Operation(summary = "Attach a stock requirement to a service execution")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock requirement attached"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing stock requirement fields"),
        @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
        @ApiResponse(responseCode = "409", description = "Service execution is completed or rejected")
})
public ResponseEntity<ServiceOrderResponse> attachStockRequirement(
        @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody StockRequirementRequest request) {
    return ResponseEntity.ok(attachStockRequirementUseCase.execute(id, executionId, request));
}
```

O contrato deve ser refletido em OpenAPI (via `@Operation`/`@ApiResponses`, gerado automaticamente pelo
springdoc) e na collection Postman
(`docs/api/postman/workshop-management-system.postman_collection.json`) antes da conclusão da feature.

## Tratamento de erros

- `404 Not Found`, código `NOT_FOUND` — Service Order inexistente (`ServiceOrderFinder.getOrThrow`) ou
  `executionId` inexistente dentro da Service Order (`NoSuchElementException` de `findExecution`, já
  mapeada hoje pelo `GlobalExceptionHandler` para `404`/`NOT_FOUND` — mesmo comportamento observado nos
  demais endpoints de `.../executions/{executionId}/...`).
- `409 Conflict`, código `INVALID_STATE_TRANSITION` — `ServiceExecution` em status `COMPLETED` ou
  `REJECTED` (`ServiceLifecycleExceptionHandler`, já existente, nenhuma mudança necessária).
- `400 Bad Request`, código `VALIDATION_ERROR` — campos obrigatórios ausentes ou `quantity <= 0`
  (Bean Validation em `StockRequirementRequest`, já existente e já usado por `perform-diagnosis`).

## Persistência e dados de bootstrap

Nenhuma migration nova. `stockRequirements` já é uma coleção mapeada via `@ElementCollection`
(`StockRequirementEmbeddable`) em `ServiceExecutionJpaEntity`/tabela associada, gravada por
`ServiceOrderPersistenceMapper` no `save()` do aggregate já existente. Nenhum dado é classificado como
seed — esta feature não introduz nenhuma tabela ou dado de referência novo.

## Segurança e operação

- Nenhum dado sensível adicional exposto; a resposta já expõe `stockRequirements` hoje (via
  `perform-diagnosis`).
- Nenhuma regra de autorização nova (mesma ausência de autenticação do restante do projeto — ver
  `docs/features/servicelifecycle/service-order-creation/technical-spec.md` §"Segurança e operação" para
  a mesma observação já registrada).
- IDs tratados como UUID; nenhuma concatenação manual de entrada em SQL.
- Superfície de abuso: qualquer chamador pode anexar um `StockRequirement` a qualquer `ServiceExecution`
  não terminal (sem autenticação), mesmo padrão de risco pré-existente de todos os outros endpoints de
  mutação do projeto — não é uma lacuna introduzida por esta feature. `quantity` é limitado a `> 0` pelo
  VO `StockRequirement`, mas não há teto superior — consistente com `perform-diagnosis`, que tem a mesma
  ausência de limite.

## Estratégia de testes

### Domínio
- `ServiceExecution.attachStockRequirement` adiciona o item quando o status permite (`PENDING`,
  `AUTHORIZED`, `READY`, `AWAITING_PART`, `IN_PROGRESS`).
- `ServiceExecution.attachStockRequirement` lança `IllegalStateException` quando o status é `COMPLETED`.
- `ServiceExecution.attachStockRequirement` lança `IllegalStateException` quando o status é `REJECTED`.
- `ServiceExecution.attachStockRequirement` rebaixa o status de `READY` para `AWAITING_PART` quando o
  novo item não está reservado.
- `ServiceExecution.attachStockRequirement` não altera o status quando ele é `PENDING` ou `IN_PROGRESS`.
- `ServiceOrder.attachStockRequirement` recalcula `statusSnapshot` após o anexo (cenário: execução única
  `READY` vira `AWAITING_PART`, Service Order deixa de estar pronta para iniciar).
- `ServiceOrder.attachStockRequirement` lança `NoSuchElementException` para `executionId` inexistente.

### Application
- `AttachStockRequirementUseCaseTest`: fluxo válido (persiste e retorna o `StockRequirement` na
  resposta); Service Order inexistente propaga `NoSuchElementException`; `ServiceExecution` inexistente
  propaga `NoSuchElementException`; `ServiceExecution` `COMPLETED`/`REJECTED` propaga
  `IllegalStateException`.

### Web
- `ServiceOrderControllerAttachStockRequirementTest`: `200` com o novo `StockRequirement` refletido na
  resposta; `404` para Service Order inexistente; `404` para `executionId` inexistente; `409`/
  `INVALID_STATE_TRANSITION` para `ServiceExecution` `COMPLETED`/`REJECTED` (setup direto via
  `ServiceOrderRepository`, mesmo padrão já usado em `ServiceOrderControllerAssignTechnicianTest`);
  `400`/`VALIDATION_ERROR` para campos ausentes ou `quantity <= 0`.

## Fora de escopo técnico

- reserva do item de estoque (`applyStockReservation`, já implementado e fora do escopo desta feature);
- validação de existência do `stockItemId` no `stockprocurement`;
- remoção ou edição de um `StockRequirement` já anexado;
- mesclar/deduplicar `StockRequirement`s com o mesmo `stockItemId` (confirmado na `functional-spec.md`);
- qualquer evento de domínio publicado ao anexar (não existe hoje para `performDiagnosis` tampouco).

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada.
- [x] Technical Spec revisada e aprovada.

Antes do PR:

- [ ] testes unitários passando;
- [ ] testes de integração aplicáveis passando;
- [ ] `make verify` passando;
- [ ] nenhuma migration nova necessária;
- [ ] OpenAPI atualizado;
- [ ] Postman atualizado;
- [ ] nenhuma fronteira do Spring Modulith violada.

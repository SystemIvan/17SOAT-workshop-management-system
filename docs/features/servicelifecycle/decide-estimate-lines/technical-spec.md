# Especificação Técnica: Decidir Linhas de uma Estimate (Aprovar/Rejeitar por ServiceExecution)

| Campo | Valor |
|---|---|
| Feature | `decide-estimate-lines` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `docs/features/servicelifecycle/decide-estimate-lines/functional-spec.md` |

## Objetivo técnico

Expor `ServiceOrder.authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate` — já públicos, mas
hoje sem nenhum caller de produção — através de um caso de uso e um endpoint HTTP que aceitam decisões
em lote (uma ou mais linhas de uma Estimate), com as validações de pertencimento e status confirmadas na
`functional-spec.md`.

## Contexto e fronteiras

A implementação cruza dois aggregates do mesmo bounded context (`servicelifecycle`): `Estimate`
(`estimate` submódulo) é lida para validar que cada `serviceExecutionId` informado pertence de fato
àquela Estimate; `ServiceOrder` (`serviceorder` submódulo) é o aggregate mutado, via os métodos de
domínio já existentes. Isso já é o padrão estabelecido em `GenerateEstimateUseCase`, que também lê
`ServiceOrder` a partir do módulo `estimate`. Nenhuma importação de pacote `domain`/`infrastructure`
interno é cruzada — apenas repositórios e modelos de domínio já públicos de `serviceorder`.

Nenhuma mudança na `Estimate`: ela permanece somente leitura após a criação (nenhum campo de status
novo, conforme a nota sobre AD-008 na `functional-spec.md`).

## Estrutura proposta

- `estimate/application/dto/DecideEstimateLinesRequest.java` (novo)
- `estimate/application/dto/EstimateLineDecision.java` (novo, enum `APPROVED`/`REJECTED`)
- `estimate/application/usecase/DecideEstimateLinesUseCase.java` (novo)
- `estimate/infrastructure/web/EstimateController.java` — adicionar endpoint (alteração de arquivo
  existente).

Nenhuma mudança em `ServiceOrder`/`ServiceExecution`: `authorizeExecutionFromEstimate`,
`rejectExecutionFromEstimate` e as guardas de status (`requireStatus(PENDING)`) já existem e já cobrem
as regras de negócio descritas na `functional-spec.md`.

## Caso de uso: `DecideEstimateLinesUseCase`

Fluxo:

1. carregar `Estimate` por `EstimateRepository.findById` — `NoSuchElementException` se não existir
   (→ `404`/`NOT_FOUND`, mesmo padrão de `GetEstimateUseCase`);
2. validar que a lista de decisões não tem `serviceExecutionId` repetido — `IllegalArgumentException`
   caso contrário (→ `400`/`VALIDATION_ERROR`, ver "Tratamento de erros");
3. validar que cada `serviceExecutionId` da requisição corresponde a uma `EstimateLine` da Estimate
   carregada — `NoSuchElementException` caso contrário (→ `404`/`NOT_FOUND`);
4. carregar `ServiceOrder` por `ServiceOrderRepository.findById(estimate.serviceOrderId())`
   (`ServiceOrderFinder.getOrThrow`, mesmo padrão dos demais use cases de `serviceorder`);
5. para cada decisão, na ordem recebida, chamar
   `serviceOrder.authorizeExecutionFromEstimate(estimate.id(), serviceExecutionId)` (decisão
   `APPROVED`) ou `serviceOrder.rejectExecutionFromEstimate(estimate.id(), serviceExecutionId)`
   (decisão `REJECTED`) — `IllegalStateException` se a `ServiceExecution` não estiver `PENDING`
   (→ `409`/`INVALID_STATE_TRANSITION`); como o método já lança antes de qualquer persistência, a
   transação inteira é revertida (tudo-ou-nada, conforme a regra de negócio);
6. persistir via `ServiceOrderRepository.save(...)`;
7. retornar `ServiceOrderResponse` (via `ServiceOrderMapper.toResponse`, já existente — reaproveitado
   do módulo `serviceorder`, mesmo padrão de retorno de todo comando que muta a Service Order).

```java
@Service
public class DecideEstimateLinesUseCase {

    private final EstimateRepository estimateRepository;
    private final ServiceOrderRepository serviceOrderRepository;

    public DecideEstimateLinesUseCase(
            EstimateRepository estimateRepository, ServiceOrderRepository serviceOrderRepository) {
        this.estimateRepository = estimateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID estimateId, DecideEstimateLinesRequest request) {
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new NoSuchElementException("Estimate not found: " + estimateId));

        Set<UUID> requestedIds = request.decisions().stream()
                .map(LineDecisionRequest::serviceExecutionId)
                .collect(Collectors.toSet());
        if (requestedIds.size() != request.decisions().size()) {
            throw new IllegalArgumentException("Duplicate serviceExecutionId in the same request");
        }

        Set<UUID> lineIds = estimate.lines().stream()
                .map(EstimateLine::serviceExecutionId)
                .collect(Collectors.toSet());
        for (UUID requestedId : requestedIds) {
            if (!lineIds.contains(requestedId)) {
                throw new NoSuchElementException(
                        "ServiceExecution " + requestedId + " is not part of estimate " + estimateId);
            }
        }

        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(serviceOrderRepository, estimate.serviceOrderId());
        for (LineDecisionRequest decision : request.decisions()) {
            if (decision.decision() == EstimateLineDecision.APPROVED) {
                serviceOrder.authorizeExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            } else {
                serviceOrder.rejectExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            }
        }

        serviceOrderRepository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
```

`IllegalArgumentException` (duplicidade) é tratada pelo `GlobalExceptionHandler` genérico existente,
como qualquer outro `IllegalArgumentException` do projeto hoje — ver a ressalva sobre o código de erro
em "Tratamento de erros".

## Repository

Nenhuma mudança — `EstimateRepository.findById` e `ServiceOrderRepository.findById`/`save` já expõem
tudo que este fluxo precisa.

## Interfaces e fluxo de dados

`POST /api/estimates/{estimateId}/decisions`

Escolhido sob `/api/estimates` (não `/api/service-orders/{id}/estimates/{estimateId}/decisions`) por
consistência com as rotas já existentes de `EstimateController` (`GET /api/estimates/{estimateId}`,
já flat, sem aninhar sob `service-orders`).

Request (`DecideEstimateLinesRequest`, novo):
```json
{
  "decisions": [
    { "serviceExecutionId": "b6e6c6b0-...-...", "decision": "APPROVED" },
    { "serviceExecutionId": "3fa2c1de-...-...", "decision": "REJECTED" }
  ]
}
```

```java
public record DecideEstimateLinesRequest(
        @NotEmpty @Valid List<LineDecisionRequest> decisions) {

    public record LineDecisionRequest(
            @NotNull UUID serviceExecutionId,
            @NotNull EstimateLineDecision decision) {
    }
}

public enum EstimateLineDecision {
    APPROVED, REJECTED
}
```

Resposta de sucesso: `200 OK` com `ServiceOrderResponse` (do módulo `serviceorder`, reaproveitado sem
alteração), refletindo o novo status de cada `ServiceExecution` decidida — mesmo formato de retorno já
usado por `perform-diagnosis` e pelos endpoints de execução (`.../assign-technician`, `.../start`, etc.).
Retornar `ServiceOrderResponse` em vez de `EstimateResponse` é deliberado: a Estimate não tem estado de
decisão próprio (ver nota sobre AD-008 na `functional-spec.md`) — a `ServiceOrder` é a fonte de verdade
observável após a decisão.

```java
@PostMapping("/estimates/{estimateId}/decisions")
@Operation(summary = "Decide one or more estimate lines (approve or reject the underlying service execution)")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Decisions applied"),
        @ApiResponse(responseCode = "400", description = "Invalid, missing or duplicated decision fields"),
        @ApiResponse(responseCode = "404", description = "Estimate not found or service execution not part of it"),
        @ApiResponse(responseCode = "409", description = "A service execution is not pending")
})
public ResponseEntity<ServiceOrderResponse> decide(
        @PathVariable UUID estimateId, @Valid @RequestBody DecideEstimateLinesRequest request) {
    return ResponseEntity.ok(decideEstimateLinesUseCase.execute(estimateId, request));
}
```

O contrato deve ser refletido em OpenAPI (via `@Operation`/`@ApiResponses`, gerado automaticamente pelo
springdoc) e na collection Postman
(`docs/api/postman/workshop-management-system.postman_collection.json`) antes da conclusão da feature.

## Tratamento de erros

- `404 Not Found`, código `NOT_FOUND` — Estimate inexistente, ou `serviceExecutionId` que não pertence
  à Estimate (`NoSuchElementException`, mapeada pelo `GlobalExceptionHandler` já existente).
- `409 Conflict`, código `INVALID_STATE_TRANSITION` — alguma `ServiceExecution` da lista não está
  `PENDING` (`IllegalStateException`, `ServiceLifecycleExceptionHandler` já existente).
- `400 Bad Request`, código `VALIDATION_ERROR` — `decisions` vazio/ausente ou campos obrigatórios
  ausentes (Bean Validation, mesmo padrão de `PerformDiagnosisRequest`/`DiagnosisItemRequest`).
- `400 Bad Request`, código `INVALID_STOCK_ITEM` — `serviceExecutionId` duplicado na mesma requisição
  (`IllegalArgumentException`, tratada pelo handler genérico já existente em `GlobalExceptionHandler`).
  **Nota preexistente**: esse handler usa o código fixo `INVALID_STOCK_ITEM` para todo
  `IllegalArgumentException` da aplicação, não só para itens de estoque — nomenclatura enganosa herdada
  de código já existente, fora do escopo desta feature corrigir; documentado aqui para não gerar
  confusão ao revisar os testes de `400` desta feature.

## Persistência e dados de bootstrap

Nenhuma migration nova. Nenhuma tabela nova: a decisão é inteiramente refletida no status já persistido
de `ServiceExecution` dentro de `ServiceOrder` (mesmo mecanismo usado por `assign-technician`,
`start`, etc.). Nenhum dado é classificado como seed.

## Segurança e operação

- Nenhum dado sensível adicional exposto; a resposta já expõe o status de cada `ServiceExecution` hoje
  (via `perform-diagnosis` e demais endpoints de execução).
- Nenhuma regra de autorização nova (mesma ausência de autenticação do restante do projeto — ver
  `docs/features/servicelifecycle/service-order-creation/technical-spec.md` §"Segurança e operação"
  para a mesma observação já registrada). Em particular, esta feature não verifica que o chamador é de
  fato o Customer dono da Estimate — mesmo padrão de risco pré-existente, não introduzido aqui.
- IDs tratados como UUID; nenhuma concatenação manual de entrada em SQL.
- Superfície de abuso: qualquer chamador pode decidir qualquer linha de qualquer Estimate (sem
  autenticação), mesmo padrão de risco pré-existente de todos os outros endpoints de mutação do
  projeto — não é uma lacuna introduzida por esta feature.

## Estratégia de testes

### Application
`DecideEstimateLinesUseCaseTest`:
- fluxo válido com uma decisão `APPROVED` (execução vira `AUTHORIZED`/`READY`, `ServiceOrder` persistida
  e refletida na resposta);
- fluxo válido com uma decisão `REJECTED` (execução vira `REJECTED`);
- fluxo válido com múltiplas decisões mistas (`APPROVED` + `REJECTED`) em uma única chamada;
- Estimate inexistente propaga `NoSuchElementException`;
- `serviceExecutionId` que não pertence à Estimate propaga `NoSuchElementException`, e nenhuma decisão
  válida da mesma chamada é persistida (tudo-ou-nada);
- `serviceExecutionId` repetido na mesma chamada propaga `IllegalArgumentException`;
- `ServiceExecution` não `PENDING` propaga `IllegalStateException`, e nenhuma decisão válida da mesma
  chamada é persistida.

### Web
`EstimateControllerDecideLinesTest`:
- `200` com o `ServiceExecution` refletindo `AUTHORIZED`/`REJECTED` na resposta;
- `404` para Estimate inexistente;
- `404` para `serviceExecutionId` fora da Estimate;
- `409`/`INVALID_STATE_TRANSITION` para `ServiceExecution` já decidida (chamando o endpoint duas vezes
  para a mesma linha);
- `400`/`VALIDATION_ERROR` para `decisions` vazio/ausente;
- `400` para `serviceExecutionId` duplicado na mesma requisição.

Nenhuma mudança de teste necessária em `ServiceOrderTest`/`ServiceExecutionTest`: os métodos de domínio
já são cobertos (`authorizingExecutionWithoutPendingStockMovesItToReadyAndClosesTheDiagnosis`,
`awaitingPartTakesPrecedenceOverAwaitingApproval`, `cannotAssignTechnicianToARejectedExecution`, etc.).

## Fora de escopo técnico

- campo de status na `Estimate` (AD-008 continua em aberto);
- fechamento automático da Estimate quando todas as linhas forem decididas;
- expiração da Estimate (AD-013, RF14, feature separada e ainda não implementada);
- qualquer evento de domínio publicado pela decisão (não existe hoje para `authorizeExecutionFromEstimate`
  tampouco fora do fluxo de `estimate-generation`);
- notificação de qualquer ator sobre a decisão.

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

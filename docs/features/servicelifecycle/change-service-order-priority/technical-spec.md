# Especificação Técnica: Alterar Prioridade da Service Order

| Campo | Valor |
|---|---|
| Feature | `change-service-order-priority` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `docs/features/servicelifecycle/change-service-order-priority/functional-spec.md` |

## Objetivo técnico

Expor `ServiceOrder.definePriority(Priority)` — hoje um método de domínio sem nenhum caller de
produção — através de um caso de uso e um endpoint HTTP, com a restrição de status confirmada na
`functional-spec.md` (bloqueado quando `COMPLETED` ou `DELIVERED`).

## Contexto e fronteiras

A implementação pertence a `servicelifecycle.serviceorder`, reaproveitando o aggregate `ServiceOrder`
já existente. Nenhuma estrutura nova de persistência é necessária — `priority` já é uma coluna
mapeada em `ServiceOrderJpaEntity`/`service_orders`.

## Estrutura proposta

- `serviceorder/domain/model/ServiceOrder.java` — adicionar guarda de status em `definePriority`
  (alteração de método existente, não novo arquivo).
- `serviceorder/application/dto/ChangeServiceOrderPriorityRequest.java` (novo)
- `serviceorder/application/usecase/ChangeServiceOrderPriorityUseCase.java` (novo)
- `serviceorder/infrastructure/web/ServiceOrderController.java` — adicionar endpoint (alteração de
  arquivo existente).

## Domínio

`ServiceOrder.definePriority(Priority newPriority)` passa a validar o status atual antes de aplicar a
mudança:

```java
public void definePriority(Priority newPriority) {
    if (statusSnapshot == ServiceOrderStatus.COMPLETED || statusSnapshot == ServiceOrderStatus.DELIVERED) {
        throw new IllegalStateException(
                "Priority cannot be changed when the ServiceOrder is COMPLETED or DELIVERED");
    }
    this.priority = newPriority;
}
```

Segue o mesmo padrão já usado em `ServiceOrder.finalize(...)` e em `ServiceExecution` para guardas de
transição de estado: `IllegalStateException` capturada pelo `ServiceLifecycleExceptionHandler`
(`servicelifecycle/ServiceLifecycleExceptionHandler.java`), que já mapeia esse tipo para `409` com
código `INVALID_STATE_TRANSITION` — nenhuma mudança necessária no exception handler.

Nenhuma outra invariante do aggregate é afetada; `recomputeStatusSnapshot` não é chamado porque
prioridade não participa do cálculo do status derivado.

## Caso de uso: `ChangeServiceOrderPriorityUseCase`

Fluxo:

1. carregar `ServiceOrder` por `ServiceOrderRepository` (`ServiceOrderFinder.getOrThrow`, mesmo padrão
   dos demais use cases de `serviceorder`);
2. chamar `serviceOrder.definePriority(request.priority())`;
3. persistir via `ServiceOrderRepository.save(...)`;
4. retornar `ServiceOrderResponse` (via `ServiceOrderMapper.toResponse`, já existente — nenhuma mudança
   no DTO de resposta, `priority` já é um campo exposto).

```java
@Service
public class ChangeServiceOrderPriorityUseCase {

    private final ServiceOrderRepository repository;

    public ChangeServiceOrderPriorityUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, ChangeServiceOrderPriorityRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.definePriority(request.priority());
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
```

## Repository

Nenhuma mudança — `ServiceOrderRepository` já expõe `findById`/`save`, suficientes para este fluxo.

## Persistência

Nenhuma migration nova. `priority` já é uma coluna `VARCHAR` mapeada via `@Enumerated(EnumType.STRING)`
em `ServiceOrderJpaEntity`, gravada por `ServiceOrderPersistenceMapper` no `save()` já existente.

## API

`PATCH /api/service-orders/{id}/priority`

Escolhido `PATCH` (não `PUT`) por consistência com o outro endpoint de atualização parcial já existente
no controller (`PATCH /api/service-orders/{id}/executions/{executionId}/progress`).

Request (`ChangeServiceOrderPriorityRequest`):
```json
{
  "priority": "HIGH"
}
```

`priority` é `@NotNull`; o Jackson já rejeita valores fora do enum `Priority` com `400` via o handler
existente de `HttpMessageNotReadableException` (`GlobalExceptionHandler`, código `VALIDATION_ERROR`) —
mesmo comportamento hoje observado para outros campos de enum na API, nenhum tratamento novo
necessário.

Resposta de sucesso: `200 OK` com `ServiceOrderResponse` (mesmo formato de todo endpoint que muta a
Service Order).

O contrato deve ser refletido em OpenAPI (via `@Operation`/`@ApiResponses` no controller, gerado
automaticamente pelo springdoc) e na collection Postman
(`docs/api/postman/workshop-management-system.postman_collection.json`) antes da conclusão da feature.

## Tratamento de erros

- `404 Not Found`, código `NOT_FOUND` — Service Order inexistente (mesmo padrão de
  `ServiceOrderFinder.getOrThrow`, já usado pelos demais endpoints).
- `409 Conflict`, código `INVALID_STATE_TRANSITION` — Service Order em status `COMPLETED` ou
  `DELIVERED`.
- `400 Bad Request`, código `VALIDATION_ERROR` — `priority` ausente ou valor fora do enum.

## Segurança e operação

- Nenhum dado sensível adicional exposto; a resposta já expõe `priority` hoje.
- Nenhuma regra de autorização nova (mesma ausência de autenticação do restante do projeto — ver
  `docs/features/servicelifecycle/service-order-creation/technical-spec.md` §"Segurança e operação"
  para a mesma observação já registrada).
- IDs tratados como UUID; nenhuma concatenação manual de entrada em SQL.
- Superfície de abuso: qualquer chamador pode alterar a prioridade de qualquer Service Order (sem
  autenticação), mesmo padrão de risco pré-existente de todos os outros endpoints de mutação do
  projeto — não é uma lacuna introduzida por esta feature.

## Estratégia de testes

### Domínio
- `ServiceOrder.definePriority` altera a prioridade quando o status permite.
- `ServiceOrder.definePriority` lança `IllegalStateException` quando o status é `COMPLETED`.
- `ServiceOrder.definePriority` lança `IllegalStateException` quando o status é `DELIVERED`.

### Application
- `ChangeServiceOrderPriorityUseCaseTest`: fluxo válido (persiste e retorna a prioridade atualizada);
  Service Order inexistente propaga `NoSuchElementException`.

### Web
- `ServiceOrderControllerChangePriorityTest`: `200` com a prioridade refletida na resposta; `404` para
  Service Order inexistente; `409`/`INVALID_STATE_TRANSITION` para Service Order `COMPLETED` (usando o
  mesmo padrão de setup direto via `ServiceOrderRepository` já usado em
  `ServiceOrderControllerAssignTechnicianTest` para chegar a estados que ainda não têm um fluxo HTTP
  completo de autorização); `400`/`VALIDATION_ERROR` para `priority` ausente ou inválida.

## Fora de escopo técnico

- notificação de qualquer ator sobre a mudança;
- histórico/auditoria de mudanças de prioridade;
- qualquer efeito automático da prioridade sobre fila de trabalho, SLA ou atribuição de Technician.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada.
- [x] Technical Spec revisada e aprovada.

Antes do PR:

- [x] testes unitários passando;
- [x] testes de integração aplicáveis passando;
- [x] `make verify` passando;
- [x] nenhuma migration nova necessária;
- [x] OpenAPI atualizado;
- [x] Postman atualizado;
- [x] nenhuma fronteira do Spring Modulith violada.

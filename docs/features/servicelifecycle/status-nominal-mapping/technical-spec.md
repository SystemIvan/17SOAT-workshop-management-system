# Especificação Técnica: Mapeamento do status da OS para os 6 estados nominais da Fase 2

| Campo | Valor |
|---|---|
| Feature | `status-nominal-mapping` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-21 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-21 |
| Especificação funcional | `docs/features/servicelifecycle/status-nominal-mapping/functional-spec.md` (`Approved` em 2026-09-21) |

## Objetivo técnico

`GET /api/service-orders/{id}/status` e `GET /api/service-orders` continuam com o mesmo contrato já
aprovado (mesmos query params, mesmos campos existentes) e ganham, de forma aditiva, um novo campo com a
representação nominal de 6 estados definida na `functional-spec.md`. A fonte da projeção continua sendo
`ServiceOrder.status()` (o `statusSnapshot` já calculado por `service-order-status-projection`) — esta
feature não recalcula nem duplica essa lógica, só adiciona uma segunda tradução de apresentação sobre o
mesmo valor.

## Contexto e desenho

Implementação inteira em `servicelifecycle.serviceorder`, camada `application/dto`. Nenhuma importação de
pacote interno de outro módulo; nenhuma mudança de contrato de evento de domínio; nenhuma mudança em
`domain/model`.

### Decisão de design: mapeamento vive em `application/dto`, não em `domain/model`

Ao contrário de `listingRank()` (`list-service-orders-sorting`), que é uma regra de negócio genuína
(prioridade operacional usada para ordenar), os 6 nomes nominais são vocabulário do enunciado da Fase 2
para o **contrato externo** — não uma regra que o domínio precisa conhecer ou usar internamente para
decidir comportamento. Colocar esse mapeamento em `domain/model` acoplaria o agregado a um vocabulário de
apresentação que só existe para o consumidor HTTP externo, violando "Keep the domain model free from
Spring, JPA and transport concerns" (`AGENTS.md`) em espírito (não é Spring/JPA, mas é forma de
apresentação HTTP). Por isso o mapeamento é um novo tipo em `application/dto`, ao lado de
`ServiceOrderMapper`, que já é a camada responsável por essa tradução (domínio → DTO de resposta).

### Decisão de design: novo enum `ServiceOrderStatusLabel`, não `String`

Um `String` solto (`"Execução"`, `"Recebida"`, ...) não aparece no schema OpenAPI como um conjunto fechado
de valores e não impede, em tempo de compilação, um typo ou um valor fora dos 6 nomes aprovados. Um novo
enum `ServiceOrderStatusLabel` com exatamente os 6 valores nominais:

- aparece no OpenAPI gerado (`/v3/api-docs`) como um `enum` fechado de 6 strings, documentando o contrato
  automaticamente sem anotação manual adicional;
- garante, via `switch` exaustivo sem `default`, que todo `ServiceOrderStatus` (os 7 valores) tem
  mapeamento — se um novo valor for adicionado a `ServiceOrderStatus` no futuro sem atualizar o mapeamento,
  a compilação quebra (mesmo raciocínio de "fail loud" já usado no `switch` de `listingRank()`).

Os nomes dos valores do enum são a tradução literal dos 6 nomes do enunciado (`RECEBIDA`, `DIAGNOSTICO`,
`AGUARDANDO_APROVACAO`, `EXECUCAO`, `FINALIZADA`, `ENTREGUE`), sem acento (Jackson serializa `Enum.name()`
por padrão; acentos em nome de constante Java não são idiomáticos e forçariam `@JsonProperty` por valor,
sem ganho). Isso é uma exceção deliberada e já registrada na `functional-spec.md` (Regra de negócio 1) à
convenção geral de `AGENTS.md` de manter identificadores em inglês: aqui a tradução para os nomes exatos do
enunciado da Fase 2 **é** o requisito, não um detalhe de implementação.

## Estrutura proposta

- `serviceorder/application/dto/ServiceOrderStatusLabel.java` (novo) — enum com os 6 valores e o método
  estático `from(ServiceOrderStatus)`.
- `serviceorder/application/dto/ServiceOrderResponse.java` — adicionar campo `statusLabel` (alteração).
- `serviceorder/application/dto/ServiceOrderStatusResponse.java` — adicionar campo `statusLabel`
  (alteração).
- `serviceorder/application/dto/ServiceOrderMapper.java` — `toResponse`/`toStatusResponse` calculam
  `statusLabel` via `ServiceOrderStatusLabel.from(status)` (alteração).
- `src/test/java/.../application/dto/ServiceOrderStatusLabelTest.java` (novo) — mapeamento total dos 7
  valores.
- `src/test/java/.../application/dto/ServiceOrderMapperTest.java` (alteração, se existir; senão os testes
  HTTP abaixo cobrem via `ServiceOrderControllerGetTest`/equivalente).
- `src/test/java/.../infrastructure/web/*` — testes HTTP existentes de `GET /{id}/status` e `GET
  /service-orders` ganham asserções do novo campo (alteração, não teste novo).
- `docs/api/postman/workshop-management-system.postman_collection.json` — adicionar assert de
  `statusLabel` nas requests já existentes de status/listagem (alteração).
- `README.md` (raiz do projeto) — linha do roteiro manual que hoje só verifica `status` `RECEIVED` no `GET
  .../status` passa a mencionar também `statusLabel` `"RECEBIDA"` (alteração pontual).
- Nenhuma migration: nada persistido muda.

## Domínio

Nenhuma mudança em `domain/model`. `ServiceOrder.status()` e `ServiceOrderStatus` permanecem exatamente
como estão hoje (incluindo `listingRank()` de `list-service-orders-sorting`).

## `ServiceOrderStatusLabel` — novo enum e mapeamento

```java
package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;

public enum ServiceOrderStatusLabel {
    RECEBIDA,
    DIAGNOSTICO,
    AGUARDANDO_APROVACAO,
    EXECUCAO,
    FINALIZADA,
    ENTREGUE;

    public static ServiceOrderStatusLabel from(ServiceOrderStatus status) {
        return switch (status) {
            case RECEIVED -> RECEBIDA;
            case IN_DIAGNOSIS -> DIAGNOSTICO;
            case AWAITING_APPROVAL -> AGUARDANDO_APROVACAO;
            case IN_PROGRESS, AWAITING_ITEMS -> EXECUCAO;
            case COMPLETED -> FINALIZADA;
            case DELIVERED -> ENTREGUE;
        };
    }
}
```

`switch` exaustivo sobre os 7 valores de `ServiceOrderStatus`, sem `default` — cobre a regra de negócio 3
da `functional-spec.md` ("mapeamento é total e determinístico") em tempo de compilação.

## DTOs — campo `statusLabel` aditivo

```java
public record ServiceOrderStatusResponse(
        UUID id,
        ServiceOrderStatus status,
        ServiceOrderStatusLabel statusLabel) {
}
```

```java
public record ServiceOrderResponse(
        UUID id,
        UUID customerId,
        UUID vehicleId,
        VehicleSnapshotResponse vehicleSnapshot,
        Priority priority,
        @Schema(nullable = true) String initialAssessment,
        @Schema(nullable = true) UUID diagnosisAssigneeId,
        @Schema(deprecated = true) ServiceOrderStatus status,
        ServiceOrderStatus statusSnapshot,
        ServiceOrderStatusLabel statusLabel,
        Set<UUID> approvedEstimateIds,
        List<ServiceExecutionResponse> executions) {
}
```

`statusLabel` é adicionado ao final da lista de campos de status existentes, depois de `statusSnapshot` —
mantém `status`/`statusSnapshot` intactos (posição, tipo, nome), só acrescenta um novo campo, que é
aditivo e não quebra clientes existentes que ignoram campos desconhecidos.

## `ServiceOrderMapper` — cálculo do novo campo

```java
public static ServiceOrderResponse toResponse(ServiceOrder serviceOrder) {
    var status = serviceOrder.status();
    return new ServiceOrderResponse(
            serviceOrder.id(),
            serviceOrder.customerId(),
            serviceOrder.vehicleId(),
            toVehicleSnapshotResponse(serviceOrder.vehicleSnapshot()),
            serviceOrder.priority(),
            serviceOrder.initialAssessment(),
            serviceOrder.diagnosisAssigneeId(),
            status,
            status,
            ServiceOrderStatusLabel.from(status),
            serviceOrder.approvedEstimateIds(),
            serviceOrder.serviceExecutions().stream().map(ServiceOrderMapper::toExecutionResponse).toList());
}

public static ServiceOrderStatusResponse toStatusResponse(ServiceOrder serviceOrder) {
    var status = serviceOrder.status();
    return new ServiceOrderStatusResponse(serviceOrder.id(), status, ServiceOrderStatusLabel.from(status));
}
```

## Interfaces e fluxo de dados

- `GET /api/service-orders/{id}/status`: mesmo request (sem mudança de path/params); resposta ganha
  `statusLabel` além de `id`/`status` já existentes.
- `GET /api/service-orders`: mesmo request (mesmos query params); cada item do array de resposta ganha
  `statusLabel` além dos campos já existentes.
- `GET /api/service-orders/{id}`: mesmo `ServiceOrderResponse`, então também ganha `statusLabel`
  automaticamente (não é um endpoint separado a implementar; é o mesmo record).
- Nenhum outro endpoint muda — comandos (`create`, `diagnosis`, `assign-technician`, `finalize`, etc.) não
  retornam status nominal e não são alterados por esta feature.
- `ListServiceOrdersUseCase`, `GetServiceOrderUseCase`, `GetServiceOrderStatusUseCase`: nenhuma mudança de
  assinatura ou lógica própria — continuam chamando `ServiceOrderMapper`, que passa a computar o campo
  extra internamente.

## Tratamento de erros

Nenhuma mudança: os mesmos casos de erro (`404` para OS inexistente) continuam valendo, sem alteração no
`GlobalExceptionHandler`. Não há novo caminho de falha — o mapeamento é uma função total sobre um enum já
validado pelo domínio (não há como `ServiceOrder.status()` retornar um valor fora de `ServiceOrderStatus`).

## Persistência e dados de bootstrap

Nenhuma. Classificação conforme `AGENTS.md` §"Persistent data and seeds": **nenhum seed necessário** — é
mapeamento de apresentação calculado em memória a partir de um valor já persistido (`status_snapshot`),
sem nova coluna, tabela ou migration.

## Segurança e operação

- Nenhuma mudança de autorização: mesmas regras já existentes em `SecurityConfig` para
  `/api/service-orders/**`.
- Nenhum dado sensível novo exposto: `statusLabel` é derivado determinístico de um campo já público
  (`status`/`statusSnapshot`); não introduz nenhuma informação que o consumidor já autorizado não tivesse
  acesso.
- Rollout: mudança aditiva de payload, sem migration, sem downtime, sem flag — o deploy padrão do projeto
  é suficiente. Clientes existentes que fazem parsing estrito de schema (rejeitam campos desconhecidos)
  seriam o único risco de compatibilidade; nenhum cliente conhecido do projeto faz isso (contrato
  documentado orienta ignorar campos desconhecidos, prática padrão REST).
- Recuperação: não aplicável — não há estado mutável novo, só uma função pura sobre dado já existente.
- Nenhuma superfície de abuso nova: é um campo a mais em respostas de leitura já existentes.

## Estratégia de testes

### Aplicação (novo — `ServiceOrderStatusLabelTest`)

- `from(status)` retorna o valor nominal esperado para cada um dos 7 valores de `ServiceOrderStatus`
  (teste parametrizado cobrindo a tabela da `functional-spec.md`), incluindo explicitamente que
  `IN_PROGRESS` e `AWAITING_ITEMS` produzem o mesmo `EXECUCAO` (decisão (a) registrada).

### Web (alteração — testes HTTP já existentes do endpoint de status e da listagem)

- `GET /api/service-orders/{id}/status` para uma OS `RECEIVED` retorna `status` `"RECEIVED"` e
  `statusLabel` `"RECEBIDA"` (mesmo padrão dos demais 6 mapeamentos, cobrindo ao menos um caso de
  `AWAITING_ITEMS` → `"EXECUCAO"` explicitamente, já que é o caso não óbvio da feature).
- `GET /api/service-orders` (reaproveitando o setup de `ServiceOrderControllerListTest` de
  `list-service-orders-sorting`, que já popula OS em todos os 7 status): cada item da resposta traz
  `statusLabel` coerente com seu `status`.
- `GET /api/service-orders/{id}`: `statusLabel` presente e coerente (mesmo `ServiceOrderResponse`).
- Regressão: `status` e `statusSnapshot` continuam presentes e com os mesmos valores de antes em todas as
  respostas acima.

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova entre módulos, nenhum novo pacote
  fora de `application/dto`.

## Contratos e documentação

- **OpenAPI**: gerado automaticamente a partir do novo campo nos records e do novo enum — nenhuma
  anotação manual obrigatória além do que já existe. `OpenApiContractTest` continua verde sem alteração
  (só verifica existência de paths, não schema de campo).
- **Postman**: `docs/api/postman/workshop-management-system.postman_collection.json` — adicionar, nos
  scripts `test` das requests já existentes "Get service order status", "List service orders" (e as
  variações filtradas) e "Get service order by ID" (se existir como request própria), uma asserção
  `pm.test('Response has statusLabel', ...)` confirmando a presença do campo.
- **README.md** (raiz, seção "Teste manual do fluxo principal pelo Postman"): a linha que hoje instrui
  conferir `GET {{baseUrl}}/api/service-orders/{{serviceOrderId}}/status` esperando `RECEIVED` passa a
  também mencionar `statusLabel` `"RECEBIDA"` na mesma resposta.

## Fora de escopo técnico

- Qualquer mudança em `domain/model` ou na lógica de `service-order-status-projection`.
- Qualquer mudança no ranking de listagem (`listingRank()`, `list-service-orders-sorting`) — os dois
  mapeamentos (ordenação interna e nome nominal externo) coexistem, são independentes.
- Remover, renomear ou depreciar `status`/`statusSnapshot` — confirmado fora de escopo na
  `functional-spec.md`.
- Internacionalização (`i18n`) genérica de qualquer outro texto da API.
- Expor `statusLabel` em qualquer endpoint de comando (`POST`/`PATCH`) — só nas leituras já listadas.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada (2026-09-21).
- [x] Technical Spec revisada e aprovada (2026-09-21).

Antes do PR:

- [ ] testes unitários passando (`ServiceOrderStatusLabelTest`);
- [ ] testes de integração HTTP passando (endpoints de status, listagem e busca por ID);
- [ ] `make verify` passando;
- [ ] nenhuma migration nova necessária (confirmado acima);
- [ ] OpenAPI, Postman e README atualizados para refletir `statusLabel`;
- [ ] nenhuma fronteira do Spring Modulith violada (`ModuleStructureTest` verde);
- [ ] revisão de segurança registrada (ver seção "Segurança e operação" acima).

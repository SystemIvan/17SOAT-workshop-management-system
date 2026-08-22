# Especificação Técnica: Rastrear progresso da execução

| Campo | Valor |
|---|---|
| Feature | `track-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-20) |

## Revisão proposta por `stock-item-reservation`

Esta revisão altera o contrato de leitura: `AWAITING_PART` é substituído por `AWAITING_ITEMS` em
`ServiceExecutionStatus`, `ServiceOrderStatus`, precedência do snapshot e OpenAPI. O response detalhado
de Service Order ganha o campo anulável e aditivo `stockReservationId` por Service Execution. O resumo
`GET /{id}/status` permanece limitado a `id` e `status`.

O tracking não replica linhas, estado, timestamps ou preços da reserva: essas informações continuam em
Stock & Procurement e são lidas pelos endpoints próprios de `StockReservation`. A migration e o mapeamento
JPA dos novos campos pertencem ao plano `stock-item-reservation`; os testes HTTP revisados devem confirmar
o novo enum e o campo aditivo sem expor dados comerciais ou do Customer.

## Gate de aprovação

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação/teste pode começar antes da
aprovação humana explícita desta especificação.

## Objetivo técnico e escopo

O comportamento de RF23 já está implementado por dois endpoints somente-leitura
(`GetServiceOrderStatusUseCase` → `GET /{id}/status`; `GetServiceOrderUseCase` → `GET /{id}`),
escritos antes do gate de SDD do projeto. Assim como RF20/RF21/RF22, esta feature **não** propõe
nenhuma mudança de comportamento, contrato HTTP ou tratamento de erro — tudo o que RF23 precisa já
existe e já está correto:

- `GetServiceOrderStatusUseCase.execute` já usa `ServiceOrderFinder.getOrThrow` (mesmo helper de
  RF19–RF22) e devolve `ServiceOrderStatusResponse` (`id` + `status` derivado);
- `GetServiceOrderUseCase.execute` já devolve o `ServiceOrderResponse` completo, incluindo
  `executions[].status` de cada `ServiceExecution`;
- `ServiceOrderFinder.getOrThrow` já mapeia `ServiceOrder` inexistente para `404 NOT_FOUND` — nenhum
  handler novo é necessário;
- a precedência de `statusSnapshot` (`recomputeStatusSnapshot`) já é recalculada por todos os
  comandos de RF19–RF22 e apenas lida aqui — já coberta por `ServiceOrderTest`.

Esta especificação cobre exclusivamente a lacuna real: **não existe `GetServiceOrderStatusUseCaseTest`
nem `GetServiceOrderUseCaseTest`, nem teste HTTP** para nenhum dos dois endpoints. O que falta:

- teste de caso de uso para os dois use cases de leitura — não existem;
- teste HTTP para os dois endpoints (`GET /{id}/status`, `GET /{id}`) — não existem;
- documentação Swagger (`@ApiResponses`) nos dois endpoints — `ServiceOrderController.getStatus` e
  `.get` hoje só têm `@Operation`, diferente de `assignTechnician`/`startExecution`/
  `updateExecutionProgress`/`completeExecution`.

Esta feature não implementará:

- autenticação/autorização de quem pode consultar (Customer restrito à própria `ServiceOrder`) —
  depende de AD-016, `Team Decision Required`, e é a lacuna já registrada em `Architecture.md` §11;
- agrupamento das execuções por Estimate no contrato de resposta — mudança de comportamento, fora do
  escopo de fechamento de lacuna de cobertura;
- qualquer mecanismo de polling com cache, SSE ou WebSocket — depende de AD-015 (`Team Decision
  Required`) e do `ADR-001-realtime-updates-strategy.md`;
- qualquer mudança na precedência de `recomputeStatusSnapshot` — já implementada e já coberta por
  teste de domínio;
- RF24 (`FinalizeServiceOrderUseCase`) — feature separada.

## Contexto e desenho

Nenhuma mudança de bounded context, módulo ou aggregate. Os dois use cases já são
`@Transactional(readOnly = true)` e dependem só de `ServiceOrderRepository` (mesmo padrão dos demais
use cases do épico). Nenhum pacote de `registration` ou `stockprocurement` é tocado.
`ModuleStructureTest` deve continuar verde sem exceções adicionais.

## Interfaces e fluxo de dados

Endpoints já existem e não mudam de path, verbo ou payload:

```
GET /api/service-orders/{id}/status
Sucesso: 200 OK com ServiceOrderStatusResponse { id, status }

GET /api/service-orders/{id}
Sucesso: 200 OK com ServiceOrderResponse completo (inclui executions[].status por ServiceExecution)
```

Contratos de erro (já implementados, sem mudança):

| Situação | Comportamento atual |
|---|---|
| `ServiceOrder` inexistente | `404` `NOT_FOUND` (`ServiceOrderFinder.getOrThrow`) |

Não há corpo de requisição em nenhum dos dois endpoints, logo não há validação `@NotBlank`/`400`
aplicável.

Único gap de interface, não funcional: nenhum dos dois endpoints tem anotações `@ApiResponses` no
Swagger. Esta feature adiciona as anotações refletindo a tabela acima, mesmo padrão já usado nos
outros métodos de `ServiceOrderController`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema e nenhuma migration nova é necessária. Nenhum dado novo é criado; os testes
reutilizam os mesmos builders/fixtures de `ServiceOrderTest`/`CompleteExecutionUseCaseTest`/
`ServiceOrderControllerCompleteExecutionTest`, variando o status da `ServiceOrder`/`ServiceExecution`
conforme o cenário (recém-criada, diagnosticada, autorizada, em progresso, concluída).

## Segurança e operação

- Sem mudança de autorização: os dois endpoints continuam públicos, mesma limitação conhecida de todo
  `servicelifecycle`, dependente de AD-016 — explicitamente registrada como lacuna também em
  `Architecture.md` §11 ("Falta ... regra de acesso ao próprio Customer").
- Nenhum dado pessoal ou segredo é manipulado; os IDs são `UUID` opacos. Não há corpo de requisição em
  nenhum dos dois endpoints.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje não existe nenhuma cobertura de aplicação ou HTTP para os dois endpoints de leitura. Esta feature
adiciona:

### Domínio (`ServiceOrder.status()`/`recomputeStatusSnapshot`)

Já coberto — nenhum teste novo necessário nesta camada: `ServiceOrderTest` já exercita o efeito de
cada comando (diagnóstico, autorização, início, progresso, conclusão, entrega) sobre o
`statusSnapshot` e sua precedência.

### Aplicação (novo — `GetServiceOrderStatusUseCaseTest`, `GetServiceOrderUseCaseTest`)

Testes com repository fake cobrindo, no mesmo estilo de `CompleteExecutionUseCaseTest`:

- `GetServiceOrderStatusUseCase`: retorna `id` + `status` corretos para uma `ServiceOrder` existente
  (em pelo menos dois status distintos, ex.: recém-criada `RECEIVED` e uma diagnosticada
  `IN_DIAGNOSIS`/`AWAITING_APPROVAL`); `NoSuchElementException` quando a `ServiceOrder` não existe.
- `GetServiceOrderUseCase`: retorna o agregado completo com `executions[].status` correto refletindo o
  estado de cada `ServiceExecution`; `NoSuchElementException` quando a `ServiceOrder` não existe.

### HTTP (novo — `ServiceOrderControllerGetStatusTest`, cobrindo os dois endpoints de leitura)

Testes MockMvc cobrindo, no mesmo estilo de `ServiceOrderControllerCompleteExecutionTest`:

- `GET /{id}/status`: `200 OK` com `status` correto para uma `ServiceOrder` existente;
  `404 NOT_FOUND` para uma `ServiceOrder` inexistente.
- `GET /{id}`: `200 OK` com o payload completo, incluindo `executions[].status`, para uma
  `ServiceOrder` existente; `404 NOT_FOUND` para uma `ServiceOrder` inexistente.

Os dois grupos de cenários HTTP podem viver na mesma classe de teste (`ServiceOrderControllerGetStatusTest`),
já que ambos endpoints são somente-leitura e compartilham a mesma fixture de `ServiceOrder`.

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova é introduzida.

## Decisões propostas para aprovação técnica

- [ ] Nenhuma mudança de comportamento, contrato HTTP ou tratamento de erro é feita nesta feature —
      escopo é 100% cobertura de teste (aplicação + HTTP) + anotações Swagger nos dois endpoints de
      leitura já existentes.
- [ ] Nenhum teste de domínio novo é obrigatório nesta feature (cobertura já existe via
      `ServiceOrderTest`).
- [ ] Os dois endpoints de leitura (`GET /{id}/status`, `GET /{id}`) são cobertos em uma única classe
      de teste HTTP (`ServiceOrderControllerGetStatusTest`), já que são ambos somente-leitura sobre o
      mesmo agregado.
- [ ] Nenhuma migration nova é necessária.

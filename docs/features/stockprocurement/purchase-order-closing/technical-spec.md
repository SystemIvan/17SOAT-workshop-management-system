# Especificação Técnica: Fechamento de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-closing` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta especificação deriva da funcional aprovada. Nenhum `implementation-plan.md`, código, migration ou contrato HTTP
desta feature pode ser criado antes da aprovação humana explícita deste documento.

RF28 e RF29 serão implementadas na mesma frente, mas cada especificação mantém seu próprio gate e seus checkpoints. O
fechamento descrito aqui não altera saldo e não depende da conclusão do recebimento.

## Objetivo técnico

Evoluir o aggregate `PurchaseOrder` entregue por RF27 para:

- adicionar a transição de negócio `OPEN → CLOSED`;
- registrar `closedAt` e `closedByUserAccountId` uma única vez;
- preservar linhas, demandas e referência externa imutáveis;
- permitir retry e concorrência sem duas transições observáveis;
- consultar e listar ordens confirmadas em `OPEN` ou `CLOSED`;
- preparar a composição de leitura que RF29 usará para indicar recebimento pendente ou concluído.

Não haverá chamada ao External Supplier System, alteração de Stock Item, Stock Receipt, movimentação ou retry de
Service Execution em RF28.

## Diagnóstico do baseline

O código atual já contém:

- `PurchaseOrder` com estados internos `PENDING_SUBMISSION`, `OPEN` e `REJECTED`;
- método de domínio para abrir e rejeitar, com timestamps monotônicos e idempotência local;
- linhas e IDs de demandas imutáveis dentro do aggregate;
- `PurchaseOrderRepository.findByIdForUpdate` com lock pessimista no adapter JPA;
- `GET /api/purchase-orders/{purchaseOrderId}`, restrito hoje a ordem `OPEN`;
- resposta pública que força o status `OPEN` por meio de `OpenPurchaseOrderStatus`;
- autorização de `/api/purchase-orders/**` para `MANAGER` e `ADMIN`;
- principal do Spring Security preenchido com o UUID de `UserAccount` extraído do subject do JWT;
- migration `V20260825024334__create_purchase_orders.sql` com checks que ainda não aceitam `CLOSED`.

Não existe listagem de Purchase Orders, autoria operacional, fechamento ou estado de recebimento.

## Contexto e fronteiras

### Stock & Procurement

Todo o delta de RF28 permanece em `stockprocurement.purchaseorder`. `PurchaseOrder` continua aggregate root
independente; não será criada entidade `Stock`, `Supplier` ou `Delivery`.

O controller extrai o UUID autenticado e o passa como valor opaco ao caso de uso. Domínio e persistência de Purchase
Order não importam tipos internos de `identity` e não possuem foreign key para `user_accounts`.

### Outros módulos e sistemas

RF28 não importa nem chama Service Lifecycle. Não há referência direta a Service Order ou Service Execution.

O External Supplier System não participa do fechamento. A referência externa já confirmada é somente exibida para
conferência e permanece inalterada.

### Spring Modulith

Não será criado módulo, named interface ou evento cross-module. `ModuleStructureTest` deve continuar encontrando apenas
`identity`, `registration`, `servicelifecycle` e `stockprocurement`.

## Modelo de domínio

### Alteração em `PurchaseOrderStatus`

Adicionar `CLOSED` aos valores internos existentes:

```text
PENDING_SUBMISSION | OPEN | REJECTED | CLOSED
```

`PENDING_SUBMISSION` e `REJECTED` continuam estados técnicos não retornados nas consultas de ordens confirmadas.
`OPEN` e `CLOSED` são os estados públicos de negócio.

### Alterações em `PurchaseOrder`

Adicionar:

| Atributo | Tipo | Regra |
|---|---|---|
| `closedAt` | `Instant?` | Presente somente em `CLOSED`; UTC e truncado em microssegundos |
| `closedByUserAccountId` | `UUID?` | Presente somente em `CLOSED`; identidade autenticada opaca |

O factory de reconstituição, construtor, mapper e validação de estado passam a aceitar os novos campos. Para `CLOSED`:

- `externalReference` e `openedAt` continuam obrigatórios;
- `supplierRejectionCode` continua nulo;
- `closedAt` e `closedByUserAccountId` são obrigatórios;
- `closedAt` não pode anteceder `openedAt` ou `updatedAt` anterior;
- `updatedAt` passa a ser igual ao instante do primeiro fechamento;
- linhas, demandas, ID, chave idempotente, hash, criação e abertura permanecem inalterados.

Novo comportamento:

```java
public void close(UUID closedByUserAccountId, Instant closedAt)
```

Regras:

- em `OPEN`, valida autoria/instante e muda para `CLOSED`;
- em `CLOSED`, retorna sem efeito e preserva os dados do primeiro fechamento;
- em qualquer outro estado, lança `PurchaseOrderTransitionException`;
- o domínio não recebe linhas, quantidades, referência externa ou status no comando.

Não será criado um aggregate `PurchaseOrderClosing`; a transição pertence à própria Purchase Order.

## Casos de uso e repositories

### `ClosePurchaseOrderUseCase`

Método público `@Transactional`:

1. valida `purchaseOrderId` e `userAccountId` não nulos;
2. carrega a ordem com `findByIdForUpdate`;
3. traduz ausência para `PurchaseOrderNotFoundException`;
4. chama `purchaseOrder.close(userAccountId, now)`;
5. persiste a ordem;
6. retorna `PurchaseOrderResponse`.

O `Clock` é injetável e usa UTC em produção. Repetição sobre `CLOSED` retorna `200` com a representação existente.

O lock serializa duas primeiras confirmações. Mesmo que ambas tenham principals diferentes, somente a vencedora em
`OPEN` define a autoria; a segunda observa `CLOSED` e não sobrescreve os dados.

### Consulta individual

`GetPurchaseOrderUseCase` passa a considerar encontráveis os estados `OPEN` e `CLOSED`. Ordens
`PENDING_SUBMISSION`/`REJECTED` continuam fora do contrato confirmado e resultam em `PURCHASE_ORDER_NOT_FOUND`.

### Listagem operacional

Adicionar `PurchaseOrderRepository.search(PurchaseOrderSearchCriteria)` e `SearchPurchaseOrdersUseCase` com transação
`readOnly = true`.

`PurchaseOrderSearchCriteria` contém um conjunto opcional de estados públicos. Sem filtro, retorna `OPEN` e `CLOSED`.
O repository nunca retorna `PENDING_SUBMISSION` ou `REJECTED` por essa consulta. O resultado é ordenado por
`updatedAt DESC, id ASC`, sem paginação no MVP.

RF29 adicionará um filtro de recebimento à mesma operação sem duplicar o endpoint ou criar outro read model.

## Contratos HTTP e OpenAPI

### Fechar a ordem

```http
POST /api/purchase-orders/{purchaseOrderId}/close
Authorization: Bearer <jwt>
```

Sem body e sem `Idempotency-Key`: a identidade idempotente é o próprio recurso e sua transição terminal.

Respostas:

| Situação | HTTP | Código estável |
|---|---:|---|
| Primeiro fechamento | `200` | — |
| Repetição de ordem já `CLOSED` | `200` | — |
| Ordem inexistente ou não confirmada | `404` | `PURCHASE_ORDER_NOT_FOUND` |
| Estado confirmado incompatível | `409` | `PURCHASE_ORDER_NOT_CLOSABLE` |
| Sem token | `401` | `UNAUTHORIZED` |
| Papel sem permissão | `403` | `FORBIDDEN` |

Embora `REJECTED` e `PENDING_SUBMISSION` existam internamente, o endpoint não revela sua existência e retorna o mesmo
not found usado pela consulta confirmada. `PURCHASE_ORDER_NOT_CLOSABLE` fica reservado para uma futura situação pública
confirmada que não permita fechamento; `CLOSED` não é conflito porque a repetição é idempotente.

### Consultar a ordem

```http
GET /api/purchase-orders/{purchaseOrderId}
```

Continua retornando `200` para `OPEN` e passa a retornar `200` para `CLOSED`. Não há mudança de path nem remoção de
campos.

### Listar ordens

```http
GET /api/purchase-orders?status=OPEN&status=CLOSED
```

- `status` é repetível e aceita somente `OPEN` e `CLOSED`;
- ausência do parâmetro retorna ambos;
- filtro válido sem resultados retorna `200 []`;
- enum inválido retorna `400 VALIDATION_ERROR`;
- o payload de cada item usa o mesmo `PurchaseOrderResponse` da consulta individual.

### Evolução de `PurchaseOrderResponse`

Substituir o tipo Java `OpenPurchaseOrderStatus` por um enum público com `OPEN` e `CLOSED`, sem alterar o nome JSON do
campo `status`.

Adicionar campos nullable:

```text
closedAt
closedByUserAccountId
```

Para respostas `OPEN`, ambos são `null`; para `CLOSED`, ambos são obrigatórios. A adição é compatível para consumidores
que toleram novos campos. RF29 adicionará `receiptId` e `receivedAt` na mesma representação.

Todos os endpoints terão `@Operation`, `@ApiResponses` e schemas coerentes. A coleção Postman e o fluxo manual no
`README.md` serão atualizados no checkpoint de implementação porque o contrato HTTP mudou.

## Persistência e migration

Classificação: **nenhum seed necessário**.

Uma nova migration Flyway, com timestamp UTC gerado na implementação e nome lowercase `snake_case`, deverá:

1. adicionar `closed_at TIMESTAMP(6) NULL` a `purchase_orders`;
2. adicionar `closed_by_user_account_id BINARY(16) NULL` sem foreign key cross-module;
3. substituir `ck_purchase_orders_status` para aceitar `CLOSED`;
4. substituir `ck_purchase_orders_state` para exigir os novos campos somente em `CLOSED`;
5. adicionar índice `idx_purchase_orders_status_updated` em `(status, updated_at, id)`.

Não existe backfill: nenhuma ordem atual está `CLOSED`, e as novas colunas permanecem nulas para todos os estados
existentes. A migration anterior é imutável e não será editada.

O JPA continua com `ddl-auto=validate`. Testes de startup deverão provar compatibilidade em H2/MySQL mode; a evidência
manual do plano deverá subir o MySQL do Docker Compose.

## Concorrência e atomicidade

- fechamento adquire lock da Purchase Order antes de validar a transição;
- duas chamadas concorrentes convergem para o mesmo `closedAt` e responsável;
- consulta não adquire lock de escrita;
- fechamento não adquire locks de Stock Item ou Service Order;
- nenhuma chamada externa ocorre dentro da transação;
- falha antes do commit preserva `OPEN` integralmente;
- falha de RF29 posterior não reabre a ordem.

## Tratamento de erros

`PurchaseOrderExceptionHandler` será ampliado para mapear a transição pública sem expor mensagem interna. Bean
Validation e conversão de path/query continuam usando `VALIDATION_ERROR`.

Não serão retornados stack trace, status técnico, SQL, payload do fornecedor ou classe de exception. Falhas técnicas
inesperadas continuam no tratamento padrão da plataforma e são logadas sem tokens ou dados pessoais.

## Segurança e operação

### Revisão de segurança preliminar

| Item | Avaliação técnica |
|---|---|
| Autenticação | JWT obrigatório; principal UUID deriva do token validado |
| Autorização | Match existente de `/api/purchase-orders/**` restringe a `MANAGER`/`ADMIN` |
| Mass assignment | Endpoint sem body; estado, autoria, instante, linhas e quantidades não são aceitos |
| Exposição | Resposta contém somente dados operacionais de compra e UUID da conta responsável |
| Dados pessoais | Nenhum Customer, Vehicle, Technician ou e-mail é consultado/exposto |
| Concorrência | Lock pessimista e transição idempotente impedem autoria duplicada |
| Logs | Não registrar JWT, payload externo ou resposta completa; usar IDs técnicos |
| Dependências | Nenhuma nova dependência |

O UUID de `UserAccount` é dado de auditoria operacional e não uma autorização por recurso. A regra de papel permanece
centralizada em `SecurityConfig`; o use case não confia em papel enviado pelo cliente.

### Rollout e recuperação

Deploy executa primeiro a migration aditiva e depois inicia a aplicação que reconhece `CLOSED`. Rollback do binário
depois que uma ordem for fechada não é seguro, pois o enum e o check antigos não reconhecem o novo estado. A recuperação
deve usar roll-forward ou restauração coordenada, nunca edição manual da migration aplicada.

## Estratégia de testes

### Domínio

- `OPEN → CLOSED` com autoria e instante;
- repetição preserva primeiro fechamento;
- rejeição de estados incompatíveis;
- timestamps monotônicos e invariantes de reconstituição;
- preservação de linhas, demandas e referência externa.

### Aplicação

- primeiro fechamento e replay idempotente;
- ordem inexistente;
- duas confirmações concorrentes com principals distintos;
- `Clock` determinístico;
- consulta e listagem de `OPEN`/`CLOSED`, ordenação e filtros.

### Persistência

- round-trip de `CLOSED`;
- lock de escrita e concorrência;
- checks de estado e índice;
- startup a partir de schema vazio com Flyway e Hibernate `validate`.

### HTTP e segurança

- close sem body, primeiro sucesso e repetição;
- GET individual antes/depois do fechamento;
- listagem sem filtro, por estado, vazia e enum inválido;
- campos nullable/obrigatórios conforme estado;
- `401`, `403`, `404` e `409` com códigos estáveis;
- token de `MANAGER`/`ADMIN` permitido e demais papéis negados;
- OpenAPI e Postman coerentes.

### Arquitetura e regressão

- `ModuleStructureTest` verde;
- nenhum import de internals de `identity` ou Service Lifecycle;
- criação e consulta de Purchase Order `OPEN` permanecem compatíveis;
- `make test` durante os checkpoints e `make verify` antes da conclusão;
- cobertura do código alterado não reduz a meta de 80%.

## Impacto coordenado em RF29

RF29 consumirá a Purchase Order `CLOSED` sob lock, mas não mudará novamente seu estado. O filtro de recebimento será
composto pelo repository de Stock Receipt, sem introduzir campos de recebimento dentro do aggregate `PurchaseOrder`.

A implementação conjunta deve concluir e testar RF28 antes de habilitar o endpoint de recebimento de RF29.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por Matheus Apostulo em 2026-08-25.
- [x] Implementation Plan criado após aprovação técnica em 2026-08-25.
- [ ] Segurança, contratos, migration, Modulith, testes e documentação verificados no plano futuro.

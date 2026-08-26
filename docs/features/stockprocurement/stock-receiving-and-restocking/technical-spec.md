# Especificação Técnica: Recebimento e Reposição de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-receiving-and-restocking` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta especificação deriva da funcional aprovada e pressupõe o desenho técnico de RF28. Nenhum
`implementation-plan.md`, código, migration, evento ou contrato HTTP de RF29 pode ser criado antes da aprovação humana
explícita deste documento e da especificação técnica de RF28.

RF28 e RF29 poderão compartilhar uma implementação coordenada, mas os checkpoints devem demonstrar separadamente que
fechar uma ordem não muda saldo e que somente o Stock Receipt produz a reposição.

## Objetivo técnico

Introduzir um fluxo de recebimento que:

- aceite somente Purchase Order `CLOSED`;
- derive todas as linhas da ordem e não aceite composição no request;
- crie no máximo um `StockReceipt` por Purchase Order;
- registre uma movimentação identificável por linha;
- aumente todos os saldos de forma atômica e protegida contra concorrência;
- permita recebimento de item desativado sem reativação;
- publique o resultado somente depois do commit;
- faça Service Lifecycle tentar novamente execuções relacionadas em ordem de prioridade;
- preserve idempotência do recebimento e dos retries.

Não serão implementados recebimento parcial, divergência, ajuste, devolução, lote, custo ou promessa de material para a
origem da compra.

## Diagnóstico do baseline

O baseline oferece:

- Purchase Order com linhas imutáveis, snapshots e leitura com lock;
- RF28 proposta para adicionar `CLOSED`, autoria e instante de fechamento;
- `StockItem.availableQuantity`, lock pessimista em lote e desconto atômico para reserva;
- `StockReservationApi` com idempotência por `serviceExecutionId`;
- `RetryStockReservationUseCase`, que deriva requirements congelados e atualiza a Service Execution;
- prioridades `URGENT`, `HIGH`, `NORMAL` e `LOW` na Service Order;
- Purchase Demands que preservam a origem sem vincular a Purchase Order diretamente à Service Order;
- autenticação JWT cujo principal é o UUID de `UserAccount`;
- autorização `MANAGER`/`ADMIN` para Purchase Orders e Stock Items.

Ainda não existem operação de soma de saldo, Stock Receipt, movimentação de entrada, query de execuções por Stock Item,
evento de reposição ou retry automático depois de recebimento.

## Contextos e dependências

### Stock & Procurement

Criar `stockprocurement.stockreceipt` com `domain`, `application` e `infrastructure`. `StockReceipt` é aggregate root
independente e referencia `PurchaseOrder` e `StockItem` somente por UUID.

O caso de uso coordena Purchase Order, Stock Receipt e vários Stock Items na mesma transação. Ele pode usar repositories
internos do mesmo bounded context, mas não importa JPA no domínio e não cria aggregate artificial `Stock`.

### Service Lifecycle

Service Lifecycle continua dono de Service Order, Service Execution, prioridade e decisão de prontidão. Stock &
Procurement não consulta repositories nem importa internals desse módulo.

A comunicação segue a direção já existente:

```text
servicelifecycle
    └──> stockprocurement :: stock-restocking-events
    └──> stockprocurement :: stock-reservation-api
```

O evento informa somente IDs técnicos de recebimento, ordem e itens. O consumidor localiza as próprias execuções,
ordena-as e usa o contrato existente de Stock Reservation.

### Spring Modulith

O package `stockprocurement.stockreceipt.application.event` será exposto como
`@NamedInterface("stock-restocking-events")`. Somente o record imutável do evento atravessa a fronteira.

Nenhum novo módulo direto será criado. `ModuleStructureTest` deve verificar a dependência unidirecional e a ausência de
imports de Service Lifecycle em Stock & Procurement.

## Modelo de domínio

### Aggregate root `StockReceipt`

| Atributo | Tipo | Regra |
|---|---|---|
| `id` | `UUID` | Gerado uma vez e imutável |
| `purchaseOrderId` | `UUID` | Obrigatório, imutável e único |
| `lines` | `List<StockReceiptLine>` | Mesma composição integral da ordem, sem item repetido |
| `receivedByUserAccountId` | `UUID` | Principal autenticado opaco |
| `receivedAt` | `Instant` | UTC, truncado em microssegundos e imutável |

`StockReceipt.create(...)` exige ao menos uma linha, IDs não nulos e quantidades positivas. A reconstituição restaura o
mesmo estado e valida todas as invariantes. Não há método de edição, cancelamento ou exclusão.

### `StockReceiptLine`

Cada linha representa também a Stock Movement de entrada desta feature:

| Atributo | Tipo | Regra |
|---|---|---|
| `movementId` | `UUID` | Identidade estável da movimentação |
| `stockItemId` | `UUID` | Copiado da Purchase Order |
| `quantity` | `int` | Inteira e positiva |
| `availableBefore` | `int` | Saldo bloqueado antes da entrada |
| `availableAfter` | `int` | Resultado exato de `before + quantity` |

A origem é implicitamente e invariavelmente `PURCHASE_ORDER_RECEIPT`; não será criado enum genérico com valores sem uso.
O `purchaseOrderId`, Receipt, autoria e instante ficam no aggregate pai e compõem a consulta da movimentação.

O histórico de RF29 será atendido por `stock_receipt_lines`. Não será criado um CRUD geral de Stock Movement antes de
existirem ajustes, perdas ou transferências aprovados.

### Alteração em `StockItem`

Adicionar comportamento de domínio:

```java
public StockItemReceiptBalance receive(Quantity receivedQuantity)
```

O método:

- aceita quantidade positiva;
- usa `Math.addExact` para impedir overflow;
- atualiza `availableQuantity` uma vez;
- retorna saldo anterior e posterior para formar a linha do Receipt;
- não exige item ativo e nunca muda `active`;
- não altera SKU, nome, tipo ou preço.

Quantidade zero/negativa e overflow são rejeitados antes de qualquer persistência confirmada.

## Fluxo de aplicação

### `ReceivePurchaseOrderUseCase`

Método público `@Transactional` recebe `purchaseOrderId` e `userAccountId`:

1. carrega Purchase Order com lock;
2. oculta `PENDING_SUBMISSION`/`REJECTED` como not found e exige `CLOSED`;
3. busca Receipt existente por `purchaseOrderId` com lock;
4. se existir, retorna-o como replay e publica novamente o evento de reposição para recuperação dos retries;
5. ordena os `stockItemId` da ordem e carrega todos com lock pessimista;
6. falha integralmente se alguma referência histórica não puder ser reconstituída;
7. aplica `receive(...)` a todas as linhas e cria as linhas de movimentação;
8. cria e salva o `StockReceipt`;
9. salva todos os Stock Items alterados;
10. publica `StockItemsRestockedEvent` dentro da transação;
11. retorna `ReceivePurchaseOrderResult(receipt, created)`.

O evento será observado somente `AFTER_COMMIT`. Publicá-lo também no replay torna uma nova chamada segura um mecanismo
de recuperação caso a aplicação tenha encerrado depois do commit original e antes dos retries.

### Ordem global de locks

O fluxo adquire:

1. Purchase Order;
2. Stock Receipt equivalente;
3. Stock Items em ordem crescente de UUID.

Reserva não bloqueia Purchase Order/Receipt e já bloqueia Stock Items por UUID. Criação/atualização cadastral deve
continuar usando o lock do Stock Item. Essa disciplina impede perda de atualização e reduz risco de deadlock.

A unique key por `purchaseOrderId` é a proteção final contra corrida. Violação concorrente é reconciliada lendo o
Receipt vencedor; jamais repete a soma dos saldos.

## Evento de reposição

### Contrato

```java
public record StockItemsRestockedEvent(
        UUID stockReceiptId,
        UUID purchaseOrderId,
        List<UUID> stockItemIds,
        Instant occurredAt) {
}
```

Os IDs de item são únicos, imutáveis e ordenados. O evento não contém quantidade, saldo, prioridade, dados pessoais ou
referência a Service Order. RF30 poderá usar `stockReceiptId`/`purchaseOrderId` para reavaliar o ciclo de baixo estoque
sem ampliar o contrato.

### Consumer em Service Lifecycle

`RestockedStockReservationRetryListener` usa
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` e não abre uma transação abrangendo todo o lote.

Fluxo:

1. consulta candidatos por IDs dos Stock Items recebidos;
2. seleciona somente `ServiceExecutionStatus.AWAITING_ITEMS` com requirement congelado sobre algum item recebido;
3. ordena por prioridade `URGENT`, `HIGH`, `NORMAL`, `LOW`;
4. desempata tecnicamente por `serviceOrderId`, depois `serviceExecutionId`;
5. chama `RetryStockReservationUseCase` sequencialmente, uma transação por execução;
6. captura falha de um candidato, registra IDs técnicos e continua os demais.

Adicionar ao repository de Service Order uma query dedicada, por exemplo
`findAwaitingItemsByStockItemIds(Collection<UUID>)`. Ela consulta o status da execução, não apenas
`ServiceOrder.statusSnapshot`, pois uma ordem pode estar globalmente `IN_PROGRESS` e ainda conter outra execução em
`AWAITING_ITEMS`.

Antes de cada tentativa, o use case existente recarrega a Service Order com lock e revalida estado/requirements. Um
candidato que deixou de estar elegível é ignorado como corrida benigna. O desempate por UUID é reproduzível, mas não é
SLA ou preferência funcional entre prioridades iguais.

### Sem rollback do recebimento

O listener roda depois do commit e captura falhas por candidato. Nenhuma falha de busca, reserva, atualização de Service
Lifecycle ou notificação reverte o Stock Receipt.

O projeto não possui registro persistente de publicações do Spring Modulith. Logo, queda no pequeno intervalo entre
commit e listener pode perder a tentativa automática. Mitigações aprovadas pelo desenho:

- replay do endpoint de recebimento republica o evento sem duplicar saldo;
- o endpoint manual de retry da Service Execution continua disponível;
- logs usam Receipt e Execution IDs para diagnóstico.

Introduzir outbox/event publication registry fica fora do MVP e exigiria migration, operação e estratégia próprias.

## Repositories e consultas

Criar `StockReceiptRepository` com:

- `findById(UUID)`;
- `findByPurchaseOrderId(UUID)`;
- `findByPurchaseOrderIdForUpdate(UUID)`;
- `findByPurchaseOrderIds(Collection<UUID>)` para composição sem N+1;
- `save(StockReceipt)`.

Adicionar consulta de Receipt por Purchase Order e composição em lote no read service de Purchase Orders. O aggregate
`PurchaseOrder` não recebe `receiptId`; esse dado pertence ao aggregate novo.

## Contratos HTTP e OpenAPI

### Registrar recebimento

```http
POST /api/purchase-orders/{purchaseOrderId}/receipt
Authorization: Bearer <jwt>
```

Sem body. Respostas:

| Situação | HTTP | Código estável |
|---|---:|---|
| Primeiro recebimento | `201` | — |
| Replay do Receipt existente | `200` | — |
| Ordem/Receipt inexistente na consulta | `404` | `PURCHASE_ORDER_NOT_FOUND` / `STOCK_RECEIPT_NOT_FOUND` |
| Purchase Order ainda `OPEN` | `409` | `PURCHASE_ORDER_NOT_CLOSED` |
| Referência histórica inconsistente | `409` | `STOCK_RECEIPT_INCONSISTENT` |
| Overflow de saldo | `409` | `STOCK_QUANTITY_OVERFLOW` |
| Sem token / sem papel | `401` / `403` | `UNAUTHORIZED` / `FORBIDDEN` |

No primeiro sucesso, `Location` aponta para `/api/purchase-orders/{purchaseOrderId}/receipt`.

### Consultar recebimento

```http
GET /api/purchase-orders/{purchaseOrderId}/receipt
```

`StockReceiptResponse`:

```text
id
purchaseOrderId
receivedByUserAccountId
receivedAt
lines[]:
  movementId
  stockItemId
  skuSnapshot
  nameSnapshot
  typeSnapshot
  quantity
  availableBefore
  availableAfter
```

Os snapshots de apresentação são lidos da Purchase Order imutável, não do cadastro atual. Quantidades e saldos vêm do
Receipt. Consulta ausente retorna `404 STOCK_RECEIPT_NOT_FOUND`.

### Evolução da consulta de Purchase Orders

Adicionar a `PurchaseOrderResponse`:

```text
receiptId
receivedAt
```

Ambos são nulos para `OPEN` e `CLOSED` ainda não recebida; o status da ordem permite distinguir os casos. Para Receipt
existente, ambos são obrigatórios.

Adicionar à listagem:

```http
GET /api/purchase-orders?receiptStatus=PENDING
GET /api/purchase-orders?receiptStatus=RECEIVED
```

- `PENDING`: somente `CLOSED` sem Receipt;
- `RECEIVED`: somente `CLOSED` com Receipt;
- ausência: não restringe pelo recebimento;
- combinação incompatível, como `status=OPEN&receiptStatus=RECEIVED`, retorna `200 []`;
- valor inválido retorna `400 VALIDATION_ERROR`.

OpenAPI, MockMvc, Postman e instruções manuais do `README.md` serão atualizados no mesmo checkpoint.

## Persistência e migration

Classificação: **nenhum seed necessário**.

Uma migration Flyway nova e imutável, com timestamp UTC da implementação, cria:

### `stock_receipts`

| Coluna | Tipo | Restrição |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `purchase_order_id` | `BINARY(16)` | not null, FK e unique |
| `received_by_user_account_id` | `BINARY(16)` | not null, sem FK cross-module |
| `received_at` | `TIMESTAMP(6)` | not null |

### `stock_receipt_lines`

| Coluna | Tipo | Restrição |
|---|---|---|
| `movement_id` | `BINARY(16)` | PK |
| `stock_receipt_id` | `BINARY(16)` | not null, FK |
| `stock_item_id` | `BINARY(16)` | not null, FK |
| `quantity` | `INTEGER` | positiva |
| `available_before` | `INTEGER` | não negativa |
| `available_after` | `INTEGER` | não negativa e igual à soma exata suportada |

Adicionar unique `(stock_receipt_id, stock_item_id)` e índices por `stock_item_id` e `received_at`. Não há backfill:
Purchase Orders antigas permanecem fecháveis e recebíveis depois do deploy.

Flyway continua sendo o único mecanismo de schema e `ddl-auto=validate` permanece obrigatório. Nenhum dado de negócio é
inserido automaticamente.

## Segurança e operação

### Revisão de segurança preliminar

| Item | Avaliação técnica |
|---|---|
| Input/mass assignment | Endpoint sem body; linhas, saldo final, autoria e instante derivam do servidor |
| Autenticação | UUID da conta vem do JWT validado |
| Autorização | `/api/purchase-orders/**` permanece `MANAGER`/`ADMIN` |
| Dados operacionais | Não expor Customer, Vehicle, Estimate, Technician ou preço de compra |
| SQL/concorrência | Locks ordenados, unique por ordem, soma exata e rollback integral |
| Item inativo | Entrada permitida, mas sem reativação ou reserva indevida |
| Logs | IDs técnicos e códigos; sem JWT, e-mail, payload completo ou SQL |
| Dependências | Nenhuma dependência externa nova |
| Abuso | Replay é idempotente; chamadas repetidas podem republicar retry, mas não alterar saldo |

A republicação no replay pode gerar trabalho adicional controlado. Rate limiting não faz parte do MVP; a autorização e
a idempotência impedem efeito financeiro ou de saldo duplicado.

### Rollout e recuperação

RF28 deve estar implantada antes de RF29 aceitar requests. A migration de Receipt é aditiva. Depois do primeiro Receipt,
rollback para um binário que desconhece as tabelas não perde schema, mas remove a capacidade de consultar/repetir o
fluxo; o caminho recomendado é roll-forward.

Falha após commit e antes do retry é recuperável repetindo o POST sem duplicar entrada. Não alterar saldos por SQL
manual; qualquer correção futura deve usar movimentação aprovada.

## Estratégia de testes

### Domínio

- criação/reconstituição de Receipt integral e imutável;
- linhas únicas, quantidades positivas e saldos consistentes;
- `StockItem.receive` ativo/inativo, soma normal e overflow.

### Aplicação e persistência

- primeiro Receipt e replay;
- ordem inexistente, `OPEN` e `CLOSED`;
- todas as linhas confirmadas ou rollback integral;
- round-trip do aggregate e movimentos;
- duas chamadas concorrentes para a mesma ordem;
- corrida entre recebimento e reserva sem perda de atualização;
- Receipt de item desativado sem reativação;
- composição em lote de Purchase Orders sem N+1;
- startup vazio com Flyway/Hibernate e MySQL do Docker Compose.

### Retry e módulos

- evento somente depois do commit;
- republicação no replay;
- query encontra execução `AWAITING_ITEMS` mesmo com Service Order global em outro status;
- ordenação `URGENT`, `HIGH`, `NORMAL`, `LOW` e desempate estável;
- cada execução usa transação independente e conjunto completo de requirements;
- falha/inelegibilidade de uma execução não bloqueia as demais nem reverte Receipt;
- maior prioridade pode consumir saldo e menor continuar aguardando;
- `@ApplicationModuleTest` verifica evento Stock & Procurement → Service Lifecycle;
- `ModuleStructureTest` confirma named interface e dependência acíclica.

### HTTP, segurança e documentação

- `201` inicial, `200` replay e `Location`;
- GET do Receipt e snapshots da Purchase Order;
- filtro `PENDING`/`RECEIVED` e combinações vazias;
- contratos sem body e sem campos atribuíveis indevidamente;
- `400`, `401`, `403`, `404` e `409` com códigos estáveis;
- OpenAPI gerado, Postman e README atualizados juntos;
- `make test`, `make verify` e cobertura mínima do código alterado.

## Impacto em RF30

RF30 poderá observar o mesmo `StockItemsRestockedEvent` e consultar Receipt/Purchase Order por interfaces internas do
bounded context para decidir se uma ocorrência comprada encerrou. Essa reação não será criada em RF29 antes da technical
spec de RF30 ser aprovada.

RF29 não conhece `minimumQuantity`, `targetQuantity` ou regra de baixo estoque.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por Matheus Apostulo em 2026-08-25.
- [x] Technical Spec de RF28 aprovada como precondição em 2026-08-25.
- [x] Implementation Plan criado após as aprovações técnicas em 2026-08-25.
- [ ] Segurança, contratos, migration, eventos, Modulith, testes e documentação verificados no plano futuro.

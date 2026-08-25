# Especificação Técnica: Criação de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-creation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta especificação deriva da funcional aprovada. Nenhum `implementation-plan.md` pode ser criado e nenhuma alteração de
código, schema, dependência, Docker Compose ou contrato HTTP pode começar antes da aprovação humana explícita deste
documento.

O desenho mantém RF30, RF28 e RF29 como features separadas. A API interna de demanda por baixo estoque será entregue
por RF27 para estabilizar o ponto de integração, mas nenhum campo de nível mínimo ou detector será implementado agora.

## Objetivo técnico

Evoluir a capability já implementada de Purchase Order para que:

- avalie requirements de reparo no Diagnosis e na geração da Estimate por API pública síncrona;
- registre Purchase Demands idempotentes desde a primeira insuficiência observada;
- reconcilie a mesma demanda quando a Estimate ou a Stock Reservation revalidarem a disponibilidade;
- aceite futuramente demandas `LOW_STOCK` por uma API interna pequena;
- liste demandas abertas para decisão do Stock Manager;
- crie Purchase Orders ad hoc, orientadas por demandas ou mistas;
- consolide as linhas e valide Stock Items ativos;
- proteja uma demanda contra duas compras concorrentes;
- integre por HTTP com o External Supplier System através de port, adapter e Anti-Corruption Layer;
- preserve idempotência diante de retry, timeout, perda de resposta e crash entre o efeito externo e o commit local;
- exponha somente Purchase Orders confirmadas como `OPEN` nos contratos de negócio.

RF27 não altera saldo, não fecha ordem, não registra recebimento, não tenta novamente reservas, não modela Supplier e
não introduz preço de compra.

## Diagnóstico do código atual

O baseline implementado oferece os elementos necessários para o delta:

- `StockItem` possui ID, SKU imutável, nome, tipo, estado ativo e quantidade disponível;
- `StockItemRepository.findAllByIdForUpdate` permite validar itens com lock e ordem determinística;
- `ReserveStockItemsUseCase` publica `StockReservationNotReservedEvent` com `serviceExecutionId`, item, quantidade
  solicitada, quantidade disponível e motivo;
- o mesmo caso de uso publica `StockReservationCreatedEvent` somente na primeira reserva efetiva;
- `INSUFFICIENT_QUANTITY`, `STOCK_ITEM_NOT_FOUND` e `STOCK_ITEM_INACTIVE` já são resultados distintos;
- `PurchaseDemand`, Purchase Order, integração do fornecedor, WireMock e contratos HTTP de RF27 estão implementados;
- `PENDING_REPAIR` nasce hoje somente de `StockReservationNotReservedEvent`;
- `PurchaseDemandApi` existe para `LOW_STOCK`, mas seu package ainda não é named interface entre módulos;
- a resposta da demanda ainda não expõe `updatedAt`;
- não existe snapshot persistido de disponibilidade em Service Execution ou Estimate;
- continua inexistente `minimumQuantity` ou detector de baixo estoque de RF30;
- os endpoints atuais não possuem autenticação ou autorização implementada no baseline local.

O listener de notificação de Stock Reservation é after-commit e continuará independente. A criação ou resolução de
Purchase Demand será uma reação transacional de negócio, não parte do outbound de Notification.

## Contextos e fronteiras

### Stock & Procurement

Todo o código novo ficará sob `stockprocurement.purchaseorder`, com `domain`, `application` e `infrastructure`.
`PurchaseDemand`, `PurchaseOrder` e `StockItem` permanecem aggregates independentes dentro do mesmo bounded context.

`PurchaseOrder` não será filha de um aggregate `Stock`, e não haverá entidade ou `stockId`. A coordenação entre
Purchase Order, demandas e Stock Items acontecerá nos casos de uso de aplicação.

### Service Lifecycle

Nenhum pacote de Service Lifecycle será importado por Stock & Procurement. `serviceExecutionId` será tratado como UUID
opaco e não terá foreign key para `service_executions`.

RF27 será chamada por Diagnosis e Estimate através de uma API pública que recebe apenas IDs e quantidades, e continuará
reagindo aos eventos de Stock Reservation. Ela não consulta Service Order, Estimate, Customer, Vehicle ou prioridade.
A priorização após recebimento permanece em RF29.

### External Supplier System

O sistema externo é upstream e será acessado somente por `ExternalSupplierGateway`, uma porta pertencente ao consumidor
Stock & Procurement. O adapter HTTP traduzirá DTOs externos para resultados internos; tipos do fornecedor nunca serão
usados pelo domínio, repository ou controller.

No MVP o SKU normalizado e imutável será enviado como código do produto externo. Não haverá tabela de Supplier nem de
mapeamento de catálogo. Caso um fornecedor real não aceite o SKU como código, essa mudança exigirá discovery própria de
catálogo/mapeamento antes da substituição do simulador.

### Spring Modulith

Nenhum novo módulo direto será criado. `ModuleStructureTest` deve continuar encontrando somente `registration`,
`servicelifecycle` e `stockprocurement`.

O package `purchaseorder.application.api` receberá `@NamedInterface("purchase-demand-api")` porque passa a ser consumido
por Service Lifecycle. Somente interfaces, commands, results e enums imutáveis desse package serão expostos; nenhum tipo
de domínio, JPA, web ou integração externa atravessará a fronteira.

## Modelo de domínio

### Aggregate root `PurchaseDemand`

| Atributo | Tipo proposto | Regra |
|---|---|---|
| `id` | `UUID` | Gerado na primeira detecção e imutável |
| `origin` | `PurchaseDemandOrigin` | `PENDING_REPAIR` ou `LOW_STOCK` |
| `originReferenceId` | `UUID` | Service Execution ou ocorrência de baixo estoque |
| `stockItemId` | `UUID` | Referência canônica, obrigatória e imutável |
| `requestedQuantity` | `Integer?` | Obrigatória apenas para `PENDING_REPAIR` |
| `observedAvailableQuantity` | `int` | Não negativa e atualizável enquanto aberta |
| `suggestedQuantity` | `int` | Positiva e atualizável enquanto aberta |
| `status` | `PurchaseDemandStatus` | `OPEN`, `CLAIMED`, `ORDERED` ou `RESOLVED` |
| `claimedByPurchaseOrderId` | `UUID?` | Presente somente em `CLAIMED` |
| `createdAt` | `Instant` | UTC, imutável e truncado em microssegundos |
| `updatedAt` | `Instant` | UTC, monotônico dentro do aggregate |
| `resolvedAt` | `Instant?` | Presente somente em `RESOLVED` |

Comportamentos:

- `recordPendingRepair(...)` cria uma demanda ou atualiza as quantidades da demanda `OPEN` equivalente;
- `recordLowStock(...)` aplica a mesma regra usando o ID estável da ocorrência produzido por RF30;
- `claim(purchaseOrderId)` muda `OPEN` para `CLAIMED` e impede outra seleção;
- repetir `claim` com o mesmo ID é idempotente; usar outro ID é conflito;
- `markOrdered(purchaseOrderId)` exige a claim correspondente e muda para `ORDERED`;
- `release(purchaseOrderId)` devolve a demanda `CLAIMED` para `OPEN` após rejeição funcional externa;
- `resolve(...)` muda apenas `OPEN` para `RESOLVED` quando a reserva é concluída antes da compra;
- eventos repetidos não reabrem `ORDERED` ou `RESOLVED` nem modificam uma demanda `CLAIMED`.

A equivalência será protegida por `origin + originReferenceId + stockItemId`. Para `PENDING_REPAIR`,
`originReferenceId` é o `serviceExecutionId`. RF30 deverá produzir um ID por ocorrência, mantendo o mesmo ID enquanto a
condição de baixo estoque continuar aberta e usando outro ID para uma ocorrência futura.

### Enum `PurchaseDemandOrigin`

Terá exatamente `PENDING_REPAIR` e `LOW_STOCK`. O enum não será aceito em um endpoint de criação manual de demanda; a
origem é definida somente por integração interna confiável.

### Aggregate root `PurchaseOrder`

| Atributo | Tipo proposto | Regra |
|---|---|---|
| `id` | `UUID` | Gerado na preparação e usado como referência enviada ao fornecedor |
| `idempotencyKey` | `UUID` | Obrigatória, única e fornecida no header HTTP |
| `payloadHash` | `String` | SHA-256 do comando normalizado, imutável |
| `status` | `PurchaseOrderStatus` | `PENDING_SUBMISSION`, `OPEN` ou `REJECTED` |
| `lines` | `List<PurchaseOrderLine>` | Não vazia, imutável e sem Stock Item repetido |
| `selectedDemandIds` | `Set<UUID>` | Pode ser vazio; imutável depois da preparação |
| `externalReference` | `String?` | Obrigatória e única somente em `OPEN` |
| `supplierRejectionCode` | `String?` | Sanitizado e presente somente em `REJECTED` |
| `createdAt` | `Instant` | Instante da preparação local |
| `updatedAt` | `Instant` | Última transição confirmada |
| `openedAt` | `Instant?` | Presente somente em `OPEN` |

`PENDING_SUBMISSION` é um estado técnico durável necessário para não manter lock de banco durante I/O e para recuperar
crash ou resposta perdida. Ele não significa que uma Purchase Order foi confirmada no contrato funcional e não será
retornado por `GET /api/purchase-orders/{id}`.

Comportamentos:

- `prepare(...)` cria o aggregate em `PENDING_SUBMISSION` com conteúdo normalizado e snapshots das linhas;
- `open(externalReference, openedAt)` confirma a resposta externa uma única vez;
- repetir a confirmação com a mesma referência é idempotente;
- confirmar outra referência ou abrir uma ordem `REJECTED` é conflito interno;
- `reject(code, rejectedAt)` registra rejeição funcional e impede ressubmissão sob a mesma idempotency key;
- não existem edição, remoção, fechamento, cancelamento ou recebimento nesta feature.

`OPEN` é o único estado de Purchase Order exposto como sucesso. `CLOSED` somente poderá ser adicionado por RF28 depois
de aprovação própria.

### Value object `PurchaseOrderLine`

Cada linha contém:

- `stockItemId` obrigatório;
- `skuSnapshot` normalizado, imutável e limitado a 100 caracteres;
- `nameSnapshot` imutável e limitado a 255 caracteres;
- `typeSnapshot` entre `PART`, `CONSUMABLE` e `SUPPLY`;
- quantidade inteira positiva.

Itens repetidos no request serão somados com `Math.addExact` e ordenados por UUID. Os snapshots preservam o material
confirmado e tornam um retry externo independente de atualização de nome ou desativação posterior. Nenhum preço será
copiado ou enviado.

## Contratos internos e eventos

### Avaliação antecipada de requirements

Adicionar ao named interface `purchase-demand-api`:

```java
public interface RepairStockAssessmentApi {
    RepairStockAssessmentResult assessAndRecord(RepairStockAssessmentCommand command);
}
```

Todos os tipos ficarão em `purchaseorder.application.api`:

- `RepairStockAssessmentCommand(List<RepairExecutionStockRequirements> executions)`;
- `RepairExecutionStockRequirements(UUID serviceExecutionId, List<RepairStockRequirement> requirements)`;
- `RepairStockRequirement(UUID stockItemId, int requestedQuantity)`;
- `RepairStockAssessmentResult(List<RepairExecutionStockAssessment> assessments,
  List<RepairStockAssessmentIssue> issues)`;
- `RepairExecutionStockAssessment(UUID serviceExecutionId, List<RepairStockItemAssessment> items)`;
- `RepairStockItemAssessment` com item, quantidades, status e `observedAt`;
- `RepairStockAssessmentIssue(UUID serviceExecutionId, UUID stockItemId, RepairStockAssessmentIssueReason reason)`;
- enums `RepairStockAvailabilityStatus` e `RepairStockAssessmentIssueReason` com os valores definidos abaixo.

O comando em lote contém uma ou mais Service Executions. Cada entrada possui `serviceExecutionId` e uma ou mais linhas
`stockItemId + requestedQuantity`. Os records públicos rejeitam IDs nulos, listas nulas/vazias, quantidade não positiva,
duplicatas não consolidadas e overflow. Diagnosis e Estimate consolidam antes da chamada, e o provider valida novamente.

O resultado de sucesso contém avaliações ordenadas por `serviceExecutionId` e `stockItemId`:

```text
serviceExecutionId
stockItemId
requestedQuantity
observedAvailableQuantity
shortageQuantity
status = AVAILABLE | INSUFFICIENT_QUANTITY
observedAt
```

O provider usa um único `observedAt` por lote, em UTC e truncado em microssegundos. `shortageQuantity` é zero quando o
saldo é suficiente e a diferença positiva exata quando insuficiente.

Item inexistente ou inativo produz resultado inválido com `issues` ordenadas, contendo IDs e reason
`STOCK_ITEM_NOT_FOUND` ou `STOCK_ITEM_INACTIVE`. Nesse caso, `assessments` fica vazio e nenhuma demanda é criada ou
atualizada. Os consumidores traduzem o primeiro issue determinístico para seus erros HTTP locais.

`AssessRepairStockNeedsUseCase` implementará a API com `@Transactional(propagation = MANDATORY)`, garantindo que
Diagnosis ou Estimate, snapshots e demandas confirmem ou revertam juntos. O algoritmo:

1. normaliza e valida o lote inteiro;
2. bloqueia todos os Stock Items únicos em ordem de UUID;
3. retorna issues sem escrita se alguma referência estiver ausente ou inativa;
4. calcula cada avaliação sem alterar `availableQuantity`;
5. para insuficiências, bloqueia a demanda equivalente e cria ou atualiza `OPEN`;
6. deixa `CLAIMED`, `ORDERED` ou `RESOLVED` imutáveis;
7. não resolve demanda em observação suficiente, pois nenhuma unidade foi reservada;
8. persiste todas as mudanças e devolve a fotografia usada pelos consumidores.

A equivalência continua `PENDING_REPAIR + serviceExecutionId + stockItemId`. Revalidações no Diagnosis, Estimate e
Stock Reservation convergem para a mesma identidade. Rejeição ou expiração da Estimate não chama `resolve`.

### Origem `PENDING_REPAIR`

O listener síncrono existente continua participando da transação que publicou os eventos:

- em `StockReservationNotReservedEvent`, filtra somente issues `INSUFFICIENT_QUANTITY`;
- calcula `suggestedQuantity = requestedQuantity - availableQuantity` com resultado estritamente positivo;
- cria ou atualiza a demanda pela chave `PENDING_REPAIR + serviceExecutionId + stockItemId`;
- ignora `STOCK_ITEM_NOT_FOUND` e `STOCK_ITEM_INACTIVE`;
- em `StockReservationCreatedEvent`, resolve demandas `OPEN` equivalentes para as linhas reservadas.

O listener usará `@EventListener`, sem `REQUIRES_NEW`. Falha ao persistir a demanda reverte a transação que produziu a
indisponibilidade, evitando uma Service Execution aprovada e pendente sem a demanda correspondente. O listener
after-commit de Notification existente não será alterado.

O fluxo já mantém locks dos Stock Items até o fim da tentativa de reserva. Reações concorrentes para o mesmo item ficam
serializadas por esses locks; o listener ainda consultará a demanda equivalente com lock antes de criar ou atualizar.

`RecordPendingRepairDemandUseCase` reutilizará a mesma operação de aplicação que grava uma observação insuficiente, para
que cálculo, estado e idempotência não sejam duplicados entre API e listener.

Uma demanda `CLAIMED` não é resolvida por reserva concorrente: a submissão externa já iniciou e deve concluir ou ser
reconciliada. Esse caso pode gerar reposição adicional, coerente com a decisão de não prometer material futuro a uma
Service Order específica.

### Origem `LOW_STOCK`

RF27 fornecerá um contrato interno equivalente a:

```java
public interface PurchaseDemandApi {
    void recordLowStock(LowStockPurchaseDemandCommand command);
}

public record LowStockPurchaseDemandCommand(
        UUID occurrenceId,
        UUID stockItemId,
        int observedAvailableQuantity,
        int suggestedQuantity) {
}
```

O use case carregará o Stock Item com lock, validará existência e estado ativo e persistirá a demanda com transação de
escrita. Não haverá controller para simular RF30, nem criação livre de demandas pelo cliente HTTP. Testes de RF27 usarão
o contrato diretamente; RF30 será o primeiro consumidor real depois do próprio SDD.

## Casos de uso de aplicação

| Caso de uso | Transação | Responsabilidade |
|---|---|---|
| `AssessRepairStockNeedsUseCase` | `MANDATORY` | Avaliar saldo e reconciliar demandas antecipadas |
| `RecordPendingRepairDemandUseCase` | participa da atual | Criar, atualizar ou resolver demanda por eventos |
| `RecordLowStockPurchaseDemandUseCase` | escrita | Implementar `PurchaseDemandApi` |
| `SearchOpenPurchaseDemandsUseCase` | `readOnly` | Listar e filtrar demandas abertas |
| `CreatePurchaseOrderUseCase` | orquestrador sem transação externa | Coordenar preparação, gateway e finalização |
| `PreparePurchaseOrderSubmissionUseCase` | escrita curta | Normalizar, validar, persistir e fazer claims |
| `ConfirmPurchaseOrderSubmissionUseCase` | escrita curta | Abrir ordem e marcar demandas como `ORDERED` |
| `RejectPurchaseOrderSubmissionUseCase` | escrita curta | Rejeitar ordem e liberar demandas |
| `GetPurchaseOrderUseCase` | `readOnly` | Consultar uma ordem `OPEN` por ID |

`CreatePurchaseOrderUseCase` não será `@Transactional`. Os três passos persistentes ficarão em colaboradores Spring
distintos para que os proxies transacionais sejam efetivos e nenhuma chamada HTTP aconteça dentro de uma transação.

## Preparação da Purchase Order

`PreparePurchaseOrderSubmissionUseCase` seguirá esta ordem:

1. normalizar `demandIds` como conjunto ordenado e consolidar as linhas por `stockItemId`;
2. rejeitar ausência de linhas, quantidade inválida, overflow ou mais de 100 linhas/demandas;
3. calcular SHA-256 sobre a representação canônica de IDs e quantidades já normalizados;
4. procurar `PurchaseOrder` pela `idempotencyKey`;
5. se existir, comparar o hash: conflito para conteúdo diferente; retorno idempotente para conteúdo igual;
6. ler uma projeção sem lock das demandas selecionadas apenas para descobrir seus Stock Items;
7. bloquear todos os Stock Items envolvidos em ordem de UUID e exigir existência e estado ativo;
8. bloquear as demandas selecionadas em ordem de UUID, recarregar e exigir estado `OPEN`;
9. exigir que toda demanda selecionada possua linha correspondente;
10. somar as sugestões por item e exigir quantidade final maior ou igual ao total selecionado;
11. criar `PurchaseOrder` `PENDING_SUBMISSION`, snapshots de linhas e links de auditoria;
12. fazer `claim` de todas as demandas e confirmar a transação.

Uma criação ad hoc usa `demandIds` vazio e passa pelas mesmas validações de Stock Item e linhas. Duas operações com
idempotency keys diferentes são compras distintas mesmo quando o conteúdo é igual.

A constraint única de idempotency key resolve a corrida entre duas primeiras chamadas iguais. Se ambas não encontrarem
o registro antes do insert, a violação concorrente será interceptada, o registro vencedor será recarregado e o mesmo
algoritmo de hash/idempotência será aplicado sem expor SQL.

## Integração externa e recuperação

### Port

A aplicação definirá tipos internos equivalentes a:

```java
public interface ExternalSupplierGateway {
    ExternalPurchaseOrderResult submit(ExternalPurchaseOrderCommand command);
}

public record ExternalPurchaseOrderCommand(
        UUID purchaseOrderId,
        UUID idempotencyKey,
        List<ExternalPurchaseOrderLine> lines) {
}
```

O resultado será selado ou enumerado em:

- `Accepted(externalReference)`;
- `Rejected(rejectionCode)`;
- falha técnica por exception própria para timeout, conexão, `5xx` ou resposta inválida.

### Contrato HTTP do fornecedor no MVP

O adapter usará `RestClient`, já disponível por `spring-boot-starter-web`, sem cliente HTTP adicional em runtime.

```http
POST /api/v1/purchase-orders
Idempotency-Key: <UUID recebido pela oficina>
Content-Type: application/json
```

Payload traduzido:

```json
{
  "workshopOrderReference": "64f14c19-b4f5-4489-91b0-8d536084b7c8",
  "items": [
    {
      "productCode": "OIL-FILTER-001",
      "quantity": 10
    }
  ]
}
```

Aceitação `201 Created`:

```json
{
  "supplierOrderReference": "SUP-64f14c19-b4f5-4489-91b0-8d536084b7c8",
  "status": "ACCEPTED"
}
```

Rejeição funcional `422 Unprocessable Content`:

```json
{
  "code": "PRODUCT_NOT_AVAILABLE",
  "message": "Purchase order cannot be accepted"
}
```

Somente `supplierOrderReference` validada e limitada a 255 caracteres e um código externo mapeado atravessam a ACL.
Mensagem, headers, stack trace e body externos não são copiados para erros públicos.

O contrato externo deve honrar `Idempotency-Key`: repetir o mesmo conteúdo devolve a mesma referência, sem outro pedido;
reutilizar a chave com outro conteúdo é rejeitado. Essa é uma precondição para a garantia de não duplicação além do
banco local e será implementada pelo simulador.

### Fluxo de submissão

Depois da preparação commitada:

1. o orquestrador monta o comando externo a partir dos snapshots persistidos;
2. chama o gateway fora de transação e sem locks de banco;
3. em `Accepted`, abre a ordem em nova transação e muda suas demandas `CLAIMED` para `ORDERED`;
4. em `Rejected`, registra `REJECTED` e devolve as demandas para `OPEN` na mesma transação;
5. em timeout, conexão, `5xx` ou resposta inconclusiva, mantém `PENDING_SUBMISSION` e demandas em `CLAIMED`;
6. devolve erro estável orientando repetir exatamente o mesmo request e `Idempotency-Key` no último caso.

No retry, o passo de preparação encontra a ordem existente:

- `OPEN`: não chama o fornecedor e retorna a resposta existente;
- `REJECTED`: não chama novamente e devolve a mesma rejeição sanitizada;
- `PENDING_SUBMISSION`: reapresenta o payload persistido com a mesma idempotency key;
- mesmo header e hash diferente: conflito sem chamada externa.

Se a aplicação cair depois da aceitação externa e antes do commit local, o retry repete o POST com a mesma chave; o
fornecedor retorna a mesma referência e a ordem é aberta localmente. Não haverá retry automático cego, scheduler,
outbox ou biblioteca de resiliência nesta feature.

## Contratos HTTP da oficina

DTOs de domínio, JPA e do fornecedor não serão expostos diretamente.

### Operações

| Método e path | Comportamento | Sucesso |
|---|---|---|
| `GET /api/purchase-demands` | Listar demandas `OPEN` com filtros opcionais | `200 OK` |
| `POST /api/purchase-orders` | Preparar, enviar e confirmar uma ordem | `201 Created` ou `200 OK` idempotente |
| `GET /api/purchase-orders/{purchaseOrderId}` | Consultar Purchase Order `OPEN` | `200 OK` |

Não haverá endpoints para criar demanda, editar, excluir, fechar, receber ou reenviar por ID. O retry usa o mesmo POST,
body e `Idempotency-Key` conhecidos pelo cliente.

### Consulta de demandas

`GET /api/purchase-demands` aceitará `origin` e `stockItemId` opcionais, combinados com `AND`. O endpoint retornará
somente demandas `OPEN`, ordenadas por `createdAt` e depois `id`, sem paginação no MVP.

Resposta:

```json
[
  {
    "id": "5c45638a-915e-47c8-a271-516a8ee08b22",
    "origin": "PENDING_REPAIR",
    "stockItem": {
      "id": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
      "sku": "OIL-FILTER-001",
      "name": "Oil filter",
      "type": "PART"
    },
    "requestedQuantity": 5,
    "observedAvailableQuantity": 2,
    "suggestedQuantity": 3,
    "serviceExecutionId": "a49e8d8a-cbd4-4e1f-92d0-6b7fe733023f",
    "createdAt": "2026-08-24T15:30:00Z",
    "updatedAt": "2026-08-25T15:35:00Z"
  }
]
```

Para `LOW_STOCK`, `requestedQuantity` e `serviceExecutionId` serão `null`. A resposta também incluirá `updatedAt`,
permitindo distinguir a primeira detecção da observação mais recente. Dados do Stock Item são obtidos pela porta do
aggregate canônico; a demanda não replica preço, Customer, Vehicle, Estimate ou prioridade.

### Criação

Header obrigatório:

```http
Idempotency-Key: e6a63465-481c-4492-84e9-7862d3ab0aa4
```

Request misto:

```json
{
  "demandIds": [
    "5c45638a-915e-47c8-a271-516a8ee08b22"
  ],
  "lines": [
    {
      "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
      "quantity": 5
    },
    {
      "stockItemId": "2323d95e-a83a-49bb-8ca6-af46a238ca33",
      "quantity": 2
    }
  ]
}
```

`demandIds` pode estar ausente ou vazio. `lines` é obrigatório, contém de 1 a 100 entradas e cada quantidade usa
`@Positive`. UUIDs nulos, listas com elementos nulos e overflow após consolidação são inválidos.

Response:

```json
{
  "id": "64f14c19-b4f5-4489-91b0-8d536084b7c8",
  "externalReference": "SUP-64f14c19-b4f5-4489-91b0-8d536084b7c8",
  "status": "OPEN",
  "lines": [
    {
      "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
      "sku": "OIL-FILTER-001",
      "name": "Oil filter",
      "type": "PART",
      "quantity": 5
    }
  ],
  "demandIds": [
    "5c45638a-915e-47c8-a271-516a8ee08b22"
  ],
  "createdAt": "2026-08-24T15:31:00Z",
  "openedAt": "2026-08-24T15:31:01Z"
}
```

A primeira confirmação retorna `201 Created`, `Location: /api/purchase-orders/{id}` e body. Repetição de uma ordem já
`OPEN` com a mesma chave e conteúdo retorna `200 OK` e o mesmo body, sem chamar o fornecedor.

### Consulta da Purchase Order

`GET /api/purchase-orders/{id}` retorna o mesmo response de criação somente quando a ordem está `OPEN`.
`PENDING_SUBMISSION` e `REJECTED` são estados internos consultados pelo fluxo idempotente e não formam um CRUD público.

## Falhas e códigos estáveis

O handler específico de `PurchaseOrderController` continuará usando o `ErrorResponse` global.

| Situação | HTTP | Código |
|---|---:|---|
| Body, header, filtro, UUID ou quantidade inválida | `400` | `VALIDATION_ERROR` |
| Invariante inválida da ordem | `400` | `INVALID_PURCHASE_ORDER` |
| Purchase Demand inexistente | `404` | `PURCHASE_DEMAND_NOT_FOUND` |
| Purchase Order `OPEN` inexistente | `404` | `PURCHASE_ORDER_NOT_FOUND` |
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| Purchase Demand não selecionável ou claim concorrente | `409` | `PURCHASE_DEMAND_NOT_SELECTABLE` |
| Stock Item inativo | `409` | `STOCK_ITEM_INACTIVE` |
| Mesma idempotency key com comando diferente | `409` | `PURCHASE_ORDER_IDEMPOTENCY_CONFLICT` |
| Rejeição funcional do fornecedor | `422` | `SUPPLIER_ORDER_REJECTED` |
| Timeout, conexão ou `5xx` externo | `503` | `EXTERNAL_SUPPLIER_UNAVAILABLE` |
| Resposta externa inválida ou incompatível | `502` | `EXTERNAL_SUPPLIER_INVALID_RESPONSE` |

Rejeições externas serão mapeadas para uma mensagem local genérica. Falhas inesperadas propagam para o tratamento padrão
sem retornar SQL, nome de constraint, URL interna, payload ou tipo de exception.

## Persistência

As tabelas abaixo já existem na migration operacional de RF27 e não serão alteradas para a detecção antecipada. O delta
reutiliza `origin_reference_id = serviceExecutionId`, `updated_at` e a unique key existentes. As novas tabelas de
snapshot pertencem às migrations de `perform-diagnosis` e `estimate-generation`; nenhuma migration aplicada será
modificada.

### `purchase_demands`

| Coluna | Tipo MySQL | Regra |
|---|---|---|
| `id` | `BINARY(16)` | Primary key |
| `origin` | `VARCHAR(32)` | `PENDING_REPAIR` ou `LOW_STOCK` |
| `origin_reference_id` | `BINARY(16)` | Obrigatória |
| `stock_item_id` | `BINARY(16)` | FK para `stock_items` |
| `requested_quantity` | `INTEGER` | Nula somente para `LOW_STOCK` |
| `observed_available_quantity` | `INTEGER` | Não negativa |
| `suggested_quantity` | `INTEGER` | Positiva |
| `status` | `VARCHAR(32)` | `OPEN`, `CLAIMED`, `ORDERED` ou `RESOLVED` |
| `claimed_by_purchase_order_id` | `BINARY(16)` | FK nullable para `purchase_orders` |
| `created_at` | `TIMESTAMP(6)` | Obrigatória |
| `updated_at` | `TIMESTAMP(6)` | Obrigatória |
| `resolved_at` | `TIMESTAMP(6)` | Presente somente em `RESOLVED` |

Constraints e índices:

- unique `(origin, origin_reference_id, stock_item_id)` para deduplicar o gatilho;
- check de enum, quantidades e coerência entre origem e `requested_quantity`;
- check entre `status`, `claimed_by_purchase_order_id` e `resolved_at`;
- índices `(status, created_at, id)`, `(origin, status)` e `(stock_item_id, status)`;
- nenhuma FK para Service Lifecycle.

### `purchase_orders`

| Coluna | Tipo MySQL | Regra |
|---|---|---|
| `id` | `BINARY(16)` | Primary key |
| `idempotency_key` | `BINARY(16)` | Obrigatória e unique |
| `payload_hash` | `CHAR(64)` | SHA-256 hexadecimal, obrigatório |
| `status` | `VARCHAR(32)` | `PENDING_SUBMISSION`, `OPEN` ou `REJECTED` |
| `external_reference` | `VARCHAR(255)` | Unique e obrigatória apenas em `OPEN` |
| `supplier_rejection_code` | `VARCHAR(64)` | Presente somente em `REJECTED` |
| `created_at` | `TIMESTAMP(6)` | Obrigatória |
| `updated_at` | `TIMESTAMP(6)` | Obrigatória |
| `opened_at` | `TIMESTAMP(6)` | Presente somente em `OPEN` |

Checks garantirão a coerência dos campos por estado. A unique constraint de `external_reference` impede que respostas
externas distintas sejam associadas indevidamente a mais de uma ordem local.

### Linhas e vínculos

`purchase_order_lines` terá primary key `(purchase_order_id, stock_item_id)`, FK para `purchase_orders` e
`stock_items`, snapshots `sku VARCHAR(100)`, `name VARCHAR(255)`, `type VARCHAR(32)` e `quantity INTEGER > 0`.

`purchase_order_demand_links` terá primary key `(purchase_order_id, purchase_demand_id)` e FKs internas. O vínculo será
preservado inclusive em uma tentativa rejeitada para auditoria e comparação idempotente. Uma demanda liberada poderá
ser vinculada a uma nova tentativa; por isso `purchase_demand_id` não será unique isoladamente nessa tabela.

Entidades JPA e embeddables serão projeções separadas. Repositories de domínio não exporão `JpaRepository`, locks JPA,
Specifications ou entidades de persistência.

## Concorrência e consistência

- todo fluxo que usa ambos bloqueia primeiro Stock Items e depois Purchase Demands, cada conjunto em ordem de UUID;
- preparação de Purchase Order usa leitura inicial sem lock apenas para descobrir Stock Items e revalida as demandas
  depois de adquirir os locks;
- criação/atualização por gatilho consultará a demanda equivalente com lock enquanto o Stock Item de origem está
  bloqueado, e a unique constraint permanecerá como proteção final;
- a preparação fará claim de todas as demandas ou de nenhuma;
- a confirmação abrirá a ordem e marcará todas as demandas como `ORDERED` na mesma transação;
- a rejeição liberará todas ou nenhuma;
- nenhuma transação de banco permanecerá aberta durante HTTP;
- a idempotency key protege repetição lógica; constraints protegem corridas que escapem da validação;
- `payloadHash` é calculado do comando normalizado, não dos bytes ou da ordem dos arrays JSON;
- a integração externa usa a mesma idempotency key em toda tentativa;
- nenhuma falha de fornecedor altera saldo ou estado de Stock Reservation;
- não será usado `REQUIRES_NEW` para fragmentar uma transição local.

Falhas de persistência na finalização deixam a ordem `PENDING_SUBMISSION`; o efeito externo é reconciliado pelo retry
idempotente. Ordens nesse estado mantêm as demandas em `CLAIMED` para impedir outra compra com uma chave diferente.

## Configuração e simulador externo

### Configuração da aplicação

As propriedades usarão prefixo `app.supplier` e binding tipado com validação:

- `base-url`, fornecida por ambiente;
- `connect-timeout`, default de desenvolvimento de 2 segundos;
- `read-timeout`, default de desenvolvimento de 5 segundos;
- eventual `api-key`, somente por variável de ambiente e nunca em arquivo versionado.

O cliente aceitará apenas a base URL configurada na inicialização; nenhum host, path ou credencial virá do request HTTP,
reduzindo risco de SSRF. O adapter não fará retry automático.

### WireMock

O Docker Compose de desenvolvimento receberá um serviço `supplier-simulator`, com imagem oficial WireMock 3.x fixada
em versão revisada no momento da implementação, porta local `8089` e mappings versionados em
`docker/wiremock/mappings`. O container `app` usará `http://supplier-simulator:8080`.

O simulador não será outra aplicação do domínio e não terá banco, autenticação, UI ou CRUD. Os mappings cobrirão:

- sucesso padrão, ecoando de forma determinística a idempotency key na referência externa;
- rejeição `422` para um SKU de demonstração explícito;
- resposta lenta para um SKU de timeout explícito;
- repetição da mesma chave com a mesma referência observável.

WireMock será dependência somente de teste para validar o adapter HTTP sem subir Docker. A versão Maven e a imagem serão
fixadas e revisadas contra vulnerabilidades na implementação; não será usada tag `latest`.

## Dados de bootstrap

Classificação: **no seed required**.

Purchase Demands devem nascer dos eventos/use cases válidos e Purchase Orders devem ser criadas pelo fluxo HTTP. Não
haverá Flyway de dados, seeder de ordens ou exemplos de negócio inseridos automaticamente.

Mappings do WireMock são configuração de um sistema externo simulado, não dados persistentes da aplicação. Os cenários
do Postman poderão criar Stock Items fictícios e sem dados pessoais para acionar sucesso, rejeição e timeout.

## Segurança e operação

### Validação e abuso

- limitar requests a 100 linhas e 100 demandas evita payloads desproporcionais;
- validar UUIDs, nulos, quantidade positiva, overflow e pertencimento item-demanda antes do efeito externo;
- não aceitar status, snapshots, referência externa, Supplier, preço ou IDs internos de submissão no body;
- impedir mass assignment com records específicos de request;
- exigir `Idempotency-Key` UUID em toda criação;
- fixar URL e path do fornecedor em configuração, sem redirecionamento controlado pelo cliente;
- aplicar timeouts curtos para não reter threads indefinidamente;
- nunca logar API key, body completo, headers, Customer, Vehicle, Estimate ou stack trace em resposta;
- logs operacionais podem conter IDs locais, outcome e duração, sem payload ou credencial.

### Autorização

Com a capability de Identity integrada, `GET /api/purchase-demands`, `POST /api/purchase-orders` e
`GET /api/purchase-orders/{id}` exigem JWT com papel `MANAGER` ou `ADMIN`. Os testes HTTP devem provar acesso
`MANAGER`, rejeição dos demais papéis e `401` sem autenticação. A autorização permanece centralizada no
`SecurityConfig`; nenhum papel é aceito por header ou campo controlado pelo cliente.

O simulador não produz efeito financeiro real. Credenciais ou endpoint de fornecedor real continuam fora de escopo.

### Recuperação e observabilidade

- `503 EXTERNAL_SUPPLIER_UNAVAILABLE` orienta retry com o mesmo header e body;
- o Postman preservará a idempotency key para repetir o cenário;
- `PENDING_SUBMISSION` é recuperável por retry, sem job automático;
- `REJECTED` preserva somente código sanitizado e libera demandas;
- métricas, circuit breaker, fila, retry agendado e painel de reconciliação ficam fora do MVP;
- secrets reais, se futuros, virão apenas de ambiente e não dos mappings ou repositório.

## OpenAPI, Postman e README

Na mesma implementação:

- anotar operações, headers, filtros, schemas, exemplos e respostas com Springdoc;
- ampliar `OpenApiContractTest` para os três endpoints e códigos relevantes;
- preservar a pasta e variáveis existentes de Purchase Orders na coleção Postman;
- atualizar a listagem para validar `updatedAt` e a demanda criada já no Diagnosis;
- preservar criação ad hoc, consulta, rejeição e retry idempotente;
- atualizar o README com a nova ordem executável Diagnosis -> demanda -> Estimate -> revalidação;
- esclarecer que RF27 termina em `OPEN` e ainda não recebe materiais nem libera `AWAITING_ITEMS`.

O OpenAPI gerado continua sendo a fonte de verdade; nenhum YAML manual será criado.

## Estratégia de testes

### Domínio

- criação, reconstituição e invariantes de Purchase Demand e Purchase Order;
- transições de claim, release, ordered, resolved, open e reject;
- idempotência e conflitos de transição;
- consolidação, ordenação, overflow e snapshots de Purchase Line.

### Aplicação

- avaliação antecipada suficiente não cria nem resolve demanda;
- avaliação antecipada insuficiente cria ou atualiza uma única demanda com a diferença correta;
- lote com item inexistente/inativo retorna issues ordenadas e não persiste parcialmente;
- revalidação na Estimate atualiza `updatedAt` sem mudar `createdAt` ou identidade;
- rejeição/expiração da Estimate não chama `resolve`;
- evento insuficiente cria e atualiza uma única demanda com a diferença correta;
- issues inexistente/inativo não criam demanda;
- evento de reserva criada resolve apenas demanda `OPEN`;
- API `LOW_STOCK` valida item, ocorrência e quantidades sem controller;
- listagem combina filtros e ordenação;
- criação ad hoc, por demanda e mista;
- mínimo sugerido, item inexistente/inativo, demanda inválida e conjunto vazio;
- repetição com mesmo hash, conflito com hash diferente e rejeição já conhecida;
- timeout preserva submissão/claims e retry confirma a mesma ordem;
- falha entre aceite externo e persistência é recuperada com a mesma referência.

### HTTP e integração externa

- MockMvc cobre requests, headers, filtros, status, DTOs, `Location` e `ErrorResponse`;
- WireMock cobre serialização, ACL, aceitação, rejeição, `5xx`, timeout e resposta malformada;
- nenhum teste de controller chama rede real;
- o adapter prova envio do mesmo `Idempotency-Key` e ausência de dados de Service Lifecycle.

### Persistência e concorrência

- migration parte de schema vazio com Hibernate `validate`;
- repositories cobrem round-trip, snapshots, filtros e locks;
- avaliação e preparação concorrentes respeitam Stock Item -> Purchase Demand sem deadlock;
- Diagnosis e Estimate concorrentes convergem para a unique key da mesma demanda;
- duas criações concorrentes com a mesma demanda produzem uma claim e um conflito estável;
- duas chamadas concorrentes com a mesma idempotency key convergem para uma ordem;
- hash diferente não chama o gateway;
- confirmação e rejeição atualizam ordem e todas as demandas atomicamente;
- constraints são traduzidas sem expor SQL.

Os testes seguirão o baseline H2 em modo MySQL. A evidência manual do plano deverá também executar o fluxo no MySQL do
Docker Compose, especialmente locks, unique constraints e recuperação após timeout.

### Módulos e qualidade

- `@ApplicationModuleTest` cobre Diagnosis/Estimate API -> Purchase Demand e Stock Reservation event -> demanda;
- `ModuleStructureTest` confirma fronteiras e ausência de novo módulo;
- busca de imports confirma que Stock & Procurement não depende de packages de Service Lifecycle;
- `make test` durante os checkpoints;
- `make verify` e revisão do relatório JaCoCo antes da conclusão;
- cobertura do código alterado não reduz a meta de 80%.

## Impacto em documentação e features futuras

- `docs/features/stockprocurement/README.md` será atualizado com os contratos técnicos aprovados;
- `perform-diagnosis` e `estimate-generation` consumirão `RepairStockAssessmentApi` pela named interface;
- RF30 consumirá `PurchaseDemandApi`, mas continuará dona de mínimo, alvo e ocorrência;
- RF28 adicionará a transição de `OPEN` para fechamento sem alterar a composição da ordem;
- RF29 será dona da movimentação de entrada e do retry priorizado das Service Executions;
- nenhuma spec de RF28–RF30 é aprovada ou criada por este documento.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por humano em 2026-08-25.
- [ ] Implementation Plan revisado somente depois da aprovação técnica.
- [ ] Segurança, contratos, migrations consumidoras, Modulith, testes e documentação verificados no plano futuro.

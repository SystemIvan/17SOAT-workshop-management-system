# Especificação Técnica: Identificação de Stock Items em Nível Baixo

| Campo | Valor |
|---|---|
| Feature | `low-stock-detection` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta especificação deriva da funcional aprovada. Nenhum `implementation-plan.md`, código, migration, contrato HTTP ou
listener de RF30 pode ser criado antes da aprovação humana explícita deste documento.

RF30 será implementada em frente separada, preferencialmente depois de RF28/RF29. O desenho pode reaproveitar o evento
real de reposição de RF29, mas não altera a responsabilidade do recebimento.

## Objetivo técnico

Evoluir Stock & Procurement para:

- configurar política opcional de mínimo e alvo por Stock Item;
- calcular baixo estoque com `availableQuantity < minimumQuantity`;
- persistir uma única ocorrência aberta por item;
- publicar e reconciliar Purchase Demand `LOW_STOCK` pelo contrato preparado em RF27;
- reavaliar depois de configuração, reserva, desativação e recebimento;
- encerrar ocorrência e resolver demanda ainda `OPEN` quando a condição termina;
- iniciar novo ciclo depois do recebimento de uma demanda já comprada, se o saldo continuar baixo;
- expor estado operacional e filtro sem efeitos em leitura;
- sinalizar o Stock Manager uma vez por ocorrência, em melhor esforço.

RF30 não cria, fecha ou recebe Purchase Order e não impede reserva quando o saldo atinge o mínimo.

## Diagnóstico do baseline

O baseline já fornece:

- `StockItem` com disponibilidade inteira, estado ativo e locks pessimistas individual/em lote;
- `ReserveStockItemsUseCase`, único fluxo atual que reduz `availableQuantity`;
- RF29 proposta para aumentar disponibilidade e publicar `StockItemsRestockedEvent` depois do commit;
- `PurchaseDemand` com origem `LOW_STOCK`, equivalência por `origin + originReferenceId + stockItemId` e estados
  `OPEN`, `CLAIMED`, `ORDERED`, `RESOLVED`;
- `PurchaseDemandApi.recordLowStock(...)`, named interface e provider transacional;
- listagem de Purchase Demands `OPEN` e criação manual de Purchase Order;
- consulta/cadastro de Stock Item com filtros cumulativos;
- porta e adapter de notificação do Stock Manager específicos de Stock Reservation;
- JWT e autorização `MANAGER`/`ADMIN` para `/api/stock-items/**`.

Ainda não existem mínimo, alvo, ocorrência, filtro de baixo estoque, resolução pública da demanda `LOW_STOCK` ou reação
à mudança de disponibilidade.

## Contexto e fronteiras

### Stock & Procurement

`LowStockPolicy` passa a compor `StockItem`, porque mínimo e alvo governam o comportamento desse item. O histórico e o
ciclo idempotente ficam em um aggregate separado `LowStockOccurrence`, sob `stockprocurement.lowstock`.

`PurchaseDemand` continua pertencendo a `stockprocurement.purchaseorder`. A integração usa a named interface
`purchase-demand-api`; o detector não importa domínio, repository ou JPA de Purchase Order.

Não será criada entidade `Stock` com ID, scheduler, Supplier ou módulo Notifications.

### Service Lifecycle

RF30 não importa nem publica dados de Service Lifecycle. Demandas `LOW_STOCK` usam o occurrence ID como origem e nunca
recebem `serviceOrderId` ou `serviceExecutionId`.

### Spring Modulith

Todo o delta permanece dentro do módulo `stockprocurement`. O consumo de `StockItemsRestockedEvent` é interno ao mesmo
módulo; o evento continua exposto porque RF29 já possui consumidor real em Service Lifecycle.

`ModuleStructureTest` deve permanecer verde e nenhum novo módulo direto será criado.

## Modelo de domínio

### Value object `LowStockPolicy`

```java
public record LowStockPolicy(Quantity minimumQuantity, Quantity targetQuantity) {
}
```

Invariantes:

- mínimo inteiro e não negativo;
- alvo inteiro e positivo;
- alvo estritamente maior que o mínimo;
- ambos presentes ou política ausente;
- cálculo usa `Math.subtractExact(target, available)` e só é chamado em condição baixa.

Comportamentos:

```java
boolean isLow(Quantity availableQuantity)
Quantity suggestedPurchase(Quantity availableQuantity)
```

`isLow` usa comparação estrita. Disponibilidade igual ao mínimo é `NORMAL`.

### Alterações em `StockItem`

Adicionar `LowStockPolicy lowStockPolicy`, nullable, ao factory, reconstituição e persistência.

Novos comportamentos:

```java
void configureLowStockPolicy(LowStockPolicy policy)
void disableLowStockPolicy()
LowStockAssessment assessLowStock()
```

Regras:

- somente item ativo aceita configuração/alteração;
- desabilitar sem política é idempotente;
- desativação remove a política vigente depois que o caso de uso encerra a ocorrência;
- reserva e recebimento não modificam a política;
- `LowStockAssessment` retorna `NOT_CONFIGURED`, `NORMAL` ou `LOW` e a sugestão quando aplicável.

O domínio não cria ocorrência, demanda ou notificação. Esses efeitos são coordenados pela aplicação.

### Aggregate root `LowStockOccurrence`

| Atributo | Tipo | Regra |
|---|---|---|
| `id` | `UUID` | Origem estável da demanda enquanto o ciclo está aberto |
| `stockItemId` | `UUID` | Obrigatório e imutável |
| `purchaseDemandId` | `UUID` | Demanda criada para esta ocorrência, imutável |
| `status` | `LowStockOccurrenceStatus` | `OPEN` ou `CLOSED` |
| `observedAvailableQuantity` | `int` | Último saldo observado, não negativo |
| `suggestedQuantity` | `int` | Última sugestão positiva |
| `detectedAt` | `Instant` | Primeira detecção |
| `updatedAt` | `Instant` | Última avaliação material |
| `closedAt` | `Instant?` | Obrigatório somente em `CLOSED` |
| `closureReason` | `LowStockClosureReason?` | Obrigatório somente em `CLOSED` |

Motivos de fechamento:

```text
STOCK_RECOVERED
POLICY_DISABLED
STOCK_ITEM_DEACTIVATED
REPLENISHMENT_CYCLE_COMPLETED
```

Comportamentos:

- `open(...)` cria `OPEN` com demanda, observação e sugestão;
- `updateObservation(...)` mantém identidade e timestamps monotônicos;
- `close(reason, at)` é idempotente e terminal;
- ocorrência `CLOSED` nunca reabre;
- novo ciclo sempre cria outro UUID e outra Purchase Demand.

Não haverá endpoint de edição ou exclusão de ocorrência.

## Evolução de `PurchaseDemandApi`

O método existente passa a retornar a identidade efetiva:

```java
LowStockPurchaseDemandResult recordLowStock(LowStockPurchaseDemandCommand command);

void resolveLowStock(LowStockPurchaseDemandResolutionCommand command);
```

Tipos públicos:

```text
LowStockPurchaseDemandResult(UUID purchaseDemandId, PurchaseDemandStatusView status)
LowStockPurchaseDemandResolutionCommand(UUID occurrenceId, UUID stockItemId, Instant resolvedAt)
```

O enum de visão expõe somente o necessário para a coordenação e não exporta o aggregate.

Provider:

- participa da transação chamadora;
- valida Stock Item/IDs/quantidades;
- cria ou atualiza a demanda equivalente `OPEN`;
- se a demanda estiver `CLAIMED`, `ORDERED` ou `RESOLVED`, preserva seu estado e conteúdo;
- retorna sempre o ID equivalente;
- `resolveLowStock` resolve somente demanda `OPEN`; outros estados permanecem históricos;
- usa lock e unique key existentes para reconciliar concorrência.

Não existe endpoint para criar/resolver demanda manualmente.

### Reconciliação durante uma claim de RF27

Uma condição pode deixar de estar baixa enquanto sua demanda está `CLAIMED` por uma Purchase Order em submissão. O
detector fecha a ocorrência, mas não rompe a claim nem interfere na chamada externa:

- se o fornecedor aceitar, RF27 conclui `ORDERED` normalmente e preserva o histórico;
- se o fornecedor rejeitar e RF27 liberar a demanda para `OPEN`, o mesmo caso de uso consulta a ocorrência pelo
  `purchaseDemandId`;
- quando a ocorrência já estiver `CLOSED`, a demanda recém-liberada transiciona imediatamente para `RESOLVED` na mesma
  transação;
- quando a ocorrência continuar `OPEN`, a demanda volta a ser selecionável como hoje.

Essa reconciliação evita uma demanda obsoleta depois da rejeição externa sem cancelar uma submissão em andamento. Será
implementada por um serviço interno pequeno de `lowstock` chamado pelo fluxo de release; não cria dependência entre
módulos nem altera o contrato HTTP de RF27.

## Avaliação e ciclo de aplicação

### `EvaluateLowStockUseCase`

Serviço interno que recebe um Stock Item já bloqueado, instante e contexto de avaliação. Para política configurada:

1. carrega a ocorrência `OPEN` do item com lock;
2. avalia a disponibilidade atual;
3. se normal e existe ocorrência, fecha-a e solicita resolução da demanda ainda `OPEN`;
4. se baixa e não existe ocorrência, gera occurrence ID, registra demanda, cria ocorrência e publica evento de alerta;
5. se baixa e existe ocorrência, atualiza observação/sugestão e a mesma demanda quando ela ainda for `OPEN`;
6. salva somente mudanças materiais.

Quando política não existe ou item está inativo, fecha eventual ocorrência com o motivo correspondente e resolve apenas
demanda `OPEN`.

Todos os timestamps usam um único `Clock` UTC por avaliação, truncado em microssegundos.

### Configuração da política

`ConfigureLowStockPolicyUseCase`, público e `@Transactional`:

1. carrega Stock Item com lock;
2. valida item ativo e cria o value object;
3. configura e salva o item;
4. avalia imediatamente na mesma transação;
5. retorna a visão completa.

`DisableLowStockPolicyUseCase`, também transacional, fecha a ocorrência/demanda `OPEN` antes de remover a política. A
repetição sem política retorna sucesso sem novo efeito.

### Criação e desativação de Stock Item

`CreateStockItemUseCase` aceita política opcional e avalia depois de persistir o novo item. O request antigo sem
política continua válido.

`DeactivateStockItemUseCase` passa a:

1. bloquear o item;
2. encerrar ocorrência e resolver demanda ainda `OPEN` com `STOCK_ITEM_DEACTIVATED`;
3. desativar item e remover política;
4. persistir tudo na mesma transação.

### Reserva

Depois de aplicar todos os descontos, mas antes do commit, `ReserveStockItemsUseCase` chama o detector para cada Stock
Item alterado, em ordem de UUID. Reserva, novos saldos, ocorrência e demanda confirmam ou sofrem rollback juntos.

Uma reserva que não altera saldo não reavalia. O consumo de uma reserva também não reavalia porque já não modifica
`availableQuantity`.

### Recebimento de RF29

`RestockedLowStockReevaluationListener` observa `StockItemsRestockedEvent` depois do commit. Em nova transação:

1. carrega Receipt e Purchase Order pelos IDs confiáveis do evento;
2. obtém as Purchase Demands selecionadas naquela ordem;
3. bloqueia os Stock Items recebidos em ordem de UUID;
4. para cada item, localiza a ocorrência aberta;
5. se o `purchaseDemandId` da ocorrência foi atendido pela ordem recebida, fecha o ciclo com
   `REPLENISHMENT_CYCLE_COMPLETED` antes de avaliar o saldo;
6. se o saldo continuar baixo, abre uma nova ocorrência e demanda com novo ID;
7. se o Receipt for ad hoc ou não atender a ocorrência atual, apenas reavalia a mesma condição.

Falha deste listener não desfaz o Receipt. Repetir o POST idempotente de RF29 republica o evento e recupera a avaliação.
O listener é idempotente: reprocessar o mesmo Receipt não fecha a nova ocorrência criada por ele, pois a nova demanda
não pertence à Purchase Order já recebida.

## Evento e sinalização de baixo estoque

Ao criar uma ocorrência, publicar dentro da transação:

```java
record LowStockDetectedEvent(
        UUID occurrenceId,
        UUID stockItemId,
        int observedAvailableQuantity,
        int minimumQuantity,
        int targetQuantity,
        int suggestedQuantity,
        Instant detectedAt) {
}
```

Um listener `AFTER_COMMIT` chama `LowStockNotificationPort`, pertencente a `stockprocurement.lowstock`. O adapter do MVP
registra uma sinalização operacional sem dados pessoais. Exceções do port são capturadas e logadas; não há retry ou
histórico de entrega.

Somente a criação publica o evento. Atualização da mesma ocorrência, leitura e replay não multiplicam sinalizações.

Não será estendido o port localizado em `stockreservation`, pois isso misturaria casos de uso diferentes em uma
abstração cujo nome e ownership são específicos de reserva.

## Repositories e concorrência

Criar `LowStockOccurrenceRepository`:

- `findById(UUID)`;
- `findOpenByStockItemIdForUpdate(UUID)`;
- `save(LowStockOccurrence)`.

Toda avaliação bloqueia primeiro o Stock Item e depois a ocorrência/demanda. Criação de Purchase Order já bloqueia
Stock Item e demanda nessa ordem, preservando a disciplina do bounded context.

A proteção combina:

- lock do Stock Item como serializador por item;
- coluna técnica `open_slot = 1` somente em ocorrência `OPEN`;
- unique key `(stock_item_id, open_slot)`; ocorrências `CLOSED` usam `NULL` e preservam histórico;
- unique key existente da Purchase Demand por origem/referência/item.

Uma violação concorrente é reconciliada lendo a ocorrência/demanda vencedora. Nenhuma leitura simples executa detector.

## Contratos HTTP e OpenAPI

### Criar Stock Item com política opcional

`POST /api/stock-items` mantém todos os campos atuais e adiciona:

```json
{
  "lowStockPolicy": {
    "minimumQuantity": 5,
    "targetQuantity": 12
  }
}
```

`lowStockPolicy` ausente preserva compatibilidade e cria item sem detecção. Quando presente, ambos os campos são
obrigatórios e a condição é avaliada na criação.

### Configurar ou desabilitar

```http
PUT /api/stock-items/{stockItemId}/low-stock-policy
Content-Type: application/json

{
  "minimumQuantity": 5,
  "targetQuantity": 12
}
```

Retorna `200 StockItemResponse`.

```http
DELETE /api/stock-items/{stockItemId}/low-stock-policy
```

Retorna `204`, inclusive em repetição idempotente.

### Resposta e listagem

Adicionar a `StockItemResponse`:

```text
lowStockPolicy: { minimumQuantity, targetQuantity } | null
lowStockStatus: NOT_CONFIGURED | NORMAL | LOW
lowStockOccurrenceId: UUID | null
suggestedPurchaseQuantity: Integer | null
```

Occurrence e sugestão são presentes somente em `LOW`. A composição consulta ocorrência aberta em lote para evitar N+1.

Adicionar filtro:

```http
GET /api/stock-items?lowStock=true
GET /api/stock-items?lowStock=false
```

- `true`: política configurada e `availableQuantity < minimumQuantity`;
- `false`: política configurada e saldo normal;
- itens sem política não aparecem em nenhum dos dois filtros;
- ausência preserva os filtros atuais e inclui itens conforme `active`;
- o filtro combina com texto, tipo, disponibilidade e estado usando `AND`;
- leitura não cria ou atualiza ocorrência.

### Falhas HTTP

| Situação | HTTP | Código estável |
|---|---:|---|
| Contrato ausente/malformado | `400` | `VALIDATION_ERROR` |
| Relação mínimo/alvo inválida | `400` | `INVALID_LOW_STOCK_POLICY` |
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| Stock Item inativo | `409` | `STOCK_ITEM_INACTIVE` |
| Sem token / sem papel | `401` / `403` | `UNAUTHORIZED` / `FORBIDDEN` |

Os endpoints permanecem cobertos pelo matcher `/api/stock-items/**` para `MANAGER`/`ADMIN`. OpenAPI, MockMvc, Postman e
README serão atualizados no mesmo checkpoint de implementação.

## Persistência e migration

Classificação: **nenhum seed necessário**. Políticas são dados operacionais configurados pelo Stock Manager, não
referência obrigatória ou demo automática.

Uma migration Flyway nova, com timestamp UTC gerado na implementação, deverá:

### Alterar `stock_items`

- adicionar `minimum_quantity INTEGER NULL`;
- adicionar `target_quantity INTEGER NULL`;
- adicionar check que exige ambos nulos ou ambos presentes com mínimo não negativo e alvo maior que mínimo;
- adicionar índice de leitura `(active, minimum_quantity, available_quantity)`.

Itens existentes ficam com ambas as colunas nulas. Não há backfill nem detecção automática no deploy.

### Criar `low_stock_occurrences`

| Coluna | Tipo | Restrição |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `stock_item_id` | `BINARY(16)` | not null, FK |
| `purchase_demand_id` | `BINARY(16)` | not null, FK e unique |
| `status` | `VARCHAR(16)` | `OPEN` ou `CLOSED` |
| `open_slot` | `TINYINT` nullable | `1` somente em `OPEN`; `NULL` em `CLOSED` |
| `observed_available_quantity` | `INTEGER` | não negativa |
| `suggested_quantity` | `INTEGER` | positiva |
| `detected_at` | `TIMESTAMP(6)` | not null |
| `updated_at` | `TIMESTAMP(6)` | not null |
| `closed_at` | `TIMESTAMP(6)` nullable | presente somente em `CLOSED` |
| `closure_reason` | `VARCHAR(48)` nullable | presente somente em `CLOSED` |

Adicionar unique `(stock_item_id, open_slot)`, checks de estado/quantidade/timeline e índice
`(stock_item_id, detected_at, id)`.

A migration anterior não será alterada. Flyway continua único mecanismo, Hibernate usa `validate` e testes cobrem H2
em modo MySQL e MySQL do Docker Compose.

## Segurança e operação

### Revisão de segurança preliminar

| Item | Avaliação técnica |
|---|---|
| Validação | Bean Validation na borda e invariantes repetidas no value object |
| Mass assignment | Cliente informa apenas mínimo/alvo; saldo, status, IDs e sugestão são calculados |
| Autorização | Configuração/consulta restritas a `MANAGER`/`ADMIN` |
| Exposição | Somente dados de inventário; sem Customer, Vehicle, Estimate ou Technician |
| Concorrência | Lock por Stock Item e duas unique keys impedem duplicidade |
| Persistência | Checks, FKs, soma/subtração exata e migrations imutáveis |
| Logs | IDs e quantidades operacionais; sem JWT, e-mail, preço ou payload completo |
| Notificação | Porta consumer-owned, melhor esforço e sem novo bounded context |
| Dependências | Nenhuma biblioteca externa nova |
| Abuso | Leituras não geram efeitos; updates repetidos convergem para a mesma ocorrência |

### Operação e recuperação

A detecção de configuração/reserva é transacional. A reavaliação depois de Receipt é after-commit para não desfazer
RF29; replay do Receipt recupera falha ou queda antes do listener.

Não existe scheduler para reconciliar itens arbitrariamente. Operação que suspeite perda do evento pode repetir o POST
idempotente do Receipt ou alterar/reaplicar a política; uma futura reconciliação em lote exige SDD próprio.

Depois que políticas forem gravadas, rollback para binário que não mapeia as colunas é possível apenas se Hibernate
ignorar colunas extras, mas ocorrências/demandas podem ficar sem manutenção. O caminho suportado é roll-forward.

## Estratégia de testes

### Domínio

- policy válida/inválida e comparação estrita;
- cálculo exato da sugestão;
- configuração/desabilitação e item inativo;
- abertura, atualização, fechamento, replay e invariantes da ocorrência.

### Aplicação

- configuração sobre saldo normal/baixo;
- criação com/sem política;
- reserva cruza o mínimo e cria uma ocorrência;
- nova redução atualiza a mesma ocorrência/demanda `OPEN`;
- igualdade com mínimo permanece `NORMAL`;
- desabilitação, recuperação de saldo e desativação fecham ocorrência;
- demanda `ORDERED` não é reaberta/duplicada;
- Receipt do ciclo fecha e abre novo ID se ainda baixo;
- Receipt ad hoc atualiza/encerra sem fechar ciclo comprado incorreto;
- replay do evento é idempotente;
- falha de alerta não reverte negócio.

### Persistência e concorrência

- round-trip de policy e occurrence;
- unique de uma ocorrência aberta e equivalência da demanda;
- duas reservas/configurações concorrentes não duplicam ciclo;
- policy/occurrence/demand sofrem rollback juntas quando aplicável;
- migration em schema vazio e baseline existente sem policy;
- Hibernate `validate`, H2/MySQL mode e MySQL Docker.

### HTTP, segurança e documentação

- POST antigo sem `lowStockPolicy` continua válido;
- POST/PUT com policy válida e combinações inválidas;
- DELETE idempotente;
- response `NOT_CONFIGURED`, `NORMAL` e `LOW`;
- filtro true/false combinado com filtros existentes;
- consulta pura sem side effect;
- `400`, `401`, `403`, `404`, `409` e códigos estáveis;
- OpenAPI, Postman e README atualizados em conjunto.

### Módulos e regressão

- `@ApplicationModuleTest` cobre PurchaseDemandApi e evento de RF29;
- `ModuleStructureTest` permanece verde;
- criação de Purchase Order continua manual;
- reserva mantém tudo-ou-nada e consumo não desconta novamente;
- `make test`, `make verify` e revisão de cobertura mínima de 80%.

## Impacto em documentos e contratos existentes

- `stock-domain-foundation`: amplia o cadastro somente com policy opcional, preservando requests existentes;
- `stock-item-reservation`: adiciona reação ao saldo confirmado sem alterar sua atomicidade;
- `purchase-order-creation`: concretiza o consumidor real do `PurchaseDemandApi` já preparado;
- `stock-receiving-and-restocking`: adiciona o listener de reavaliação sem permitir rollback do Receipt;
- `docs/features/stockprocurement/README.md`, OpenAPI, Postman e README principal serão atualizados na implementação.

Nenhuma especificação aprovada anterior se torna materialmente inválida: todas registravam RF30 como extensão futura e
mantinham mínimo/detecção fora do escopo da entrega anterior.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por Matheus Apostulo em 2026-08-25.
- [x] Contratos técnicos de RF28/RF29 aprovados antes do desenho do listener de Receipt em 2026-08-25.
- [x] Implementation Plan criado após aprovação técnica em 2026-08-25.
- [ ] Segurança, contratos, migration, eventos, Modulith, testes e documentação verificados no plano futuro.

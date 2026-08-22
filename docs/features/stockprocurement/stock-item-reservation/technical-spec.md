# Especificação Técnica: Reserva Atômica de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-item-reservation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-20) |

## Gate de aprovação

Esta especificação descreve a implementação proposta para a funcional aprovada. Nenhum
`implementation-plan.md` pode ser criado e nenhuma alteração de código, schema ou contrato pode começar antes da
aprovação humana explícita deste documento.

As especificações aprovadas de Service Lifecycle listadas em "Impacto sobre especificações existentes" também
precisam ser revisadas conforme o SDD antes da implementação dos comportamentos que foram materialmente alterados.

## Objetivo técnico

Introduzir `StockReservation` como aggregate root em Stock & Procurement e integrar sua criação atômica à aprovação
de cada Service Execution. A solução deve:

- comprometer todos os Stock Items de uma execução ou nenhum;
- impedir saldo negativo em solicitações concorrentes;
- manter no máximo uma Stock Reservation por `serviceExecutionId`;
- preservar a aprovação comercial quando não houver disponibilidade;
- associar um `reservationId` estável à Service Execution quando a reserva for criada;
- congelar os Stock Requirements quando a Estimate for gerada;
- substituir `AWAITING_PART` por `AWAITING_ITEMS` em domínio, persistência e contratos;
- permitir consulta e consumo integral da reserva;
- produzir resultados suficientes para as notificações consumidoras sem criar um bounded context de Notification.

Não serão implementados `RELEASED`, cancelamento, timeout, liberação de saldo, reserva parcial, Purchase Order,
estoque mínimo ou movimentações administrativas.

## Diagnóstico do código atual

O código recebido em `dev` já fornece a base de Service Lifecycle, mas ainda representa reserva como flags isoladas:

- `ServiceExecution` contém vários `StockRequirement`, cada um com `reserved`;
- `ServiceOrder.applyStockReservation(serviceExecutionId, stockItemId)` confirma um item por chamada e não possui
  identidade de reserva;
- `ServiceExecution.recomputeReadiness()` considera os flags individuais e usa `AWAITING_PART`;
- `attachStockRequirement` aceita alterações depois da geração da Estimate;
- `GenerateEstimateUseCase` cria o snapshot, mas não congela nem salva esse estado na Service Order;
- `DecideEstimateLinesUseCase` autoriza as execuções, mas não solicita reserva em Stock & Procurement;
- `StockItem.availableQuantity` é imutável no domínio e o repository não oferece leitura com lock;
- `stock_items` não possui proteção contra atualização concorrente;
- não existem aggregate, tabela, repository ou endpoint de Stock Reservation.

Os campos comerciais de `StockRequirement` ainda podem ser enviados pelo cliente. A correção completa desse
contrato permanece em BL-004; a reserva desta feature nunca confiará em nome, tipo ou preço recebidos de Service
Lifecycle.

## Contextos e fronteiras

### Stock & Procurement

`StockReservation` será criada em `stockprocurement.stockreservation`, com separação entre `domain`, `application` e
`infrastructure`. Ela será aggregate root independente de `StockItem` e conterá suas linhas como value objects.

O caso de uso de reserva coordenará vários aggregates `StockItem` e uma `StockReservation` na mesma transação. Não
será criado aggregate ou repository chamado `Stock`.

### Service Lifecycle

`ServiceOrder` continua dona de `ServiceExecution` e `StockRequirement`. Service Lifecycle decide quando uma execução
está autorizada e envia somente `serviceExecutionId`, `stockItemId` e quantidade ao contrato público de Stock &
Procurement.

Service Lifecycle persistirá apenas `stockReservationId` na Service Execution. Linhas, estado e timestamps da reserva
permanecem exclusivamente em Stock & Procurement.

### Dependência entre módulos

A dependência será unidirecional:

```text
servicelifecycle
    └──> stockprocurement :: stock-reservation-api
```

O pacote `stockprocurement.stockreservation.application.api` será exposto por
`@NamedInterface("stock-reservation-api")`. Somente interface, commands e results imutáveis desse pacote poderão ser
importados por Service Lifecycle. Domínio, repositories, JPA, controllers e exceptions internas não atravessarão a
fronteira.

Stock & Procurement não importará Service Lifecycle. `serviceExecutionId` é uma referência opaca e não terá foreign key
para `service_executions`.

## Modelo de domínio de Stock & Procurement

### Aggregate root `StockReservation`

| Atributo | Tipo proposto | Regra |
|---|---|---|
| `id` | `UUID` | Gerado na criação e imutável |
| `serviceExecutionId` | `UUID` | Obrigatório, imutável e único |
| `lines` | `List<StockReservationLine>` | Não vazia, imutável e sem item repetido |
| `status` | `StockReservationStatus` | `ACTIVE` ou `CONSUMED` |
| `createdAt` | `Instant` | UTC e imutável |
| `consumedAt` | `Instant?` | Preenchido somente na primeira transição para `CONSUMED` |

Comportamentos:

- `create(serviceExecutionId, lines, createdAt)` cria uma reserva `ACTIVE` depois que a aplicação confirmou todos os
  itens;
- `consume(consumedAt)` muda `ACTIVE` para `CONSUMED`;
- consumir novamente retorna o mesmo estado e preserva o primeiro `consumedAt`;
- uma reserva `CONSUMED` nunca volta a `ACTIVE`;
- linhas não podem ser adicionadas, removidas ou alteradas depois da criação;
- `reconstitute(...)` restaura o estado persistido sem gerar nova identidade.

O domínio não conhecerá preço, SKU, nome, tipo, Service Order, Estimate, JPA ou HTTP.

### Value object `StockReservationLine`

Cada linha contém `stockItemId` obrigatório e quantidade inteira positiva. A aplicação consolida linhas repetidas por
`stockItemId` com soma exata antes de adquirir locks. Overflow de inteiro ou quantidade não positiva é entrada inválida
e não altera saldo.

### Alteração no aggregate `StockItem`

`availableQuantity` deixará de ser final. `assessReservation(Quantity requested)` avaliará a solicitação sem mutar o
item e `reserve(Quantity requested)` aplicará o desconto somente depois que o conjunto inteiro for considerado elegível:

- rejeitar item inativo;
- rejeitar quantidade maior que a disponibilidade;
- subtrair somente quando a quantidade for positiva e integralmente atendida;
- nunca permitir resultado negativo.

O resultado da avaliação distinguirá item inativo de quantidade insuficiente. Item inexistente é identificado pelo
repository. Essa fase não mutável permite coletar todos os issues de uma execução antes de descontar sua primeira linha.

O consumo não volta a alterar `StockItem`: a disponibilidade já foi reduzida na criação da reserva. Atualização
cadastral e desativação passarão a usar a mesma disciplina de lock das reservas para não sobrescrever saldo concorrente.

## Alterações no modelo de Service Lifecycle

### `ServiceExecution`

Serão adicionados:

| Atributo | Tipo | Regra |
|---|---|---|
| `stockRequirementsFrozen` | `boolean` | Inicia `false` e muda uma vez para `true` na geração da Estimate |
| `stockReservationId` | `UUID?` | Nulo antes do sucesso; estável depois da associação |

Comportamentos propostos:

- `freezeStockRequirements()` é idempotente e impede qualquer novo `attachStockRequirement`;
- `attachStockRequirement(...)` exige status `PENDING` e conjunto ainda não congelado;
- `authorize(estimateId)` leva execução com requirements para `AWAITING_ITEMS` e execução sem requirements para
  `READY`;
- `confirmStockReservation(reservationId)` exige execução autorizada em `AWAITING_ITEMS`, associa o ID e muda todas as
  marcações de compatibilidade para reservado na mesma chamada;
- antes da guarda de estado, repetir a confirmação com o mesmo ID é sucesso sem novo efeito;
- confirmar ID diferente depois de uma associação é conflito;
- `start()` continua exigindo `READY`; separação e retirada demoradas não alteram esse status.

`ServiceOrder.applyStockReservation(serviceExecutionId, stockItemId)` e
`ServiceExecution.markStockAsReserved(stockItemId)` serão removidos. O novo método do aggregate confirma o conjunto
por `reservationId`, sem aplicação item a item.

O campo `reserved` de `StockRequirement` será mantido temporariamente no contrato e na persistência para
compatibilidade. Ele mudará para `true` em todas as linhas somente depois da confirmação da reserva e nunca será usado
para recalcular prontidão. Permanecerá `true` depois do consumo como registro de que o requirement foi atendido. A fonte
de verdade da reserva é `stockReservationId` e o aggregate de Stock & Procurement.

### Congelamento na geração da Estimate

`GenerateEstimateUseCase` continuará criando os snapshots comerciais, mas, dentro da mesma transação:

1. carregará a Service Order com lock de escrita;
2. validará e criará a Estimate;
3. chamará `serviceOrder.freezeStockRequirements(diagnosisId)` para todas as execuções daquele Diagnosis;
4. persistirá Estimate e Service Order;
5. somente então concluirá a operação.

`AttachStockRequirementUseCase` também carregará a Service Order com lock de escrita. Assim, geração e anexo
concorrentes são serializados e o anexo não pode atravessar o instante de congelamento.

### Renomeação de status

`AWAITING_PART` será substituído por `AWAITING_ITEMS` em `ServiceExecutionStatus`, `ServiceOrderStatus`, regras de
precedência, DTOs, OpenAPI, Postman, testes e documentação. Não haverá alias no contrato JSON; a migration converterá
os valores persistidos antes da validação Hibernate.

## Contrato público entre os módulos

A named interface oferecerá uma operação de lote para que uma decisão de Estimate com várias linhas adquira todos os
locks de Stock Item em ordem global:

```java
public interface StockReservationApi {
    List<ReserveStockItemsResult> reserveAll(List<ReserveStockItemsCommand> commands);
}
```

Os tipos públicos serão equivalentes a:

```java
record ReserveStockItemsCommand(
        UUID serviceExecutionId,
        List<ReserveStockItem> items) {
}

record ReserveStockItem(UUID stockItemId, int quantity) {
}

record ReserveStockItemsResult(
        UUID serviceExecutionId,
        ReservationAttemptOutcome outcome,
        UUID reservationId,
        boolean newlyCreated,
        List<StockReservationIssue> issues) {
}
```

`ReservationAttemptOutcome` terá `RESERVED` e `NOT_RESERVED`. Cada issue conterá `stockItemId`, quantidade solicitada,
quantidade disponível observada quando existir e um motivo entre `STOCK_ITEM_NOT_FOUND`, `STOCK_ITEM_INACTIVE` e
`INSUFFICIENT_QUANTITY`.

`newlyCreated` permite que os consumidores diferenciem a primeira confirmação de uma repetição idempotente. Somente a
primeira criação produz notificação de separação ou retirada.

O contrato não recebe nome, tipo, SKU, preço, `stockRequirementId`, `reservationId` escolhido pelo consumidor ou flags
de reserva. Commands vazios e quantidades inválidas são rejeitados antes de qualquer mutação.

## Casos de uso e transações

| Caso de uso | Módulo | Transação | Responsabilidade |
|---|---|---|---|
| `ReserveStockItemsUseCase` | Stock & Procurement | escrita, `REQUIRED` | Reservar lote com locks globais |
| `GetStockReservationUseCase` | Stock & Procurement | `readOnly` | Consultar por `reservationId` |
| `GetStockReservationByExecutionUseCase` | Stock & Procurement | `readOnly` | Consultar pela origem única |
| `ConsumeStockReservationUseCase` | Stock & Procurement | escrita | Consumir integralmente com lock da reserva |
| `GenerateEstimateUseCase` | Service Lifecycle | escrita | Criar snapshot e congelar requirements |
| `DecideEstimateLinesUseCase` | Service Lifecycle | escrita | Decidir linhas e reservar as aprovadas |
| `RetryStockReservationUseCase` | Service Lifecycle | escrita | Repetir o conjunto congelado sem body arbitrário |

### Aprovação automática

`DecideEstimateLinesUseCase` manterá a regra comercial de tudo-ou-nada para a lista de decisões. Depois das validações
de Estimate, pertencimento, duplicidade e estado:

1. carrega a Service Order com lock de escrita;
2. aplica cada decisão comercial no aggregate;
3. cria um command para cada execução aprovada que possua requirements congelados;
4. chama `StockReservationApi.reserveAll(...)` uma vez;
5. para cada resultado `RESERVED`, confirma o `reservationId` na execução correspondente;
6. para cada `NOT_RESERVED`, mantém a aprovação e `AWAITING_ITEMS`;
7. salva a Service Order e publica os resultados para reações posteriores ao commit.

Uma execução sem requirements vai diretamente para `READY` e não gera command vazio.

Indisponibilidade é resultado de negócio, não exception: ela não marca rollback e não impede a reserva de outra
execução da mesma chamada. Uma falha técnica inesperada reverte a chamada inteira, impedindo divergência entre decisão,
saldo, reserva e referência em Service Lifecycle.

### Nova tentativa pelo Stock Manager

`RetryStockReservationUseCase` recebe apenas `serviceOrderId` e `serviceExecutionId`, valida pertencimento e estado e
reconstrói o command a partir dos requirements congelados. Nenhuma linha vem do body HTTP.

- em `AWAITING_ITEMS`, tenta novamente o conjunto inteiro;
- em `READY` com `stockReservationId`, retorna o sucesso existente sem novo desconto;
- em qualquer outro estado, retorna conflito de transição;
- sucesso confirma o mesmo resultado na Service Execution e a leva a `READY`;
- nova indisponibilidade preserva `AWAITING_ITEMS`.

### Consumo

`ConsumeStockReservationUseCase` carrega a reserva com lock de escrita e chama `consume(clock.instant())`. A primeira
chamada persiste `CONSUMED` e `consumedAt`; repetições retornam a mesma representação. Nenhum saldo de Stock Item é
alterado e nenhum callback para Service Lifecycle é necessário, pois `stockReservationId` permanece estável.

## Concorrência e atomicidade

`ReserveStockItemsUseCase` seguirá esta ordem:

1. validar e consolidar cada command sem alterar estado;
2. coletar a união de todos os `stockItemId` e ordenar por UUID;
3. carregar todos os Stock Items em uma consulta com `PESSIMISTIC_WRITE` e ordem determinística;
4. consultar reservas existentes por `serviceExecutionId`;
5. comparar linhas de reservas existentes para idempotência ou conflito;
6. processar os commands na ordem recebida, sem atribuir a ela significado de prioridade de negócio;
7. para cada execução, avaliar o conjunto inteiro contra o saldo corrente já afetado pelos sucessos anteriores;
8. quando não houver issue, descontar todas as linhas e criar `ACTIVE`; caso contrário, não descontar linha alguma;
9. persistir Stock Items e Stock Reservations na mesma transação.

O repository de Stock Item exporá leituras de escrita para um item e para uma coleção ordenada. Update cadastral,
desativação e reserva usarão essas leituras, evitando lost update entre alteração de cadastro e saldo.

A constraint única de `service_execution_id` é a proteção final contra reserva duplicada. Os fluxos válidos de Service
Lifecycle também serão serializados pelo lock da Service Order. Uma violação concorrente da constraint não será exposta
como SQL; será traduzida para conflito estável.

Não será usado `REQUIRES_NEW`: decisão comercial, alteração de saldo, criação da reserva e associação do ID participam
da mesma transação física do banco. A independência entre execuções significa que um resultado `NOT_RESERVED` não
desfaz os sucessos das demais; não significa preservar commits parciais diante de falha técnica inesperada.

## Contratos HTTP

Domain objects, JPA entities e commands entre módulos não serão expostos diretamente.

### Operações

| Método e path | Comportamento | Sucesso |
|---|---|---|
| `POST /api/service-orders/{id}/executions/{executionId}/stock-reservation` | Repetir reserva congelada | `200` |
| `GET /api/stock-reservations/{reservationId}` | Consultar por ID | `200` |
| `GET /api/stock-reservations/by-service-execution/{serviceExecutionId}` | Consultar por origem | `200` |
| `POST /api/stock-reservations/{reservationId}/consume` | Consumir integralmente | `200` |

A criação inicial não terá endpoint público: ela é efeito da aprovação em
`POST /api/estimates/{estimateId}/decisions`. A nova tentativa não aceita body e, portanto, não permite que o Stock
Manager escolha linhas diferentes das apresentadas ao Customer.

### Response da reserva

```json
{
  "id": "4c795e80-b0c8-46ad-b0ef-14bdddcf5075",
  "serviceExecutionId": "a49e8d8a-cbd4-4e1f-92d0-6b7fe733023f",
  "status": "ACTIVE",
  "lines": [
    {
      "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
      "quantity": 2
    }
  ],
  "createdAt": "2026-08-21T01:45:16Z",
  "consumedAt": null
}
```

### Response da tentativa manual

Sucesso e indisponibilidade retornam `200`, pois ambos são resultados esperados da tentativa:

```json
{
  "serviceExecutionId": "a49e8d8a-cbd4-4e1f-92d0-6b7fe733023f",
  "outcome": "NOT_RESERVED",
  "reservationId": null,
  "issues": [
    {
      "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
      "reason": "INSUFFICIENT_QUANTITY",
      "requestedQuantity": 2,
      "availableQuantity": 1
    }
  ]
}
```

Item inexistente usa `availableQuantity: null`. Em sucesso, `outcome` é `RESERVED`, `reservationId` é preenchido e
`issues` é vazio. Uma repetição idempotente produz o mesmo resultado de sucesso.

### Alterações em contratos existentes

- `ServiceExecutionResponse` recebe `stockReservationId`, campo anulável e aditivo;
- `StockRequirementResponse.reserved` é preservado por compatibilidade;
- `POST /api/estimates/{estimateId}/decisions` mantém request, path e `200`, mas a resposta passa a refletir
  `AWAITING_ITEMS` ou `READY` e o eventual `stockReservationId`;
- todos os contratos que expõem status deixam de produzir `AWAITING_PART` e passam a produzir `AWAITING_ITEMS`.

O rename de status é uma mudança incompatível explicitamente aprovada na especificação funcional. OpenAPI gerado,
testes de contrato e a collection Postman serão atualizados no mesmo checkpoint da implementação HTTP.

## Falhas e códigos estáveis

| Situação | HTTP | Código ou resultado |
|---|---:|---|
| UUID ou contrato inválido | `400` | `VALIDATION_ERROR` |
| Command interno vazio, nulo ou com quantidade inválida | `400` | `INVALID_STOCK_RESERVATION` |
| Service Order ou Service Execution inexistente no retry | `404` | `NOT_FOUND` |
| Stock Reservation inexistente em consulta ou consumo | `404` | `STOCK_RESERVATION_NOT_FOUND` |
| Retry em estado não permitido | `409` | `INVALID_STATE_TRANSITION` |
| Mesma execução com linhas diferentes | `409` | `STOCK_RESERVATION_CONFLICT` |
| Item ausente, inativo ou insuficiente na tentativa | `200` | `NOT_RESERVED` com issues por item |

Um advice limitado aos controllers de Stock Reservation traduzirá exceptions esperadas sem reutilizar o código
genérico e impreciso `INVALID_STOCK_ITEM`. Mensagens não exporão SQL, locks, nomes de constraints ou classes internas.
Falhas técnicas inesperadas continuarão sob o tratamento padrão da plataforma e causarão rollback.

## Persistência

### Tabelas de Stock Reservation

`stock_reservations`:

| Coluna | Tipo MySQL | Regra |
|---|---|---|
| `id` | `BINARY(16)` | Primary key |
| `service_execution_id` | `BINARY(16)` | `NOT NULL`, unique, sem foreign key entre contextos |
| `status` | `VARCHAR(32)` | `NOT NULL`, check `ACTIVE` ou `CONSUMED` |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL`, UTC |
| `consumed_at` | `TIMESTAMP(6)` | Nulo em `ACTIVE`, preenchido em `CONSUMED` |

`stock_reservation_lines`:

| Coluna | Tipo MySQL | Regra |
|---|---|---|
| `reservation_id` | `BINARY(16)` | Parte da primary key e foreign key para a reserva |
| `stock_item_id` | `BINARY(16)` | Parte da primary key e foreign key para `stock_items` |
| `quantity` | `INTEGER` | `NOT NULL`, check maior que zero |

A chave composta `(reservation_id, stock_item_id)` reforça a consolidação. Reservas não são excluídas; o mapeamento
não terá remoção em cascata como comportamento de negócio.

### Alterações em Service Lifecycle

`service_executions` receberá:

- `stock_requirements_frozen BOOLEAN NOT NULL DEFAULT FALSE`;
- `stock_reservation_id BINARY(16) NULL` com unicidade, mas sem foreign key entre bounded contexts.

`service_execution_stock_requirements.reserved` será preservado nesta entrega como campo de compatibilidade.

### Migration incremental

Será criada a migration incremental:

```text
V20260821014516__create_stock_reservations.sql
```

A baseline existente não será editada. A migration criará tabelas, constraints e índices, adicionará os campos de
Service Lifecycle e executará o backfill:

1. trocar `AWAITING_PART` por `AWAITING_ITEMS` em `service_executions.status` e
   `service_orders.status_snapshot`;
2. marcar `stock_requirements_frozen = true` nas execuções já presentes em `estimate_lines`;
3. zerar flags `reserved` preexistentes, pois não possuem Stock Reservation nem desconto de saldo rastreável;
4. mover para `AWAITING_ITEMS` as execuções `READY` que tenham requirements, mas não possuam reserva;
5. recalcular por SQL o `status_snapshot` das Service Orders afetadas, respeitando a precedência de `IN_PROGRESS` sobre
   `AWAITING_ITEMS`;
6. manter execuções legadas `IN_PROGRESS` ou `COMPLETED` sem fabricar reserva ou movimentação retroativa.

O backfill deliberadamente não cria reservas para flags antigos: o código anterior não reduzia
`stock_items.available_quantity`, logo sintetizar compromissos inventaria saldo e histórico.

### Classificação de dados

**Nenhum seed novo é necessário.** Stock Reservations são dados transacionais criados somente pelos casos de uso. O
seeder de Stock Item existente permanece limitado ao perfil `dev` e `app.seed.enabled=true`. Testes usarão fixtures e
builders próprios, sem depender de dados de demonstração.

## Resultados e notificações

Os resultados serão publicados dentro da aplicação e entregues somente depois do commit, usando listeners
transacionais. Isso evita notificar uma reserva que posteriormente sofreu rollback.

Stock & Procurement será dono de `StockManagerNotificationPort`, com operações reais para:

- informar uma reserva criada que precisa de separação física;
- informar items ausentes, inativos ou insuficientes que impedem a reserva.

Service Lifecycle ampliará seu `TechnicianNotificationPort` para comunicar que os materiais de uma execução estão
reservados. Se não houver Technician no momento do sucesso, `AssignTechnicianUseCase` produzirá essa comunicação ao
atribuir um Technician a uma execução `READY` com `stockReservationId`.

Como o projeto não possui contato ou canal do Stock Manager e o adapter atual do Technician é simulado, os adapters
desta entrega usarão log estruturado sem dados pessoais. Os ports serão efetivamente chamados; não serão placeholders
ociosos. Falha de entrega será capturada e registrada depois do commit, sem desfazer Estimate, reserva ou saldo.

Não serão criados tabela de entrega, retry, template, canal real ou bounded context de Notification.

## Segurança e operação

### Validação e mass assignment

- a criação inicial não é exposta por HTTP;
- o retry não aceita linhas, preço, estado ou ID de reserva no body;
- commands entre módulos validam UUIDs, lista não vazia, quantidade positiva e overflow;
- consultas e mutations usam JPA parametrizado, sem SQL montado a partir de entrada;
- constraints de saldo, quantidade e unicidade complementam as invariantes do domínio.

### Autenticação e autorização

O projeto ainda não possui autenticação. Os novos endpoints seguirão temporariamente o padrão público atual. Quando a
fronteira de identidade existir:

- retry, consulta operacional e consumo deverão exigir Stock Manager;
- consulta necessária à retirada poderá ser concedida ao Technician atribuído;
- a decisão da Estimate continuará restrita ao Customer ou ator autorizado pelo Service Lifecycle.

A ausência atual de autorização é um risco conhecido do MVP e não será disfarçada com papéis fictícios dentro do
domínio.

### Dados, erros, logs e segredos

Reservas expõem IDs e quantidades operacionais, mas não preço, custo, dados pessoais ou dados do Customer. Logs de
notificação não registrarão request completo, snapshots comerciais, credenciais ou variáveis de ambiente.

Nenhum segredo, configuração sensível ou dependência de produção será adicionado. Exceptions técnicas não serão
convertidas em mensagens que revelem estrutura do banco.

### Abuso, contenção e recuperação

- transações não executarão notificações externas antes do commit;
- locks serão adquiridos em ordem determinística e mantidos somente durante o comando;
- consultas por ID são limitadas a uma reserva e não introduzem listagem irrestrita;
- falha inesperada causa rollback integral; retry idempotente pode ser feito pelo mesmo fluxo;
- a migration é forward-only; rollback em ambiente compartilhado exige backup e migration compensatória, nunca
  edição do arquivo já aplicado;
- reset destrutivo permanece permitido somente para ambiente local explicitamente identificado.

### Revisão preliminar de segurança

| Tema | Avaliação | Mitigação proposta |
|---|---|---|
| Reserva parcial ou saldo negativo | Alto sem controle | Transação, locks ordenados, invariantes e constraints |
| Mass assignment de linhas | Mitigado | Sem endpoint de criação; retry deriva requirements congelados |
| Acesso sem papel de Stock Manager | Limitação conhecida | Documentar e restringir quando autenticação existir |
| Exposição de dados | Baixo | Somente IDs, quantidades, estado e timestamps operacionais |
| SQL injection | Baixo | JPA e parâmetros; nenhum SQL dinâmico vindo do request |
| Logs e notificações | Baixo | IDs operacionais, sem PII, body, preço ou segredo |
| Novas dependências | N/A | Nenhuma dependência nova planejada |

A revisão final e suas evidências serão registradas no implementation plan. Nenhum finding crítico ou alto poderá
permanecer aberto na conclusão.

## Estratégia de testes

### Domínio

- criação e reconstituição de `StockReservation`;
- rejeição de origem, linhas ou quantidades inválidas e itens repetidos não consolidados;
- transição `ACTIVE` para `CONSUMED`, repetição idempotente e preservação de `consumedAt`;
- desconto válido em `StockItem`, saldo exato zero, insuficiência e item inativo;
- congelamento dos requirements, rejeição de anexo posterior e confirmação atômica por `reservationId`;
- status `READY` sem requirements, `AWAITING_ITEMS` com pendência e ausência de regressão após sucesso.

### Aplicação

- reserva de um e de vários itens;
- consolidação de linhas repetidas;
- ausência, inatividade e insuficiência sem qualquer desconto parcial;
- reserva de quantidade exatamente igual ao saldo;
- idempotência por `serviceExecutionId` e conflito com linhas diferentes;
- lote com uma execução reservada e outra `NOT_RESERVED`;
- aprovação preservada na indisponibilidade e associação do ID no sucesso;
- aprovação de execução sem requirements sem reserva vazia;
- retry usando somente requirements congelados;
- consumo e repetição idempotente;
- geração da Estimate congela e persiste a Service Order;
- atribuição posterior do Technician produz a notificação de materiais reservados;
- falha dos adapters de notificação não altera o resultado persistido.

### Concorrência e persistência

- duas transações disputando as últimas unidades nunca deixam saldo negativo;
- solicitação de vários itens sofre rollback completo quando um deles não pode ser atendido;
- duas criações para o mesmo `serviceExecutionId` produzem no máximo uma linha em `stock_reservations`;
- update/desativação concorrente não sobrescreve desconto confirmado;
- consumo concorrente preserva o primeiro `consumedAt`;
- projections JPA reconstituem reserva, linhas, timestamps e referência na Service Execution;
- Flyway cria e valida o schema com `ddl-auto=validate` no banco de testes;
- teste dedicado do upgrade verifica rename, congelamento e normalização dos flags legados.

Os testes de concorrência usarão transações reais e sincronização por barreira; mocks não serão aceitos como
evidência de lock. A compatibilidade dos DDLs e locks com MySQL 8 será verificada pelo ambiente Docker existente, sem
adicionar dependência de produção.

### HTTP, notificações e Modulith

- MockMvc cobrirá os quatro endpoints novos, bodies, estados e códigos estáveis;
- o endpoint de retry será testado sem body arbitrário e com resultado `NOT_RESERVED` detalhado;
- respostas de Service Order cobrirão `stockReservationId` e `AWAITING_ITEMS`;
- `AWAITING_PART` deixará de aparecer no OpenAPI e nos contratos;
- listeners after-commit serão testados para sucesso, ausência de Technician e falha do adapter;
- `@ApplicationModuleTest` validará a interação pela named interface;
- `ModuleStructureTest` continuará verde e encontrará somente os três bounded contexts;
- OpenAPI gerado e Postman serão atualizados no mesmo checkpoint;
- `make test`, `make verify` e `make coverage` fornecerão as evidências finais, preservando a meta de 80%.

## Impacto sobre especificações e documentação existentes

Antes da implementação, as seguintes especificações funcionais de Service Lifecycle precisam voltar para `Draft`, ser
atualizadas e receber nova aprovação; suas especificações técnicas e planos atuais ficam stale enquanto isso:

- `attach-stock-requirement`: congelamento na Estimate e proibição de anexo posterior;
- `estimate-generation`: persistência do congelamento na mesma transação;
- `decide-estimate-lines`: tentativa automática, resultados independentes de indisponibilidade e novos status;
- `assign-technician`: notificação quando uma execução atribuída já possui materiais reservados;
- `start-execution` e `track-execution`: rename contratual para `AWAITING_ITEMS`.

Também serão atualizados `docs/Architecture.md`, diagramas Modulith gerados, OpenAPI e
`docs/api/postman/workshop-management-system.postman_collection.json`. O Miro continuará como contexto de descoberta,
mas o termo `AWAITING_ITEMS` e a ausência de liberação nesta feature deverão ser refletidos nele para remover a
divergência conhecida.

## Decisões técnicas aprovadas

- [x] `StockReservation` é aggregate root em `stockprocurement.stockreservation` e possui somente `ACTIVE` e `CONSUMED`.
- [x] Existe no máximo uma reserva por `serviceExecutionId`, protegida também por constraint única.
- [x] Service Lifecycle armazena somente o `stockReservationId`; linhas e estado pertencem a Stock & Procurement.
- [x] A integração usa a named interface `stock-reservation-api`, sem importação de pacotes internos.
- [x] A decisão de Estimate chama uma operação de lote e trata indisponibilidade como resultado sem rollback comercial.
- [x] O retry HTTP pertence a Service Lifecycle, não aceita linhas e deriva o conjunto congelado.
- [x] Criação e associação da reserva participam da mesma transação; notificações ocorrem depois do commit.
- [x] Locks pessimistas ordenados protegem todas as mutações concorrentes de `StockItem`.
- [x] `StockRequirement.reserved` permanece apenas por compatibilidade e deixa de decidir prontidão.
- [x] A migration incremental cria as tabelas, converte status e rebaixa flags antigos sem fabricar reservas.
- [x] Não existe seed de reserva; somente fixtures de teste.
- [x] Os quatro endpoints, os códigos estáveis, OpenAPI e Postman seguem os contratos descritos acima.
- [x] Ports consumidores e listeners after-commit implementam as notificações reais do MVP sem um módulo genérico.
- [x] As limitações de autorização, contenção, dados e logs estão registradas e mitigadas no escopo disponível.

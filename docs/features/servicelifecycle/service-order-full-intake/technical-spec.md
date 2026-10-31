# Especificação Técnica: Abertura completa de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-full-intake` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-16 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-16 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

### Módulos afetados

| Módulo | Papel nesta feature | Mudança |
|---|---|---|
| `servicelifecycle` | Dono do fluxo: orquestra a abertura | Novo endpoint, caso de uso orquestrador, portas e adapters consumidores, DTOs e exceções |
| `registration` (customer, vehicle, servicecatalog) | Dono dos cadastros e dos preços dos serviços | Novas APIs públicas em `application/api`: identificar-ou-cadastrar Customer e Vehicle, e cotação de Catalog Service |
| `stockprocurement` (stock) | Dono dos Stock Items | Nova API pública em `stock/application/api`: cotação de Stock Item |
| `identity` | Autorização | Nova regra no `SecurityConfig` |

A direção das dependências não muda: `servicelifecycle → registration` e `servicelifecycle → stockprocurement` já
existem (elegibilidade de Vehicle e Catalog Service, avaliação de estoque). Nenhuma dependência nova no sentido
contrário e nenhum ciclo.

### Integração entre módulos (ADR-005)

Segue a ADR-005: **porta Java in-process no consumidor** (`servicelifecycle/.../application/port`) e **API pública no
módulo dono** (`<módulo>/.../application/api`, exposta como `@NamedInterface`). O adapter do consumidor fica em
`servicelifecycle/serviceorder/infrastructure/registration` (já existe) e em um novo
`servicelifecycle/serviceorder/infrastructure/stock`. Não há endpoint REST interno entre módulos.

As APIs públicas novas usam `@Transactional(propagation = Propagation.MANDATORY)`, o mesmo padrão de
`CheckVehicleAvailabilityUseCase`: elas só rodam **dentro** da transação aberta pelo orquestrador e falham se forem
chamadas sem ela. É isso que garante a atomicidade entre módulos: Customer, Vehicle, Service Order, Diagnosis e
Estimate são gravados no mesmo banco e na mesma transação.

### Orquestração (decisão D1)

O novo `OpenServiceOrderIntakeUseCase` (`servicelifecycle/serviceorder/application/usecase`), com `@Transactional`,
executa:

1. `CustomerIntakePort.identifyOrRegister(...)` → `customerId` + `created`;
2. `VehicleIntakePort.identifyOrRegister(customerId, ...)` → `vehicleId`, dados do snapshot e `created`;
3. `CreateServiceOrderUseCase.execute(...)`, com o `VehicleSnapshotRequest` montado a partir do passo 2 (o cliente da
   API não envia snapshot);
4. somente se houver serviços:
   1. `AssignDiagnosisAssigneeUseCase.execute(serviceOrderId, technicianId)`;
   2. `CatalogServiceQuotePort` e `StockItemQuotePort` → nome, preço e tipo vindos dos cadastros;
   3. `PerformDiagnosisUseCase.execute(serviceOrderId, PerformDiagnosisRequest)`, com nomes e preços cotados no passo
      anterior;
   4. `GenerateEstimateUseCase.execute(serviceOrderId, diagnosisId)`;
5. relê a Service Order e monta a resposta.

**D1 — reaproveitar os casos de uso existentes, em vez de duplicar a lógica no orquestrador.** Todos usam
`@Transactional` com propagação `REQUIRED` e entram na transação externa. Assim as regras de criação, triagem,
atribuição, diagnóstico, avaliação de estoque, Purchase Demand e Estimate continuam num único lugar, e qualquer
correção futura nelas vale automaticamente para a abertura completa.

Alternativa rejeitada: chamar os métodos do aggregate diretamente no orquestrador. Isso duplicaria a avaliação de
estoque, o congelamento e a geração do Estimate.

Consequência aceita: o orquestrador depende das assinaturas desses casos de uso. É uma dependência interna do mesmo
módulo, coberta pelos testes de integração.

### Efeitos colaterais e momento em que ocorrem

| Efeito | Mecanismo atual | Comportamento na abertura completa |
|---|---|---|
| Notificação ao Customer do Estimate gerado | Evento `EstimateGenerated` + `@ApplicationModuleListener` (após o commit) | Só ocorre se a abertura inteira for confirmada. Rollback = sem e-mail. |
| Notificação aos Technicians da nova OS | `TechnicianNotificationPort` chamado dentro de `CreateServiceOrderUseCase` | Hoje o adapter só escreve um log estruturado (`LoggedTechnicianNotificationAdapter`). Em rollback, sobra uma linha de log sem OS correspondente. **Aceito** (D2). |
| Purchase Demand por falta de peça | `RepairStockAssessmentApi` na mesma transação | Desfeita junto em rollback |

**D2 — não alterar a notificação de Technician nesta feature.** Movê-la para depois do commit mudaria também o
`POST /api/service-orders`, que está fora de escopo. O único efeito em rollback é uma linha de log, sem e-mail e sem
dado gravado. Fica registrado como risco baixo na revisão de segurança.

## Interfaces e fluxo de dados

### Endpoint HTTP

`POST /api/service-orders/intake` → `201 Created`, com `Location: /api/service-orders/{id}`.

O caminho não conflita com rotas existentes: `GET /api/service-orders/{id}` é outro método, e as demais rotas `POST`
têm mais segmentos (`/{id}/diagnosis` etc.).

#### Request — `OpenServiceOrderIntakeRequest`

```json
{
  "customer": {
    "document": "529.982.247-25",
    "name": "Cliente Exemplo",
    "contactInfo": { "email": "cliente@example.com", "phone": "11999998888", "address": { "...": "..." } }
  },
  "vehicle": {
    "licensePlate": "ABC1D23",
    "brand": "Fiat",
    "model": "Argo",
    "year": 2022,
    "color": "Prata",
    "chassis": null,
    "mileage": 42500
  },
  "priority": "NORMAL",
  "initialAssessment": "Barulho na suspensão dianteira",
  "diagnosisTechnicianId": "6f1c...",
  "services": [
    {
      "catalogServiceId": "a1b2...",
      "parts": [ { "stockItemId": "c3d4...", "quantity": 2 } ]
    }
  ]
}
```

Validação de borda (Bean Validation):

| Campo | Regra |
|---|---|
| `customer` | `@NotNull @Valid` |
| `customer.document` | `@NotBlank`; a validação de CPF/CNPJ fica no domínio (`TaxId`) |
| `customer.name`, `customer.contactInfo` | opcionais na borda; obrigatórios só quando o Customer for novo (validado na API de registration) |
| `vehicle` | `@NotNull @Valid` |
| `vehicle.licensePlate` | `@NotBlank @Size(max = 16)`; o formato fica no domínio (`LicensePlate`) |
| `vehicle.brand`, `model`, `year`, `color`, `chassis`, `mileage` | opcionais na borda, com os mesmos `@Size`/`@Min`/`@PositiveOrZero` de `CreateVehicleRequest`; obrigatoriedade só para Vehicle novo |
| `priority` | opcional; padrão `NORMAL` |
| `initialAssessment` | `@NotBlank` |
| `diagnosisTechnicianId` | opcional na borda; obrigatório quando `services` não estiver vazio (validado no caso de uso → `400 VALIDATION_ERROR`) |
| `services` | opcional; `@Size(max = 20)` e `@Valid` |
| `services[].catalogServiceId` | `@NotNull` |
| `services[].parts` | opcional; `@Size(max = 20)` e `@Valid` |
| `parts[].stockItemId` | `@NotNull` |
| `parts[].quantity` | `@Positive` |

O request **não tem** campos de nome, preço, tipo ou snapshot. Se o cliente da API enviar campos desconhecidos, como
`price`, eles são ignorados pelo Jackson, que é o comportamento padrão do projeto, e nunca chegam ao domínio.

Os limites de 20 serviços e 20 peças por serviço protegem contra requests abusivos que travariam muitas linhas de
estoque numa única transação.

#### Response — `ServiceOrderIntakeResponse`

```json
{
  "serviceOrder": { "...": "mesmo formato de ServiceOrderResponse" },
  "customer": { "id": "...", "created": true },
  "vehicle": { "id": "...", "created": false },
  "estimateId": "..."
}
```

`estimateId` é `null` quando não há serviços. O detalhe do Estimate continua em `GET /api/estimates/{estimateId}`,
sem duplicar o contrato dele aqui.

### APIs públicas novas nos módulos donos

Todas com `@Transactional(propagation = Propagation.MANDATORY)`.

**`registration.customer.application.api.CustomerIntakeApi`** (nova `@NamedInterface("customer-intake-api")`)

```java
CustomerIntakeResult identifyOrRegister(CustomerIntakeCommand command);
// CustomerIntakeCommand(String document, String name, ContactData contact)
// CustomerIntakeResult(UUID customerId, CustomerIntakeOutcome outcome)
// CustomerIntakeOutcome { REUSED, CREATED, ARCHIVED }
```

- Busca `findActiveByTaxId`. Se encontrar, devolve `REUSED` sem alterar nada.
- Se não houver ativo, mas `existsByTaxId` for verdadeiro, devolve `ARCHIVED` sem gravar.
- Se não existir, cria com `Customer.create(...)` e as mesmas validações de `CreateCustomerUseCase`. Dados inválidos
  ou ausentes lançam `IllegalArgumentException`, que é o comportamento atual dos value objects.

**`registration.vehicle.application.api.VehicleIntakeApi`** (no pacote `application/api` existente, que já é a
`@NamedInterface("vehicle-availability-api")`; o nome da named interface é do pacote e fica como está para não mexer
em quem já a usa)

```java
VehicleIntakeResult identifyOrRegister(VehicleIntakeCommand command);
// VehicleIntakeCommand(UUID customerId, String licensePlate, String brand, String model,
//                      Integer year, String color, String chassis, Long mileage)
// VehicleIntakeResult(UUID vehicleId, VehicleIntakeOutcome outcome,
//                     String licensePlate, String brand, String model, int year)
// VehicleIntakeOutcome { REUSED, CREATED, ARCHIVED, OWNED_BY_ANOTHER_CUSTOMER }
```

- Exige `VehicleRepository.findByLicensePlateForUpdate(LicensePlate)`, um **método novo** no repositório e na
  implementação JPA (hoje só existe `existsByLicensePlate`).
- Mesmo dono e ativo → `REUSED`. Outro dono → `OWNED_BY_ANOTHER_CUSTOMER`. Arquivado → `ARCHIVED`. Inexistente → cria
  com as regras de `CreateVehicleUseCase`, inclusive a unicidade do chassi.
- O snapshot devolvido vem sempre do cadastro persistido, normalizado pelo domínio.

**`registration.servicecatalog.application.api.CatalogServiceQuoteApi`** (na
`@NamedInterface("catalog-service-availability-api")` existente)

```java
CatalogServiceQuote quote(UUID catalogServiceId);
// CatalogServiceQuote(UUID id, CatalogServiceAvailability availability, String name,
//                     BigDecimal price, String currency)
```

**`stockprocurement.stock.application.api.StockItemQuoteApi`** (nova `@NamedInterface("stock-item-quote-api")`)

```java
List<StockItemQuote> quote(Collection<UUID> stockItemIds);
// StockItemQuote(UUID id, boolean active, String name, String type, BigDecimal price, String currency)
// IDs inexistentes simplesmente não aparecem na lista.
```

O `type` é passado como `String` e convertido pelo adapter para o `StockItemType` de `servicelifecycle`. Os dois enums
têm hoje os mesmos valores (`PART`, `CONSUMABLE`, `SUPPLY`). Um valor desconhecido é um erro técnico (`500`), não de
negócio.

### Portas e adapters consumidores (`servicelifecycle`)

| Porta (`application/port`) | Adapter (`infrastructure/...`) | API chamada |
|---|---|---|
| `CustomerIntakePort` | `registration/RegistrationCustomerIntakeAdapter` | `CustomerIntakeApi` |
| `VehicleIntakePort` | `registration/RegistrationVehicleIntakeAdapter` | `VehicleIntakeApi` |
| `CatalogServiceQuotePort` | `registration/RegistrationCatalogServiceQuoteAdapter` | `CatalogServiceQuoteApi` |
| `StockItemQuotePort` | `stock/StockItemQuoteAdapter` | `StockItemQuoteApi` |

Os adapters traduzem os resultados e as exceções do módulo dono em tipos e exceções de `servicelifecycle`, para que o
caso de uso e o `ServiceLifecycleExceptionHandler` não conheçam tipos de outro módulo. Os handlers de `registration`
estão restritos por `assignableTypes` aos controllers deles e não pegariam essas exceções.

### Tradução de falhas

Mapeadas no `ServiceLifecycleExceptionHandler`, com `ErrorResponse`:

| Situação | Exceção (`servicelifecycle`) | HTTP | Código |
|---|---|---|---|
| Bean Validation | — (já existente) | 400 | `VALIDATION_ERROR` |
| CPF/CNPJ, placa ou dados de cadastro novo inválidos/ausentes | `ServiceOrderIntakeInvalidDataException` (traduz `IllegalArgumentException` da API de registration) | 400 | `VALIDATION_ERROR` |
| Serviços sem `diagnosisTechnicianId` | `ServiceOrderIntakeInvalidDataException` | 400 | `VALIDATION_ERROR` |
| Technician inexistente | `NoSuchElementException` (já existente) | 404 | `NOT_FOUND` |
| Catalog Service inexistente | `CatalogServiceNotFoundForNewWorkException` (existente) | 404 | `CATALOG_SERVICE_NOT_FOUND` |
| Stock Item inexistente | `ServiceOrderStockItemNotFoundException` (existente) | 404 | `STOCK_ITEM_NOT_FOUND` |
| Customer arquivado | `ServiceOrderIntakeCustomerArchivedException` (nova) | 409 | `CUSTOMER_ARCHIVED` |
| Vehicle arquivado | `ServiceOrderVehicleArchivedException` (existente) | 409 | `VEHICLE_ARCHIVED` |
| Placa de outro Customer | `ServiceOrderIntakeVehicleOwnershipConflictException` (nova) | 409 | `VEHICLE_OWNED_BY_ANOTHER_CUSTOMER` |
| Chassi já usado por outro Vehicle | `ServiceOrderIntakeVehicleChassisConflictException` (nova) | 409 | `VEHICLE_CHASSIS_ALREADY_EXISTS` |
| Catalog Service arquivado | `CatalogServiceArchivedForNewWorkException` (existente) | 409 | `CATALOG_SERVICE_ARCHIVED` |
| Stock Item inativo | `ServiceOrderStockItemInactiveException` (nova) | 409 | `STOCK_ITEM_INACTIVE` |
| Cadastro concorrente do mesmo CPF/CNPJ ou placa | `DataIntegrityViolationException` no commit | 409 | `CONCURRENT_REGISTRATION` |

Notas:

- **Stock Item inativo:** a cotação valida antes do diagnóstico. Hoje, no endpoint de Diagnosis, o
  `StockItemInactiveException` do módulo de estoque não é mapeado em `servicelifecycle` e vira `500`. Esta feature não
  corrige o endpoint de Diagnosis (fora de escopo), mas a abertura completa não herda o problema, porque valida antes.
  Fica registrado como achado para uma correção separada.
- **Concorrência:** duas aberturas simultâneas com o mesmo documento ou placa novos passam pelo "não existe" ao mesmo
  tempo, e a segunda viola `uk_customers_document` ou `uk_vehicles_license_plate` no commit. O handler, restrito ao
  pacote de `servicelifecycle`, responde `409 CONCURRENT_REGISTRATION`, com uma mensagem genérica e sem detalhes de SQL.
  A segunda chamada pode ser repetida, e então reaproveita o cadastro criado pela primeira.
- Mensagens de erro não incluem CPF/CNPJ, e-mail nem telefone.

## Persistência e dados de bootstrap

- **Sem mudança de schema e sem migration.** Tudo é gravado nas tabelas existentes (`customers`, `vehicles`,
  `service_orders` e tabelas associadas, `estimates` etc.).
- Novo método de leitura `findByLicensePlateForUpdate` no repositório de Vehicle, usando a coluna `license_plate`, que
  já tem índice único.
- `@Lock(PESSIMISTIC_WRITE)` na busca por placa: segue o padrão `findByIdForUpdate` do projeto e serializa aberturas
  concorrentes para um Vehicle que já existe.
- **Classificação de dados:** **nenhum seed necessário.** A feature não cria dados de referência. Os testes usam
  fixtures/builders próprios e não dependem do seed de desenvolvimento.

## Segurança e operação

### Autorização

Nova regra no `identity/SecurityConfig`, declarada **antes** da regra geral de `/api/service-orders/**`, que hoje
concede acesso a `TECHNICIAN`:

```java
.requestMatchers(HttpMethod.POST, "/api/service-orders/intake").hasAnyAuthority("MANAGER", "ADMIN")
```

### Revisão de segurança (prévia; o registro final vai no implementation-plan)

| Item | Análise | Mitigação |
|---|---|---|
| Validação de entrada e mass assignment | O request não tem preço, nome, tipo, status nem IDs de dono. Campos extras são ignorados. | DTO dedicado, Bean Validation e validação de domínio |
| Manipulação de preço | Era possível no Diagnosis atual (preço no request) | Nome e preço vêm do Service Catalog e do Stock Item (functional-spec) |
| Autenticação e autorização | O endpoint cria Customer e Vehicle e gera Estimate | Só `MANAGER`/`ADMIN`; teste MockMvc para `TECHNICIAN` (403) e sem token (401) |
| Exposição de dados | A resposta só traz IDs, a flag `created` e a OS | Os dados do Customer não voltam na resposta; quem precisar consulta `/api/customers/{id}` |
| Enumeração de cadastros | `customer.created=false` revela que o CPF já existia | Aceitável: só `MANAGER`/`ADMIN` chegam aqui, e esses papéis já têm `GET /api/customers/identify` |
| Logs | O orquestrador loga só IDs | Proibido logar documento, e-mail ou telefone |
| SQL e persistência | Só JPA/repositórios; sem SQL nativo novo | Constraints únicas existentes; `409` genérico em violação |
| Abuso e DoS | Muitos serviços e peças travariam linhas de estoque | Limite de 20 serviços e 20 peças por serviço |
| Notificação de Technician em rollback | Linha de log órfã (D2) | Risco baixo e aceito; sem e-mail e sem dado gravado |
| Dependências novas | Nenhuma | N/A |
| Segredos | Nenhum segredo novo | N/A |

### Rollout e recuperação

- A mudança é aditiva: nenhum contrato existente muda. Para fazer rollback, basta reverter o commit; não há migration
  para desfazer.
- Os casos de uso existentes não mudam de assinatura nem de comportamento.

### Documentação obrigatória (AGENTS.md)

- Anotações Springdoc/OpenAPI no novo endpoint (`@Operation` e `@ApiResponses` com 201, 400, 401, 403, 404 e 409).
- Coleção Postman: nova request "Abrir OS completa" na pasta de Service Orders, com dois exemplos (com e sem
  serviços) e as variáveis `serviceOrderId` e `estimateId` capturadas por script de teste.
- README: seção de teste manual da abertura completa (pré-requisitos: token de `MANAGER`, um Catalog Service e um Stock
  Item cadastrados; ordem das requests; resultados esperados).

## Estratégia de testes

| Nível | O que cobre | Onde |
|---|---|---|
| Unitário (registration) | `identifyOrRegister` do Customer: `REUSED` sem alterar dados, `ARCHIVED` e `CREATED`; dados inválidos para cadastro novo | `registration/customer/application/...Test` |
| Unitário (registration) | `identifyOrRegister` do Vehicle: `REUSED`, `OWNED_BY_ANOTHER_CUSTOMER`, `ARCHIVED`, `CREATED` e chassi duplicado | `registration/vehicle/application/...Test` |
| Unitário (registration, stock) | Cotações: ativo, arquivado/inativo e inexistente | testes das APIs |
| Unitário (servicelifecycle) | Orquestrador com portas e casos de uso dublados: ordem das chamadas, sem serviços → sem diagnóstico, serviços sem técnico → erro, nome e preço vindos da cotação, tradução de cada outcome em exceção | `OpenServiceOrderIntakeUseCaseTest` |
| Integração HTTP (MockMvc, banco de teste) | Os critérios de aceite ponta a ponta: 201 com Customer e Vehicle novos (`AWAITING_APPROVAL`, Estimate existente); reaproveitamento sem alteração; sem serviços (`RECEIVED`); cada 400, 404 e 409 da tabela; 401/403 | `ServiceOrderControllerIntakeTest` |
| Atomicidade | Falha **depois** do cadastro (ex.: Catalog Service arquivado) com Customer e Vehicle novos → contagem de `customers`, `vehicles`, `service_orders` e `estimates` igual à de antes | `ServiceOrderControllerIntakeTest` |
| Efeito após o commit | Em rollback, nenhum `EstimateGenerated` chega ao listener; em sucesso, chega um | teste de integração com `@RecordApplicationEvents` ou um spy do port de notificação |
| Regressão de contrato | `ServiceOrderControllerCreateTest` e os demais testes existentes passam sem alteração | suíte existente |
| Fronteiras Modulith | `ModuleStructureTest` verde com as novas named interfaces e os adapters | existente |
| Cobertura | JaCoCo ≥ 80%, sem reduzir a cobertura do código alterado | `make coverage` / `make verify` |

Os testes de integração usam builders e fixtures próprios para Catalog Service, Stock Item e Technician, sem depender
do seed de desenvolvimento.

# Plano de Implementação: Abertura completa de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-full-intake` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-17 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-10-16) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-10-16) |

## Objetivo da execução

Entregar `POST /api/service-orders/intake`, conforme as specs aprovadas: identificar-ou-cadastrar Customer e Vehicle,
abrir a Service Order e, se houver serviços, registrar o Diagnosis com preços cotados nos cadastros e gerar o Estimate,
tudo numa única transação. Nenhum endpoint existente muda de contrato ou de comportamento.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas e `.claude/rules/epic-3-service-lifecycle.md` antes de alterar código.
- Executar um checkpoint por vez, com no máximo um `In Progress`, e atualizar o status aqui (`Pending`,
  `In Progress`, `Completed`).
- Rodar os testes indicados antes de concluir cada checkpoint e registrar a evidência.
- Não importar pacotes internos de outro módulo; só `application/api` (ADR-005).
- Não alterar a assinatura nem o comportamento de `CreateServiceOrderUseCase`, `AssignDiagnosisAssigneeUseCase`,
  `PerformDiagnosisUseCase`, `GenerateEstimateUseCase`, `CreateCustomerUseCase` ou `CreateVehicleUseCase`. Se
  for preciso extrair lógica compartilhada em registration (ex.: montagem do Customer/Vehicle), a extração não pode
  mudar o comportamento observável dos casos de uso atuais.
- Interromper e devolver as specs a `Draft` se surgir decisão material diferente da aprovada.
- Git: nenhum commit sem pedido explícito do responsável. Ao fim de cada checkpoint, apenas sugerir a mensagem.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | APIs públicas de registration (Customer e Vehicle intake) | Completed |
| 2 | APIs de cotação (Catalog Service e Stock Item) | Completed |
| 3 | Portas, adapters e exceções em `servicelifecycle` | Completed |
| 4 | Caso de uso orquestrador | Completed |
| 5 | Endpoint HTTP, autorização e tradução de falhas | Completed |
| 6 | Testes de integração ponta a ponta e atomicidade | Completed |
| 7 | OpenAPI, Postman e README | Completed |
| 8 | Revisão de segurança e gates finais | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar a branch `feat/terraform-san` e um `git status --short` sem mudanças inesperadas.
- Rodar a suíte antes de qualquer mudança, para ter a linha de base.

### Verificação

- `./mvnw test` verde (registrar o número de testes e o tempo).

## Checkpoint 1 — APIs públicas de registration

### Alterações

- `registration/customer/application/api/`: `CustomerIntakeApi`, `CustomerIntakeCommand`, `CustomerIntakeResult`,
  `CustomerIntakeOutcome` e `package-info.java` com `@NamedInterface("customer-intake-api")`.
- Implementação em `registration/customer/application/usecase/` (ex.: `IdentifyOrRegisterCustomerUseCase`), com
  `@Transactional(propagation = MANDATORY)`.
- `registration/vehicle/application/api/`: `VehicleIntakeApi`, `VehicleIntakeCommand`, `VehicleIntakeResult` e
  `VehicleIntakeOutcome`.
- `VehicleRepository.findByLicensePlateForUpdate(LicensePlate)`, com a implementação JPA (`PESSIMISTIC_WRITE`).
- Implementação em `registration/vehicle/application/usecase/` (ex.: `IdentifyOrRegisterVehicleUseCase`).

### Verificação

- Testes unitários: Customer `REUSED` (sem alterar nome nem contato), `ARCHIVED`, `CREATED` e dados ausentes para
  cadastro novo; Vehicle `REUSED`, `OWNED_BY_ANOTHER_CUSTOMER`, `ARCHIVED`, `CREATED` e chassi duplicado.
- Teste de persistência para `findByLicensePlateForUpdate` contra o banco de teste.
- `ModuleStructureTest` verde.

### Commit sugerido

`feat(registration): add customer and vehicle intake public APIs`

## Checkpoint 2 — APIs de cotação

### Alterações

- `registration/servicecatalog/application/api/`: `CatalogServiceQuoteApi` e `CatalogServiceQuote`, com a
  implementação no módulo.
- `stockprocurement/stock/application/api/`: `StockItemQuoteApi`, `StockItemQuote` e `package-info.java` com
  `@NamedInterface("stock-item-quote-api")`, com a implementação no módulo.

### Verificação

- Testes unitários: Catalog Service ativo, arquivado e inexistente; Stock Item ativo, inativo e inexistente (ausente
  da lista).
- `ModuleStructureTest` verde.

### Commit sugerido

`feat(registration): add catalog service and stock item quote APIs`. Como o commit toca dois módulos, uma alternativa
é separá-lo em dois commits, um por módulo, com os escopos `registration` e `stockprocurement`.

## Checkpoint 3 — Portas, adapters e exceções em `servicelifecycle`

### Alterações

- Portas em `servicelifecycle/serviceorder/application/port/`: `CustomerIntakePort`, `VehicleIntakePort`,
  `CatalogServiceQuotePort` e `StockItemQuotePort`, com os tipos de resultado próprios do módulo.
- Adapters em `infrastructure/registration/` e no novo `infrastructure/stock/`, traduzindo outcomes e
  `IllegalArgumentException` para tipos e exceções de `servicelifecycle`.
- Exceções novas em `application/exception/`: `ServiceOrderIntakeInvalidDataException`,
  `ServiceOrderIntakeCustomerArchivedException`, `ServiceOrderIntakeVehicleOwnershipConflictException`,
  `ServiceOrderIntakeVehicleChassisConflictException` e `ServiceOrderStockItemInactiveException`.

### Verificação

- Testes unitários dos adapters, um por outcome traduzido.
- `ModuleStructureTest` verde. É aqui que o Modulith reprovaria um import interno indevido.

### Commit sugerido

`feat(servicelifecycle): add intake ports and registration/stock adapters`

## Checkpoint 4 — Caso de uso orquestrador

### Alterações

- DTOs em `application/dto/`: `OpenServiceOrderIntakeRequest` (e os records aninhados) e
  `ServiceOrderIntakeResponse`.
- `OpenServiceOrderIntakeUseCase` com `@Transactional`, na ordem da technical-spec (D1).

### Verificação

- `OpenServiceOrderIntakeUseCaseTest` (dublês para portas e casos de uso): ordem das chamadas; sem serviços → sem
  atribuição, sem diagnóstico e sem Estimate; serviços sem técnico → `ServiceOrderIntakeInvalidDataException`; nome e
  preço do request de diagnóstico vindos da cotação; Catalog Service arquivado e inexistente; Stock Item inativo e
  inexistente; snapshot montado a partir do resultado do Vehicle.

### Commit sugerido

`feat(servicelifecycle): add service order full intake use case`

## Checkpoint 5 — Endpoint HTTP, autorização e tradução de falhas

### Alterações

- `ServiceOrderController`: `POST /intake` → `201` com `Location`, anotado com `@Operation` e `@ApiResponses`.
- `ServiceLifecycleExceptionHandler`: handlers das exceções novas e de `DataIntegrityViolationException` →
  `409 CONCURRENT_REGISTRATION`, com mensagem genérica.
- `identity/SecurityConfig`: regra `POST /api/service-orders/intake` para `MANAGER` e `ADMIN`, declarada antes da regra
  geral de `/api/service-orders/**`.

### Verificação

- MockMvc: 201; 400 (Bean Validation e dados de domínio); 401 sem token; 403 com `TECHNICIAN`; mapeamento de cada 404 e
  409 da tabela da technical-spec.
- Regressão: `ServiceOrderControllerCreateTest` e os testes de segurança existentes passam sem alteração.

### Commit sugerido

`feat(servicelifecycle): expose service order full intake endpoint`

## Checkpoint 6 — Testes de integração ponta a ponta e atomicidade

### Alterações

- `ServiceOrderControllerIntakeTest`, contra o banco de teste, com fixtures próprias (Catalog Service, Stock Item,
  Technician).

### Verificação

Um teste por critério de aceite da functional-spec, incluindo:

- Customer e Vehicle novos com serviços → `AWAITING_APPROVAL`, Estimate gravado, preços iguais aos cadastros mesmo com
  `price` arbitrário enviado no JSON;
- reaproveitamento sem alterar o cadastro;
- abertura sem serviços → `RECEIVED`;
- **atomicidade:** Customer e Vehicle novos + Catalog Service arquivado → `409` e as contagens de `customers`,
  `vehicles`, `service_orders` e `estimates` iguais às de antes;
- falta de estoque → sucesso, com indisponibilidade registrada na Service Execution;
- `EstimateGenerated` entregue ao listener só em sucesso.

### Commit sugerido

`test(servicelifecycle): cover service order full intake end to end`

## Checkpoint 7 — OpenAPI, Postman e README

### Alterações

- Conferir no `/v3/api-docs` o endpoint novo com os códigos documentados.
- Postman: request "Abrir OS completa" (com e sem serviços), com script que captura `serviceOrderId` e `estimateId`.
- README: seção de teste manual (pré-requisitos, ordem das requests, variáveis e resultados esperados).

### Verificação

- Execução manual pelo responsável, com a aplicação rodando localmente: os dois exemplos do Postman retornam `201` e o
  `GET /api/estimates/{estimateId}` mostra os totais.

### Commit sugerido

`docs(servicelifecycle): document full intake in openapi, postman and readme`

## Checkpoint 8 — Revisão de segurança e gates finais

### Alterações

- Registrar abaixo a revisão de segurança final, item por item (a prévia está na technical-spec).
- Registrar o achado separado: `StockItemInactiveException` não é mapeado no endpoint de Diagnosis atual (possível
  `500`). Fica fora desta feature, para uma correção própria.
- Atualizar o status das specs e deste plano para `Implemented` só depois de todos os gates passarem.

### Verificação

- `make verify` verde, sem testes ignorados.
- `make coverage`: ≥ 80% no total, sem reduzir a cobertura do código alterado.
- `ModuleStructureTest` verde.

## Revisão de segurança

| Item | Resultado | Mitigação / evidência |
|---|---|---|
| Validação de entrada e mass assignment | **Achado médio, corrigido no CP8** | DTOs dedicados sem preço, nome, tipo, status ou dono (`OpenApiContractTest` trava a ausência de `price`); Bean Validation + validação de domínio. Achado: `"services": [null]` e `"parts": [null]` passavam pelo `@Valid` (elemento nulo não é validado em cascata) e causariam `NullPointerException` → `500`. Correção: `List<@NotNull ...>` nos dois DTOs; 2 casos novos em `ControllerTest.rejectsInvalidBodies...` (falharam antes da correção, passam depois) |
| Autenticação e autorização | OK | `SecurityConfig`: `POST /api/service-orders/intake` só `MANAGER`/`ADMIN`, antes da regra geral de `TECHNICIAN`. `ControllerTest`: sem token → 401; `TECHNICIAN` e `CUSTOMER` → 403 |
| Exposição de dados de Customer/Vehicle | OK (risco aceito) | A resposta traz só IDs, `created` e a OS; nenhum documento, e-mail ou telefone volta. `created=false` revela que o CPF já é cliente: aceito, porque só `MANAGER`/`ADMIN` chegam aqui e já têm `GET /api/customers/identify` |
| Segredos e logs sensíveis | OK | Nenhum segredo novo; nenhum log novo no código da feature (conferido por busca nos 50 arquivos alterados) |
| SQL, persistência e migrations | OK; migrations N/A | Sem schema novo. Só duas queries novas, JPQL com parâmetros nomeados (`VehicleJpaRepository`, lock pessimista); constraints únicas existentes como última barreira; concorrência → `409 CONCURRENT_REGISTRATION`. Validado no MySQL 8.4 real no CP7 |
| Respostas de erro e vazamento de informação | OK | Mensagens fixas, sem CPF, placa ou e-mail; mensagens de domínio repassadas só citam o nome do campo. Sem stack trace nem SQL na resposta; tipo de peça desconhecido vira exceção própria (falha técnica) |
| Dependências novas | N/A | Nenhuma dependência nova |
| Abuso do endpoint (volume, concorrência, manipulação de preço) | OK (risco residual aceito) | Preço sempre do catálogo/estoque (`FlowTest` envia 0,01 e recebe 150,00/45,90); máximo de 20 serviços e 20 peças por serviço (limita linhas travadas); IDs ordenados antes do lock (sem deadlock). Residual: não há rate limiting no projeto, mitigado por exigir `MANAGER`/`ADMIN` |

Achado registrado para correção separada (fora do escopo): no endpoint de Diagnosis atual, a
`StockItemInactiveException` de `stockprocurement` herda de `IllegalStateException` e, por isso, deve ser capturada
pelo handler genérico de `servicelifecycle` como `409 INVALID_STATE_TRANSITION`, um código enganoso, em vez de
`409 STOCK_ITEM_INACTIVE`. A hipótese anterior de `500` não se confirma pela leitura do código; confirmar com um teste
na correção própria.

## Evidências de verificação

Registrar aqui, por checkpoint, os comandos executados, os resultados (número de testes, cobertura) e as verificações
manuais.

### CP0 — 2026-10-16

- Branch `feat/terraform-san` em `66b8f10`; `git status --short` só com os arquivos locais não versionados
  (`contexto-proxima-sessao.md`, `teste.md`).
- `./mvnw -q test`: exit 0 em ~122 s. Surefire: 168 suítes, **828 testes, 0 falhas, 0 erros, 0 ignorados**.
- Banco de teste: H2 em memória (`MODE=MySQL`); não depende do Docker.

### CP1 — 2026-10-16

- Código: `customer/application/api` (nova named interface `customer-intake-api`),
  `IdentifyOrRegisterCustomerUseCase`, `VehicleIntake*` no pacote `vehicle/application/api` existente,
  `IdentifyOrRegisterVehicleUseCase` e `VehicleRepository.findByLicensePlateForUpdate` (JPA com `PESSIMISTIC_WRITE`).
- Testes novos: `IdentifyOrRegisterCustomerUseCaseTest` (7), `IdentifyOrRegisterVehicleUseCaseTest` (10) e
  `RegistrationIntakeApiIntegrationTest` (4, contra H2: exige transação, cadastra e reaproveita, placa de outro dono e
  **rollback do consumidor descarta Customer e Vehicle criados na mesma transação**). A busca por placa com lock é
  exercitada nesse teste de integração.
- `ModuleStructureTest`: 2/2 verdes.
- `./mvnw -q test`: 171 suítes, **849 testes** (828 + 21), 0 falhas, 0 erros, 0 ignorados.
- Uma falha intermediária (colisão da sequência de CPF de teste com `ServiceOrderControllerCreateTest`, ambos em
  `940_000_000`) foi corrigida movendo o novo teste para a faixa livre `960_000_000`.
- Refinamentos não materiais em relação à technical-spec, sem efeito no contrato HTTP:
  - `CustomerRepository.save` e `VehicleRepository.save` usam `saveAndFlush` e já traduzem a violação de unicidade no
    próprio `save`. Por isso a concorrência chega como `CustomerIntakeConflictException`/`VehicleIntakeConflictException`
    (públicas, no pacote `api`), e não como `DataIntegrityViolationException` no commit. O resultado HTTP previsto
    (`409 CONCURRENT_REGISTRATION`) não muda; o mapeamento fica no CP3/CP5.
  - O chassi duplicado em Vehicle novo é um outcome (`CHASSIS_ALREADY_REGISTERED`), e não uma exceção, como os demais
    casos de rejeição. O resultado HTTP previsto (`409 VEHICLE_CHASSIS_ALREADY_EXISTS`) não muda.
  - Customer inexistente ou inativo ao cadastrar o Vehicle (só possível com arquivamento concorrente entre os dois
    passos) lança `IllegalStateException`, mapeada hoje em `servicelifecycle` como `409 INVALID_STATE_TRANSITION`.

### CP2 — 2026-10-16

- Código: `CatalogServiceQuoteApi`/`CatalogServiceQuote` no pacote `servicecatalog/application/api` existente, com
  `QuoteCatalogServiceUseCase` (lock por `findByIdForUpdate`, como a checagem de disponibilidade);
  `stock/application/api` (nova named interface `stock-item-quote-api`) com `StockItemQuoteApi`/`StockItemQuote` e
  `QuoteStockItemsUseCase` (IDs deduplicados e ordenados antes do `findAllByIdForUpdate`, mesma ordem de lock da
  avaliação de estoque, para evitar deadlock). Ambas com `Propagation.MANDATORY`.
- Testes novos: `QuoteCatalogServiceUseCaseTest` (3), `QuoteStockItemsUseCaseTest` (3) e
  `QuoteApisTransactionIntegrationTest` (1, exige transação do consumidor).
- `ModuleStructureTest`: 2/2 verdes.
- `./mvnw -q test`: 174 suítes, **856 testes** (849 + 7), 0 falhas, 0 erros, 0 ignorados.

### CP3 — 2026-10-16

- Portas (`application/port`): `CustomerIntakePort`, `VehicleIntakePort`, `CatalogServiceQuotePort` e
  `StockItemQuotePort`, com records e enums aninhados próprios de `servicelifecycle`.
- Adapters: `RegistrationCustomerIntakeAdapter`, `RegistrationVehicleIntakeAdapter`,
  `RegistrationCatalogServiceQuoteAdapter` (em `infrastructure/registration`) e `StockItemQuoteAdapter` (novo
  `infrastructure/stock`). Traduzem `IllegalArgumentException` → `ServiceOrderIntakeInvalidDataException` (mensagem
  do domínio preservada; conferido que ela não repete documento, placa nem chassi) e os conflitos de concorrência →
  `ServiceOrderIntakeConcurrentRegistrationException`. Os outcomes de negócio (arquivado, outro dono etc.) voltam
  como status, e quem decide a rejeição é o caso de uso (CP4).
- Exceções novas: `ServiceOrderIntakeInvalidDataException`, `ServiceOrderIntakeCustomerArchivedException`,
  `ServiceOrderIntakeVehicleOwnershipConflictException`, `ServiceOrderIntakeVehicleChassisConflictException`,
  `ServiceOrderIntakeConcurrentRegistrationException` (decorre do refinamento do CP1) e
  `ServiceOrderStockItemInactiveException`.
- Tipo de Stock Item desconhecido: `StockItemQuoteContractException` (não mapeada → 500), e não a
  `IllegalArgumentException` crua do `valueOf`, que o `GlobalExceptionHandler` transformaria em
  `400 INVALID_STOCK_ITEM`.
- Testes novos: `RegistrationIntakeAdaptersTest` (15) e `StockItemQuoteAdapterTest` (5). Uma falha intermediária no
  próprio teste (re-stub de mock com `when(...)` após `thenThrow`) foi corrigida com `doThrow(...).when(...)`.
- `ModuleStructureTest`: 2/2 verdes.
- `./mvnw -q test`: 176 suítes, **876 testes** (856 + 20), 0 falhas, 0 erros, 0 ignorados.

### CP4 — 2026-10-16

- **Refinamento de localização (não material):** o orquestrador e seus DTOs ficam no novo subpacote
  `servicelifecycle/intake` (`application/usecase`, `application/dto`), e não em `serviceorder/application`. Motivo:
  o orquestrador chama `GenerateEstimateUseCase`, e `estimate` já depende de `serviceorder`; colocá-lo em
  `serviceorder` criaria um ciclo `serviceorder ↔ estimate` dentro do módulo. Pela mesma razão, o endpoint do CP5
  fica num controller próprio em `intake/infrastructure/web`, com o mesmo caminho `POST /api/service-orders/intake`.
  Continua no mesmo módulo Modulith; o contrato HTTP não muda.
- Código: `OpenServiceOrderIntakeUseCase` (`@Transactional`) e os DTOs `OpenServiceOrderIntakeRequest`,
  `IntakeCustomerRequest`, `IntakeContactInfoRequest`, `IntakeAddressRequest`, `IntakeVehicleRequest`,
  `IntakeServiceRequest`, `IntakePartRequest` e `ServiceOrderIntakeResponse`.
- Ordem: técnico obrigatório com serviços → Customer → Vehicle → `CreateServiceOrderUseCase` → (com serviços) cotação
  de Catalog Service (IDs ordenados) e Stock Item → `AssignDiagnosisAssigneeUseCase` → `PerformDiagnosisUseCase` →
  `GenerateEstimateUseCase` → releitura por `GetServiceOrderUseCase`. As cotações acontecem antes da atribuição, para
  que as rejeições de catálogo e estoque ocorram antes de qualquer mudança de diagnóstico.
- Técnico informado sem serviços é ignorado (não há atribuição).
- Testes novos: `OpenServiceOrderIntakeUseCaseTest` (14): snapshot vindo do Vehicle, sem serviços, técnico ignorado,
  serviços sem técnico, ordem das chamadas (`InOrder`), nome e preço vindos das cotações, contato ausente,
  Customer arquivado, cada status de Vehicle rejeitado, Catalog Service arquivado e inexistente, Stock Item
  inexistente e inativo, e serviço sem peças sem cotação de estoque.
- `ModuleStructureTest`: 2/2 verdes.
- `./mvnw -q test`: 177 suítes, **890 testes** (876 + 14), 0 falhas, 0 erros, 0 ignorados.

### CP5 — 2026-10-17

- `ServiceOrderIntakeController` (`intake/infrastructure/web`): `POST /api/service-orders/intake` → `201` com
  `Location: /api/service-orders/{id}`, `@Operation` e `@ApiResponses` (201, 400, 401, 403, 404, 409).
- `ServiceLifecycleExceptionHandler`: `VALIDATION_ERROR` (400), `CUSTOMER_ARCHIVED`,
  `VEHICLE_OWNED_BY_ANOTHER_CUSTOMER`, `VEHICLE_CHASSIS_ALREADY_EXISTS`, `STOCK_ITEM_INACTIVE` e
  `CONCURRENT_REGISTRATION` (409). Os demais códigos da tabela da technical-spec já existiam (`NOT_FOUND` vem do
  `GlobalExceptionHandler`).
- **Decisão:** `DataIntegrityViolationException` **não** foi mapeada no handler de `servicelifecycle`. Com o
  refinamento do CP1 (os repositórios traduzem a violação no `saveAndFlush`), a concorrência já chega como
  `ServiceOrderIntakeConcurrentRegistrationException`. Mapear a exceção genérica mudaria a resposta (de 500 para 409)
  de outros endpoints de `servicelifecycle`, o que está fora de escopo.
- `SecurityConfig`: `POST /api/service-orders/intake` → `MANAGER`/`ADMIN`, declarada antes da regra geral que concede
  `TECHNICIAN`.
- Testes novos: `ServiceOrderIntakeControllerTest` (22, use case mockado com `@MockitoBean`): 201 com `Location`
  e referências; 401 sem token; 403 para `TECHNICIAN` e `CUSTOMER`; 8 bodies inválidos → 400 sem chamar o caso de uso
  (sem customer, sem vehicle, triagem em branco, documento vazio, placa vazia, quantidade 0, serviço sem ID e 21
  serviços); 11 exceções → código HTTP e código estável corretos.
- Regressão: `ServiceOrderControllerCreateTest` e `SecurityAuthorizationTest` verdes, sem alteração.
- Estilo: corrigidas 6 linhas acima de 120 caracteres (2 delas em arquivos do CP4: `OpenServiceOrderIntakeUseCase` e
  seu teste); a checagem em todos os arquivos alterados desde o CP1 não acusa mais nenhuma.
- `./mvnw -q test`: 178 suítes, **912 testes** (890 + 22), 0 falhas, 0 erros, 0 ignorados.

### CP6 — 2026-10-17

- `ServiceOrderIntakeFlowTest` (9, `@SpringBootTest` contra H2, fixtures próprias via repositórios, sem seeds;
  `CustomerEstimateNotificationPort` substituído por `@MockitoBean` para observar a notificação pós-commit).
- Uma falha intermediária no próprio teste (`$.total` é um objeto `{value, currency}`) foi corrigida para
  `$.total.value`; o valor (241,80 = 150,00 + 2 × 45,90) já estava correto.
- `./mvnw -q test`: 179 suítes, **921 testes** (912 + 9), 0 falhas, 0 erros, 0 ignorados.

Rastreabilidade dos critérios de aceite da functional-spec:

| Critério | Teste |
|---|---|
| Customer e Vehicle novos + serviço com peças → `AWAITING_APPROVAL`, execução, Estimate | `FlowTest.opensWithNewRegistrationsDiagnosisAndEstimateUsingRegisteredPrices` |
| Reaproveitamento sem alterar cadastros | `FlowTest.reusesExistingRegistrationsWithoutChangingThem` |
| Customer existente + placa nova | `FlowTest.registersNewVehicleForAnExistingCustomer` |
| Sem serviços → `RECEIVED`, sem Diagnosis e sem Estimate | `FlowTest.opensWithoutServicesInReceivedStatus` |
| `VehicleSnapshot` igual ao cadastro | `FlowTest.opensWithNew...` e `reusesExisting...`; `UseCaseTest.opensServiceOrderWithoutServices...` |
| Nome e preço vindos dos cadastros, mesmo com `price` no JSON | `FlowTest.opensWithNew...` (envia 0,01; espera 150,00 e 45,90; total 241,80) |
| Placa de outro Customer → 409 | `FlowTest.rejectsPlateOwnedByAnotherCustomer` |
| Customer, Vehicle ou Catalog Service arquivado → 409 | `FlowTest.rollsBackNewRegistrationsWhenALaterStepFails` (catálogo); `UseCaseTest` + `ControllerTest` (Customer e Vehicle) |
| Technician, Catalog Service ou Stock Item inexistente → 404 | `FlowTest.rejectsUnknownTechnicianAndStockItemAsNotFound`; `UseCaseTest` + `ControllerTest` (catálogo) |
| Serviços sem técnico → 400 | `UseCaseTest.rejectsServicesWithoutTechnician...` + `ControllerTest` (mapeamento) |
| Dados inválidos → 400 | `ControllerTest.rejectsInvalidBodies...` (8 casos) + `RegistrationIntakeAdaptersTest` (validação de domínio) |
| **Atomicidade:** falha depois do cadastro não deixa nada gravado | `FlowTest.rollsBackNewRegistrationsWhenALaterStepFails` (contagens de `customers`, `vehicles`, `service_orders` e `estimates` iguais; placa ausente; sem notificação) |
| Falta de estoque não rejeita e fica registrada | `FlowTest.recordsStockShortageWithoutRejectingTheIntake` |
| Stock Item inativo → 409 | `FlowTest.rejectsInactiveStockItemAsConflict` |
| `TECHNICIAN`/sem token não acessam | `ControllerTest.requiresAuthentication` e `rejectsTechnicianAndCustomerRoles` |
| Endpoints existentes inalterados | suíte completa verde sem alterar testes existentes |
| Notificação do Estimate só após o commit | `FlowTest.opensWithNew...` (chega) e `rollsBack...` (nunca chega) |
| OpenAPI, Postman e README | CP7 |

### CP7 — 2026-10-17

- OpenAPI: em vez de só conferir o `/v3/api-docs` manualmente, o contrato ficou travado em
  `OpenApiContractTest.documentServiceOrderFullIntakeContract`: caminho `POST /api/service-orders/intake` com
  respostas 201, 400, 401, 403, 404 e 409; `customer`, `vehicle` e `initialAssessment` obrigatórios; nenhuma
  propriedade `price` em `IntakeServiceRequest` nem em `IntakePartRequest`; `estimateId` na resposta.
  `./mvnw -q test -Dtest=OpenApiContractTest`: 20 testes, 0 falhas.
- Achado na conferência manual: sem `@Tag`, o Swagger UI agrupava o endpoint em `service-order-intake-controller`,
  longe de "Service Orders". O controller recebeu `@Tag(name = "Service Orders")`, e o teste de contrato passou a
  conferir a tag. `OpenApiContractTest` (20) e `ServiceOrderIntakeControllerTest` (22) verdes.
- Postman: pasta nova `Service Lifecycle — Full Intake` com `Open full intake with services`,
  `Open full intake without services` e `Get full intake estimate`. CPF e placas são gerados a cada execução; o
  segundo exemplo reaproveita o Customer do primeiro (`customer.created: false`).
- **Refinamento não material:** os IDs são capturados em variáveis próprias (`intakeServiceOrderId`,
  `intakeEstimateId` e `intakeReceivedServiceOrderId`) em vez de `serviceOrderId`/`estimateId`, para não
  sobrescrever as variáveis do fluxo principal quando as pastas forem executadas fora de ordem.
- `Makefile`: os três requests entram no fim de `E2E_STEPS` (`make e2e`).
- README: seção "Abertura completa da OS" (pré-requisitos, variáveis, ordem, resultados e falhas esperadas) e
  menção a `/intake` na tabela de recursos.
- Execução manual pelo responsável (2026-10-17), Postman contra `docker compose` com MySQL 8.4: os pré-requisitos
  (login, catalog service, technician, stock item) e os três requests da pasta `Service Lifecycle — Full Intake`
  passaram em todos os `pm.test`. `Open full intake with services` → `201`; `Open full intake without services` →
  `201`; `Get full intake estimate` → `200`, `status: "SENT"`, uma linha com `servicePrice` 150,00, 2 × `priceSnapshot`
  45,90 e `total.value` 241,80 BRL (150,00 + 2 × 45,90); `stockAvailability` `AVAILABLE` (20 em saldo, falta 0).
- Achado operacional (fora do escopo, sem mudança versionada): a imagem antiga (10/10) não tinha o endpoint, e o
  primeiro `docker compose up -d --build app` falhou com `exec ./wait-for-mysql.sh: no such file or directory`,
  porque o `core.autocrlf=true` entregou o script com CRLF na cópia de trabalho. A cópia local foi convertida para
  LF (o índice do git já tinha LF; o diff de conteúdo é vazio). A correção definitiva (`*.sh text eol=lf` no
  `.gitattributes`) fica para o CP1 da infra, que também remove o script (D2).

### CP8 — 2026-10-17

- `make` não existe na máquina (Windows); `make verify` e `make coverage` equivalem a `./mvnw clean verify`
  (ver `Makefile`), que foi o comando executado.
- Primeira execução, antes das correções: 922 testes, 0 falhas, 0 ignorados; gate JaCoCo atendido; 94,3% de
  instruções. Lacunas no código da feature: `IntakeAddressRequest` 0/24 e `OpenServiceOrderIntakeUseCase` 438/456
  (mapeamento do endereço sem teste).
- Correções: `List<@NotNull ...>` em `OpenServiceOrderIntakeRequest.services` e `IntakeServiceRequest.parts` (ver a
  revisão de segurança); 3 corpos inválidos novos em `ServiceOrderIntakeControllerTest` (serviço nulo, peça nula e
  `state` com 3 letras); `OpenServiceOrderIntakeUseCaseTest.passesEveryAddressFieldToTheRegistration`. A correção do
  DTO não muda o contrato aprovado: a technical-spec já previa `400` para dados inválidos.
- Execução final (`./mvnw clean verify`): **926 testes**, 0 falhas, 0 erros, 0 ignorados; `ModuleStructureTest` verde
  (2); "All coverage checks have been met"; **94,5%** de instruções, 95,3% de linhas e 77,8% de branches no total
  (gate do projeto: 80% de instruções). Código da feature: `OpenServiceOrderIntakeUseCase` 456/456 instruções e
  39/41 branches; `IntakeAddressRequest` 24/24; adapters, APIs de cotação e controller com 100%.
- Specs: `functional-spec.md` e `technical-spec.md` continuam `Approved`, seguindo a convenção das demais features
  (`jwt-authentication`, `notifications-so-finalized`): só o plano passa a `Implemented`.

## Rollback ou recuperação

Mudança aditiva, sem migration: reverter os commits da feature remove o endpoint, sem efeito em dados existentes nem em
contratos atuais. Registros criados pelo endpoint antes do rollback continuam válidos, porque são Customers, Vehicles,
Service Orders e Estimates comuns.

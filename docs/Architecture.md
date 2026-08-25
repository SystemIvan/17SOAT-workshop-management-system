# Architecture

> Baseline arquitetural consolidada em 10 de agosto de 2026 e sincronizada em 11 de agosto de 2026 a partir do
> bloco oficial **Tech Challenge**, dos artefatos do grupo no Miro, do repositório e das decisões explícitas em
> `Architecture-Decisions.md`. Este documento não transforma decisões pendentes em arquitetura aprovada.

## Como ler este documento

As informações são classificadas da seguinte forma:

- **A — Requisito oficial:** texto associado ao título exato **Tech Challenge** no Miro.
- **B — Desenho do grupo no Miro:** modelagem, RFC, ADR ou diagrama produzido pelo grupo.
- **I — Implementado:** evidência encontrada no código/configuração atual do repositório.
- **P — Estrutura pretendida:** direção definida no autoritativo `docs/PROJECT-STRUCTURE.md`.
- **R — Decisão resolvida:** opção explicitamente aceita e registrada em `docs/Architecture-Decisions.md`.
- **C — Assunção:** interpretação necessária para conectar artefatos; nunca é tratada como decisão.
- **D — Lacuna:** informação ausente, contraditória ou ainda não decidida.

Quando fontes divergem, todas são registradas e a divergência aparece nas comparações e na seção 12.
`PROJECT-STRUCTURE.md` é a fonte autoritativa para a organização pretendida do repositório; o Miro é a fonte do
desenho de domínio; `Architecture-Decisions.md` registra quais escolhas foram efetivamente aceitas; o código é a
única evidência de implementação. Nenhuma dessas perspectivas, isoladamente, prova que uma funcionalidade esteja
concluída.

## Atualização de implementação — 21 de agosto de 2026 (I)

A feature `stock-item-reservation` introduziu a reserva atômica de materiais já aprovada nas respectivas specs. O
estado abaixo descreve a implementação corrente; as seções históricas deste documento permanecem como registro da
baseline consolidada em 10 de agosto.

- Os módulos diretos atuais são `registration`, `servicelifecycle` e `stockprocurement`; Notification não é bounded
  context. Cada consumidor possui sua porta outbound de notificação.
- `StockReservation`, em `stockprocurement.stockreservation`, é um aggregate independente com linhas imutáveis,
  estados `ACTIVE` e `CONSUMED` e, no máximo, uma reserva por `serviceExecutionId`.
- Service Lifecycle acessa Stock & Procurement apenas pela named interface
  `stockprocurement.stockreservation.application.api` (`stock-reservation-api`). A Service Execution conserva apenas
  `stockReservationId`, congela seus requirements na geração da Estimate e usa `AWAITING_ITEMS` quando a aprovação
  comercial não consegue reservar o conjunto integral.
- A migration `V20260821014516__create_stock_reservations.sql` cria as tabelas de reserva, preserva o backfill
  necessário e não inclui seed de negócio. A criação e o consumo usam locks de escrita para preservar saldo e
  idempotência sob concorrência.
- Os contratos HTTP incluem tentativa de retry da reserva pela execução, consulta por reserva ou execução e consumo
  integral. O OpenAPI gerado pela aplicação é a fonte de verdade e a coleção Postman contém exemplos `RESERVED`,
  `NOT_RESERVED`, `ACTIVE` e `CONSUMED`.
- As notificações de Stock Manager e Technician são disparadas por eventos internos com
  `@TransactionalEventListener(AFTER_COMMIT)`: falhas de entrega são registradas sem desfazer a reserva ou a decisão.

## Atualização de implementação — 24 de agosto de 2026 (I)

A feature `jwt-authentication` introduziu o quarto módulo Spring Modulith, resolvendo AD-016.

- Os módulos diretos passam a ser `registration`, `servicelifecycle`, `stockprocurement` e `identity`. O módulo
  `identity` (pacote `identity.auth`) é dono exclusivo de credenciais e do mapeamento papel→ID de domínio;
  `Customer` e `Technician` continuam apenas referências por UUID, sem carregar senha ou papel.
- Todos os endpoints administrativos existentes passam a exigir um JWT válido (`Authorization: Bearer`), com
  autorização por papel (`CUSTOMER`, `TECHNICIAN`, `MANAGER`, `ADMIN`) aplicada via `SecurityConfig` e
  `JwtAuthenticationFilter`, ambos hospedados dentro do módulo `identity` para evitar um ciclo com a raiz da
  aplicação.
- A migration `V20260824120000__create_user_accounts.sql` cria a tabela `user_accounts`;
  `V20260824120001__seed_bootstrap_admin_account.sql` insere a conta `admin`/`ADMIN` como dado de referência
  obrigatório (não um seed de demonstração), necessária para o bootstrap de qualquer outra conta via
  `POST /api/auth/users`.
- `docs/adr/ADR-003-authentication-strategy.md` foi promovida de `Proposed` para `Accepted`. Ver
  `docs/features/platform/jwt-authentication/` para a trilha SDD completa.

## 1. Tech Challenge overview

### 1.1 Problema oficial (A)

O desafio pede o MVP do back-end de um sistema integrado para uma oficina mecânica de médio porte. O processo
atual depende de anotações manuais e planilhas, causando erros de priorização, falhas no controle de peças e
insumos, baixa visibilidade do andamento dos serviços, perda de histórico e ineficiência no fluxo de orçamento
e autorização.

O resultado esperado é um sistema monolítico que organize clientes, veículos, serviços, peças e ordens de
serviço, permitindo ao cliente acompanhar o reparo e autorizar serviços adicionais. DDD, qualidade de software,
segurança, testes e execução local por containers fazem parte explícita da entrega.

### 1.2 Interpretação de domínio do grupo (B)

O grupo definiu **Transparent Service Order Lifecycle Management** como core subdomain. O diferencial do produto
não é apenas registrar uma Service Order, mas oferecer transparência operacional, rastreamento próximo de tempo
real, visibilidade para o Customer e automação de todo o ciclo diagnóstico → orçamento → autorização → execução
→ entrega.

Essa escolha explica por que `ServiceExecution` possui estado granular e por que a Service Order apresenta um
status-resumo sem esconder o estado individual de cada serviço.

## 2. Requirements

### 2.1 Requisitos funcionais oficiais (A)

#### Criação da Service Order

- Identificar o cliente por CPF ou CNPJ.
- Cadastrar veículo com placa, marca, modelo e ano.
- Incluir os serviços solicitados.
- Incluir peças e insumos necessários.
- Gerar o orçamento automaticamente a partir de serviços e peças.
- Enviar o orçamento ao cliente para aprovação.

#### Acompanhamento

- Representar os estados **Recebida**, **Em diagnóstico**, **Aguardando aprovação**, **Em execução**,
  **Finalizada** e **Entregue**.
- Alterar os estados automaticamente conforme as ações no sistema.
- Disponibilizar consulta por API para o Customer acompanhar o progresso.

#### Gestão administrativa

- CRUD de Customer, Vehicle, Service Catalog e peças/insumos.
- Controlar estoque.
- Listar e detalhar Service Orders.
- Monitorar o tempo médio de execução dos serviços.

#### Segurança e qualidade

- Autenticar com JWT as APIs administrativas.
- Validar CPF/CNPJ e placa de veículo.
- Testar unitária e integralmente os fluxos principais.

### 2.2 Requisitos técnicos e entregáveis oficiais (A)

- Back-end monolítico; arquitetura em camadas é admitida para o MVP.
- Banco de dados de livre escolha, com justificativa documentada.
- APIs RESTful documentadas por Swagger ou solução equivalente.
- `Dockerfile` para build e `docker-compose.yml` para o ambiente completo.
- Cobertura automatizada mínima de 80% nos domínios críticos.
- Execução local simples e explicada no `README.md`.
- Documentação DDD com Event Storming dos fluxos de criação/acompanhamento da OS e gestão de estoque,
  diagramas da disciplina e Ubiquitous Language.
- Vídeo de demonstração de até 15 minutos, código-fonte, relatório de vulnerabilidades e PDF de entrega.

O próprio texto oficial contém uma inconsistência sobre visibilidade do repositório: em um ponto solicita
repositório privado com acesso ao usuário `soatarchitecture`; em outro, lista código-fonte em repositório público.
O grupo precisa confirmar a regra operacional de entrega.

### 2.3 Refinamento funcional do grupo (B)

O documento **Levantamento de Requisitos e Refinamento Técnico** detalha 33 requisitos internos:

- RF01–RF08: Registrations (Customer, Vehicle e Service Catalog).
- RF09–RF18: criação da SO, diagnóstico, Estimate, decisão por linha, expiração e reparo adicional.
- RF19–RF24: atribuição, início, progresso, conclusão, tracking e entrega.
- RF25–RF30: disponibilidade, reserva atômica, Purchase Order, recebimento e nível baixo de Stock.
- RF31–RF33: notificações de Estimate, baixo estoque e finalização da SO.

Esses RFs são refinamentos do grupo e não substituem o texto oficial. Alguns ampliam o escopo, como expiração de
Estimate, compra com fornecedor externo, notificações operacionais e aprovação/reprovação por linha.

## 3. Domain overview

### 3.1 Subdomínios (B)

| Tipo | Subdomínios identificados |
|---|---|
| Core | Transparent Service Order Lifecycle Management (Service Order + Estimate) |
| Supporting | Customer, Vehicle, Stock e Service Catalog |
| Generic | Identity & Access Management, Validation, Notification e Performance Analytics |

### 3.2 Conceitos centrais (B)

- **Customer:** pessoa física ou jurídica, dona do Vehicle e responsável por decisões comerciais.
- **Vehicle:** veículo identificado por placa e vinculado a um Customer.
- **Service Order (SO):** coordena o reparo do diagnóstico à entrega.
- **Diagnosis:** ciclo em que o Technician identifica serviços e materiais necessários.
- **Estimate:** snapshot comercial de um diagnóstico enviado ao Customer.
- **Estimate Line:** decisão e valores apresentados no orçamento; pode representar serviço ou material.
- **Service Execution:** trabalho concreto pertencente à SO, criado no diagnóstico e executado após autorização.
- **Stock Requirement:** material necessário a uma Service Execution, incluindo part, consumable ou supply.
- **Stock / Stock Item:** disponibilidade, reserva e movimentação de materiais.
- **Purchase Order:** reposição de materiais indisponíveis ou abaixo do nível mínimo.
- **Service Catalog:** definição e preço-base dos serviços oferecidos.
- **Technician:** ator que diagnostica e executa; na modelagem Miro mais recente, é usuário autenticado por ID.
- **Notification:** contexto reativo que consome eventos e envia comunicações.

### 3.3 Atores (B)

| Ator | Responsabilidade |
|---|---|
| Customer | Aprovar/reprovar linhas da Estimate e acompanhar a SO |
| Service Advisor | Abrir a SO, definir prioridade e intermediar o contato com o Customer |
| Technician | Diagnosticar, executar e atualizar serviços |
| Stock Manager | Gerenciar reservas, reposições e Purchase Orders |
| External Supplier System | Receber/retornar dados de compra por integração protegida por ACL |

## 4. Domain-Driven Design

### 4.1 Princípios de fronteira (B)

1. Uma transação modifica um único aggregate.
2. Referências entre aggregates são feitas por ID.
3. Dados históricos relevantes são copiados como snapshots.
4. Coordenação entre aggregates e contextos ocorre por policies que reagem a domain events.
5. Modelos internos não são compartilhados entre bounded contexts.

### 4.2 Bounded contexts (B)

| Bounded Context | Papel | Aggregate roots / conteúdo |
|---|---|---|
| Registrations | Supporting | Customer, Vehicle, ServiceCatalog |
| Service Lifecycle | Core | ServiceOrder, Estimate; ServiceExecution dentro de ServiceOrder |
| Stock & Procurement | Supporting | Stock, PurchaseOrder; StockItem dentro de Stock |
| Notification | Generic | Reações a eventos, sem aggregate ou invariantes próprios |

O texto **6. Bounded Contexts & Context Map** chama a lista de “três contextos” e omite Notification da tabela,
enquanto o refinamento técnico e a RFC registram quatro. Este documento não escolhe silenciosamente entre essas
versões; usa quatro como intenção mais recente e mantém a divergência como lacuna.

### 4.3 Aggregates, entities e value objects (B)

#### ServiceOrder

É a aggregate root do ciclo operacional. Possui `customerId`, `vehicleId`, `VehicleSnapshot`, prioridade,
referências de Estimates aprovadas e `ServiceExecution`s. Toda mutação de uma execução passa pela root.

Invariantes principais:

- uma execução só inicia quando está autorizada e pronta;
- execuções rejeitadas são terminais e não bloqueiam a conclusão;
- reparo adicional cria novo Diagnosis, nova Estimate e novo lote de execuções;
- mudanças em Vehicle ou Service Catalog não reescrevem snapshots históricos.

#### ServiceExecution

Entidade rica dentro de ServiceOrder. Estados refinados:

`pending → authorized → ready/awaiting_part → in_progress → completed`, com `rejected` como estado terminal.

Mantém `diagnosisId`, serviço e preço em snapshot, `authorizedByEstimateId`, `assignedTechnicianId` e uma lista de
`StockRequirement`s.

#### Estimate

Aggregate separado no mesmo contexto Service Lifecycle. Referencia `serviceOrderId` e `diagnosisId`, contém linhas
em snapshot, datas de criação/expiração e decisões comerciais. A versão mais recente define decisão por
`EstimateLineService` e encerra a Estimate quando todas as linhas foram decididas.

#### Registrations

- **Customer:** `TaxId`, nome e contato; CPF/CNPJ deve ser válido e único. AD-002 aceita `TaxId` como VO imutável
  do domínio; o repositório atual ainda usa `String document` (R/I).
- **Vehicle:** `LicensePlate`, `ChassisNumber`, dados descritivos, quilometragem e `customerId`. AD-003 o mantém
  como aggregate root independente e seleciona `customer` como destino físico, condicionado à decisão de time
  AD-001 (R/D).
- **ServiceCatalog:** nome e `Money` como preço-base. AD-004 o mantém como aggregate root independente no mesmo
  destino físico condicional, e consumidores preservam nome/preço por snapshot (R/D).
- **Lifecycle:** AD-005 define remoção de Customer, Vehicle e ServiceCatalog como desativação/arquivamento lógico;
  registros históricos permanecem consultáveis e não são apagados fisicamente (R).

#### Stock & Procurement

- **Stock:** aggregate que possui `StockItem`s e executa reserva atômica “tudo ou nada”.
- **StockItem:** entidade com quantidades available/reserved e nível mínimo.
- **PurchaseOrder:** aggregate com `PurchaseLine`s e estados `created`/`closed`.

#### Value objects identificados

`VehicleSnapshot`, `StockRequirement`, `EstimateLineService`, `EstimateLinePart`, `TaxId`, `Email`, `Phone`,
`Address`, `LicensePlate`, `ChassisNumber`, `Money` e `PurchaseLine`.

### 4.4 Domain events e policies (B)

Eventos principais incluem SO criada/diagnosticada/finalizada; Estimate gerada/enviada/expirada; linha aprovada ou
reprovada; execução iniciada/atualizada/concluída; Stock Items disponíveis/indisponíveis/reservados; baixo estoque;
Purchase Order criada/fechada; e recebimento de material.

Policies documentadas:

| Evento | Reação | Destino |
|---|---|---|
| SO Diagnosticada | Gerar Estimate | Estimate |
| Estimate Gerada | Notificar Customer | Notification |
| Linha da Estimate aprovada | Autorizar execução e solicitar reserva | ServiceOrder / Stock |
| Linha da Estimate reprovada | Rejeitar execução | ServiceOrder |
| Estimate Expirada | Encerrar/cancelar o escopo afetado | ServiceOrder |
| Stock Items indisponíveis | Criar Purchase Order | PurchaseOrder |
| Purchase Order fechada | Registrar recebimento | Stock |
| Stock Items reservados | Liberar execução para `ready` | ServiceOrder |
| Low Level identificado | Notificar Stock Manager | Notification |
| Todos os serviços concluídos | Finalizar SO e notificar Customer | ServiceOrder / Notification |

### 4.5 Ubiquitous Language (B)

O vocabulário normativo do grupo é o documento **3. Ubiquitous Language**. A implementação e novas histórias devem
usar os termos Customer, Vehicle, Service Order, Diagnosis, Estimate, Estimate Line, Service Execution,
Stock Requirement, Stock, Stock Item, Purchase Order, Notification, Service Catalog e Technician com os
significados definidos nele. `Stock` foi escolhido em vez de `Inventory`, e `Technician` representa um papel no
processo, não apenas uma profissão.

## 5. System architecture

### 5.1 Estilo arquitetural (B)

O estilo aceito na RFC é um **monólito modular**: um processo, um artefato e um deploy, com um único módulo Maven e
um pacote de topo por bounded context. A opção escolhida combina arquitetura em camadas/hexagonal dentro de cada
contexto com Spring Modulith para verificar fronteiras durante os testes.

### 5.2 Camadas e responsabilidades (B)

| Camada | Responsabilidade |
|---|---|
| Domain | Aggregates, entities, VOs, invariantes, domain events e contratos de repository |
| Application | Use cases, portas de entrada/saída, coordenação transacional e DTOs |
| Infrastructure | REST controllers, persistência JPA, adapters, segurança e integrações externas |
| Bootstrap/transversal | Inicialização Spring e preocupações realmente compartilhadas |

Controllers não expõem entidades de domínio. Use cases orquestram o domínio, e adapters implementam contratos sem
levar detalhes de JPA, HTTP ou fornecedor para os aggregates.

### 5.3 Fronteiras de módulo (B)

A RFC aceita a “Opção 2”: pacotes por contexto e fronteiras verificadas por teste. O repositório já utiliza
`@ApplicationModule` e `ApplicationModules.verify()`, confirmando a adoção de Spring Modulith. A comunicação
planejada é por API pública restrita, ports/adapters e domain events; importações de detalhes internos de outro
contexto são proibidas.

AD-003 e AD-004 selecionam Vehicle e ServiceCatalog como aggregates independentes sob `customer`, mas essa
localização só pode ser materializada se o time resolver AD-001 confirmando que o módulo físico `customer` hospeda
o contexto conceitual Cadastros. Até lá, não se criam esses pacotes nem se altera a fronteira do módulo.

## Current Repository State

Esta seção usa somente evidência do checkout atual na branch de documentação (I). A presença de classes ou
endpoints indica implementação existente, mas não implica aderência completa a todos os requisitos nem qualidade
de produção.

### Estrutura e stack implementadas (I)

- Um módulo Maven com Java 21, Spring Boot 4.1.0 e Spring Modulith 2.1.0.
- Pacote raiz `br.com.fiap.workshop_management_system`.
- Quatro módulos de topo: `customer`, `technician`, `parts` e `serviceorder`.
- Em cada módulo existem as camadas `domain`, `application` e `infrastructure` conforme aplicável.
- Spring Data JPA, MySQL em runtime e H2 em testes.
- `Dockerfile` multi-stage e `docker-compose.yml` com aplicação + MySQL.
- `ErrorResponse` e `GlobalExceptionHandler` como componentes transversais no pacote raiz.
- `ModuleStructureTest` executa `ApplicationModules.verify()` e gera documentação do Modulith.

### Módulos e capacidades encontradas (I)

| Módulo atual | Domain model | Application/infrastructure e API encontradas |
|---|---|---|
| `customer` | Customer aggregate e ContactInfo VO | Create/Get/List/Rename/UpdateContact, JPA adapter e `/api/customers` |
| `technician` | Technician aggregate, Specialty e TechnicianStatus | Create/Get/List/Rename/UpdateStatus, JPA adapter e `/api/technicians` |
| `parts` | Part aggregate, Price e Quantity | Create/Get/List/Rename/UpdatePrice/IncreaseStock/DecreaseStock, JPA adapter e `/api/parts` |
| `serviceorder` | ServiceOrder aggregate, ServiceExecution entity e VOs de diagnóstico, veículo, dinheiro e estoque | Create/Get/GetStatus/Diagnosis/Assign/Start/Progress/Complete/Finalize, JPA adapter e `/api/service-orders` |

O `serviceorder` atual já implementa os estados `RECEIVED`, `IN_DIAGNOSIS`, `AWAITING_APPROVAL`,
`AWAITING_PART`, `IN_PROGRESS`, `COMPLETED` e `DELIVERED`. A execução implementa `PENDING`, `AUTHORIZED`,
`REJECTED`, `AWAITING_PART`, `READY`, `IN_PROGRESS` e `COMPLETED`. O status-resumo é materializado em
`statusSnapshot` e recalculado por comandos relevantes.

### Endpoints atualmente expostos (I)

- Customer: criar, buscar por ID, listar, renomear e atualizar contato.
- Technician: criar, buscar por ID, listar, renomear e atualizar status.
- Part: criar, buscar por ID, listar, renomear, aumentar/diminuir estoque e atualizar preço.
- Service Order: criar, buscar por ID, consultar status, diagnosticar, atribuir Technician, iniciar/atualizar/
  concluir execução e finalizar a SO.

Não há endpoint de listagem de Service Orders no controller atual. Também não foram encontrados endpoints ou
módulos para Vehicle, Service Catalog, Estimate, Purchase Order, Notification ou autenticação.

### Persistência, containers e testes atuais (I)

- Cada módulo possui interface de repository no domain e implementação/JPA mapper na infrastructure.
- A aplicação usa `spring.jpa.hibernate.ddl-auto=update`; não foram encontradas migrations Flyway/Liquibase.
- O Compose persiste MySQL em volume e aguarda o health check do banco antes de iniciar a aplicação.
- Há testes unitários para Customer, Technician, Part, ServiceOrder e ServiceExecution, além do smoke test Spring
  e do teste de fronteiras do Modulith.
- Não foram encontrados testes próprios de use case, controller ou persistência.
- JaCoCo não está configurado no `pom.xml`; o workflow de CI e seu check de cobertura estão totalmente comentados.
- Spring Security/JWT, Swagger/OpenAPI, Actuator e cache não aparecem nas dependências atuais.

### Documentação e configuração atuais (I)

- `docs/PROJECT-STRUCTURE.md` descreve corretamente a organização modular atual e é a direção estrutural futura.
- `README.md` contém apenas uma descrição curta; as instruções de execução estão concentradas em `DOCKER.md`.
- `DOCKER.md` referencia `.env.example`, Actuator e listagem de Service Orders, mas o repositório possui
  `env.example`, não inclui Actuator e não expõe `GET /api/service-orders`. Essas instruções precisam de revisão.

## Intended Project Structure

`docs/PROJECT-STRUCTURE.md` é autoritativo para a organização de implementação (P). Ele define um monólito modular
com os módulos **Customer**, **Technician**, **Parts** e **Service Order**, cada um como pacote direto da raiz e
anotado com `@ApplicationModule`.

### Forma interna obrigatória (P)

```text
<bounded-context>/
├── domain/
│   ├── model/
│   └── repository/
├── application/
│   ├── dto/
│   └── usecase/
└── infrastructure/
    ├── persistence/
    └── web/
```

- Domain concentra regras puras, aggregates, entities, VOs e contratos de repository.
- Application contém um use case por classe e DTOs que protegem o modelo de domínio.
- Infrastructure contém JPA entities/repositories/mappers, implementações de ports e REST controllers.
- Código transversal verdadeiramente compartilhado permanece no pacote raiz.
- Módulos atuais se referenciam somente por UUID. Se surgir comunicação direta, o consumidor declara um port em
  `application` e a infrastructure fornece o adapter; nenhum domain interno de outro módulo pode ser importado.
- `ModuleStructureTest` deve continuar impondo a fronteira em todo `mvn test`.

### Relação com a arquitetura do Miro (P/B/D)

Os princípios estruturais são compatíveis: monólito, fronteiras explícitas, camadas internas, IDs, ports/adapters e
Spring Modulith. A decomposição de bounded contexts, porém, não é a mesma:

| `PROJECT-STRUCTURE.md` (P) | Miro mais recente (B) | Relação observada |
|---|---|---|
| `customer` | Registrations | Implementa apenas Customer; AD-003/AD-004 selecionam Vehicle e ServiceCatalog neste módulo, mas a localização permanece condicionada à confirmação de AD-001 pelo time |
| `technician` | Technician é ator/usuário, não aggregate | Conflito de modelagem: o repositório/intenção estrutural o tratam como módulo e aggregate |
| `parts` | Stock & Procurement | Implementa Part individual e quantidade; não equivale ao aggregate Stock + StockItem + PurchaseOrder |
| `serviceorder` | Service Lifecycle | Alinhamento parcial; ServiceOrder/ServiceExecution existem, Estimate não |
| Sem módulo correspondente | Notification | A estrutura pretendida não define onde o contexto reativo ficará |

`PROJECT-STRUCTURE.md` decide como o código atual e futuro deve ser organizado, mas não resolve sozinho o mapeamento
entre módulos físicos e contexts do Miro. Ivan escolheu em AD-003/AD-004 que Vehicle e ServiceCatalog ficarão como
aggregates independentes dentro de `customer`, desde que AD-001 confirme que esse módulo representa Cadastros. Por
isso, a estrutura autoritativa ainda não é alterada. Estimate, PurchaseOrder e Notification continuam inteiramente
sujeitos às decisões dos respectivos responsáveis/time; este documento não cria módulos nem move arquivos.

## Current vs. Intended State

| Área | Current implementation (I) | Intended structure (P) | Miro design (B) | Tech Challenge / situação |
|---|---|---|---|---|
| Forma do sistema | Monólito Spring Boot, um Maven module | Monólito modular com Spring Modulith | Monólito modular/camadas hexagonais | Alinhado estruturalmente |
| Fronteiras | `customer`, `technician`, `parts`, `serviceorder` verificadas por teste | Os mesmos quatro módulos | Registrations, Service Lifecycle, Stock & Procurement, Notification | Nomes e responsabilidades precisam ser reconciliados |
| Customer | Aggregate, JPA, use cases e REST; `document` ainda é String | Módulo Customer | Aggregate dentro de Registrations | AD-002 aceita TaxId imutável e AD-005 desativação lógica; implementação ainda pendente |
| Vehicle | Snapshot/ID apenas em ServiceOrder | Nenhum local explícito; AD-003 seleciona `customer` condicionado a AD-001 | Aggregate em Registrations | Jira pode ser planejado; código RF03–RF06 aguarda AD-001 |
| Service Catalog | `catalogServiceId`, nome/preço em snapshot | Nenhum local explícito; AD-004 seleciona `customer` condicionado a AD-001 | Aggregate em Registrations | Jira pode ser planejado; código RF07–RF08 aguarda AD-001 |
| Technician | Aggregate, módulo, persistência e API | Módulo Technician | Ator autenticado por ID | Inconsistência explícita de modelagem |
| Parts/Stock | Part com preço e quantidade | Módulo Parts | Stock aggregate, StockItem entity e PurchaseOrder | Controle básico implementado; procurement/reserva atômica ausentes |
| Service Order | Aggregate, executions, status e principais endpoints | Core em `serviceorder` | ServiceOrder + Estimate no Service Lifecycle | Fluxo operacional parcial; Estimate ausente |
| Estimate | Apenas IDs/flags de integração em ServiceOrder | Sem localização explícita | Aggregate com linhas, decisão e expiração | Requisito de orçamento ainda não implementado |
| Notification | Ausente | Sem localização explícita | Contexto genérico reativo | Canal e implementação ausentes |
| Supplier integration | Ausente | Adapter futuro seria infrastructure | Gateway + ACL | Ampliação do grupo ainda não implementada |
| JWT/autorização | Ausente | Preocupação transversal/infrastructure ainda não descrita | ADR aceita Spring Security + JWT | Requisito oficial não implementado |
| API docs | REST controllers sem OpenAPI | DTOs/controllers por módulo | C4 mostra REST API | Swagger obrigatório não implementado |
| Testes | Domain tests, smoke e boundary test | Mesmo espelhamento; use case/controller ainda pendentes | DoD pede unit/integration | Cobertura/integração insuficientes para o requisito |
| Database | MySQL/JPA, H2 em testes, `ddl-auto=update` | MySQL 8 | ADR MySQL aceita | Banco alinhado; migrations/schema ainda ausentes |
| Containers | Dockerfile + Compose | Parte da estrutura relevante | C4 mostra app + MySQL | Artefatos presentes; documentação tem referências inválidas |
| Métrica de tempo médio | Ausente | Não definida | Apenas Performance Analytics genérico | Requisito oficial não identificado/implementado |
| Vulnerability scan | Ausente | Não definido | Apenas citado em segurança | Entregável oficial ausente |

## 6. Component interactions

### 6.1 Fluxo principal da Service Order (B)

1. O Service Advisor identifica ou registra Customer e Vehicle e abre a SO.
2. A prioridade é definida e o Technician recebe/consulta trabalho para diagnóstico.
3. O Diagnosis cria um lote de ServiceExecution`s e seus StockRequirement`s.
4. O sistema verifica disponibilidade e gera uma Estimate em snapshot.
5. O Customer é notificado e decide cada linha de serviço.
6. Linhas aprovadas autorizam execuções; linhas rejeitadas tornam-se terminais.
7. Stock tenta reservar todos os materiais daquela execução.
8. Com reserva completa, a execução fica `ready`; sem material, fica `awaiting_part` e pode gerar Purchase Order.
9. O Technician confirma atribuição, inicia, atualiza e conclui cada execução.
10. Todos os serviços não rejeitados concluídos levam a SO a `Completed`; a entrega leva a `Delivered`.

### 6.2 Reposição de estoque (B)

Indisponibilidade ou nível baixo dispara criação de Purchase Order. A integração com o fornecedor fica atrás de
Gateway + Anti-Corruption Layer. Após fechamento/recebimento, Stock registra os itens, reavalia demandas pendentes,
reserva material e publica o evento que libera as execuções.

### 6.3 Reparo adicional (B)

Uma necessidade descoberta durante a execução não altera um orçamento aprovado. Ela abre novo Diagnosis, cria
novo lote de ServiceExecution`s, gera nova Estimate e repete autorização/reserva. `diagnosisId` e
`authorizedByEstimateId` preservam rastreabilidade entre trabalho e decisão comercial.

### 6.4 Tracking (B)

A visão do Customer combina um status-resumo da SO com o estado individual das execuções agrupadas por Estimate.
O refinamento técnico mais recente e o código atual mantêm um `statusSnapshot`, recalculado ao fim de comandos
relevantes e apenas lido nas consultas. Um documento anterior dizia que o status não seria armazenado e seria
calculado em leitura. AD-010 permanece como decisão de time não ratificada nesta conversa: preserve-se a evidência
implementada, mas não se apresenta a divergência documental como encerrada.

Precedência documentada: `Delivered` → `Completed` → `In Progress` → `Awaiting Part` → `Awaiting Approval` →
`In Diagnosis` → `Received`.

## 7. Data architecture

### 7.1 Persistência (B)

O ADR **ADR-001: Escolha do Banco de Dados (MySQL)** está aceito. MySQL/InnoDB foi escolhido pela natureza
relacional, integridade referencial, transações ACID, integração com Spring Data JPA/Hibernate, suporte em Docker
e familiaridade do time.

### 7.2 Ownership e consistência (B)

- Cada aggregate root possui seu próprio repository.
- A consistência forte fica dentro da fronteira do aggregate.
- Integrações entre contexts usam events e consistência eventual.
- Não há transação distribuída entre Service Lifecycle e Stock & Procurement.
- Referências cruzadas usam IDs; snapshots preservam o histórico comercial e operacional.
- Customer, Vehicle e ServiceCatalog usam desativação/arquivamento lógico: itens inativos não participam de novas
  operações, mas referências e snapshots históricos permanecem válidos (AD-005).
- Estimate encerrada foi proposta como histórico append-only consultado por read model separado, porém isso foi
  adiado para depois do MVP.

### 7.3 Relações conceituais (B)

- Customer 1:N Vehicle.
- Customer e Vehicle são referenciados por ServiceOrder via ID; Vehicle também é copiado como snapshot.
- ServiceOrder 1:N ServiceExecution.
- Um Diagnosis agrupa N ServiceExecution`s e origina uma Estimate.
- Estimate referencia ServiceOrder e suas linhas referenciam ServiceExecution/StockItem por ID.
- ServiceExecution contém N StockRequirement`s.
- Stock contém N StockItem`s; PurchaseOrder contém N PurchaseLine`s.

Não foi identificado no Miro um diagrama físico de banco de dados, DDL definitivo, estratégia de migrations,
índices, constraints completas ou mapeamento de tabelas para todos os aggregates.

## 8. Interfaces and integrations

### 8.1 REST APIs (A/B)

O requisito oficial exige RESTful APIs e documentação Swagger. O C4 mostra uma REST API Spring Boot na porta
8080. Os contratos de endpoint, payloads, versionamento e OpenAPI ainda não aparecem consolidados no Miro.

Registrations é descrito no Context Map como Open Host Service REST consumido por Service Lifecycle. A RFC, por
outro lado, exemplifica uma chamada Java direta no mesmo processo por port + adapter. A interface efetiva entre
os módulos precisa ser confirmada.

### 8.2 Eventos internos (B)

Service Lifecycle e Stock & Procurement mantêm Partnership e Published Language baseada em domain events. O
formato exato, versionamento, entrega, retries, idempotência e persistência desses eventos ainda não foram definidos.

### 8.3 Fornecedor externo (B)

Stock & Procurement integra com o External Supplier System por Gateway + ACL. O protocolo, autenticação,
operações, payloads e tratamento de indisponibilidade não foram identificados.

### 8.4 Autenticação (A/B)

O ADR **ADR 002: Authentication Strategy — Spring Security + JWT vs Spring Authorization Server** está aceito e
escolhe Spring Security + JWT no próprio monólito. Os papéis propostos são CUSTOMER, TECHNICIAN, MANAGER e ADMIN.
Modelo de usuário, matriz final de autorização, refresh/revogação de tokens e gestão de credenciais ainda exigem
detalhamento.

### 8.5 Notificações e sistemas externos (B/D)

Notification reage a eventos e pode enviar e-mail/push. O Event Storming mantém como hotspot a forma de entrega e
sugere um endpoint que simule push para o Tech Challenge; isso não está registrado como decisão aceita.

Os diagramas C4 também mostram **Payment Gateway** e processamento de pagamentos/faturas. Essa integração não
aparece no requisito oficial, no Context Map, nos aggregates nem no refinamento funcional; portanto, não é
considerada parte confirmada da arquitetura.

## 9. Architecture diagrams

Os nomes abaixo são os nomes exatos dos artefatos no Miro. Este documento referencia e explica os desenhos
existentes sem criar novos diagramas Mermaid.

| Artefato | O que representa |
|---|---|
| **Service Order** | Domain Storytelling da abertura, diagnóstico, Estimate, aprovação e tracking |
| **Stock** | Domain Storytelling de reserva, baixa, baixo estoque, compra, recebimento e reposição |
| **Brainstorming** | Inventário inicial de domain events para os dois fluxos principais |
| **Ordenação** | Linha principal e caminhos alternativos do Event Storming; setas contínuas mostram o happy path e setas vermelhas tracejadas mostram exceções/policies |
| **Hotspots** | Questões abertas sobre notificação, expiração, indisponibilidade, transições e fornecedor |
| **Pivotal Events, Actors, Commands and Policies** | Event Storming detalhado ligando actors, commands, events, policies e read models |
| **Aggregates and Bounded Contexts** | Agrupamento dos commands/events por aggregate e pelas fronteiras de contexto |
| **Context Map** | Relações Registrations → Service Lifecycle, parceria com Stock & Procurement e ACL do fornecedor |
| **C4 Model - Level 1: System Context** | Pessoas, Workshop Management System, Email System e Payment Gateway |
| **C4 Model - Level 2: Containers** | REST API, cache, lógica de domínio e MySQL dentro do ambiente Docker |

Relações relevantes expostas pelos diagramas:

- o happy path conecta identificação/cadastro → SO → diagnóstico → Estimate → decisão → reserva → execução → entrega;
- caminhos tracejados cobrem cadastro inexistente, reprovação, indisponibilidade, compra, baixo estoque e reparo adicional;
- o Context Map marca Registrations como upstream de Service Lifecycle por OHS/snapshots;
- Service Lifecycle e Stock & Procurement colaboram em duas direções por Published Language;
- o fornecedor é upstream e o domínio de Stock é protegido por ACL.

## 10. Technical decisions

| Decisão existente | Estado | Racional documentado |
|---|---|---|
| MySQL/InnoDB | Aceita | Relações, ACID, JPA, Docker e familiaridade do time |
| Monólito modular, módulo Maven único | Aceita | Atender ao monólito com menor setup e fronteiras explícitas |
| Spring Modulith para verificar fronteiras | Aceita/implementada no repositório | Falhar o build quando módulos importam detalhes internos |
| Camadas/hexagonal dentro de cada contexto | Definida nos documentos | Isolar domínio de frameworks e permitir trabalho paralelo por ports |
| IDs e snapshots entre aggregates | Definida | Evitar acoplamento e preservar histórico |
| Domain events entre contexts | Definida em nível conceitual | Consistência eventual sem transação distribuída |
| Spring Security + JWT | Aceita | Atender ao requisito com menor complexidade no MVP |
| Decisão de Estimate por linha | Refinamento mais recente | Permitir aprovação parcial sem recriar execuções |
| `statusSnapshot` atualizado em comandos (AD-010) | Implementado e presente no refinamento recente; ratificação do time pendente | Preservar comportamento atual sem declarar a alternativa compartilhada resolvida |
| `TaxId` imutável no Customer (AD-002) | Aceita por Ivan; não implementada | Centralizar a validação de CPF/CNPJ como invariante do domínio |
| Vehicle independente dentro de `customer` (AD-003) | Aceita por Ivan, condicionada a AD-001 | Preservar identidade/repository próprios sem criar módulo antes da decisão do time |
| ServiceCatalog independente dentro de `customer` e snapshots nos consumidores (AD-004) | Aceita por Ivan, condicionada a AD-001 | Preservar preços históricos e evitar novo módulo sem aprovação do time |
| Desativação lógica de dados cadastrais (AD-005) | Aceita por Ivan; não implementada | Impedir novo uso sem destruir histórico ou acoplar remoção aos consumidores |
| Polling/cache para tracking | Proposta local, não confirmada no Miro | ADR local está sem status; C4 mostra SimpleCache de 5 s |

## 11. Tech Challenge traceability

O status abaixo mede cobertura por solução ou artefato existente, como solicitado pelo desafio; não significa que
a funcionalidade esteja pronta no código. A coluna de notas e a seção **Current vs. Intended State** registram a
situação de implementação.

| Requirement | Existing solution/artifact | Status | Notes |
|---|---|---|---|
| Identificar Customer por CPF/CNPJ | Customer, TaxId; **3. Ubiquitous Language**; RF01; AD-002 | Covered | VO imutável aceito; código ainda usa String e requer implementação/testes de unicidade e dígitos |
| Cadastrar e manter Vehicle | Vehicle aggregate; LicensePlate/ChassisNumber; RF03–RF06; AD-003/AD-005 | Covered | Aggregate/local e desativação foram escolhidos, mas o destino `customer` depende de AD-001 e ainda não há código |
| Incluir serviços solicitados | ServiceCatalog + ServiceExecution; RF07–RF13; AD-004 | Covered | Snapshot protege alterações futuras de preço; aggregate ainda não implementado e localização depende de AD-001 |
| Incluir peças/insumos | StockRequirement e StockItem | Covered | Abrange part, consumable e supply |
| Gerar orçamento automaticamente | Diagnosis → Estimate policy | Covered | Geração está modelada; algoritmo de preço/impostos não está detalhado |
| Enviar orçamento para aprovação | Estimate + Notification; Event Storming | Covered | Canal de notificação permanece aberto |
| Estados oficiais da SO e transição automática | ServiceOrder statusSnapshot e policies | Covered | Adiciona `Awaiting Part`; terminologia precisa ser uniformizada |
| Consulta do progresso pelo Customer via API | Tracking granular + C4 REST API | Partially covered | Falta contrato REST/OpenAPI e regra de acesso ao próprio Customer |
| CRUD de Customer | Registrations, RF01–RF02; AD-002/AD-005 | Covered | Desativação lógica está decidida; implementação atual não possui esse lifecycle |
| CRUD de Vehicle | Vehicle, RF03–RF06; AD-003/AD-005 | Covered | RF06 significa desativação lógica; implementação aguarda AD-001 |
| CRUD de Service Catalog | ServiceCatalog, RF07–RF08; AD-004/AD-005 | Partially covered | Cadastro/preço e desativação estão definidos; list/detail e contratos REST seguem incompletos |
| CRUD de peças/insumos e estoque | Stock/StockItem, RF25–RF30 | Partially covered | O modelo foca operação de estoque, não explicita todo o CRUD administrativo |
| Listar/detalhar Service Orders | C4/API e read models do Event Storming | Partially covered | Contratos e filtros não foram identificados |
| Tempo médio de execução | Performance Analytics como generic subdomain | Not identified | Não há evento temporal, métrica, consulta ou modelo definido |
| JWT nas APIs administrativas | ADR 002 | Covered | Implementação/autorização ainda deve seguir a matriz final de papéis |
| Validar CPF/CNPJ e placa | TaxId e LicensePlate VOs | Covered | Regras foram descritas no DDD tático |
| Testes unitários e de integração | DoD + estratégia do repositório | Partially covered | Não há matriz de fluxos/cobertura por requisito no Miro |
| Back-end monolítico/em camadas | RFC aceita + C4 + Spring Modulith | Covered | É monólito modular, um único processo/deploy |
| Justificar banco de dados | **ADR-001: Escolha do Banco de Dados (MySQL)** | Covered | ADR aceita |
| RESTful API documentada por Swagger | C4 mostra REST API | Partially covered | Swagger/OpenAPI não foi identificado |
| Dockerfile e docker-compose | C4 Level 2 + arquivos existentes no repositório | Covered | Verificação funcional pertence à etapa de implementação |
| Cobertura mínima de 80% | Requisito oficial e DoD de testes | Partially covered | Política/ferramenta de enforcement não está ativa/consolidada |
| Execução local simples/README | `README.md`, `DOCKER.md` e containers | Partially covered | README atual ainda não é completo |
| Event Storming dos fluxos obrigatórios | **Ordenação**, **Hotspots**, **Pivotal Events, Actors, Commands and Policies** | Covered | Fluxos de SO e Stock estão representados com alternativas |
| Diagramas DDD e Ubiquitous Language | **Aggregates and Bounded Contexts**, **Context Map**, **3. Ubiquitous Language** | Covered | Existem divergências de versão registradas abaixo |
| Relatório de vulnerabilidades | Apenas menções em ADR de segurança | Not identified | Scan, ferramenta, resultado e relatório não foram encontrados |
| Vídeo e PDF de entrega | Requisito oficial | Not identified | São entregáveis, não decisões arquiteturais |
| Regra de visibilidade/acesso ao repositório | Texto oficial | Not identified | O texto oficial alterna entre privado e público |

## 12. Gaps and open questions

### 12.1 Inconsistências entre artefatos do Miro (D)

1. **Quantidade de contexts:** um documento lista três; refinamento e RFC listam quatro incluindo Notification.
2. **Decisão da Estimate:** versão anterior aprova/reprova a Estimate inteira; versão recente decide por linha e usa
   `closed`. Os artefatos antigos ainda não foram atualizados.
3. **Status da SO:** versão anterior calcula em toda leitura; refinamento recente e código persistem
   `statusSnapshot`; AD-010 ainda requer ratificação do time.
4. **Prazo da Estimate:** aparecem 24 h, 48 h e “48 h + ETA”. Não há regra única aceita para todos os cenários.
5. **EstimateLinePart:** não está ligada inequivocamente a uma ServiceExecution nem tem decisão própria.
6. **Notification:** é contexto próprio em alguns documentos e generic subdomain fora do core em outros; falta definir
   sua fronteira técnica e o canal do MVP.
7. **Integração Registrations/Service Lifecycle:** Context Map diz REST/OHS; RFC diz chamada Java direta por port.
8. **C4 desatualizado ou divergente:** usa ServiceOrder, Technician, Customer e Parts como bounded contexts, enquanto
   o DDD mais recente usa Service Lifecycle, Registrations, Stock & Procurement e Notification.
9. **Sistemas externos no C4:** Payment Gateway não é sustentado por requisitos ou DDD; External Supplier System,
   presente no Context Map, não aparece no C4.
10. **Cache/polling:** C4 mostra cache de 5 s, mas o ADR local de polling está sem aprovação explícita.

### 12.2 Diferenças entre repositório, estrutura pretendida e Miro (D)

- O repositório está alinhado a `PROJECT-STRUCTURE.md`: ambos usam `customer`, `technician`, `parts` e
  `serviceorder`. A divergência principal é entre essa estrutura autoritativa e os quatro contexts do Miro recente.
- Technician está implementado e planejado como aggregate/módulo, enquanto o Miro recente o define apenas como ator.
- Vehicle e ServiceCatalog não aparecem como aggregates atuais nem em `PROJECT-STRUCTURE.md`. AD-003/AD-004
  selecionam aggregates independentes dentro de `customer`, mas essa direção continua condicionada a AD-001;
  `vehicleId`, `VehicleSnapshot` e snapshots de serviço permanecem hoje em ServiceOrder.
- Estimate, PurchaseOrder e Notification ainda não aparecem no código e não têm destino explícito na estrutura
  pretendida, apesar de possuírem fronteiras no Miro.
- O módulo `parts` modela Part individual, conforme `PROJECT-STRUCTURE.md`, enquanto o Miro define Stock como
  aggregate contendo StockItem`s e separa PurchaseOrder.
- Spring Security/JWT, Swagger/OpenAPI e enforcement de cobertura não aparecem no `pom.xml` atual.
- A estratégia Hibernate `ddl-auto=update` existe, mas migrations versionadas não foram identificadas.

Essas observações não autorizam refatoração automática. Como `PROJECT-STRUCTURE.md` é autoritativo para a direção
estrutural, qualquer mudança de módulos exige uma atualização deliberada. AD-002 e AD-005 não alteram essa estrutura
e já podem orientar Customer. AD-003 e AD-004 não serão incorporadas à árvore pretendida até o grupo decidir
AD-001: mapear os conceitos do Miro para os módulos definidos ou atualizar formalmente a estrutura.

### 12.3 Decisões ainda necessárias (D)

- Mapeamento dos contexts do Miro para os módulos físicos existentes (AD-001). É o único bloqueio arquitetural
  restante para implementar Vehicle e ServiceCatalog; não bloqueia o planejamento Jira de RF01–RF08.
- Contratos REST e OpenAPI, códigos de erro, paginação, filtros e versionamento.
- Autorização por endpoint e ownership de Customer.
- Schema físico, migrations, índices, constraints e estratégia de concorrência/locking para reserva atômica.
- Garantias do event bus: transação, outbox, idempotência, retry, ordenação e observabilidade.
- Scheduler/mensageria para expiração de Estimate.
- Regra de preço total, descontos, impostos e eventual pagamento/faturamento.
- Cálculo e exposição do tempo médio de execução.
- Canal real ou simulado de Notification.
- Contrato e resiliência da integração com fornecedor.
- Escopo e ferramenta do vulnerability scan.
- Regra final de visibilidade do repositório para entrega.

### 12.4 Assunções usadas nesta consolidação (C)

- A RFC marcada **Aceito - Opção 2** e o refinamento técnico posterior representam decisões mais recentes que os
  esboços iniciais. Isso é uma regra de leitura, não uma resolução das contradições.
- O `statusSnapshot` é descrito como status derivado materializado: sua regra continua derivada das execuções, mas
  o valor é armazenado para leitura eficiente.
- Payment Gateway é tratado como elemento não confirmado porque só foi identificado nos C4.
- A localização de Vehicle e ServiceCatalog em `customer` não é uma assunção ativa: é uma decisão explícita de Ivan
  condicionada a AD-001. Até a confirmação do time, não autoriza criação de pacotes nem edição estrutural.

## 13. Miro artifact index

| Artifact / frame | Purpose | Main information | Related architecture area |
|---|---|---|---|
| **Tech Challenge** | Brief oficial | Problema, funcionalidades, requisitos técnicos e entregáveis | Requirements / traceability |
| **Service Order** | Domain Storytelling | Abertura, diagnóstico, Estimate, aprovação e tracking | Domain / flows |
| **Stock** | Domain Storytelling | Reserva, baixa, compra, recebimento e reposição | Stock & Procurement |
| **Brainstorming** | Event Storming inicial | Lista de eventos do domínio | Domain events |
| **Ordenação** | Event Storming ordenado | Happy path e alternativas | Component interactions |
| **Hotspots** | Questões da sessão | Expiração, notificação, indisponibilidade e fornecedor | Gaps / risks |
| **Pivotal Events, Actors, Commands and Policies** | Event Storming detalhado | Actors, commands, events, policies e read models conectados | DDD / flows |
| **Aggregates and Bounded Contexts** | Desenho de fronteiras | Aggregates, commands/events e agrupamento por contexto | DDD |
| **Context Map** | Mapa estratégico | OHS, Partnership, Published Language e ACL | Integrations |
| **1. Mapeamento do Domínio** | Documento estratégico | Core/supporting/generic subdomains e princípios de fronteira | Domain overview |
| **3. Ubiquitous Language** | Glossário normativo | Termos oficiais e decisões de nomenclatura | Ubiquitous Language |
| **4. Aggregates** | Modelagem tática | Estrutura, commands, events e invariantes dos aggregates | DDD |
| **5. Detalhes Adicionais dos Aggregates** | Complemento tático | Status, reparo adicional, tracking, policies e ownership | Domain / flows |
| **6. Bounded Contexts & Context Map** | Documento estratégico | Contexts e relações de integração | System boundaries |
| **7. Detalhamento de Aggregate Roots, Entities e Value Objects** | Especificação tática | Atributos, estados e comportamentos conceituais | Domain model |
| **Levantamento de Requisitos e Refinamento Técnico** | Requisitos internos | RF01–RF33, RNFs e casos de borda | Requirements / backlog input |
| **6. Refinamento Técnico** | Planejamento técnico | Events, riscos, épicos, dependências, DoR e DoD | Delivery architecture |
| **ADR-001: Escolha do Banco de Dados (MySQL)** | Decisão de dados | MySQL/InnoDB e trade-offs | Data architecture |
| **RFC: Estrutura de Código do Projeto — Monólito Modular por Bounded Context** | Decisão estrutural | Opção 2, módulo único e Spring Modulith | System architecture |
| **ADR 002: Authentication Strategy — Spring Security + JWT vs Spring Authorization Server** | Decisão de segurança | JWT no monólito para o MVP | Security |
| **C4 Model - Level 1: System Context** | C4 contexto | Pessoas e sistemas externos | System context |
| **C4 Model - Level 2: Containers** | C4 containers | API, cache, domínio, MySQL e Docker | Runtime architecture |

### Links diretos para os artefatos principais

- [Tech Challenge](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680441449197)
- [Pivotal Events, Actors, Commands and Policies](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720)
- [Aggregates and Bounded Contexts](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679675285092)
- [Context Map](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679674975757)
- [3. Ubiquitous Language](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684049703)
- [Levantamento de Requisitos e Refinamento Técnico](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679721508363)
- [ADR-001: Escolha do Banco de Dados (MySQL)](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679864450722)
- [RFC: Estrutura de Código do Projeto — Monólito Modular por Bounded Context](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680294073916)
- [C4 Model - Level 1: System Context](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680297894517)
- [C4 Model - Level 2: Containers](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680297894596)
- [ADR 002: Authentication Strategy — Spring Security + JWT vs Spring Authorization Server](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680437842719)

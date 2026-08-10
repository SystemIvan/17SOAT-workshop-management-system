# Estrutura do Projeto

Documento de referência rápida da organização do código-fonte. Para as regras de
como escrever código novo (naming, DDD, testes, commits), veja o [AGENTS.md](../AGENTS.md).

## Visão geral

O projeto é um **monólito modular** construído com **Spring Modulith**: cada
bounded context (Customer, Parts, Technician, Service Order) é isolado em seu
próprio pacote, e o Spring Modulith verifica automaticamente, a cada `mvn test`,
que nenhum módulo acessa código interno de outro.

```
Pacote raiz: br.com.fiap.workshop_management_system
```

## Árvore de pacotes

```
src/main/java/br/com/fiap/workshop_management_system/
│
├── WorkshopManagementSystemApplication.java   # @SpringBootApplication (entry point)
├── ErrorResponse.java                         # DTO de erro, usado por todos os módulos
├── GlobalExceptionHandler.java                # @RestControllerAdvice global
│
├── customer/                        # Bounded Context: Clientes
│   ├── package-info.java              #   @ApplicationModule(displayName = "Customer")
│   ├── domain/
│   │   ├── model/                     #   Customer (aggregate root), ContactInfo (VO)
│   │   └── repository/                #   CustomerRepository (interface)
│   ├── application/
│   │   ├── dto/                       #   Requests/Responses da API
│   │   └── usecase/                   #   Create/Get/List/Rename/UpdateContact
│   └── infrastructure/
│       ├── persistence/               #   CustomerJpaEntity + RepositoryImpl
│       └── web/                       #   CustomerController (REST)
│
├── technician/                      # Bounded Context: Técnicos
│   └── (mesma forma do customer: domain/application/infrastructure)
│
├── parts/                           # Bounded Context: Peças / Estoque
│   └── (mesma forma do customer: domain/application/infrastructure)
│
└── serviceorder/                    # Bounded Context: Ordens de Serviço (core subdomain)
    ├── package-info.java              #   @ApplicationModule(displayName = "Service Order")
    ├── domain/
    │   ├── model/                     #   ServiceOrder (aggregate root), ServiceExecution
    │   │                               #   (entity), Money/Priority/StockRequirement/... (VOs)
    │   └── repository/                #   ServiceOrderRepository (interface)
    ├── application/
    │   ├── dto/                       #   Requests/Responses da API
    │   └── usecase/                   #   Create, AssignTechnician, PerformDiagnosis,
    │                                   #   StartExecution, UpdateProgress, Complete, Finalize...
    └── infrastructure/
        ├── persistence/               #   JPA entities + RepositoryImpl
        └── web/                       #   ServiceOrderController (REST)
```

Cada contexto segue **sempre a mesma forma interna** (arquitetura em camadas
dentro do módulo):

| Camada | Pacote | Responsabilidade |
|---|---|---|
| **Domain** | `<contexto>/domain/model` | Aggregate root, entities e value objects. Regras de negócio puras, sem dependência de Spring/JPA. |
| | `<contexto>/domain/repository` | Interface do repositório (contrato), sem detalhes de persistência. |
| **Application** | `<contexto>/application/usecase` | Um use case por classe (`@Service`), orquestra o domínio. |
| | `<contexto>/application/dto` | Records de request/response da API — nunca expõe entidades. |
| **Infrastructure** | `<contexto>/infrastructure/persistence` | JPA entity, `JpaRepository`, mapper entidade↔domínio, e a implementação do repositório do domain. |
| | `<contexto>/infrastructure/web` | `@RestController`, único ponto de entrada HTTP do módulo. |

## Regras de fronteira (Spring Modulith)

- **Módulo = pacote direto da raiz.** `customer`, `technician`, `parts` e
  `serviceorder` são os 4 módulos da aplicação. Cada um tem um `package-info.java`
  anotado com `@ApplicationModule`.
- **Comunicação entre módulos hoje é só por ID.** Não existe import cruzado —
  `ServiceOrder.customerId` e `ServiceExecution.assignedTechnicianId` são `UUID`
  simples, não referências a objetos de outro módulo. Se um módulo precisar
  chamar outro no futuro, o padrão é: interface ("port") em `application/` do
  módulo consumidor, implementação ("adapter") em `infrastructure/`, nunca
  importando `domain/` de outro contexto diretamente.
- **Código transversal fica na raiz.** `ErrorResponse` e `GlobalExceptionHandler`
  ficam em `br.com.fiap.workshop_management_system` (mesmo pacote da classe
  `@SpringBootApplication`) — por convenção do Spring Modulith, o pacote raiz em
  si não é tratado como módulo, então fica de fora da verificação de fronteiras.
- **Fronteira é garantida por teste, não só por convenção.** Veja
  `src/test/java/br/com/fiap/workshop_management_system/ModuleStructureTest.java`:
  roda `ApplicationModules.of(...).verify()` em todo `mvn test`, e qualquer
  import indevido entre módulos quebra o build.

## Testes

```
src/test/java/br/com/fiap/workshop_management_system/
├── ModuleStructureTest.java             # Verifica as fronteiras dos módulos
├── WorkshopManagementSystemApplicationTests.java  # Smoke test (Spring context load)
├── customer/domain/model/CustomerTest.java
├── technician/domain/model/TechnicianTest.java
├── parts/domain/model/PartTest.java
└── serviceorder/domain/model/
    ├── ServiceOrderTest.java
    └── ServiceExecutionTest.java
```

Os testes espelham 1:1 o pacote da classe testada em `src/main`. Hoje só existem
testes de domínio (regras de negócio dos aggregates); use case e controller ainda
não têm cobertura própria.

## Outros diretórios relevantes

| Caminho | O que é |
|---|---|
| `docs/` | ADRs e documentação de arquitetura (este arquivo, `ADR-001-realtime-updates-strategy.md`) |
| `scripts/init-db.sql` | Script de inicialização do banco MySQL local |
| `Dockerfile`, `docker-compose.yml`, `DOCKER.md` | Setup de desenvolvimento local em container |
| `src/main/resources/application.properties` | Configuração Spring (datasource MySQL, `ddl-auto=update`) |

## Stack

- Java 21, Spring Boot 4.1.0, Spring Modulith 2.1.0
- Spring Data JPA + MySQL 8 (H2 em testes)
- Maven (`./mvnw`)

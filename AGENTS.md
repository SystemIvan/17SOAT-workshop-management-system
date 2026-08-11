# AGENTS.md — Workshop Management System

Guia de instruções para o time desenvolver o **MVP de Gestão de Oficina Mecânica** com coerência, qualidade e escalabilidade. Este documento é referência **obrigatória** para todos os 5 membros do time.

---

## 📋 Informações do Projeto

| Aspecto | Detalhes |
|--------|----------|
| **Nome** | Workshop Management System |
| **Objetivo** | Sistema de gestão back-end para oficina mecânica |
| **Desafio** | Tech Challenge Pós Tech FIAP (Fase 1) |
| **Prazo** | 1 mês |
| **Time** | 5 desenvolvedores |
| **Formato** | Monolítico (Spring Boot) |
| **API** | REST |
| **DB** | MySQL (pronto para migração PostgreSQL em AWS) |

---

## 🏗️ Arquitetura e Bounded Contexts

O projeto usa **Spring Modulith**: cada bounded context é um pacote direto sob a raiz da aplicação (`br.com.fiap.workshop_management_system`), anotado com `@ApplicationModule` em seu `package-info.java`. As fronteiras entre módulos são verificadas automaticamente pelo teste `ModuleStructureTest` (`src/test/.../ModuleStructureTest.java`), que roda `ApplicationModules.of(...).verify()` — `mvn test` falha se algum módulo importar classes internas de outro.

Código verdadeiramente transversal (ex.: `GlobalExceptionHandler`, `ErrorResponse`) fica no pacote raiz, junto da classe `@SpringBootApplication` — pacotes diretos da raiz é que são tratados como módulos, então o pacote raiz em si fica fora da verificação de fronteiras.

### Estrutura de Pastas (real)

```
src/main/java/br/com/fiap/workshop_management_system/
├── WorkshopManagementSystemApplication.java   # @SpringBootApplication
├── ErrorResponse.java                         # Código transversal (fora de qualquer módulo)
├── GlobalExceptionHandler.java                # @RestControllerAdvice global
│
├── customer/                        # Bounded Context: Customers
│   ├── package-info.java            # @ApplicationModule(displayName = "Customer")
│   ├── domain/
│   │   ├── model/                   # Customer (aggregate root), ContactInfo (VO)
│   │   └── repository/              # CustomerRepository (interface)
│   ├── application/
│   │   ├── dto/
│   │   └── usecase/                 # CreateCustomerUseCase, RenameCustomerUseCase, ...
│   └── infrastructure/
│       ├── persistence/             # CustomerJpaEntity, CustomerRepositoryImpl, ...
│       └── web/                     # CustomerController
│
├── technician/                      # Bounded Context: Technicians (mesma forma acima)
├── parts/                           # Bounded Context: Parts/Inventory (mesma forma acima)
│
└── serviceorder/                    # Bounded Context: Service Orders (core subdomain)
    ├── package-info.java            # @ApplicationModule(displayName = "Service Order")
    ├── domain/
    │   ├── model/                   # ServiceOrder (aggregate root), ServiceExecution, ...
    │   └── repository/              # ServiceOrderRepository (interface)
    ├── application/
    │   ├── dto/
    │   └── usecase/                 # CreateServiceOrderUseCase, AssignTechnicianUseCase, ...
    └── infrastructure/
        ├── persistence/
        └── web/                     # ServiceOrderController
```

Hoje o acoplamento entre `serviceorder` e `customer`/`technician` é feito só por `UUID` (ex.: `ServiceOrder.customerId`, `ServiceExecution.assignedTechnicianId`) — não há chamadas diretas entre módulos. Se isso mudar no futuro, use o padrão Port (interface em `application/`) + Adapter (implementação em `infrastructure/`) chamando a API pública do outro módulo, nunca importando classes de `domain/` de outro contexto.

### Bounded Contexts (DDD)

#### 1. **Service Order Context**
**Aggregate Root:** `ServiceOrder`
- **Entities:** ServiceExecution, ServiceExecutionItem
- **Value Objects:** ServiceOrderStatus, ExecutionStatus, Priority, ServiceOrderNumber
- **Repository:** ServiceOrderRepository
- **Use Cases:**
    - Criar nova Service Order
    - Atribuir técnico
    - Iniciar execução
    - Atualizar progresso
    - Aprovar antes da execução
    - Completar execução
    - Obter status em tempo real

#### 2. **Technician Context**
**Aggregate Root:** `Technician`
- **Value Objects:** TechnicianId, Specialty, Availability
- **Repository:** TechnicianRepository

#### 3. **Customer Context**
**Aggregate Root:** `Customer`
- **Value Objects:** CustomerId, ContactInfo
- **Repository:** CustomerRepository

#### 4. **Parts Context**
**Aggregate Root:** `Part`
- **Value Objects:** PartId, Quantity, Price
- **Repository:** PartRepository

---

## 🎯 Padrões de Projeto

### Domain-Driven Design (DDD)

#### Aggregate Root
```java
@Entity
@Table(name = "service_orders")
public class ServiceOrder extends AggregateRoot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Embedded
    private ServiceOrderId serviceOrderId;
    
    @Enumerated(EnumType.STRING)
    private ServiceOrderStatus status;
    
    @OneToMany(mappedBy = "serviceOrder", cascade = CascadeType.ALL)
    private List<ServiceExecution> executions = new ArrayList<>();
    
    // Constructor, getters, business methods
}
```

#### Value Object
```java
@Embeddable
public class ServiceOrderStatus implements Serializable {
    @Column(name = "status")
    private String value;
    
    private ServiceOrderStatus() {}
    
    public ServiceOrderStatus(String value) {
        if (!isValid(value)) {
            throw new BusinessRuleException("Status inválido: " + value);
        }
        this.value = value;
    }
    
    private static boolean isValid(String status) {
        return Arrays.asList("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED")
            .contains(status);
    }
}
```

#### Repository Pattern
```java
// Domain
public interface ServiceOrderRepository {
    void save(ServiceOrder serviceOrder);
    Optional<ServiceOrder> findById(ServiceOrderId id);
    List<ServiceOrder> findByStatus(ServiceOrderStatus status);
}

// Infrastructure
@Repository
public class ServiceOrderRepositoryImpl implements ServiceOrderRepository {
    @Autowired
    private ServiceOrderJpaRepository jpaRepository;
    
    @Override
    public void save(ServiceOrder serviceOrder) {
        jpaRepository.save(serviceOrder);
    }
    
    // Implementar outros métodos
}
```

### DTOs para Requisições/Respostas

**Nunca exponha Entities diretamente na API!**

```java
// Request DTO
public record CreateServiceOrderDTO(
    Long customerId,
    String description,
    String priority,
    List<String> requiredServices
) {}

// Response DTO
public record ServiceOrderResponseDTO(
    String serviceOrderId,
    String customerName,
    String status,
    LocalDateTime createdAt,
    List<ServiceExecutionDTO> executions
) {}

// Execução DTO
public record ServiceExecutionDTO(
    String executionId,
    String technicianName,
    String currentStatus,  // "PENDING", "IN_PROGRESS", "COMPLETED"
    Integer progressPercentage,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {}
```

### Exception Handling

```java
// Domain Exception
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}

// Controller Advice
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<?> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse("BUSINESS_RULE_VIOLATION", ex.getMessage()));
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity
            .notFound()
            .build();
    }
}
```

---

## 🔄 Fluxo da Service Execution (Épico 3)

### Estados e Transições

```
        ┌─────────────┐
        │   PENDING   │  (Pendente de aprovação)
        └──────┬──────┘
               │ ✓ Aprovado
               ▼
        ┌─────────────────┐
        │  IN_PROGRESS    │  (Técnico atribuído)
        └──────┬──────────┘
               │ Progresso atualizado
               │ (0% → 50% → 100%)
               ▼
        ┌─────────────┐
        │  COMPLETED  │  (Serviço finalizado)
        └─────────────┘
        
        Alternativa: CANCELLED (em qualquer estado)
```

### Fluxo Use Case por Use Case

#### 1. Criar Service Order
```
Input: CreateServiceOrderDTO
├─ Validar dados do cliente
├─ Criar agregado ServiceOrder com status PENDING
├─ Salvar no repositório
└─ Retornar ID + status
```

#### 2. Atribuir Técnico
```
Input: serviceOrderId, technicianId
├─ Buscar ServiceOrder pelo ID
├─ Validar se técnico existe
├─ Atribuir técnico ao ServiceOrder
├─ Salvar
└─ Retornar confirmação
```

#### 3. Aprovar Execução (antes de iniciar)
```
Input: serviceOrderId
├─ Buscar ServiceOrder
├─ Validar se está em status PENDING
├─ Mudar para IN_PROGRESS
├─ Registrar timestamp de início
└─ Salvar
```

#### 4. Atualizar Progresso
```
Input: serviceOrderId, progressPercentage (0-100)
├─ Buscar ServiceExecution
├─ Validar porcentagem
├─ Validar se está IN_PROGRESS
├─ Atualizar campo progressPercentage
├─ Emitir evento de progresso (para tempo real)
└─ Salvar
```

#### 5. Completar Execução
```
Input: serviceOrderId
├─ Buscar ServiceOrder
├─ Validar se está IN_PROGRESS
├─ Mudar para COMPLETED
├─ Registrar timestamp de conclusão
├─ Calcular tempo total
├─ Emitir evento de conclusão
└─ Salvar
```

---

## 🔐 Permissões e Acesso

### Matriz de Permissões

| Ação | Customer | Technician | Manager | Admin |
|------|----------|-----------|---------|-------|
| Visualizar própria SO | ✅ | - | ✅ | ✅ |
| Visualizar todas SO | - | - | ✅ | ✅ |
| Criar SO | ✅ | - | ✅ | ✅ |
| Atribuir técnico | - | - | ✅ | ✅ |
| Iniciar execução | - | ✅ | - | ✅ |
| Atualizar progresso | - | ✅ | ✅ | ✅ |
| Aprovar SO | - | - | ✅ | ✅ |
| Cancelar SO | - | - | ✅ | ✅ |

**Implementar com Spring Security:**
```java
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@PostMapping("/service-orders/{id}/approve")
public ResponseEntity<?> approveServiceOrder(@PathVariable Long id) {
    // ...
}
```

---

## 📡 Tempo Real - Estratégia Recomendada

### Para MVP: **Polling com cache**

✅ **Por quê:** Simples, sem dependências extras, não requer WebSocket
✅ **Para:** Status basic, ideal para MVP

```java
@GetMapping("/service-orders/{id}/status")
@Cacheable(value = "statusCache", key = "#id", unless = "#result == null")
public ResponseEntity<ServiceExecutionDTO> getStatus(@PathVariable Long id) {
    ServiceExecution execution = useCase.getExecution(id);
    return ResponseEntity.ok(mapper.toDTO(execution));
}
```

### Para Futuro: **WebSocket com eventos**

Para versões posteriores, quando precisar de real-time true:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    // Configuração para push de eventos
}
```

**Por enquanto: Polling é suficiente e mais simples.**

---

## 🧪 Testes (Obrigatório: 80% Cobertura)

### Estrutura de Testes

```
src/test/java/com/workshop/
├── serviceorder/
│   ├── domain/
│   │   └── aggregates/
│   │       └── ServiceOrderTest.java          # Unit tests da entidade
│   ├── application/
│   │   └── usecases/
│   │       ├── CreateServiceOrderUseCaseTest.java
│   │       ├── AssignTechnicianUseCaseTest.java
│   │       └── UpdateExecutionProgressUseCaseTest.java
│   └── infrastructure/
│       └── controller/
│           └── ServiceOrderControllerTest.java  # Integration tests
```

### Exemplo: Unit Test

```java
@DisplayName("ServiceOrder - Regras de Negócio")
class ServiceOrderTest {
    
    @Test
    @DisplayName("Deve criar ServiceOrder com status PENDING")
    void shouldCreateWithPendingStatus() {
        // Arrange
        Long customerId = 1L;
        String description = "Revisão completa";
        
        // Act
        ServiceOrder order = new ServiceOrder(customerId, description, Priority.HIGH);
        
        // Assert
        assertEquals(ServiceOrderStatus.PENDING, order.getStatus());
        assertNotNull(order.getCreatedAt());
    }
    
    @Test
    @DisplayName("Deve rejeitar transição PENDING → COMPLETED direta")
    void shouldRejectDirectTransition() {
        // Arrange
        ServiceOrder order = new ServiceOrder(1L, "Revisão", Priority.NORMAL);
        
        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> {
            order.complete();  // Sem passar por IN_PROGRESS
        });
    }
}
```

### Exemplo: Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class ServiceOrderControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("POST /service-orders deve criar e retornar 201")
    void shouldCreateServiceOrder() throws Exception {
        CreateServiceOrderDTO request = new CreateServiceOrderDTO(
            1L, "Revisão", "HIGH", List.of("Óleo", "Filtro")
        );
        
        mockMvc.perform(post("/service-orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
```

### Checklist de Cobertura

- **Unit Tests:** Cada classe de domain (agregados, entities, value objects)
- **Integration Tests:** Cada endpoint REST
- **Testes de Fluxo:** Cenários completos (criar → atribuir → iniciar → atualizar → completar)
- **Testes de Erro:** Exceções de negócio, validações

**Comando para verificar cobertura:**
```bash
mvn clean test jacoco:report
# Relatório em: target/site/jacoco/index.html
```

---

## 📝 Convenções de Código

### Naming Conventions

| Elemento | Padrão | Exemplo |
|----------|--------|---------|
| Packages | `br.com.fiap.workshop_management_system.{bounded-context}.{layer}` | `br.com.fiap.workshop_management_system.serviceorder.domain` |
| Classes | PascalCase | `ServiceOrder`, `CreateServiceOrderUseCase` |
| Interfaces | PascalCase (sem prefixo `I`) | `ServiceOrderRepository` |
| Methods | camelCase, verbo primeiro | `getStatus()`, `updateProgress()` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS`, `DEFAULT_TIMEOUT` |
| Variables | camelCase | `serviceOrderId`, `technicianName` |

### Code Style

- **Indentation:** 4 spaces (configurar no IntelliJ)
- **Line length:** Máximo 120 caracteres
- **Imports:** Evite wildcard imports
- **Comments:** Inglês, apenas para lógica complexa. Self-explanatory code é preferível.

```java
// ❌ Ruim
ServiceOrder so = new ServiceOrder(1L, "Revisão", Priority.HIGH);
so.setStatus(ServiceOrderStatus.IN_PROGRESS);  // Muda status

// ✅ Bom
ServiceOrder serviceOrder = new ServiceOrder(1L, "Revisão", Priority.HIGH);
serviceOrder.startExecution(technicianId);  // Method name explica a intenção
```

---

## 🔗 Conventional Commits

**Obrigatório para todo commit!**

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Tipos Permitidos

| Tipo | Uso | Exemplo |
|------|-----|---------|
| `feat` | Nova feature | `feat(serviceorder): add execution approval` |
| `fix` | Bug fix | `fix(serviceorder): correct status transition` |
| `refactor` | Refatoração | `refactor(serviceorder): extract validation logic` |
| `test` | Testes | `test(serviceorder): add status transition tests` |
| `docs` | Documentação | `docs: update AGENTS.md` |
| `chore` | Tarefas | `chore: update dependencies` |

### Exemplos

```
✅ feat(serviceorder): implement execution approval workflow

- Add ApproveServiceOrderUseCase
- Add status transition validation
- Add integration tests for approval flow

Closes #45

❌ fixed the thing
❌ implemented new stuff
❌ update code
```

---

## 🔄 Fluxo de Desenvolvimento (Code Review)

### Procedimento Obrigatório

1. **Branch**
   ```bash
   git checkout -b feat/serviceorder-approval
   # Nomenclatura: {tipo}/{bounded-context}-{feature}
   ```

2. **Desenvolvimento Local**
   ```bash
   # Rodar testes continuamente
   mvn test -Dtest=ServiceOrderTest
   mvn clean test jacoco:report  # Verificar cobertura
   mvn sonar:sonar  # Se configurado
   ```

3. **Commit com Conventional Commits**
   ```bash
   git commit -m "feat(serviceorder): add execution approval

   - Create ApproveServiceOrderUseCase
   - Add status transition validation
   - Add unit and integration tests"
   ```

4. **Push e Pull Request**
   ```bash
   git push origin feat/serviceorder-approval
   # Abrir PR no GitHub com descrição clara
   ```

5. **Code Review (Obrigatório: 1 pessoa)**
    - Verificar regras de negócio
    - Verificar cobertura de testes (>= 80%)
    - Rodar localmente: `mvn clean test`
    - Verificar SonarLint
    - Checklist da PR (veja abaixo)

6. **Merge**
    - Apenas após aprovação
    - Usar "Squash and merge" se múltiplos commits pequenos
    - Deletar branch remoto

---

## ✅ Checklist para Pull Request

**Cole isso na descrição de toda PR:**

```markdown
## Description
Descreva brevemente o que foi feito

## Type of Change
- [ ] New feature (nova funcionalidade)
- [ ] Bug fix (correção)
- [ ] Breaking change
- [ ] Refactoring

## Testing
- [ ] Testes unitários criados/atualizados
- [ ] Cobertura >= 80%
- [ ] Teste de integração passa
- [ ] Testei manualmente no Postman/Insomnia

## Code Quality
- [ ] Sem SonarLint warnings
- [ ] Conventional Commits usados
- [ ] Code style segue AGENTS.md
- [ ] Sem código duplicado

## Business Logic
- [ ] Regras de DDD respeitadas
- [ ] Transições de estado validadas
- [ ] Permissions/Authorization verificadas
- [ ] Documentação de APIs atualizada (se necessário)

## Related Issues
Closes #XXX
```

---

## 📚 Dependências Spring Boot

**pom.xml - Versões Principais**

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>4.1.0</spring-boot.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>

<dependencies>
    <!-- Spring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Code Coverage -->
    <dependency>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.10</version>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

---

## 🛠️ Configuração Recomendada do IntelliJ

### Code Style
1. **File → Settings → Code Style → Java**
    - Line length limit: 120
    - Indentation: 4 spaces
    - Import static: Desabilitado inicialmente

### Plugins Recomendados
- SonarLint
- Lombok (se usar)
- Spring Boot Assistant

### Git Hooks (Opcional)
```bash
# .git/hooks/pre-commit
#!/bin/bash
mvn clean test
if [ $? -ne 0 ]; then
  echo "Testes falharam. Commit cancelado."
  exit 1
fi
```

---

## 📞 Dúvidas Frequentes

### "Quando usar Entity vs Value Object?"
- **Entity:** Tem identidade única que persiste no tempo (ServiceOrder, Technician)
- **Value Object:** Não tem identidade, é imutável (Status, Priority)

### "Repository deve estar na domain ou infrastructure?"
- **Interface (ServiceOrderRepository):** Domain (define contrato)
- **Implementação (ServiceOrderRepositoryImpl):** Infrastructure (detalhes de persistência)

### "Como testar métodos privados?"
- Não teste. Se precisa testar, método deve ser público.
- Se lógica é complexa, extraia para um novo método testável.

### "Pode usar @Transactional nos Use Cases?"
- Sim. Use `@Transactional` nos métodos públicos dos Use Cases.
- Deixa Spring gerenciar commit/rollback.

### "Como lidar com erros de negócio vs técnicos?"
- **BusinessRuleException:** Erros de regra (status inválido)
- **RuntimeException:** Erros técnicos (DB indisponível) - deixa tratar no handler global

---

## 📞 Contato e Clarificações

Se houver dúvidas sobre:
- **Arquitetura DDD:** Revisite as seções de DDD acima ou veja quadro no [miro](https://miro.com/app/board/uXjVH9faCu4=/)
- **Testes:** Verifique os exemplos de Unit e Integration Tests
- **Padrões de código:** Consulte Naming Conventions e Code Style
- **Fluxo de trabalho:** Siga rigorosamente o seção "Code Review"

---

**Last Updated:** Agosto 2026  
**Versão:** 1.0  
**Status:** ✅ Ativo para Fase 1 do Tech Challenge

<!--
# AGENTS.md — Workshop Management System

Operational guidance for Codex and contributors working on the FIAP Tech Challenge MVP.

## Source precedence

Before architecture-sensitive work:

1. Read `docs/Architecture.md` for the consolidated current and target architecture.
2. Read `docs/Architecture-Decisions.md` and follow only decisions marked **Resolved**.
3. Read `docs/PROJECT-STRUCTURE.md` before creating packages, modules or directories.
4. Inspect the current repository; it is the evidence of what is actually implemented.
5. Use the official **Tech Challenge** requirements for mandatory outcomes and the named Miro artifacts for the
   group's domain design.

Do not treat a recommendation, temporary assumption, Miro draft or unresolved decision as approval to invent
architecture. If sources conflict, preserve the conflict and escalate the relevant decision owner.

## Architecture guidance

- The system is a Java 21 Spring Boot modular monolith using Spring Modulith, REST and MySQL.
- Current top-level modules are `customer`, `technician`, `parts` and `serviceorder` under
  `br.com.fiap.workshop_management_system`.
- Do not add, rename, merge or move top-level modules until AD-001 is resolved by the team.
- Preserve `@ApplicationModule` boundaries and keep `ModuleStructureTest` passing.
- Do not import another module's internal `domain`, `application` or `infrastructure` types.
- Cross-aggregate and cross-module references use IDs and immutable snapshots. Final synchronous/event contracts
  remain subject to AD-011 and AD-012.
- Do not introduce a new architectural pattern, framework or infrastructure dependency without an explicit
  requirement or resolved decision.

Within a module:

- `domain/model`: framework-agnostic aggregates, entities, value objects and business invariants.
- `domain/repository`: repository contracts owned by aggregate roots.
- `application/usecase`: one use case per class; coordinates domain behavior and transaction boundaries.
- `application/dto`: request/response records and mapping at application boundaries.
- `infrastructure/persistence`: JPA entities, Spring Data repositories, persistence mappers and repository adapters.
- `infrastructure/web`: REST controllers; controllers validate transport input and delegate to use cases.
- Keep JPA and HTTP annotations out of domain objects. Never expose domain or JPA entities directly through REST.
- Truly cross-cutting bootstrap/error handling may remain in the root package; do not use it as a shared-domain
  dumping ground.

## Resolved registration decisions

- **AD-002:** Customer CPF/CNPJ is an immutable `TaxId` value object in `customer/domain/model`. Check-digit validity
  is a domain invariant; uniqueness is enforced through repository/application coordination. Keep DTO and JPA
  mapping concerns outside the value object.
- **AD-003:** Vehicle is an independent aggregate root with its own repository, selected to live inside `customer`.
  This placement is conditional on team-owned AD-001. Until AD-001 confirms that `customer` hosts Cadastros, do not
  create Vehicle packages or implementation. Vehicle is not a child entity inside the Customer aggregate.
- **AD-004:** ServiceCatalog is an independent aggregate root with its own repository, selected to live inside
  `customer`. This is also conditional on AD-001; do not implement its package before confirmation. Consumers retain
  copied service name and price snapshots rather than live mutable catalog objects.
- **AD-005:** Removal of Customer, Vehicle and ServiceCatalog means logical deactivation/archival. Inactive records
  cannot be selected for new work, but historical IDs and snapshots remain readable. Do not physically delete
  referenced registration data.

Resolved does not mean implemented. Inspect the code before claiming any of these rules is already present.

## Unresolved boundaries

- AD-001 still blocks implementation placement for Vehicle and ServiceCatalog, but not their Jira planning.
- Technician ownership, Stock/PurchaseOrder boundaries, Estimate decisions, module integration, event delivery,
  notifications, tracking transport/cache, identity ownership, schema migrations, external integrations and the
  execution-time metric remain governed by their unresolved entries.
- Do not use this file to settle those decisions. In particular, do not assume polling cache, a simplified execution
  state machine, Manager approval, a Payment Gateway, Flyway, or new authentication modules are accepted.

## Current implementation guardrails

- Current code implements Customer, Technician, Part and ServiceOrder/ServiceExecution capabilities only.
- `Customer` still stores a raw document string; AD-002 implementation is pending.
- Vehicle and ServiceCatalog aggregates, logical deactivation, Estimate, PurchaseOrder, Notification, JWT,
  OpenAPI, cache and versioned schema migrations are not currently implemented.
- `ServiceOrder` materializes `statusSnapshot`; preserve its implemented state machine and aggregate entry points.
- `ServiceExecution` progress is currently a note guarded by state, not a decided percentage model.
- `spring.jpa.hibernate.ddl-auto=update` is current configuration, not an approved long-term migration policy.

## Testing and quality

- Mirror production packages under `src/test/java`.
- Add focused unit tests for domain invariants and state transitions.
- Add use-case, persistence and controller/integration tests when implementing those layers.
- The official target is at least 80% automated coverage in critical domains. JaCoCo enforcement is not currently
  configured; do not claim the target is met without a generated report.
- Run `./mvnw test` (or `mvnw.cmd test` on Windows) after code changes. This includes Spring Modulith verification.
- Validate success, invalid input, not-found, conflict/uniqueness and forbidden state-transition paths as applicable.

## Code conventions

- Packages: lowercase; classes/interfaces: PascalCase; methods/variables: camelCase; constants: UPPER_SNAKE_CASE.
- Repository interfaces have no `I` prefix. Use-case class names describe the command/query they perform.
- Use four-space indentation, avoid wildcard imports and keep lines near 120 characters.
- Prefer intention-revealing aggregate methods over public setters.
- Use immutable value objects and replace-on-change semantics.
- Use Conventional Commits when commits are explicitly requested; do not commit, push or open a PR without user
  authorization.

## Required documentation synchronization

When a decision changes:

- update `docs/Architecture-Decisions.md` first;
- update affected current/target/traceability/gap sections in `docs/Architecture.md`;
- update `docs/PROJECT-STRUCTURE.md` only when the structural change is approved by the appropriate owner/team;
- update this file only with operational rules supported by a requirement, current accepted architecture or a
  resolved decision. -->

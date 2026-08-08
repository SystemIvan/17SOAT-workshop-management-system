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

### Estrutura de Pastas

```
src/main/java/com/workshop/
├── shared/                          # Código compartilhado
│   ├── domain/
│   │   ├── ValueObject.java
│   │   ├── Entity.java
│   │   └── AggregateRoot.java
│   ├── application/
│   │   └── UseCase.java
│   └── infrastructure/
│       ├── config/
│       └── persistence/
│
├── serviceorder/                    # Bounded Context: Service Orders
│   ├── domain/
│   │   ├── aggregates/
│   │   │   ├── ServiceOrder.java    # Aggregate Root
│   │   │   └── ServiceOrderId.java
│   │   ├── entities/
│   │   │   └── ServiceExecution.java
│   │   ├── valueobjects/
│   │   │   ├── ServiceOrderStatus.java
│   │   │   ├── ExecutionStatus.java
│   │   │   └── Priority.java
│   │   └── repositories/
│   │       └── IServiceOrderRepository.java
│   ├── application/
│   │   ├── usecases/
│   │   │   ├── CreateServiceOrderUseCase.java
│   │   │   ├── AssignTechnicianUseCase.java
│   │   │   ├── StartExecutionUseCase.java
│   │   │   ├── UpdateExecutionProgressUseCase.java
│   │   │   └── CompleteExecutionUseCase.java
│   │   ├── dto/
│   │   │   ├── CreateServiceOrderDTO.java
│   │   │   ├── ServiceOrderResponseDTO.java
│   │   │   ├── ServiceExecutionDTO.java
│   │   │   └── UpdateExecutionDTO.java
│   │   └── mappers/
│   │       └── ServiceOrderMapper.java
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── ServiceOrderJpaRepository.java
│   │   │   └── ServiceOrderRepositoryImpl.java
│   │   └── controller/
│   │       └── ServiceOrderController.java
│   └── ServiceOrderModule.java      # Configuração do módulo
│
├── technician/                      # Bounded Context: Technicians
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── TechnicianModule.java
│
├── customer/                        # Bounded Context: Customers
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── CustomerModule.java
│
├── parts/                           # Bounded Context: Parts/Inventory
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── PartsModule.java
│
└── shared/
    ├── exceptions/
    │   ├── DomainException.java
    │   ├── BusinessRuleException.java
    │   └── GlobalExceptionHandler.java
    ├── events/
    │   └── DomainEvent.java
    └── config/
        ├── JpaAuditingConfig.java
        └── MySQLDialectConfig.java
```

### Bounded Contexts (DDD)

#### 1. **Service Order Context**
**Aggregate Root:** `ServiceOrder`
- **Entities:** ServiceExecution, ServiceExecutionItem
- **Value Objects:** ServiceOrderStatus, ExecutionStatus, Priority, ServiceOrderNumber
- **Repository:** IServiceOrderRepository
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
- **Repository:** ITechnicianRepository

#### 3. **Customer Context**
**Aggregate Root:** `Customer`
- **Value Objects:** CustomerId, ContactInfo
- **Repository:** ICustomerRepository

#### 4. **Parts Context**
**Aggregate Root:** `Part`
- **Value Objects:** PartId, Quantity, Price
- **Repository:** IPartRepository

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
public interface IServiceOrderRepository {
    void save(ServiceOrder serviceOrder);
    Optional<ServiceOrder> findById(ServiceOrderId id);
    List<ServiceOrder> findByStatus(ServiceOrderStatus status);
}

// Infrastructure
@Repository
public class ServiceOrderRepositoryImpl implements IServiceOrderRepository {
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
| Packages | `com.workshop.{bounded-context}.{layer}` | `com.workshop.serviceorder.domain` |
| Classes | PascalCase | `ServiceOrder`, `CreateServiceOrderUseCase` |
| Interfaces | I + PascalCase | `IServiceOrderRepository` |
| Methods | camelCase, verbo primeiro | `getStatus()`, `updateProgress()` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS`, `DEFAULT_TIMEOUT` |
| Variables | camelCase | `serviceOrderId`, `technicianName` |

### Code Style

- **Indentation:** 4 spaces (configurar no IntelliJ)
- **Line length:** Máximo 120 caracteres
- **Imports:** Use wildcard imports `import com.workshop.*` só se absolutamente necessário
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
- **Interface (IServiceOrderRepository):** Domain (define contrato)
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
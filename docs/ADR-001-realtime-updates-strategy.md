# ADR 001: Real-Time Updates Strategy — Polling vs WebSocket

**Status:** -
**Date:** Agosto 2026  
**Deciders:** Time de Desenvolvimento (5 pessoas)  
**Affected By:** Épico 3 (ServiceExecution Status Updates), Customers, Technicians  

---

## Context

O sistema Workshop Management System precisa exibir **atualizações em tempo real** do status de execução de serviços:

- **Customers** veem o progresso de sua Service Order
- **Technicians** veem tarefas atribuídas e atualizações de status
- **Managers** monitoram múltiplas execuções simultaneamente

Existem duas estratégias principais:

1. **Polling:** Cliente consulta o servidor periodicamente (HTTP GET)
2. **WebSocket:** Conexão persistente com push de dados do servidor

**Esta ADR decide qual usar para o MVP (Fase 1) e como escalar para futuro.**

---

## Problem Statement

### Requisitos Funcionais
- Status de execução deve estar "próximo de tempo real" para usuário
- Latência máxima aceitável: 5-10 segundos
- Não é verdadeiro real-time (ex: linha de produção crítica)
- MVP com escopo limitado

### Restrições do Projeto
- **Prazo:** 1 mês (Fase 1)
- **Team:** 5 desenvolvedores
- **Stack:** Java 21, Spring Boot 4.1, MySQL
- **Ambiente:** MVP em localhost, futuro em AWS
- **Experiência do team:** Não mencionou experiência anterior com WebSocket

### Cenários de Uso

| Cenário | Frequência | Tolerância de Latência |
|---------|-----------|----------------------|
| Customer checando progresso | Ocasional (1-2x/hora) | 5-30 seg OK |
| Technician começando trabalho | Ocasional | Real-time (< 1 seg) |
| Manager monitorando dashboard | Contínuo | 5-10 seg aceitável |

---

## Considered Options

### Option 1: HTTP Polling ✅ SELECIONADO

**Como funciona:**
```
Cliente                          Servidor
  │                                 │
  ├─── GET /service-orders/123/status ────>
  │                                 │
  │                    <─── {status: "IN_PROGRESS", progress: 45%}
  │
  [Aguarda 5-10 segundos]
  │
  ├─── GET /service-orders/123/status ────>
  │                                 │
  │                    <─── {status: "IN_PROGRESS", progress: 60%}
  │
  ... (repetir)
```

#### Vantagens ✅
- ✅ **Simples:** Usa HTTP GET existente, sem dependências novas
- ✅ **Stateless:** Servidor não precisa manter conexões
- ✅ **Escalável:** Cada request é independente, easy load balancing
- ✅ **Cacheable:** Suporta cache HTTP (Redis, ETag)
- ✅ **Debugging:** Fácil ver requests no DevTools, Postman
- ✅ **Segurança:** Mesmos mecanismos Spring Security / JWT
- ✅ **Conhecimento:** Time já conhece REST/HTTP
- ✅ **Custos AWS:** Sem conexões persistentes = menos custos

#### Desvantagens ❌
- ❌ **Largura de banda:** Múltiplos requests com overhead HTTP
- ❌ **Latência:** Pior que WebSocket (5-10 seg vs <1 seg)
- ❌ **Carga servidor:** Muitos requests simultâneos se muitos clientes
- ❌ **UX:** Não é "live" (usuário vê delay ao atualizar manualmente)

#### Custo de Implementação
- **Tempo:** ~2 horas (método GET simples + cache)
- **Complexidade:** Baixa
- **Manutenção:** Mínima

---

### Option 2: WebSocket

**Como funciona:**
```
Cliente                          Servidor
  │                                 │
  ├─── UPGRADE to WebSocket ───────>
  │                                 │
  ├<──── [Conexão persistente] ──────┤
  │                                 │
  │  <────── {status: "IN_PROGRESS", progress: 45%} (push)
  │  <────── {status: "IN_PROGRESS", progress: 60%} (push)
  │  <────── {status: "COMPLETED"} (push)
  │                                 │
  ├─── CLOSE connection ───────────>
```

#### Vantagens ✅
- ✅ **Verdadeiro real-time:** Atualizações < 1 segundo
- ✅ **Banda:** Menos overhead após conexão aberta
- ✅ **UX:** Dashboard atualiza automaticamente ao vivo
- ✅ **Bidirecional:** Cliente pode enviar também (chat, notifications)

#### Desvantagens ❌
- ❌ **Complexidade:** Requer Spring WebSocket, StompJS no frontend
- ❌ **Stateful:** Servidor mantém conexões abertas em memória
- ❌ **Escalabilidade:** Precisa de message broker (RabbitMQ) em multi-server
- ❌ **Debugging:** Mais difícil testar, ver mensagens
- ❌ **Segurança:** Autenticação/autorização mais complexa
- ❌ **Custo AWS:** Conexões persistentes = mais consumo RAM/CPU
- ❌ **Experiência team:** Requer aprendizado novo em 1 mês

#### Custo de Implementação
- **Tempo:** ~1-2 semanas (setup, testing, deployment)
- **Complexidade:** Alta
- **Manutenção:** Média-Alta
- **Dependências novas:** Spring WebSocket, STOMP, RabbitMQ (futuro)

---

### Option 3: Server-Sent Events (SSE)

**Híbrido:** Push do servidor via HTTP

```
Cliente                          Servidor
  │                                 │
  ├─── GET /service-orders/123/events ──>
  │                                 │
  ├<──── [HTTP Stream aberto] ───────┤
  │                                 │
  │  <────── data: {status: "IN_PROGRESS"} (server push)
  │  <────── data: {status: "COMPLETED"}
  │                                 │
  ├─── CLOSE connection ───────────>
```

#### Vantagens ✅
- ✅ HTTP nativo (sem WebSocket)
- ✅ Mais simples que WebSocket
- ✅ Server push automaticamente
- ✅ Suporta autenticação HTTP padrão

#### Desvantagens ❌
- ❌ Apenas servidor → cliente (não bidirecional)
- ❌ Mantém conexão aberta (como WebSocket)
- ❌ Menos suporte em navegadores antigos
- ❌ Não economiza banda vs Polling

**Não é melhor que ambas as opções anteriores para este caso.**

---

## Decision

### ✅ POLLING para MVP (Fase 1)

**Implementar com HTTP GET + Cache (Redis ou Spring Cache)**

### Por Quê?

1. **Time e Prazo:** 1 mês com 5 devs = simplicidade crítica
2. **MVP Scope:** "Próximo de real-time" é suficiente, não é verdadeiro RT
3. **Fácil de implementar:** Método GET existente + @Cacheable
4. **Fácil de testar:** HTTP simples, debugging trivial
5. **Fácil de escalar:** Stateless, sem conexões persistentes
6. **Custo:** Baixo na AWS (requests vs conexões abertas)
7. **Segurança:** Mesma autenticação REST/JWT
8. **Sem dependências:** Não precisa Maven adicional
9. **Versioning:** Fácil evoluir para WebSocket depois (cliente muda, servidor não)

### 🎯 Critérios de Aceite para MVP

- [ ] GET /service-orders/{id}/status retorna JSON com status + progresso
- [ ] Resposta em < 500ms
- [ ] Cache de 5-10 segundos (evita query ao DB a cada request)
- [ ] Teste integração: Cliente faz poll a cada 5 seg, recebe atualizações
- [ ] Documentação sobre como escalar para WebSocket

---

### 🚀 Estratégia de Escala (Fase 2+)

**Quando migrar para WebSocket?**

- [ ] Mais de 100 usuários simultâneos
- [ ] Latência > 5 seg se torna problema
- [ ] Team teve experiência anterior com WebSocket
- [ ] Budget para infraestrutura (RabbitMQ, load balancing)

**Como migrar?**

1. **Backend:** Implementar WebSocket endpoint em paralelo ao Polling
2. **Frontend:** Detectar suporte, usar WebSocket se disponível, fallback para Polling
3. **Zero downtime:** Ambas as estratégias convivem

---

## Implementation Details

### Para MVP: Como Implementar

#### 1. Endpoint REST (3 linhas no Controller)

```java
@RestController
@RequestMapping("/service-orders")
public class ServiceOrderController {
    
    @Autowired
    private ServiceExecutionUseCase useCase;
    
    /**
     * GET /service-orders/{id}/status
     * Retorna status e progresso de execução (cacheado por 5 segundos)
     * 
     * @param id ID da Service Order
     * @return ServiceExecutionDTO com status e progresso
     */
    @GetMapping("/{id}/status")
    @Cacheable(
        value = "executionStatusCache",
        key = "#id",
        unless = "#result == null"
    )
    public ResponseEntity<ServiceExecutionDTO> getExecutionStatus(
        @PathVariable Long id
    ) {
        ServiceExecution execution = useCase.getExecution(id);
        return ResponseEntity.ok(mapper.toDTO(execution));
    }
}
```

#### 2. Configurar Cache (application.yml)

```yaml
spring:
  cache:
    type: simple  # Usar SimpleCache para MVP
    cache-names: executionStatusCache
    simple:
      expire-after-write: 5000  # 5 segundos

# Futuro: trocar para Redis
#  cache:
#    type: redis
#    redis:
#      time-to-live: 5000
```

#### 3. Response DTO

```java
public record ServiceExecutionDTO(
    String executionId,
    String technicianName,
    String currentStatus,  // "PENDING", "IN_PROGRESS", "COMPLETED"
    Integer progressPercentage,  // 0-100
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime lastUpdated
) {}
```

#### 4. Frontend (JavaScript simples)

```javascript
class ServiceOrderTracker {
    constructor(orderId, updateIntervalMs = 5000) {
        this.orderId = orderId;
        this.updateIntervalMs = updateIntervalMs;
        this.intervalId = null;
    }
    
    async start() {
        this.intervalId = setInterval(() => this.poll(), this.updateIntervalMs);
    }
    
    async poll() {
        try {
            const response = await fetch(`/service-orders/${this.orderId}/status`);
            const data = await response.json();
            
            // Atualizar UI com dados
            this.updateUI(data);
            
            // Parar se completado
            if (data.currentStatus === 'COMPLETED') {
                this.stop();
            }
        } catch (error) {
            console.error('Erro ao buscar status:', error);
        }
    }
    
    updateUI(data) {
        document.getElementById('status').textContent = data.currentStatus;
        document.getElementById('progress').style.width = data.progressPercentage + '%';
    }
    
    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
        }
    }
}

// Uso
const tracker = new ServiceOrderTracker(123);
tracker.start();
```

---

## Where to Fit in Your Project

### 📁 Estrutura de Pastas (Adicione isto)

```
src/main/java/com/workshop/
├── shared/
│   ├── cache/
│   │   ├── CacheConfig.java              ← Configurar Spring Cache
│   │   └── CacheNames.java               ← Constantes de cache
│   └── dto/
│       └── ServiceExecutionDTO.java      ← DTO de resposta
│
└── serviceorder/
    ├── application/
    │   ├── usecases/
    │   │   └── GetExecutionStatusUseCase.java  ← Novo UC
    │   └── dto/
    │       └── ServiceExecutionDTO.java
    │
    └── infrastructure/
        └── controller/
            └── ServiceOrderStatusController.java ← Novo controller ou existente
```

### 🔧 Arquivos a Criar/Modificar

#### Novo: CacheConfig.java
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("executionStatusCache")
        ));
        return cacheManager;
    }
}
```

#### Novo: GetExecutionStatusUseCase.java
```java
@Service
public class GetExecutionStatusUseCase {
    
    @Autowired
    private IServiceOrderRepository repository;
    
    public ServiceExecution getExecutionStatus(Long serviceOrderId) {
        ServiceOrder order = repository.findById(
            new ServiceOrderId(serviceOrderId)
        ).orElseThrow(() -> 
            new EntityNotFoundException("ServiceOrder não encontrado")
        );
        
        // Retornar execução mais recente ou atual
        return order.getCurrentExecution();
    }
}
```

#### Modificar: ServiceOrderController.java (adicione método)
```java
@GetMapping("/{id}/status")
@Cacheable(value = "executionStatusCache", key = "#id")
public ResponseEntity<ServiceExecutionDTO> getStatus(@PathVariable Long id) {
    ServiceExecution execution = getExecutionStatusUseCase.getExecutionStatus(id);
    return ResponseEntity.ok(ServiceExecutionMapper.toDTO(execution));
}
```

#### Novo: application-dev.yml
```yaml
# application-dev.yml - Configuração para desenvolvimento
spring:
  cache:
    type: simple
    cache-names: executionStatusCache
  jpa:
    hibernate:
      ddl-auto: update
```

#### Novo: application-prod.yml (para futuro AWS)
```yaml
# application-prod.yml - Configuração para produção
spring:
  cache:
    type: redis
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      time-to-live: 5000
```

---

## Testing Strategy

### Unit Test: Use Case
```java
@ExtendWith(MockitoExtension.class)
class GetExecutionStatusUseCaseTest {
    
    @Mock
    private IServiceOrderRepository repository;
    
    @InjectMocks
    private GetExecutionStatusUseCase useCase;
    
    @Test
    @DisplayName("Deve retornar status da execução")
    void shouldReturnExecutionStatus() {
        // Arrange
        ServiceOrder order = new ServiceOrder(1L, "Revisão", Priority.HIGH);
        when(repository.findById(any())).thenReturn(Optional.of(order));
        
        // Act
        ServiceExecution execution = useCase.getExecutionStatus(1L);
        
        // Assert
        assertNotNull(execution);
    }
}
```

### Integration Test: Controller + Cache
```java
@SpringBootTest
@AutoConfigureMockMvc
class ServiceOrderStatusControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Test
    @DisplayName("GET /status deve cachear resposta por 5 segundos")
    void shouldCacheStatusResponse() throws Exception {
        // First call
        mockMvc.perform(get("/service-orders/1/status"))
            .andExpect(status().isOk());
        
        // Verify cache hit (segunda chamada é mais rápida)
        // Isso é testável monitorando tempo de resposta
    }
}
```

---

## Consequências

### Positivas ✅
- ✅ MVP entregue no prazo
- ✅ Código simples, fácil manutenção
- ✅ Fácil testar e debugar
- ✅ Escalável para WebSocket depois
- ✅ Time ganha confiança para Fase 2

### Negativas ❌
- ❌ Overhead de banda vs WebSocket
- ❌ Latência 5-10 seg vs < 1 seg
- ❌ Não é "live" em sentido estrito
- ❌ Se crescer demais, precisa otimizar (Redis, índices)

### Mitigação de Riscos
- Implementar cacheamento desde o início
- Monitorar latência com métricas (Spring Micrometer)
- Planejar migração WebSocket na Fase 2
- Documentar bem para facilitar transição

---

## Related ADRs

- **ADR 002:** (Futuro) WebSocket Implementation Strategy
- **ADR 003:** (Futuro) Caching Strategy (SimpleCache → Redis)
- **ADR 004:** (Futuro) Real-Time Notifications Architecture

---

## References

- [Spring Cache Abstraction](https://spring.io/guides/gs/caching/)
- [Spring WebSocket](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [HTTP Polling vs WebSocket](https://www.ably.io/topic/websockets)
- [Polling Best Practices](https://www.smashingmagazine.com/2018/02/sse-websockets-data-flow-http2/)

---

## Approval Checklist

- [ ] Time concorda com Polling para MVP
- [ ] Team entende plano de migração WebSocket
- [ ] Documentação atualizada em AGENTS.md

---

**Last Updated:** Agosto 2026  
**Decision Maker:** Santiago Silvestre  
**Reviewed By:** [Time]  
**Status:**  - 

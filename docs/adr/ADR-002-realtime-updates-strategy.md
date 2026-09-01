# ADR 002: Real-Time Updates Strategy — Polling vs WebSocket

**Status:** Accepted
**Date:** 2026-08  
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

Detalhes de implementação (endpoints, cache, DTOs, testes) não pertencem a esta ADR — eles são
formalizados no `technical-spec.md` da feature que consome esta decisão (ver
`docs/features/servicelifecycle/track-execution/technical-spec.md` e
`docs/features/servicelifecycle/update-progress/technical-spec.md`). Esta ADR registra apenas a decisão
arquitetural (Polling vs. WebSocket) e seu racional; qualquer esboço de código aqui ficaria desatualizado
frente ao código real.

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

- **ADR-003:** Authentication Strategy (Spring Security + JWT)
- **ADR-004:** Notifications Are an Outbound Capability
- **ADR-005:** Inter-Module Integration Contract — não relacionada à migração WebSocket; o número foi
  usado para essa decisão. Uma futura ADR de WebSocket Implementation Strategy deve confirmar o próximo
  número livre em `docs/adr/` no momento da criação, não reutilizar este.
- **(Futuro, número provisório):** Caching Strategy (SimpleCache → Redis)

---

## References

- [Spring Cache Abstraction](https://spring.io/guides/gs/caching/)
- [Spring WebSocket](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [HTTP Polling vs WebSocket](https://www.ably.io/topic/websockets)
- [Polling Best Practices](https://www.smashingmagazine.com/2018/02/sse-websockets-data-flow-http2/)

---

## Approval Checklist

- [x] Time concorda com Polling para MVP — ratificado em 23 de agosto de 2026
- [ ] Team entende plano de migração WebSocket
- [ ] Documentação atualizada em AGENTS.md

---

**Last Updated:** 2026-08-23  
**Decision Maker:** Santiago Silvestre  
**Reviewed By:** Time de Desenvolvimento — ratificação do item "Polling para MVP" em 2026-08-23  
**Status:** Accepted (ratificado pelo time; ver AD-015 em `docs/Architecture-Decisions.md`)

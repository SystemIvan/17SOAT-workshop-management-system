# ADR 003: Authentication Strategy — Spring Security + JWT vs Spring Authorization Server

**Status:** Proposed
**Date:** 2026-08  
**Deciders:** Time de Desenvolvimento (5 pessoas)  
**Affected By:** Épico 3 (ServiceExecution), APIs administrativas, Segurança  

---

## Context

O **desafio técnico da Pós Tech exige explicitamente:**

> "Implementação de autenticação JWT para APIs administrativas"

A aplicação Workshop Management System precisa de autenticação segura, mas a questão é: **qual abordagem usar?**

**Duas opções principais:**

1. **Spring Security + JWT** (Framework básico + implementação custom de JWT)
2. **Spring Authorization Server** (Servidor OAuth 2.0 dedicado)

Esta ADR compara ambas as abordagens no contexto do MVP.

---

## Problem Statement

### Requisitos Funcionais
- ✅ Autenticação JWT obrigatória
- ✅ APIs administrativas protegidas
- ✅ Roles/Permissões: CUSTOMER, TECHNICIAN, MANAGER, ADMIN
- ✅ Validação de dados sensíveis (CPF/CNPJ)
- ✅ Testes de segurança (80% cobertura)

### Restrições do Projeto
- **Prazo:** 1 mês (Fase 1)
- **Team:** 5 desenvolvedores
- **Escopo:** MVP, monolítico, backend-only
- **Requisitos:** Sem SSO, sem múltiplos serviços, sem provedores externos
- **Experiência:** Não mencionou experiência anterior com OAuth 2.0

---

## Considered Options

### Option 1: Spring Security + JWT ✅ SELECIONADO

**Como funciona:**

```
Cliente                           Servidor (Spring Security)
   │                                      │
   ├─ POST /auth/login ─────────────────>│
   │ {username, password}                 │
   │                                      ├─ Valida credenciais
   │                                      ├─ Gera JWT token
   │  <────────────── {token: "eyJ..."} ──┤
   │                                      │
   ├─ GET /api/service-orders ─────────>│
   │ Header: Authorization: Bearer token  │
   │                                      ├─ Valida token
   │  <────────────── {orders: [...]} ────┤
```

#### Arquitetura

```
┌─────────────────────────────────────────────────┐
│           Spring Boot Application               │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │      Spring Security (Framework)         │  │
│  ├──────────────────────────────────────────┤  │
│  │                                          │  │
│  │  1. SecurityConfig                       │  │
│  │     └─ Define roles, endpoints           │  │
│  │                                          │  │
│  │  2. JwtAuthenticationFilter              │  │
│  │     └─ Valida tokens em cada request    │  │
│  │                                          │  │
│  │  3. JwtUtil (Custom)                     │  │
│  │     └─ Gera/valida JWT tokens           │  │
│  │                                          │  │
│  │  4. AuthController (Custom)              │  │
│  │     └─ POST /auth/login                 │  │
│  │                                          │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

#### Vantagens ✅

- ✅ **Simples:** Spring Security é know-how padrão
- ✅ **Rápido:** Implementação em 4-5 horas
- ✅ **Leve:** Sem servidor separado, sem dependências extras
- ✅ **Monolítico:** Perfeito para MVP backend-only
- ✅ **JWT nativo:** Implementação custom, você controla tudo
- ✅ **Debugging:** Fácil testar com curl, Postman
- ✅ **Testável:** Testes unitários simples e rápidos
- ✅ **Stateless:** Sem sessões, escalável horizontalmente
- ✅ **Dockerizável:** Funciona perfeitamente em containers
- ✅ **Requisito atendido:** "Autenticação JWT" — sim, tem
- ✅ **Time small:** 5 devs não precisam de infraestrutura complexa

#### Desvantagens ❌

- ❌ Sem SSO (Single Sign-On)
- ❌ Sem provedores externos (Google, GitHub, etc)
- ❌ Sem padrão OAuth 2.0 formal (você implementa o JWT)
- ❌ Precisa manter user/password database
- ❌ Se escalar muito (100k+ usuários), pode ficar pesado

---

### Option 2: Spring Authorization Server

**Como funciona:**

```
Cliente                  Auth Server                App Server
   │                         │                          │
   ├─ /oauth2/authorize ───>│                          │
   │                         ├─ Valida user            │
   │  <─ {code} ────────────│                          │
   │                         │                          │
   ├─ /oauth2/token ───────>│                          │
   │ (code + client_secret)  │                          │
   │  <─ {token} ────────────│                          │
   │                                                    │
   ├─ GET /api/service-orders ──────────────────────>│
   │ Header: Authorization: Bearer token              │
   │                                                   │
   │                    ├─ Valida token com Auth Server
   │  <─ {orders} ────────────────────────────────────┤
```

#### Arquitetura

```
┌──────────────────────────┐      ┌──────────────────────────┐
│   Spring Authorization   │      │   Spring Boot App        │
│        Server            │      │   (Resource Server)      │
├──────────────────────────┤      ├──────────────────────────┤
│                          │      │                          │
│ • /oauth2/authorize      │      │ • SecurityConfig         │
│ • /oauth2/token          │      │ • JWT Validation         │
│ • /oauth2/userinfo       │      │ • Controllers            │
│ • Token Management       │      │ • Business Logic         │
│ • User Management        │      │                          │
│ • Client Management      │      │ (Verifica tokens com     │
│                          │<────>│  Authorization Server)   │
│                          │      │                          │
└──────────────────────────┘      └──────────────────────────┘
     (Port 9000)                      (Port 8080)
```

#### Vantagens ✅

- ✅ **Padrão OAuth 2.0:** Indústria standard
- ✅ **SSO:** Suporta Single Sign-On
- ✅ **Provedores externos:** Google, GitHub, etc
- ✅ **Separação de responsabilidades:** Auth centralizado
- ✅ **Multi-tenant:** Um servidor para múltiplas apps
- ✅ **Segurança melhorada:** Especializado em auth
- ✅ **Produção-ready:** Usado em grandes sistemas

#### Desvantagens ❌

- ❌ **Complexo:** Curva de aprendizado alta
- ❌ **Lento:** Requer 2-3 semanas de implementação
- ❌ **Overhead:** Servidor separado, mais recursos
- ❌ **Overkill para MVP:** Muito mais que o necessário
- ❌ **Custo de deploy:** 2 serviços em vez de 1
- ❌ **Time pequeno:** 5 devs é muito para manter
- ❌ **Prazo apertado:** 1 mês não é realista
- ❌ **Sem requisito:** O desafio não exige OAuth, só JWT
- ❌ **Debugar é lento:** 2 serviços envolvidos
- ❌ **Testes complexos:** Mais pontos de falha

---

### Comparison Matrix

| Critério | Spring Security + JWT | Spring Authorization Server |
|----------|----------------------|---------------------------|
| **Tempo implementação** | 4-5h | 2-3 semanas |
| **Complexidade** | 🟢 Baixa | 🔴 Muito Alta |
| **Linhas de código** | ~500 | ~2000+ |
| **Dependências novas** | jjwt | spring-authorization-server, spring-security-oauth2 |
| **Serviços** | 1 | 2 (Auth + App) |
| **JWT nativo** | ✅ Sim | ✅ Sim (mas via servidor) |
| **SSO** | ❌ Não | ✅ Sim |
| **Provedores externos** | ❌ Não | ✅ Sim (Google, GitHub) |
| **Padrão OAuth 2.0** | ❌ Não (custom) | ✅ Sim |
| **Escalabilidade** | 🟡 Média (monolítico) | 🟢 Excelente |
| **Testabilidade** | ✅ Fácil | ❌ Difícil |
| **Debugging** | ✅ Fácil | ❌ Complexo |
| **Atende requisito** | ✅ JWT obrigatório | ✅ JWT obrigatório |
| **Prazo 1 mês** | ✅ Viável | ❌ Não |
| **Team 5 devs** | ✅ Adequado | ❌ Sobrecarga |

---

## Decision

### ✅ SPRING SECURITY + JWT para MVP (Fase 1)

**Por quê?**

1. **Requisito atendido:** Desafio exige "autenticação JWT" — Spring Security + JWT entrega isso
2. **Prazo:** 1 mês é crítico. Spring Security em 4-5h, Authorization Server em 2-3 semanas
3. **Equipe:** 5 devs = não há banda para manter 2 serviços
4. **MVP scope:** Monolítico backend-only, sem SSO, sem múltiplas apps
5. **Simplicidade:** Focus em features de negócio (Service Orders), não infraestrutura
6. **Risco baixo:** Tecnologia conhecida, fácil testar e debugar
7. **Custo:** 1 container Docker vs 2
8. **Futuro:** Fácil evoluir para Authorization Server depois

### 🚀 Estratégia de Escala (Fase 2+)

**Quando considerar Spring Authorization Server:**

- [ ] Mais de 2-3 aplicações (web, mobile, terceiros)
- [ ] Requisito de SSO
- [ ] Requisito de provedores externos (Google, GitHub)
- [ ] 100+ usuários simultâneos
- [ ] Necessidade de OAuth 2.0 formal
- [ ] Team crescer para 10+ devs

**Como migrar sem quebrar:**

1. Manter Spring Security no backend
2. Adicionar Authorization Server em paralelo
3. Frontend detecta qual usar
4. Coexistência até deprecação

---

## Implementation Details

Detalhes de implementação (filtros, endpoints, testes) não pertencem a esta ADR — eles serão
formalizados no `technical-spec.md` da feature de autenticação quando ela for aberta seguindo o gate SDD
do `AGENTS.md`. Esta ADR registra apenas a decisão arquitetural (Spring Security + JWT vs. Spring
Authorization Server) e seu racional.

### Security Checklist (itens a cobrir na especificação/implementação futura)

- [ ] Tokens expiram após 1 hora
- [ ] Refresh tokens implementados (opcional)
- [ ] Senhas com bcrypt (não plaintext)
- [ ] CORS configurado (se tiver frontend)
- [ ] CSRF disabled (stateless)
- [ ] Validação de entrada (CPF/CNPJ)
- [ ] SQL injection prevenido (JPA parametrizado)
- [ ] XSS prevenido (respostas JSON)
- [ ] Rate limiting considerado
- [ ] Logs de segurança (tentativas falhadas)

---

## Consequências

### Positivas ✅

- ✅ MVP entregue no prazo
- ✅ Equipe se concentra em features
- ✅ Implementação simples e rápida
- ✅ Fácil testar e debugar
- ✅ Escalável para Fase 2
- ✅ Atende requisito JWT explícito
- ✅ Análise de vulnerabilidades facilitada

### Negativas ❌

- ❌ Sem SSO
- ❌ Sem padrão OAuth 2.0 formal
- ❌ Sem provedores externos
- ❌ Precisa manter database de users

### Mitigação de Riscos

- Implementar JWT com expiração curta (1h)
- Usar bcrypt para senhas
- Validar entrada rigorosamente
- Testes de segurança (80% cobertura)
- Scan de vulnerabilidades (SonarQube)
- Documentar decisão (esta ADR)

---

## Related ADRs

- **ADR-002:** Real-Time Updates Strategy (Polling vs WebSocket)
- **ADR-004:** Notifications Are an Outbound Capability
- **ADR-007:** (Futuro, número provisório) Spring Authorization Server para Fase 2+

---

## References

- [Spring Security Official Docs](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/)
- [JJWT Library](https://github.com/jwtk/jjwt)
- [Spring Authorization Server Docs](https://spring.io/projects/spring-authorization-server)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)

---

## Approval Checklist

- [ ] Arquiteto concorda com Spring Security + JWT para MVP
- [ ] Team entende fluxo de autenticação
- [ ] SonarLint vai validar segurança
- [ ] Testes de segurança planejados
- [ ] Migração para Authorization Server documentada para Fase 2

---

**Last Updated:** 2026-08  
**Decision Maker:** Santiago Silvestre (Lead Developer) — proposta, aguardando ratificação do time  
**Reviewed By:** [Time]  
**Status:** Proposed — nenhum item do Approval Checklist confirmado pelo time ainda  
**Target Implementation:** A definir após aprovação

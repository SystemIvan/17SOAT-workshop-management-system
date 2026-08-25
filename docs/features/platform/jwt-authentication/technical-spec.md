# Especificação Técnica: Autenticação e autorização JWT

| Campo | Valor |
|---|---|
| Feature | `jwt-authentication` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-24 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-24) |

## Gate de aprovação

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação/teste pode começar antes da
aprovação humana explícita desta especificação.

## Contexto e desenho

Hoje o repositório tem três módulos Spring Modulith (`registration`, `servicelifecycle`,
`stockprocurement`), nenhum deles dono de credenciais, e nenhum endpoint valida chamador. **AD-016**
(`Resolved`, 2026-08-24) decidiu que um módulo interno de Identity/Auth é o dono de credenciais e do
mapeamento papel→ID de domínio.

Esta feature introduz um **quarto módulo Spring Modulith**, `identity`, e uma camada de segurança HTTP
cross-cutting na raiz da aplicação. Isso é uma mudança estrutural — `AGENTS.md` precisa ser atualizado para
listar `identity` como bounded context (junto de `registration`, `servicelifecycle`, `stockprocurement`),
e `ModuleStructureTest` deve continuar verde após a mudança.

`identity` não depende de nenhum outro módulo de domínio, e nenhum outro módulo depende dele. A única
integração é a camada de segurança da raiz da aplicação (`SecurityConfig`, `JwtAuthenticationFilter`)
consumindo a **API pública** do módulo (classes no pacote raiz `identity.auth`, conforme convenção padrão
do Spring Modulith de que o pacote raiz de um módulo é sua superfície pública; sub-pacotes como
`identity.auth.infrastructure` permanecem internos).

### Modelo de domínio (`identity.auth.domain`)

- `UserAccount` (aggregate root): `id` (UUID), `username` (VO `Username`, único, formato e-mail ou login
  simples — validado na criação), `passwordHash` (String, nunca a senha em texto plano — o domínio nunca
  vê a senha original, só o hash), `role` (`Role`), `linkedDomainId` (UUID, nullable).
- `Role` (enum): `CUSTOMER`, `TECHNICIAN`, `MANAGER`, `ADMIN` — conforme `ADR-003` e o functional-spec.
- Invariante: `linkedDomainId` é obrigatório quando `role` é `CUSTOMER` ou `TECHNICIAN` (referencia o
  `Customer.id`/`Technician.id` correspondente); é sempre `null` para `MANAGER`/`ADMIN`, que não
  correspondem a nenhum aggregate de negócio existente. Validado em `UserAccount.create(...)`.
- **`identity` não valida a existência do `linkedDomainId` em `registration`/`servicelifecycle` no
  momento da criação da credencial.** Validar isso exigiria um contrato cross-module que `AD-011`
  (`Team Decision Required`) ainda não resolveu. Assunção temporária segura, coerente com o que `AD-011`
  já recomenda ("consumidores declaram suas próprias interfaces e dependem só de UUID/DTOs"): confiar que
  quem cria a credencial (um `ADMIN`) informa um ID válido; um `linkedDomainId` inexistente não quebra
  `identity`, só torna o token emitido inútil para os casos de uso que também carregam o `Customer`/
  `Technician` pelo ID. Não é uma lacuna desta feature — é uma dependência explícita de `AD-011`.
- O domínio **não depende de Spring Security nem de nenhuma biblioteca de hashing/JWT** (regra do
  `AGENTS.md` — "Keep the domain model free from Spring, JPA and transport concerns"). Duas portas de
  aplicação isolam essas dependências:
  - `PasswordHasher` (`identity.auth.application.port`): `hash(String rawPassword): String`,
    `matches(String rawPassword, String hash): boolean`.
  - `TokenIssuer` (`identity.auth.application.port`): `issue(UserAccount account): IssuedToken`,
    `parse(String token): TokenClaims` (registro com `userAccountId`, `role`, `linkedDomainId`,
    `expiresAt`).

### Casos de uso (`identity.auth.application.usecase`)

- `AuthenticateUserAccountUseCase.execute(username, rawPassword): IssuedToken` — busca por `username`;
  se não encontrado **ou** `PasswordHasher.matches` falhar, lança a mesma
  `InvalidCredentialsException` em ambos os casos (nunca revelar se o username existe — mitigação de
  enumeração de usuários). Em caso de sucesso, `TokenIssuer.issue(account)`.
- `CreateUserAccountUseCase.execute(username, rawPassword, role, linkedDomainId): UserAccountResponse` —
  cria uma credencial nova. Só é alcançável por um chamador já autenticado com `role = ADMIN` (reforçado
  pela camada de autorização HTTP, não pelo caso de uso). `rawPassword` é hasheado via `PasswordHasher`
  antes de `UserAccount.create(...)`.

Nenhum outro caso de uso (alteração de senha, desativação de conta, refresh token) é criado nesta feature
— coerente com "Fora de escopo" do functional-spec.

## Interfaces e fluxo de dados

### Novos endpoints (`identity.auth.infrastructure.web.AuthController`)

| Endpoint | Acesso | Request | Response | Falhas |
|---|---|---|---|---|
| `POST /api/auth/login` | Público | `{username, password}` | `200 {token, role, expiresAt}` | `401` credenciais inválidas (`InvalidCredentialsException`) |
| `POST /api/auth/users` | `ADMIN` | `{username, password, role, linkedDomainId?}` | `201 {id, username, role, linkedDomainId}` | `400` validação; `409` username duplicado |

Ambos documentados via Springdoc (`@Operation`/`@ApiResponses`), seguindo o padrão dos controllers
existentes, e adicionados a `docs/api/postman/workshop-management-system.postman_collection.json`.

### Camada de segurança HTTP

> **Nota de implementação (checkpoint 4):** o plano original desta seção previa
> `br.com.fiap.workshop_management_system.security` (pacote cross-cutting na raiz, espelhando
> `GlobalExceptionHandler`/`OpenApiConfiguration`). Na prática isso criou um ciclo real detectado pelo
> `ModuleStructureTest`: a raiz passaria a depender de `identity` (via `JwtAuthenticationFilter` usando
> `TokenIssuer`) enquanto `identity` já depende da raiz (via `ErrorResponse` nos handlers de erro), e o
> Spring Modulith não permite essa dependência mútua entre a fatia raiz e um módulo. As quatro classes
> (`SecurityConfig`, `JwtAuthenticationFilter`, `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`)
> foram movidas para `br.com.fiap.workshop_management_system.identity` (pacote raiz do próprio módulo
> `identity`, não `identity.auth`) — isso quebra o ciclo porque a dependência passa a ser só
> `identity → raiz` (mesmo sentido que qualquer outro módulo já usa para `ErrorResponse`), nunca o
> inverso. Nenhum contrato HTTP, comportamento ou regra de autorização mudou; é só a localização física
> das classes. Ver evidência no `implementation-plan.md`.

- `SecurityConfig`: `SecurityFilterChain` Spring Security — `csrf` desabilitado (API stateless, sem
  formulário/cookie), `sessionManagement` `STATELESS`, `POST /api/auth/login` liberado, todo o resto
  exige autenticação, com regras por papel via `authorizeHttpRequests` centralizadas nesta classe (não
  espalhadas em cada controller). CORS não é configurado — não há frontend consumidor além dos clientes
  de API já documentados no Swagger/Postman.
- `JwtAuthenticationFilter`: extrai `Authorization: Bearer <token>`, chama `TokenIssuer.parse(token)` (API
  pública de `identity.auth`), popula o `SecurityContext` com `role`/`linkedDomainId` como
  `Authentication`. Token ausente/expirado/inválido → `401`, tratado pelo
  `AuthenticationEntryPoint` (retorna o mesmo formato de `ErrorResponse` do `GlobalExceptionHandler`,
  não a página padrão do Spring Security).
- Papel sem permissão para o endpoint → `403`, tratado por `AccessDeniedHandler` no mesmo formato de
  `ErrorResponse`.

### Matriz de autorização por papel

Classificação de "endpoint administrativo" (requisito do desafio) aplicada por controller/grupo de
endpoint. Verificação por **papel**, não por posse do recurso (posse é "Fora de escopo", ver
functional-spec):

| Controller / endpoint | Papéis autorizados |
|---|---|
| `POST /api/auth/login` | Público |
| `POST /api/auth/users` | `ADMIN` |
| `CustomerController` (`/api/customers/**`) | `MANAGER`, `ADMIN` |
| `VehicleController` (`/api/vehicles/**`) | `MANAGER`, `ADMIN` |
| `TechnicianController` (`/api/technicians/**`) | `MANAGER`, `ADMIN` |
| `StockItemController` (`/api/stock-items/**`) | `MANAGER`, `ADMIN` |
| `StockReservationController` (`/api/stock-reservations/**`) | `MANAGER`, `ADMIN` |
| `PurchaseDemandController` (`/api/purchase-demands/**`) | `MANAGER`, `ADMIN` |
| `PurchaseOrderController` (`/api/purchase-orders/**`) | `MANAGER`, `ADMIN` |
| `ServiceOrderController` — criação, diagnóstico, atribuição, prioridade, progresso, conclusão, anexar peça, finalizar | `MANAGER`, `TECHNICIAN`, `ADMIN` |
| `ServiceOrderController` — `GET /api/service-orders/{id}/status` (tracking) | `CUSTOMER`, `MANAGER`, `TECHNICIAN`, `ADMIN` (qualquer papel autenticado; checagem de posse fica para uma feature futura) |
| `EstimateController` — geração de Estimate | `MANAGER`, `ADMIN` |
| `EstimateController` — `POST /api/estimates/{id}/decisions` | `CUSTOMER`, `ADMIN` |

Esta tabela é a fonte de verdade para os `requestMatchers` de `SecurityConfig`; qualquer endpoint novo
adicionado depois precisa de uma entrada explícita aqui (atualizar esta spec) antes de ficar acessível sem
revisão de segurança.

### Impacto em `AGENTS.md`

- Seção "Bounded contexts": adicionar `identity`: "User accounts, credentials and role-to-domain-ID
  mapping for JWT authentication."
- Nenhuma outra seção do `AGENTS.md` muda.

## Persistência e dados de bootstrap

Nova tabela `user_accounts` (`id UUID PK`, `username VARCHAR(255) UNIQUE NOT NULL`,
`password_hash VARCHAR(255) NOT NULL`, `role VARCHAR(20) NOT NULL`, `linked_domain_id UUID NULL`,
`created_at TIMESTAMP NOT NULL`). Migração Flyway `V<timestamp>__create_user_accounts.sql`.

Classificação de dado, por linha inserida:

- **Mandatory reference data (via Flyway, na mesma migração ou em uma migração `V<timestamp>__seed_bootstrap_admin_account.sql` separada):** exatamente uma conta `ADMIN` de bootstrap (`username = "admin"`), necessária porque sem ela ninguém consegue autenticar para criar as demais contas (`POST /api/auth/users` exige `ADMIN`) — não é um dado de negócio de exemplo (não é um Customer fictício), é um requisito operacional do próprio módulo, análogo ao papel de uma migração de schema. A senha inicial é uma constante documentada (não gerada em runtime) e o `README.md` e o `technical-spec.md` do checkpoint de implementação devem instruir a rotacioná-la imediatamente fora de um ambiente de demonstração — registrado como achado da revisão de segurança abaixo, não escondido.
- **Local demonstration data (seeder `dev` + `app.seed.enabled=true`, módulo-owned em `identity`):**
  contas de exemplo `MANAGER`, `TECHNICIAN` (linkada a um `Technician` do seeder de `servicelifecycle`, se
  existir) e `CUSTOMER` (linkada a um `Customer` do seeder de `registration`, se existir), para permitir
  demonstrar os fluxos protegidos no vídeo de entrega do desafio sem depender só da conta `ADMIN`.
- **Test fixture:** builders dedicados (`UserAccountTestDataBuilder` ou equivalente) para os testes desta
  feature; nenhum teste depende do seeder de demonstração, conforme regra do `AGENTS.md`.

`UserAccountJpaEntity`/`UserAccountPersistenceMapper`/`UserAccountRepositoryImpl` seguem o padrão já usado
pelos outros módulos (ex.: `CustomerJpaEntity`/`CustomerPersistenceMapper`).

## Segurança e operação

- **Hashing de senha:** bcrypt via `PasswordHasher` (implementado com `BCryptPasswordEncoder` do Spring
  Security no adapter de infraestrutura). Senha nunca logada, nunca retornada em nenhuma response.
- **JWT:** assinado com HS256, segredo lido de variável de ambiente (`APP_SECURITY_JWT_SECRET`, adicionada
  a `.env.example` e `docker-compose.yml`, nunca hardcoded no código-fonte nem em `application.yml`).
  Expiração de 1 hora (`ADR-003`). Claims: `sub` (userAccountId), `role`, `linkedDomainId` (quando
  presente), `iat`, `exp`. Nenhum dado pessoal (nome, CPF/CNPJ, e-mail de contato) entra no token.
- **Nova dependência:** `spring-boot-starter-security` e uma biblioteca JWT (`io.jsonwebtoken:jjwt-api` +
  `jjwt-impl`/`jjwt-jackson`, runtime), conforme `ADR-003`. Registrar no checkpoint de segurança do
  `implementation-plan.md` a verificação de CVEs conhecidas nessas versões antes de fixá-las no `pom.xml`.
- **Enumeração de usuário:** `POST /api/auth/login` retorna a mesma mensagem/status para "usuário não
  existe" e "senha incorreta" (`401` genérico).
- **CSRF/CORS:** CSRF desabilitado (API stateless, sem sessão de navegador); CORS não configurado — não
  há frontend integrado nesta fase (mesma leitura já registrada em `ADR-003`).
- **Validação de entrada:** `LoginRequest`/`CreateUserAccountRequest` usam Bean Validation
  (`@NotBlank`, `@Email` quando aplicável) na borda HTTP, seguindo o padrão do projeto.
- **Erros mapeados:** `InvalidCredentialsException → 401`, `DuplicateUsernameException → 409`,
  `AuthenticationException`/token ausente-inválido-expirado → `401` (via `AuthenticationEntryPoint`),
  autorização insuficiente → `403` (via `AccessDeniedHandler`) — todos no mesmo formato `ErrorResponse` já
  usado pelo `GlobalExceptionHandler`, sem stack trace nem detalhe interno exposto.
- **Rotação da conta `ADMIN` de bootstrap:** achado de segurança aceito com mitigação documentada (ver
  seção de persistência acima) — não é um bloqueador para `Implemented`, mas precisa constar no checkpoint
  de segurança do `implementation-plan.md` como item explícito, não implícito.
- **Rate limiting / log de tentativas falhadas:** fora de escopo (functional-spec); registrar como `N/A —
  fora de escopo desta feature` no checkpoint de segurança, não como esquecido.
- **Promoção de `ADR-003`:** ao final da implementação, `docs/adr/ADR-003-authentication-strategy.md` move
  de `Status: Proposed` para `Status: Accepted`, com cada item do Approval Checklist marcado e ligado à
  evidência correspondente (ex.: "Tokens expiram após 1 hora" → link para o teste/config que comprova).

## Estratégia de testes

- **Domínio (`UserAccount`):** criação válida por papel; falha quando `CUSTOMER`/`TECHNICIAN` sem
  `linkedDomainId`; falha quando `MANAGER`/`ADMIN` com `linkedDomainId` não nulo; `Username` rejeita valor
  vazio/duplicável na igualdade de VO.
- **Aplicação (`AuthenticateUserAccountUseCase`):** login com credenciais corretas emite token; username
  inexistente e senha incorreta lançam a mesma exceção (teste explícito comparando os dois caminhos);
  `PasswordHasher`/`TokenIssuer` mockados nesta camada.
- **Aplicação (`CreateUserAccountUseCase`):** cria conta com sucesso; username duplicado falha; senha é
  persistida como hash, nunca em texto plano (assert no mock do repositório).
- **Infraestrutura (`BCryptPasswordHasher`, `JwtTokenIssuer`):** teste de round-trip (hash→matches,
  issue→parse), teste de token expirado sendo rejeitado por `parse`, teste de assinatura inválida sendo
  rejeitada.
- **HTTP (`AuthController`):** `MockMvc` cobrindo os dois endpoints e seus códigos de erro.
- **HTTP (`SecurityConfig`/`JwtAuthenticationFilter`), por controller existente:** para cada linha da
  matriz de autorização, ao menos um teste de `401` sem token, um de `403` com papel errado e um de
  sucesso com papel correto — reaproveitando os `MockMvcTest` já existentes de cada controller, adicionando
  o cabeçalho `Authorization` em vez de duplicar toda a suíte.
- **Módulo (`ModuleStructureTest`):** deve continuar verde com o novo módulo `identity`. Na implementação
  real, as classes de segurança HTTP ficaram dentro de `identity` (não num pacote cross-cutting na raiz —
  ver nota de implementação na seção "Camada de segurança HTTP" acima) justamente para manter
  `modules.verify()` sem ciclos.
- **Persistência:** teste de migração/startup para `user_accounts`, incluindo a linha de bootstrap
  `ADMIN` inserida pela migração; teste de mapeamento cobrindo os quatro papéis e `linked_domain_id`
  nulo/não nulo.
- Nenhum teste existente de `registration`/`servicelifecycle`/`stockprocurement` muda de comportamento
  além de precisar de um `Authorization` header válido nos testes HTTP — atualizar os `MockMvcTest`
  existentes desses controllers para autenticar antes de chamar o endpoint, sem alterar as asserções de
  negócio já existentes.

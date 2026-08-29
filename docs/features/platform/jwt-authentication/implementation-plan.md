# Plano de Implementação: Autenticação e autorização JWT

| Campo | Valor |
|---|---|
| Feature | `jwt-authentication` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-24 |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-24) |

## Objetivo

Introduzir o quarto módulo Spring Modulith `identity` (dono de credenciais e do mapeamento
papel→ID de domínio, conforme AD-016) e uma camada de segurança HTTP cross-cutting na raiz da
aplicação, protegendo os endpoints administrativos já existentes com autenticação JWT e autorização
por papel (`CUSTOMER`, `TECHNICIAN`, `MANAGER`, `ADMIN`), conforme `technical-spec.md`. Ao final,
`docs/adr/ADR-003-authentication-strategy.md` é promovida de `Proposed` para `Accepted`.

## Checkpoint 1 — Domínio (`identity.auth.domain`)

Criar:
- `Role` (enum): `CUSTOMER`, `TECHNICIAN`, `MANAGER`, `ADMIN`.
- `Username` (VO): valida não-vazio e formato; igualdade por valor.
- `UserAccount` (aggregate root): `id`, `username` (`Username`), `passwordHash` (String), `role` (`Role`),
  `linkedDomainId` (UUID, nullable). `create(...)` valida: `linkedDomainId` obrigatório quando
  `role ∈ {CUSTOMER, TECHNICIAN}`; sempre `null` quando `role ∈ {MANAGER, ADMIN}`.
- Portas de aplicação (`identity.auth.application.port`): `PasswordHasher` (`hash`/`matches`),
  `TokenIssuer` (`issue`/`parse`, com `IssuedToken`/`TokenClaims` como records).
- `identity.auth.domain.UserAccountRepository` (contrato de repositório, seguindo o padrão dos outros
  módulos).

Testes (`UserAccountTest`):
- criação válida para cada um dos 4 papéis;
- falha (`InvalidUserAccountException` ou equivalente) quando `CUSTOMER`/`TECHNICIAN` sem
  `linkedDomainId`;
- falha quando `MANAGER`/`ADMIN` com `linkedDomainId` não nulo;
- `Username` rejeita valor vazio.

## Checkpoint 2 — Persistência

Criar:
- Migração Flyway `V<timestamp>__create_user_accounts.sql`: tabela `user_accounts` (`id UUID PK`,
  `username VARCHAR(255) UNIQUE NOT NULL`, `password_hash VARCHAR(255) NOT NULL`,
  `role VARCHAR(20) NOT NULL`, `linked_domain_id UUID NULL`, `created_at TIMESTAMP NOT NULL`).
- Migração Flyway `V<timestamp>__seed_bootstrap_admin_account.sql` (mandatory reference data, não
  seed de negócio — ver `technical-spec.md` §Persistência): insere exatamente uma conta `ADMIN`
  (`username = "admin"`), com hash bcrypt de uma senha documentada. Comentário SQL explícito
  instruindo rotação imediata fora de ambiente de demonstração.
- `UserAccountJpaEntity`, `UserAccountPersistenceMapper`, `UserAccountRepositoryImpl`
  (`identity.auth.infrastructure.persistence`), seguindo o padrão de `CustomerJpaEntity`/
  `CustomerPersistenceMapper`.
- Seeder de demonstração `identity` (`dev` + `app.seed.enabled=true`): contas `MANAGER`, `TECHNICIAN`
  (linkada a um `Technician` do seeder de `servicelifecycle`, se existir) e `CUSTOMER` (linkada a um
  `Customer` do seeder de `registration`, se existir). Idempotente, sem dado pessoal real.

Testes:
- `UserAccountPersistenceMapperTest`: round-trip para os 4 papéis, `linked_domain_id` nulo/não nulo.
- Teste de migração/startup: tabela `user_accounts` existe; a conta `admin` de bootstrap está presente
  após a migração.

## Checkpoint 3 — Aplicação (`identity.auth.application.usecase`) e infraestrutura das portas

Criar:
- `AuthenticateUserAccountUseCase.execute(username, rawPassword): IssuedToken` — mesma exceção
  (`InvalidCredentialsException`) para username inexistente e senha incorreta.
- `CreateUserAccountUseCase.execute(username, rawPassword, role, linkedDomainId): UserAccountResponse`
  — hasheia a senha antes de `UserAccount.create(...)`; `DuplicateUsernameException` em conflito.
- `BCryptPasswordHasher` (`identity.auth.infrastructure.security`), implementando `PasswordHasher` com
  `BCryptPasswordEncoder`.
- `JwtTokenIssuer` (`identity.auth.infrastructure.security`), implementando `TokenIssuer` com
  `io.jsonwebtoken` (HS256, segredo de `APP_SECURITY_JWT_SECRET`, expiração de 1h).

Alterar:
- `pom.xml`: adicionar `spring-boot-starter-security`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson`.
- `.env.example`, `docker-compose.yml`: nova variável `APP_SECURITY_JWT_SECRET`.

Testes:
- `AuthenticateUserAccountUseCaseTest`: sucesso emite token; username inexistente e senha incorreta
  lançam a mesma exceção (teste comparando os dois caminhos, com `PasswordHasher`/repositório mockados).
- `CreateUserAccountUseCaseTest`: sucesso; username duplicado falha; senha nunca chega em texto plano
  ao repositório mockado.
- `BCryptPasswordHasherTest`: round-trip hash/matches.
- `JwtTokenIssuerTest`: round-trip issue/parse; token expirado rejeitado; assinatura inválida rejeitada.

## Checkpoint 4 — Segurança HTTP

Criar:
- `SecurityConfig` (`SecurityFilterChain`): CSRF off, sessão `STATELESS`, `POST /api/auth/login`
  público, matriz de autorização por papel conforme a tabela do `technical-spec.md` aplicada via
  `requestMatchers`.
- `JwtAuthenticationFilter`: extrai `Authorization: Bearer`, chama `TokenIssuer.parse` (API pública de
  `identity.auth`), popula `SecurityContext`.
- `ApiAuthenticationEntryPoint` (401) e `ApiAccessDeniedHandler` (403), respondendo no formato
  `ErrorResponse` já usado por `GlobalExceptionHandler`.

Alterar:
- `AGENTS.md`: adicionar `identity` à lista de bounded contexts.

Testes:
- Criado `SecurityAuthorizationTest` (raiz, 10 casos) exercitando a matriz de ponta a ponta pelo filtro
  real (`.apply(springSecurity())`): sem token → `401`; papel errado → `403`; papel correto passa da
  segurança para a lógica de negócio; `/api/auth/login` alcançável sem token; token adulterado → `401`.
  Os `MockMvcTest` existentes dos controllers **não** foram tocados neste checkpoint — eles constroem o
  `MockMvc` sem `.apply(springSecurity())`, então nunca passam pelo filtro de segurança e continuam
  verdes sem exercitar autorização nenhuma. Retrofit desses arquivos ficou para o Checkpoint 5.
- `ModuleStructureTest`: continua verde.

**Desvio em relação ao plano original:** as quatro classes de segurança (`SecurityConfig`,
`JwtAuthenticationFilter`, `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`) foram implementadas
primeiro em `br.com.fiap.workshop_management_system` (raiz, conforme planejado), mas isso criou um ciclo
real `raiz ⇄ identity` detectado pelo `ModuleStructureTest` (raiz depende de `identity` via
`JwtAuthenticationFilter`/`TokenIssuer`; `identity` já depende da raiz via `ErrorResponse` nos handlers de
erro). Movidas para `br.com.fiap.workshop_management_system.identity` (pacote raiz do módulo, não
`identity.auth`) — resolve o ciclo porque a dependência passa a ser só `identity → raiz`, mesmo sentido já
usado por todo módulo existente. Nenhum contrato ou comportamento mudou. `technical-spec.md` atualizada
com nota de implementação equivalente.

Também precisei expor `identity.auth.application.port` e `identity.auth.domain.model` como
`@NamedInterface` do Spring Modulith (`package-info.java` novos) — sem isso, mesmo a versão final em
`identity` não conseguiria ser consumida por `AuthController`/`JwtAuthenticationFilter` de fora do
sub-pacote `auth`, pois o Modulith só expõe o pacote raiz de um módulo por padrão.

## Checkpoint 5 — API e documentação (`identity.auth.infrastructure.web`)

Criado:
- `AuthController`: `POST /api/auth/login` (público), `POST /api/auth/users` (`ADMIN`), com
  `@Operation`/`@ApiResponses` (Springdoc).
- `LoginRequest`, `CreateUserAccountRequest` (Bean Validation), `IssuedTokenResponse`,
  `UserAccountResponse`.
- `AuthExceptionHandler`: `InvalidCredentialsException → 401 INVALID_CREDENTIALS`,
  `DuplicateUsernameException → 409 USERNAME_ALREADY_EXISTS` (mesmo padrão de
  `CustomerExceptionHandler`/`@RestControllerAdvice(assignableTypes = ...)`).
- `OpenApiConfiguration`: novo `SecurityScheme` `bearerAuth` (HTTP bearer/JWT) registrado nos
  `Components`, para o Swagger UI oferecer o botão "Authorize".
- `testsupport.TestAuth` (test-only): mint de token `ADMIN` sem persistência, reaproveitado pelos 18
  `MockMvcTest` retrofitted abaixo.

Alterado:
- `docs/api/postman/workshop-management-system.postman_collection.json`: nova pasta "Auth" (Login com
  script de teste que salva `authToken` como variável de collection; Create user account), variável
  `authToken`, e `auth` de nível de collection (`bearer {{authToken}}`) — toda requisição da collection
  herda o bearer automaticamente, exceto o próprio Login (`noauth`).
- **18 `MockMvcTest` existentes** (não 7 controllers — vários controllers têm mais de um arquivo de
  teste): `CustomerControllerTest`, `CustomerContactControllerTest`, `CustomerLifecycleControllerTest`,
  `VehicleControllerTest`, `UpdateVehicleControllerTest`, `EstimateControllerTest`,
  `EstimateControllerDecideLinesTest`, as 10 classes `ServiceOrderController*Test`,
  `TechnicianControllerStatusConflictTest`, `StockItemControllerTest`,
  `StockReservationControllerTest`. Cada um passou a construir o `MockMvc` com
  `.apply(springSecurity())` e um `defaultRequest(...)` carregando `Authorization: Bearer` (token
  `ADMIN` via `TestAuth`) — nenhuma asserção de negócio foi alterada, só o `setUp()`.
  `OpenApiContractTest` e `AuthControllerTest` foram deixados sem essa mudança de propósito: o primeiro
  só lê `/v3/api-docs` (público); o segundo testa o próprio `AuthController`, cujo endpoint de login é
  público e cujo endpoint de criação de conta já é coberto para autorização por `SecurityAuthorizationTest`.

Testes:
- `AuthControllerTest` (7 casos): criar conta e logar em seguida; senha errada e username desconhecido
  retornam o mesmo `401 INVALID_CREDENTIALS`; username duplicado retorna `409`; campos em branco/ausentes
  retornam `400`; conta `CUSTOMER` linkada a um `linkedDomainId` é criada corretamente.
- `OpenApiContractTest`: dois novos casos (`documentAuthenticationContract` e assert adicional em
  `documentEveryCurrentHttpOperation`) confirmando `/api/auth/login`/`/api/auth/users` documentados, com
  os códigos de resposta esperados e o `securityScheme` `bearerAuth`.

## Checkpoint 6 — Promoção de `ADR-003` e validação final

Alterar:
- `docs/adr/ADR-003-authentication-strategy.md`: `Status: Proposed → Accepted`; marcar cada item do
  Approval Checklist com link para a evidência (teste/config) que o satisfaz.

Executar:
- `./mvnw test -Dtest=UserAccountTest,AuthenticateUserAccountUseCaseTest,CreateUserAccountUseCaseTest,BCryptPasswordHasherTest,JwtTokenIssuerTest,AuthControllerTest`
- `./mvnw test` (suíte completa — confirmar ausência de regressão nos `MockMvcTest` atualizados dos
  outros controllers).
- `./mvnw test -Dtest=ModuleStructureTest`.
- `make verify` / `./mvnw verify`.

Revisar:
- Matriz de autorização do `technical-spec.md` corresponde exatamente aos `requestMatchers` de
  `SecurityConfig` — nenhum endpoint administrativo ficou de fora.
- OpenAPI e Postman refletem o contrato final.

## Definition of Done

- [x] `identity.auth` (domínio, aplicação, infraestrutura) implementado e testado.
- [x] Migração Flyway aplicada; conta `ADMIN` de bootstrap presente; seeder de demonstração testado.
- [x] `SecurityConfig`/`JwtAuthenticationFilter` protegendo todos os endpoints da matriz do
      `technical-spec.md` (implementados em `identity`, não na raiz — ver desvio registrado no
      Checkpoint 4).
- [x] `AuthController` implementado e testado; OpenAPI e Postman atualizados.
- [x] `MockMvcTest` existentes (18 arquivos) atualizados com autenticação, sem regressão de asserção de
      negócio.
- [x] `AGENTS.md` atualizado com `identity` como bounded context.
- [x] `docs/adr/ADR-003-authentication-strategy.md` em `Accepted`, Approval Checklist marcado com
      evidência (4 de 5 itens; "SonarLint vai validar segurança" permanece aberto, hoje rastreado em
      `docs/tech-debt/TD-003-achados-de-seguranca-aceitos-sem-correcao.md`, não bloqueador).
- [x] Testes relevantes passando.
- [x] `make verify` passando (`./mvnw verify`, JaCoCo incluso).
- [x] Revisão de segurança concluída, com achados e mitigações registrados.
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: `LoginRequest`/`CreateUserAccountRequest` com Bean Validation.
- **Autenticação/autorização**: objeto central desta feature — cobertura descrita nos checkpoints 4 e 5.
- **Exposição de dados**: `passwordHash` nunca sai em nenhuma response; token não contém dado pessoal
  (nome, CPF/CNPJ, contato).
- **Segredos/logs**: `APP_SECURITY_JWT_SECRET` via variável de ambiente, nunca hardcoded; senha e token
  nunca logados.
- **SQL/persistência/migration**: migrações aditivas (`CREATE TABLE`, `INSERT`), sem `DROP`/`ALTER`
  destrutivo.
- **Erros e disclosure**: `401` genérico para username inexistente e senha incorreta (anti-enumeração);
  `403` sem detalhe de política interna; nenhum stack trace exposto.
- **Dependências novas**: `spring-boot-starter-security`, `jjwt-*` — checar CVEs conhecidas antes de
  fixar versão no `pom.xml` (registrar aqui o resultado).
- **Abuso**: rate limiting e log de tentativas falhadas ficam **fora de escopo** (functional-spec) —
  registrado aqui como `N/A — fora de escopo`, não esquecido.
- **Achado aceito com mitigação**: conta `ADMIN` de bootstrap tem senha inicial documentada e conhecida
  — mitigação é a instrução explícita de rotação imediata fora de ambiente de demonstração (README +
  este plano). Não bloqueia `Implemented`, mas deve constar no relatório de vulnerabilidades do
  desafio como item tratado — **cumprido** em `docs/security/vulnerability-report.md` §5 e registrado em
  `docs/tech-debt/TD-003-achados-de-seguranca-aceitos-sem-correcao.md` (Grupo 4).

## Evidências de verificação

- Checkpoint 1: `./mvnw test -Dtest=UserAccountTest,UsernameTest` — 2026-08-24, 16 testes, 0 falhas.
  `ModuleStructureTest` verde após adicionar `identity` à lista esperada de módulos e ao `AGENTS.md`.
- Checkpoint 2: `./mvnw test -Dtest=UserAccountTest,UsernameTest,UserAccountPersistenceMapperTest,UserAccountRepositoryIntegrationTest`
  — 2026-08-24, 24 testes, 0 falhas. Migração `V20260824120000__create_user_accounts.sql` aplicada
  limpo junto às 12 pré-existentes; constraint `uk_user_accounts_username` confirmada via teste de
  conflito.
- Checkpoint 3: `./mvnw test -Dtest=AuthenticateUserAccountUseCaseTest,CreateUserAccountUseCaseTest,BCryptPasswordHasherTest,JwtTokenIssuerTest`
  — 2026-08-24, 14 testes, 0 falhas. Bug real corrigido: `JwtTokenIssuer.parse` não usava o `Clock`
  injetado para validar expiração (usava o relógio real do sistema) — corrigido com
  `.clock(() -> Date.from(clock.instant()))` no `JwtParserBuilder`. Hash bcrypt real gerado para a
  migration de bootstrap do `ADMIN` (não um valor inventado) e validado por
  `BootstrapAdminAccountMigrationTest` (`./mvnw test -Dtest=BootstrapAdminAccountMigrationTest`,
  1 teste, 0 falhas). `IdentityDevelopmentDataSeederTest` — 1 teste, 0 falhas.
  `./mvnw test` completo — 2026-08-24, 388 testes, 0 falhas (nenhuma regressão).
- Checkpoint 4: dois bugs reais corrigidos: (1) `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler`
  tentavam injetar um `ObjectMapper` bean inexistente — trocado por instância própria; (2) beans de
  segurança quebravam `@ApplicationModuleTest(DIRECT_DEPENDENCIES)` de outros módulos que não incluem
  `identity` — corrigido com `@ConditionalOnBean`. Ciclo `raiz ⇄ identity` motivou mover as 4 classes de
  segurança para `identity` (ver desvio acima) — exigiu expor `identity.auth.application.port` e
  `identity.auth.domain.model` via `@NamedInterface`. `SecurityAuthorizationTest` criado (10 casos) —
  `./mvnw test -Dtest=SecurityAuthorizationTest`, 2026-08-24, 10 testes, 0 falhas. `./mvnw test`
  completo — 388 testes, 0 falhas.
- Checkpoint 5: `AuthControllerTest` (7 casos) e 2 novos casos em `OpenApiContractTest` —
  `./mvnw test -Dtest=AuthControllerTest,OpenApiContractTest`, 2026-08-24, 16 testes, 0 falhas.
  Retrofit dos 18 `MockMvcTest` existentes com `.apply(springSecurity())` + `defaultRequest` carregando
  um token `ADMIN` (via `testsupport.TestAuth`) — nenhuma asserção de negócio alterada. Postman
  atualizado (pasta "Auth", variável `authToken`, `auth` de collection); JSON validado
  (`JSON.parse` via Node sem erro).
  `./mvnw test` completo — 2026-08-24, **396 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`.
- Checkpoint 6: `docs/adr/ADR-003-authentication-strategy.md` promovida `Proposed → Accepted`
  (`Status` no topo do documento + seção "Approval Checklist", ambos atualizados). Dos 5 itens do
  Approval Checklist, 4 confirmados (2 de consenso humano confirmados por Santiago Silvestre em
  2026-08-24 via pergunta explícita — o agente não inferiu essa aprovação; os outros 2 confirmados por
  evidência técnica direta) e 1 deixado explicitamente aberto ("SonarLint vai validar segurança" — não
  configurado, hoje rastreado em `docs/tech-debt/TD-003-achados-de-seguranca-aceitos-sem-correcao.md`).
  Do Security Checklist da mesma ADR, 6 de 10
  itens marcados com evidência de teste/config; 4 deixados abertos com justificativa (`N/A` ou
  explicitamente fora de escopo), não escondidos.
  `./mvnw verify` — 2026-08-24, **396 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`, JaCoCo gerado.
  Cobertura de instruções do módulo `identity` por pacote: `domain.model` 100%, `application.port` 100%,
  `application.usecase` 100%, `application.dto` 100%, `application.exception` 100%,
  `infrastructure.security` 100%, `infrastructure.web` 100%, `infrastructure.persistence` 90%,
  `infrastructure.bootstrap` 90% — todos acima da meta de 80% do projeto. Cobertura global do projeto:
  93% de instruções.

## Rollback ou recuperação

Reversível via `git revert` do commit/PR da feature. As migrações são aditivas (`CREATE TABLE`,
`INSERT`); reverter o código sem reverter as migrações deixa uma tabela `user_accounts` não utilizada
no banco, o que é inofensivo (sem perda de dado de outro módulo, sem violação de constraint em tabelas
existentes). Se as migrações precisarem ser desfeitas também, isso exige novas migrações
`DROP TABLE`/`DELETE` — o projeto não usa `DOWN` migrations. Em caso de rollback, qualquer `SecurityConfig`
que já esteja em produção precisa ser removida/desabilitada junto, para não deixar endpoints exigindo um
módulo `identity` que não existe mais.

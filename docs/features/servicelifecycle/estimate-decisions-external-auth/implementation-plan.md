# Plano de Implementação: Autenticação externa de `POST /api/estimates/{estimateId}/decisions`

| Campo | Valor |
|---|---|
| Feature | `estimate-decisions-external-auth` |
| Status | In Progress |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-26 |
| Especificação técnica | `./technical-spec.md` (Approved, 2026-09-23) |

## Objetivo

Fazer `POST /api/estimates/{estimateId}/decisions` aceitar, além do JWT de usuário interno já existente,
uma segunda forma de autenticação (HMAC-SHA256 assinado + timestamp) para o gateway externo de aprovação do
Customer, sem tocar `DecideEstimateLinesUseCase`, sem estender o enum `Role`/mapeamento role→domain-ID
(AD-016), e sem quebrar nenhum chamador JWT existente.

## Checkpoint 1 — `CachedBodyHttpServletRequest`

Criar `identity.CachedBodyHttpServletRequest extends HttpServletRequestWrapper`:
- lê `getInputStream()` da requisição original uma única vez no construtor, guarda em `byte[]`;
- `getInputStream()` retorna sempre um novo `ServletInputStream` sobre esse array;
- `getReader()` retorna sempre um novo `BufferedReader` sobre esse array (mesmo charset da requisição
  original).

Testes (`CachedBodyHttpServletRequestTest`):
- `getInputStream()` chamado duas vezes retorna o conteúdo completo nas duas vezes;
- `getReader()` chamado depois de `getInputStream()` também retorna o conteúdo completo;
- corpo vazio não lança exceção.

## Checkpoint 2 — `EstimateGatewayHmacAuthenticationFilter`

Criar `identity.EstimateGatewayHmacAuthenticationFilter extends OncePerRequestFilter`:
- construtor recebe o segredo (`@Value("${app.security.estimate-gateway.hmac-secret}")`) e a tolerância de
  timestamp (`@Value("${app.security.estimate-gateway.timestamp-tolerance-seconds:300}")`) — mesmo padrão
  de injeção de `JwtTokenIssuer`;
- `doFilterInternal`: se `!HttpMethod.POST.matches(request.getMethod())` ou o path não casar com
  `/api/estimates/*/decisions` (via `AntPathMatcher`), chama `filterChain.doFilter(request, response)`
  sem envolver nada e retorna;
- caso contrário, envolve a requisição em `CachedBodyHttpServletRequest`, lê
  `X-Estimate-Gateway-Timestamp`/`X-Estimate-Gateway-Signature`;
- se ambos presentes: recalcula `HMAC-SHA256(timestamp + "." + rawBody, secret)`, compara com
  `MessageDigest.isEqual`, e valida `|now - timestamp| <= tolerância`;
- válido → `SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
  "estimate-approval-gateway", null, List.of(new SimpleGrantedAuthority("ESTIMATE_APPROVAL_GATEWAY"))))`;
- ausente/inválido → `SecurityContextHolder.clearContext()`, sem lançar exceção;
- `filterChain.doFilter` sempre recebe a requisição envolvida (para o path relevante) ou a original (para
  os demais paths).

Adicionar as duas properties em `application.properties` (segredo com valor de desenvolvimento explícito,
mesmo padrão de `app.security.jwt.secret`) e replicar em `application-dev.properties` se aplicável.

Testes (`EstimateGatewayHmacAuthenticationFilterTest`):
- assinatura válida + timestamp dentro da janela → contexto autenticado com `ESTIMATE_APPROVAL_GATEWAY`;
- headers ausentes → contexto vazio, sem exceção, chain continua;
- assinatura presente mas incorreta → contexto vazio;
- timestamp fora da janela (hash correto para aquele timestamp) → contexto vazio;
- requisição para outro path (`POST /api/service-orders`, por exemplo) → filtro não mexe no corpo nem no
  contexto;
- corpo ainda legível por um segundo consumidor após o filtro (prova de que `CachedBodyHttpServletRequest`
  não quebra leitura posterior).

## Checkpoint 3 — Wiring em `SecurityConfig`

- Registrar `EstimateGatewayHmacAuthenticationFilter` como `@Component` e adicioná-lo à cadeia:
  `.addFilterBefore(estimateGatewayHmacAuthenticationFilter, JwtAuthenticationFilter.class)`.
- Atualizar a regra existente:
  `.requestMatchers(HttpMethod.POST, "/api/estimates/*/decisions").hasAnyAuthority("CUSTOMER", "ADMIN", "ESTIMATE_APPROVAL_GATEWAY")`.

Testes de integração HTTP (`MockMvc`, em cima do que já existe de `EstimateControllerDecideLinesTest` ou em
uma classe nova dedicada a este caminho):
- HMAC válido, sem `Authorization` → mesmo resultado de sucesso já coberto por `decide-estimate-lines`;
- sem HMAC e sem JWT → `401`, `DecideEstimateLinesUseCase` nunca invocado;
- HMAC inválido → `401`, caso de uso não invocado;
- timestamp expirado → `401`, caso de uso não invocado;
- JWT válido (`CUSTOMER`/`ADMIN`), sem headers HMAC → comportamento idêntico ao já coberto por
  `decide-estimate-lines` (regressão pontual, não duplicar a suíte inteira).
- `ModuleStructureTest` continua verde.

## Checkpoint 4 — Documentação e contrato

- `README.md`: corrigir as duas afirmações que hoje dizem, sem ressalva, que todos os endpoints
  administrativos exigem JWT; documentar o segundo caminho de autenticação e como simular uma chamada
  assinada localmente (segredo de desenvolvimento, formato dos headers, string a assinar).
- OpenAPI: garantir que `401` está documentado no endpoint (`@ApiResponses` em `EstimateController`) e que
  a descrição menciona o caminho de autenticação alternativo.
- Postman (`docs/api/postman/workshop-management-system.postman_collection.json`): adicionar uma requisição
  de exemplo simulando o gateway, com pre-request script (`CryptoJS.HmacSHA256`, disponível no sandbox do
  Postman) calculando os dois headers a partir do corpo e do segredo de desenvolvimento; documentar o fluxo
  no `README.md` da feature (variáveis, ordem, resultado esperado), conforme exigido por `AGENTS.md` para
  qualquer mudança na coleção.

## Checkpoint 5 — Validação final

Executar:
- testes novos desta feature (`CachedBodyHttpServletRequestTest`,
  `EstimateGatewayHmacAuthenticationFilterTest`, testes de integração HTTP do checkpoint 3);
- `./mvnw test` (suíte completa) para garantir ausência de regressão, em especial em
  `EstimateControllerDecideLinesTest` e em qualquer teste que dependa de `SecurityConfig`;
- `./mvnw test -Dtest=ModuleStructureTest`;
- `make verify` / `./mvnw verify`.

Revisar:
- nenhuma mudança em `servicelifecycle` (controller, caso de uso, DTOs) além, possivelmente, de nenhuma —
  todo o código novo fica em `identity`;
- nenhuma authority nova adicionada ao enum `Role` nem ao mapeamento role→domain-ID;
- OpenAPI e Postman refletem exatamente o contrato descrito em `technical-spec.md`.

## Definition of Done

- [x] `CachedBodyHttpServletRequest` implementado e testado.
- [x] `EstimateGatewayHmacAuthenticationFilter` implementado e testado.
- [x] `SecurityConfig` atualizado (filtro registrado, authority `ESTIMATE_APPROVAL_GATEWAY` na regra da
      rota).
- [x] Testes de integração HTTP cobrindo sucesso HMAC, falha HMAC (ausente/inválido/expirado) e regressão
      JWT.
- [x] `README.md`, OpenAPI e Postman atualizados.
- [ ] Testes relevantes passando.
- [ ] `make verify` passando.
- [ ] Revisão de segurança concluída (ver abaixo).
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: headers `X-Estimate-Gateway-Timestamp`/`X-Estimate-Gateway-Signature`
  validados antes de qualquer acesso a dados de domínio; formato do timestamp validado (parse seguro, sem
  lançar exceção não tratada para o cliente).
- **Autenticação/autorização**: esta é a própria mudança da feature — dois caminhos de autenticação
  (JWT interno inalterado; HMAC novo para o gateway). Autorização segue `hasAnyAuthority`, sem alteração de
  princípio.
- **Exposição de dados**: nenhum dado novo exposto; resposta de falha de autenticação é o `401` genérico já
  existente, sem diferenciar causa nem confirmar/negar existência da Estimate.
- **Segredos/logs**: segredo HMAC configurado por ambiente, nunca logado; assinatura recebida e corpo bruto
  nunca logados; falha de autenticação pode ser logada em `DEBUG` sem payload.
- **SQL/persistência/migration**: nenhuma migration, nenhuma mudança de schema.
- **Erros e disclosure**: falha de autenticação sempre `401` (ver `technical-spec.md` — resolução da
  ambiguidade 401/403); nenhum stack trace nem detalhe interno exposto.
- **Dependências novas**: nenhuma biblioteca nova — `MessageDigest`/`Mac` (`javax.crypto`) já disponíveis no
  JDK.
- **Abuso**: mitigação de replay via janela de tolerância de timestamp (default 300s), risco residual
  *dentro* da janela aceito e registrado explicitamente como risco de MVP no `technical-spec.md`; sem essa
  mitigação, um payload capturado poderia ser reenviado indefinidamente. Comparação de assinatura em tempo
  constante evita timing attack.

Nenhum achado crítico/alto pendente identificado nesta etapa de planejamento; qualquer achado durante a
implementação deve ser registrado aqui antes de marcar a feature como implementada.

Achados durante a implementação:

- **Checkpoint 2 — overflow na janela de timestamp (alto, resolvido)**: `Math.abs(now - timestamp)` estoura
  para timestamps extremos e aceitaria uma assinatura válida fora de qualquer janela (replay indefinido).
  Mitigado com comparação de limites e teste `extremeTimestampDoesNotOverflowIntoTheWindow`.

## Evidências de verificação

A preencher durante a implementação (comandos executados, resultados de teste, contagens, saída de
`make verify`), seguindo o mesmo padrão de `decide-estimate-lines/implementation-plan.md`.

### Checkpoint 1 — `CachedBodyHttpServletRequest` (2026-09-26)

- `identity.CachedBodyHttpServletRequest` criado como classe package-private (só o filtro HMAC do mesmo
  pacote a usa; não entra na API pública do módulo). Além do previsto, expõe `body()` (cópia defensiva)
  para o filtro calcular o HMAC sem reabrir o stream, e `setReadListener` lança
  `UnsupportedOperationException` (leitura assíncrona não é usada por nenhum endpoint).
- `./mvnw test -Dtest=CachedBodyHttpServletRequestTest,ModuleStructureTest`:
  - `CachedBodyHttpServletRequestTest`: 5 testes, 0 falhas (duas leituras de `getInputStream()`,
    `getReader()` após `getInputStream()`, charset da requisição respeitado, corpo vazio, cópia defensiva);
  - `ModuleStructureTest`: 2 testes, 0 falhas.

### Checkpoint 2 — `EstimateGatewayHmacAuthenticationFilter` (2026-09-26)

- `identity.EstimateGatewayHmacAuthenticationFilter` criado com construtor `@Value` (segredo + tolerância)
  e construtor package-private com `Clock`, mesmo padrão de `JwtTokenIssuer`. **Ainda sem `@Component`**:
  como qualquer `Filter` anotado vira também filtro de servlet no Spring Boot, registrá-lo antes do wiring
  em `SecurityConfig` o faria rodar fora da cadeia de segurança. `@Component` e o
  `.addFilterBefore(...)` entram juntos no Checkpoint 3.
- Decisões de implementação:
  - segredo em branco rejeitado na construção (`IllegalArgumentException`) — falha no startup em vez de
    aceitar HMAC com chave vazia;
  - assinatura comparada em hex minúsculo com `MessageDigest.isEqual`; hex maiúsculo enviado pelo gateway é
    normalizado antes da comparação;
  - timestamp não numérico → não autenticado, sem exceção;
  - a assinatura é sempre calculada antes de checar a janela, para as duas falhas custarem o mesmo trabalho.
- Properties adicionadas: `app.security.estimate-gateway.hmac-secret`
  (`APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`, default só de desenvolvimento) e
  `app.security.estimate-gateway.timestamp-tolerance-seconds`
  (`APP_SECURITY_ESTIMATE_GATEWAY_TIMESTAMP_TOLERANCE_SECONDS`, default `300`) em `application.properties`;
  segredo de teste em `src/test/resources/application.properties`. `application-dev.properties` não
  sobrescreve segredos hoje, então não foi alterado.
- `./mvnw test -Dtest=EstimateGatewayHmacAuthenticationFilterTest,CachedBodyHttpServletRequestTest,ModuleStructureTest`:
  - `EstimateGatewayHmacAuthenticationFilterTest`: 16 testes, 0 falhas — assinatura válida, hex maiúsculo,
    borda da janela, headers ausentes, só um header, segredo errado, corpo adulterado, timestamp expirado,
    timestamp futuro fora da janela, timestamp `Long.MIN_VALUE`, timestamp malformado, limpeza de contexto
    pré-existente, outro path não envolvido, outro método não envolvido, corpo legível downstream, segredo
    em branco;
  - `CachedBodyHttpServletRequestTest`: 5 testes, 0 falhas;
  - `ModuleStructureTest`: 2 testes, 0 falhas.
- Achado de segurança durante a implementação (resolvido): a checagem de janela sugerida,
  `|now - timestamp| <= tolerância`, estoura `long` para timestamps próximos de `Long.MIN_VALUE`
  (`Math.abs` devolve negativo) e aceitaria a requisição. Substituída por comparação de limites
  (`now - tol <= timestamp <= now + tol`) e coberta por teste dedicado.

### Checkpoint 3 — Wiring em `SecurityConfig` (2026-09-26)

- `EstimateGatewayHmacAuthenticationFilter` anotado com `@Component` e registrado com
  `.addFilterBefore(estimateGatewayHmacAuthenticationFilter, JwtAuthenticationFilter.class)`. Assim como o
  `JwtAuthenticationFilter`, o registro automático como filtro de servlet roda depois da cadeia de
  segurança e é ignorado por `OncePerRequestFilter` (a requisição já foi marcada como filtrada).
- Regra da rota: `.requestMatchers(HttpMethod.POST, "/api/estimates/*/decisions")
  .hasAnyAuthority("CUSTOMER", "ADMIN", "ESTIMATE_APPROVAL_GATEWAY")`. Nenhuma outra regra recebeu a
  authority do gateway.
- Precedência: como o filtro HMAC roda antes do JWT, um `Authorization: Bearer` válido enviado junto com
  headers HMAC prevalece (o filtro JWT sobrescreve o contexto). Coberto por teste.
- Nova classe `EstimateControllerGatewayAuthenticationTest` (`@SpringBootTest` + `springSecurity()`), 8
  testes, 0 falhas:
  - HMAC válido sem JWT → `200`, linha `READY` (aprovação) e `REJECTED` (rejeição);
  - sem HMAC e sem JWT → `401`; HMAC com segredo errado → `401`; timestamp 301s no passado → `401`. Nos
    três casos, uma decisão posterior com JWT ADMIN ainda retorna `200`/`READY`, o que prova que
    `DecideEstimateLinesUseCase` não alterou a linha (ela continuava `PENDING`);
  - JWT `CUSTOMER` sem HMAC → `200` (regressão pontual);
  - HMAC inválido + JWT `CUSTOMER` válido → `200`;
  - headers HMAC válidos em `GET /api/estimates/{id}` → `401` (a assinatura não autentica outras rotas).
- Execuções:
  - `./mvnw test -Dtest=EstimateControllerGatewayAuthenticationTest,EstimateControllerDecideLinesTest,SecurityAuthorizationTest,EstimateGatewayHmacAuthenticationFilterTest,ModuleStructureTest`:
    8 + 8 + 15 + 16 + 2 testes, 0 falhas;
  - `./mvnw test` (suíte completa): 729 testes, 0 falhas, 0 erros, 0 skipped.

### Checkpoint 4 — Documentação e contrato (2026-09-26)

- OpenAPI (`EstimateController.decide`): descrição da operação com os dois caminhos de autenticação; headers
  opcionais `X-Estimate-Gateway-Timestamp`/`X-Estimate-Gateway-Signature` declarados como `@Parameter`
  (`in = HEADER`); respostas `401` e `403` documentadas. Não foi criado `SecurityScheme` para o HMAC: o par de
  headers só vale junto (AND), o que as anotações de `@SecurityRequirement` do springdoc não expressam sem
  virar alternativas independentes (OR) — os headers como parâmetros descrevem o contrato sem ambiguidade.
- `OpenApiContractTest.documentEstimateDecisionsGatewayAuthentication`: verifica `401`, `403` e os dois
  headers no `/v3/api-docs`.
- Postman:
  - `Estimates / Decide estimate lines without credentials (expect 401)` — `noauth`, sem headers HMAC,
    espera `401`; determinística em qualquer estado da Estimate;
  - `Isolated / Decide estimate lines via external gateway (HMAC)` — `noauth`, pre-request script com
    `CryptoJS.HmacSHA256` sobre o corpo já resolvido (`pm.variables.replaceIn`), espera `200`. Fica na pasta
    `Isolated` porque substitui o passo 10 (a linha precisa estar `PENDING`);
  - nova variável de coleção `estimateGatewaySecret`, com o mesmo valor padrão de desenvolvimento da
    aplicação;
  - nenhuma das duas requisições entrou em `E2E_STEPS` do `Makefile`, então `make e2e` não muda.
- `.env.example` e `docker-compose.yml`: `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` com o mesmo padrão de
  desenvolvimento, para o Postman funcionar sem configuração no ambiente Docker local.
- `README.md`: as duas afirmações de "todos os endpoints exigem JWT" passaram a citar a exceção; nova
  variável na tabela; passo 10 aponta a alternativa; nova seção "Decisão de orçamento pelo gateway externo
  (HMAC)" com headers, janela, roteiro Postman e exemplo `bash`/`openssl`/`curl`. O plano citava um
  "README.md da feature", que não existe no padrão do projeto; o fluxo foi documentado no `README.md` raiz,
  onde ficam as demais instruções de teste manual pelo Postman.
- Receita `openssl dgst -sha256 -hmac` do README conferida contra `crypto.createHmac('sha256')` do Node para
  o mesmo timestamp/corpo/segredo: assinaturas idênticas.
- `./mvnw test -Dtest=OpenApiContractTest,EstimateControllerDecideLinesTest,EstimateControllerGatewayAuthenticationTest`:
  18 + 8 + 8 testes, 0 falhas.
- Não executado neste checkpoint: chamada real via Postman/newman contra a aplicação em Docker (fica para a
  validação final).

## Rollback ou recuperação

Reversível via `git revert` do commit da feature — nenhuma migration, nenhum dado persistido. Em caso de
vazamento do segredo HMAC em produção, rotacionar `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` no ambiente;
nenhuma migração de dado é necessária para a rotação.

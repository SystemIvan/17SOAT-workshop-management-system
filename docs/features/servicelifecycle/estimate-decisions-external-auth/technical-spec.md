# Especificação Técnica: Autenticação externa de `POST /api/estimates/{estimateId}/decisions`

| Campo | Valor |
|---|---|
| Feature | `estimate-decisions-external-auth` |
| Status | Draft |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-23 |
| Aprovado por | — |
| Aprovado em | — |
| Especificação funcional | `./functional-spec.md` (Approved, 2026-09-23 — decisão (b) HMAC de webhook) |

## Contexto e desenho

Único módulo com código novo: **`identity`**. `servicelifecycle` não muda — nem o controller, nem
`DecideEstimateLinesUseCase`, nem o DTO de request/response. Isso é deliberado: a spec funcional já decidiu
que esta feature é só a borda de autenticação HTTP, não uma mudança de regra de negócio.

Componentes novos, todos ao lado de `JwtAuthenticationFilter`/`SecurityConfig` (pacote raiz `identity`, o
mesmo lugar onde hoje já existem as regras de autorização por path para todos os módulos):

1. `CachedBodyHttpServletRequest` — `HttpServletRequestWrapper` utilitário. Lê o corpo da requisição uma
   única vez no construtor e o guarda em `byte[]`; `getInputStream()`/`getReader()` retornam sempre um novo
   stream sobre esse array. É necessário porque `HttpServletRequest.getInputStream()` só pode ser consumido
   uma vez — sem isso, ler o corpo cru para validar a assinatura HMAC no filtro deixaria o corpo vazio
   quando o `HttpMessageConverter` (Jackson) tentasse desserializar `DecideEstimateLinesRequest` mais adiante
   na cadeia. (`org.springframework.web.util.ContentCachingRequestWrapper` **não** resolve isso sozinho: ele
   cacheia bytes conforme são lidos através dele, mas não os reexpõe em uma segunda leitura — precisa do
   wrapper próprio descrito aqui.)
2. `EstimateGatewayHmacAuthenticationFilter extends OncePerRequestFilter` — só age quando
   `HttpMethod.POST` e o path casa com `/api/estimates/*/decisions` (checagem via `AntPathMatcher`, mesmo
   estilo de padrão já usado em `SecurityConfig`); para qualquer outro request, chama `filterChain.doFilter`
   imediatamente sem envolver o corpo (evita custo de buffering em todo o resto da API).
3. Duas novas properties, seguindo o padrão já usado por `app.security.jwt.secret`:
   - `app.security.estimate-gateway.hmac-secret` (env `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`).
   - `app.security.estimate-gateway.timestamp-tolerance-seconds` (default `300`).
4. `SecurityConfig`: adiciona a authority `ESTIMATE_APPROVAL_GATEWAY` à regra já existente de
   `POST /api/estimates/*/decisions` e registra o novo filtro com
   `.addFilterBefore(estimateGatewayHmacAuthenticationFilter, JwtAuthenticationFilter.class)`.

Nenhum módulo passa a importar tipos de outro por causa desta feature. `SecurityConfig` já referencia o
path `/api/estimates/*/decisions` como string (não como tipo) desde `decide-estimate-lines` — este desenho
apenas segue o mesmo precedente, sem criar uma dependência `identity → servicelifecycle`.

## Interfaces e fluxo de dados

### Contrato HTTP inalterado

`POST /api/estimates/{estimateId}/decisions` continua recebendo exatamente o mesmo corpo
(`DecideEstimateLinesRequest`) e devolvendo a mesma resposta já documentada em `decide-estimate-lines`. Não
há novo endpoint nem novo formato de payload de negócio.

### Novo contrato de headers (caminho externo)

| Header | Formato | Descrição |
|---|---|---|
| `X-Estimate-Gateway-Timestamp` | string, epoch seconds em UTC | Momento em que o gateway assinou a requisição. |
| `X-Estimate-Gateway-Signature` | string, hex minúsculo | `HMAC-SHA256(timestamp + "." + rawBody, secret)`. |

`rawBody` é o corpo exato da requisição, bytes crus, antes de qualquer parsing — a mesma string que o
gateway serializou para montar a assinatura deve ser enviada literalmente como corpo da requisição (sem
reformatação posterior, já que qualquer diferença de espaçamento/ordem de campos quebraria a comparação).

### Fluxo de verificação

1. `EstimateGatewayHmacAuthenticationFilter` intercepta a requisição; se método+path não casarem com
   `POST /api/estimates/*/decisions`, segue a cadeia sem nenhuma outra ação.
2. Envolve a requisição em `CachedBodyHttpServletRequest` (lê o corpo uma única vez).
3. Se **ambos** os headers estiverem presentes:
   a. Recalcula a assinatura esperada com o segredo configurado.
   b. Compara com `MessageDigest.isEqual` (tempo constante, evita timing attack).
   c. Verifica se `|now - timestamp| <= timestamp-tolerance-seconds`; fora da janela é tratado como
      inválido mesmo que o hash bata (mitigação de replay, ver "Segurança e operação").
4. Se válido: popula `SecurityContextHolder` com um `UsernamePasswordAuthenticationToken` cuja única
   authority é `ESTIMATE_APPROVAL_GATEWAY` — uma `GrantedAuthority` sintética, **não** um valor do enum
   `Role` do Identity/Auth module e **não** passa pelo mapeamento role→domain-ID de AD-016 (decisão já
   registrada no functional-spec: um gateway externo não é um ator de domínio).
5. Se ausente/inválido: `SecurityContextHolder.clearContext()`, sem lançar exceção — mesmo padrão já usado
   por `JwtAuthenticationFilter` para token ausente/inválido. A cadeia continua (o `JwtAuthenticationFilter`
   seguinte ainda pode autenticar via `Authorization: Bearer`, cobrindo o fluxo interno inalterado).
6. `filterChain.doFilter` é chamado sempre com a requisição envolvida (`CachedBodyHttpServletRequest`), para
   que o `HttpMessageConverter` ainda consiga ler o corpo normalmente mais adiante.
7. `SecurityConfig.authorizeHttpRequests` passa a exigir, para esta rota,
   `hasAnyAuthority("CUSTOMER", "ADMIN", "ESTIMATE_APPROVAL_GATEWAY")` — sem autenticação válida por nenhum
   dos dois caminhos, `ApiAuthenticationEntryPoint` responde `401` como já faz hoje.
8. `EstimateController`/`DecideEstimateLinesUseCase` seguem 100% inalterados — não distinguem se a chamada
   veio autenticada por JWT ou por HMAC.

### Tradução de falhas

Nenhuma exceção de domínio nova. Falha de autenticação (header ausente, assinatura inválida, timestamp fora
da janela) resulta sempre em `401` via `ApiAuthenticationEntryPoint` já existente
(`ErrorResponse("UNAUTHORIZED", "Authentication is required")`) — resolução da ambiguidade deixada em
aberto no functional-spec ("401 ou 403, conforme o mecanismo"): como não existe conceito de "autenticado com
permissão insuficiente" neste caminho (a única authority do gateway já é exatamente a exigida pela rota),
todo cenário de falha aqui é ausência de autenticação, nunca autorização insuficiente — logo, sempre `401`,
nunca `403`. `403` continua reservado para um chamador autenticado (JWT válido) cujo role não tem permissão
para o recurso, comportamento já existente e inalterado.

## Persistência e dados de bootstrap

Nenhuma migração Flyway. Nenhum dado novo é persistido — a autenticação e a authority são derivadas em
memória por requisição e descartadas ao final dela. O segredo compartilhado é configuração de ambiente, não
dado de aplicação.

Classificação: **nenhum seed necessário**.

## Segurança e operação

- **Segredo**: `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` nunca versionado em texto plano. Um valor padrão
  de desenvolvimento (claramente marcado como "local dev only, rotacionar em qualquer ambiente real") é
  aceitável em `application.properties`/`.env.example`, seguindo exatamente o precedente já existente de
  `APP_SECURITY_JWT_SECRET`.
- **Mitigação de replay**: janela de tolerância de timestamp (default 300s). Isso não elimina replay
  *dentro* da janela — um payload capturado poderia ser reenviado por até 5 minutos. Aceito como risco de
  MVP, coerente com o caráter simulado do canal (ver `functional-spec.md`); registrado aqui explicitamente
  em vez de omitido. Se o requisito de segurança subir no futuro, a mitigação seguinte seria um nonce/`jti`
  de uso único — fora de escopo desta entrega.
- **Timing attack**: comparação de assinatura via `MessageDigest.isEqual`, nunca `String.equals`.
- **Não-enumeração**: a resposta de falha de autenticação é sempre o mesmo `401` genérico já usado para JWT
  ausente/inválido — não diferencia "header ausente" de "assinatura incorreta", nem revela se a
  Estimate/`serviceExecutionId` do payload existe (a verificação de assinatura acontece antes de qualquer
  acesso a dados de domínio).
- **Modelo de identidade intocado**: `ESTIMATE_APPROVAL_GATEWAY` não é adicionado ao enum
  `identity.auth.domain.model.Role` nem ao mapeamento role→domain-ID (AD-016 permanece exatamente como
  está); é uma `GrantedAuthority` do Spring Security que só existe no `SecurityContext` da requisição.
- **Logs**: nunca logar o segredo, a assinatura recebida ou o corpo bruto da requisição. No máximo, logar em
  nível `DEBUG` que uma tentativa de autenticação via gateway falhou (sem payload), para depuração
  operacional.
- **Rollout**: mudança puramente aditiva (`hasAnyAuthority` ganha uma opção a mais); nenhum chamador JWT
  existente muda de comportamento. Sem downtime, sem migração. Recuperação em caso de vazamento do segredo:
  rotacionar a variável de ambiente — nenhuma migração de dado é necessária.
- **Documentação a atualizar nesta mesma entrega** (exigido por `AGENTS.md` para mudança de contrato HTTP):
  - `README.md` linhas ~42 e ~159 (afirmam hoje, sem ressalva, que "todos os endpoints administrativos
    exigem JWT") — passam a documentar explicitamente o segundo caminho de autenticação e como simular uma
    chamada assinada.
  - OpenAPI/Springdoc: anotar `@ApiResponse` de `401` no endpoint (se ainda não documentado) e descrever o
    esquema de segurança alternativo.
  - Coleção Postman (`docs/api/postman/workshop-management-system.postman_collection.json`): adicionar uma
    requisição de exemplo simulando o gateway, com um pre-request script (Postman expõe `CryptoJS` no
    sandbox) que calcula `X-Estimate-Gateway-Timestamp`/`X-Estimate-Gateway-Signature` a partir do corpo e
    do segredo de desenvolvimento.
  - `README.md` do manual de teste da feature: passo a passo de como testar o novo caminho (variáveis,
    ordem de requisições, resultado esperado) — regra já existente em `AGENTS.md` para qualquer mudança na
    coleção Postman.

## Estratégia de testes

- **Unitário — `EstimateGatewayHmacAuthenticationFilterTest`**:
  - assinatura válida + timestamp dentro da janela → `SecurityContext` recebe `ESTIMATE_APPROVAL_GATEWAY`;
  - assinatura ausente → contexto permanece vazio, sem exceção;
  - assinatura presente mas incorreta → contexto permanece vazio;
  - timestamp fora da janela de tolerância (mesmo com hash correto para aquele timestamp) → contexto
    permanece vazio;
  - o corpo da requisição ainda pode ser lido integralmente **depois** do filtro (prova de que
    `CachedBodyHttpServletRequest` não quebra o parsing downstream) — este é o caso mais fácil de quebrar
    silenciosamente e por isso precisa de teste dedicado.
- **Unitário — `CachedBodyHttpServletRequestTest`**: múltiplas chamadas a `getInputStream()`/`getReader()`
  retornam o mesmo conteúdo completo.
- **Integração HTTP (`MockMvc`)**, sobre o endpoint real (sem mockar `DecideEstimateLinesUseCase` além do
  que `decide-estimate-lines` já mocka):
  - chamada com HMAC válido, sem JWT → mesmo resultado de sucesso já coberto por `decide-estimate-lines`;
  - chamada sem HMAC e sem JWT → `401`, `DecideEstimateLinesUseCase` nunca invocado (verificar via mock);
  - chamada com HMAC inválido (assinatura errada) → `401`, caso de uso não invocado;
  - chamada com timestamp expirado → `401`, caso de uso não invocado;
  - chamada com JWT válido (`CUSTOMER`/`ADMIN`), sem headers HMAC → comportamento idêntico ao já coberto por
    `decide-estimate-lines` (teste de regressão pontual, não duplicar a suíte inteira daquela feature).
- `ModuleStructureTest` deve continuar verde — nenhum import cruzado novo entre módulos.
- Cobertura: manter ≥80% no código novo (filtro + wrapper), meta do projeto.

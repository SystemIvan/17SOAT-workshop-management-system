# Especificação Técnica: Aprovação ou recusa do orçamento por link no e-mail

| Campo | Valor |
|---|---|
| Feature | `estimate-approval-link` |
| Status | Draft |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-10 |
| Aprovado por | — |
| Aprovado em | — |
| Especificação funcional | `./functional-spec.md` (Approved em 2026-10-10, RF53) |
| Decisão arquitetural | `../../../adr/ADR-008-customer-estimate-decision-link-authentication.md` (**Proposed**; esta spec não pode ser aprovada antes da ratificação do time) |

## Situação: feature pausada (2026-10-10)

Esta spec está pausada em `Draft`, pelo mesmo motivo registrado em `./functional-spec.md` ("Situação: feature
pausada"). Não deve ser aprovada nem usada para gerar `implementation-plan.md` até a feature ser retomada. Se for
retomada, revisar antes se o código citado aqui (adapter SMTP, casos de uso de decisão, `SecurityConfig`) mudou.

## Histórico de revisão

- 2026-10-10: versão inicial.
- 2026-10-10: revisão depois do `architecture-reviewer`.
  - B1: o mecanismo de autenticação do link passa a ser registrado no ADR-008 (Proposed).
  - R1: o handler de exceções é restrito e não engole falhas inesperadas.
  - R2: a regra "orçamento vencido" passa para o domínio (`Estimate`).
  - R3: o token passa a carregar o `serviceOrderId`, e `confirm` trava a OS como primeira consulta. O risco nos
    canais existentes foi registrado em `TD-005`.
  - R4: o segredo não tem valor padrão fora do perfil `dev`.
  - R5: as dependências do AD-014 e do AD-012 foram declaradas.
  - Observações: enums em `application.dto`, view model sem tipo de domínio, Bearer inválido testado, CSRF
    justificado pelo ADR-003.
  - R6 (refatoração do RF40 em checkpoint próprio) fica para o `implementation-plan.md`.

## Contexto e desenho

**Módulos afetados:**

- **`servicelifecycle`** (submódulo `estimate`): geração do link no e-mail de orçamento, endpoints das páginas, caso
  de uso de consulta e confirmação, e assinatura do link. A regra de decisão continua toda em
  `DecideEstimateLinesUseCase` (`decide-estimate-lines`).
- **`identity`** (pacote raiz): `SecurityConfig` ganha uma regra `permitAll` para o path das páginas. O path entra
  como string, sem importar tipo de `servicelifecycle`, como já acontece com `/api/estimates/*/decisions`
  (ADR-005).

Nenhuma dependência nova entre módulos. O código novo em `estimate` usa `serviceorder`, que é do mesmo módulo
`servicelifecycle`, como `ApplyExternalStatusUpdateUseCase` já faz.

**Dependências declaradas:**

- **ADR-008 (Proposed).** Esta spec implementa o mecanismo descrito ali e depende da ratificação do time.
- **AD-014 (`Team Decision Required`, escopo de outro membro do time).** O link chega pelo e-mail SMTP da RF52, que já
  está implementado. Esta feature não decide nada sobre canal de notificação. Se o AD-014 for resolvido de outra forma,
  o ponto de entrega dos links muda junto com a RF52.
- **AD-012 (Deferred, entrega de eventos).** O e-mail de mudança de status que vem depois da decisão não tem garantia
  de entrega (limitação já registrada na RF52). Por isso a página de resultado não promete o e-mail (ver Decisão de
  desenho 4).

### Decisão de desenho 1: link assinado sem estado (HMAC), sem tabela nova

O mecanismo e as alternativas estão no ADR-008. Esta seção cobre só o formato.

O token tem a forma `<payload>.<assinatura>`, com as duas partes em Base64 URL-safe sem padding:

- `payload` tem 33 bytes: os 16 bytes do `serviceOrderId`, os 16 bytes do `estimateId` e 1 byte com a decisão (`A` ou
  `R`). O `serviceOrderId` entra no token para que `confirm` possa travar a OS antes de qualquer outra leitura
  (Decisão de desenho 3).
- `assinatura` é o HMAC-SHA256 completo (32 bytes) calculado sobre os bytes do `payload`.
- O token fica com cerca de 88 caracteres, todos seguros para uso em path.

O que o token garante:

- **Não dá para forjar nem alterar.** Qualquer mudança no conteúdo invalida a assinatura. A comparação é feita em
  tempo constante (`MessageDigest.isEqual`), como no filtro do RF41.
- **Não dá para trocar Aprovar por Recusar.** A decisão faz parte do conteúdo assinado, e o POST de confirmação não
  aceita parâmetro de decisão.
- **Não dá para apontar para outro orçamento.** `estimateId` e `serviceOrderId` são assinados juntos. Se o orçamento
  carregado não pertencer ao `serviceOrderId` do token, o resultado é `INVALID`.

Validade e status não ficam no token. Eles são lidos do orçamento a cada acesso, o que atende às regras 5 e 6 da
functional-spec.

### Decisão de desenho 2: o token é tratado só na camada web

`EstimateDecisionLinkSigner` (`estimate.infrastructure.link`, `@Component`) gera e verifica o token. Ele é usado
pelo adapter SMTP, que monta os links, e pelo controller das páginas, que transforma o token em
`(serviceOrderId, estimateId, decisão)`. O caso de uso recebe só IDs e a decisão e não sabe que existe um token. Isso
evita criar uma porta só para criptografia e mantém a camada de aplicação livre de transporte (`AGENTS.md`).

### Decisão de desenho 3: caso de uso próprio que delega a decisão

`EstimateDecisionLinkUseCase` (`estimate.application.usecase`) tem duas operações:

- `preview(serviceOrderId, estimateId, decision)`, com `@Transactional(readOnly = true)`. Classifica o estado e
  devolve o resumo para a página. Nunca altera nada (regra 3).
- `confirm(serviceOrderId, estimateId, decision)`, com `@Transactional`. **A primeira consulta da transação é
  `ServiceOrderRepository.findByIdForUpdate(serviceOrderId)`.** Só depois do lock o caso de uso carrega o orçamento,
  reclassifica o estado e, se for `CONFIRMABLE`, delega a `DecideEstimateLinesUseCase` uma decisão por linha ainda
  `PENDING`, todas com a mesma decisão (regra 4).

A seleção das linhas pendentes é a mesma do RF40. Hoje ela está dentro de `ApplyExternalStatusUpdateUseCase` e passa
a ser um método estático package-private, `PendingEstimateLines.decisionsFor(Estimate, ServiceOrder,
EstimateLineDecision)`, usado pelos dois casos de uso. O comportamento do RF40 não muda e continua coberto pelos testes
existentes. Essa extração vira um checkpoint próprio no plano (R6).

**Ordem de classificação** (mesma função em `preview` e `confirm`):

1. orçamento inexistente, orçamento em `DRAFT`, ou orçamento cujo `serviceOrderId` não bate com o do token →
   `INVALID`. Um link válido nunca aponta para `DRAFT`, porque o e-mail só sai depois de `markSent()`;
2. `CLOSED` → `ALREADY_DECIDED`;
3. `EXPIRED`, ou `estimate.isPastValidity(clock.instant())` → `EXPIRED` (ver abaixo);
4. `SENT`, mas sem linha `PENDING` na OS (outro canal decidiu linha a linha e o orçamento ainda não foi fechado) →
   `ALREADY_DECIDED`;
5. qualquer outro caso → `CONFIRMABLE`.

**Validade no domínio (R2).** `Estimate` ganha o método de domínio `isPastValidity(Instant now)`. Ele devolve `true`
quando `expiresAt` não é nulo e `now` é igual ou posterior a `expiresAt`, o mesmo critério de
`findSentExpiredAtOrBefore`, usado pelo agendador (`ExpireEstimatesUseCase`). Com `expiresAt` nulo, o orçamento não
tem validade, como já mostra o e-mail ("Validade: não definida"). O cálculo de `expiresAt` não muda: continua em
`EstimateExpirationPolicy` (24 h ou 48 h, conforme a disponibilidade de estoque).

> **Divergência consciente entre canais.** O link para de valer exatamente em `expiresAt`, sem esperar o agendador
> (que roda a cada 60 s) marcar o orçamento como `EXPIRED`. Isso cumpre a regra 5 da functional-spec. O canal
> interno (JWT) e o RF40 continuam aceitando decisão até o agendador rodar, como hoje. Alinhar esses dois canais está
> fora do escopo da RF53, porque mudaria contratos aprovados. A janela de diferença é de no máximo um ciclo do
> agendador.
>
> Observação: o registro do AD-013 em `docs/Architecture-Decisions.md` ainda diz que o código usa 48 h fixas
> (`DEFAULT_EXPIRATION`). O código atual já usa `EstimateExpirationPolicy` (24 h / 48 h). O prazo extra de reposição
> continua pendente do AD-009. Esta feature só lê `expiresAt` e não depende de como ele é calculado.

**Concorrência (regra 6 e critério "duas confirmações → uma decisão").** `confirm` trava a OS com
`findByIdForUpdate` **antes de qualquer outra leitura** da transação:

- num duplo clique, a segunda transação espera o commit da primeira;
- como nenhuma leitura comum aconteceu antes do lock, o snapshot da segunda transação (MySQL `REPEATABLE READ`) só é
  fixado depois que ela obtém o lock. Por isso as execuções e o orçamento são lidos já atualizados;
- o resultado é `CLOSED`/`ALREADY_DECIDED`, sem exceção;
- a chamada seguinte de `DecideEstimateLinesUseCase` a `findByIdForUpdate` acontece na mesma transação (`REQUIRED`), e
  o lock já pertence a ela.

O caminho normal não depende de capturar `IllegalStateException`. Se capturasse, a transação seria marcada como
rollback-only e o commit falharia com `UnexpectedRollbackException`.

Os canais existentes (`DecideEstimateLinesUseCase` e RF40) fazem uma leitura comum antes do lock e podem ler as
execuções desatualizadas. Esta feature não muda esses canais. O risco está registrado em
`../../../tech-debt/TD-005-decisao-de-orcamento-le-execucoes-fora-do-lock.md`.

### Decisão de desenho 4: HTML montado no servidor, sem dependência nova

As páginas são três variações simples: confirmação, resultado e aviso (inválido, vencido, já decidido ou erro). Elas
são montadas por `EstimateDecisionPageRenderer` (`estimate.infrastructure.web`) com text blocks, e todo valor
dinâmico passa por `org.springframework.web.util.HtmlUtils.htmlEscape`. O único texto livre é `serviceName`,
cadastrado por usuário interno no catálogo, e mesmo assim ele é escapado para evitar XSS armazenado. O HTML é
semântico e básico, sem CSS externo, sem script e sem imagem (regra 11).

A página de resultado diz: "Orçamento aprovado. Você pode acompanhar o andamento da sua OS com a oficina". A
recusa tem o texto equivalente. Ela não promete um e-mail de mudança de status, porque esse e-mail depende do AD-014
e do AD-012 e não tem garantia de entrega. O cenário principal da functional-spec (passo 4) sugeria "Você receberá
um e-mail"; o texto foi suavizado aqui e o ajuste fica registrado como detalhe de redação, sem mudança de regra.

**Alternativa descartada:** Thymeleaf. Seria uma dependência nova, com revisão no OWASP dependency-check e
configuração de templates, para atender só três páginas fixas.

### Decisão de desenho 5: e-mail de orçamento com links

`SmtpCustomerEstimateNotificationAdapter.body(...)` é o ponto de extensão que a RF52 deixou para esta feature. Muda
só o trecho final do corpo:

- sai "Para aprovar ou recusar o orçamento, entre em contato com a oficina.";
- entra:

```text
A decisão vale para o orçamento inteiro (todos os serviços acima).

Aprovar o orçamento: <base-url>/estimate-decisions/<token-aprovar>
Recusar o orçamento: <base-url>/estimate-decisions/<token-recusar>

Ao abrir um dos links, você verá o resumo e precisará confirmar a decisão.
```

Não mudam: o formato em texto puro (`SimpleMailMessage`), o assunto, o gatilho (`EstimateGeneratedNotificationListener`)
e a assinatura da porta (regra 1).

`SimulatedEmailCustomerEstimateNotificationAdapter` (canal `log`) **não** ganha os links, porque o link é a credencial
da decisão e não pode ir para log (regra 10). A demo usa o canal `smtp` com Mailpit, que já é o padrão do
`docker-compose`.

As funções de formatação de valor (`format(Money)`) e de validade saem do adapter SMTP para `EstimateDisplayFormat`
(`estimate.infrastructure`, classe final e pública). O e-mail e as páginas passam a usar a mesma classe e mostram os
mesmos valores.

## Interfaces e fluxo de dados

### Endpoints (HTML, fora de `/api`)

```text
GET  /estimate-decisions/{token}   → página de confirmação ou aviso; nunca altera estado
POST /estimate-decisions/{token}   → confirma; corpo vazio (form sem campos); decisão vem só do token
```

Os dois endpoints respondem `text/html;charset=UTF-8` e por isso ficam fora de `/api` (regra 11). Todos os outros
endpoints continuam devolvendo JSON.

| Situação | `GET` | `POST` |
|---|---|---|
| Token malformado ou com assinatura inválida; orçamento inexistente, em `DRAFT` ou de outra OS (`INVALID`) | `404`, página "link inválido" | `404`, mesma página |
| `CONFIRMABLE` | `200`, página de confirmação com form `POST` para a mesma URL | `200`, página de resultado |
| `ALREADY_DECIDED` | `409`, página "já decidido", sem botão | `409`, mesma página |
| `EXPIRED` | `410`, página "prazo encerrado, fale com a oficina" | `410`, mesma página |
| Falha inesperada | `500`, página genérica "não foi possível concluir; fale com a oficina" | idem |

A página "link inválido" é sempre a mesma, com o mesmo status, para qualquer causa de `INVALID`. Assim ela não revela
se a OS ou o orçamento existem (regra 8).

**Página de confirmação.** Mostra:

- o número da OS;
- uma linha por serviço, com `serviceName` e `lineTotal()`;
- o total (`Estimate.total()`);
- a validade, no fuso `America/Sao_Paulo`;
- a decisão escolhida ("Você está aprovando/recusando este orçamento");
- o botão **Confirmar aprovação** ou **Confirmar recusa**.

Nada de `Customer` nem do veículo aparece na página, e o view model nem tem esses campos (regra 8).

**Tratamento de erros (R1).** O controller `EstimateDecisionPageController` (`estimate.infrastructure.web`) não trata
erros esperados como exceção: o caso de uso devolve o estado, e o controller escolhe página e status por ele. Os
erros inesperados ficam num `@ControllerAdvice(assignableTypes = EstimateDecisionPageController.class)` próprio, que
tem precedência sobre `ServiceLifecycleExceptionHandler` (`basePackages = servicelifecycle`, que responde JSON):

- O advice trata só `IllegalStateException` vinda de `DecideEstimateLinesUseCase`. É o caso residual de uma corrida
  que a ordem de lock não cobre, por exemplo um estoque que exige requisitos congelados. A resposta é `409` com a
  página "não foi possível concluir". O log em `WARN` traz `estimateId`, `serviceOrderId` e o stack trace. Mensagens
  dessas exceções só contêm IDs, sem dado pessoal.
- Qualquer outra exceção **não** é engolida. Ela segue para o tratamento padrão da plataforma, que loga o stack trace.
  Para o navegador, o advice devolve a página genérica com `500` e loga em `ERROR` com o stack trace.

O AGENTS.md manda erros esperados para o `GlobalExceptionHandler`. Este é o único desvio, e ele existe porque essas
rotas respondem HTML e não `ErrorResponse` JSON. O desvio fica registrado como exceção explícita e é limitado a este
controller.

### Caso de uso e DTOs

Todos ficam em `estimate.application.dto`, exceto o próprio caso de uso.

```java
public enum EstimateLinkDecision { APPROVE, REJECT }

public enum EstimateLinkState { INVALID, CONFIRMABLE, ALREADY_DECIDED, EXPIRED }

public record EstimateDecisionLinkView(
        EstimateLinkState state,
        EstimateLinkDecision decision,
        UUID serviceOrderId,          // null quando INVALID
        List<Line> lines,             // vazio quando INVALID
        BigDecimal total,             // null quando INVALID
        String currency,              // null quando INVALID
        Instant expiresAt) {
    public record Line(String serviceName, BigDecimal lineTotal, String currency) { }
}

public class EstimateDecisionLinkUseCase {
    @Transactional(readOnly = true)
    public EstimateDecisionLinkView preview(UUID serviceOrderId, UUID estimateId, EstimateLinkDecision decision);

    @Transactional
    public EstimateDecisionLinkView confirm(UUID serviceOrderId, UUID estimateId, EstimateLinkDecision decision);
}
```

O view model usa `BigDecimal` e o código da moeda em vez do `Money` de `serviceorder.domain.model`, para não expor
tipo de domínio fora da camada de aplicação. `EstimateDisplayFormat` formata tanto `Money` (no e-mail) quanto
`BigDecimal` com moeda (nas páginas).

`confirm` devolve o view model com o estado de **antes** da decisão. Quando o estado é `CONFIRMABLE`, a decisão foi
aplicada e o controller mostra a página de resultado. Nos demais estados, nada mudou e o controller mostra o aviso
correspondente.

O mapeamento `EstimateLinkDecision` → `EstimateLineDecision` é explícito no caso de uso, no mesmo padrão de
`ExternalIntendedStatus`: `APPROVE` → `APPROVED` e `REJECT` → `REJECTED`.

### Signer

```java
@Component
public class EstimateDecisionLinkSigner {
    public String sign(UUID serviceOrderId, UUID estimateId, EstimateLinkDecision decision);
    public Optional<SignedEstimateDecision> verify(String token);   // vazio para qualquer token inválido
    public record SignedEstimateDecision(UUID serviceOrderId, UUID estimateId, EstimateLinkDecision decision) { }
}
```

`verify` nunca lança exceção para entrada do usuário. Devolve `Optional.empty()` nestes casos:

- Base64 inválido;
- tamanho errado;
- byte de decisão desconhecido;
- assinatura errada;
- mais de 128 caracteres (rejeitado antes de decodificar).

### Fluxo

```text
GenerateEstimateUseCase → EstimateGenerated → EstimateGeneratedNotificationListener (async, após commit)
  → SmtpCustomerEstimateNotificationAdapter.body(): links com signer.sign(serviceOrderId, estimateId, APPROVE/REJECT)

Cliente clica → GET /estimate-decisions/{token}
  → signer.verify(token) → vazio? → 404 "link inválido"
  → useCase.preview(...) → página conforme o estado                              [nada muda]

Cliente confirma → POST /estimate-decisions/{token}
  → signer.verify(token) → vazio? → 404
  → useCase.confirm(...)  [@Transactional]
       findByIdForUpdate(serviceOrderId)  ← primeira consulta da transação
       → carrega Estimate → classifica → CONFIRMABLE?
       → PendingEstimateLines.decisionsFor(...) → DecideEstimateLinesUseCase.execute(...)
  → commit → ServiceOrderStatusChanged → e-mail de mudança de status da RF52 (sem mudança)
```

## Persistência e dados de bootstrap

Não há mudança de schema nem migration. A feature só lê `Estimate` e `ServiceOrder` e delega a escrita ao caso de uso
existente. Classificação: **nenhum seed necessário**.

**Configuração nova (R4):**

| Propriedade | Variável de ambiente | Padrão | Observação |
|---|---|---|---|
| `app.estimate-approval-link.secret` | `APP_ESTIMATE_APPROVAL_LINK_SECRET` | **sem padrão** em `application.properties` (`${APP_ESTIMATE_APPROVAL_LINK_SECRET:}`); padrão só em `application-dev.properties` | Secret no K8s. A aplicação não sobe se o valor estiver vazio ou tiver menos de 32 bytes. |
| `app.estimate-approval-link.base-url` | `APP_ESTIMATE_APPROVAL_LINK_BASE_URL` | `http://localhost:8080` | URL pública dos links. ConfigMap no K8s. A barra final é removida. |

Por que o segredo é diferente do JWT e do HMAC do RF41: as rotas do link são públicas. Um padrão versionado ativo
fora do `dev` deixaria qualquer pessoa que conheça o repositório e um par de IDs forjar decisões. Com a regra acima:

- o `docker-compose` usa `SPRING_PROFILES_ACTIVE=dev` e recebe o padrão de `application-dev.properties`, então a demo
  continua funcionando sem configuração extra;
- qualquer subida sem perfil `dev` e sem a variável falha no startup, com uma mensagem que cita só o nome da
  propriedade;
- `src/test/resources/application.properties` ganha um valor exclusivo de teste, como já acontece com os outros
  segredos;
- `.env.example` e o README orientam a definir a variável em qualquer ambiente real.

## Segurança e operação

- **Autenticação e autorização:** mecanismo do ADR-008 (Proposed).
  - `SecurityConfig` ganha duas regras, junto das rotas públicas: `.requestMatchers(HttpMethod.GET,
    "/estimate-decisions/*")` e `.requestMatchers(HttpMethod.POST, "/estimate-decisions/*")`, ambas com
    `.permitAll()`. Qualquer outro método cai em `anyRequest().authenticated()`.
  - Não há login (regra 7); o token é a credencial.
  - O filtro HMAC do RF41 não protege este path.
- **Bearer inválido no path público:** o `JwtAuthenticationFilter` pode responder `401` JSON a um
  `Authorization: Bearer` inválido antes da autorização. O plano verifica o comportamento real e, se for o caso,
  o filtro passa a ignorar `/estimate-decisions/*`, como o filtro HMAC já faz com paths fora da lista dele. Isso
  conta como mudança na cadeia de segurança e é testada.
- **CSRF:** continua desabilitado, pelo mesmo motivo do ADR-003: a cadeia é stateless, sem cookie nem sessão.
  Aqui o motivo pesa ainda mais, porque o token é a credencial e um site terceiro não consegue montar a URL sem
  conhecê-lo.
- **Força bruta:** a assinatura tem 256 bits, então adivinhar um token é inviável. Não há rate limiting e esta
  feature não o introduz. Fica registrado como `N/A` justificado na revisão de segurança do plano.
- **Abertura automática (regra 3):** `GET` nunca altera estado. O risco residual de um scanner que submete formulários
  foi aceito no ADR-008.
- **Vazamento do token pela URL:** as respostas levam os headers:
  - `Referrer-Policy: no-referrer`;
  - `Cache-Control: no-store` (padrão do Spring Security);
  - `X-Robots-Tag: noindex`;
  - `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors
    'none'`.

  As páginas não carregam nenhum recurso externo.
- **Logs (regra 10):**
  - O controller e o caso de uso logam só `serviceOrderId`, `estimateId`, decisão e estado, em `INFO`.
  - O token e a URL completa nunca vão para log.
  - O projeto não tem access log do Tomcat configurado. Se alguém ativá-lo, a URL com o token entra no log; o README
    registra esse cuidado operacional.
- **Exposição de dados (regra 8):** o view model não tem nenhum campo de dado pessoal. Os testes conferem a
  ausência de nome, e-mail, telefone e placa.
- **XSS:** todo valor dinâmico é escapado e a CSP bloqueia scripts. Há teste com `serviceName` contendo `<script>`.
- **Dependências:** nenhuma nova.
- **Canais existentes (regra 9):** `POST /api/estimates/{id}/decisions` (JWT) e
  `POST /api/service-orders/{id}/external-status-updates` (HMAC) não mudam de comportamento. A única mudança é a
  extração de `PendingEstimateLines`, feita em checkpoint próprio.
- **Rollout e rollback:**
  - A mudança é aditiva. Rollback com `git revert`; depois dele, os links já enviados respondem `404` e nada é
    decidido.
  - Trocar `APP_ESTIMATE_APPROVAL_LINK_SECRET` invalida os links pendentes. Nesse caso, o cliente decide pela
    oficina.

## Documentação

- **OpenAPI:**
  - O controller recebe `@Tag`/`@Operation` com `produces = text/html` e as respostas `200`, `404`, `409`, `410` e
    `500`.
  - A descrição explica que a rota é para o navegador do cliente e que o token vem do e-mail.
  - `OpenApiContractTest` passa a esperar os dois paths.
- **Postman:** nova pasta `Estimate approval link (RF53)`, com:
  - `Open approval link`: `GET`. Um pre-request script gera o token a partir de `{{serviceOrderId}}`,
    `{{estimateId}}` e `{{estimateApprovalLinkSecret}}`, com o mesmo CryptoJS do RF41;
  - `Confirm approval link`: `POST`;
  - `Open tampered link (expect 404)`: determinística.
- **README:** subseção sobre o fluxo pelo Mailpit e o roteiro Postman, com pré-requisitos, ordem, variáveis e
  resultados esperados (`AGENTS.md`). Também inclui a nova variável de ambiente e o cuidado com access log.
  `service-order-status-notifications/README.md` passa a usar o clique no link como forma de aprovar na demo.
- **Registro de decisões:** o ADR-008 é referenciado. Corrigir o texto desatualizado do AD-013 em
  `Architecture-Decisions.md` fica fora desta feature, porque o AD-013 é de outro escopo; a divergência só é apontada
  ao responsável.

## Estratégia de testes

- **Domínio (`EstimateTest`):** `isPastValidity` com `expiresAt` nulo, antes, exatamente igual e depois de `now`.
- **Signer (`EstimateDecisionLinkSignerTest`, unitário):**
  - assinar e verificar devolve os mesmos IDs e a mesma decisão;
  - devolve vazio quando: o byte de decisão é trocado; algum dos IDs é trocado; um caractere da assinatura é alterado;
    o Base64 é inválido; o token é vazio, tem mais de 128 caracteres ou não tem `.`;
  - um token assinado com outra chave devolve vazio;
  - segredo vazio ou com menos de 32 bytes falha na construção.
- **Configuração:** o contexto não sobe sem `app.estimate-approval-link.secret` fora do perfil `dev`, com mensagem que
  não contém valor.
- **Caso de uso (`EstimateDecisionLinkUseCaseTest`, unitário, `Clock` fixo):**
  - cobre cada estado da classificação: OS do token diferente da do orçamento (`INVALID`), `SENT` vencido
    (`EXPIRED`), `expiresAt` nulo (`CONFIRMABLE`);
  - `preview` nunca chama `DecideEstimateLinesUseCase`;
  - em `confirm`, `findByIdForUpdate` é a primeira chamada a repositório (`InOrder` do Mockito, nenhuma interação
    anterior);
  - `confirm` em `CONFIRMABLE` delega uma decisão por linha `PENDING`;
  - `confirm` nos outros estados não delega.
- **`PendingEstimateLines`:** teste unitário próprio. `ApplyExternalStatusUpdateUseCaseTest` continua verde sem
  mudança de cenário.
- **Renderer (`EstimateDecisionPageRendererTest`):**
  - escapa HTML em `serviceName`;
  - a página de confirmação traz form `POST`, serviços, total e validade;
  - as páginas de aviso não têm botão;
  - a página de resultado não promete e-mail.
- **Adapter SMTP (`SmtpCustomerEstimateNotificationAdapterTest`, atualizado):**
  - o e-mail traz os links com a `base-url` e a frase sobre o orçamento inteiro, e não traz mais "entre em contato";
  - os tokens verificam como `APPROVE`/`REJECT` para aquela OS e aquele orçamento;
  - o log `INFO` não contém o token (`ListAppender`).
- **HTTP (`EstimateDecisionPageControllerTest`, `@SpringBootTest` + `springSecurity()`, cadeia real, fluxo real até um
  orçamento `SENT`):**
  - `GET` válido → `200` `text/html` com os headers de segurança; a OS continua `AWAITING_APPROVAL` e as linhas
    `PENDING`;
  - `POST` de aprovação → `200`; execuções autorizadas, orçamento `CLOSED` e OS em execução;
  - `POST` de recusa → `200`; execuções `REJECTED`;
  - segundo `POST` → `409` "já decidido", sem nova mudança;
  - decisão anterior pelo canal interno (JWT) → `409` no `GET` e no `POST`;
  - `EXPIRED`, ou `SENT` com `expiresAt` vencido (`Clock` de teste) → `410`; nada muda;
  - token adulterado (decisão, `estimateId` ou `serviceOrderId` trocados) e token inventado → `404`, com o mesmo
    corpo;
  - sem autenticação → funciona; JWT válido de qualquer papel → mesmo resultado; **Bearer inválido → página HTML, não
    `401` JSON**;
  - a página não contém nome, e-mail, telefone nem placa do cliente de teste;
  - `IllegalStateException` forçada na delegação → página HTML com `409`, não `ErrorResponse` JSON.
- **Concorrência:** teste de integração com duas threads chamando `confirm` para o mesmo orçamento. Exatamente uma
  decisão é aplicada; a outra recebe `ALREADY_DECIDED`, sem `UnexpectedRollbackException`. Ele roda no H2, que usa
  `READ COMMITTED` e não reproduz o snapshot do `REPEATABLE READ` do MySQL. Por isso a garantia vem de duas
  coisas juntas: a ordem "lock primeiro" (verificada com `InOrder` no teste unitário) e a semântica do InnoDB descrita
  na Decisão de desenho 3. Um teste contra MySQL real exigiria Testcontainers, que é dependência nova. Isso fica
  registrado como recomendação em `TD-005` e não entra nesta feature. Além disso, a validação manual com o
  `docker-compose` inclui um duplo clique rápido.
- **Módulo (`@ApplicationModuleTest`):** do orçamento gerado até a confirmação pelo link, `CustomerNotificationPort`
  é chamado uma vez com `EXECUCAO`.
- **Regressão:** `EstimateControllerDecideLinesTest`, `ExternalStatusUpdateControllerTest`,
  `EstimateControllerGatewayAuthenticationTest` e `SecurityAuthorizationTest` continuam verdes.
  `SecurityAuthorizationTest` ganha os casos "`/estimate-decisions/*` é público só para `GET`/`POST`" e "nenhum outro
  path foi aberto".
- **Contrato:** `OpenApiContractTest` atualizado.
- **Fronteiras:** `ModuleStructureTest` verde, sem `@NamedInterface` nova.
- **Cobertura:** pelo menos 80% no código novo e alterado (`make coverage`). `make verify` obrigatório.
- **Validação manual (demo):**
  1. `docker compose up` e levar uma OS até o orçamento.
  2. No Mailpit (`http://localhost:8025`), clicar em **Aprovar** e conferir o resumo, sem nenhuma mudança na OS.
  3. Confirmar e ver a página de resultado.
  4. Conferir o e-mail de "Execução".
  5. Clicar de novo no link e ver "já decidido".
  6. Repetir com outra OS, com duplo clique rápido em **Confirmar**.

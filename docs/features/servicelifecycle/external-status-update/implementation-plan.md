# Plano de Implementação: Atualização de status da OS via canal externo (e-mail)

| Campo | Valor |
|---|---|
| Feature | `external-status-update` |
| Status | In Progress |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-26 |
| Especificação técnica | `./technical-spec.md` (Approved, 2026-09-26) |

## Objetivo

Expor `POST /api/service-orders/{serviceOrderId}/external-status-updates`, autenticado só pelo HMAC de RF41,
que traduz a intenção `APPROVED`/`REJECTED` do Customer em decisões sobre todas as linhas `PENDING` do orçamento
`SENT` da OS e delega a `DecideEstimateLinesUseCase`, sem criar regra de negócio nova nem alterar contratos
existentes.

## Checkpoint 1 — Adendo 2 na technical-spec de RF41

- Adicionar "Adendo 2 — Segundo path protegido (RF40)" ao fim de
  `docs/features/servicelifecycle/estimate-decisions-external-auth/technical-spec.md`:
  - o filtro passa a proteger `POST /api/service-orders/*/external-status-updates` além de
    `POST /api/estimates/*/decisions`, com comportamento idêntico (leitura condicional, limite de corpo,
    janela, authority `ESTIMATE_APPROVAL_GATEWAY`);
  - a regra do novo path em `SecurityConfig` exige **só** `ESTIMATE_APPROVAL_GATEWAY`, declarada antes de
    `/api/service-orders/**`;
  - referência a `external-status-update/technical-spec.md`.
- **Gate**: o adendo fica com aprovação pendente até confirmação explícita do responsável. O Checkpoint 4
  (código do filtro e da `SecurityConfig`) não começa antes disso. Os Checkpoints 2 e 3 não dependem do adendo
  e podem avançar.

## Checkpoint 2 — Persistência

- Migração `src/main/resources/db/migration/V<UTC yyyyMMddHHmmss>__add_service_order_status_index_to_estimates.sql`:
  `CREATE INDEX idx_estimates_service_order_id_status ON estimates (service_order_id, status);`
- `EstimateJpaRepository.findByServiceOrderIdAndStatus(UUID serviceOrderId, EstimateStatus status)`.
- `EstimateRepository.findByServiceOrderIdAndStatus(...)` no domínio e implementação em
  `EstimateRepositoryImpl` usando `EstimatePersistenceMapper`.
- Conferir se há implementações de teste/fakes de `EstimateRepository` que precisam do método novo.

Testes:
- teste de integração do repositório: com Estimates de duas OS e status diferentes, retorna só as da OS e do
  status pedidos; lista vazia quando não há;
- subida do contexto com Flyway contra o banco de teste (cobre a migração).

## Checkpoint 3 — DTOs e caso de uso

- `estimate.application.dto.ExternalIntendedStatus` (enum `APPROVED`, `REJECTED`).
- `estimate.application.dto.ExternalStatusUpdateRequest(@NotNull ExternalIntendedStatus intendedStatus)`.
- `estimate.application.usecase.ApplyExternalStatusUpdateUseCase`, `@Transactional`, conforme "Fluxo do caso
  de uso" do `technical-spec.md`:
  1. OS inexistente → `NoSuchElementException`;
  2. zero ou mais de uma Estimate `SENT` → `IllegalStateException`;
  3. nenhuma linha cuja `ServiceExecution` esteja `PENDING` → `IllegalStateException`;
  4. monta `DecideEstimateLinesRequest` só com as linhas pendentes e delega a
     `DecideEstimateLinesUseCase.execute(estimateId, request)`.
- Mensagens de exceção só com IDs.

Testes (`ApplyExternalStatusUpdateUseCaseTest`, mocks de `ServiceOrderRepository`, `EstimateRepository` e
`DecideEstimateLinesUseCase`):
- `APPROVED` e `REJECTED` geram uma decisão por linha pendente, com a decisão mapeada, para a Estimate certa;
- linhas já decididas não entram no request;
- OS inexistente, nenhuma Estimate `SENT`, duas `SENT`, nenhuma linha `PENDING` → exceção correspondente e o caso
  de uso delegado nunca é chamado;
- o retorno é o `ServiceOrderResponse` do caso de uso delegado.

## Checkpoint 4 — Controller e segurança

Pré-requisito: Adendo 2 do Checkpoint 1 aprovado.

- `estimate.infrastructure.web.ExternalStatusUpdateController`:
  `POST /api/service-orders/{serviceOrderId}/external-status-updates`, `@Valid @RequestBody`, retorna `200` com
  `ServiceOrderResponse`.
- `EstimateGatewayHmacAuthenticationFilter`: trocar o path único por uma lista com os dois padrões; nenhuma
  outra mudança de comportamento.
- `SecurityConfig`: nova regra
  `.requestMatchers(HttpMethod.POST, "/api/service-orders/*/external-status-updates").hasAuthority("ESTIMATE_APPROVAL_GATEWAY")`
  antes de `.requestMatchers("/api/service-orders/**")`.

Testes:
- `EstimateGatewayHmacAuthenticationFilterTest`: o novo path é autenticado com assinatura válida e recusa
  assinatura inválida; `POST /api/service-orders` (outro path) continua sem leitura de corpo.
- `ExternalStatusUpdateControllerTest` (`@SpringBootTest` + `springSecurity()`, fluxo real até a Estimate
  `SENT`):
  - HMAC + `APPROVED` → `200`, execução `READY`; HMAC + `REJECTED` → `200`, execução `REJECTED`;
  - reenvio após aplicar → `409 INVALID_STATE_TRANSITION`;
  - OS em diagnóstico (sem Estimate) → `409`; orçamento já decidido pelo canal interno → `409`;
  - OS inexistente → `404 NOT_FOUND`; `intendedStatus` ausente ou inválido → `400 VALIDATION_ERROR`;
  - sem HMAC → `401`; assinatura inválida → `401`; JWT `ADMIN` sem HMAC → `403`; nos três casos a linha
    continua `PENDING` (decisão posterior pelo canal interno com JWT `ADMIN` retorna `200`).
- Regressão: `EstimateControllerDecideLinesTest`, `EstimateControllerGatewayAuthenticationTest`,
  `SecurityAuthorizationTest`, `ModuleStructureTest`.

## Checkpoint 5 — Documentação e contrato

- OpenAPI no controller: `@Operation` (descrição do caminho HMAC e dos valores aceitos), os dois headers como
  `@Parameter(in = HEADER)`, `@ApiResponses` `200`/`400`/`401`/`403`/`404`/`409`.
- `OpenApiContractTest`: path presente, headers declarados, respostas `401`/`403`/`409`.
- Postman (`docs/api/postman/workshop-management-system.postman_collection.json`):
  - `Isolated / Update service order status via external channel (HMAC)` — `noauth`, mesmo pre-request script
    de assinatura de RF41, corpo `{"intendedStatus": "APPROVED"}`, espera `200`;
  - `Service Lifecycle / Update service order status via external channel without credentials (expect 401)` —
    `noauth`, sem headers, espera `401`;
  - nenhuma das duas entra em `E2E_STEPS` do `Makefile`.
- `README.md`: subseção junto à de RF41 com endpoint, payload, valores aceitos, respostas, roteiro Postman
  (alternativa ao passo 10) e exemplo `bash`/`openssl`/`curl`.

## Checkpoint 6 — Validação final

Executar:
- testes novos e de regressão listados acima;
- `./mvnw test -Dtest=ModuleStructureTest`;
- `make verify` (`./mvnw clean verify`), incluindo `jacoco:check`.

Revisar:
- nenhuma regra de decisão duplicada fora de `DecideEstimateLinesUseCase`;
- nenhum import novo entre módulos; `identity` referencia o path só como string;
- nenhum contrato existente alterado (`/decisions` com JWT e HMAC intacto);
- OpenAPI e Postman batem com o contrato do `technical-spec.md`.

## Definition of Done

- [x] Adendo 2 da technical-spec de RF41 escrito e aprovado pelo responsável (Santiago Silvestre, 2026-09-26).
- [x] Migração do índice e consulta `findByServiceOrderIdAndStatus` implementadas e testadas.
- [x] `ApplyExternalStatusUpdateUseCase` e DTOs implementados e testados.
- [x] Controller, filtro HMAC e `SecurityConfig` atualizados, com testes HTTP pela cadeia real.
- [x] OpenAPI, Postman e `README.md` atualizados.
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança concluída (ver abaixo) — nenhum achado crítico, alto ou médio.
- [ ] Chamada real via Postman contra a aplicação em Docker — pendente, a cargo do responsável.

## Revisão de segurança

Concluída no Checkpoint 6 (2026-09-26). Nenhum achado crítico, alto ou médio.

- **Validação de entrada**: confirmada — DTO com um único enum `@NotNull`; `EXECUCAO` e `{}` → `400
  VALIDATION_ERROR` (testes HTTP), sem alterar estado.
- **Mass assignment**: confirmado — o chamador não escolhe Estimate, linhas nem nenhum outro campo; o caso de uso
  deriva tudo da OS.
- **Autenticação/autorização**: confirmada — só `ESTIMATE_APPROVAL_GATEWAY`; a regra vem antes de
  `/api/service-orders/**` e JWT `ADMIN` sem HMAC → `403` (teste); sem assinatura ou com segredo errado → `401`
  (testes); `POST /api/service-orders/{id}/estimates` com headers HMAC não é autenticado pelo filtro (teste).
  Nenhum import de `servicelifecycle` em `identity` (os paths são strings).
- **Exposição de dados**: resposta igual à de `/decisions` para o mesmo chamador; falha de autenticação é `401`
  antes de qualquer acesso a dados, sem revelar a existência da OS.
- **Segredos/logs**: nenhum logger nem chamada de log adicionados na feature (conferido no diff).
- **SQL/persistência/migration**: a migração só cria um índice; consulta derivada pelo Spring Data, sem SQL
  manual nem concatenação.
- **Erros e disclosure**: só códigos estáveis existentes (`NOT_FOUND`, `INVALID_STATE_TRANSITION`,
  `VALIDATION_ERROR`, `UNAUTHORIZED`); mensagens de exceção só com IDs.
- **Dependências novas**: nenhuma.
- **Abuso**: reenvio dentro da janela → `409` sem efeito duplicado (teste); o limite de 64 KiB e a leitura
  condicional do corpo (RF41, Adendo 1) valem para o novo path, pois o filtro trata os dois paths igual.
  Concorrência entre este canal e o interno termina em `409` sem aplicação parcial, porque
  `DecideEstimateLinesUseCase` revalida as linhas sob lock.

## Evidências de verificação

A preencher checkpoint a checkpoint (comandos, resultados, contagens de testes, cobertura).

### Checkpoint 1 — Adendo 2 em RF41 (2026-09-26)

- "Adendo 2 — Segundo path protegido pelo filtro HMAC (RF40)" adicionado a
  `estimate-decisions-external-auth/technical-spec.md` e aprovado por Santiago Silvestre em 2026-09-26.

### Checkpoint 2 — Persistência (2026-09-26)

- Migração `V20260926043630__add_service_order_status_index_to_estimates.sql`: índice
  `idx_estimates_service_order_id_status` em `estimates (service_order_id, status)`.
- `EstimateJpaRepository.findByServiceOrderIdAndStatus` (consulta derivada) e
  `EstimateRepositoryImpl.findByServiceOrderIdAndStatus`, mapeando para o domínio.
- `EstimateRepository.findByServiceOrderIdAndStatus` declarado como método `default` que lança
  `UnsupportedOperationException`: os três fakes em memória existentes (`DecideEstimateLinesUseCaseTest`,
  `ExpireEstimatesUseCaseTest`, `GenerateEstimateUseCaseTest`) não usam a consulta e ficam inalterados. É o mesmo
  padrão já usado por `ServiceOrderRepository.search`.
- `EstimateRepositoryIntegrationTest` (`@SpringBootTest`, H2 em modo MySQL com Flyway habilitado — a subida do
  contexto aplica a migração nova): 3 testes, 0 falhas — só as Estimates da OS e do status pedidos; as duas
  quando a OS tem duas `SENT`; lista vazia sem correspondência. O teste roda em `@Transactional`: as linhas
  da Estimate são carregadas sob demanda e, neste projeto, quem abre a transação é o caso de uso, nunca o
  adaptador de repositório (sem transação, o mapeamento falha com `LazyInitializationException`). O
  `ApplyExternalStatusUpdateUseCase` do Checkpoint 3 é `@Transactional`, como o spec prevê.
- Regressão: `EstimateStatusMigrationTest` (1), `DecideEstimateLinesUseCaseTest` (12),
  `ExpireEstimatesUseCaseTest` (2), `GenerateEstimateUseCaseTest` (6) e `ModuleStructureTest` (2), 0 falhas.

### Checkpoint 3 — DTOs e caso de uso (2026-09-26)

- `ExternalIntendedStatus` (enum `APPROVED`/`REJECTED`) e `ExternalStatusUpdateRequest(@NotNull intendedStatus)`
  em `estimate.application.dto`.
- `ApplyExternalStatusUpdateUseCase` (`@Transactional`): OS inexistente → `NoSuchElementException`; zero ou
  duas+ Estimates `SENT` → `IllegalStateException`; seleciona as linhas da Estimate cujas execuções estão
  `PENDING` (nenhuma → `IllegalStateException`); mapeia a intenção com `switch` exaustivo e delega a
  `DecideEstimateLinesUseCase.execute(estimateId, request)`. Mensagens só com IDs.
- `ApplyExternalStatusUpdateUseCaseTest` (mocks dos dois repositórios e do caso de uso delegado; OS e Estimate
  reais): 7 testes, 0 falhas — `APPROVED` com duas linhas pendentes; `REJECTED`; linha já decidida fora do
  request; OS inexistente (sem tocar repositório de Estimate nem o caso de uso delegado); nenhuma `SENT`; duas
  `SENT`; `SENT` sem linha pendente (nos três últimos, o caso de uso delegado nunca é chamado).
- `ModuleStructureTest`: 2 testes, 0 falhas.
- Estilo: a única linha acima de 120 caracteres é o import do record aninhado
  `DecideEstimateLinesRequest.LineDecisionRequest`, idêntico ao de `DecideEstimateLinesUseCase` (imports não
  podem ser quebrados).

### Checkpoint 4 — Controller e segurança (2026-09-26)

- `ExternalStatusUpdateController`: `POST /api/service-orders/{serviceOrderId}/external-status-updates`,
  `@Valid @RequestBody`, `200` com `ServiceOrderResponse`. (Anotações OpenAPI ficam para o Checkpoint 5.)
- `EstimateGatewayHmacAuthenticationFilter`: `PROTECTED_PATH` virou `PROTECTED_PATHS` com os dois padrões;
  nenhuma outra mudança de comportamento. Javadoc atualizado.
- `SecurityConfig`: regra `POST /api/service-orders/*/external-status-updates` →
  `hasAuthority("ESTIMATE_APPROVAL_GATEWAY")`, antes de `/api/service-orders/**`. O comentário da regra de RF41
  ("reaches this single route and nothing else") foi corrigido, pois o gateway agora alcança as duas rotas.
- `ExternalStatusUpdateControllerTest` (`@SpringBootTest` + `springSecurity()`, fluxo real até a Estimate
  `SENT`): 11 testes, 0 falhas —
  - HMAC + `APPROVED` → `200`, execução `READY`; HMAC + `REJECTED` → `200`, execução `REJECTED`;
  - reenvio após aplicar → `409 INVALID_STATE_TRANSITION`; OS em diagnóstico → `409`; orçamento já decidido
    pelo canal interno → `409`;
  - OS inexistente → `404 NOT_FOUND`; `intendedStatus` `EXECUCAO` → `400 VALIDATION_ERROR` (linha continua
    `PENDING`); corpo `{}` → `400`;
  - sem assinatura → `401`; segredo errado → `401`; JWT `ADMIN` sem HMAC → `403`; nos três, uma decisão
    posterior pelo canal interno retorna `200`/`READY`, provando que a linha continuava `PENDING`.
- `EstimateGatewayHmacAuthenticationFilterTest`: 24 testes, 0 falhas (3 novos — novo path autenticado com
  assinatura válida; novo path com assinatura inválida fica não autenticado; `POST
  /api/service-orders/{id}/estimates` com headers HMAC não é envolvido nem autenticado).
- Regressão: `EstimateControllerDecideLinesTest` (8), `EstimateControllerGatewayAuthenticationTest` (9),
  `SecurityAuthorizationTest` (15), `ModuleStructureTest` (2), 0 falhas.
- Estilo: a linha acima de 120 caracteres no controller é o import de `ApplyExternalStatusUpdateUseCase`
  (imports não podem ser quebrados).

### Checkpoint 5 — Documentação e contrato (2026-09-26)

- OpenAPI em `ExternalStatusUpdateController`: `@Tag("Service Orders")`, `@Operation` (uso exclusivo do
  gateway, valores aceitos, regra das linhas `PENDING`, formato da assinatura, janela e limite de corpo), os dois
  headers HMAC como `@Parameter(in = HEADER, required = true)` e `@ApiResponses` `200`/`400`/`401`/`403`/`404`/
  `409`. Aqui os headers são obrigatórios (em `/decisions` são opcionais, porque lá o JWT também vale).
- `OpenApiContractTest.documentExternalStatusUpdateEndpoint`: path, respostas `200`/`401`/`403`/`409`, os dois
  headers e o enum de `intendedStatus` no schema `ExternalStatusUpdateRequest`.
- Postman:
  - `Isolated / Update service order status via external channel (HMAC)` — `noauth`, pre-request script de
    assinatura igual ao de RF41, corpo `{"intendedStatus": "APPROVED"}`, testes: `200`, OS correta, nenhuma
    execução `PENDING`;
  - `Estimates / Update service order status via external channel without credentials (expect 401)` — `noauth`,
    sem headers, espera `401`. **Divergência do plano**: ficou na pasta `Estimates`, ao lado da requisição
    equivalente de RF41, e não em `Service Lifecycle` (as duas checagens negativas do gateway ficam juntas);
  - nenhuma das duas entrou em `E2E_STEPS`; JSON validado com `node` (`JSON.parse`).
- `README.md`: os dois parágrafos sobre "todos os endpoints exigem JWT" citam os dois endpoints do gateway; passo
  10 aponta a alternativa de RF40; nova seção "Atualização de status da OS via canal externo (HMAC)" com payload,
  valores aceitos, tabela de respostas, roteiro Postman e exemplo `bash`/`openssl`/`curl` autocontido. Também
  reflui uma linha pré-existente de 170 caracteres no parágrafo editado.
- `./mvnw test -Dtest=OpenApiContractTest,ExternalStatusUpdateControllerTest`: 19 + 11 testes, 0 falhas.

### Checkpoint 6 — Validação final (2026-09-26)

- Primeira execução de `./mvnw clean verify`: `BUILD FAILURE` com 763 testes e 1 erro em
  `ExternalSupplierHttpAdapterTest.translatesAcceptanceAndSendsOnlyTheSupplierContract` (`stockprocurement`,
  não tocado por esta feature): `SocketTimeoutException: Read timed out` contra o WireMock local, com read timeout
  configurado no teste em 200 ms. Isolado, o teste passou (3/3). É instabilidade de tempo pré-existente sob carga
  da suíte completa, não regressão desta feature; não foi alterado (fora de escopo) e fica registrado aqui.
- Segunda execução de `./mvnw clean verify`: `BUILD SUCCESS`; 763 testes, 0 falhas, 0 erros, 0 skipped;
  "All coverage checks have been met".
- Cobertura (JaCoCo, linhas): projeto 93,98% (instruções 93,12%, branches 75,71%);
  `ApplyExternalStatusUpdateUseCase` 31/31 (branches 10/10); `ExternalStatusUpdateController` 4/4;
  `EstimateRepositoryImpl` 18/18; `SecurityConfig` 39/39; `EstimateGatewayHmacAuthenticationFilter` 58/60 (não
  coberto: `catch` de `GeneralSecurityException`, inalcançável).
- Revisão de escopo (diff de `src/main` desde `720986c`): 10 arquivos de produção, todos previstos no spec.
  - nenhuma regra de decisão fora de `DecideEstimateLinesUseCase` (`ApplyExternalStatusUpdateUseCase` não chama
    `authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`/`close`/reserva de estoque);
  - nenhum import de `servicelifecycle` em `identity`; `ModuleStructureTest` verde;
  - `EstimateController`, `DecideEstimateLinesUseCase` e `DecideEstimateLinesRequest` sem nenhuma alteração;
    `/decisions` com JWT e HMAC coberto pelos testes de regressão;
  - OpenAPI e Postman conferidos contra o contrato do `technical-spec.md` (path, `intendedStatus`, headers,
    códigos).
- Pendente: chamada real via Postman contra a aplicação em Docker (a cargo do responsável). Por isso o plano
  continua `In Progress`.

## Rollback ou recuperação

Reversível por `git revert` dos commits da feature. A migração só cria um índice: pode permanecer após o revert
(não quebra `ddl-auto=validate` nem o código anterior) ou ser removida por uma nova migração, se desejado —
nunca editando a migração já aplicada. Nenhum dado é criado ou alterado pela feature.

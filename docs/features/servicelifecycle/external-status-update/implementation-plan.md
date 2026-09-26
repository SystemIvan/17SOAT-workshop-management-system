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

- [ ] Adendo 2 da technical-spec de RF41 escrito e aprovado pelo responsável.
- [ ] Migração do índice e consulta `findByServiceOrderIdAndStatus` implementadas e testadas.
- [ ] `ApplyExternalStatusUpdateUseCase` e DTOs implementados e testados.
- [ ] Controller, filtro HMAC e `SecurityConfig` atualizados, com testes HTTP pela cadeia real.
- [ ] OpenAPI, Postman e `README.md` atualizados.
- [ ] Testes relevantes passando.
- [ ] `make verify` passando.
- [ ] Revisão de segurança concluída (ver abaixo).
- [ ] Chamada real via Postman contra a aplicação em Docker.

## Revisão de segurança

A preencher no Checkpoint 6. Itens previstos, conforme `technical-spec.md`:

- **Validação de entrada**: DTO com um único enum `@NotNull`; valores fora de `APPROVED`/`REJECTED` → `400`.
- **Mass assignment**: o chamador não escolhe Estimate, linhas nem nenhum outro campo.
- **Autenticação/autorização**: só `ESTIMATE_APPROVAL_GATEWAY`; ordem da regra antes de `/api/service-orders/**`
  verificada por teste (JWT `ADMIN` → `403`).
- **Exposição de dados**: resposta igual à de `/decisions` para o mesmo chamador; falha de autenticação não
  revela a existência da OS.
- **Segredos/logs**: nenhum log novo com payload, assinatura ou dados pessoais.
- **SQL/persistência/migration**: migração só cria índice; consulta derivada pelo Spring Data, sem SQL manual.
- **Erros e disclosure**: só códigos estáveis existentes; mensagens só com IDs.
- **Dependências novas**: nenhuma.
- **Abuso**: replay dentro da janela resulta em `409` sem efeito duplicado; limite de corpo de RF41 vale para o
  novo path.

## Evidências de verificação

A preencher checkpoint a checkpoint (comandos, resultados, contagens de testes, cobertura).

## Rollback ou recuperação

Reversível por `git revert` dos commits da feature. A migração só cria um índice: pode permanecer após o revert
(não quebra `ddl-auto=validate` nem o código anterior) ou ser removida por uma nova migração, se desejado —
nunca editando a migração já aplicada. Nenhum dado é criado ou alterado pela feature.

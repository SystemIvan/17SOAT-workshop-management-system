# Plano de Implementação: Status de Ciclo de Vida da Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-lifecycle-status` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-23 |
| Especificação técnica | `./technical-spec.md` |

## Objetivo

Adicionar `EstimateStatus` (`DRAFT`, `SENT`, `CLOSED`, `EXPIRED`) ao agregado `Estimate` e conectar as duas
transições hoje determináveis (`DRAFT → SENT` na geração; `SENT → CLOSED` quando a última linha `PENDING` é
decidida), reaproveitando os dois use cases já existentes (`GenerateEstimateUseCase`,
`DecideEstimateLinesUseCase`). Nenhum use case, endpoint ou tabela nova é criado; `EXPIRED` fica apenas como
valor de enum, sem gatilho (bloqueado por AD-013).

## Checkpoint 1 — Domínio

Alterar:
- Novo `EstimateStatus` (enum, `estimate/domain/model`): `DRAFT`, `SENT`, `CLOSED`, `EXPIRED`.
- `Estimate` (`estimate/domain/model/Estimate.java`):
  - novo campo `status` (`EstimateStatus`);
  - `create(...)` passa a construir com `status = DRAFT`;
  - `reconstitute(...)` passa a receber `status` como parâmetro;
  - novo método `markSent()` (`DRAFT → SENT`; lança `IllegalStateException` fora de `DRAFT`);
  - novo método `close()` (`SENT → CLOSED`; lança `IllegalStateException` fora de `SENT`);
  - novo getter `status()`.

Testes (`EstimateTest`):
- `create(...)` retorna Estimate com `status() == DRAFT`;
- `markSent()` a partir de `DRAFT` resulta em `SENT`;
- `markSent()` a partir de `SENT`/`CLOSED` lança `IllegalStateException`;
- `close()` a partir de `SENT` resulta em `CLOSED`;
- `close()` a partir de `DRAFT`/`CLOSED` lança `IllegalStateException`;
- `reconstitute(...)` preserva o `status` recebido (`SENT` e `CLOSED`).

## Checkpoint 2 — Persistência

Criar:
- Migração Flyway `V<timestamp>__add_status_to_estimates.sql`:
  ```sql
  ALTER TABLE estimates
      ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENT';
  ```

Alterar:
- `EstimateJpaEntity`: novo campo `status` (`@Enumerated(EnumType.STRING)`, `@Column(name = "status",
  nullable = false)`), construtor e getter.
- `EstimatePersistenceMapper`: mapear `status` domínio ⇄ entidade nos dois sentidos (`toDomain`/
  `toEntity` ou equivalentes já existentes no mapper).

Nenhuma mudança em `EstimateRepositoryImpl` além de continuar delegando ao mapper. Classificação de dado:
nenhum seed necessário (mudança estrutural de schema).

Testes:
- teste de mapeamento (`EstimatePersistenceMapper`, se já houver suíte própria; caso contrário, cobrir via
  teste de repositório) para os quatro valores de `EstimateStatus`, ida e volta;
- teste de startup/migração (suíte já existente que valida Flyway + Hibernate `validate` contra H2 em modo
  MySQL) confirmando que a nova coluna existe e que uma linha inserida antes da migração recebe `SENT` pelo
  `DEFAULT`.

## Checkpoint 3 — Aplicação

Alterar `GenerateEstimateUseCase.execute`:
- inserir `estimate.markSent()` entre `Estimate.create(...)` e `estimateRepository.save(estimate)`.

Alterar `DecideEstimateLinesUseCase.execute`:
- logo após carregar `Estimate` (antes das validações de duplicidade/pertencimento já existentes),
  validar `estimate.status() == EstimateStatus.SENT`; caso contrário, `IllegalStateException` sem
  aplicar nenhuma decisão da chamada;
- depois de aplicar as decisões da requisição, verificar se todas as `ServiceExecution` referenciadas
  pelas linhas da Estimate (via `executionsById`, já carregado a partir do `ServiceOrder`) deixaram de
  estar `PENDING`; em caso positivo, chamar `estimate.close()` e incluir `estimateRepository.save(estimate)`
  no fluxo (hoje o use case não salva a Estimate porque ela nunca muda).

Testes (`GenerateEstimateUseCaseTest`):
- Estimate gerada é persistida com `status = SENT` (não `DRAFT`).

Testes (`DecideEstimateLinesUseCaseTest`):
- decidir uma linha de uma Estimate `SENT` com outras linhas ainda `PENDING` mantém `status = SENT` (sem
  chamar `estimateRepository.save` para a Estimate, ou chamando com `SENT` inalterado — confirmar conforme
  implementação);
- decidir a última linha `PENDING` fecha a Estimate (`status = CLOSED`) na mesma transação;
- decidir qualquer linha de uma Estimate já `CLOSED` lança `IllegalStateException` e não aplica nenhuma
  decisão da chamada (nem `ServiceExecution`, nem reserva de estoque) — espelhar
  `rejectsWhenServiceExecutionIsNotPendingAndAppliesNoDecision`.

## Checkpoint 4 — API e documentação

Alterar:
- `EstimateResponse.from(Estimate)`: incluir `status` (string) no corpo.
- `EstimateController`: atualizar `@ApiResponses` do endpoint `decide` para documentar o novo caso `409`
  (Estimate não `SENT`), além do já existente (`ServiceExecution` não `PENDING`).

Atualizar:
- OpenAPI (gerado automaticamente pelo springdoc a partir das anotações do controller, sem YAML manual);
- collection Postman (`docs/api/postman/workshop-management-system.postman_collection.json`): exemplos de
  resposta de `GET /api/estimates/{estimateId}` e `POST /api/service-orders/{serviceOrderId}/estimates`
  passam a incluir `status`; documentar o cenário `409` adicional em `POST
  /api/estimates/{estimateId}/decisions`.

Testes (`EstimateControllerTest`/`EstimateControllerDecideLinesTest`):
- `status` presente no corpo de `GET /api/estimates/{id}` e de `POST
  /api/service-orders/{id}/estimates`;
- `409`/`INVALID_STATE_TRANSITION` em `POST /api/estimates/{id}/decisions` para Estimate já `CLOSED`
  (decidir todas as linhas primeiro, depois tentar decidir novamente).

## Checkpoint 5 — Validação final

Executar:
- testes de domínio, aplicação e web desta feature;
- `./mvnw test` (suíte completa) para garantir ausência de regressão em `estimate-generation` e
  `decide-estimate-lines`;
- `./mvnw test -Dtest=ModuleStructureTest` (nenhuma mudança de fronteira de módulo é esperada);
- `make verify` / `./mvnw verify`.

Revisar:
- OpenAPI e Postman refletem exatamente o contrato descrito em `technical-spec.md`;
- nenhuma regra de `estimate-generation` (congelamento de `StockRequirement`, `expiresAt`, evento
  `EstimateGenerated`) foi alterada;
- nenhum método `expire()`/gatilho de expiração foi introduzido (permanece bloqueado por AD-013).

## Definition of Done

- [x] `EstimateStatus` implementado; `Estimate` com `status`, `markSent()`, `close()` e testes.
- [x] Migração Flyway aplicada; `EstimateJpaEntity`/`EstimatePersistenceMapper` atualizados e testados.
- [x] `GenerateEstimateUseCase` chama `markSent()` antes de persistir; testado.
- [x] `DecideEstimateLinesUseCase` valida `SENT` e fecha a Estimate na última decisão; testado.
- [x] `EstimateResponse`/`EstimateController` atualizados; testes HTTP passando.
- [x] OpenAPI e Postman atualizados.
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança concluída, com achados e mitigações registrados.
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: nenhum campo novo aceito do cliente; `status` é somente computado pelo
  domínio.
- **Autenticação/autorização**: nenhuma mudança; mesmo padrão de risco pré-existente já registrado em
  `decide-estimate-lines` (nenhum mecanismo de autenticação no projeto hoje).
- **Exposição de dados**: `status` da Estimate passa a ser exposto em leitura — não é dado sensível (é um
  valor de fluxo de negócio já implícito no status de cada `ServiceExecution`, hoje visível via
  `ServiceOrderResponse`).
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo.
- **SQL/persistência/migration**: migração aditiva (`ADD COLUMN ... DEFAULT`), sem `DROP`/`ALTER`
  destrutivo; sem SQL dinâmico.
- **Erros e disclosure**: novo `409` reaproveita `ServiceLifecycleExceptionHandler` já existente, sem
  stack trace nem detalhe de SQL.
- **Dependências novas**: nenhuma.
- **Abuso**: nenhum vetor novo; mesma superfície de `POST /api/estimates/{estimateId}/decisions` já
  existente.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=EstimateTest,GenerateEstimateUseCaseTest,DecideEstimateLinesUseCaseTest,EstimateControllerTest,EstimateControllerDecideLinesTest`
  — 2026-08-23, 36 testes, 0 falhas (transições `DRAFT→SENT`/`SENT→CLOSED` no domínio; Estimate persistida
  com `SENT` na geração; fechamento automático na última decisão; rejeição de decisão em Estimate `SENT`
  parcial permanece `SENT`; rejeição de decisão em Estimate `CLOSED`; `status` no corpo HTTP).
- `./mvnw test -Dtest=EstimateGeneratedNotificationApplicationModuleTest` — 2026-08-23, 1 teste, 0 falhas
  (corrigida uma assinatura pré-existente e desatualizada de `ServiceOrder.create`/`performDiagnosis`
  nesse arquivo — não relacionada a esta feature, ajustada em separado por bloquear a compilação de toda a
  suíte).
- `./mvnw test -Dtest=ModuleStructureTest` — 2026-08-23, 2 testes, 0 falhas; nenhuma fronteira de módulo
  violada.
- Revisão de código (`/code-review`) apontou duas lacunas de cobertura frente ao próprio `technical-spec.md`:
  ausência de teste HTTP para `status` e para o `409` de Estimate não `SENT`, ausência de teste de
  `EstimatePersistenceMapper`/migração para os 4 valores de `EstimateStatus`, e um teste HTTP existente
  (`returnsConflictWhenServiceExecutionIsNotPending`) que, com uma Estimate de uma única linha, passou a
  cobrir a checagem de status da Estimate em vez da checagem de status da `ServiceExecution` que seu nome
  descreve. Corrigido: `EstimateControllerTest`/`EstimateControllerDecideLinesTest` ganharam asserções de
  `$.status`; o teste existente passou a usar uma Estimate de duas linhas para isolar a checagem de
  `ServiceExecution`; foi adicionado `returnsConflictWhenEstimateIsAlreadyClosed` para cobrir
  especificamente a Estimate já `CLOSED`; foram criados `EstimatePersistenceMapperTest` (round-trip dos 4
  valores de `EstimateStatus`) e `EstimateStatusMigrationTest` (confirma o backfill `DEFAULT 'SENT'` da
  migração para uma Estimate inserida antes da coluna existir).
- `./mvnw test -Dtest=EstimateControllerTest,EstimateControllerDecideLinesTest,EstimateStatusMigrationTest,EstimatePersistenceMapperTest`
  — 2026-08-23, 15 testes, 0 falhas.
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-23, 338 testes no total, 0 falhas, 0 erros,
  JaCoCo executado, `BUILD SUCCESS`.
- Postman: descrição do cenário `409` adicional documentada em "Decide estimate lines"
  (`docs/api/postman/workshop-management-system.postman_collection.json`); JSON validado (`JSON.parse`
  via Node sem erro). Não havia exemplos de resposta salvos para `Estimates` antes desta feature, então
  nenhum exemplo precisou ser reescrito com o novo campo `status`.
- OpenAPI: `status` e o novo `409` documentados via `@Operation`/`@ApiResponses` em `EstimateController`
  (springdoc gera o contrato automaticamente, sem YAML manual).

## Rollback ou recuperação

Reversível via `git revert` do commit da feature. A migração é aditiva (`ADD COLUMN ... DEFAULT`); reverter
o código sem reverter a migração deixa uma coluna `status` não utilizada no banco, o que é inofensivo (sem
perda de dado, sem violação de constraint). Se a migração precisar ser desfeita também, isso exige uma nova
migração `DROP COLUMN status` — o projeto não usa `DOWN` migrations.

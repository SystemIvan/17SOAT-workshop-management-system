# Plano de Implementação: Monitorar tempo médio de execução dos serviços

| Campo | Valor |
|---|---|
| Feature | `average-service-execution-time` |
| Status | Implemented |
| Responsável | Ivan Gomes |
| Atualizado em | 2026-08-28 |
| Branch de implementação | `feat/servicelifecycle-average-service-execution-time` |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-28) |

## Checkpoints

### 1. Fatos temporais no domínio e na aplicação

- [x] Adicionar `startedAt` e `completedAt` imutáveis a `ServiceExecution`.
- [x] Registrar os instantes UTC nas transições de início e conclusão usando `Clock` testável.
- [x] Usar `findByIdForUpdate` nos comandos para preservar a primeira transição concorrente.
- [x] Preservar compatibilidade de reconstituição com registros legados sem timestamps.
- [x] Atualizar testes de domínio e casos de uso de start/complete.

### 2. Persistência e agregação

- [x] Criar migration Flyway aditiva com colunas `TIMESTAMP(6)` anuláveis, constraint e índice.
- [x] Classificação de dados: **no seed required**.
- [x] Atualizar entidade e mapper JPA sem backfill inferido.
- [x] Criar porta e adapter de leitura com agregação no banco, sem carregar `ServiceOrder`s.
- [x] Cobrir migration e agregação contra H2 em modo MySQL.

### 3. Consulta administrativa HTTP

- [x] Criar value object de período e erro estável para intervalo inválido.
- [x] Criar caso de uso e DTOs com unidade `HOURS`, duas casas e `averageHours = null` sem amostras.
- [x] Expor `GET /api/service-orders/metrics/average-execution-time` com OpenAPI.
- [x] Restringir o endpoint a `MANAGER` e `ADMIN` antes do matcher geral de Service Orders.
- [x] Cobrir contrato, validação, ausência de dados e matriz de autorização por MockMvc.

### 4. Contratos e documentação

- [x] Atualizar `OpenApiContractTest` e o contrato gerado.
- [x] Atualizar a coleção Postman com a consulta após conclusão de execução.
- [x] Atualizar `README.md` com o fluxo manual, variáveis e resultados esperados em horas.
- [x] Atualizar a matriz JWT e `docs/Architecture.md`.

### 5. Segurança e qualidade

- [x] Revisar validação, mass assignment e abuso do intervalo.
- [x] Revisar autenticação/autorização e exposição de dados operacionais.
- [x] Revisar SQL parametrizado, migration, índices e ausência de backfill.
- [x] Revisar logs, segredos, dependências e respostas de erro.
- [x] Executar `make test`, `make verify`, validar cobertura e `ModuleStructureTest`.
- [x] Resolver findings críticos/altos e registrar findings menores com mitigação.

## Evidências de verificação

- Testes direcionados de domínio e aplicação: `ServiceExecutionTest`, `StartExecutionUseCaseTest`,
  `CompleteExecutionUseCaseTest` e `GetAverageServiceExecutionTimeUseCaseTest` passaram sem falhas.
- Testes direcionados de persistência: round-trip JPA, migration sem backfill, constraint/índice e agregação global e
  agrupada passaram contra H2 em modo MySQL.
- Compatibilidade do HQL com MySQL: teste do `MySQLDialect` confirma a tradução de `NANOSECOND` para
  `timestampdiff(microsecond, ...)*1e3`, unidade suportada pelo banco, sem expor essa granularidade pela porta.
- Testes HTTP/OpenAPI/segurança: contrato em horas, período inválido/ausente, ausência de amostras e papéis
  `CUSTOMER`, `TECHNICIAN`, `MANAGER` e `ADMIN` passaram com os status esperados.
- `make test` — 657 testes, zero falhas, erros ou skips antes do gate final.
- `make verify` — 658 testes, `BUILD SUCCESS`; migration/startup, empacotamento, JaCoCo e Maven Enforcer passaram.
- `ModuleStructureTest` — 2 testes, zero falhas; nenhuma violação das fronteiras Modulith.
- JaCoCo — cobertura global de linhas em 92,23% (meta do projeto: 80%); relatório em
  `target/site/jacoco/index.html`. `make verify` e `make coverage` executam o mesmo `./mvnw clean verify` neste projeto.
- Postman validado com `jq empty`; `git diff --check` sem erros.
- Ambiente manual: Docker CLI 29.7.2, Docker Engine 29.5.2 via Colima, Docker Compose 5.5.0 e MySQL 8.0.46.
- Startup em schema vazio: Flyway validou e aplicou 23 migrations, concluindo em
  `V20260828150535__add_service_execution_timestamps.sql`; Hibernate iniciou com `ddl-auto=validate`.
- Inspeção do MySQL confirmou `started_at` e `completed_at` como `TIMESTAMP(6) NULL`, a constraint
  `ck_service_executions_execution_times` ativa e o índice
  `idx_service_executions_execution_time_metrics(status, completed_at, catalog_service_id)`.
- `make e2e` passou sem falhas contra o stack Docker/MySQL. A request `Get average execution time` retornou `200`,
  `unit = HOURS` e confirmou a amostra concluída nos resultados global e por `catalogServiceId`.
- `EXPLAIN FORMAT=TREE` confirmou `Index range scan` no índice dedicado tanto para a agregação global quanto para a
  agrupada. O plano agrupado utiliza tabela temporária e ordenação para `GROUP BY catalog_service_id`, sem carregar
  agregados na aplicação; comportamento aceito para o MVP.

## Revisão de segurança

- **Entrada e mass assignment:** a consulta recebe somente `from` e `to` obrigatórios como `Instant`; valores ausentes
  ou malformados retornam `400/VALIDATION_ERROR`, e `from >= to` retorna
  `400/INVALID_EXECUTION_TIME_PERIOD`. Não há body nem binding de estado interno. OK.
- **Autenticação e autorização:** matcher literal anterior à regra geral permite apenas `MANAGER` e `ADMIN`.
  Ausência de token retorna `401`; `CUSTOMER` e `TECHNICIAN` recebem `403`. Cobertura end-to-end pelo filtro real. OK.
- **Exposição:** a resposta contém período, `catalogServiceId`, quantidade e média agregada. Não expõe Customer,
  Vehicle, Technician, timestamps individuais ou entidades de domínio/JPA. OK.
- **Persistência e SQL:** HQL com parâmetros, sem concatenação de entrada; cálculo no banco, constraint de ordem
  temporal e índice `(status, completed_at, catalog_service_id)`. A migration é aditiva, mantém colunas anuláveis e
  não infere backfill. OK.
- **Concorrência e integridade:** comandos de início/conclusão usam `findByIdForUpdate`; o domínio rejeita repetição,
  ausência de início e conclusão anterior ao início, e a constraint reforça a ordem temporal. OK.
- **Erros e disclosure:** respostas usam códigos estáveis e mensagens seguras, sem stack trace, SQL ou tipos internos.
  Nenhum log foi adicionado. OK.
- **Segredos e dados sensíveis:** nenhum segredo/configuração nova e nenhum JWT, dado pessoal ou payload operacional
  é registrado. OK.
- **Dependências e vulnerabilidades:** nenhuma dependência foi adicionada; revisão de nova dependência é `N/A`. OK.
- **Abuso/performance:** o período não possui limite máximo porque nenhum limite funcional foi aprovado. O risco
  residual de intervalos extensos é baixo no MVP: endpoint administrativo, agregação no banco e índice dedicado. Se o
  volume operacional justificar, limite de janela/rate limit exige nova regra contratual e observabilidade. Aceito.

Nenhum finding crítico ou alto permanece aberto.

## Validação manual no MySQL 8.0

O gate manual foi concluído em 2026-08-28 sobre MySQL 8.0.46 iniciado pelo `docker-compose.yml` do projeto. A
migration e o startup foram verificados em schema vazio, o fluxo Postman completo foi executado por `make e2e` e os
planos das consultas global e agrupada foram revisados com `EXPLAIN FORMAT=TREE`. Não foi identificado finding de
compatibilidade, integridade ou performance que impeça o MVP.

## Rollback ou recuperação

A migration é aditiva e não recebe down migration. Em falha de aplicação, reimplantar a versão anterior preservando
as colunas anuláveis. Não remover timestamps já gravados e não editar a migration depois de aplicada. Uma correção de
schema posterior deve usar nova migration versionada.

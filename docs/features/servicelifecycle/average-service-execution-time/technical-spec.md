# Especificação Técnica: Monitorar tempo médio de execução dos serviços

| Campo | Valor |
|---|---|
| Feature | `average-service-execution-time` |
| Status | Approved |
| Responsável | Ivan Gomes |
| Atualizado em | 2026-08-28 |
| Aprovado por | Ivan Gomes |
| Aprovado em | 2026-08-28 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-28) |
| Decisão arquitetural | `../../../adr/ADR-006-average-service-execution-time.md` (`Accepted` em 2026-08-28) |

## Objetivo técnico

Persistir os instantes UTC das transições que iniciam e concluem uma `ServiceExecution` e disponibilizar uma consulta
administrativa que agregue, no banco de dados, a duração média das execuções concluídas em um período. A resposta
apresenta os valores exclusivamente em horas, com a quantidade de amostras global e por `catalogServiceId`.

A solução permanece em `servicelifecycle.serviceorder`. Não cria bounded context de Analytics, não carrega agregados
`ServiceOrder` completos para calcular a métrica e não consulta o módulo `registration` para enriquecer os grupos.

## Contexto e desenho

### Componentes afetados

- `ServiceExecution` passa a preservar `startedAt` e `completedAt` como fatos temporais imutáveis.
- `StartExecutionUseCase` e `CompleteExecutionUseCase` fornecem o instante atual ao domínio usando `Clock.systemUTC()`
  e permitem `Clock` fixo nos testes.
- `ServiceExecutionJpaEntity` e `ServiceOrderPersistenceMapper` passam a persistir e reconstituir os dois instantes.
- Uma porta de leitura dedicada expõe agregados estatísticos sem ampliar `ServiceOrderRepository` nem materializar
  `ServiceOrder`s.
- `GetAverageServiceExecutionTimeUseCase` valida o período, executa a leitura transacional e monta o contrato HTTP.
- `ServiceOrderController` recebe um endpoint literal de métricas sob `/api/service-orders/metrics`.
- `SecurityConfig` recebe um matcher específico, anterior à regra geral de Service Orders.

### Fluxo de escrita dos fatos temporais

```text
POST .../start
    -> StartExecutionUseCase obtém Clock UTC
    -> carrega ServiceOrder com lock
    -> ServiceOrder.startExecution(executionId, startedAt)
    -> ServiceExecution valida READY + Technician e grava startedAt
    -> persiste o agregado

POST .../complete
    -> CompleteExecutionUseCase obtém Clock UTC
    -> carrega ServiceOrder com lock
    -> ServiceOrder.completeExecution(executionId, completedAt)
    -> ServiceExecution valida IN_PROGRESS + ordem temporal e grava completedAt
    -> persiste o agregado
```

Os dois casos de uso passam a utilizar `ServiceOrderFinder.getOrThrowForUpdate`. O lock pessimista já oferecido por
`ServiceOrderRepository.findByIdForUpdate` impede que comandos concorrentes registrem instantes diferentes para a
mesma primeira transição. A operação continua rejeitando repetição por `INVALID_STATE_TRANSITION`.

O instante obtido do `Clock` é truncado para microssegundos antes de entrar no domínio, alinhando a precisão Java às
colunas `TIMESTAMP(6)` e evitando diferenças artificiais após persistência e reconstituição.

### Alterações no domínio

`ServiceExecution` recebe:

```java
private Instant startedAt;
private Instant completedAt;

void start(Instant startedAt);
void complete(Instant completedAt);

public Instant startedAt();
public Instant completedAt();
```

Regras aplicadas nos métodos de intenção:

- `start` preserva as validações atuais de status `READY` e Technician atribuído;
- `startedAt` é obrigatório e só é gravado depois que todas as pré-condições passam;
- `complete` preserva a validação atual de status `IN_PROGRESS`;
- `completedAt` é obrigatório, exige `startedAt` e não pode ser anterior a ele;
- igualdade entre os instantes é válida e representa duração zero;
- nenhum método de pausa, retomada ou ajuste de duração é criado;
- os instantes não possuem setters nem métodos de correção retroativa.

`ServiceOrder.startExecution` e `ServiceOrder.completeExecution` recebem o `Instant` e o encaminham à execução. A
reconstituição aceita ambos nulos para registros legados; quando os dois existirem, preserva a ordem temporal. Os
overloads usados por testes legados continuam disponíveis e delegam com timestamps nulos, reduzindo alterações sem
relação com a feature.

`ServiceExecutionResponse` e os contratos atuais de detalhe da Service Order não recebem os timestamps. Eles são
fatos internos necessários para a métrica; expô-los seria um contrato adicional não exigido pela especificação
funcional.

### Porta de leitura e agregação

Será criada `ServiceExecutionTimeMetricsReadPort` em `serviceorder.application.port`, com operações para o agregado
global e para os grupos por `catalogServiceId`. Os resultados da porta já usam `BigDecimal averageHours`; nenhuma
unidade intermediária atravessa a camada de aplicação ou a borda HTTP.

O adapter `ServiceExecutionTimeMetricsQueryAdapter`, em `infrastructure.persistence`, usa projeções HQL sobre
`ServiceExecutionJpaEntity`. As consultas aplicam os mesmos predicados:

```text
status = COMPLETED
startedAt IS NOT NULL
completedAt IS NOT NULL
completedAt >= fromInclusive
completedAt < toExclusive
```

Uma consulta retorna `COUNT` e `AVG` global. A segunda agrupa as mesmas expressões por `catalogServiceId`. A expressão
de duração usa a aritmética temporal do Hibernate em granularidade de nanossegundos e é traduzida pelo Dialect para
MySQL e H2; o adapter converte o resultado agregado para horas antes de retornar pela porta. Nenhuma entidade ou lista
de execuções é materializada.

Os grupos são ordenados por `catalogServiceId` para produzir resposta determinística. Execuções novas sempre possuem
esse ID pela regra atual de Diagnosis. A consulta agrupada também exige `catalogServiceId IS NOT NULL` como proteção
contra scaffolding legado inconsistente; registros sem ID não originam um grupo inventado.

### Precisão e arredondamento

- a unidade pública é `HOURS`;
- `averageHours` é `BigDecimal` com duas casas decimais;
- o arredondamento usa `RoundingMode.HALF_UP` apenas depois da média global ou agrupada ter sido calculada;
- a duração de cada execução não é arredondada individualmente;
- `sampleCount > 0` com `averageHours = 0.00` é diferente de ausência de amostras;
- `sampleCount = 0` produz `averageHours = null`.

## Interfaces e fluxo de dados

### Contrato HTTP

```http
GET /api/service-orders/metrics/average-execution-time?from={instant}&to={instant}
Authorization: Bearer {jwt}
```

`from` e `to` são obrigatórios e usam ISO-8601 com offset. O controller converte ambos para `Instant`; a resposta ecoa
o intervalo normalizado em UTC. `from` é inclusivo e `to` é exclusivo.

Resposta `200 OK` com amostras:

```json
{
  "completedFromInclusive": "2026-08-01T00:00:00Z",
  "completedToExclusive": "2026-09-01T00:00:00Z",
  "unit": "HOURS",
  "global": {
    "sampleCount": 3,
    "averageHours": 1.50
  },
  "byCatalogService": [
    {
      "catalogServiceId": "11111111-1111-1111-1111-111111111111",
      "sampleCount": 2,
      "averageHours": 1.25
    }
  ]
}
```

Resposta `200 OK` sem amostras:

```json
{
  "completedFromInclusive": "2026-08-01T00:00:00Z",
  "completedToExclusive": "2026-09-01T00:00:00Z",
  "unit": "HOURS",
  "global": {
    "sampleCount": 0,
    "averageHours": null
  },
  "byCatalogService": []
}
```

A resposta usa records próprios e nunca expõe entidades de domínio ou JPA:

- `AverageServiceExecutionTimeResponse`;
- `ExecutionTimeAverageResponse`;
- `CatalogServiceExecutionTimeAverageResponse`.

O resultado global e os grupos são entregues juntos para o mesmo período, conforme confirmação funcional. Não há
paginação porque a quantidade de grupos é limitada aos serviços do catálogo representados nas amostras, não à
quantidade de execuções.

### Validação e falhas

Um value object `ExecutionTimePeriod` valida `fromInclusive < toExclusive` antes de chamar a porta de leitura.

| Situação | HTTP | Código estável | Comportamento |
|---|---:|---|---|
| `from` ou `to` ausente/malformado | 400 | `VALIDATION_ERROR` | Handler global existente |
| `from >= to` | 400 | `INVALID_EXECUTION_TIME_PERIOD` | Novo erro de `servicelifecycle` |
| Período válido sem amostras | 200 | — | Contagem zero, média nula e grupos vazios |
| JWT ausente ou inválido | 401 | `UNAUTHORIZED` | Handler de autenticação existente |
| Papel diferente de `MANAGER`/`ADMIN` | 403 | `FORBIDDEN` | Handler de acesso existente |

`InvalidExecutionTimePeriodException` é traduzida por `ServiceLifecycleExceptionHandler`, com mensagem segura e sem
detalhes de SQL. Falhas técnicas inesperadas continuam seguindo o tratamento padrão da plataforma.

### Caso de uso

`GetAverageServiceExecutionTimeUseCase.execute(from, to)`:

1. cria e valida `ExecutionTimePeriod`;
2. consulta o resultado global e os grupos pela porta de leitura;
3. normaliza escala, arredondamento e ausência de média;
4. retorna o DTO com o período UTC e `unit = HOURS`.

O método é público e anotado com `@Transactional(readOnly = true)`.

## Persistência e dados de bootstrap

### Migration Flyway

Uma migration versionada, com timestamp UTC no momento da implementação e nome
`VyyyyMMddHHmmss__add_service_execution_timestamps.sql`, executará:

```sql
ALTER TABLE service_executions ADD COLUMN started_at TIMESTAMP(6) NULL;
ALTER TABLE service_executions ADD COLUMN completed_at TIMESTAMP(6) NULL;

ALTER TABLE service_executions
    ADD CONSTRAINT ck_service_executions_execution_times
    CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at));

CREATE INDEX idx_service_executions_execution_time_metrics
    ON service_executions (status, completed_at, catalog_service_id);
```

As colunas são anuláveis por compatibilidade com registros anteriores. Não haverá `UPDATE`, inferência por status,
consulta a logs nem qualquer backfill. Novas escritas passam a preencher os fatos pelas transições do domínio.

O índice começa por `status` e `completed_at`, predicados da consulta global e agrupada, e inclui
`catalog_service_id` para reduzir o custo do agrupamento. O plano da consulta será verificado em MySQL durante a
implementação; otimizações adicionais exigem evidência de volume e não fazem parte desta versão.

### Classificação de dados

**No seed required.** A feature adiciona colunas e índice, mas não introduz dado de referência nem exemplo de
negócio. Testes usam fixtures próprias e não dependem dos seeders de desenvolvimento.

### Rollout e recuperação

- a migration é aditiva e compatível com registros existentes;
- Hibernate continua com `ddl-auto=validate`;
- a aplicação nova pode iniciar com timestamps legados nulos;
- rollback da aplicação preserva as colunas adicionadas; a migration aplicada não é revertida nem editada;
- registros criados durante o rollout só entram na métrica quando possuírem os dois instantes válidos.

## Segurança e operação

`SecurityConfig` recebe, antes de `/api/service-orders/**`:

```java
.requestMatchers(
        HttpMethod.GET,
        "/api/service-orders/metrics/average-execution-time")
.hasAnyAuthority("MANAGER", "ADMIN")
```

Isso impede que a permissão geral atual de `TECHNICIAN` para Service Orders alcance a métrica. A matriz em
`docs/features/platform/jwt-authentication/technical-spec.md` será atualizada no mesmo checkpoint.

Revisão de segurança prevista:

- entrada: somente dois instantes, sem mass assignment ou corpo arbitrário;
- autorização: `MANAGER` e `ADMIN`, com testes explícitos para `CUSTOMER` e `TECHNICIAN`;
- exposição: resposta contém apenas período, IDs de catálogo e dados agregados, sem Customer, Vehicle ou
  Technician;
- persistência: query parametrizada por HQL, sem concatenação de entrada em SQL;
- logs: não registrar JWT nem payloads; períodos e contagens podem ser logados somente se houver necessidade
  operacional;
- abuso: o período é obrigatório, a consulta é indexada e restrita a papéis administrativos; não há rate limiting
  novo;
- dependências e segredos: nenhuma dependência, configuração sensível ou segredo novo.

## Estratégia de testes

### Domínio

Atualizar `ServiceExecutionTest` e `ServiceOrderTest` para cobrir:

- início válido registra exatamente o `startedAt` informado;
- conclusão válida preserva `startedAt` e registra `completedAt`;
- conclusão no mesmo instante aceita duração zero;
- conclusão anterior ao início falha sem alterar o estado;
- transições inválidas não gravam timestamps;
- reconstituição legada aceita os dois instantes nulos;
- reconstituição válida preserva ambos os instantes.

### Aplicação

Atualizar testes de `StartExecutionUseCase` e `CompleteExecutionUseCase` com `Clock.fixed` para comprovar o instante
persistido e a utilização de `findByIdForUpdate`.

Criar `GetAverageServiceExecutionTimeUseCaseTest` com porta fake para cobrir:

- encaminhamento exato do período inclusivo/exclusivo;
- resultado global e agrupado no mesmo DTO;
- arredondamento `HALF_UP` em duas casas;
- amostra zero produz média nula;
- média zero com amostras permanece `0.00`;
- `from >= to` produz `INVALID_EXECUTION_TIME_PERIOD` sem consultar a porta.

### Persistência e migration

Criar teste de integração do adapter de leitura contra H2 em modo MySQL cobrindo:

- média global de múltiplas durações fracionárias em horas;
- agrupamento por dois `catalogServiceId`s;
- início inclusivo e fim exclusivo por `completedAt`;
- exclusão de status diferente de `COMPLETED` e de timestamps nulos;
- resultado vazio;
- precisão preservada sem carregar `ServiceOrder`s.

Criar cobertura de migration/startup para colunas anuláveis, constraint temporal, índice e Hibernate validate.
Executar também o fluxo manual contra MySQL e revisar `EXPLAIN` das consultas global e agrupada.

### HTTP, OpenAPI e segurança

Criar teste MockMvc do endpoint para:

- `200` com contrato em horas, período, média global e grupos;
- `200` sem amostras, com `averageHours = null`;
- `400 VALIDATION_ERROR` para parâmetros ausentes ou inválidos;
- `400 INVALID_EXECUTION_TIME_PERIOD` para intervalo vazio ou invertido;
- `401` sem token;
- `403` para `CUSTOMER` e `TECHNICIAN`;
- `200` para `MANAGER` e `ADMIN`.

Atualizar `OpenApiContractTest` para verificar path, parâmetros obrigatórios, schemas, nulabilidade de `averageHours`,
unidade `HOURS` e responses `200`, `400`, `401` e `403`.

### Regressão e fronteiras

- testes atuais de start/complete continuam verdes com o contrato HTTP inalterado;
- `ModuleStructureTest` confirma que nenhuma dependência entre módulos foi criada;
- `make test` durante o desenvolvimento e `make verify` antes da conclusão;
- `make coverage` confirma que a cobertura total permanece em pelo menos 80% e que o código alterado está coberto.

## Contratos e documentação

No mesmo checkpoint da implementação:

- documentar o endpoint e os erros com Springdoc/OpenAPI;
- atualizar `docs/api/postman/workshop-management-system.postman_collection.json` com a consulta após o fluxo de
  conclusão de execução;
- atualizar `README.md` com pré-requisitos, obtenção do JWT administrativo, variáveis `from`/`to`, ordem das
  requests e resultados esperados em horas;
- atualizar a matriz da spec de JWT com o matcher `MANAGER`/`ADMIN`;
- atualizar `docs/Architecture.md` para trocar a lacuna de tempo médio por decisão aceita e, após implementação,
  pela evidência do endpoint e dos timestamps;
- manter OpenAPI gerado pela aplicação como fonte de verdade, sem YAML manual.

## Compatibilidade

- endpoint novo e migration aditiva: não há breaking change externo;
- endpoints de start, complete, detalhe e tracking preservam requests e responses atuais;
- registros históricos continuam válidos, mas ficam fora da métrica enquanto não tiverem ambos os timestamps;
- `ServiceExecutionResponse` permanece inalterado;
- nenhuma chamada entre módulos ou consulta viva ao Service Catalog é adicionada.

## Fora de escopo técnico

- pausa, retomada, segmentos ativos ou correção manual de timestamps;
- lead time completo da `ServiceOrder`;
- backfill ou reconstrução a partir de logs;
- dashboard, exportação, cache, tabela pré-agregada, scheduler ou Micrometer como fonte da métrica;
- filtros por Technician, Customer, Vehicle ou prioridade;
- mediana, percentis, SLA, tendência e comparação entre períodos;
- paginação dos grupos ou enriquecimento com nome/preço do Service Catalog;
- exposição de `startedAt` e `completedAt` nos responses atuais de Service Order.

## Gates de validação

Antes da implementação:

- [x] ADR-006 aceita e AD-019 resolvida em 2026-08-28.
- [x] Especificação funcional aprovada por Ivan Gomes em 2026-08-28.
- [x] Especificação técnica revisada e aprovada por Ivan Gomes em 2026-08-28.

Antes da conclusão:

- [x] migration, persistência, domínio, aplicação e HTTP cobertos por testes;
- [x] `make verify` verde, sem testes indevidamente ignorados;
- [x] cobertura revisada e não inferior a 80%;
- [x] `ModuleStructureTest` verde;
- [x] revisão de segurança registrada sem finding crítico/alto aberto;
- [x] OpenAPI, Postman, README, matriz JWT e Architecture atualizados;
- [x] execução manual em MySQL e plano das consultas revisados.

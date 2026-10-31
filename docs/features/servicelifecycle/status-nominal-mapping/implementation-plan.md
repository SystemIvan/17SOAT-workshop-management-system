# Plano de Implementação: Mapeamento do status da OS para os 6 estados nominais da Fase 2

| Campo | Valor |
|---|---|
| Feature | `status-nominal-mapping` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-21 |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-09-21) |

## Objetivo

Adicionar, de forma aditiva, o campo `statusLabel` (novo enum `ServiceOrderStatusLabel`, 6 valores:
`RECEBIDA`, `DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, `EXECUCAO`, `FINALIZADA`, `ENTREGUE`) a
`ServiceOrderResponse` e `ServiceOrderStatusResponse`, calculado a partir do `ServiceOrderStatus` já
persistido, com `AWAITING_ITEMS` e `IN_PROGRESS` mapeando ambos para `EXECUCAO` (decisão (a) registrada na
`functional-spec.md`). Nenhum contrato existente (`status`, `statusSnapshot`) muda de nome, tipo ou
semântica; nenhuma migration.

## Checkpoint 1 — `ServiceOrderStatusLabel` (novo enum + mapeamento)

- Criar `serviceorder/application/dto/ServiceOrderStatusLabel.java` com os 6 valores e o método estático
  `from(ServiceOrderStatus)` (`switch` exaustivo, sem `default`).
- Teste (`ServiceOrderStatusLabelTest`, novo, parametrizado): os 7 valores de `ServiceOrderStatus` mapeiam
  para o `ServiceOrderStatusLabel` esperado, incluindo o caso explícito `AWAITING_ITEMS` → `EXECUCAO` e
  `IN_PROGRESS` → `EXECUCAO` (mesmo valor, statuses internos diferentes).

## Checkpoint 2 — DTOs de resposta

- `ServiceOrderStatusResponse`: adicionar componente `ServiceOrderStatusLabel statusLabel` (mantendo `id`
  e `status` existentes).
- `ServiceOrderResponse`: adicionar componente `ServiceOrderStatusLabel statusLabel` logo após
  `statusSnapshot` (mantendo `status`/`statusSnapshot` existentes, sem reordenar os demais campos).

## Checkpoint 3 — `ServiceOrderMapper`

- `toStatusResponse`: calcular `ServiceOrderStatusLabel.from(status)` e passar ao novo record.
- `toResponse`: idem, reaproveitando a variável local `status` já existente no método.
- Nenhuma outra alteração no mapper.

## Checkpoint 4 — Testes HTTP de ponta a ponta (alteração dos testes já existentes)

- Endpoint `GET /{id}/status`: para cada um dos 7 status possíveis de uma OS (reaproveitando os use cases
  já existentes para levar a OS a cada status, mesmo padrão de setup usado em
  `ServiceOrderControllerListTest` de `list-service-orders-sorting`), a resposta traz `statusLabel`
  coerente — cobrir explicitamente `AWAITING_ITEMS` → `"EXECUCAO"`, o caso não óbvio da feature.
- Endpoint `GET /api/service-orders` (listagem): cada item do array retornado traz `statusLabel` coerente
  com seu `status`.
- Endpoint `GET /api/service-orders/{id}`: resposta traz `statusLabel` coerente (mesmo
  `ServiceOrderResponse` usado pela listagem).
- Regressão: `status` e `statusSnapshot` continuam presentes com os mesmos valores de antes em todas as
  respostas acima.

## Checkpoint 5 — Contratos e documentação

- **OpenAPI**: campo/enum novos nos records são inferidos automaticamente pelo Springdoc, sem anotação
  manual — mesmo padrão já usado para os demais campos de `ServiceOrderResponse`/
  `ServiceOrderStatusResponse`. Não foi possível subir a aplicação completa localmente para inspecionar
  `/v3/api-docs` (ambiente sem MySQL disponível nesta sessão; `spring-boot:run` falhou por
  `CJCommunicationsException`/conexão recusada). Em vez disso, a serialização real de `statusLabel` como
  enum de 6 valores foi confirmada indiretamente pelos testes HTTP do Checkpoint 4 (MockMvc contra H2), que
  exercitam o mesmo `ObjectMapper`/Jackson usado pelo Springdoc para gerar o schema. Revisão visual do
  Swagger UI fica pendente para quando houver MySQL disponível — não bloqueia esta feature dado que
  `OpenApiContractTest` (que só verifica existência de paths, não schema de campo) continua verde.
- **Postman**: adicionados `pm.test` de `statusLabel` em "Get service order", "Get service order status",
  "List service orders" e "List service orders filtered by status" — cobrindo o caso não óbvio
  (`IN_DIAGNOSIS` → `DIAGNOSTICO`) além do enum fechado de 6 valores nas demais requests.
- **README.md** (raiz): item 6 (consulta de status logo após a criação) e item 15 (finalização) passam a
  mencionar `statusLabel` explicitamente; item 16 (explicação de `Get service order` vs.
  `Get service order status`) foi reescrito para documentar `statusLabel` como a representação nominal
  externa de 6 estados, com a nota de que `AWAITING_ITEMS`/`IN_PROGRESS` mapeiam ambos para `EXECUCAO`.

## Checkpoint 6 — Validação final

Executar:
- `./mvnw test -Dtest=ServiceOrderStatusLabelTest`;
- `./mvnw test` (suíte completa, checar ausência de regressão em qualquer teste que já asserte o corpo
  completo de `ServiceOrderResponse`/`ServiceOrderStatusResponse`, já que um campo novo pode quebrar
  asserções de igualdade estrita de JSON);
- `./mvnw verify` (`make verify`);
- `./mvnw test -Dtest=ModuleStructureTest`.

Revisar:
- nenhuma mudança de assinatura HTTP além do campo aditivo (path, método, params, status codes idênticos);
- nenhuma fronteira do Spring Modulith violada;
- `docs/api/postman/...json` continua um JSON válido após a edição manual.

## Definition of Done

- [x] `ServiceOrderStatusLabel` implementado e testado (mapeamento total dos 7 valores).
- [x] `ServiceOrderResponse`/`ServiceOrderStatusResponse` com `statusLabel`, `ServiceOrderMapper`
      atualizado.
- [x] Testes HTTP cobrindo `statusLabel` nos 3 endpoints de leitura afetados, sem regressão em
      `status`/`statusSnapshot`.
- [x] Testes relevantes passando.
- [x] `make verify` passando.
- [x] Revisão de segurança registrada.
- [x] Postman e README (raiz) atualizados; OpenAPI conferido indiretamente (ver Checkpoint 5 — inspeção
      visual do Swagger UI pendente por falta de MySQL local nesta sessão).
- [ ] PR pronto para review.

## Revisão de segurança

- **Validação de entrada**: nenhum parâmetro novo; nenhuma mudança de validação. N/A.
- **Autenticação/autorização**: nenhuma mudança — mesma regra já existente em `SecurityConfig` para
  `/api/service-orders/**`. Confirmado: a suíte completa (700 testes, incluindo os de autorização) passou
  sem quebra por causa do campo novo no corpo da resposta.
- **Exposição de dados**: `statusLabel` é derivado determinístico de `status`/`statusSnapshot`, já
  públicos para o mesmo consumidor autorizado — nenhum dado novo exposto.
- **Segredos/logs**: nenhum segredo manipulado; nenhum log novo.
- **SQL/persistência/migration**: nenhuma — não há migration nesta feature.
- **Erros e disclosure**: nenhum código de erro novo; nenhuma mudança de comportamento de erro.
- **Dependências novas**: nenhuma.
- **Abuso**: nenhuma superfície nova — campo a mais em respostas de leitura já existentes.

Nenhum achado crítico/alto pendente. Único ponto registrado: inspeção visual do OpenAPI gerado
(`/v3/api-docs`) não foi possível nesta sessão por falta de um MySQL local acessível — não é um achado de
segurança, é uma verificação de documentação adiada (ver Checkpoint 5).

## Evidências de verificação

- `./mvnw -o test -Dtest=ServiceOrderStatusLabelTest` — 2026-09-21, 8 testes (7 casos parametrizados + 1),
  0 falhas.
- `./mvnw -o test -Dtest=ServiceOrderControllerGetStatusTest` — 2026-09-21, 4 testes, 0 falhas.
- `./mvnw -o test -Dtest=ServiceOrderControllerListTest` — 2026-09-21, 16 testes, 0 falhas (confirma
  `statusLabel` correto para os 5 status da listagem padrão, incluindo `AWAITING_ITEMS` → `EXECUCAO`).
- `./mvnw -o test` (suíte completa) — 2026-09-21, 700 testes, 0 falhas, 0 erros, 0 skipped.
- `./mvnw -o verify` (equivalente a `make verify`) — 2026-09-21, `BUILD SUCCESS`; `jacoco:check` — "All
  coverage checks have been met."; `ModuleStructureTest` — 2 testes, 0 falhas, nenhuma fronteira de módulo
  violada.
- `node -e "JSON.parse(...)"` sobre `docs/api/postman/workshop-management-system.postman_collection.json`
  após as edições — JSON válido.

## Rollback ou recuperação

N/A para dado persistido — não há migration nem escrita nova. Reverter o PR remove o campo `statusLabel`
do payload; como é aditivo, nenhum cliente que já ignora campos desconhecidos é afetado por essa reversão.

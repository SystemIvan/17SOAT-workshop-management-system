# Especificação Funcional: Ordenação operacional e exclusão lógica na listagem de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders-sorting` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-19 |
| Referências | RF38; enunciado do Tech Challenge Fase 2 — "Listagem de ordens de serviço"; `docs/features/servicelifecycle/list-service-orders/functional-spec.md` (feature que esta evolui); `.claude/rules/epic-3-service-lifecycle.md` |

## Nota sobre a origem dos requisitos

RF38 é um requisito novo da Fase 2 do Tech Challenge, ainda **não registrado no board do Miro** — essa
atualização do board é uma task separada, sem gate de `functional-spec.md`. Até essa task ser concluída, a
única fonte deste requisito é o enunciado da Fase 2. Os detalhes de regra abaixo (ranking exato de status,
tratamento de `AWAITING_ITEMS`, comportamento com filtro explícito) são decisões de produto tomadas com o
responsável nesta sessão — não uma transcrição de texto já existente em outro documento.

Esta feature **evolui** `list-service-orders` (aprovada em 2026-08-25), cujo "Fora de escopo" já previa
isso: *"Paginação, ordenação (`sort`) e busca textual livre"* ficaram deliberadamente fora daquela feature
"para uma feature futura". RF38 é essa feature futura, restrita a ordenação e exclusão lógica — paginação e
busca textual continuam fora de escopo aqui também (ver seção "Fora de escopo").

## Problema e resultado esperado

Hoje `GET /api/service-orders` (implementado em `list-service-orders`) retorna as ordens de serviço na
ordem em que o banco as devolve, sem nenhum `ORDER BY`, e inclui ordens já `COMPLETED`/`DELIVERED`
misturadas com as ativas quando nenhum filtro de `status` é informado. Um Manager/Service Advisor que abre
a listagem para decidir o que atender agora precisa primeiro filtrar mentalmente (ou via query) o que já
foi concluído, e não tem nenhuma ordenação por urgência operacional — apenas a ordem física dos registros.

Resultado esperado: `GET /api/service-orders`, por padrão (quando nenhum `status` é informado), não traz
ordens `COMPLETED` nem `DELIVERED`, e os resultados vêm ordenados primeiro pelo status operacional (o que
está em execução aparece antes do que está aguardando aprovação, que aparece antes do que está em
diagnóstico, que aparece antes do que acabou de ser recebido) e, dentro do mesmo status, da ordem de
serviço mais antiga para a mais nova. Quando `status` é informado explicitamente (incluindo
`COMPLETED`/`DELIVERED`), a exclusão lógica não se aplica — o filtro explícito sempre prevalece.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Manager / Service Advisor | Abre a listagem sem filtro nenhum e vê primeiro o que está em execução, depois aguardando aprovação, depois em diagnóstico, depois recebida — sem precisar excluir mentalmente o que já foi entregue. |
| Manager / Service Advisor | Dentro do mesmo status (ex. duas OS `IN_DIAGNOSIS`), vê a mais antiga primeiro, para não deixar cliente esperando mais tempo do que o necessário. |
| Manager / Service Advisor | Filtra explicitamente `status=COMPLETED` (ex. para auditoria/relatório) e recebe as OS concluídas normalmente, mesmo sendo excluídas da listagem padrão. |
| Technician | Usa a mesma listagem (sem mudança de autorização) e se beneficia da mesma ordenação ao consultar suas OS atribuídas via `technicianId`. |

## Regras de negócio

1. **Ranking de status para ordenação** (aplica-se sempre, não só na listagem padrão): quando o resultado
   contém ordens de serviço de mais de um status, elas são ordenadas pela seguinte prioridade
   (posição 1 = aparece primeiro):
   1. `IN_PROGRESS` (Execução)
   2. `AWAITING_APPROVAL` (Aguardando Aprovação)
   3. `IN_DIAGNOSIS` (Diagnóstico)
   4. `RECEIVED` (Recebida)
   5. `AWAITING_ITEMS`
   6. `COMPLETED`
   7. `DELIVERED`

   Os 4 primeiros níveis vêm literalmente do enunciado da Fase 2. **Decisão aprovada nesta spec:**
   `AWAITING_ITEMS` fica logo após `RECEIVED` (nível 5) porque é um status de execução bloqueada por falta
   de peça — ainda operacionalmente relevante, mas sem o refinamento explícito do enunciado (mesma lacuna
   já registrada em RF39, que trata do mapeamento externo dos 6 nomes nominais; esta spec só decide o
   *ranking interno de ordenação*, não o nome exposto). `COMPLETED` e `DELIVERED` ficam nos últimos níveis
   apenas para o caso em que aparecem via filtro explícito (regra 3) — na listagem padrão elas nunca
   aparecem (regra 2).
2. **Exclusão lógica por padrão**: quando a requisição **não informa** o parâmetro `status`, o resultado
   nunca inclui ordens de serviço com `statusSnapshot` igual a `COMPLETED` ou `DELIVERED`. Os demais
   filtros (`customerId`, `technicianId`, `priority`) continuam sendo aplicados normalmente em conjunto com
   essa exclusão.
3. **Filtro explícito de status desativa a exclusão**: quando a requisição informa `status=COMPLETED` ou
   `status=DELIVERED` (ou qualquer outro valor), a exclusão lógica da regra 2 não se aplica — o
   comportamento já existente de `list-service-orders` (retornar exatamente as OS daquele status) não muda.
4. **Ordenação secundária — mais antiga primeiro**: dentro do mesmo nível de status (regra 1), as ordens de
   serviço são ordenadas da mais antiga para a mais nova, por data de criação da ordem de serviço.
   **Achado técnico relevante para a spec técnica**: `ServiceOrder` (domínio) e `ServiceOrderJpaEntity`
   (persistência) hoje **não têm nenhum campo de timestamp de criação** (`createdAt` ou equivalente) — não
   é uma lacuna de contrato HTTP, é uma lacuna de modelo. Implementar esta regra exige introduzir esse
   campo (schema + domínio), o que a `technical-spec.md` deve endereçar explicitamente, incluindo a
   migração Flyway correspondente (classificação: mudança de schema, não seed — `AGENTS.md` §"Persistent
   data and seeds"). Não escondo isso como detalhe de implementação porque afeta o desenho da migração e
   pode exigir uma estratégia de backfill para ordens de serviço já existentes em ambientes com dado
   real/demo.
5. **Sem novo parâmetro de ordenação**: a ordenação desta feature é sempre aplicada (não é opt-in via
   `sort=`) e não é configurável pelo cliente da API nesta feature — ver "Fora de escopo".
6. **Autorização**: reaproveita a regra já existente para `/api/service-orders/**`
   (`MANAGER`, `TECHNICIAN`, `ADMIN`) — nenhuma mudança de matriz de autorização.
7. **Sem alteração de estado**: como a listagem original, é uma operação somente-leitura; não dispara
   transições de `statusSnapshot` nem eventos de domínio.
8. **Compatibilidade**: os filtros existentes (`status`, `customerId`, `technicianId`, `priority`) e o
   payload por item (`ServiceOrderResponse`, idêntico ao de `GET /api/service-orders/{id}`) não mudam —
   esta feature só adiciona ordenação e exclusão lógica por cima do comportamento já aprovado.

## Fora de escopo

- Paginação (`page`/`size`) e busca textual livre — continuam fora de escopo, como já registrado em
  `list-service-orders`.
- Parâmetro de ordenação customizável pelo cliente (`sort=`, `order=`) — a ordenação desta feature é fixa
  e sempre aplicada; permitir ao cliente escolher outra ordenação é uma feature separada, se necessário.
- O mapeamento dos 7 valores internos de `ServiceOrderStatus` para os 6 nomes nominais do enunciado da
  Fase 2 (Recebida, Diagnóstico, Aguardando Aprovação, Execução, Finalizada, Entregue) — isso é RF39,
  tratado em spec própria. Esta feature só decide o *ranking interno* de `AWAITING_ITEMS` para fins de
  ordenação (regra de negócio 1), não o nome exposto ao consumidor externo.
- Expor `createdAt` como campo do payload `ServiceOrderResponse` — o achado técnico da regra 4 exige o
  campo internamente para ordenar, mas expor esse campo na resposta HTTP é decisão de produto separada,
  fora desta spec (a `technical-spec.md` decide se o campo fica só interno ou também é exposto, mas expô-lo
  não é um requisito desta feature).
- Estratégia de backfill de `createdAt` para ordens de serviço já persistidas em ambientes com dado real —
  a `technical-spec.md` decide a abordagem (ex.: usar timestamp da migração como valor de fallback), esta
  spec só sinaliza que a lacuna existe.
- Mudanças no endpoint de detalhamento (`GET /api/service-orders/{id}`) ou no de status
  (`GET /api/service-orders/{id}/status`) — ambos fora do escopo desta feature.

## Critérios de aceite

- [ ] `GET /api/service-orders` sem filtro de `status` nunca retorna ordens com `statusSnapshot`
      `COMPLETED` ou `DELIVERED`.
- [ ] `GET /api/service-orders` sem filtro de `status`, com OS em `IN_PROGRESS`, `AWAITING_APPROVAL`,
      `IN_DIAGNOSIS`, `RECEIVED` e `AWAITING_ITEMS` simultaneamente, retorna nessa ordem de status
      (`IN_PROGRESS` primeiro, depois `AWAITING_APPROVAL`, depois `IN_DIAGNOSIS`, depois `RECEIVED`, depois
      `AWAITING_ITEMS`).
- [ ] Duas ou mais ordens de serviço no mesmo status aparecem ordenadas da mais antiga para a mais nova.
- [ ] `GET /api/service-orders?status=COMPLETED` retorna as ordens `COMPLETED` normalmente (exclusão não se
      aplica quando o filtro é explícito).
- [ ] `GET /api/service-orders?status=DELIVERED` retorna as ordens `DELIVERED` normalmente.
- [ ] Combinar `status=IN_PROGRESS` com `customerId`/`technicianId`/`priority` continua aplicando AND entre
      os filtros, como já implementado em `list-service-orders`.
- [ ] A ordenação por antiguidade (regra 4) dentro de um mesmo status se mantém estável mesmo quando o
      resultado é filtrado por `customerId`, `technicianId` ou `priority`.
- [ ] Nenhum novo parâmetro de query é introduzido; requisições que já funcionavam com os filtros
      existentes continuam funcionando sem mudança de contrato.
- [ ] `GET /api/service-orders/{id}` e `GET /api/service-orders/{id}/status` continuam se comportando
      exatamente como hoje (sem regressão).

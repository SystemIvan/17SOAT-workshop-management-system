# Especificação Funcional: Listagem e detalhamento de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-25 |
| Referências | RF34 (enunciado do Tech Challenge — "Gestão administrativa: ... Listagem e detalhamento de ordens de serviço"; e board Miro, "Levantamento de Requisitos e Refinamento Técnico" §3.3 Service Lifecycle - Execução e Tracking, item 7, mesmo título, sem refinamento adicional); `docs/Architecture.md` §"Gaps" ("Listar/detalhar Service Orders ... Contratos e filtros não foram identificados"); `docs/features/servicelifecycle/track-execution/functional-spec.md` (detalhamento e `statusSnapshot` já implementados via `GET /api/service-orders/{id}`); `.claude/rules/epic-3-service-lifecycle.md` |

## Nota sobre a origem dos requisitos

RF34 existe em apenas dois lugares — o enunciado do Tech Challenge e o item 7 de §3.3 do quadro
"Levantamento de Requisitos e Refinamento Técnico" no Miro — e em ambos aparece só como o título
"Listagem e detalhamento de ordens de serviço", sem refinamento, critérios de aceite ou definição de
filtros. Não existe um terceiro texto de RF a ser consultado no board. Todos os detalhes de filtro,
paginação e formato de payload abaixo (seção "Regras de negócio") foram decisões de produto tomadas
diretamente com o responsável (Santiago Silvestre) nesta sessão, e são a fonte de verdade para esta
feature — não uma suposição pendente de confirmação em outro lugar do board.

## Problema e resultado esperado

Hoje o sistema já expõe o detalhamento completo de uma `ServiceOrder` por ID
(`GET /api/service-orders/{id}`, implementado e documentado em
`docs/features/servicelifecycle/track-execution/functional-spec.md`), mas não existe nenhum endpoint
que devolva uma coleção de ordens de serviço. Um Manager/Service Advisor (e um Technician, dentro do
mesmo papel de acesso já usado para o restante de `/api/service-orders/**`) precisa localizar rapidamente
as ordens de serviço relevantes — por status, por cliente, por técnico responsável ou por prioridade —
sem precisar conhecer os IDs de antemão.

Resultado esperado: `GET /api/service-orders` retorna a lista de ordens de serviço existentes, cada uma
no mesmo formato completo já retornado por `GET /api/service-orders/{id}` (`ServiceOrderResponse`,
incluindo `vehicleSnapshot` e a lista de `ServiceExecution`s), opcionalmente filtrada por um ou mais
critérios combináveis (`status`, `customerId`, `technicianId`, `priority`, aplicados com AND quando mais
de um é informado). O detalhamento por ID (`GET /api/service-orders/{id}`) não muda: esta feature cobre
apenas a listagem que faltava.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Manager / Service Advisor | Lista todas as ordens de serviço ativas para ter visão geral da operação. |
| Manager / Service Advisor | Filtra ordens de serviço por `status` (ex.: só as `IN_DIAGNOSIS`) para priorizar o trabalho do dia. |
| Manager / Service Advisor | Filtra ordens de serviço de um `customerId` específico para atender uma ligação do cliente. |
| Manager / Service Advisor | Filtra ordens de serviço de um `technicianId` específico para ver a carga de trabalho de um técnico. |
| Manager / Service Advisor | Combina `status` + `priority` para achar o que está `AWAITING_APPROVAL` e é `HIGH`/`URGENT`. |
| Technician | Usa a mesma listagem (o papel já tem acesso a todo `/api/service-orders/**`) para ver as ordens sob sua responsabilidade, tipicamente combinando `technicianId=<o próprio id>`. |

## Regras de negócio

1. **Endpoint**: `GET /api/service-orders`. Sem corpo de requisição.
2. **Sem paginação**: retorna um array JSON simples (`[]` quando vazio), no mesmo padrão das demais
   listagens do projeto (`GET /api/vehicles`, `GET /api/customers`, `GET /api/catalog-services`). Não
   introduz `page`/`size` nesta feature.
3. **Payload por item**: idêntico ao de `GET /api/service-orders/{id}` (`ServiceOrderResponse`) — mesmo
   DTO, mesmo mapeamento, mesmos campos (`id`, `customerId`, `vehicleId`, `vehicleSnapshot`, `priority`,
   `initialAssessment`, `status`/`statusSnapshot`, `diagnosisAssigneeId`, `executions[]` com o detalhe de
   cada `ServiceExecution`). Não existe DTO resumido separado para a listagem.
4. **Filtros (query params), todos opcionais e combináveis com AND**:
   - `status`: um valor de `ServiceOrderStatus` (`RECEIVED`, `IN_DIAGNOSIS`, `AWAITING_APPROVAL`,
     `AWAITING_ITEMS`, `IN_PROGRESS`, `COMPLETED`, `DELIVERED`). Compara contra o `statusSnapshot`
     persistido — a mesma projeção lida em `GET /{id}` e `GET /{id}/status`, sem recomputar em leitura
     (AD-010, ver `.claude/rules/epic-3-service-lifecycle.md`).
   - `customerId`: UUID; retorna as ordens de serviço cujo `customerId` seja igual.
   - `technicianId`: UUID; retorna as ordens de serviço em que o técnico é o `diagnosisAssigneeId` da SO
     **ou** o `assignedTechnicianId` de pelo menos uma `ServiceExecution` da SO. Não valida se o
     `technicianId` corresponde a um Technician existente (mesmo padrão de referência apenas por UUID já
     usado no restante do módulo).
   - `priority`: um valor de `Priority` (`LOW`, `NORMAL`, `HIGH`, `URGENT`).
5. **Parâmetro inválido**: um `status` ou `priority` fora do enum, ou um `customerId`/`technicianId` que
   não seja um UUID válido, retorna `400 VALIDATION_ERROR` (mesmo padrão de erro do restante da API) —
   não retorna lista vazia silenciosamente.
6. **Sem resultados**: filtro válido que não bate com nenhuma ordem de serviço retorna `200` com array
   vazio, não `404`.
7. **Autorização**: reaproveita a regra já existente para `/api/service-orders/**`
   (`MANAGER`, `TECHNICIAN`, `ADMIN`) em `SecurityConfig` — nenhuma mudança de matriz de autorização é
   necessária nesta feature.
8. **Sem alteração de estado**: é uma operação somente-leitura; não dispara transições de
   `statusSnapshot` nem eventos de domínio.

## Fora de escopo

- Paginação, ordenação (`sort`) e busca textual livre.
- DTO resumido/enxuto para a listagem (fica para uma feature futura, se o volume de dados justificar).
- Filtro por intervalo de datas (`createdAt`/`updatedAt`) — a agregação atual não expõe esses campos no
  `ServiceOrderResponse`; adicioná-los é decisão de outra feature.
- Qualquer restrição de visibilidade por dono/atribuição (ex.: um Technician só ver as SOs atribuídas a
  ele por padrão) — o filtro `technicianId` é opt-in, não uma regra de autorização por recurso.
- Mudanças no endpoint de detalhamento (`GET /api/service-orders/{id}`) ou no de status
  (`GET /api/service-orders/{id}/status`) — ambos já implementados e fora do escopo desta feature.
- Contagem/estatísticas agregadas (ex.: total por status) — não pedido no enunciado.

## Critérios de aceite

- [ ] `GET /api/service-orders` sem query params retorna `200` com todas as ordens de serviço
      existentes, cada uma no formato completo de `ServiceOrderResponse`.
- [ ] `GET /api/service-orders` em uma base sem nenhuma ordem de serviço retorna `200` com `[]`.
- [ ] `?status=<valor válido>` retorna somente as ordens de serviço com aquele `statusSnapshot`.
- [ ] `?customerId=<uuid>` retorna somente as ordens de serviço daquele cliente.
- [ ] `?technicianId=<uuid>` retorna as ordens de serviço em que o técnico é o `diagnosisAssigneeId` ou
      está `assignedTechnicianId` em ao menos uma execução.
- [ ] `?priority=<valor válido>` retorna somente as ordens de serviço com aquela prioridade.
- [ ] Combinar dois ou mais filtros aplica AND entre eles (ex.: `status=IN_PROGRESS&priority=HIGH` só
      retorna o que atende as duas condições).
- [ ] `?status=NAO_EXISTE` (ou `priority` inválido) retorna `400 VALIDATION_ERROR`.
- [ ] `?customerId=nao-e-um-uuid` (ou `technicianId` inválido) retorna `400 VALIDATION_ERROR`.
- [ ] Requisição sem token, ou com token de um papel fora de `MANAGER`/`TECHNICIAN`/`ADMIN`, é rejeitada
      pela mesma regra de autorização já aplicada a `/api/service-orders/**`.
- [ ] `GET /api/service-orders/{id}` continua se comportando exatamente como hoje (sem regressão).

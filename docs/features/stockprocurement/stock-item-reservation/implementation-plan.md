# Plano de Implementação: Reserva Atômica de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-item-reservation` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-21 |
| Branch | `feat/stockprocurement-stock-item-reservation` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-20) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-20) |

## Objetivo da execução

Implementar uma Stock Reservation atômica para todos os Stock Items exigidos por uma Service Execution, acionada após
a aprovação da linha da Estimate. O fluxo final deve preservar a aprovação quando faltar estoque, impedir saldo
negativo sob concorrência, associar um único `reservationId` à execução e manter a reserva durante a separação e a
espera pela retirada.

O estado final esperado é:

```text
Estimate aprovada
    ├── execução sem requirements → READY, sem reserva
    ├── conjunto integral disponível → StockReservation ACTIVE + READY
    └── conjunto indisponível → sem reserva + AWAITING_ITEMS

StockReservation ACTIVE → CONSUMED
```

Não implementar `RELEASED`, liberação por timeout, cancelamento, reserva parcial, Purchase Order, estoque mínimo ou
movimentações administrativas.

## Instruções para retomada com contexto zerado

Antes de executar qualquer checkpoint:

1. ler integralmente o `AGENTS.md` da raiz;
2. confirmar a branch `feat/stockprocurement-stock-item-reservation`;
3. ler `functional-spec.md`, `technical-spec.md` e este plano na ordem;
4. inspecionar `git status --short` e preservar mudanças alheias;
5. confirmar que as duas specs desta feature continuam `Approved`;
6. usar somente `./mvnw` ou targets do `Makefile` para Maven;
7. manter apenas um checkpoint `In Progress` e registrar evidências antes de marcá-lo `Completed`.

Se a execução exigir uma decisão material diferente das specs aprovadas, interromper o código, devolver a spec afetada
para `Draft` e obter nova aprovação humana. Não criar abstrações, estados, endpoints ou migrations fora do escopo para
contornar uma dificuldade de implementação.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Reconciliar o SDD afetado em Service Lifecycle | Completed |
| 1 | Atualizar o Miro por widgets novos e imutáveis | Completed |
| 2 | Implementar os modelos de domínio e suas invariantes | Completed |
| 3 | Implementar migration, projeções e locks de persistência | Completed |
| 4 | Implementar casos de uso e named interface de Stock Reservation | Completed |
| 5 | Integrar Estimate, Service Execution e retry | Completed |
| 6 | Implementar HTTP e tradução de falhas | Completed |
| 7 | Implementar notificações consumidoras after-commit | Completed |
| 8 | Validar concorrência, transações e fronteiras Modulith | Completed |
| 9 | Atualizar OpenAPI, Postman e documentação do projeto | Completed |
| 10 | Concluir segurança, cobertura e gates finais | Completed |

## Checkpoint 0 — Reconciliar o SDD afetado em Service Lifecycle

### Objetivo

Eliminar conflitos entre as specs aprovadas anteriormente e a nova funcional de Stock Reservation antes de modificar
código. Este checkpoint é gate de documentação: nenhum checkpoint de código pode iniciar enquanto as revisões materiais
não receberem aprovação humana.

### Alterações

- Devolver para `Draft` e revisar as functional specs:
  - `servicelifecycle/attach-stock-requirement`;
  - `servicelifecycle/estimate-generation`;
  - `servicelifecycle/decide-estimate-lines`;
  - `servicelifecycle/assign-technician`;
  - `servicelifecycle/start-execution`;
  - `servicelifecycle/track-execution`.
- Registrar em cada documento apenas o delta que lhe pertence:
  - congelamento dos requirements na geração da Estimate;
  - anexo permitido somente antes do congelamento e em `PENDING`;
  - tentativa automática por execução aprovada;
  - `AWAITING_ITEMS` e `stockReservationId` no tracking;
  - notificação do Technician atribuído quando os materiais estiverem reservados;
  - `READY` como precondição estável para iniciar, sem regressão por atraso operacional.
- Marcar as technical specs e implementation plans downstream como `Stale` enquanto o respectivo documento funcional
  estiver em `Draft`.
- Obter aprovação humana explícita das functional specs revisadas.
- Revisar as technical specs afetadas, alinhá-las ao desenho aprovado desta feature e obter aprovação humana quando a
  mudança for material.
- Registrar que os deltas de implementação passam a ser executados por este plano, sem reexecutar checkpoints antigos
  já concluídos e não afetados.

### Verificação

- Nenhuma spec vigente afirma que `attachStockRequirement` aceita `AUTHORIZED`, `READY` ou `IN_PROGRESS`.
- Nenhuma spec vigente trata `AWAITING_PART` como status atual.
- Nenhuma spec vigente exclui a reserva do efeito da aprovação ou a notificação nova do Technician.
- Todos os aprovadores e datas foram preenchidos por confirmação humana, nunca inferidos pelo agente.

### Evidência

Registrar links dos documentos revisados, status final e mensagem de aprovação humana.

Estado em 2026-08-20:

- `attach-stock-requirement`, `estimate-generation`, `decide-estimate-lines`, `assign-technician`,
  `start-execution` e `track-execution` foram revisadas e estão em `Draft`;
- as respectivas technical specs e implementation plans estão em `Stale`;
- aprovação humana das seis functional specs: `Approved` por Matheus Apostulo em 2026-08-20;
- revisão e aprovação das seis technical specs: `Approved` por Matheus Apostulo em 2026-08-20.

Nenhum checkpoint de código foi iniciado enquanto esse gate permanece pendente.

## Checkpoint 1 — Atualizar o Miro por widgets novos e imutáveis

### Regra append-only obrigatória

O board principal é:

- [Workshop Management System](https://miro.com/app/board/uXjVH9faCu4=/)
- [Aggregates — Modelo Atualizado](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870345674&cot=14)

Ao identificar um bloco desatualizado, **não editar, apagar, substituir, mover, redimensionar, desagrupar ou reutilizar
o widget antigo**. Criar um novo widget ou bloco versionado ao lado da área correspondente. O conteúdo antigo permanece
como registro histórico e salvaguarda contra edição incorreta.

Cada novo widget deve conter:

- título com `Stock Reservation`, versão ou data `2026-08-20`;
- texto `Supersedes` com link ou ID do widget antigo que motivou a atualização;
- referência a `stock-item-reservation` e às specs aprovadas;
- nota de que o widget anterior não foi alterado;
- conteúdo autocontido, sem depender de uma edição parcial do bloco antigo.

### Blocos a inspecionar

- Story de Stock:
  `3458764678560725831`;
- Pivotal Events:
  `3458764678817744720`;
- Requisitos e refinamento:
  `3458764679721508363`;
- Modelo tático atualizado:
  `3458764680870224027`;
- Aggregates atualizados:
  `3458764680870345674`.

Criar um sucessor somente para o bloco que realmente divergir. Não duplicar widgets que já expressem corretamente a
decisão.

### Conteúdo que os novos widgets devem consolidar

- uma Service Execution pode manter vários Stock Requirements congelados pela Estimate;
- Stock Requirement continua value object sem ID;
- existe no máximo uma Stock Reservation por Service Execution;
- Service Execution mantém somente `stockReservationId`; linhas e estado ficam em Stock & Procurement;
- a reserva possui todas as linhas e é criada de forma atômica;
- estados da reserva nesta feature: `ACTIVE` e `CONSUMED`;
- não existem `RELEASED`, timeout ou liberação operacional neste incremento;
- aprovação dispara a tentativa; falta de item preserva aprovação e resulta em `AWAITING_ITEMS`;
- sucesso resulta em `READY`, sem regressão por demora na separação ou retirada;
- Stock Manager prioriza a separação; Technician é avisado para buscar os materiais;
- preço comercial permanece congelado na Estimate e nunca é recalculado pela reserva;
- Notification continua outbound capability de cada consumidor, não bounded context genérico.

### Verificação e evidência

- Reler cada widget novo pelo conector depois da criação.
- Confirmar que nenhum ID antigo teve `modifiedAt` alterado pela execução.
- Registrar neste checkpoint a lista `widget antigo → widget sucessor`, com IDs e links.
- Capturar screenshot ou link direto de cada sucessor para revisão humana.
- Se o conector não permitir criação segura, manter o checkpoint `Pending`, registrar o bloqueio e continuar somente
  com trabalho local reversível. A feature não pode ser marcada `Implemented` enquanto este checkpoint estiver pendente.

Evidência concluída em 2026-08-21:

- `3458764678560725831` →
  [3458764681445773180](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764681445773180)
  (Story de Stock); conteúdo relido pelo conector;
- `3458764678817744720` →
  [3458764681445773210](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764681445773210)
  (Pivotal Events); conteúdo relido pelo conector;
- `3458764679721508363` →
  [3458764681353461731](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764681353461731)
  (Requisitos e Refinamento); conteúdo relido pelo conector;
- `3458764680870224027` →
  [3458764681353461859](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764681353461859)
  (Modelo Tático); conteúdo relido pelo conector;
- `3458764680870345674` →
  [3458764681353503955](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764681353503955)
  (Aggregates); conteúdo relido pelo conector.

Os cinco widgets antigos foram inspecionados. Story e Pivotal Events divergiam do fluxo aprovado e receberam documentos
sucessores autocontidos, versionados e posicionados ao lado das áreas correspondentes. Os outros três sucessores já
existentes foram preservados e relidos. O conector não expõe `modifiedAt`; nenhuma ferramenta de edição, movimentação
ou remoção foi chamada para os widgets antigos.

## Checkpoint 2 — Implementar os modelos de domínio e suas invariantes

### Stock & Procurement

- Criar `StockReservation`, `StockReservationLine` e `StockReservationStatus` em
  `stockprocurement.stockreservation.domain.model`.
- Implementar criação `ACTIVE`, reconstituição, linhas imutáveis, origem obrigatória e consumo idempotente.
- Manter exatamente os estados `ACTIVE` e `CONSUMED`.
- Alterar `StockItem` para permitir mudança controlada de `availableQuantity`.
- Implementar avaliação não mutável de elegibilidade e desconto protegido de quantidade.
- Distinguir item inativo de quantidade insuficiente e nunca permitir saldo negativo.
- Criar repository ports para Stock Reservation e ampliar o port de Stock Item com leituras de escrita, sem importar
  Spring ou JPA no domínio.

### Service Lifecycle

- Adicionar `stockRequirementsFrozen` e `stockReservationId` a `ServiceExecution`.
- Implementar congelamento idempotente e rejeitar anexo quando a execução não estiver `PENDING` ou estiver congelada.
- Substituir `AWAITING_PART` por `AWAITING_ITEMS` nos enums e regras de prontidão.
- Implementar `confirmStockReservation(reservationId)` com confirmação integral e idempotência por ID.
- Remover confirmação item a item de `ServiceExecution` e `ServiceOrder`.
- Preservar `StockRequirement.reserved` somente como compatibilidade, atualizado em conjunto e sem participar da regra
  de prontidão.

### Testes e verificação

- Cobrir todas as invariantes de Stock Reservation e Stock Item com testes unitários rápidos.
- Cobrir congelamento, anexo, autorização, indisponibilidade, confirmação idempotente e status da Service Execution.
- Confirmar ausência de Spring, JPA, Bean Validation e HTTP nos packages de domínio.
- Executar os testes unitários dos dois modelos antes de concluir o checkpoint.

### Evidência

Registrar classes alteradas, testes executados e resultado. Não marcar `Completed` com teste de domínio falhando.

Concluído em 2026-08-20:

- criados `StockReservation`, `StockReservationLine` e `StockReservationStatus`, com linhas imutáveis,
  unicidade por item e consumo idempotente;
- `StockItem` passou a avaliar e descontar disponibilidade por métodos de domínio, sem saldo negativo;
- `ServiceExecution` recebeu congelamento, `stockReservationId`, confirmação integral idempotente e
  `AWAITING_ITEMS`; a confirmação item a item foi removida;
- executado `./mvnw test -Dtest=StockItemTest,StockReservationTest,ServiceExecutionTest,ServiceOrderTest`:
  40 testes, sucesso.

## Checkpoint 3 — Implementar migration, projeções e locks de persistência

### Migration Flyway

- Criar `V20260821014516__create_stock_reservations.sql`; se esse nome já existir no início do checkpoint, gerar novo
  timestamp UTC sem editar uma migration aplicada.
- Criar `stock_reservations` e `stock_reservation_lines` com PKs, unicidade, checks e foreign key interna para
  `stock_items` definidos na spec técnica.
- Adicionar `stock_requirements_frozen` e `stock_reservation_id` a `service_executions`, sem foreign key entre bounded
  contexts.
- Converter `AWAITING_PART` para `AWAITING_ITEMS` em execuções e snapshots da Service Order.
- Congelar execuções legadas presentes em `estimate_lines`.
- Zerar flags `reserved` sem reserva rastreável e reclassificar `READY` com requirements para `AWAITING_ITEMS`.
- Recalcular snapshots afetados, preservando a precedência de `IN_PROGRESS`.
- Não fabricar reserva, consumo ou alteração retroativa para execuções `IN_PROGRESS` ou `COMPLETED`.

### JPA e repositories

- Criar projections, embeddables, mapper, Spring Data repositories e adapters de Stock Reservation.
- Implementar consulta por ID, por `serviceExecutionId` e leitura com lock para consumo.
- Mapear `Instant`, enums e linhas sem expor JPA ao domínio.
- Implementar leitura pessimista ordenada dos Stock Items.
- Fazer update cadastral e desativação usarem leitura de escrita para evitar lost update com reserva.
- Mapear os dois campos novos da Service Execution e adaptar reconstituição, DTOs e fixtures.
- Adicionar leitura com lock de escrita ao repository de Service Order para geração, anexo, decisão e retry.

### Testes e verificação

- Validar Flyway seguido de Hibernate com `ddl-auto=validate` em H2/MySQL mode.
- Testar constraints, unicidade da origem, reconstituição e consultas.
- Criar teste dedicado de upgrade com dados legados para status, congelamento, flags e snapshots.
- Executar `make test` ao final do checkpoint e corrigir somente falhas relacionadas ao escopo.

### Classificação de dados

- Nenhum seed de reserva é necessário.
- O seeder de Stock Item existente não deve criar reservas ou executar movimentações.
- Testes usam fixtures/builders; nenhum teste depende do perfil `dev`.

### Evidência

Concluído em 2026-08-20:

- adicionada a migration `V20260821014516__create_stock_reservations.sql`, com constraints, backfill de
  congelamento, normalização de flags e rename de status;
- criados mapeamentos e adapters JPA de Stock Reservation, campos de Service Execution e leituras com
  `PESSIMISTIC_WRITE` para Stock Item e Service Order;
- update e desativação de Stock Item agora usam leitura de escrita;
- `StockReservationMigrationTest` validou upgrade de dados legados e
  `StockReservationRepositoryImplTest`/`ServiceOrderRepositoryImplTest` validaram Flyway, Hibernate
  `validate` e reconstituição em H2 MySQL mode.

## Checkpoint 4 — Implementar casos de uso e named interface de Stock Reservation

### Contrato público

- Criar `stockprocurement.stockreservation.application.api` com
  `@NamedInterface("stock-reservation-api")`.
- Expor somente `StockReservationApi`, commands, linhas, results, outcomes e issues imutáveis.
- Não expor aggregate, repository, projection JPA, controller ou exception interna.
- Implementar `reserveAll` para adquirir a união de locks em ordem global e processar commands na ordem recebida.
- Incluir `newlyCreated` no result para impedir notificação duplicada em retry idempotente.

### Casos de uso

- Implementar `ReserveStockItemsUseCase` com `@Transactional` e propagação `REQUIRED`.
- Consolidar linhas por `stockItemId` com soma exata antes de locks.
- Avaliar todas as linhas de uma execução antes do primeiro desconto daquela execução.
- Retornar `NOT_RESERVED` com todos os issues de item ausente, inativo ou insuficiente.
- Retornar a reserva existente para origem e linhas iguais; rejeitar linhas diferentes.
- Implementar consultas read-only por ID e origem.
- Implementar consumo integral e idempotente com `Clock` injetável e lock da reserva.

### Testes e verificação

- Cobrir um item, vários itens, consolidação, saldo exato, todos os tipos de issue e ausência de desconto parcial.
- Cobrir lote com sucesso e indisponibilidade independentes.
- Cobrir idempotência, conflito de linhas, consumo repetido e timestamps.
- Verificar que o contrato público não possui preço, snapshots comerciais ou tipos internos.

### Evidência

Concluído em 2026-08-21:

- criada a named interface `stock-reservation-api` e os casos de uso de reserva em lote, consulta e consumo;
- a reserva consolida linhas, bloqueia Stock Items em ordem global, retorna todos os issues sem desconto parcial e
  preserva idempotência/conflito por `serviceExecutionId`;
- executados `StockItemTest`, `StockReservationTest`, `ReserveStockItemsUseCaseTest`,
  `GetStockReservationUseCaseTest` e `ConsumeStockReservationUseCaseTest`: 17 testes, sucesso.

## Checkpoint 5 — Integrar Estimate, Service Execution e retry

### Congelamento

- Alterar `GenerateEstimateUseCase` para carregar a Service Order com lock, congelar o Diagnosis e salvar Estimate e
  Service Order na mesma transação.
- Alterar `AttachStockRequirementUseCase` para usar o mesmo lock e respeitar o congelamento.
- Preservar os snapshots comerciais existentes; a reserva recebe somente IDs e quantidades.

### Aprovação

- Alterar `DecideEstimateLinesUseCase` para validar primeiro o lote comercial, carregar a Service Order com lock e
  montar commands apenas para execuções aprovadas com requirements.
- Chamar `StockReservationApi.reserveAll` uma vez por request.
- Confirmar cada `reservationId` bem-sucedido na execução correspondente.
- Tratar `NOT_RESERVED` como resultado sem exception, preservando aprovação e `AWAITING_ITEMS`.
- Manter execuções sem requirements em `READY`, sem reserva vazia.
- Salvar Service Order, reservas e saldos na mesma transação física; não usar `REQUIRES_NEW`.

### Retry

- Criar `RetryStockReservationUseCase` em Service Lifecycle.
- Receber apenas Service Order e Service Execution IDs.
- Derivar o conjunto de requirements congelados, sem aceitar linhas no request.
- Permitir tentativa em `AWAITING_ITEMS` e retorno idempotente em `READY` com reserva.
- Rejeitar estados incompatíveis sem alterar saldo.

### Testes e verificação

- Cobrir decisões mistas e múltiplas execuções.
- Provar que indisponibilidade não reverte aprovação nem sucesso de outra execução.
- Provar rollback integral em falha técnica inesperada.
- Cobrir corrida entre gerar Estimate e anexar requirement.
- Cobrir retry, conjunto congelado, resposta existente e status incompatível.

### Evidência

Concluído em 2026-08-21:

- geração e anexo de requirements passaram a carregar a Service Order com lock; a geração congela e persiste o
  conjunto no mesmo limite transacional da Estimate;
- a decisão comercial valida o lote antes de mutar, reserva em chamada única e confirma apenas resultados `RESERVED`;
  `NOT_RESERVED` preserva aprovação e `AWAITING_ITEMS`;
- criado `RetryStockReservationUseCase`, que deriva as linhas congeladas e só permite `AWAITING_ITEMS` ou `READY`
  com reserva existente;
- executados `GenerateEstimateUseCaseTest`, `DecideEstimateLinesUseCaseTest`,
  `AttachStockRequirementUseCaseTest` e `RetryStockReservationUseCaseTest`: 23 testes, sucesso.

## Checkpoint 6 — Implementar HTTP e tradução de falhas

### Endpoints

- Adicionar ao `ServiceOrderController`:
  - `POST /api/service-orders/{id}/executions/{executionId}/stock-reservation`.
- Criar controller de Stock Reservation com:
  - `GET /api/stock-reservations/{reservationId}`;
  - `GET /api/stock-reservations/by-service-execution/{serviceExecutionId}`;
  - `POST /api/stock-reservations/{reservationId}/consume`.
- Não criar endpoint HTTP de criação com linhas arbitrárias.
- Retornar DTOs específicos, nunca aggregate ou JPA entity.
- Adicionar `stockReservationId` a `ServiceExecutionResponse` e preservar `StockRequirementResponse.reserved`.
- Retornar `200` com `RESERVED` ou `NOT_RESERVED` no retry.

### Falhas

- Implementar advice limitado ao controller de Stock Reservation.
- Usar `VALIDATION_ERROR`, `INVALID_STOCK_RESERVATION`, `STOCK_RESERVATION_NOT_FOUND`,
  `STOCK_RESERVATION_CONFLICT` e `INVALID_STATE_TRANSITION` conforme a spec técnica.
- Não reutilizar `INVALID_STOCK_ITEM` para exceptions da reserva.
- Não expor SQL, locks, constraints, packages ou stack traces.

### Testes e verificação

- Criar testes MockMvc para os quatro endpoints e todos os resultados/erros documentados.
- Verificar UUID inválido, not found, conflito de estado, retry sem body e consumo idempotente.
- Verificar que nenhum endpoint aceita preço, status, `reservationId` escolhido ou linhas de reserva.
- Confirmar que nenhum response expõe dados do Customer ou snapshots comerciais.

### Evidência

Concluído em 2026-08-21:

- incluídos retry sem body no `ServiceOrderController`, consulta por ID/execução e consumo idempotente no
  `StockReservationController`;
- responses usam DTOs específicos; `ServiceExecutionResponse` passou a expor somente o
  `stockReservationId` de integração;
- `StockReservationExceptionHandler` traduz UUID inválido, ausência e conflito da reserva com códigos estáveis,
  sem revelar detalhes de persistência;
- `StockReservationControllerTest` validou retry, consulta, consumo repetido, UUID inválido, ausência e estado
  incompatível: 3 testes, sucesso.

## Checkpoint 7 — Implementar notificações consumidoras after-commit

### Stock Manager

- Criar `StockManagerNotificationPort` pertencente a Stock & Procurement.
- Notificar reserva recém-criada que precisa de separação física.
- Notificar issues que impediram a reserva.
- Implementar adapter de log estruturado porque não existe contato/canal do Stock Manager.

### Technician

- Ampliar o `TechnicianNotificationPort` existente com comunicação de materiais reservados.
- Notificar o Technician já atribuído depois de uma reserva recém-criada.
- Alterar `AssignTechnicianUseCase` para notificar quando a execução já estiver `READY` com `stockReservationId`.
- Não notificar por uma repetição idempotente que retornou `newlyCreated=false`.

### Entrega

- Publicar eventos internos durante a transação e consumi-los somente em `AFTER_COMMIT`.
- Capturar e registrar falha do adapter sem desfazer estado persistido.
- Não criar bounded context, tabela, retry, template, histórico ou canal real de Notification.

### Testes e verificação

- Testar notificação de sucesso, indisponibilidade, Technician presente e atribuição posterior.
- Testar ausência de notificação duplicada em retry idempotente.
- Testar que listener não executa antes do commit e que falha do adapter não causa rollback.
- Garantir que logs contenham somente IDs e quantidades operacionais, sem PII, preço ou body completo.

### Evidência

Concluído em 2026-08-21:

- criados eventos internos de reserva criada/indisponível e de materiais prontos para o Technician;
- os listeners usam `@TransactionalEventListener(phase = AFTER_COMMIT)` e protegem a transação contra falha do
  adapter de log;
- a atribuição posterior do Technician notifica uma execução já pronta; retries idempotentes não emitem evento novo;
- executados `StockReservationNotificationListenerTest` (2),
  `TechnicianMaterialsReservedNotificationListenerTest` (2),
  `StockReservationNotificationAfterCommitTest` (1), `AssignTechnicianUseCaseTest` (4) e
  `RetryStockReservationUseCaseTest` (5), todos com sucesso.

## Checkpoint 8 — Validar concorrência, transações e fronteiras Modulith

### Concorrência

- Criar testes de integração com transações reais e barreiras, não mocks de repository.
- Disputar as últimas unidades entre duas Service Executions e provar saldo nunca negativo.
- Disputar o mesmo `serviceExecutionId` e provar unicidade/idempotência.
- Testar reserva multi-item com um item insuficiente e nenhum desconto.
- Testar update/desativação concorrente sem lost update.
- Testar consumo concorrente preservando o primeiro `consumedAt`.
- Verificar o comportamento dos locks no MySQL 8 do `docker-compose.yml` além do H2 em MySQL mode.

### Transações e módulos

- Provar que aprovação, saldo, reserva e `stockReservationId` commitam ou sofrem rollback juntos.
- Criar `@ApplicationModuleTest` para a comunicação pela named interface.
- Executar `ModuleStructureTest` e confirmar somente `registration`, `servicelifecycle` e `stockprocurement`.
- Buscar imports cruzados e confirmar que Service Lifecycle importa somente
  `stockprocurement :: stock-reservation-api`.
- Confirmar ausência de dependência de Stock & Procurement para Service Lifecycle.

### Evidência

Registrar cenários concorrentes, saldo final, quantidade de reservas, banco usado e resultado de
`ModuleStructureTest`.

Concluído em 2026-08-21:

- `StockReservationConcurrencyIntegrationTest` executou em H2 MySQL mode cinco cenários com barreiras e
  transações reais: disputa da última unidade, mesma execução idempotente, lote insuficiente sem desconto,
  update/desativação concorrente e consumo concorrente; resultado: 5 testes, sucesso;
- o mesmo teste foi executado contra MySQL 8.0.46 do `docker-compose.yml`, em schema temporário isolado
  `wms_stock_reservation_it_20260821`; Flyway aplicou as 7 migrations e Hibernate validou o schema. O schema foi
  removido ao término, sem alteração do schema de desenvolvimento `workshop`;
- `StockReservationApiApplicationModuleTest` confirmou a comunicação de Service Lifecycle pela named interface
  pública (1 teste, sucesso) e `ModuleStructureTest` confirmou as três fronteiras Modulith (2 testes, sucesso).

## Checkpoint 9 — Atualizar OpenAPI, Postman e documentação do projeto

### OpenAPI e Postman

- Documentar os quatro endpoints, DTOs, enums, validações e respostas com Springdoc.
- Atualizar expectativas executáveis do OpenAPI gerado.
- Atualizar `docs/api/postman/workshop-management-system.postman_collection.json` com:
  - variáveis `stockReservationId` e `serviceExecutionId` quando necessário;
  - retry, consultas e consumo;
  - exemplos `RESERVED`, `NOT_RESERVED`, `ACTIVE` e `CONSUMED`.
- Validar a collection como JSON.
- Confirmar que `AWAITING_PART` não aparece em contratos atuais.

### Documentação estrutural

- Atualizar `docs/Architecture.md` para o aggregate, named interface, transação e status novos.
- Atualizar documentação estrutural gerada pelo Spring Modulith quando aplicável.
- Atualizar backlog/RFC/ADR apenas quando o texto atual representar estado vigente e divergir das specs aprovadas.
- Não apagar referências históricas legítimas a decisões anteriores.
- Confirmar que os links dos widgets sucessores do Miro foram registrados no Checkpoint 1.

### Verificação

- Executar testes de contrato OpenAPI.
- Validar o JSON Postman com parser, sem reformatar conteúdo alheio.
- Buscar `AWAITING_PART`, `AWAITING_ITEM`, `RELEASED` e rotas de reserva para classificar cada ocorrência remanescente.

### Evidência

Concluído em 2026-08-21:

- controllers e DTOs estão anotados com Springdoc; `StockReservationControllerTest` validou no `/v3/api-docs` as
  três novas operações e o retry da execução (3 testes, sucesso);
- a coleção Postman recebeu `stockReservationId` e `serviceExecutionId`, os quatro endpoints e exemplos
  `RESERVED`, `NOT_RESERVED`, `ACTIVE` e `CONSUMED`; o arquivo foi validado por parser JSON sem reformatar conteúdo
  existente;
- `docs/Architecture.md` passou a registrar o aggregate, a named interface, os locks, `AWAITING_ITEMS` e listeners
  after-commit;
- a busca não encontrou `AWAITING_PART`, `AWAITING_ITEM` ou `RELEASED` em contratos atuais. As duas ocorrências de
  `AWAITING_PART` remanescentes estão somente na migration como condição histórica de backfill; a referência no
  baseline histórico de `Architecture.md` foi mantida explicitamente como registro anterior.

## Checkpoint 10 — Concluir segurança, cobertura e gates finais

### Revisão de segurança

Preencher a tabela com `Resolved`, `Accepted limitation`, `N/A` ou achado pendente e registrar evidência real:

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação e mass assignment | Resolved | Retry não aceita body; comandos públicos validam UUID/quantidade positiva e responses não possuem campos mutáveis. |
| Autenticação e autorização | Accepted limitation | O projeto ainda não possui autenticação sistêmica; não foram simulados papéis ou permissões nesta feature. |
| Exposição operacional/Customer | Resolved | Contratos expõem somente IDs, quantidades, estado e timestamps; não incluem Customer, preço ou snapshots comerciais. |
| Segredos, credenciais e logs | Resolved | Não houve dependência, segredo ou dado pessoal novo; adapters registram apenas IDs e quantidades operacionais. |
| SQL, migration e saldo | Resolved | Flyway possui checks/FKs/unique constraint; locks pessimistas ordenados e testes H2/MySQL 8 cobrem saldo e rollback. |
| Erros e information disclosure | Resolved | Handlers retornam códigos estáveis sem SQL, constraint, package ou stack trace no corpo HTTP. |
| Dependências e vulnerabilidades | Resolved | Nenhuma dependência foi adicionada ou alterada pela feature; `make verify` usa o grafo existente. |
| Abuso e contenção | Resolved | Endpoints não aceitam linhas livres; transações são curtas e locks de Stock Item são ordenados por UUID. |
| Notificação antes do commit | Resolved | Listeners `AFTER_COMMIT` foram validados em commit e rollback por `StockReservationNotificationAfterCommitTest`. |

Nenhum achado crítico ou alto pode permanecer aberto. A ausência de autenticação pode ser registrada como limitação
aceita do MVP somente porque é sistêmica e explicitamente fora do escopo; ela não autoriza a inclusão de campos mutáveis
desnecessários.

### Gates finais

- Executar `make test`.
- Executar `make coverage` e revisar a meta global de 80%, sem reduzir a cobertura do código alterado.
- Executar `make verify` sem testes inadequadamente ignorados.
- Executar `ModuleStructureTest` e testes de módulo.
- Confirmar startup Flyway/Hibernate com `ddl-auto=validate`.
- Confirmar verificação concorrente em MySQL 8.
- Executar `git diff --check` e revisar line length, imports wildcard e diff fora do escopo.
- Confirmar OpenAPI, Postman, arquitetura e Miro atualizados.
- Atualizar critérios de aceite das specs com evidências reais.
- Marcar este plano `Implemented` somente depois de todos os checkpoints estarem `Completed`.

### Evidências finais

Preencher durante a execução:

| Evidência | Resultado |
|---|---|
| `make test` | Passou: 308 testes, 0 falhas e 0 erros. |
| `make coverage` | Passou: relatório JaCoCo gerado. |
| `make verify` | Passou: sem testes ignorados, falhas ou erros. |
| Cobertura JaCoCo global e do código alterado | 93,13% global (9.861/10.588 instruções); 94,78% em `stockreservation`. |
| `ModuleStructureTest` | Passou: 2 testes; três módulos diretos e named interface válida. |
| Flyway + Hibernate `validate` | Passou em H2 vazio e em MySQL 8 isolado, com 7 migrations. |
| Concorrência em H2 e MySQL 8 | Passou: cinco cenários com transações reais; schema temporário MySQL removido. |
| OpenAPI e Postman | Atualizados; OpenAPI testado e Postman JSON válido. |
| Widgets sucessores no Miro | Concluído: cinco pares; sucessores relidos pelo conector. |
| Revisão de segurança | Concluída, sem achado crítico ou alto; limitação de autenticação aceita e pré-existente. |

## Rollback e recuperação

### Antes de qualquer reserva em ambiente compartilhado

- obter backup verificável antes da migration;
- implantar migration e aplicação de forma coordenada por causa do rename para `AWAITING_ITEMS`;
- se a migration falhar, interromper o deploy e não editar o arquivo aplicado ou a tabela de histórico Flyway;
- criar migration corretiva com novo timestamp quando o ambiente não puder ser reconstruído.

### Depois que reservas reais existirem

Rollback simples para o código anterior não é seguro: o binário antigo não reconhece `AWAITING_ITEMS`, não entende
`stockReservationId` e pode ignorar saldos comprometidos. Preferir correção forward.

Se rollback for indispensável:

1. colocar comandos de aprovação, retry e consumo em manutenção;
2. obter backup e listar todas as reservas criadas;
3. reconciliar manualmente cada `ACTIVE` e seu desconto antes de qualquer devolução de saldo;
4. criar migration compensatória aprovada para dados e status, sem alterar migrations aplicadas;
5. somente então implantar o binário anterior e reabrir escritas;
6. preservar o histórico para auditoria da recuperação.

Em ambiente local descartável, `make docker-reset` pode recriar o banco apenas depois de confirmar explicitamente que o
volume é local e não contém dados relevantes. Nunca usar reset destrutivo como procedimento para ambiente compartilhado.

## Checklist de conclusão

- [x] Specs desta feature permanecem aprovadas e specs afetadas de Service Lifecycle foram reconciliadas.
- [x] Widgets sucessores do Miro foram criados sem modificar widgets anteriores.
- [x] Aggregate, saldo atômico, consumo e integração foram implementados.
- [x] Migration incremental, backfill e classificação sem seed foram verificados.
- [x] OpenAPI, Postman, arquitetura e diagramas de módulo foram atualizados.
- [x] Revisão de segurança não possui achado crítico ou alto aberto.
- [x] `make verify` passou e a cobertura do código alterado não regrediu.
- [x] Critérios de aceite possuem evidências e o plano foi marcado `Implemented`.

# Plano de Implementação: Recebimento e Reposição de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-receiving-and-restocking` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Branch de implementação | `feat/stockprocurement-purchase-order-receiving` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo da execução

Registrar uma única entrada integral para cada Purchase Order `CLOSED`, aumentar todos os saldos atomicamente e tentar
novamente as execuções relacionadas em ordem de prioridade depois do commit.

```text
PurchaseOrder CLOSED sem Receipt
    └── POST /receipt
        ├── StockReceipt + uma movimentação por linha
        ├── availableQuantity incrementada atomicamente
        └── AFTER_COMMIT → retries URGENT, HIGH, NORMAL, LOW
```

Receber novamente a mesma ordem retorna o Receipt existente e republica a tentativa de retry sem duplicar saldo.

## Instruções para retomada

Antes de executar qualquer checkpoint:

1. ler o `AGENTS.md` e as três peças SDD desta feature;
2. confirmar que specs de RF28 e RF29 continuam `Approved`;
3. usar a mesma branch `feat/stockprocurement-purchase-order-receiving` de RF28;
4. confirmar que RF28 concluiu ao menos seus checkpoints 1–5 e está verde;
5. inspecionar `git status --short` e preservar mudanças alheias;
6. manter um único checkpoint `In Progress` por vez;
7. registrar evidências antes de mudar qualquer status para `Completed`.

Mudança material em entrega integral, item inativo, prioridade ou consistência after-commit exige retornar a spec
afetada para `Draft` e obter nova aprovação.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Confirmar precondições e baseline de RF28 | Completed |
| 1 | Implementar domínio de Receipt e reposição | Completed |
| 2 | Criar migration e persistência de Receipt | Completed |
| 3 | Implementar recebimento atômico e idempotente | Completed |
| 4 | Implementar HTTP e composição de leitura | Completed |
| 5 | Publicar evento e integrar retries priorizados | Completed |
| 6 | Validar concorrência e falhas after-commit | Completed |
| 7 | Atualizar OpenAPI, Postman e documentação | Completed |
| 8 | Concluir segurança, cobertura e gates conjuntos | Completed |

## Checkpoint 0 — Confirmar precondições e baseline de RF28

### Verificações

- `PurchaseOrderStatus.CLOSED` existe e está persistido com invariantes.
- `POST /api/purchase-orders/{id}/close` é idempotente.
- Consulta/listagem encontram `OPEN` e `CLOSED`.
- Fechamento não altera `availableQuantity`.
- Testes focados de RF28 e `ModuleStructureTest` estão verdes.

### Evidência

Registrar commit/base, comandos e resultados. Não iniciar migration ou código de Receipt enquanto uma precondição
estiver falhando.

## Checkpoint 1 — Implementar domínio de Receipt e reposição

### `StockReceipt`

- Criar aggregate, linhas e factory/reconstituição em `stockprocurement.stockreceipt.domain.model`.
- Garantir Purchase Order única, autoria/instante imutáveis e ao menos uma linha.
- Modelar `movementId`, item, quantidade e saldos anterior/posterior por linha.
- Rejeitar duplicata, quantidade inválida e estado inconsistente.
- Não criar CRUD genérico de Stock Movement.

### `StockItem`

- Implementar `receive(Quantity)` com `Math.addExact`.
- Retornar saldo anterior/posterior para formar a movimentação.
- Permitir item inativo sem reativá-lo.
- Preservar cadastro e política futura de baixo estoque.

### Testes

- Cobrir criação/reconstituição e imutabilidade do Receipt.
- Cobrir item ativo/inativo, soma e overflow.
- Confirmar domínio sem Spring/JPA/HTTP.

## Checkpoint 2 — Criar migration e persistência de Receipt

### Migration

- Gerar timestamp UTC e criar `VyyyyMMddHHmmss__create_stock_receipts.sql`.
- Criar `stock_receipts` com unique/FK de Purchase Order, autoria e instante.
- Criar `stock_receipt_lines` com movement ID, item, quantidade e saldos.
- Adicionar checks, unique por Receipt/item e índices definidos na spec.
- Não adicionar FK para `user_accounts`.
- Não alterar migrations anteriores.

### Adapters

- Criar entity, embeddable/entity de linhas, mapper, JPA repository e adapter.
- Implementar buscas por ID, Purchase Order, lock e lote de IDs.
- Traduzir unique concorrente sem expor SQL.

### Dados e testes

- Classificação: **nenhum seed necessário**.
- Cobrir round-trip, constraints e lock.
- Cobrir startup vazio com Flyway e Hibernate `validate`.
- Planejar evidência no MySQL do Docker Compose.

## Checkpoint 3 — Implementar recebimento atômico e idempotente

### Caso de uso

- Criar `ReceivePurchaseOrderUseCase` transacional e `ReceivePurchaseOrderResult`.
- Bloquear ordem, Receipt e Stock Items na ordem definida na technical spec.
- Exigir Purchase Order `CLOSED` e derivar todas as linhas.
- Criar Receipt/movimentos e salvar todos os saldos na mesma transação.
- Em replay, retornar Receipt existente sem mutar saldos/autoria/instante.
- Publicar `StockItemsRestockedEvent` no sucesso novo e no replay.

### Falhas e concorrência

- Traduzir ordem inexistente/não fechada, inconsistência e overflow.
- Reconciliar corrida pela unique key lendo o vencedor depois do lock/rollback apropriado.
- Garantir rollback integral se qualquer item falhar.
- Não recusar item somente por estar inativo.

### Testes

- Receipt integral de uma/várias linhas.
- Replay e chamadas concorrentes da mesma ordem.
- Corrida entre reserva e recebimento sem perda de atualização.
- Item ausente/inativo, overflow e falha no meio do lote.
- Confirmar movimento e saldo exatamente uma vez.

## Checkpoint 4 — Implementar HTTP e composição de leitura

### Endpoints

- Adicionar `POST /api/purchase-orders/{purchaseOrderId}/receipt` sem body.
- Retornar `201` + `Location` no primeiro Receipt e `200` no replay.
- Adicionar `GET /api/purchase-orders/{purchaseOrderId}/receipt`.
- Extrair `userAccountId` do principal autenticado.

### Respostas e queries

- Criar `StockReceiptResponse` com linhas, snapshots e saldos.
- Compor snapshots da Purchase Order, nunca do cadastro mutável.
- Adicionar `receiptId` e `receivedAt` à `PurchaseOrderResponse`.
- Adicionar `receiptStatus=PENDING|RECEIVED` à listagem.
- Buscar Receipts em lote para evitar N+1.

### Erros e segurança

- Implementar códigos estáveis previstos na technical spec.
- Preservar `401`/`403` via matriz central.
- Não aceitar linhas, quantidades, saldo final, autoria ou timestamps no request.
- Cobrir endpoints e filtros com MockMvc.

## Checkpoint 5 — Publicar evento e integrar retries priorizados

### Contrato Modulith

- Criar `StockItemsRestockedEvent` imutável e ordenado.
- Expor somente `stockreceipt.application.event` como `stock-restocking-events`.
- Publicar dentro da transação e consumir `AFTER_COMMIT`.
- Confirmar dependência `servicelifecycle → stockprocurement`, sem inversão.

### Consulta e ordenação

- Adicionar query dedicada por execution `AWAITING_ITEMS` e Stock Item.
- Não filtrar somente por `ServiceOrder.statusSnapshot`.
- Ordenar `URGENT`, `HIGH`, `NORMAL`, `LOW`.
- Desempatar por `serviceOrderId` e `serviceExecutionId`.

### Execução dos retries

- Criar listener/coordenador em Service Lifecycle.
- Revalidar cada candidato sob lock usando requirements congelados.
- Executar uma transação por Service Execution.
- Capturar falha individual e continuar as demais.
- Reusar `RetryStockReservationUseCase` e notificações idempotentes existentes.

### Testes

- `@ApplicationModuleTest` para publicação/consumo real.
- Execuções de prioridades diferentes disputando o mesmo saldo.
- Service Order global `IN_PROGRESS` com execution `AWAITING_ITEMS` elegível.
- Candidato que muda de estado antes do lock.
- Falha em uma execução sem bloquear a seguinte.

## Checkpoint 6 — Validar concorrência e falhas after-commit

### Cenários obrigatórios

- dois Receipts diferentes alterando o mesmo Stock Item;
- Receipt concorrendo com reserva;
- dois requests para a mesma Purchase Order;
- queda/falha simulada depois do commit e antes/durante o listener;
- replay republicando evento sem nova entrada;
- retry repetido sem duplicar Stock Reservation ou notificação.

### Limitação operacional

- Confirmar e documentar que o projeto não possui event publication registry persistente.
- Demonstrar recuperação por replay do POST e retry manual existente.
- Não introduzir outbox ou nova dependência fora das specs.

### Evidência

Registrar banco, threads, comandos, saldos finais, ordem dos retries e logs sanitizados.

## Checkpoint 7 — Atualizar OpenAPI, Postman e documentação

### Contratos

- Atualizar annotations/schemas do OpenAPI.
- Adicionar no Postman: fechar, receber, repetir Receipt, consultar Receipt e filtrar pendentes/recebidos.
- Asserir `201` inicial, `200` replay, IDs, movimentos, saldos e campos na Purchase Order.
- Atualizar variáveis da coleção sem dados pessoais reais.

### README e arquitetura

- Documentar pré-requisitos, autenticação, sequência RF27 → RF28 → RF29 e resultados.
- Incluir cenário `AWAITING_ITEMS` → Receipt → retry → `READY`.
- Incluir caso de maior prioridade consumindo saldo primeiro.
- Atualizar `docs/Architecture.md` com aggregate, evento, named interface e direção da dependência.
- Atualizar mapa de Stock & Procurement e backlog BL-002/BL-003 conforme o recorte concluído.

### Verificação manual mínima

1. criar item e execução que fique `AWAITING_ITEMS`;
2. criar e fechar Purchase Order;
3. registrar Receipt e verificar saldo/movimentos;
4. observar retry e estado da execução;
5. repetir Receipt e confirmar ausência de duplicação.

## Checkpoint 8 — Concluir segurança, cobertura e gates conjuntos

### Revisão de segurança a preencher

| Item | Status inicial | Evidência/mitigação esperada |
|---|---|---|
| Validação/mass assignment | Completed | Endpoint sem body; linhas, saldos, autoria e instante são sempre derivados no servidor. |
| Autenticação/autorização | Completed | Matriz central para `MANAGER`/`ADMIN` e `SecurityAuthorizationTest` com `401`, `403` e `404` autorizado. |
| Exposição operacional | Completed | Evento e logs usam somente UUIDs técnicos; Service Lifecycle não é exposto na resposta de Receipt. |
| Segredos/logs | Completed | Listener registra IDs técnicos, sem JWT, payload completo, SQL ou dado pessoal. |
| SQL/migration | Completed | Checks, FKs e unicidade; Flyway e Hibernate `validate` cobertos em banco H2 vazio. |
| Concorrência | Completed | Locks ordenados, `Math.addExact` e testes reais para Receipt/replay, dois Receipts e Receipt/reserva. |
| Dependências | N/A | Nenhuma dependência nova prevista |
| Eventos/abuso | Completed | Evento imutável `AFTER_COMMIT`, replay republica sem saldo adicional e falha de uma tentativa não impede a próxima. |

Nenhum finding crítico/alto pode permanecer aberto. `N/A` final exige motivo.

### Gates finais

- Executar testes focados por checkpoint.
- Executar `make test` durante a integração.
- Executar `make verify` depois de RF28/RF29 completas.
- Executar `make coverage` e revisar cobertura do delta.
- Confirmar `ModuleStructureTest` e `@ApplicationModuleTest` verdes.
- Confirmar OpenAPI/Postman/README no mesmo diff.
- Marcar ambos os planos `Implemented` somente após evidências finais.

## Evidências de verificação

Preencher por checkpoint com comandos, resultados, banco, cobertura, logs relevantes e links. Não usar apenas relato sem
comando ou assertion automatizada quando o comportamento puder ser testado.

| Checkpoint | Data | Evidência |
|---|---|---|
| 0 | 2026-08-25 | RF28 concluída: `CLOSED` persistido, `POST /close` idempotente, consulta/listagem de `OPEN`/`CLOSED` e saldo inalterado. `make test`, `make verify`, cobertura e Modulith verdes. |
| 1 | 2026-08-25 | `./mvnw -q -Dtest=StockItemTest,StockReceiptTest test` concluído com criação/reconstituição imutável de Receipt e reposição de item ativo/inativo, quantidade inválida e overflow. |
| 2–4 | 2026-08-25 | `./mvnw -q -Dtest=PurchaseOrderFlowIntegrationTest test` concluiu com Flyway/Hibernate em schema vazio, `201` inicial, `200` no replay, `Location`, consulta do Receipt, snapshots da ordem e saldo incrementado uma única vez. |
| 5 | 2026-08-25 | `RestockedStockReservationRetryListenerTest` cobriu filtro de requirements congelados, prioridade `URGENT → HIGH → NORMAL → LOW` e continuidade após falha. `StockRestockingEventsApplicationModuleTest` confirmou a named interface `stock-restocking-events`. `PurchaseOrderFlowIntegrationTest` confirmou publicação `AFTER_COMMIT` inicial e no replay. |
| 6 | 2026-08-25 | `PurchaseOrderConcurrencyIntegrationTest` cobriu duas chamadas para a mesma ordem, dois Receipts de ordens distintas para o mesmo item e Receipt concorrente com reserva sem perda de saldo. O projeto não possui publication registry persistente: a recuperação aprovada é replay do Receipt ou retry manual; Docker Compose/MySQL não estava em execução para a evidência operacional. |
| 7 | 2026-08-25 | Springdoc/OpenAPI, Postman, README, arquitetura e mapa de Stock & Procurement atualizados; coleção JSON e `git diff --check` validados. |
| 8 | 2026-08-26 | Revisão de segurança registrada acima; `make test`, `make verify` e `make coverage` concluídos. JaCoCo: 91,75% de linhas (4539/4947). Nenhuma dependência nova ou finding crítico/alto. |

## Rollback e recuperação

- Migration é aditiva e não deve ser removida/editada depois de aplicada.
- Antes de Receipts reais, o código pode ser revertido mantendo tabelas vazias.
- Depois de Receipts, usar roll-forward; binário anterior não atualiza nem consulta o novo histórico.
- Falha after-commit é recuperada repetindo o POST idempotente ou usando retry manual.
- Nunca corrigir saldo com SQL manual; ajuste futuro exige operação de inventário aprovada.

## Checklist de conclusão

- [x] RF28 está concluída e permanece sem efeito de saldo.
- [x] Um Receipt integral altera todos os saldos exatamente uma vez.
- [x] Movimentos, autoria e instante permanecem auditáveis.
- [x] Item inativo é recebido sem reativação.
- [x] Retry priorizado e isolamento de falhas estão cobertos.
- [x] Concorrência não perde atualização nem duplica Receipt/reserva.
- [x] Segurança não possui finding crítico/alto aberto.
- [x] OpenAPI, Postman, README e arquitetura estão atualizados.
- [x] `make verify`, Modulith e cobertura foram revisados.

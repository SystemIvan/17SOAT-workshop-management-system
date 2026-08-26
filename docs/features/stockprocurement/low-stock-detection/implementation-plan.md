# Plano de Implementação: Identificação de Stock Items em Nível Baixo

| Campo | Valor |
|---|---|
| Feature | `low-stock-detection` |
| Status | Draft |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Branch de implementação | `feat/stockprocurement-low-stock-detection` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo da execução

Adicionar uma política opcional de mínimo/alvo por Stock Item e transformar cada condição contínua de baixo estoque em
uma única ocorrência, Purchase Demand `LOW_STOCK` e sinalização operacional, sem criar Purchase Order automaticamente.

```text
availableQuantity >= minimumQuantity → NORMAL
availableQuantity < minimumQuantity
    └── LowStockOccurrence OPEN
        ├── PurchaseDemand LOW_STOCK
        └── uma sinalização ao Stock Manager

saldo recuperado/policy desabilitada/item inativo → ocorrência CLOSED
Receipt do ciclo ainda insuficiente → nova ocorrência com novo ID
```

## Instruções para retomada

Antes de iniciar qualquer checkpoint:

1. ler `AGENTS.md`, functional spec, technical spec e este plano;
2. confirmar que as specs continuam `Approved`;
3. iniciar a partir de `dev` contendo RF28/RF29 completas;
4. criar ou confirmar `feat/stockprocurement-low-stock-detection`;
5. executar o fluxo de Receipt e os testes baseline antes de alterar Stock Item;
6. inspecionar `git status --short` e preservar mudanças alheias;
7. manter um checkpoint `In Progress` e registrar evidência antes de concluí-lo.

Se houver necessidade de scheduler, outbox, comparação não estrita, policy global ou compra automática, interromper a
implementação e devolver as specs afetadas para `Draft`.

## Precondições

- RF27 fornece `PurchaseDemandApi`, `LOW_STOCK` e idempotência por occurrence ID.
- RF28 fornece Purchase Order `CLOSED`.
- RF29 fornece Stock Receipt, movimentos e `StockItemsRestockedEvent` after-commit.
- Stock Reservation continua sendo o único fluxo atual de redução de `availableQuantity`.

RF30 não deve iniciar integração com Receipt antes dessas precondições estarem no baseline da branch.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Validar baseline RF27–RF29 e contratos existentes | Pending |
| 1 | Implementar policy e occurrence no domínio | Pending |
| 2 | Criar migration, JPA, repositories e queries | Pending |
| 3 | Evoluir `PurchaseDemandApi` e reconciliação de claim | Pending |
| 4 | Implementar detector nos fluxos transacionais | Pending |
| 5 | Integrar reavaliação after-commit de Receipt | Pending |
| 6 | Implementar HTTP, filtros e respostas | Pending |
| 7 | Implementar sinalização consumer-owned | Pending |
| 8 | Validar concorrência, idempotência e Modulith | Pending |
| 9 | Atualizar OpenAPI, Postman e documentação | Pending |
| 10 | Concluir segurança, cobertura e gates finais | Pending |

## Checkpoint 0 — Validar baseline RF27–RF29 e contratos existentes

### Verificações

- `PurchaseDemandApi.recordLowStock` e named interface existem.
- Demandas possuem unique `(origin, originReferenceId, stockItemId)`.
- Purchase Order preserva IDs das demandas selecionadas.
- Receipt expõe ID, Purchase Order e evento com Stock Items.
- Reserva/Receipt usam locks de Stock Item por UUID.
- Replay do Receipt republica o evento sem duplicar saldo.

### Evidência

Registrar commit base e resultados dos testes de Purchase Demand, Reservation, Closing e Receipt. Não alterar o
contrato público antes de reconciliar qualquer diferença entre baseline e technical spec.

## Checkpoint 1 — Implementar policy e occurrence no domínio

### `LowStockPolicy` e `StockItem`

- Criar value object com mínimo não negativo, alvo positivo e `target > minimum`.
- Implementar comparação estrita e cálculo por subtração exata.
- Adicionar policy opcional a `StockItem` e sua reconstituição.
- Implementar configurar, desabilitar e `assessLowStock`.
- Preservar reserva, recebimento, cadastro e estado ativo.

### `LowStockOccurrence`

- Criar aggregate, status e closure reasons definidos na technical spec.
- Implementar abertura, atualização e fechamento idempotente.
- Preservar occurrence ID e purchaseDemandId imutáveis.
- Validar timeline, observação e sugestão.
- Proibir reabertura de ocorrência `CLOSED`.

### Testes

- Policy válida, limites, igualdade, cálculo e overflow.
- Stock Item sem policy, normal, baixo e inativo.
- Ciclo completo da ocorrência e reconstituições inválidas.
- Confirmar ausência de Spring/JPA/HTTP no domínio.

## Checkpoint 2 — Criar migration, JPA, repositories e queries

### Migration

- Gerar timestamp UTC e criar `VyyyyMMddHHmmss__add_low_stock_detection.sql`.
- Adicionar `minimum_quantity` e `target_quantity` nullable em `stock_items`.
- Adicionar check de paridade/relação e índice de detecção.
- Criar `low_stock_occurrences` com FKs, checks, timestamps e closure reason.
- Criar `open_slot`, unique por item/slot e unique de `purchase_demand_id`.
- Não editar migration de Stock Item ou Purchase Demand já aplicada.

### Persistência

- Evoluir entity/mapper de Stock Item para policy opcional.
- Criar entity, mapper, JPA repository e adapter de occurrence.
- Implementar busca aberta com lock.
- Adicionar composição em lote das ocorrências para respostas.
- Evoluir busca de Stock Item com filtro low stock sem side effect.

### Dados e testes

- Classificação: **nenhum seed necessário**.
- Confirmar itens existentes com policy nula e nenhuma ocorrência criada no deploy.
- Cobrir round-trip, unique, checks e índices.
- Cobrir startup vazio com Flyway/Hibernate `validate` e MySQL do Docker Compose.

## Checkpoint 3 — Evoluir `PurchaseDemandApi` e reconciliação de claim

### Contrato público

- Fazer `recordLowStock` retornar demand ID/status view.
- Adicionar `resolveLowStock` com command imutável.
- Manter todos os tipos no named interface e sem domínio/JPA expostos.
- Atualizar testes consumidores sem criar endpoint manual.

### Provider

- Participar da transação chamadora e manter lock/unique existentes.
- Criar/atualizar somente demanda `OPEN` equivalente.
- Preservar `CLAIMED`, `ORDERED` e `RESOLVED`.
- Resolver somente `OPEN` e sempre manter histórico.

### Claim concorrente

- Criar serviço interno que consulta occurrence por `purchaseDemandId` depois de release de RF27.
- Se occurrence estiver `CLOSED`, resolver a demanda recém-liberada na mesma transação.
- Se occurrence estiver `OPEN`, manter a demanda selecionável.
- Não interromper submissão externa já iniciada.

### Testes

- Record/replay, update e resolução.
- Occurrence fecha durante `CLAIMED`, fornecedor aceita e termina `ORDERED`.
- Occurrence fecha durante `CLAIMED`, fornecedor rejeita e termina `RESOLVED`.
- Occurrence permanece aberta, rejeição libera para `OPEN`.
- `@ApplicationModuleTest` e `ModuleStructureTest` para o named interface.

## Checkpoint 4 — Implementar detector nos fluxos transacionais

### Serviço de avaliação

- Criar `EvaluateLowStockUseCase` interno com `Clock` UTC.
- Exigir Stock Item bloqueado antes de occurrence/demand.
- Abrir, atualizar ou fechar conforme assessment.
- Publicar evento somente em nova ocorrência.
- Não executar efeitos em consultas.

### Policy e cadastro

- Criar `ConfigureLowStockPolicyUseCase` e `DisableLowStockPolicyUseCase`.
- Adicionar policy opcional ao `CreateStockItemUseCase`.
- Avaliar imediatamente depois da criação/configuração.
- Encerrar occurrence/demanda `OPEN` ao desabilitar ou desativar item.

### Reserva

- Integrar detector depois de descontos confirmáveis e antes do commit.
- Avaliar itens alterados em ordem de UUID.
- Não reavaliar tentativa malsucedida ou consumo sem mudança de disponibilidade.
- Garantir rollback conjunto de saldo, reserva, occurrence e demanda se a avaliação falhar.

### Testes

- Configuração sobre saldo normal/baixo e mudança de alvo.
- Criação antiga sem policy e nova com policy.
- Reserva cruza o mínimo e nova redução atualiza o mesmo ciclo.
- Igualdade não abre occurrence.
- Desabilitação/desativação fecham sem apagar histórico.
- Falha transacional não deixa saldo ou demanda parcial.

## Checkpoint 5 — Integrar reavaliação after-commit de Receipt

### Listener

- Criar `RestockedLowStockReevaluationListener` para `StockItemsRestockedEvent`.
- Carregar Receipt, Purchase Order e demandas selecionadas pelos IDs do evento.
- Bloquear Stock Items em ordem de UUID em nova transação.
- Identificar se o demand ID da ocorrência pertence ao ciclo recebido.

### Regras

- Receipt do ciclo encerra ocorrência com `REPLENISHMENT_CYCLE_COMPLETED`.
- Saldo ainda baixo abre novo occurrence ID/demand ID.
- Receipt ad hoc apenas reavalia a ocorrência atual.
- Saldo recuperado encerra com `STOCK_RECOVERED`.
- Replay não fecha uma nova ocorrência não vinculada à ordem antiga.
- Falha do listener não desfaz Receipt.

### Testes

- Ciclo comprado chega ao alvo.
- Ciclo comprado chega ainda abaixo do mínimo e gera novo ID.
- Receipt ad hoc normaliza ou mantém a mesma ocorrência.
- Retry do evento é idempotente.
- Ordem do listener de RF30 e do retry de Service Lifecycle não muda o estado final.

## Checkpoint 6 — Implementar HTTP, filtros e respostas

### Requests

- Adicionar `LowStockPolicyRequest` com Bean Validation.
- Adicionar policy opcional ao create request de Stock Item.
- Criar `PUT /api/stock-items/{id}/low-stock-policy`.
- Criar `DELETE /api/stock-items/{id}/low-stock-policy` idempotente.

### Responses e consulta

- Adicionar policy, status, occurrence ID e sugestão a `StockItemResponse`.
- Compor occurrence em lote para evitar N+1.
- Adicionar `lowStock=true|false` ao filtro cumulativo.
- Excluir item sem policy dos dois valores explícitos.
- Garantir que GET não abre/atualiza ocorrência.

### Erros e segurança

- Mapear `VALIDATION_ERROR`, `INVALID_LOW_STOCK_POLICY`, `STOCK_ITEM_NOT_FOUND` e `STOCK_ITEM_INACTIVE`.
- Manter autorização `MANAGER`/`ADMIN` via `/api/stock-items/**`.
- Não aceitar saldo, status, occurrence ID ou sugestão no request.

### Testes

- Compatibilidade do POST antigo.
- POST/PUT válidos e combinações inválidas.
- DELETE repetido.
- Respostas nos três estados e filtros combinados.
- `400`, `401`, `403`, `404` e `409` com códigos estáveis.

## Checkpoint 7 — Implementar sinalização consumer-owned

- Criar `LowStockDetectedEvent` com dados operacionais mínimos.
- Criar `LowStockNotificationPort` em `lowstock.application.port`.
- Implementar adapter de log sanitizado pertencente a Stock & Procurement.
- Consumir `AFTER_COMMIT` e capturar exception do port.
- Publicar apenas na abertura; não em update, leitura ou replay.
- Não estender o port específico de Stock Reservation e não criar módulo Notifications.

### Testes

- Uma sinalização na nova ocorrência.
- Nenhuma sinalização em reavaliação do mesmo ciclo.
- Nova sinalização em novo ciclo legítimo.
- Falha do adapter não reverte policy, occurrence ou demand.
- Log não contém JWT, preço ou dados pessoais.

## Checkpoint 8 — Validar concorrência, idempotência e Modulith

### Concorrência

- duas reservas cruzando o limite do mesmo item;
- configuração concorrente com reserva;
- Receipt concorrente com reserva/detector;
- duas aberturas disputando `open_slot`;
- claim/release concorrendo com fechamento de occurrence.

### Invariantes finais

- no máximo uma occurrence `OPEN` por Stock Item;
- no máximo uma demanda equivalente por occurrence/item;
- demanda `ORDERED` nunca reabre;
- Receipt replay não multiplica ciclos;
- leitura não produz escrita;
- nenhuma Purchase Order é criada automaticamente.

### Módulos e transações

- Executar `ModuleStructureTest`.
- Executar `@ApplicationModuleTest` de Purchase Demand e Receipt.
- Buscar imports proibidos entre módulos.
- Registrar locks, threads, banco e saldos finais nos testes concorrentes.

## Checkpoint 9 — Atualizar OpenAPI, Postman e documentação

### Contratos e fluxo manual

- Atualizar annotations e schemas OpenAPI.
- Atualizar Postman com create policy opcional, PUT, DELETE e filtro low stock.
- Demonstrar reserva que abre `LOW_STOCK` e demanda disponível em RF27.
- Demonstrar Purchase Order manual, fechamento, Receipt e encerramento/novo ciclo.
- Adicionar assertions de IDs, sugestão, ausência de duplicata e status.

### README e arquitetura

- Atualizar README com pré-requisitos, autenticação, requests, variáveis e resultados.
- Atualizar `docs/Architecture.md` com policy, occurrence e integrações.
- Atualizar mapa de Stock & Procurement e BL-002.
- Registrar a comparação estrita e a ausência de scheduler/compra automática.
- Não manter contrato YAML duplicado.

## Checkpoint 10 — Concluir segurança, cobertura e gates finais

### Revisão de segurança a preencher

| Item | Status inicial | Evidência/mitigação esperada |
|---|---|---|
| Validação/mass assignment | Pending | Somente mínimo/alvo; demais campos calculados |
| Autenticação/autorização | Pending | `MANAGER`/`ADMIN`, `401`, `403` |
| Exposição de dados | Pending | Apenas dados operacionais de inventário |
| Segredos/logs | Pending | Sem JWT, PII, preço ou payload completo |
| SQL/migration | Pending | Checks, FKs, unique `open_slot` e `validate` |
| Concorrência | Pending | Locks, unique e testes com claims/receipts |
| Dependências | N/A | Nenhuma dependência externa nova prevista |
| Eventos/abuso | Pending | Leitura pura, replay idempotente e alerta único |

Nenhum finding crítico/alto pode permanecer aberto. `N/A` final exige justificativa curta.

### Gates finais

- Executar testes focados por checkpoint.
- Executar `make test` durante desenvolvimento.
- Executar `make verify` antes da conclusão.
- Executar `make coverage` e revisar meta de 80% e delta.
- Confirmar Flyway/Hibernate, MySQL, Modulith, OpenAPI e Postman.
- Confirmar que testes não foram ignorados ou enfraquecidos.
- Marcar plano e feature `Implemented` somente após todas as evidências.

## Evidências de verificação

Preencher durante a execução com data, comandos, resultados, banco, cobertura, links e achados de segurança. Checkpoint
sem evidência permanece `Pending` ou `In Progress`.

## Rollback e recuperação

- Migration é aditiva e imutável depois de aplicada.
- Itens existentes permanecem sem policy; deploy não cria demanda automaticamente.
- Antes de policies reais, código pode ser revertido mantendo colunas/tabela vazias.
- Depois de occurrences/demands, usar roll-forward para preservar o ciclo e a idempotência.
- Falha do listener de Receipt é recuperada pelo replay idempotente do Receipt.
- Não apagar occurrence/demand nem corrigir saldo manualmente por SQL.

## Checklist de conclusão

- [ ] Policy opcional preserva compatibilidade e valida invariantes.
- [ ] Comparação estrita e sugestão estão cobertas.
- [ ] Uma única occurrence/demand existe por ciclo.
- [ ] Claim/release não deixa demanda obsoleta aberta.
- [ ] Reserva e Receipt reavaliam sem quebrar suas próprias invariantes.
- [ ] Leitura não gera efeitos e compra continua manual.
- [ ] Sinalização ocorre uma vez por ocorrência e falha em melhor esforço.
- [ ] Segurança não possui finding crítico/alto aberto.
- [ ] OpenAPI, Postman, README e arquitetura estão atualizados.
- [ ] `make verify`, Modulith, migrations e cobertura foram revisados.


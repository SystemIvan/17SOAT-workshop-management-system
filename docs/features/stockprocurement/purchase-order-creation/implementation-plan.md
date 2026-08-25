# Plano de Implementação: Criação de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-creation` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Branch | `feat/stockprocurement-purchase-order-creation` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo

Disponibilizar a avaliação antecipada de reparo para Diagnosis e Estimate. Insuficiência concreta gera ou atualiza
`PENDING_REPAIR` antes da decisão comercial; a decisão do Stock Manager de criar Purchase Order continua manual.

## Checkpoints ordenados

### 1. API de avaliação e regras de demanda

- [x] Criar o named interface `purchase-demand-api`, `RepairStockAssessmentApi` e DTOs públicos de lote.
- [x] Validar referências do lote integralmente, avaliar por Stock Item e registrar/atualizar apenas insuficiências
  `PENDING_REPAIR` pela chave `(origin, serviceExecutionId, stockItemId)`.
- [x] Preservar demanda em observação suficiente, Estimate rejeitada/expirada e demanda não `OPEN`; resolver somente
  quando `StockReservationCreatedEvent` encontrar demanda aberta equivalente.

### 2. Persistência, concorrência e consulta

- [x] Reutilizar a migration e a unique key existentes de `purchase_demands`; adaptar domínio, repository e mapper para
  `updatedAt` e atualização observável.
- [x] Aplicar lock global Stock Item -> Purchase Demand, em UUID ordenado, e cobrir concorrência/rollback.
- [x] Incluir `updatedAt` na resposta de listagem, OpenAPI, MockMvc e Postman.

### 3. Integração com Service Lifecycle

- [x] Provar em `@ApplicationModuleTest` que Diagnosis e Estimate usam a API pública na mesma transação e não causam
  dependência inversa.
- [x] Confirmar que nenhum fluxo cria Purchase Order automaticamente e que RF28–RF30 seguem fora de escopo.

### 4. Documentação, segurança e qualidade

- [x] Atualizar README com o fluxo executável Diagnosis -> Purchase Demand -> Estimate -> reserva após aprovação.
- [x] Revisar validação, informação exposta, locks, dados persistentes sem seed e autorização JWT.
- [x] Rodar testes focados, `make test`, `make verify`, ModuleStructureTest e revisar cobertura.

## Critérios de conclusão

- [x] Uma necessidade insuficiente gera no máximo uma demanda equivalente atualizável.
- [x] Falha de stock no Diagnosis não altera `availableQuantity` nem `AWAITING_ITEMS`.
- [x] Contratos, documentação, segurança, migrations consumidoras e qualidade verificados.

## Evidências e segurança

`make verify`, ModuleStructureTest e a migração Flyway contra H2 com Hibernate `validate` passaram em 2026-08-25.
Os DTOs públicos limitam o input a IDs e quantidades, a ordem não é criada automaticamente e a disponibilidade não
altera saldo. `Purchase Demand` e `Purchase Order` exigem JWT `MANAGER` ou `ADMIN`; os testes de autorização cobrem
acesso `MANAGER`, `401` sem token e `403` para papel sem permissão. Não há finding crítico ou alto aberto.

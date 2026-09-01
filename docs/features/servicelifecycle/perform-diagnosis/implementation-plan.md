# Plano de Implementação: Registrar Diagnóstico

| Campo | Valor |
|---|---|
| Feature | `perform-diagnosis` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo

No Diagnosis, avaliar em lote os Stock Requirements das novas Service Executions, persistir a fotografia de
disponibilidade e registrar ou atualizar a Purchase Demand `PENDING_REPAIR` na mesma transação. Essa observação não
reserva unidades nem muda a execução para `AWAITING_ITEMS`.

## Checkpoints ordenados

### 1. Contrato público e domínio

- [x] Expor `RepairStockAssessmentApi` no named interface `purchase-demand-api`, com comandos/resultados imutáveis
  limitados a IDs, quantidades, status e instante.
- [x] Criar `StockAvailabilitySnapshot` e invariantes, adicionando a substituição atômica na `ServiceExecution` e na
  `ServiceOrder` sem alterar reserva, congelamento ou status.
- [x] Cobrir domínio para disponibilidade, insuficiência, duplicidade, conjunto divergente e preservação de `PENDING`.

### 2. Avaliação em Stock & Procurement

- [x] Implementar a avaliação e o registro síncronos: validar todo o lote antes de escrever, bloquear Stock Items e
  Purchase Demands em ordem determinística e criar/atualizar somente insuficiências.
- [x] Manter demanda aberta após leitura suficiente, rejeição ou expiração; resolver somente pela reserva criada.
- [x] Cobrir item inexistente/inativo, rollback integral, deduplicação, atualização de `updatedAt` e concorrência.

### 3. Caso de uso, persistência e contrato HTTP

- [x] Integrar a avaliação única em `PerformDiagnosisUseCase`, consolidando requisitos com overflow seguro e gravando
  os snapshots antes do save.
- [x] Criar migration aditiva e mapper/entidade da coleção de snapshots, com constraints e sem seed.
- [x] Incluir `stockAvailability` não nulo em `ServiceExecutionResponse`, OpenAPI e testes MockMvc.

### 4. Documentação, segurança e evidências

- [x] Atualizar Postman e README com Diagnosis -> Purchase Demand antes da Estimate.
- [x] Registrar revisão de segurança: campos calculados pelo servidor, ausência de dados pessoais, locks/constraints e
  lacuna conhecida de autenticação.
- [x] Executar testes focados, `make test`, `make verify`, ModuleStructureTest e revisar cobertura.

## Revisão de segurança e evidências

Campos de disponibilidade são calculados no servidor e o contrato interno contém somente IDs e quantidades. Nenhum
dado de Customer, Vehicle ou preço atravessa a fronteira. A ausência de autenticação é uma lacuna preexistente do
baseline, sem simulação de papel nesta entrega. `make verify`, ModuleStructureTest, Flyway/Hibernate validate e os
testes de domínio/aplicação passaram em 2026-08-25; não há finding crítico ou alto pendente.

## Critérios de conclusão

- [x] Snapshot e Purchase Demand persistidos atomicamente no Diagnosis.
- [x] `AWAITING_ITEMS` continua dependente de aprovação e falha de reserva.
- [x] Migration, OpenAPI, Postman, README, testes e fronteiras Modulith verificados.
- [x] Nenhum finding crítico/alto de segurança pendente.

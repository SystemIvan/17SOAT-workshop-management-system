# Plano de Implementação: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Implemented |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo

Revalidar os requisitos congelados durante a geração da Estimate, reconciliar Purchase Demands e guardar em cada linha
a fotografia de disponibilidade independente do snapshot comercial, sem reservar estoque.

## Checkpoints ordenados

### 1. Domínio e avaliação compartilhada

- [x] Criar `EstimateStockAvailability` e invariantes, agregando uma entrada por Stock Item em cada `EstimateLine`.
- [x] Reutilizar exclusivamente `RepairStockAssessmentApi`; não importar internals de Stock & Procurement.
- [x] Cobrir cópia imutável, resultado incompleto/extra/duplicado e execução sem requisito.

### 2. Orquestração e persistência

- [x] Alterar `GenerateEstimateUseCase` para congelar, consolidar, avaliar uma vez, atualizar a Service Execution e só
  então criar/persistir a Estimate e publicar `EstimateGenerated`.
- [x] Criar migration aditiva para `estimate_line_stock_availability` e adaptar JPA/mappers, sem backfill e sem seed.
- [x] Garantir rollback de congelamento, snapshots, Estimate e Purchase Demands para qualquer erro do lote.

### 3. HTTP e documentação

- [x] Expor `stockAvailability` como array não nulo em criação e consulta de Estimate, mantendo `stockItems` comercial.
- [x] Atualizar Springdoc, MockMvc, coleção Postman e README para a revalidação da Estimate.

### 4. Segurança e qualidade

- [x] Revisar dados calculados pelo servidor, erros estáveis, ausência de dados pessoais e a lacuna de autenticação do
  baseline.
- [x] Executar testes de domínio, aplicação, persistência, módulo, `make test`, `make verify` e revisão de cobertura.

## Critérios de conclusão

- [x] Estimate preserva sua fotografia e não altera saldo/reserva.
- [x] Leitura suficiente não resolve Purchase Demand; reserva criada continua sendo a transição de resolução.
- [x] Migrations, contratos, documentação, segurança e qualidade verificados.

## Evidências e segurança

`make verify`, ModuleStructureTest e validação Flyway/Hibernate passaram em 2026-08-25. O snapshot é calculado pelo
servidor, não recebe saldo/status no request e não expõe dados pessoais. A autenticação continua sendo lacuna do
baseline, sem achado crítico ou alto introduzido por esta feature.

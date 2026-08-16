# Especificação Técnica: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-16 |
| Aprovado por | Matheus Campagnone |
| Aprovado em | 2026-08-16 |
| Especificação funcional | `docs/features/servicelifecycle/estimate-generation/functional-spec.md` |

## Objetivo técnico

Implementar a geração de `Estimate` dentro do módulo `servicelifecycle`, a partir de um Diagnosis já realizado em uma `ServiceOrder`.

A feature deve:

- criar e persistir uma `Estimate` como Aggregate Root separado;
- representar comercialmente as `ServiceExecution` pertencentes ao Diagnosis informado;
- copiar para a Estimate snapshots comerciais suficientes para manter o orçamento estável;
- impedir mais de uma Estimate para o mesmo ciclo de Diagnosis;
- publicar o evento `EstimateGenerated` após a persistência válida da Estimate;
- não implementar aprovação, rejeição, expiração automática, reserva de Stock ou Notification.

## Contexto e fronteiras

A implementação pertence a `servicelifecycle.estimate`.

`ServiceOrder` continua sendo Aggregate Root separado e fonte de verdade das `ServiceExecution`.

A `Estimate` referencia a `ServiceOrder` e o Diagnosis por ID. Ela não contém nem modifica diretamente objetos internos do aggregate `ServiceOrder`.

A geração é orquestrada na Application Layer.

## Estrutura proposta

- `estimate/domain/model/Estimate.java`
- `estimate/domain/model/EstimateLine.java`
- `estimate/domain/event/EstimateGenerated.java`
- `estimate/domain/repository/EstimateRepository.java`
- `estimate/application/dto/EstimateResponse.java`
- `estimate/application/usecase/GenerateEstimateUseCase.java`
- `estimate/infrastructure/persistence/...`

## Aggregate Estimate

A `Estimate` será Aggregate Root separado de `ServiceOrder`.

### Estado mínimo

- `id`
- `serviceOrderId`
- `diagnosisId`
- `customerId`
- `createdAt`
- `expiresAt`
- coleção imutável de `EstimateLine`

### Invariantes

- IDs obrigatórios não podem ser nulos.
- A Estimate deve possuir ao menos uma linha.
- Todas as linhas precisam pertencer ao mesmo Diagnosis usado na criação da Estimate.
- Uma Estimate criada não altera as `ServiceExecution` correspondentes.
- Alterações posteriores em ServiceOrder, Service Catalog ou Stock não alteram seus snapshots.

## EstimateLine

Cada `EstimateLine` representa o snapshot comercial de uma `ServiceExecution`.

### Estado mínimo

- `serviceExecutionId`
- `serviceName`
- preço do serviço
- coleção dos Stock Requirements necessários àquela execução

Os Stock Requirements existentes em `ServiceExecution` já possuem:

- `stockItemId`
- `type`
- `quantity`
- `nameSnapshot`
- `priceSnapshot`

A geração da Estimate deve copiar essas informações para seu próprio snapshot comercial conforme necessário, sem buscar dados vivos novamente no Stock.

## Fonte de dados da geração

`GenerateEstimateUseCase` recebe o identificador da `ServiceOrder` e o `diagnosisId`.

Fluxo:

1. carregar `ServiceOrder` por `ServiceOrderRepository`;
2. validar que o Diagnosis informado corresponde ao ciclo aberto;
3. selecionar `ServiceExecution` cujo `diagnosisId` corresponda ao Diagnosis informado;
4. exigir pelo menos uma execução;
5. verificar no `EstimateRepository` se já existe Estimate para aquele Diagnosis;
6. construir as linhas usando os snapshots existentes na `ServiceExecution`;
7. criar a `Estimate`;
8. persistir via `EstimateRepository`;
9. publicar `EstimateGenerated`.

A geração da Estimate não limpa openDiagnosisId. O código existente somente encerra o Diagnosis aberto quando suas ServiceExecutions deixam de estar PENDING. Geração, envio e decisão da Estimate permanecem momentos distintos.

## Repository

Será criado `EstimateRepository`.

Contrato mínimo:

- `Optional<Estimate> findById(UUID id)`
- `boolean existsByDiagnosisId(UUID diagnosisId)`
- `void save(Estimate estimate)`

Nenhum acesso direto à infraestrutura será feito pelo domínio.

## Persistência

Criar migration Flyway adicional, sem modificar migrations anteriores.

### estimates

Campos mínimos:

- `id`
- `service_order_id`
- `diagnosis_id`
- `customer_id`
- `created_at`
- `expires_at`

Deve existir unicidade para `diagnosis_id`, impedindo duas Estimates para o mesmo ciclo também no banco.

### estimate_lines

Campos mínimos:

- `id`
- `estimate_id`
- `service_execution_id`
- `service_name`
- `service_price_value`
- `service_price_currency`

### estimate_line_stock_items

Snapshot comercial dos materiais:

- `estimate_line_id`
- `stock_item_id`
- `type`
- `quantity`
- `name_snapshot`
- `price_snapshot_value`
- `price_snapshot_currency`

## Evento EstimateGenerated

O evento representa apenas a criação válida da Estimate.

Contrato mínimo:

- `eventId`
- `occurredAt`
- `estimateId`
- `serviceOrderId`
- `diagnosisId`
- `customerId`
- `expiresAt`

`expiresAt` representa o prazo já determinado pelo produtor. Notification não deve conhecer nem recalcular a regra de duração.

Enquanto a duração definitiva permanecer aberta, a feature não deve hard-code 24h ou 48h.

## Publicação do evento

O evento somente pode ser publicado depois que a Estimate tiver sido validamente criada e persistida.

O projeto ainda não possui infraestrutura compartilhada de publicação de eventos. Esta feature define e produz o contrato EstimateGenerated de forma testável, sem introduzir uma solução global de mensageria. A integração entre módulos poderá evoluir posteriormente sem alterar o significado do evento.

Nenhum consumidor de Notification será implementado nesta feature.

## Integração com ServiceOrder

O código existente de `ServiceOrder` já possui pontos de integração do Épico 2 para autorização e rejeição futuras.

Esses comportamentos não fazem parte desta entrega.

A geração utiliza apenas o estado público necessário da ServiceOrder e de suas ServiceExecutions.

As execuções são selecionadas pelo `diagnosisId`.

## Integração com Stock

Nenhuma chamada ao `StockItemRepository` será necessária durante a geração da Estimate.

O Diagnosis já registra os snapshots necessários dentro de cada `StockRequirement`.

Isso evita leitura viva de Stock e mantém a Estimate aderente ao princípio de snapshot comercial.

## API

Expor operação REST para geração da Estimate.

Contrato proposto:

`POST /api/service-orders/{serviceOrderId}/diagnoses/{diagnosisId}/estimate`

Resposta de sucesso: `201 Created`.

A resposta deve permitir identificar:

- Estimate;
- Service Order;
- Diagnosis;
- Customer;
- linhas comerciais;
- `createdAt`;
- `expiresAt`.

O contrato HTTP deverá ser refletido em OpenAPI e Postman antes da conclusão da feature.

## Tratamento de erros

Devem existir respostas coerentes para:

- Service Order inexistente;
- Diagnosis inexistente ou diferente do ciclo esperado;
- Diagnosis sem Service Executions;
- Estimate já existente para o mesmo Diagnosis;
- dados inválidos na geração.

Exceções internas não devem expor stack trace pela API.

## Estratégia de testes

### Domínio

Cobrir:

- criação válida de Estimate;
- rejeição de Estimate sem linhas;
- preservação de snapshots;
- imutabilidade dos dados expostos;
- validações obrigatórias.

### Application

Cobrir:

- geração com Diagnosis válido;
- Service Order inexistente;
- Diagnosis inválido;
- Diagnosis sem execuções;
- duplicidade por Diagnosis;
- persistência;
- publicação de `EstimateGenerated`.

### Infrastructure e Web

Cobrir:

- `201 Created`;
- payload esperado;
- erros de entrada e conflito;
- persistência quando aplicável.

## Segurança e operação

- IDs são tratados como UUID;
- nenhuma regra de autorização nova será inventada nesta feature;
- nenhum dado sensível adicional deve ser exposto;
- persistência deve utilizar os mecanismos já adotados pelo projeto;
- não haverá concatenação manual de entrada em SQL.

## Fora de escopo técnico

- aprovação ou rejeição de EstimateLine;
- atualização de `authorizedByEstimateId`;
- reserva de Stock;
- Notification Adapter;
- scheduler de expiração;
- regra fixa de 24h/48h;
- revisão ou versionamento de Estimate;
- execução ou tracking de ServiceExecution.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada.
- [x] Technical Spec revisada e aprovada.

Antes do PR:

- [ ] testes unitários passando;
- [ ] testes de integração aplicáveis passando;
- [ ] `make verify` passando;
- [ ] migration Flyway validada;
- [ ] OpenAPI atualizado;
- [ ] Postman atualizado;
- [ ] evento `EstimateGenerated` testado;
- [ ] nenhuma fronteira do Spring Modulith violada.



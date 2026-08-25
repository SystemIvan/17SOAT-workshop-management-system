# Especificação Técnica: Registrar Diagnóstico

| Campo | Valor |
|---|---|
| Feature | `perform-diagnosis` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta revisão deriva da especificação funcional aprovada em 2026-08-25. Nenhum código, migration ou contrato HTTP deste
delta pode ser alterado antes da aprovação humana explícita desta especificação técnica. O plano histórico permanece
`Stale` e não autoriza implementação.

## Objetivo técnico

Estender o fluxo existente de `POST /api/service-orders/{id}/diagnosis` para que, depois de criar as Service Executions,
ele:

- consolide os Stock Requirements por execução e Stock Item;
- solicite uma avaliação síncrona à API pública de Stock & Procurement;
- registre na Service Execution um snapshot informativo da disponibilidade observada;
- permita que Stock & Procurement crie ou atualize `PENDING_REPAIR` na mesma transação;
- preserve `PENDING`, sem reservar unidades nem alterar `availableQuantity`.

O baseline de atribuição, autoria, criação das execuções e `statusSnapshot = IN_DIAGNOSIS` permanece inalterado.

## Contextos e fronteiras

### Service Lifecycle

`ServiceOrder` continua sendo o aggregate root e `ServiceExecution`, sua entity interna. O novo snapshot pertence à
execução porque descreve os requirements diagnosticados naquele instante; ele não é fonte de verdade para saldo nem
substitui Stock Reservation.

`PerformDiagnosisUseCase` consumirá somente a named interface pública `purchase-demand-api`. Nenhum domínio, repository,
entity JPA ou package interno de `stockprocurement` será importado.

### Stock & Procurement

Stock & Procurement continua dono de `StockItem`, disponibilidade e `PurchaseDemand`. Sua API avalia necessidades,
persiste as demandas insuficientes e devolve DTOs públicos. `serviceExecutionId` é recebido como UUID opaco e não possui
foreign key ou navegação para Service Lifecycle.

A dependência permanece `servicelifecycle -> stockprocurement`, já existente por Stock Reservation, e não cria ciclo no
Spring Modulith.

## Contrato interno consumido

RF27 disponibilizará `RepairStockAssessmentApi` no named interface `purchase-demand-api`. O comando será em lote e
conterá uma entrada para cada Service Execution, com `serviceExecutionId` e linhas `stockItemId + requestedQuantity`.

O resultado terá uma avaliação por execução e item:

- `stockItemId`;
- `requestedQuantity` consolidada;
- `observedAvailableQuantity`;
- `shortageQuantity = max(requestedQuantity - observedAvailableQuantity, 0)`;
- `status`: `AVAILABLE` ou `INSUFFICIENT_QUANTITY`;
- `observedAt` em UTC com precisão de microssegundos.

O provider valida todas as referências antes de persistir qualquer demanda. Item inexistente ou inativo invalida o lote
inteiro. Para lotes válidos, cada insuficiência cria ou atualiza a demanda equivalente antes de devolver o resultado.
O mapper de aplicação converte `RepairStockAvailabilityStatus` para o enum local; o domínio não armazena tipos do
provider.

## Modelo de domínio

### `StockAvailabilitySnapshot`

Adicionar value object imutável em `serviceorder.domain.model`:

```text
stockItemId: UUID
requestedQuantity: int > 0
observedAvailableQuantity: int >= 0
shortageQuantity: int >= 0
status: AVAILABLE | INSUFFICIENT_QUANTITY
observedAt: Instant
```

Invariantes:

- `AVAILABLE` exige `shortageQuantity = 0` e saldo observado maior ou igual ao requerido;
- `INSUFFICIENT_QUANTITY` exige diferença positiva exata;
- IDs e instante são obrigatórios;
- uma execução possui no máximo um snapshot por Stock Item.

### `ServiceExecution`

Adicionar coleção de snapshots e o método package-private `replaceStockAvailability(...)`. O método:

- só aceita atualização enquanto a execução estiver `PENDING`;
- exige exatamente os Stock Items e totais obtidos da consolidação dos requirements atuais;
- substitui atomicamente a fotografia anterior;
- não muda `status`, `reserved`, `stockReservationId` nem `stockRequirementsFrozen`.

`reconstitute(...)` e os getters de leitura passam a preservar a coleção. Execução sem requirements mantém coleção
vazia.

### `ServiceOrder`

Adicionar operação `recordStockAvailability(diagnosisId, assessments)` que localiza somente as execuções do Diagnosis
recém-criado e delega a substituição. IDs ausentes, extras ou duplicados são erro de integridade e não produzem estado
parcial.

## Caso de uso e transação

`PerformDiagnosisUseCase.execute(...)` continua `@Transactional` e seguirá esta ordem:

1. carregar a Service Order com `findByIdForUpdate`;
2. validar responsável planejado, autor efetivo e request;
3. chamar `serviceOrder.performDiagnosis(...)` e obter o novo `diagnosisId`;
4. selecionar as novas execuções e consolidar requirements repetidos com `Math.addExact`;
5. ordenar execuções e itens por UUID e chamar `RepairStockAssessmentApi.assessAndRecord(...)` uma vez;
6. aplicar todos os snapshots retornados ao aggregate;
7. persistir a Service Order e devolver `ServiceOrderResponse`.

Execuções sem requirements não são enviadas ao provider e recebem lista vazia. A API de RF27 participa da transação já
aberta; falha de validação, referência, persistência ou lock reverte Service Order, snapshots e Purchase Demands. Não
será usado `REQUIRES_NEW`, evento after-commit ou compensação.

O use case não tenta reserva. A revalidação da Estimate e a reserva posterior à aprovação permanecem comandos distintos.

## Persistência e dados

Criar migration Flyway aditiva com timestamp UTC da implementação e nome em lowercase `snake_case`. Nenhuma migration
existente será modificada.

Nova tabela `service_execution_stock_availability`:

| Coluna | Regra |
|---|---|
| `service_execution_id BINARY(16)` | FK local para `service_executions` |
| `stock_item_id BINARY(16)` | ID opaco, sem FK cross-module |
| `requested_quantity INTEGER` | Positiva |
| `observed_available_quantity INTEGER` | Não negativa |
| `shortage_quantity INTEGER` | Não negativa |
| `status VARCHAR(32)` | `AVAILABLE` ou `INSUFFICIENT_QUANTITY` |
| `observed_at TIMESTAMP(6)` | Obrigatório |

A primary key será `(service_execution_id, stock_item_id)`. Checks garantirão a coerência entre status e quantidades.
Registros anteriores permanecem com coleção vazia; não haverá backfill inferido nem consulta automática no startup.

Classificação de dados: **no seed required**. Testes usarão fixtures próprias.

## Contrato HTTP

O request de `POST /api/service-orders/{id}/diagnosis` não muda. `ServiceExecutionResponse` recebe o campo aditivo,
obrigatório e não nulo `stockAvailability`, sempre como array.

Exemplo de item:

```json
{
  "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
  "requestedQuantity": 3,
  "observedAvailableQuantity": 1,
  "shortageQuantity": 2,
  "status": "INSUFFICIENT_QUANTITY",
  "observedAt": "2026-08-25T15:30:00Z"
}
```

O mesmo campo aparecerá nas leituras de Service Order que reutilizam `ServiceExecutionResponse`. Isso é aditivo, mas
altera o schema OpenAPI e exige atualização conjunta de MockMvc, Postman e README.

## Falhas esperadas

Além das falhas existentes:

| Situação | HTTP | Código |
|---|---:|---|
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| Stock Item inativo | `409` | `STOCK_ITEM_INACTIVE` |
| Overflow na consolidação | `400` | `VALIDATION_ERROR` |
| Resultado interno incompleto ou incoerente | erro técnico | não expor tipo interno |

O adapter traduz somente resultados públicos de RF27 para erros já estáveis. Nenhuma mensagem inclui saldo de outros
itens, SQL, packages internos ou stack trace.

## Concorrência e consistência

- o lock da Service Order serializa dois diagnósticos concorrentes;
- RF27 bloqueia Stock Items e Purchase Demands em ordem determinística;
- a mesma transação garante correspondência entre snapshot persistido e observação gravada na demanda;
- disponibilidade é fotografia, não promessa: movimentações posteriores podem mudar o saldo;
- a unique key da demanda impede duplicação em retries ou revalidações concorrentes.

## Segurança

- nenhum campo novo é aceito do cliente; disponibilidade e instante vêm do servidor;
- o cliente não pode informar status, saldo observado, shortage ou Purchase Demand;
- IDs e quantidades são validados antes de qualquer persistência;
- o response não inclui Customer, Vehicle ou dados do fornecedor junto ao snapshot;
- a ausência atual de autenticação permanece finding de plataforma; não será simulada por header ou campo de request.

## Estratégia de testes

- domínio: invariantes, substituição completa, itens duplicados, mismatch e preservação de `PENDING`;
- aplicação: item suficiente, insuficiente, vários itens, execução sem requirement, item inexistente/inativo e rollback;
- integração modular: Diagnosis -> `RepairStockAssessmentApi` -> Purchase Demand na mesma transação;
- persistência: round-trip da coleção, constraints e startup com Flyway + Hibernate `validate`;
- concorrência: dois diagnósticos/revalidações sem demanda duplicada nem deadlock;
- HTTP: array vazio/não vazio, schema, `404`, `409` e compatibilidade dos campos existentes;
- estrutura: `ModuleStructureTest`, `make test`, `make verify` e cobertura do código alterado.

## Documentação afetada

Na implementação, atualizar OpenAPI, coleção Postman e o fluxo manual do README para demonstrar que a demanda nasce no
Diagnosis, antes da Estimate, sem alterar saldo ou status. O OpenAPI gerado permanece a fonte de verdade.

## Fora de escopo técnico

- reservar unidades durante o Diagnosis;
- alterar `AWAITING_ITEMS` antes da aprovação;
- criar Purchase Order automaticamente;
- resolver demanda por rejeição ou expiração da Estimate;
- RF28, RF29, RF30 ou política de prioridade após recebimento;
- corrigir os snapshots comerciais informados no request de Diagnosis, ainda registrado em BL-004.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por humano em 2026-08-25.
- [ ] Implementation Plan revisado somente depois da aprovação técnica.
- [ ] Segurança, contratos, migration, Modulith, testes e documentação verificados no plano futuro.

# Especificação Técnica: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |

## Gate de aprovação

Esta revisão deriva da especificação funcional aprovada em 2026-08-25. Nenhum código, migration ou contrato HTTP deste
delta pode ser alterado antes da aprovação humana explícita desta especificação técnica. O plano histórico permanece
`Stale`.

## Objetivo técnico

Estender `GenerateEstimateUseCase` para revalidar a disponibilidade dos Stock Requirements congelados, reconciliar as
Purchase Demands e persistir na Estimate a fotografia apresentada ao Customer.

A geração continuará:

- criando uma Estimate por Diagnosis;
- congelando os requirements na mesma transação;
- preservando snapshots comerciais;
- publicando `EstimateGenerated`;
- sem aprovar execução, reservar saldo ou criar Purchase Order.

## Contextos e fronteiras

`Estimate` permanece aggregate root de `servicelifecycle.estimate`; `ServiceOrder` continua fonte de verdade das Service
Executions. A geração já coordena os dois aggregates com lock da Service Order.

O caso de uso consumirá somente `RepairStockAssessmentApi`, do named interface público `purchase-demand-api`. Nenhum
tipo de domínio, repository ou persistência de Stock & Procurement será importado. A dependência entre módulos continua
unidirecional `servicelifecycle -> stockprocurement`.

Stock & Procurement recebe apenas `serviceExecutionId`, `stockItemId` e quantidade. Não recebe Customer, Vehicle,
Diagnosis, preço ou conteúdo comercial da Estimate.

## Contrato interno consumido

É o mesmo contrato definido pela revisão de `perform-diagnosis` e provido por RF27:

```text
RepairStockAssessmentApi.assessAndRecord(command) -> result
```

O comando contém as execuções e requirements consolidados. O resultado retorna, por execução e Stock Item:

- quantidade requerida;
- quantidade disponível observada;
- diferença positiva;
- `AVAILABLE` ou `INSUFFICIENT_QUANTITY`;
- `observedAt` definido pelo provider.

Para itens válidos, a chamada cria ou atualiza somente demandas insuficientes. Uma observação suficiente não resolve uma
demanda existente, pois não reserva as unidades; somente `StockReservationCreatedEvent` pode resolvê-la antes da compra.
O mapper de aplicação converte o status público para o enum local; o domínio de Estimate não armazena tipos de RF27.

## Modelo de domínio

### `EstimateStockAvailability`

Adicionar value object imutável em `estimate.domain.model`:

```text
stockItemId: UUID
requestedQuantity: int > 0
observedAvailableQuantity: int >= 0
shortageQuantity: int >= 0
status: AVAILABLE | INSUFFICIENT_QUANTITY
observedAt: Instant
```

As invariantes são iguais às de `StockAvailabilitySnapshot`: diferença zero quando disponível e diferença positiva
exata quando insuficiente. A Estimate copia valores do resultado público; ela não referencia o objeto mantido pela
Service Execution.

### `EstimateLine`

Cada linha continuará contendo serviço, preço e Stock Items comerciais. Adicionar uma coleção
`stockAvailability`, com no máximo uma entrada por Stock Item consolidado naquela execução.

Separar a coleção de disponibilidade da lista comercial evita duplicar ou distribuir artificialmente o saldo quando
existirem vários Stock Requirements para o mesmo item. Alterações posteriores no estoque ou na Service Order não mudam
a fotografia persistida na Estimate.

### `Estimate`

As invariantes existentes permanecem. A criação exige que todas as linhas tenham a avaliação correspondente ao conjunto
consolidado de requirements; execução sem requirement usa coleção vazia. Resultado ausente, extra ou duplicado é erro de
integridade e impede a criação inteira.

## Fluxo de aplicação e transação

`GenerateEstimateUseCase.execute(...)` permanece `@Transactional`:

1. carregar a Service Order com `findByIdForUpdate`;
2. validar Diagnosis aberto e unicidade da Estimate;
3. selecionar e ordenar as Service Executions do Diagnosis;
4. congelar seus Stock Requirements;
5. consolidar requirements repetidos por execução usando `Math.addExact`;
6. chamar `RepairStockAssessmentApi.assessAndRecord(...)` uma vez para o lote não vazio;
7. atualizar os snapshots informativos das Service Executions com o resultado mais recente;
8. criar as linhas da Estimate copiando snapshots comerciais e de disponibilidade;
9. persistir Service Order e Estimate;
10. publicar `EstimateGenerated` e devolver a resposta.

A API de RF27 participa da mesma transação. Qualquer falha reverte congelamento, snapshot, Estimate e Purchase Demands.
Não haverá `REQUIRES_NEW`, chamada after-commit nem compensação. Execuções sem requirements não acionam RF27.

O lock da Service Order preserva a serialização já exigida entre geração e `AttachStockRequirementUseCase`: o anexo
termina antes do congelamento ou é rejeitado depois dele.

## Persistência e dados

Criar migration Flyway aditiva com timestamp UTC da implementação, sem modificar migrations existentes.

Nova tabela `estimate_line_stock_availability`:

| Coluna | Regra |
|---|---|
| `estimate_line_id BINARY(16)` | FK para `estimate_lines` |
| `stock_item_id BINARY(16)` | ID opaco, sem FK cross-module |
| `requested_quantity INTEGER` | Positiva |
| `observed_available_quantity INTEGER` | Não negativa |
| `shortage_quantity INTEGER` | Não negativa |
| `status VARCHAR(32)` | `AVAILABLE` ou `INSUFFICIENT_QUANTITY` |
| `observed_at TIMESTAMP(6)` | Obrigatório |

A primary key será `(estimate_line_id, stock_item_id)`. Checks garantirão coerência de status e quantidades. O mapper
JPA fará cópia domínio ⇄ persistência sem reutilizar entities de Service Order.

Estimates legadas permanecem com coleção vazia. Não haverá backfill com o saldo atual, pois isso falsificaria a
fotografia histórica.

Classificação de dados: **no seed required**. Testes usam fixtures dedicadas.

## Evento `EstimateGenerated`

O contrato permanece inalterado: `eventId`, `occurredAt`, `estimateId`, `serviceOrderId`, `diagnosisId`, `customerId` e
`expiresAt`. O evento representa a criação válida da Estimate, não Purchase Demand, disponibilidade, reserva ou envio de
notificação.

Ele continua publicado dentro da transação após as persistências. Consumers after-commit só observam o evento depois do
commit; uma falha transacional anterior não produz notificação de Estimate inexistente.

## Contratos HTTP

Os endpoints permanecem:

- `POST /api/service-orders/{serviceOrderId}/estimates`;
- `GET /api/estimates/{estimateId}`.

O request de geração não muda. Cada `LineResponse` recebe o campo aditivo e não nulo `stockAvailability`, sempre array.

Exemplo:

```json
{
  "stockItemId": "e9ce63a8-d9aa-449b-9e12-a1e87ce089ca",
  "requestedQuantity": 3,
  "observedAvailableQuantity": 1,
  "shortageQuantity": 2,
  "status": "INSUFFICIENT_QUANTITY",
  "observedAt": "2026-08-25T15:35:00Z"
}
```

`stockItems` continua sendo o snapshot comercial original; `stockAvailability` é a fotografia operacional consolidada.
Nenhum preço, quantidade comercial ou campo existente será removido ou renomeado.

## Falhas esperadas

Além das falhas atuais:

| Situação | HTTP | Código |
|---|---:|---|
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| Stock Item inativo | `409` | `STOCK_ITEM_INACTIVE` |
| Overflow na consolidação | `400` | `VALIDATION_ERROR` |
| Resultado interno incompleto ou incoerente | erro técnico | não expor tipo interno |

Uma falha não cria Estimate parcial, não congela requirements e não altera demanda. Respostas não expõem SQL, saldo de
itens alheios ao orçamento ou packages internos.

## Concorrência e idempotência

- unicidade de `diagnosisId` continua protegida no domínio e banco;
- lock da Service Order serializa geração, anexo e decisão concorrentes;
- RF27 bloqueia Stock Items e Purchase Demands em ordem determinística;
- repetir geração após rollback faz nova observação e não duplica a demanda equivalente;
- repetir após commit continua falhando como Estimate já existente e não reavalia o snapshot histórico;
- o snapshot não promete unidades e não substitui a revalidação atômica na aprovação.

## Segurança

- saldo, status e instante são calculados pelo servidor e nunca aceitos no request;
- RF27 recebe somente IDs e quantidades operacionais;
- Estimate não expõe Customer ou Vehicle dentro da disponibilidade;
- nenhum log contém payload completo, preço, saldo de itens não solicitados ou dados pessoais;
- a ausência de autenticação permanece finding de plataforma e não será simulada nesta feature.

## OpenAPI, Postman e README

Na implementação:

- documentar `stockAvailability`, enum, quantidades e semântica de snapshot no Springdoc;
- atualizar testes do OpenAPI gerado;
- atualizar `Get estimate` e o fluxo de geração na coleção Postman;
- atualizar o README, pois a coleção muda, mostrando demanda criada no Diagnosis e revalidada na Estimate;
- preservar `/swagger-ui.html` e `/v3/api-docs` como fontes executáveis.

## Estratégia de testes

- domínio: invariantes e cópia imutável da disponibilidade por linha;
- aplicação: suficiente, insuficiente, itens repetidos, várias execuções, lista vazia e rollback integral;
- integração modular: Estimate -> RF27 atualiza a mesma demanda criada no Diagnosis;
- persistência: round-trip, Estimate legada vazia, constraints e Flyway + Hibernate `validate`;
- concorrência: geração versus anexo e revalidação versus criação de Purchase Order sem deadlock;
- HTTP/OpenAPI: campo aditivo, enum, `404`, `409` e preservação do contrato existente;
- regressão: `EstimateGenerated`, Notification, decisão de linhas e Stock Reservation permanecem verdes;
- qualidade: `ModuleStructureTest`, `make test`, `make verify` e cobertura do código alterado.

## Fora de escopo técnico

- reservar ou prometer Stock Items na geração;
- criar Purchase Order automaticamente;
- resolver demanda por rejeição ou expiração;
- modificar o contrato `EstimateGenerated`;
- expiração automática ou regra de 24h/48h;
- corrigir os snapshots comerciais recebidos no Diagnosis, ainda registrado em BL-004.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por humano em 2026-08-25.
- [ ] Implementation Plan revisado somente depois da aprovação técnica.
- [ ] Segurança, contratos, migration, Modulith, testes e documentação verificados no plano futuro.

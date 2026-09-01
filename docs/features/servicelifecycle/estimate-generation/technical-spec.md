# Especificação Técnica: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-27 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Decisão arquitetural | AD-013 — política de expiração de Estimate |
| Regra de negócio | BL-008 — totais consolidados da Estimate |

## Gate de aprovação

Esta revisão deriva da especificação funcional aprovada em 2026-08-25.

A revisão também incorpora a política de expiração ratificada na AD-013 e já implementada no domínio de Estimate.

O plano histórico permanece sujeito à atualização para refletir o comportamento efetivamente implementado.

## Objetivo técnico

Estender `GenerateEstimateUseCase` para revalidar a disponibilidade dos Stock Requirements congelados, reconciliar as
Purchase Demands e persistir na Estimate a fotografia apresentada ao Customer.

A geração também passa a calcular o `expiresAt` conforme a disponibilidade observada dos Stock Items:

- todos os Stock Items disponíveis: validade de 24 horas;
- qualquer Stock Item indisponível ou em quantidade insuficiente: validade de 48 horas.

O cálculo do prazo é responsabilidade de uma política de domínio dedicada e permanece separado do mecanismo que executa
a expiração automática.

A representação HTTP da Estimate também deve expor os totais comerciais derivados definidos pelo BL-008:

```text
lineTotal
    = servicePrice
    + soma(priceSnapshot * quantity)

total
    = soma(lineTotal)
```

Os cálculos utilizam exclusivamente os snapshots comerciais já congelados na Estimate e não realizam nova consulta ao
Service Catalog ou ao Stock & Procurement.

A geração continuará:

- criando uma Estimate por Diagnosis;
- congelando os requirements na mesma transação;
- preservando snapshots comerciais;
- preservando o snapshot de disponibilidade observado;
- calculando e persistindo `expiresAt`;
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

A política de expiração pertence ao domínio de Estimate e utiliza somente o snapshot já produzido durante a geração.
Ela não acessa diretamente Stock & Procurement.

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

O resultado dessa avaliação também fornece a informação necessária para a política de expiração decidir entre a janela
de 24 ou 48 horas. A política não realiza nova consulta ao estoque.

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
exata quando insuficiente.

A Estimate copia valores do resultado público; ela não referencia o objeto mantido pela Service Execution.

### `EstimateLine`

Cada linha continuará contendo serviço, preço e Stock Items comerciais. Adicionar uma coleção
`stockAvailability`, com no máximo uma entrada por Stock Item consolidado naquela execução.

Separar a coleção de disponibilidade da lista comercial evita duplicar ou distribuir artificialmente o saldo quando
existirem vários Stock Requirements para o mesmo item.

Alterações posteriores no estoque ou na Service Order não mudam a fotografia persistida na Estimate.

Para o BL-008, `EstimateLine` fornece `lineTotal()`, calculado como o preço do serviço somado ao valor dos Stock Items
da linha, multiplicando cada `priceSnapshot` por sua `quantity`.

Conceitualmente:

```text
stockItemsTotal
    = soma(priceSnapshot * quantity)

lineTotal
    = servicePrice + stockItemsTotal
```

Quando a linha não possui Stock Items, `lineTotal` corresponde ao próprio `servicePrice`.

O cálculo utiliza somente os dados comerciais já congelados na Estimate.

### `Estimate`

As invariantes existentes permanecem.

A criação exige que todas as linhas tenham a avaliação correspondente ao conjunto consolidado de requirements; execução
sem requirement usa coleção vazia. Resultado ausente, extra ou duplicado é erro de integridade e impede a criação inteira.

A Estimate persiste:

```text
createdAt: Instant
expiresAt: Instant
status: EstimateStatus
```

Após a geração válida, a Estimate é enviada e permanece em estado `SENT` enquanto aguarda decisão do Customer.

Quando uma Estimate ainda estiver `SENT` e o instante persistido em `expiresAt` for atingido, ela poderá realizar a
transição:

```text
SENT -> EXPIRED
```

A regra de domínio responsável pela transição não recalcula o prazo original.

O `total` consolidado exposto no contrato HTTP é derivado das linhas:

```text
total
    = soma(lineTotal)
```

Não é necessária persistência adicional de `lineTotal` ou `total`, pois os valores podem ser derivados
deterministicamente do snapshot comercial já persistido.

## Política de expiração

### `EstimateExpirationPolicy`

`EstimateExpirationPolicy` é o serviço de domínio responsável pelo cálculo do instante de expiração.

A política recebe:

```text
startsAt: Instant
lines: List<EstimateLine>
```

e retorna:

```text
expiresAt: Instant
```

A regra ratificada é:

```text
todos os Stock Items disponíveis
    -> startsAt + 24 horas

qualquer Stock Item indisponível
    -> startsAt + 48 horas
```

Para esta implementação, `INSUFFICIENT_QUANTITY` é considerado indisponibilidade para definição do prazo.

A política utiliza o snapshot de disponibilidade já presente nas linhas da Estimate.

Ela não:

- consulta repositories;
- consulta diretamente Stock & Procurement;
- altera estoque;
- cria Purchase Demand;
- cria Purchase Order;
- utiliza o relógio do sistema diretamente;
- executa a expiração da Estimate.

Essa separação permite testar o cálculo de prazo independentemente do scheduler.

## Referência temporal

O instante de geração é obtido por meio de `Clock` no `GenerateEstimateUseCase`:

```text
createdAt = clock.instant()
```

O mesmo instante é fornecido à política:

```text
expiresAt = expirationPolicy.calculateExpiresAt(createdAt, lines)
```

O uso de `Clock` permite testes determinísticos sem criar endpoint específico para manipulação ou simulação de horário.

O horário fictício necessário aos testes é obtido com um `Clock` controlado no próprio teste.

Em produção, o construtor utilizado pelo Spring utiliza `Clock.systemUTC()`.

## Fluxo de aplicação e transação

`GenerateEstimateUseCase.execute(...)` permanece `@Transactional`:

1. carregar a Service Order com `findByIdForUpdate`;
2. validar Diagnosis aberto e unicidade da Estimate;
3. selecionar as Service Executions do Diagnosis;
4. congelar seus Stock Requirements;
5. consolidar requirements repetidos por execução;
6. chamar `RepairStockAssessmentApi.assessAndRecord(...)` uma vez para o lote não vazio;
7. atualizar os snapshots informativos das Service Executions com o resultado mais recente;
8. criar as linhas da Estimate copiando snapshots comerciais e de disponibilidade;
9. obter `createdAt` por meio do `Clock`;
10. calcular `expiresAt` utilizando `EstimateExpirationPolicy`;
11. criar a Estimate com `createdAt`, `expiresAt` e as linhas calculadas;
12. marcar a Estimate como `SENT`;
13. persistir Estimate e Service Order;
14. publicar `EstimateGenerated` contendo o mesmo `expiresAt` persistido;
15. devolver a resposta.

A API de RF27 participa da mesma transação. Qualquer falha reverte congelamento, snapshot, Estimate e Purchase Demands.

Não haverá `REQUIRES_NEW`, chamada after-commit nem compensação. Execuções sem requirements não acionam RF27.

O lock da Service Order preserva a serialização já exigida entre geração e `AttachStockRequirementUseCase`: o anexo
termina antes do congelamento ou é rejeitado depois dele.

O cálculo de `lineTotal` e `total` não introduz operação externa ou nova leitura de dados; ambos são derivados dos
snapshots comerciais já existentes.

## Mecanismo de expiração automática

O cálculo de `expiresAt` e a execução da expiração são responsabilidades separadas.

A geração determina **quando** a Estimate vence.

O mecanismo automático determina **se esse instante já foi atingido**.

O scheduler periódico executa o caso de uso responsável por localizar Estimates vencidas.

Conceitualmente:

```text
EstimateExpirationScheduler
        |
        v
ExpireEstimatesUseCase
        |
        v
EstimateRepository
        |
        v
SENT com expiresAt <= now
        |
        v
Estimate.expire()
        |
        v
EXPIRED
```

O scheduler:

- não conhece a regra de 24 horas;
- não conhece a regra de 48 horas;
- não consulta disponibilidade de estoque;
- não recalcula `expiresAt`;
- não depende de ETA de fornecedor.

Ele utiliza exclusivamente o `expiresAt` persistido.

O repository fornece uma operação para localizar Estimates ainda `SENT` cujo `expiresAt` tenha sido atingido.

O caso de uso aplica a transição de domínio e persiste as Estimates expiradas.

Essa separação evita acoplamento entre a política de duração e o mecanismo periódico de expiração.

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

A primary key será `(estimate_line_id, stock_item_id)`.

Checks garantirão coerência de status e quantidades. O mapper JPA fará cópia domínio ⇄ persistência sem reutilizar
entities de Service Order.

Estimates legadas permanecem com coleção vazia. Não haverá backfill com o saldo atual, pois isso falsificaria a
fotografia histórica.

O `expiresAt` permanece persistido na própria Estimate e é utilizado como fonte de verdade pelo mecanismo de expiração.

O BL-008 não exige migration adicional. `lineTotal` e `total` são derivados de `servicePrice`, `priceSnapshot` e
`quantity`, já disponíveis no snapshot comercial persistido.

Classificação de dados: **no seed required**. Testes usam fixtures dedicadas.

## Evento `EstimateGenerated`

O contrato permanece:

```text
eventId
occurredAt
estimateId
serviceOrderId
diagnosisId
customerId
expiresAt
```

O `expiresAt` publicado deve ser exatamente o valor calculado pela política e persistido na Estimate.

O evento representa a criação válida da Estimate, não Purchase Demand, disponibilidade, reserva ou envio de notificação.

Ele continua publicado dentro da transação após as persistências. Consumers after-commit só observam o evento depois do
commit; uma falha transacional anterior não produz notificação de Estimate inexistente.

Notification recebe o prazo já calculado e não deve implementar novamente a política de 24h/48h.

O BL-008 não altera o contrato de `EstimateGenerated`.

## Contratos HTTP

Os endpoints permanecem:

- `POST /api/service-orders/{serviceOrderId}/estimates`;
- `GET /api/estimates/{estimateId}`.

Não é criado endpoint específico para calcular, alterar ou simular a expiração.

O request de geração não muda.

Cada `LineResponse` recebe o campo aditivo e não nulo `stockAvailability`, sempre array.

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

O `expiresAt` retornado pela representação da Estimate corresponde ao valor persistido durante a geração.

O BL-008 adiciona de forma aditiva ao response:

- `lineTotal: MoneyResponse` em cada `LineResponse`;
- `total: MoneyResponse` no nível raiz de `EstimateResponse`.

`lineTotal` é calculado como:

```text
servicePrice + soma(priceSnapshot * quantity)
```

`total` corresponde a:

```text
soma(lineTotal)
```

Os dois valores são calculados pelo servidor e não fazem parte do request.

Nenhum preço, quantidade comercial ou campo existente será removido ou renomeado.

## Falhas esperadas

Além das falhas atuais:

| Situação | HTTP | Código |
|---|---:|---|
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| Stock Item inativo | `409` | `STOCK_ITEM_INACTIVE` |
| Overflow na consolidação | `400` | `VALIDATION_ERROR` |
| Resultado interno incompleto ou incoerente | erro técnico | não expor tipo interno |

Uma falha não cria Estimate parcial, não congela requirements e não altera demanda.

O cálculo de `lineTotal` e `total` não introduz novo input ou novo código de erro HTTP.

Respostas não expõem SQL, saldo de itens alheios ao orçamento ou packages internos.

## Concorrência e idempotência

- unicidade de `diagnosisId` continua protegida no domínio e banco;
- lock da Service Order serializa geração, anexo e decisão concorrentes;
- RF27 bloqueia Stock Items e Purchase Demands em ordem determinística;
- repetir geração após rollback faz nova observação e não duplica a demanda equivalente;
- repetir após commit continua falhando como Estimate já existente e não reavalia o snapshot histórico;
- o snapshot não promete unidades e não substitui a revalidação atômica na aprovação;
- o scheduler atua somente sobre Estimates elegíveis à expiração;
- a transição para `EXPIRED` é condicionada ao estado `SENT`, evitando reexpirar uma Estimate já finalizada;
- `lineTotal` e `total` são determinísticos para o mesmo snapshot comercial.

## Segurança

- saldo, status e instante são calculados pelo servidor e nunca aceitos no request;
- RF27 recebe somente IDs e quantidades operacionais;
- Estimate não expõe Customer ou Vehicle dentro da disponibilidade;
- `expiresAt` é calculado pelo servidor e não pode ser fornecido pelo Customer;
- `lineTotal` e `total` são calculados pelo servidor e não podem ser fornecidos pelo Customer;
- nenhum endpoint administrativo é criado para forçar horário ou expiração;
- nenhum log contém payload completo, preço, saldo de itens não solicitados ou dados pessoais;
- a ausência de autenticação permanece finding de plataforma e não será simulada nesta feature.

## OpenAPI, Postman e README

Na implementação:

- documentar `stockAvailability`, enum, quantidades e semântica de snapshot no Springdoc;
- documentar `expiresAt` como instante limite para decisão do Customer;
- documentar `lineTotal` e `total` no contrato OpenAPI;
- atualizar testes do OpenAPI gerado para exigir `lineTotal` e `total`;
- atualizar `Generate estimate` e `Get estimate` na coleção Postman para validar os totais;
- atualizar o README com a leitura de `lineTotal` e `total` no fluxo manual;
- preservar `/swagger-ui.html` e `/v3/api-docs` como fontes executáveis.

Não é necessário adicionar endpoint específico para totais.

Não é necessário adicionar endpoint específico de expiração à coleção Postman.

## Estratégia de testes

### Política de expiração

Testes unitários de `EstimateExpirationPolicy` devem validar:

- todos os Stock Items disponíveis -> 24 horas;
- qualquer Stock Item indisponível -> 48 horas;
- cálculo determinístico a partir do instante recebido.

### Totais comerciais

`EstimateLineTest` deve validar:

- serviço somado aos Stock Items;
- multiplicação de `priceSnapshot` pela `quantity`;
- linha sem Stock Items;
- moeda do resultado.

`EstimateResponseTest` deve validar:

- exposição de `lineTotal` em cada linha;
- exposição de `total` na raiz da Estimate;
- soma dos `lineTotal` no valor consolidado.

`OpenApiContractTest` deve validar:

- `lineTotal` no schema das linhas;
- `total` no schema da Estimate;
- estrutura monetária usada pelos campos.

### Geração

Testes de `GenerateEstimateUseCase` devem validar:

- Estimate disponível recebe `expiresAt` de 24 horas;
- Estimate com indisponibilidade recebe `expiresAt` de 48 horas;
- o mesmo `expiresAt` é persistido e publicado em `EstimateGenerated`;
- suficiente, insuficiente, itens repetidos, várias execuções, lista vazia e rollback integral.

### Expiração automática

Testes de domínio, aplicação e scheduler devem validar:

- `SENT -> EXPIRED` quando o prazo é atingido;
- Estimate não vencida permanece inalterada;
- repository seleciona apenas Estimates elegíveis;
- scheduler delega a execução ao caso de uso;
- scheduler não contém regra de 24h/48h.

### Regressão

Também devem permanecer verdes:

- integração modular Estimate -> RF27;
- persistência e round-trip;
- `EstimateGenerated`;
- Notification;
- decisão de linhas;
- Stock Reservation;
- `ModuleStructureTest`;
- suíte completa de Estimate;
- `OpenApiContractTest`;
- `mvnw verify`;
- `git diff --check`.

## Fora de escopo técnico

- reservar ou prometer Stock Items na geração;
- criar Purchase Order automaticamente;
- resolver Purchase Demand por rejeição ou expiração;
- modificar estruturalmente o contrato `EstimateGenerated`;
- persistir redundantemente `lineTotal` ou `total`;
- criar endpoint separado para cálculo de totais;
- recalcular os totais por consulta viva ao Service Catalog ou Stock & Procurement;
- ETA real de reposição;
- integração com fornecedor externo para cálculo de prazo;
- prazo adicional baseado em ETA de fornecedor;
- endpoint para manipular relógio ou forçar expiração;
- corrigir os snapshots comerciais recebidos no Diagnosis, ainda registrado em BL-004.

## Gates

- [x] Functional Spec aprovada em 2026-08-25.
- [x] Technical Spec revisada e aprovada por humano em 2026-08-25.
- [x] Política AD-013 de 24h/48h implementada.
- [x] Política de cálculo isolada do mecanismo de expiração.
- [x] BL-008 implementado como cálculo derivado dos snapshots comerciais.
- [x] `lineTotal` exposto em cada linha da resposta.
- [x] `total` exposto na raiz da resposta.
- [x] Testes unitários da política de expiração verdes.
- [x] Testes de `GenerateEstimateUseCase` verdes.
- [x] `EstimateLineTest` verde.
- [x] `EstimateResponseTest` verde.
- [x] `OpenApiContractTest` verde.
- [x] OpenAPI, Postman e README atualizados para refletir os totais.
- [x] Implementation Plan atualizado para refletir a implementação.
- [x] `mvnw verify` completo executado após a atualização final.
- [x] `git diff --check` executado antes do commit.
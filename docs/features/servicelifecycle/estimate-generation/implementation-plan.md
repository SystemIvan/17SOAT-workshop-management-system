# Plano de Implementação: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Implemented |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-27 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |
| Decisão arquitetural | AD-013 — política de expiração de Estimate |
| Regra de negócio | BL-008 — totais consolidados da Estimate |

## Objetivo

Revalidar os requisitos congelados durante a geração da Estimate, reconciliar Purchase Demands e guardar em cada linha
a fotografia de disponibilidade independente do snapshot comercial, sem reservar estoque.

Implementar também a política ratificada de expiração da Estimate, utilizando a disponibilidade observada durante a
geração para determinar `expiresAt`:

- todos os Stock Items disponíveis: 24 horas;
- qualquer Stock Item indisponível ou com quantidade insuficiente: 48 horas.

A política de cálculo do prazo permanece separada do mecanismo automático de expiração. O scheduler utiliza somente o
`expiresAt` persistido e não conhece nem recalcula as durações de 24h ou 48h.

A representação HTTP da Estimate também deve apresentar os totais comerciais consolidados definidos pelo BL-008,
permitindo que o consumidor obtenha o valor de cada linha e o valor total da Estimate sem recalcular os snapshots
comerciais:

- `lineTotal = servicePrice + soma(priceSnapshot × quantity)` dos Stock Items da linha;
- `total = soma dos lineTotal` de todas as linhas da Estimate.

Os totais são derivados exclusivamente dos snapshots comerciais já congelados na Estimate. O cálculo não realiza
nova consulta ao Service Catalog ou ao Stock & Procurement e não altera os valores persistidos que compõem o snapshot.

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

### 3. Política de expiração — AD-013

- [x] Criar `EstimateExpirationPolicy` como serviço de domínio dedicado ao cálculo de `expiresAt`.
- [x] Definir prazo de 24 horas quando todos os Stock Items estiverem disponíveis.
- [x] Definir prazo de 48 horas quando qualquer Stock Item estiver indisponível ou possuir quantidade insuficiente.
- [x] Utilizar o snapshot de disponibilidade já produzido durante a geração, sem nova consulta ao estoque.
- [x] Utilizar o instante fornecido pelo `Clock` como referência para o cálculo.
- [x] Integrar `EstimateExpirationPolicy` ao `GenerateEstimateUseCase`.
- [x] Persistir o `expiresAt` calculado na Estimate.
- [x] Publicar em `EstimateGenerated` o mesmo `expiresAt` persistido.
- [x] Cobrir os cenários de 24h e 48h com testes automatizados.

### 4. Expiração automática

- [x] Permitir a transição de domínio `SENT -> EXPIRED`.
- [x] Disponibilizar busca de Estimates `SENT` cujo `expiresAt` tenha sido atingido.
- [x] Implementar `ExpireEstimatesUseCase` para executar e persistir a expiração.
- [x] Implementar `EstimateExpirationScheduler` para disparar periodicamente o caso de uso.
- [x] Habilitar scheduling na aplicação.
- [x] Manter o scheduler independente da política de 24h/48h.
- [x] Utilizar exclusivamente o `expiresAt` persistido para decidir se uma Estimate venceu.
- [x] Cobrir caso de uso e scheduler com testes automatizados.

### 5. Totais comerciais — BL-008

- [x] Adicionar em `EstimateLine` o cálculo de `lineTotal`.
- [x] Calcular `lineTotal` como `servicePrice + soma(priceSnapshot × quantity)` dos Stock Items.
- [x] Preservar a moeda do `servicePrice` na representação do `lineTotal`.
- [x] Garantir que linha sem Stock Items tenha `lineTotal` igual ao `servicePrice`.
- [x] Calcular o `total` consolidado da Estimate como a soma dos `lineTotal`.
- [x] Utilizar exclusivamente os snapshots comerciais congelados para os cálculos.
- [x] Não realizar leitura adicional de Service Catalog ou Stock & Procurement para calcular os totais.
- [x] Expor `lineTotal` em cada elemento de `lines` na resposta HTTP.
- [x] Expor `total` no nível raiz da resposta HTTP da Estimate.
- [x] Cobrir o cálculo de `lineTotal` com testes de domínio.
- [x] Cobrir o mapeamento de `lineTotal` e `total` com teste de `EstimateResponse`.

### 6. HTTP e documentação

- [x] Expor `stockAvailability` como array não nulo em criação e consulta de Estimate, mantendo `stockItems` comercial.
- [x] Manter `expiresAt` como parte da representação da Estimate.
- [x] Expor `lineTotal` como `MoneyResponse` em cada linha da Estimate.
- [x] Expor `total` como `MoneyResponse` consolidado no response da Estimate.
- [x] Não criar endpoint adicional para cálculo dos totais.
- [x] Não criar endpoint específico para manipular horário ou forçar expiração.
- [x] Atualizar a especificação funcional com a política ratificada da AD-013.
- [x] Atualizar a especificação técnica com a política e o mecanismo de expiração.
- [x] Atualizar as especificações afetadas pelo BL-008.
- [x] Atualizar este plano de implementação.
- [x] Atualizar as expectativas do contrato OpenAPI para `lineTotal` e `total`.
- [x] Atualizar a coleção Postman para validar `lineTotal` e `total`.
- [x] Atualizar o README com o fluxo manual e os totais retornados pela Estimate.
- [x] Manter a documentação da revalidação de disponibilidade da Estimate.

### 7. Segurança e qualidade

- [x] Revisar dados calculados pelo servidor, erros estáveis, ausência de dados pessoais e a lacuna de autenticação do
  baseline.
- [x] Manter `expiresAt` calculado exclusivamente pelo servidor.
- [x] Manter `lineTotal` e `total` calculados exclusivamente pelo servidor.
- [x] Não aceitar `lineTotal` ou `total` como entrada controlada pelo cliente.
- [x] Utilizar `Clock` nos testes em vez de endpoint para manipulação de horário.
- [x] Executar testes unitários de `EstimateExpirationPolicy`.
- [x] Executar testes de `GenerateEstimateUseCase`.
- [x] Executar testes de `EstimateLine`.
- [x] Executar teste de `EstimateResponse`.
- [x] Executar testes de contrato OpenAPI.
- [x] Executar suíte relacionada a Estimate.
- [x] Executar `mvnw verify` completo após a atualização final.
- [x] Executar `git diff --check` antes do commit.

## Critérios de conclusão

- [x] Estimate preserva sua fotografia e não altera saldo/reserva.
- [x] Leitura suficiente não resolve Purchase Demand; reserva criada continua sendo a transição de resolução.
- [x] `expiresAt` é calculado durante a geração da Estimate.
- [x] Estimate com todos os itens disponíveis recebe prazo de 24 horas.
- [x] Estimate com qualquer item indisponível recebe prazo de 48 horas.
- [x] O mecanismo automático de expiração não recalcula a política de duração.
- [x] Estimate `SENT` vencida pode transicionar para `EXPIRED`.
- [x] O scheduler utiliza o `expiresAt` persistido como fonte de verdade.
- [x] Não existe endpoint adicional para simular passagem de tempo.
- [x] Cada linha da Estimate apresenta `lineTotal`.
- [x] `lineTotal` inclui o preço do serviço e os preços dos Stock Items multiplicados por suas quantidades.
- [x] Linha sem Stock Items apresenta `lineTotal` igual ao preço do serviço.
- [x] A representação da Estimate apresenta `total` consolidado.
- [x] `total` corresponde à soma dos `lineTotal`.
- [x] `lineTotal` e `total` são derivados dos snapshots comerciais congelados.
- [x] O cálculo dos totais não exige nova leitura de catálogo ou estoque.
- [x] O contrato OpenAPI contempla `lineTotal` e `total`.
- [x] A coleção Postman valida a presença e consistência de `lineTotal` e `total`.
- [x] O README documenta os totais no fluxo manual de Estimate.
- [x] Testes específicos da política de expiração passaram com 2 testes, 0 falhas e 0 erros.
- [x] Testes de `GenerateEstimateUseCase` passaram com 6 testes, 0 falhas e 0 erros.
- [x] `EstimateLineTest` passou com 2 testes, 0 falhas e 0 erros.
- [x] `EstimateResponseTest` passou com 1 teste, 0 falhas e 0 erros.
- [x] `OpenApiContractTest` passou com 16 testes, 0 falhas e 0 erros.
- [x] Build completo final validado após atualização da documentação.
- [x] Diff final validado antes do commit.

## Evidências e segurança

A implementação mantém o cálculo de `expiresAt` no servidor e não permite que o Customer determine ou altere o prazo.

A política utiliza o snapshot de disponibilidade produzido durante a geração:

```text
todos disponíveis
    -> createdAt + 24h

qualquer indisponibilidade
    -> createdAt + 48h
```

Os totais comerciais também são calculados pelo servidor exclusivamente a partir do snapshot já pertencente à
Estimate:

```text
lineTotal
    = servicePrice
    + soma(priceSnapshot * quantity)

total
    = soma(lineTotal)
```

Assim, `lineTotal` e `total` são dados derivados da fotografia comercial da Estimate e não dependem de leitura viva
posterior do Service Catalog ou dos Stock Items.

Validação da implementação do BL-008 em 2026-08-27:

- `EstimateLineTest`: 2 testes, 0 falhas e 0 erros;
- `EstimateResponseTest`: 1 teste, 0 falhas e 0 erros;
- suíte relacionada a Estimate: 56 testes, 0 falhas e 0 erros;
- `OpenApiContractTest`: 16 testes, 0 falhas e 0 erros;
- contrato OpenAPI atualizado para contemplar `lineTotal` e `total`;
- coleção Postman atualizada para validar os totais;
- README atualizado com o cálculo e a validação manual dos totais.

Validação anterior da política de expiração em 2026-08-26 após integrar `dev`:

- conflito de `GenerateEstimateUseCase` resolvido preservando a política AD-013 e o mapeamento de
  `StockItemNotFoundException` introduzido em `dev`;
- testes focados: 12 testes, 0 falhas e 0 erros;
- `mvnw clean verify`: 635 testes, 0 falhas, 0 erros e 0 ignorados;
- `ModuleStructureTest`: 2 testes, 0 falhas e 0 erros;
- JaCoCo: todos os limites de cobertura atendidos;
- `git diff --check`: sem erros.

Revisão de segurança: `lineTotal` e `total` são calculados pelo servidor e não introduzem nova entrada controlada pelo
cliente. Nenhuma credencial, dado pessoal, migração ou dependência foi adicionada pelo BL-008. O prazo permanece
calculado no servidor, e o tratamento de Stock Item inexistente preserva o erro HTTP estável sem expor detalhes
internos.
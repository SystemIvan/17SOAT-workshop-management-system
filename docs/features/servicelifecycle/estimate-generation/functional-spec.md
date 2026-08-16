# Especificação Funcional: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-16 |
| Aprovado por | Matheus Campagnone |
| Aprovado em | 2026-08-16 |
| Referências | `docs/Architecture.md`, `docs/Architecture-Decisions.md`, DDD/Event Storming do Miro, `stock-domain-foundation` |

## Problema e resultado esperado

Após o Technician realizar um Diagnosis, a Service Order já possui as Service Executions identificadas e seus respectivos Stock Requirements.

O Customer precisa receber uma representação comercial estável desse diagnóstico antes de autorizar qualquer execução.

Esta feature cria uma Estimate a partir de um ciclo de Diagnosis já existente, preservando um snapshot comercial do trabalho identificado.

Ao final da geração:

- existe uma Estimate identificável e associada à Service Order;
- a Estimate representa exatamente um ciclo de Diagnosis;
- as Service Executions daquele Diagnosis são representadas comercialmente sem transferir a propriedade do trabalho para a Estimate;
- os dados comerciais necessários ficam congelados como snapshot;
- é produzido o evento `EstimateGenerated`, permitindo que a capability de Notification reaja sem conhecer a implementação interna de Estimate.

## Atores e cenários

- Um Technician realiza um Diagnosis em uma Service Order.
- O Diagnosis produz uma ou mais Service Executions.
- Cada Service Execution pode possuir Stock Requirements.
- O sistema gera uma Estimate correspondente ao Diagnosis aberto.
- A Estimate mantém snapshots comerciais das Service Executions e dos Stock Requirements relevantes.
- Após a criação bem-sucedida da Estimate, o sistema produz `EstimateGenerated`.
- A capability de Notification poderá reagir ao evento e comunicar o Customer.

## Regras de negócio

### Relação com Diagnosis

- Uma Estimate somente pode ser gerada para um Diagnosis existente.
- O Diagnosis deve possuir pelo menos uma Service Execution.
- Uma Estimate representa exatamente um ciclo de Diagnosis.
- Um mesmo ciclo de Diagnosis não pode produzir múltiplas Estimates independentes.
- Um novo Diagnosis somente poderá ser tratado como novo ciclo após o anterior estar coberto por uma Estimate.

### Fonte de verdade

- A Service Order continua sendo a fonte de verdade do trabalho.
- A Estimate não passa a ser dona das Service Executions.
- A Estimate representa uma fotografia comercial do Diagnosis em determinado momento.
- Referências entre Estimate e Service Order/Service Execution são feitas por IDs.

### Snapshot comercial

- Cada serviço incluído na Estimate deve preservar os dados comerciais apresentados ao Customer.
- Stock Requirements são referenciados a partir das Service Executions do Diagnosis.
- Dados comerciais provenientes de Stock Items devem ser copiados para a Estimate quando necessários à apresentação comercial.
- Alterações posteriores em Service Catalog ou Stock Item não podem modificar retroativamente uma Estimate já gerada.

### Evento de domínio

- `EstimateGenerated` somente ocorre após a criação válida da Estimate.
- O evento identifica a Estimate, sua Service Order, o Diagnosis e o Customer relacionado.
- O evento não representa envio de notificação, aprovação, rejeição, reserva de Stock ou início de execução.
- Notification é consumidora do evento e não deve recalcular regras internas da Estimate.

### Expiração

- A Estimate deve permitir representar uma data limite por meio de `expiresAt`.
- A duração que determina `expiresAt` não será fixada nesta feature enquanto a decisão arquitetural correspondente permanecer aberta.
- Nenhuma regra de 24 horas, 48 horas ou prazo de reposição será hard-coded nesta entrega.

## Fora de escopo

- decisão do Customer sobre as linhas da Estimate;
- aprovação ou rejeição de Service Executions;
- fechamento da Estimate;
- expiração automática ou scheduler;
- regra definitiva de duração do prazo de aprovação;
- reserva, liberação ou consumo de Stock Items;
- Purchase Order;
- execução e tracking das Service Executions;
- implementação do canal de Notification;
- alteração do fluxo de Diagnosis já existente;
- revisão ou versionamento posterior de Estimate.

## Critérios de aceite

- [ ] Uma Estimate pode ser gerada para um Diagnosis existente com pelo menos uma Service Execution.
- [ ] A Estimate referencia a Service Order e o Diagnosis que a originaram.
- [ ] As Service Executions daquele Diagnosis são representadas na Estimate como snapshots comerciais.
- [ ] Dados comerciais copiados para a Estimate não dependem de leitura viva posterior do catálogo.
- [ ] Não é possível gerar mais de uma Estimate para o mesmo ciclo de Diagnosis.
- [ ] A Service Order continua sendo a fonte de verdade das Service Executions.
- [ ] Após a criação válida da Estimate é produzido `EstimateGenerated`.
- [ ] `EstimateGenerated` contém dados suficientes para identificar Estimate, Service Order, Diagnosis e Customer.
- [ ] A geração da Estimate não aprova Service Executions nem reserva Stock.
- [ ] A feature permite representar `expiresAt` sem hard-code da duração do prazo.
- [ ] Nenhum comportamento de Notification é implementado dentro do domínio de Estimate.


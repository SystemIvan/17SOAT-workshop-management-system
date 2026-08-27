# Especificação Funcional: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Referências | Architecture docs; Miro; `stock-domain-foundation`; `purchase-order-creation`; AD-013 |

## Revisão material por disponibilidade e Purchase Demand

Esta revisão, aprovada em 2026-08-25, exige que a Estimate apresente a disponibilidade revalidada dos Stock Requirements
e reconcilie a Purchase Demand criada desde o Diagnosis. Ela substitui funcionalmente a aprovação de 2026-08-20; a
especificação técnica anterior não cobre esse comportamento.

## Delta de `stock-item-reservation` incorporado

Além de congelar o snapshot comercial, a geração válida da Estimate deve congelar, na mesma transação, o conjunto de
`StockRequirement` de cada Service Execution apresentada e revalidar sua disponibilidade. A partir desse momento não se
anexa, remove nem altera requirement nessa execução. A consulta não cria reserva, não compromete unidades e não altera
`availableQuantity`; uma insuficiência apenas cria ou atualiza a Purchase Demand pertencente a Stock & Procurement.

## Problema e resultado esperado

Após o Technician realizar um Diagnosis, a Service Order já possui as Service Executions identificadas e seus
respectivos Stock Requirements.

O Customer precisa receber uma representação comercial estável desse diagnóstico antes de autorizar qualquer execução.

Esta feature cria uma Estimate a partir de um ciclo de Diagnosis já existente, preservando um snapshot comercial do
trabalho identificado.

Ao final da geração:

- existe uma Estimate identificável e associada à Service Order;
- a Estimate representa exatamente um ciclo de Diagnosis;
- as Service Executions daquele Diagnosis são representadas comercialmente sem transferir a propriedade do trabalho
  para a Estimate;
- os dados comerciais necessários ficam congelados como snapshot;
- o conjunto de `StockRequirement` de cada Service Execution apresentada fica congelado para uma futura
  tentativa de reserva;
- cada item apresenta a disponibilidade observada na geração e eventual quantidade insuficiente, sem prometer saldo ao
  Customer;
- insuficiências revalidam a mesma Purchase Demand registrada desde o Diagnosis, sem criar ordem de compra automática;
- a Estimate recebe um `expiresAt` calculado conforme a disponibilidade de estoque observada na geração;
- Estimates com todos os itens disponíveis possuem janela de aprovação de 24 horas;
- Estimates com qualquer item indisponível possuem janela de aprovação de 48 horas;
- Estimates ainda em estado `SENT` são automaticamente expiradas quando `expiresAt` é atingido;
- é produzido o evento `EstimateGenerated`, permitindo que a capability de Notification reaja sem conhecer a
  implementação interna de Estimate.

## Atores e cenários

- Um Technician realiza um Diagnosis em uma Service Order.
- O Diagnosis produz uma ou mais Service Executions.
- Cada Service Execution pode possuir Stock Requirements.
- O Diagnosis já pode ter registrado Purchase Demands para requirements insuficientes.
- O sistema gera uma Estimate correspondente ao Diagnosis aberto.
- A Estimate revalida e mantém snapshots comerciais e informativos das Service Executions e dos Stock Requirements.
- O prazo de aprovação é calculado a partir da disponibilidade observada dos Stock Requirements.
- Após a criação bem-sucedida da Estimate, o sistema produz `EstimateGenerated`.
- A capability de Notification poderá reagir ao evento e comunicar o Customer.
- Enquanto a Estimate permanecer `SENT`, o sistema verifica periodicamente se seu `expiresAt` foi atingido.
- Ao atingir `expiresAt`, uma Estimate ainda `SENT` passa para `EXPIRED`.

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
- A geração válida da Estimate congela o conjunto de `StockRequirement` apresentado para cada execução,
  no mesmo comando que persiste a Estimate.
- Depois do congelamento, nenhum requirement daquela execução pode ser anexado, removido ou alterado;
  uma necessidade posterior exige novo Diagnosis, nova Service Execution e nova Estimate.
- Dados comerciais provenientes de Stock Items devem ser copiados para a Estimate quando necessários à apresentação
  comercial.
- Alterações posteriores em Service Catalog ou Stock Item não podem modificar retroativamente uma Estimate já gerada.

### Snapshot de disponibilidade

- A disponibilidade é revalidada para todos os Stock Requirements congelados na geração.
- O snapshot distingue quantidade suficiente de insuficiente e registra as quantidades requerida e disponível observada.
- O snapshot é informativo: não reserva unidades, não garante atendimento futuro e não altera estado da Service
  Execution.
- Insuficiência cria ou atualiza a mesma demanda `PENDING_REPAIR` identificada por Service Execution e Stock Item.
- A decisão posterior do Customer não altera retroativamente o snapshot apresentado.
- Rejeição ou expiração da Estimate não resolve a demanda, pois a insuficiência de estoque foi concretamente observada.

### Evento de domínio

- `EstimateGenerated` somente ocorre após a criação válida da Estimate.
- O evento identifica a Estimate, sua Service Order, o Diagnosis e o Customer relacionado.
- O evento não representa envio de notificação, aprovação, rejeição, reserva de Stock, Purchase Order ou início de
  execução.
- Notification é consumidora do evento e não deve recalcular regras internas da Estimate.

### Expiração

- Toda Estimate gerada deve possuir uma data limite de aprovação representada por `expiresAt`.
- O instante utilizado como referência para o cálculo é o momento de geração da Estimate.
- Quando todos os Stock Items necessários estiverem disponíveis, `expiresAt` deve corresponder a 24 horas após a
  geração da Estimate.
- Quando qualquer Stock Item necessário estiver indisponível ou possuir quantidade insuficiente, `expiresAt` deve
  corresponder a 48 horas após a geração da Estimate.
- A decisão de 24 ou 48 horas é realizada durante a geração da Estimate a partir do snapshot de disponibilidade.
- O valor calculado de `expiresAt` é persistido na própria Estimate.
- O mecanismo responsável pela expiração automática não recalcula a política de duração.
- O mecanismo de expiração considera apenas Estimates no estado `SENT`.
- Uma Estimate `SENT` cujo `expiresAt` tenha sido atingido deve transicionar para `EXPIRED`.
- A verificação de Estimates vencidas é executada periodicamente por um scheduler.
- O scheduler utiliza o `expiresAt` persistido como fonte de verdade para determinar se a Estimate venceu.
- A política de cálculo do prazo e o mecanismo de expiração permanecem separados: a primeira determina `expiresAt`;
  o segundo executa a transição de estado quando esse instante é atingido.

## Fora de escopo

- decisão do Customer sobre as linhas da Estimate;
- aprovação ou rejeição de Service Executions;
- reserva, liberação ou consumo de Stock Items;
- seleção, criação ou envio de Purchase Order; somente o registro/reconciliação da Purchase Demand pertence à integração
  funcional com RF27;
- execução e tracking das Service Executions;
- implementação do canal de Notification;
- alteração do fluxo de Diagnosis já existente;
- revisão ou versionamento posterior de Estimate;
- cálculo de ETA real de fornecedor ou prazo adicional de reposição.

## Critérios de aceite

- [x] Uma Estimate pode ser gerada para um Diagnosis existente com pelo menos uma Service Execution.
- [x] A Estimate referencia a Service Order e o Diagnosis que a originaram.
- [x] As Service Executions daquele Diagnosis são representadas na Estimate como snapshots comerciais.
- [x] Dados comerciais copiados para a Estimate não dependem de leitura viva posterior do catálogo.
- [ ] A geração válida congela o conjunto de `StockRequirement` de cada Service Execution apresentada na
      mesma transação que cria a Estimate.
- [ ] Um requirement não pode ser anexado, removido ou alterado depois do congelamento; necessidade
      posterior segue novo ciclo de Diagnosis e Estimate.
- [x] Não é possível gerar mais de uma Estimate para o mesmo ciclo de Diagnosis.
- [x] A Service Order continua sendo a fonte de verdade das Service Executions.
- [x] Após a criação válida da Estimate é produzido `EstimateGenerated`.
- [x] `EstimateGenerated` contém dados suficientes para identificar Estimate, Service Order, Diagnosis e Customer.
- [x] A geração da Estimate não aprova Service Executions nem reserva Stock.
- [ ] A geração revalida todos os Stock Requirements e apresenta um snapshot informativo de disponibilidade.
- [ ] Uma insuficiência atualiza a mesma Purchase Demand originada no Diagnosis, sem duplicação e sem criar Purchase
      Order automaticamente.
- [ ] Rejeição ou expiração da Estimate não elimina a Purchase Demand correspondente.
- [x] A Estimate possui `expiresAt` persistido.
- [x] Quando todos os Stock Items estão disponíveis, a Estimate possui prazo de aprovação de 24 horas.
- [x] Quando qualquer Stock Item está indisponível, a Estimate possui prazo de aprovação de 48 horas.
- [x] A política de duração é aplicada durante a geração da Estimate.
- [x] O mecanismo de expiração utiliza o `expiresAt` persistido e não recalcula a duração.
- [x] Uma Estimate `SENT` vencida pode transicionar automaticamente para `EXPIRED`.
- [x] A verificação automática de expiração é executada periodicamente por scheduler.
- [x] Nenhum comportamento de Notification é implementado dentro do domínio de Estimate.
# Especificação Funcional: Registrar Diagnóstico

| Campo | Valor |
|---|---|
| Feature | `perform-diagnosis` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Referências | Architecture §2.3; Miro; `assign-diagnosis-assignee`; `diagnosis-authorship`; RF27 |

> **Nota:** o baseline deste documento é retroativo. A feature já está implementada em produção
> (`ServiceOrder.performDiagnosis`, `PerformDiagnosisUseCase`, `POST /api/service-orders/{id}/diagnosis`)
> e foi identificada sem passar pelo gate SDD do `AGENTS.md`, no mesmo levantamento que originou a
> documentação retroativa de `service-order-creation` (RF09). A revisão funcional aprovada em 2026-08-25 ainda não foi
> implementada: verificar disponibilidade após registrar os requirements e alimentar RF27 antes da decisão da Estimate.

## Revisão material por insuficiência observada no Diagnosis

Ao concluir o registro das Service Executions e seus Stock Requirements, o sistema verifica a disponibilidade canônica
dos Stock Items. A verificação produz um snapshot informativo na execução e, quando a quantidade requerida superar a
disponível, registra ou atualiza uma Purchase Demand `PENDING_REPAIR` em Stock & Procurement.

A demanda representa falta concreta de estoque para uma necessidade diagnosticada. Ela independe da autorização para
executar o serviço e, por isso, não é eliminada se o Customer rejeitar ou deixar expirar a Estimate. O gatilho nunca
cria Purchase Order automaticamente; essa decisão continua pertencendo ao Stock Manager.

## Deltas materiais de features dependentes incorporados nesta aprovação

`assign-diagnosis-assignee` exige que a Service Order tenha `diagnosisAssigneeId` antes de aceitar cada Diagnosis.
`diagnosis-authorship` exige `diagnosedByTechnicianId` no request e registra um `diagnosedAt` único, gerado pelo
sistema, em todas as Service Executions do lote. O responsável planejado pode divergir do autor efetivo e nenhum dos
dois preenche `assignedTechnicianId`.

Classificação: **material** — há nova precondição, o request incompatível ganha campo obrigatório, a resposta de cada
execução ganha autoria e instante, e o fluxo passa a validar a existência do autor. As duas specs das features
dependentes são as fontes de verdade; esta spec não replica regras de lock, persistência ou auditoria. A revisão foi
aprovada por humano em 2026-08-22 e a especificação técnica revisada foi aprovada na sequência. O plano histórico
permanece `Stale` porque não cobre a implementação dos deltas.

## Problema e resultado esperado

Depois que uma Service Order é criada (RF09), um Technician examina o veículo e identifica um ou mais
serviços necessários. Esse levantamento — o diagnóstico — precisa virar um ou mais `ServiceExecution`
rastreáveis dentro da Service Order, que servirão de base para gerar um Estimate (feature
`estimate-generation`) e, depois de aprovado, para a execução em si (RF19+).

Ao final do registro de diagnóstico:

- cada item informado vira um `ServiceExecution` novo, com status inicial `PENDING`;
- os Stock Requirements de cada execução são confrontados com o saldo disponível sem reservar unidades;
- insuficiências registram ou atualizam Purchase Demands antes da decisão do Customer;
- todos os `ServiceExecution` criados nesse registro compartilham o mesmo `diagnosisId`, que identifica
  o lote;
- a Service Order passa a ter um diagnóstico "aberto" (`openDiagnosisId`), e seu status derivado passa a
  `IN_DIAGNOSIS`;
- cada item pode opcionalmente declarar necessidades de peça/estoque, associadas ao `ServiceExecution`
  correspondente.

## Atores e cenários

- Um Technician (ou o atendente em nome dele) registra o diagnóstico de uma Service Order já existente, após o
  planejamento de um responsável e informando o autor efetivo e um ou mais serviços identificados.
- O sistema cria um `ServiceExecution` por item informado e abre um diagnóstico associando todos eles.
- Um diagnóstico só pode ficar aberto por vez: uma nova tentativa de registrar diagnóstico enquanto o
  anterior ainda não gerou Estimate é rejeitada.

## Regras de negócio

### Diagnóstico é um lote, não um item isolado

- Um único registro de diagnóstico aceita uma lista de um ou mais itens; cada item vira um
  `ServiceExecution` distinto.
- Todos os `ServiceExecution` criados no mesmo registro compartilham um `diagnosisId` gerado pelo
  sistema — não informado pelo chamador.

### Um diagnóstico aberto por vez

- Uma Service Order só pode ter um diagnóstico aberto por vez (`openDiagnosisId` não nulo).
- Tentar registrar um novo diagnóstico enquanto o anterior ainda está aberto (nenhum Estimate gerado
  para ele) é rejeitado.
- O diagnóstico deixa de estar aberto quando um Estimate é gerado a partir dele (feature
  `estimate-generation`, fora do escopo deste documento) — a transição de fechamento não é
  responsabilidade desta feature.

### Planejamento e autoria do diagnóstico

- Antes do registro, a Service Order deve conter `diagnosisAssigneeId`; sem o planejamento, o comando é rejeitado
  sem criar Service Executions.
- O request informa `diagnosedByTechnicianId`, que deve identificar um Technician existente. Ele é declaratório até
  existir identidade autenticada e pode divergir do responsável planejado.
- O sistema define uma única vez `diagnosedAt` para todo o lote. O instante não é informado pelo chamador.
- Os detalhes completos pertencem, respectivamente, às features `assign-diagnosis-assignee` e
  `diagnosis-authorship`.

### Cada item do diagnóstico

- `catalogServiceId`, `name` e `price` são obrigatórios por item — referenciam o serviço do catálogo
  identificado pelo Technician, mas são copiados como valor (mesmo padrão de referência por ID +
  snapshot já usado no `VehicleSnapshot` da RF09) — não há leitura viva do Service Catalog no momento do
  diagnóstico.
- `stockRequirements` é opcional por item — permite já indicar, no momento do diagnóstico, que aquele
  serviço depende de uma ou mais peças em estoque. Ausência de `stockRequirements` significa que o
  serviço não depende de peça.
- Cada `ServiceExecution` criado começa no status `PENDING`.

### Disponibilidade informativa e demanda de compra

- Requirements repetidos do mesmo Stock Item na execução são consolidados para verificar a quantidade total necessária.
- A consulta registra quantidade requerida, quantidade disponível observada, eventual diferença positiva e instante da
  observação.
- A verificação não cria Stock Reservation, não altera `availableQuantity` e não muda o status `PENDING` da execução.
- Uma diferença positiva cria ou atualiza uma única demanda por Service Execution e Stock Item.
- Item inexistente, inativo ou quantidade inválida é problema de referência ou integridade, não uma demanda de compra.
- A Estimate revalida o snapshot antes de apresentá-lo; a aprovação revalida novamente antes da reserva.

### Efeito no status da Service Order

- Registrar um diagnóstico move o status derivado da Service Order para `IN_DIAGNOSIS`.

## Rastreabilidade: cobre também RF18

Registrar um segundo (ou N-ésimo) lote de diagnóstico para a mesma Service Order — o cenário da
**RF18** ("Registrar novo diagnóstico/reparo adicional durante a execução") — já funciona com este
mesmo `performDiagnosis`/`POST /api/service-orders/{id}/diagnosis`, sem nenhuma mudança de código.
A guarda de "diagnóstico único aberto por vez" (ver "Um diagnóstico aberto por vez" abaixo) verifica
apenas `openDiagnosisId`, não o status das `ServiceExecution` de lotes anteriores. Assim que o
diagnóstico anterior é totalmente decidido (todas as suas `ServiceExecution` aprovadas/rejeitadas via
`decide-estimate-lines`, RF15/RF16), `openDiagnosisId` volta a `null` — mesmo que uma execução aprovada
já esteja `IN_PROGRESS` ou `COMPLETED` — e um novo diagnóstico pode ser registrado, coexistindo com as
execuções em andamento de lotes anteriores. Coberto por
`ServiceOrderTest#rf18_canRegisterANewDiagnosisWhileAnEarlierExecutionIsInProgress`.

## Fora de escopo

- geração do Estimate a partir do diagnóstico (feature `estimate-generation`, já especificada
  separadamente);
- fechamento do diagnóstico aberto (acontece como efeito colateral da geração do Estimate);
- adicionar um `ServiceExecution` avulso a um diagnóstico já aberto sem reabrir o fluxo completo (método
  de domínio `addServiceExecution` existe no código, mas não tem nenhum caller de produção hoje — fora
  do escopo deste documento);
- validação de existência do `catalogServiceId` no Service Catalog (`registration`) no momento do
  diagnóstico;
- execução dos serviços diagnosticados (RF19+).
- criação automática de Purchase Order; o Diagnosis apenas produz a necessidade selecionável por RF27.

## Critérios de aceite

- [x] Um diagnóstico com um ou mais itens pode ser registrado para uma Service Order existente.
- [x] Cada item do diagnóstico vira um `ServiceExecution` com status `PENDING`.
- [x] Todos os `ServiceExecution` de um mesmo registro compartilham o mesmo `diagnosisId`.
- [x] Registrar um diagnóstico move o status da Service Order para `IN_DIAGNOSIS`.
- [ ] O Diagnosis verifica os Stock Requirements consolidados sem reservar ou alterar saldo.
- [ ] Uma insuficiência cria ou atualiza uma única Purchase Demand por Service Execution e Stock Item.
- [ ] A demanda permanece aberta mesmo se a futura Estimate for rejeitada ou expirar.
- [x] Tentar registrar diagnóstico em uma Service Order que já tem um diagnóstico aberto é rejeitado.
- [x] Tentar registrar diagnóstico em uma Service Order inexistente resulta em erro de "não encontrado".
- [x] Uma lista de itens vazia ou ausente é rejeitada como erro de validação.
- [ ] Diagnosis sem responsável planejado ou sem autor efetivo válido é rejeitado sem persistência parcial; a
      evidência será incluída após a reaprovação e implementação dos deltas.

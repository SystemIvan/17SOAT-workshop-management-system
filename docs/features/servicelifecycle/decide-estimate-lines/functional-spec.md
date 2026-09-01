# Especificação Funcional: Decidir Linhas de uma Estimate (Aprovar/Rejeitar por ServiceExecution)

| Campo | Valor |
|---|---|
| Feature | `decide-estimate-lines` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Referências | `docs/Architecture.md` §2.3 (RF09–RF18); `docs/Architecture-Decisions.md` AD-008; features `estimate-generation`, `perform-diagnosis` |

## Delta proposto por `stock-item-reservation`

A aprovação comercial de uma linha com requirements congelados passa a solicitar automaticamente sua
reserva integral em Stock & Procurement. A indisponibilidade não reverte a decisão do Customer: a
execução permanece autorizada em `AWAITING_ITEMS`; o sucesso associa uma única `stockReservationId` e a
leva a `READY`. Uma decisão em lote preserva sua validação comercial atômica, mas cada tentativa de
reserva é independente das demais execuções aprovadas.

## Rastreabilidade: cobre RF15 e RF16

Esta feature implementa, no mesmo endpoint (`POST /api/estimates/{estimateId}/decisions`), tanto a
**RF15** ("Aprovar uma ou mais ServiceExecutions de uma Estimate — decisão por linha") quanto a
**RF16** ("Reprovar uma ou mais ServiceExecutions de uma Estimate — execuções reprovadas ficam com
status terminal `rejected`"). Não há separação de endpoint entre aprovar e rejeitar: `decision` aceita
`APPROVED` ou `REJECTED` por linha na mesma requisição em lote (ver "Regras de negócio" abaixo), e
ambas as decisões já usam o mesmo caso de uso (`DecideEstimateLinesUseCase`) e os mesmos métodos de
domínio (`ServiceOrder.authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`).

O caráter terminal de `REJECTED` exigido pela RF16 já é garantido pelo domínio existente
(`ServiceExecution.reject`/guardas de status) e coberto por teste: uma `ServiceExecution` já `REJECTED`
não pode ser decidida de novo (nem `APPROVED` nem `REJECTED`) — `IllegalStateException` →
`409/INVALID_STATE_TRANSITION`, sem persistir nenhuma decisão da chamada
(`DecideEstimateLinesUseCaseTest#rejectsWhenServiceExecutionIsNotPendingAndAppliesNoDecision`).

## Nota sobre AD-008

`docs/Architecture-Decisions.md` lista **AD-008** (granularidade de aprovação da Estimate) como
`Resolved` (ratificada pelo time em 2026-08-23): Option A — decisão por linha, `ServiceExecution` a
`ServiceExecution`, com a Estimate rastreada por status (`draft`/`sent`/`closed`/`expired`) em vez de um
único approve/reject na Estimate inteira. O **código já implementa a metade "por linha" da Option A**:
`ServiceOrder.authorizeExecutionFromEstimate(estimateId, serviceExecutionId)` e
`ServiceOrder.rejectExecutionFromEstimate(estimateId, serviceExecutionId)` já existem e já operam por
`ServiceExecution`, não pela Estimate como um todo. Esta feature expõe essa capacidade já modelada; **não**
introduz o campo de status na Estimate (`draft`/`sent`/`closed`/`expired`) — essa parte de AD-008, embora já
aprovada, permanece um gap de implementação separado (fora desta entrega; ver `Fora de escopo` abaixo) e
precisa de sua própria feature sob o gate de SDD do `AGENTS.md`. A fonte de verdade da decisão continua
sendo o status do `ServiceExecution` dentro da `ServiceOrder` — o mesmo princípio já registrado em
`estimate-generation`.

## Problema e resultado esperado

Depois que uma Estimate é gerada (`estimate-generation`, RF13), o Customer precisa decidir, serviço a
serviço, quais `ServiceExecution` autoriza e quais rejeita — nem sempre aprova o orçamento inteiro de uma
vez. Esta feature permite registrar essa decisão para uma ou mais linhas de uma Estimate existente em uma
única chamada.

Ao final da decisão:

- cada `ServiceExecution` aprovada muda de `PENDING` para `AUTHORIZED`; quando possui requirements
  congelados, solicita automaticamente uma reserva integral e fica `READY` no sucesso ou
  `AWAITING_ITEMS` na indisponibilidade; execuções sem requirements ficam `READY` sem reserva. Uma
  execução rejeitada muda para `REJECTED`;
- o `statusSnapshot` da Service Order é recalculado;
- o diagnóstico deixa de estar "aberto" quando nenhuma `ServiceExecution` daquele lote continuar
  `PENDING` (comportamento já existente em `ServiceOrder`, não modificado por esta feature).

## Atores e cenários

- O Customer (ou o atendente em nome dele) decide, para uma Estimate já gerada, aprovar ou rejeitar uma
  ou mais das `ServiceExecution` que ela representa.
- O sistema valida que cada `ServiceExecution` informada pertence de fato àquela Estimate.
- O sistema aplica a decisão de cada linha na `ServiceOrder` (fonte de verdade), não na Estimate.

## Regras de negócio

### A decisão é em lote, por linha

- Uma única chamada aceita uma lista de uma ou mais decisões, cada uma associando um
  `serviceExecutionId` a uma decisão (`APPROVED` ou `REJECTED`).
- Não é obrigatório decidir todas as linhas da Estimate na mesma chamada — o Customer pode aprovar
  algumas agora e decidir o restante depois, em uma chamada separada.
- A chamada é tudo-ou-nada: se qualquer linha da lista falhar validação (linha não pertence à Estimate)
  ou conflito de estado (`ServiceExecution` não está `PENDING`), nenhuma decisão da chamada é aplicada.
- Informar o mesmo `serviceExecutionId` mais de uma vez na mesma chamada é rejeitado como erro de
  validação (decisão ambígua).

### Toda linha decidida deve pertencer à Estimate

- Cada `serviceExecutionId` informado deve corresponder a uma linha existente daquela Estimate
  (`EstimateLine.serviceExecutionId`). Referenciar um `serviceExecutionId` que não pertence à Estimate
  é rejeitado como "não encontrado" — mesmo que a `ServiceExecution` exista em outra Service Order/Estimate.

### Só é possível decidir uma ServiceExecution pendente

- Só é possível aprovar ou rejeitar uma `ServiceExecution` que ainda está `PENDING`. Uma
  `ServiceExecution` já decidida (`AUTHORIZED`, `REJECTED` ou qualquer status posterior) não pode ser
  decidida de novo por esta feature — comportamento já garantido pelos métodos de domínio existentes
  (`ServiceExecution.authorize`/`reject`, que exigem `PENDING`).

### Efeito de cada decisão

- `APPROVED` autoriza a execução e, quando há requirements congelados, solicita sua reserva integral. O
  sucesso associa um `stockReservationId` e leva a execução a `READY`; indisponibilidade preserva a
  aprovação e a mantém em `AWAITING_ITEMS`. Execução sem requirements fica `READY` sem reserva.
- `REJECTED` chama `ServiceOrder.rejectExecutionFromEstimate(estimateId, serviceExecutionId)`: a
  `ServiceExecution` fica `REJECTED` (estado terminal).
- Em ambos os casos, o `statusSnapshot` da Service Order é recalculado ao final.

## Fora de escopo

- introduzir um campo de status na Estimate (`draft`/`sent`/`closed`/`expired` — parte de AD-008 já aprovada,
  mas ainda não implementada; requer feature própria);
- fechar a Estimate automaticamente quando todas as linhas forem decididas;
- expiração da Estimate (AD-013, feature `send-estimate`/RF14, ainda não implementada);
- consumo, liberação, reserva parcial ou reposição de estoque; a tentativa integral automática é o único
  efeito de reserva incorporado por `stock-item-reservation`;
- notificar o Customer ou qualquer outro ator sobre a decisão;
- decisão parcial de uma linha (ex.: aprovar parte da quantidade de uma `StockRequirement`);
- alterar o fluxo de geração da Estimate (`estimate-generation`, já especificada separadamente).

## Critérios de aceite

- [ ] Uma ou mais decisões (`APPROVED`/`REJECTED`) podem ser registradas para linhas de uma Estimate
      existente em uma única chamada.
- [ ] `APPROVED` autoriza a `ServiceExecution` e solicita automaticamente a reserva integral de seus
      requirements congelados; no sucesso associa `stockReservationId` e fica `READY`.
- [ ] Indisponibilidade preserva a aprovação comercial, não cria reserva parcial e mantém a execução em
      `AWAITING_ITEMS`; o resultado de outra execução aprovada no mesmo lote é independente.
- [ ] `REJECTED` move a `ServiceExecution` para `REJECTED`.
- [ ] O `statusSnapshot` da Service Order é recalculado após a chamada.
- [ ] Decidir um `serviceExecutionId` que não pertence à Estimate é rejeitado como "não encontrado".
- [ ] Decidir uma `ServiceExecution` que não está `PENDING` é rejeitado como conflito de estado.
- [ ] Repetir o mesmo `serviceExecutionId` na mesma chamada é rejeitado como erro de validação.
- [ ] Se qualquer linha da chamada falhar, nenhuma decisão da chamada é aplicada (tudo-ou-nada).
- [ ] Decidir linhas de uma Estimate inexistente é rejeitado como "não encontrado".
- [ ] Uma lista de decisões vazia ou ausente é rejeitada como erro de validação.

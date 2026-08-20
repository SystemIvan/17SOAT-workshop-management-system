# Especificação Funcional: Decidir Linhas de uma Estimate (Aprovar/Rejeitar por ServiceExecution)

| Campo | Valor |
|---|---|
| Feature | `decide-estimate-lines` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Referências | `docs/Architecture.md` §2.3 (RF09–RF18); `docs/Architecture-Decisions.md` AD-008; features `estimate-generation`, `perform-diagnosis` |

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
`Team Decision Required`, com a Option A recomendada (decisão por linha, `ServiceExecution` a
`ServiceExecution`, em vez da Estimate inteira de uma vez). O **código já implementa a Option A** hoje:
`ServiceOrder.authorizeExecutionFromEstimate(estimateId, serviceExecutionId)` e
`ServiceOrder.rejectExecutionFromEstimate(estimateId, serviceExecutionId)` já existem e já operam por
`ServiceExecution`, não pela Estimate como um todo — só não têm nenhum caso de uso ou endpoint HTTP que
os chame ainda. Esta feature expõe essa capacidade já modelada; **não** introduz um campo de status na
Estimate (`draft`/`sent`/`closed`/`expired`, o restante do escopo de AD-008), que continua em aberto e
fora desta entrega. A fonte de verdade da decisão continua sendo o status do `ServiceExecution` dentro
da `ServiceOrder` — o mesmo princípio já registrado em `estimate-generation`.

## Problema e resultado esperado

Depois que uma Estimate é gerada (`estimate-generation`, RF13), o Customer precisa decidir, serviço a
serviço, quais `ServiceExecution` autoriza e quais rejeita — nem sempre aprova o orçamento inteiro de uma
vez. Esta feature permite registrar essa decisão para uma ou mais linhas de uma Estimate existente em uma
única chamada.

Ao final da decisão:

- cada `ServiceExecution` decidido muda de `PENDING` para `AUTHORIZED` (e, a partir daí, sua prontidão é
  recalculada — vira `READY` ou `AWAITING_PART` conforme suas necessidades de estoque) ou para
  `REJECTED`, conforme a decisão informada para aquela linha;
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

- `APPROVED` chama `ServiceOrder.authorizeExecutionFromEstimate(estimateId, serviceExecutionId)`: a
  `ServiceExecution` fica `AUTHORIZED` e, em seguida, sua prontidão é recalculada conforme suas
  `StockRequirement` (`READY` se não houver pendência de reserva, `AWAITING_PART` caso contrário).
- `REJECTED` chama `ServiceOrder.rejectExecutionFromEstimate(estimateId, serviceExecutionId)`: a
  `ServiceExecution` fica `REJECTED` (estado terminal).
- Em ambos os casos, o `statusSnapshot` da Service Order é recalculado ao final.

## Fora de escopo

- introduzir um campo de status na Estimate (`draft`/`sent`/`closed`/`expired` — AD-008 continua aberto);
- fechar a Estimate automaticamente quando todas as linhas forem decididas;
- expiração da Estimate (AD-013, feature `send-estimate`/RF14, ainda não implementada);
- reserva de estoque (efeito de `READY` já existente, não desta feature);
- notificar o Customer ou qualquer outro ator sobre a decisão;
- decisão parcial de uma linha (ex.: aprovar parte da quantidade de uma `StockRequirement`);
- alterar o fluxo de geração da Estimate (`estimate-generation`, já especificada separadamente).

## Critérios de aceite

- [ ] Uma ou mais decisões (`APPROVED`/`REJECTED`) podem ser registradas para linhas de uma Estimate
      existente em uma única chamada.
- [ ] `APPROVED` move a `ServiceExecution` para `AUTHORIZED` e recalcula sua prontidão
      (`READY`/`AWAITING_PART`).
- [ ] `REJECTED` move a `ServiceExecution` para `REJECTED`.
- [ ] O `statusSnapshot` da Service Order é recalculado após a chamada.
- [ ] Decidir um `serviceExecutionId` que não pertence à Estimate é rejeitado como "não encontrado".
- [ ] Decidir uma `ServiceExecution` que não está `PENDING` é rejeitado como conflito de estado.
- [ ] Repetir o mesmo `serviceExecutionId` na mesma chamada é rejeitado como erro de validação.
- [ ] Se qualquer linha da chamada falhar, nenhuma decisão da chamada é aplicada (tudo-ou-nada).
- [ ] Decidir linhas de uma Estimate inexistente é rejeitado como "não encontrado".
- [ ] Uma lista de decisões vazia ou ausente é rejeitada como erro de validação.

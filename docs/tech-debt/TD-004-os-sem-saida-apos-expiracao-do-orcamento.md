# TD 004: OS sem saída depois da expiração do orçamento

**Status:** Open  
**Date:** 2026-10-06  
**Reported by:** Santiago Silvestre  
**Affected areas:** `servicelifecycle` (`estimate`, `serviceorder`)  
**Related decisions:** AD-013 (expiração de orçamento, `docs/Architecture-Decisions.md`);
`docs/features/servicelifecycle/awaiting-approval-status/functional-spec.md` (decisão registrada 1)

---

## Contexto

A correção `awaiting-approval-status` (2026-10-06) passou a devolver a OS para `IN_DIAGNOSIS` ("Diagnóstico") quando o
orçamento enviado expira sem decisão. Foi a decisão do responsável: a oficina retoma a OS a partir do diagnóstico. Na
mesma spec ficou decidido que permitir de fato um novo orçamento depois da expiração é **fora do escopo** dessa
correção, e que a lacuna seria registrada aqui.

## A dívida

Depois da expiração, a OS fica em "Diagnóstico", mas nenhum comando permite seguir adiante:

- o diagnóstico continua aberto (`openDiagnosisId` preenchido) e suas execuções continuam `PENDING`;
- não é possível gerar um novo orçamento para esse diagnóstico, porque já existe um (agora `EXPIRED`);
- não é possível registrar um novo diagnóstico, porque o anterior ainda está aberto;
- não é possível decidir as linhas, porque o orçamento não está mais `SENT`.

A OS só pode ficar parada, o que contradiz a intenção de "voltar para Diagnóstico para a oficina retomar".

## Evidência

- `estimate/application/usecase/GenerateEstimateUseCase.java`: `estimateRepository.existsByDiagnosisId(diagnosisId)`
  lança `IllegalStateException("Estimate already exists for diagnosis: ...")`, sem considerar o status do orçamento
  existente.
- `serviceorder/domain/model/ServiceOrder.java`: `performDiagnosis(...)` lança
  `IllegalStateException("A diagnosis is already open without an Estimate generated for it")` enquanto
  `openDiagnosisId != null`.
- `estimate/application/usecase/DecideEstimateLinesUseCase.java`: rejeita decisões quando o orçamento não está
  `SENT`.

## Impacto se não for pago

Toda OS cujo orçamento expira fica sem caminho operacional dentro do sistema. A oficina não consegue refazer o
orçamento, e a OS permanece indefinidamente em "Diagnóstico" na listagem e no acompanhamento do cliente. Quanto mais
orçamentos expirarem, maior o número de OSs paradas.

## Opções de encaminhamento

### Opção A: permitir novo orçamento quando o anterior do mesmo diagnóstico expirou

`GenerateEstimateUseCase` passa a ignorar orçamentos `EXPIRED` na verificação de duplicidade. Esforço pequeno.
Exige rever a regra "um orçamento por diagnóstico" e o efeito na Purchase Demand e na revalidação de estoque.

### Opção B: encerrar o diagnóstico expirado e permitir um novo diagnóstico

A expiração rejeita ou cancela as execuções `PENDING` daquele diagnóstico e fecha `openDiagnosisId`, liberando um
novo `performDiagnosis`. Esforço médio. Muda o significado das execuções (de "pendente" para "cancelada por
expiração").

### Opção C: ação explícita de "reabrir orçamento" pela oficina

Comando novo que gera um orçamento substituto e invalida o expirado. Esforço maior, com endpoint e contrato novos.

## Recomendação

Avaliar a Opção A primeiro, por ser a menor mudança que destrava a OS sem alterar o significado das execuções. Toda
opção altera regra de negócio aprovada (orçamento por diagnóstico, ciclo de vida das execuções), então segue o
workflow de SDD normal do `AGENTS.md`, começando por uma `functional-spec.md`.

## Custo de não decidir agora

O sistema continua funcionando para OSs cujo orçamento é decidido dentro do prazo. Enquanto a dívida existir, não
tratar a volta a "Diagnóstico" como um caminho de reorçamento disponível: na demonstração e na documentação, a
expiração deve ser apresentada como encerramento sem retomada automática.

---

**Last Updated:** 2026-10-06  
**Status:** Open

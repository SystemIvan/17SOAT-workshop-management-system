# Especificação Funcional: Anexar Necessidade de Estoque a uma ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `attach-stock-requirement` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Referências | `docs/Architecture.md` §2.3 (RF09–RF18); feature `perform-diagnosis` (RF11) |

## Problema e resultado esperado

Ao registrar um diagnóstico (`perform-diagnosis`, RF11), cada item pode já declarar as peças,
consumíveis ou insumos (`StockRequirement`) que o serviço vai exigir. Na prática, porém, um Technician
nem sempre sabe de antemão tudo o que vai precisar: pode identificar uma necessidade adicional de peça
depois que o diagnóstico já foi registrado — por exemplo, ao começar a desmontar o veículo, ou já durante
a execução do serviço.

Esta feature expõe essa capacidade como uma operação isolada: anexar um novo `StockRequirement` a um
`ServiceExecution` que já existe, sem precisar reabrir ou repetir o diagnóstico. O domínio já modela o
conceito (`StockRequirement`, `ServiceExecution.attachStockRequirement`, `ServiceOrder.attachStockRequirement`),
mas nenhum caso de uso ou endpoint HTTP o expõe hoje — este é exatamente o gap que a feature fecha.

Ao final da operação:

- o `ServiceExecution` alvo passa a ter mais um `StockRequirement` na sua lista, com `reserved = false`
  (a reserva em si é efeito de um fluxo do `stockprocurement`, fora do escopo desta feature);
- se o `ServiceExecution` estava com status `READY` (todas as peças já reservadas), o novo item, ainda
  não reservado, faz o status recuar para `AWAITING_PART` — a leitura de "pronto para iniciar" não pode
  ficar desatualizada;
- o status derivado da Service Order (`statusSnapshot`) é recalculado, podendo passar a `AWAITING_PART`
  pelo mesmo motivo.

## Atores e cenários

- Um Technician (ou o atendente em nome dele) anexa uma nova necessidade de peça/consumível/insumo a um
  `ServiceExecution` já existente de uma Service Order.
- O sistema valida que a Service Order e o `ServiceExecution` existem e que o `ServiceExecution` ainda
  está em um status que faz sentido receber novas necessidades de estoque.
- O `StockRequirement` é anexado por valor (snapshot de nome e preço, como já ocorre no diagnóstico) —
  não há leitura viva do módulo `stockprocurement` no momento do anexo.

## Regras de negócio

### Campos obrigatórios do StockRequirement

Mesmo contrato já usado em `perform-diagnosis` (`StockRequirementRequest`):

- `stockItemId` — referência por ID ao item de estoque (`stockprocurement`), sem import de pacote
  interno de outro módulo.
- `type` — classificação (`StockItemType`).
- `quantity` — obrigatório, deve ser `> 0` (invariante já existente no VO `StockRequirement`).
- `nameSnapshot` e `priceSnapshot` — copiados como valor no momento do anexo; mudanças futuras de preço
  no catálogo de estoque não afetam este `StockRequirement` já anexado.
- O novo `StockRequirement` sempre começa com `reserved = false`; não é possível anexar um item já
  marcado como reservado por esta operação — reserva é um efeito de outro fluxo (`applyStockReservation`).

### ServiceExecution deve estar em status compatível

- Permitido para `ServiceExecution` nos status `PENDING`, `AUTHORIZED`, `READY`, `AWAITING_PART` e
  `IN_PROGRESS`.
- Rejeitado para `COMPLETED` e `REJECTED` (estados terminais) — não faz sentido declarar uma nova
  necessidade de peça para um serviço já concluído ou rejeitado.

### Múltiplas necessidades para o mesmo item de estoque

- Anexar um `StockRequirement` cujo `stockItemId` já existe na lista do `ServiceExecution` **não**
  mescla quantidades — cria uma nova linha independente, com seu próprio `reserved = false`. Este é o
  mesmo comportamento já existente para a lista de `stockRequirements` de um item de diagnóstico
  (nenhuma deduplicação lá também).

### Efeito no status do ServiceExecution e da Service Order

- Anexar um `StockRequirement` recalcula a prontidão do `ServiceExecution`: se o status era `READY`, o
  novo item não reservado faz o status recuar para `AWAITING_PART`. Para os demais status permitidos
  (`PENDING`, `AUTHORIZED`, `AWAITING_PART`, `IN_PROGRESS`), anexar não altera o status do
  `ServiceExecution` em si.
- Em seguida, o `statusSnapshot` da Service Order é recalculado (mesmo padrão de todo comando que afeta
  um `ServiceExecution`).

## Fora de escopo

- reserva do item de estoque (`applyStockReservation`) — feature separada, fluxo do `stockprocurement`;
- validação de existência do `stockItemId` no módulo `stockprocurement` no momento do anexo (mesmo
  padrão de "referência por ID sem leitura viva" já aceito para `catalogServiceId` no diagnóstico);
- remoção ou edição de um `StockRequirement` já anexado;
- mesclar/deduplicar `StockRequirement`s com o mesmo `stockItemId`;
- anexar `StockRequirement` durante o próprio registro de diagnóstico (já coberto por `perform-diagnosis`,
  RF11).

## Critérios de aceite

- [ ] Um `StockRequirement` válido pode ser anexado a um `ServiceExecution` existente em status
      `PENDING`, `AUTHORIZED`, `READY`, `AWAITING_PART` ou `IN_PROGRESS`.
- [ ] O `StockRequirement` anexado começa sempre com `reserved = false`.
- [ ] Anexar a um `ServiceExecution` em `COMPLETED` ou `REJECTED` é rejeitado.
- [ ] Anexar a um `ServiceExecution` em `READY` faz seu status recuar para `AWAITING_PART`.
- [ ] O `statusSnapshot` da Service Order é recalculado após o anexo.
- [ ] Anexar a uma Service Order ou `ServiceExecution` inexistente resulta em erro de "não encontrado".
- [ ] `quantity <= 0` ou campos obrigatórios ausentes são rejeitados como erro de validação.
- [ ] Anexar um segundo `StockRequirement` com o mesmo `stockItemId` de um já existente cria uma nova
      linha, sem mesclar quantidades.

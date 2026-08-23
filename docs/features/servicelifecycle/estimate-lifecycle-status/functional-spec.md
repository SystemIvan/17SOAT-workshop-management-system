# Especificação Funcional: Status de Ciclo de Vida da Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-lifecycle-status` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-23 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-23 |
| Referências | `docs/Architecture-Decisions.md` AD-008 (Resolved), AD-013 (Team Decision Required); features `estimate-generation`, `decide-estimate-lines` |

## Problema e resultado esperado

`AD-008` foi ratificada: a Estimate deve ser rastreada por um status próprio — `draft`, `sent`, `closed`,
`expired` — em vez de permanecer sem nenhum estado explícito. Hoje a `Estimate` (`Estimate.java`) não tem
campo de status; a decisão fica inteiramente representada no status de cada `ServiceExecution` dentro da
`ServiceOrder` (`decide-estimate-lines`), e a Estimate nunca transiciona nem sinaliza quando o ciclo de
decisão terminou.

Esta feature adiciona o campo de status à `Estimate` e as transições que já podem ser determinadas com as
decisões arquiteturais hoje resolvidas, sem inventar comportamento que dependa de uma decisão ainda aberta
(`AD-013`, mecanismo/duração de expiração).

Ao final:

- toda `Estimate` tem um status explícito e observável (`draft`, `sent`, `closed`, `expired`);
- o status transiciona automaticamente para `closed` quando todas as suas linhas (`ServiceExecution`) saem
  de `PENDING`;
- `decide-estimate-lines` passa a rejeitar decisões sobre uma Estimate que já não esteja `sent` (ex.: já
  `closed` ou `expired`);
- o valor `expired` existe no modelo e pode ser lido/exposto, mas **nenhum mecanismo automático dispara essa
  transição** nesta entrega — isso depende de `AD-013`, que continua `Team Decision Required`.

## Atores e cenários

- O sistema gera uma Estimate a partir de um Diagnosis (`estimate-generation`, já implementado).
- A Estimate é publicada para o Customer (via `EstimateGenerated`, já implementado) e passa a estar
  visível/decidível.
- Um Customer decide (aprova/rejeita) linhas da Estimate uma a uma (`decide-estimate-lines`, já
  implementado).
- Quando a última linha pendente é decidida, a Estimate fecha automaticamente.
- Uma tentativa de decidir uma linha de uma Estimate já fechada ou expirada é rejeitada.

## Regras de negócio

### Valores de status

- `draft`: Estimate criada mas ainda não publicada para o Customer. Nesta entrega, é um estado transitório
  interno ao comando de geração — a `Estimate` nasce em `draft` e transiciona para `sent` antes de ser
  persistida, na mesma transação de `estimate-generation`, imediatamente antes da publicação de
  `EstimateGenerated`. Não existe hoje (nem é criado por esta feature) um caso de uso que mantenha uma
  Estimate em `draft` de forma persistente/observável — isso exigiria uma ação explícita de "enviar depois",
  fora de escopo.
- `sent`: Estimate publicada para o Customer e com pelo menos uma linha (`ServiceExecution`) ainda
  `PENDING`. É o único status em que `decide-estimate-lines` aceita novas decisões.
- `closed`: todas as linhas da Estimate saíram de `PENDING` (cada uma `AUTHORIZED` ou `REJECTED`). Transição
  automática, disparada pelo próprio `decide-estimate-lines` ao aplicar a última decisão pendente — não
  requer ação separada do Customer nem endpoint próprio.
- `expired`: reservado para quando o prazo de decisão (`expiresAt`) se esgota antes de todas as linhas serem
  decididas. O valor existe no modelo desde já; o gatilho automático (scheduler, duração) é escopo de
  `AD-013` e **não é implementado nesta feature**.

### Transições permitidas

- `draft → sent`: automática, dentro do comando de geração da Estimate (delta em `estimate-generation`).
- `sent → closed`: automática, quando a última linha `PENDING` é decidida (delta em `decide-estimate-lines`).
- `sent → expired`: modelada como transição válida no domínio, mas sem gatilho implementado nesta entrega.
- Nenhuma outra transição é permitida (`closed` e `expired` são terminais).

### Impacto em `decide-estimate-lines`

- Decidir uma ou mais linhas de uma Estimate cujo status não seja `sent` deve falhar com o mesmo padrão de
  erro já usado para transições de `ServiceExecution` inválidas (`409/INVALID_STATE_TRANSITION` — ver
  `technical-spec.md` de `decide-estimate-lines`), sem aplicar nenhuma decisão da chamada.
- Esta validação é adicional à já existente (cada `ServiceExecution` alvo deve estar `PENDING`); as duas
  continuam necessárias porque uma Estimate pode, em tese, ter menos linhas decididas do que o total sem
  ainda estar `closed`.

### Consistência com `estimate-generation`

- Não altera nenhuma regra de congelamento de snapshot comercial ou de `StockRequirement` já definida em
  `estimate-generation`.
- Não fixa duração nem regra de `expiresAt` — isso permanece bloqueado por `AD-013`, como já registrado em
  `estimate-generation`.

## Fora de escopo

- gatilho automático de expiração (scheduler, mensageria atrasada ou equivalente) — depende de `AD-013`;
- qualquer endpoint ou caso de uso que publique uma Estimate em `draft` de forma persistente/observável
  antes de `sent` ("salvar rascunho" para revisão interna antes de enviar ao Customer);
- reabertura de uma Estimate `closed` ou `expired`;
- reparo adicional / nova Estimate para necessidades pós-fechamento (permanece um novo ciclo de Diagnosis,
  como já registrado em `estimate-generation`);
- exposição do status da Estimate em endpoints de tracking do Épico 3 (`track-execution`) — a fonte de
  verdade de execução continua sendo `ServiceOrder`/`ServiceExecution`, não a Estimate.

## Critérios de aceite

- [ ] Toda Estimate gerada por `estimate-generation` é persistida com status `sent`.
- [ ] Uma Estimate com pelo menos uma linha `PENDING` permanece `sent`.
- [ ] Ao decidir (aprovar ou rejeitar) a última linha `PENDING` de uma Estimate, o status transiciona
      automaticamente para `closed` na mesma transação da decisão.
- [ ] Uma tentativa de decidir qualquer linha de uma Estimate `closed` falha sem aplicar nenhuma decisão da
      chamada, com o código de erro padrão de transição de estado inválida.
- [ ] O valor `expired` é representável no modelo e nas respostas de leitura, mas nenhum teste ou
      comportamento desta feature depende de uma transição automática para `expired`.
- [ ] Nenhuma regra de `estimate-generation` relativa a snapshot, `StockRequirement` ou `expiresAt` é
      alterada.

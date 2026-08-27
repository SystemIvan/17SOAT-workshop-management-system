# Especificação Funcional: Fechamento de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-closing` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Referências | RF28, Miro e SDDs de Stock & Procurement (links abaixo) |

Referências:

- [RF25–RF30 no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679722227775);
- [Domain Storytelling de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831);
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720);
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027);
- `docs/features/stockprocurement/purchase-order-creation/`;
- `docs/features/stockprocurement/README.md`;
- `docs/backlog.md`, especialmente BL-002.

## Estado desta descoberta

RF28 e RF29 formam uma sequência operacional curta e podem ser planejadas e implementadas de forma coordenada, mas
preservam resultados de negócio diferentes:

- RF28 confirma que a entrega integral de uma Purchase Order `OPEN` ocorreu e fecha a ordem;
- RF29 registra a entrada dos materiais, altera o saldo e reage às Service Executions em `AWAITING_ITEMS`.

Esta separação mantém a decisão já aprovada em RF27 de que fechar uma Purchase Order não altera o estoque. Ela também
permite repetir com segurança o recebimento se houver falha depois do fechamento. RF30 permanece independente porque
introduz política de nível mínimo, alvo e detecção de baixo estoque.

As decisões abaixo foram aprovadas para o MVP. Em especial, esta especificação adota entrega integral, sem divergência,
cancelamento ou entrega parcial.

## Problema e resultado esperado

RF27 termina quando o External Supplier System aceita a compra e a Purchase Order local fica `OPEN`. Sem uma confirmação
posterior, a oficina não consegue distinguir ordens ainda aguardando entrega daquelas cuja entrega já foi conferida pelo
Stock Manager.

O resultado esperado é que o Stock Manager consiga localizar uma ordem aberta, confirmar uma única vez que todas as
suas linhas foram entregues e obter uma Purchase Order `CLOSED`, preservando a composição e a referência externa. O
fechamento não registra movimentação, não aumenta `availableQuantity` e não tenta reservar materiais.

## Linguagem ubíqua

### Purchase Order aberta

Uma Purchase Order `OPEN` foi aceita pelo External Supplier System e aguarda confirmação de entrega pela oficina. Suas
linhas, quantidades, demandas selecionadas e referência externa já são imutáveis.

### Confirmação de entrega

Confirmação de entrega é a decisão do Stock Manager de que a entrega integral correspondente à Purchase Order foi
conferida. No MVP, o sistema não recebe callback do fornecedor e não compara nota fiscal, lote, valor, frete ou preço.

O cliente não reenvia linhas ou quantidades ao confirmar. A composição confirmada é sempre a própria composição
imutável da Purchase Order, evitando alteração indevida ou confirmação parcial por mass assignment.

### Purchase Order fechada

Uma Purchase Order `CLOSED` teve sua entrega integral confirmada. `CLOSED` é terminal no MVP: a ordem não pode ser
editada, reaberta, cancelada nem fechada novamente com outro significado.

O fechamento registra ao menos o instante da confirmação e a identidade autenticada responsável. A identidade é obtida
do contexto de autenticação e nunca aceita livremente no comando.

## Escopo funcional desta entrega

### Consultar ordens pendentes de entrega

O Stock Manager pode consultar Purchase Orders e distinguir, no mínimo, as situações `OPEN` e `CLOSED`. A visão
operacional permite localizar as ordens `OPEN` pendentes de confirmação e apresenta:

- ID local e referência externa;
- estado atual;
- linhas imutáveis com Stock Item e quantidade;
- demandas atendidas, quando existirem;
- instante de abertura;
- instante e responsável pelo fechamento, quando a ordem estiver `CLOSED`.

A consulta individual criada em RF27 passa a encontrar ordens `OPEN` ou `CLOSED`; uma ordem não deixa de ser consultável
por ter sido fechada. Filtros, ordenação, paginação e formato HTTP exatos serão definidos na especificação técnica.

### Confirmar entrega integral

O Stock Manager confirma a entrega de uma Purchase Order `OPEN` pelo ID local. O sistema usa todas as linhas já
confirmadas pelo fornecedor e transiciona a ordem para `CLOSED` em uma única operação de negócio.

O fechamento:

- preserva ID local, referência externa, linhas, snapshots e demandas vinculadas;
- registra `closedAt` e o responsável autenticado;
- não aceita alteração de item ou quantidade;
- não depende de nova chamada ao External Supplier System;
- não altera qualquer saldo de Stock Item;
- não cria um Stock Receipt ou uma Stock Movement;
- não tenta novamente Stock Reservations;
- não modifica Service Order ou Service Execution.

### Repetir a confirmação

Repetir a confirmação da mesma Purchase Order já `CLOSED` retorna o fechamento existente sem criar uma nova transição,
trocar `closedAt` ou substituir o responsável. Chamadas concorrentes convergem para um único fechamento observável.

## Atores e cenários

- O Stock Manager lista Purchase Orders `OPEN` para localizar entregas pendentes.
- O Stock Manager consulta uma ordem e confere sua referência externa e todas as linhas.
- O Stock Manager confirma a entrega integral e a ordem passa de `OPEN` para `CLOSED`.
- O mesmo comando é repetido depois de timeout e retorna o fechamento já registrado.
- Duas confirmações concorrentes para a mesma ordem produzem uma única transição.
- Uma tentativa usa uma ordem inexistente ou que não foi aberta e é rejeitada sem alterar estado.
- Depois do fechamento, RF29 registra o recebimento em uma operação própria e repetível com segurança.

## Regras de negócio

### Elegibilidade e transição

- somente Purchase Order `OPEN` pode ser fechada pela primeira vez;
- `PENDING_SUBMISSION` e `REJECTED` não representam compras entregáveis e não podem ser fechadas;
- `CLOSED` é terminal e permanece consultável;
- não existem fechamento parcial, confirmação por linha ou quantidade entregue diferente no MVP;
- uma divergência física impede a confirmação até ser resolvida fora do sistema;
- o fechamento não muda a situação histórica das Purchase Demands já `ORDERED`.

### Identidade, auditoria e idempotência

- o ID local identifica a Purchase Order a fechar; a referência externa é somente dado de conferência;
- `closedAt` é registrado pelo sistema e não pode anteceder `openedAt`;
- o responsável deriva do usuário autenticado com papel operacional permitido;
- repetir o fechamento não substitui dados de auditoria;
- concorrência não pode produzir dois fechamentos nem estados intermediários observáveis;
- a ordem e sua composição nunca são excluídas fisicamente por este fluxo.

### Autorização e exposição

- somente Stock Manager pode listar, consultar e fechar Purchase Orders;
- enquanto Stock Manager for representado tecnicamente por `MANAGER`, `MANAGER` e `ADMIN` preservam o acesso HTTP;
- o comando não aceita papel, identidade, linhas, estado ou timestamps arbitrários;
- as respostas não expõem Customer, Vehicle, Technician, Service Order, credenciais ou dados internos do fornecedor.

## Falhas esperadas

O fluxo deve distinguir pelo menos:

- Purchase Order inexistente;
- Purchase Order em estado incompatível com o primeiro fechamento;
- confirmação sem autenticação ou sem papel permitido;
- conflito concorrente que não possa ser reconciliado como repetição idempotente;
- falha técnica antes da persistência da transição.

Uma falha não pode deixar a ordem parcialmente fechada nem expor SQL, stack trace, credenciais ou tipos internos.
Códigos HTTP e códigos de erro estáveis serão definidos na especificação técnica.

## Relação com RF29

O fluxo operacional recomendado executa RF29 logo após RF28, mas os dois resultados continuam explícitos:

1. RF28 confirma a entrega e fecha a Purchase Order;
2. RF29 registra uma única entrada referente à ordem fechada;
3. RF29 aumenta os saldos e só então inicia novas tentativas de reserva.

Se o passo 2 falhar, a ordem permanece `CLOSED` e pendente de recebimento. O Stock Manager pode repetir RF29 sem reabrir
ou fechar novamente a ordem. A visão operacional deve permitir distinguir `CLOSED` com recebimento pendente de `CLOSED`
já recebido, mesmo que essa informação seja composta a partir do registro pertencente a RF29.

## Fora de escopo

- registrar recebimento, movimentação ou mudança de saldo de RF29;
- tentar novamente Stock Reservations ou priorizar Service Orders;
- entrega ou recebimento parcial;
- informar falta, excesso, substituição ou avaria de materiais;
- cancelar, rejeitar depois da abertura ou reabrir Purchase Order;
- editar linhas, quantidades, fornecedor ou referência externa;
- nota fiscal, lote, validade, custo, imposto, frete, pagamento ou conciliação financeira;
- callback, consulta ou atualização no External Supplier System;
- política de baixo estoque e demandas `LOW_STOCK` de RF30;
- múltiplos locais de estoque.

## Critérios de aceite

- [ ] O Stock Manager lista ordens e identifica quais Purchase Orders `OPEN` aguardam confirmação de entrega.
- [ ] A consulta por ID continua encontrando a Purchase Order depois que ela chegar a `CLOSED`.
- [ ] Confirmar uma Purchase Order `OPEN` muda seu estado para `CLOSED` uma única vez.
- [ ] O fechamento preserva referência externa, linhas, quantidades, snapshots e demandas vinculadas.
- [ ] O fechamento registra instante e responsável autenticado sem aceitá-los livremente no comando.
- [ ] Repetir a confirmação retorna o fechamento existente sem alterar dados de auditoria.
- [ ] Confirmações concorrentes convergem para uma única transição observável.
- [ ] Purchase Order inexistente, `PENDING_SUBMISSION` ou `REJECTED` não é fechada.
- [ ] O cliente não consegue confirmar somente parte das linhas nem alterar quantidades no fechamento.
- [ ] Fechar a ordem não altera `availableQuantity`, não cria movimentação e não tenta reserva.
- [ ] A visão operacional distingue ordem `CLOSED` pendente de RF29 de ordem cujo recebimento já foi registrado.
- [ ] Somente `MANAGER` e `ADMIN` acessam as operações HTTP enquanto representarem Stock Manager.
- [ ] Falhas não deixam estado parcial nem expõem informações internas ou sensíveis.

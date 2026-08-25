# Especificação Funcional: Criação de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-creation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-24 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-24 |
| Referências | RF27, Miro, specs de Stock Item e Stock Reservation (links abaixo) |

Referências:

- [RF25–RF30 no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679722227775);
- [Domain Storytelling de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831);
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720),
  especialmente o read model de pedidos de compra, o comando de criação e o External Supplier System;
- [Context Map no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684515255),
  especialmente a relação `External Supplier System [U] → Stock & Procurement [D]`;
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027);
- [Aggregates atualizados no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870345674);
- `docs/features/stockprocurement/stock-domain-foundation/`;
- `docs/features/stockprocurement/stock-item-reservation/`;
- `docs/features/stockprocurement/README.md`;
- `docs/rfc/RFC-001-stock-item-foundation.md`;
- `docs/backlog.md`, especialmente BL-002.

## Estado desta descoberta

Esta especificação define o recorte funcional aprovado de RF27 — Criar Purchase Order por nível baixo de estoque ou por
reparo pendente. A aprovação funcional autoriza a criação da especificação técnica, mas ainda não autoriza a
implementação.

O Miro contém duas representações que poderiam levar a comportamentos diferentes:

- uma tabela histórica de policies relaciona `Stock Items indisponíveis` diretamente a `Cria Purchase Order`;
- o fluxo detalhado mais recente estabelece um read model de pedidos de compra e registra que podem existir demandas
  originadas por Service Orders e por baixo estoque, cabendo ao usuário selecionar quais virarão Purchase Orders.

Este Draft adota o segundo comportamento: nenhum gatilho cria ou envia uma Purchase Order automaticamente. Os gatilhos
criam demandas de compra; o Stock Manager decide quais demandas atender, pode adicionar livremente outros Stock Items e
confirma a criação no External Supplier System. Uma Purchase Order também pode ser criada inteiramente ad hoc, sem
depender de um gatilho ou de uma Purchase Demand anterior.

### Recorte recomendado das histórias relacionadas

- **RF27 — Criar Purchase Order:** pertence a esta feature. Inclui registrar e consultar demandas de compra, criar uma
  ordem a partir de demandas ou livremente, consolidar linhas, enviá-la ao sistema externo e registrar o resultado.
- **RF30 — Identificar Stock Items em nível baixo:** permanece uma feature separada porque exige definir nível mínimo,
  alvo de reposição e os momentos de detecção. RF27 consumirá sua demanda de baixo estoque por um contrato estável. O
  cenário completo de origem `LOW_STOCK` é uma dependência para RF27 ser considerado implementado de ponta a ponta.
- **RF28 — Fechar Purchase Order:** permanece uma feature separada. RF27 termina com uma Purchase Order aberta.
- **RF29 — Registrar recebimento/reposição:** permanece uma feature separada. Criar a ordem não altera quantidade
  disponível e não tenta novamente reservas pendentes.

Uma especificação funcional própria de RF30 deve ser produzida e aprovada antes de seu desenho técnico. RF28 e RF29 não
precisam ser especificadas agora para que o contrato funcional de criação seja revisado.

## Problema e resultado esperado

Uma tentativa de reserva pode identificar material insuficiente para uma Service Execution, e uma futura política de
inventário poderá identificar Stock Items abaixo do nível mínimo. Sem um fluxo de Procurement, essas necessidades ficam
somente em notificações ou observações operacionais, podem ser duplicadas por retries e não chegam de forma controlada
ao fornecedor.

O resultado esperado é um fluxo rastreável de criação:

- cada necessidade de compra válida gera ou atualiza uma demanda identificável sem duplicação;
- o Stock Manager consulta demandas abertas originadas por reparos pendentes e por baixo estoque;
- o Stock Manager pode selecionar demandas, escolher diretamente Stock Items ou combinar as duas formas;
- o Stock Manager informa quanto deseja comprar de cada Stock Item;
- demandas e seleções manuais do mesmo Stock Item são consolidadas em uma única linha da Purchase Order;
- Stock & Procurement envia a ordem ao External Supplier System através de uma tradução entre os dois modelos;
- somente uma confirmação do sistema externo torna a Purchase Order aberta no sistema da oficina;
- retries não criam duas ordens locais ou externas para a mesma confirmação;
- a Purchase Order não reserva, recebe ou aumenta saldo e não fica diretamente vinculada a uma Service Order.

## Linguagem ubíqua

### Purchase Demand

`Purchase Demand` é a necessidade operacional ainda não atendida que pode ser selecionada para uma compra. Ela não é
uma Purchase Order, sua criação não envia nada ao fornecedor e sua existência não é precondição para uma compra manual.

Cada demanda possui identidade, Stock Item, origem, quantidade sugerida, instante de criação e situação atual. As
origens canônicas propostas são:

- `PENDING_REPAIR`: insuficiência observada ao tentar reservar todos os Stock Requirements de uma Service Execution;
- `LOW_STOCK`: necessidade publicada pela feature que identifica Stock Items abaixo do nível mínimo.

Uma demanda de reparo mantém `serviceExecutionId` somente como origem e rastreabilidade. Uma demanda de baixo estoque
não inventa referência a Service Order ou Service Execution.

### Purchase Order

`Purchase Order` é o pedido de reposição de Stock Items aceito pelo External Supplier System e registrado em Stock &
Procurement. Possui ID local estável, referência externa do fornecedor, estado `OPEN`, linhas e instante de criação.

Cada linha contém `stockItemId` e quantidade inteira positiva. Nome e SKU podem ser apresentados como dados de leitura,
mas o ID do Stock Item permanece a referência canônica interna.

### External Supplier System

O External Supplier System é upstream e possui modelo próprio para pedidos, produtos, quantidades e respostas. Stock &
Procurement protege sua linguagem e regras por meio de uma Anti-Corruption Layer.

No MVP existe uma única integração configurada. Não será criado cadastro de Supplier, seleção de fornecedor, cotação ou
comparação de preços nesta feature. A referência devolvida pelo sistema externo identifica a ordem naquele sistema; ela
não transforma Supplier em aggregate interno.

## Escopo funcional desta entrega

### Registrar demanda por reparo pendente

Quando a tentativa atômica de reserva falhar por `INSUFFICIENT_QUANTITY`, o sistema registra uma demanda
`PENDING_REPAIR` para cada Stock Item insuficiente.

A demanda registra:

- o `stockItemId` canônico;
- o `serviceExecutionId` que originou a tentativa;
- a quantidade total solicitada pela execução;
- a quantidade disponível observada na tentativa;
- a quantidade sugerida para compra, correspondente à diferença positiva observada;
- o instante da detecção.

Falhas `STOCK_ITEM_NOT_FOUND`, `STOCK_ITEM_INACTIVE` ou quantidade inválida são problemas de referência ou integridade e
não geram demanda de compra automaticamente.

Retries da mesma Service Execution para o mesmo Stock Item não criam demandas abertas duplicadas. Se a nova tentativa
for bem-sucedida antes da compra, a demanda deixa de ser selecionável. Se continuar insuficiente, a informação observada
e a sugestão podem ser atualizadas sem perder a identidade e a origem.

### Receber demanda por baixo estoque

RF27 aceita uma demanda `LOW_STOCK` contendo `stockItemId`, quantidade disponível observada e quantidade de reposição
sugerida pela feature RF30.

Definir ou alterar nível mínimo, calcular o alvo de reposição, varrer o catálogo e decidir em quais movimentações a
detecção ocorre pertencem a RF30. RF27 não adiciona `minimumQuantity` ao Stock Item nem tenta inferir baixo estoque a
partir de saldo zero.

Repetições da mesma condição de baixo estoque não criam várias demandas abertas equivalentes. Uma nova demanda só pode
nascer depois que a anterior deixar de representar uma necessidade aberta e RF30 identificar uma nova ocorrência.

### Consultar pedidos de compra

O Stock Manager consulta as Purchase Demands ainda selecionáveis. A visão permite distinguir, no mínimo:

- ID da demanda e origem;
- ID, SKU, nome e tipo do Stock Item;
- quantidade sugerida e informações de saldo observadas na origem;
- `serviceExecutionId`, somente quando a origem for `PENDING_REPAIR`;
- instante da detecção.

A consulta pode ser filtrada por origem e Stock Item. Ela não expõe Customer, Vehicle, preço da Estimate ou outros
dados pessoais/comerciais de Service Lifecycle.

### Montar e consolidar a Purchase Order

O Stock Manager possui três formas de montar a Purchase Order:

- selecionar uma ou mais Purchase Demands abertas;
- adicionar diretamente Stock Items ativos, sem demanda anterior;
- combinar demandas selecionadas e itens adicionados livremente.

Em todos os casos, o Stock Manager informa a quantidade final inteira e positiva de cada Stock Item que deseja comprar.

As seguintes regras se aplicam:

- uma criação deve resultar em ao menos uma Purchase Line;
- a seleção de Purchase Demands é opcional;
- quando houver demandas selecionadas, todas devem existir e continuar selecionáveis na confirmação;
- demandas de ambas as origens podem participar da mesma Purchase Order;
- várias demandas e adições manuais do mesmo Stock Item geram uma única Purchase Line;
- quando uma linha cobre demandas, sua quantidade não pode ser menor que a soma das sugestões selecionadas para o item;
- uma linha inteiramente ad hoc não possui quantidade mínima derivada de demanda;
- o Stock Manager pode comprar quantidade maior para formar margem operacional;
- uma demanda não pode ser usada em mais de uma Purchase Order;
- uma linha manual não cria, resolve ou consome automaticamente uma Purchase Demand que não tenha sido selecionada;
- Stock Items inexistentes ou inativos não podem compor uma nova ordem;
- uma falha em qualquer demanda selecionada ou linha impede a criação inteira; não há Purchase Order parcial.

A origem permanece rastreável pelas Purchase Demands, mas a Purchase Order não pertence a uma Service Order e não
garante que o material recebido será destinado ao reparo que originou a demanda. Essa independência preserva a decisão
operacional futura de atender primeiro Service Orders de maior prioridade.

### Criar no External Supplier System

Depois da confirmação do Stock Manager, Stock & Procurement traduz e envia uma única Purchase Order ao External
Supplier System.

- somente a aceitação externa com referência identificável conclui a criação;
- a ordem local nasce em `OPEN` e guarda a referência externa confirmada;
- as demandas selecionadas, quando existirem, deixam de aparecer como abertas somente depois dessa confirmação;
- rejeição de produto, quantidade ou pedido não cria uma Purchase Order aberta e mantém as demandas selecionáveis;
- indisponibilidade ou timeout do sistema externo não pode consumir silenciosamente as demandas;
- repetir a mesma confirmação após timeout ou perda de resposta deve recuperar o mesmo resultado, sem duplicar a ordem
  no fornecedor ou no sistema da oficina;
- repetir a confirmação com conteúdo diferente sob a mesma identidade de operação é conflito;
- a resposta informa o ID local, a referência externa, o estado, as linhas consolidadas e o instante da criação.

Mapeamento de produto, autenticação técnica e formato do fornecedor serão definidos na especificação técnica. A
integração não envia dados de Customer, Vehicle, Technician, Estimate ou Service Order.

### Consultar a ordem criada

O Stock Manager pode consultar uma Purchase Order por seu ID local para confirmar referência externa, estado, linhas e
demandas atendidas, quando existirem. A consulta não altera demandas, saldo ou estado da ordem.

Uma listagem operacional de ordens pendentes de recebimento poderá ser detalhada por RF28 ou RF29. RF27 não cria um CRUD
genérico nem permite editar ou apagar uma Purchase Order aberta.

## Atores e cenários

- O sistema falha ao reservar um item ativo por quantidade insuficiente e registra uma única Purchase Demand de reparo.
- Um retry ainda insuficiente atualiza a observação sem duplicar a demanda aberta.
- Um retry reserva os itens antes da compra e torna a demanda de reparo não selecionável.
- RF30 publica uma necessidade de baixo estoque e RF27 a torna selecionável sem conhecer a regra do nível mínimo.
- O Stock Manager consulta demandas das duas origens, escolhe quais comprar e informa as quantidades finais.
- O Stock Manager cria uma Purchase Order somente com Stock Items escolhidos livremente, sem demanda anterior.
- O Stock Manager combina demandas selecionadas e itens ad hoc na mesma Purchase Order.
- O Stock Manager seleciona várias demandas do mesmo item e o sistema envia uma única linha consolidada.
- O External Supplier System aceita a ordem, devolve sua referência e o sistema registra a Purchase Order `OPEN`.
- O sistema externo rejeita uma linha; nenhuma Purchase Order aberta é registrada e nenhuma demanda é consumida.
- O sistema perde a resposta externa e repete a confirmação sem criar um pedido duplicado.
- Duas confirmações concorrentes tentam usar a mesma demanda; apenas uma delas pode concluir.

## Regras de negócio

### Identidade e rastreabilidade

- toda Purchase Demand e Purchase Order possui ID local estável;
- toda Purchase Order confirmada possui referência externa obrigatória;
- a referência externa não pode identificar duas Purchase Orders locais;
- toda Purchase Line referencia um Stock Item por ID e possui quantidade inteira positiva;
- uma Purchase Order contém ao menos uma linha;
- a composição e as quantidades de uma Purchase Order `OPEN` são imutáveis nesta feature;
- a relação entre as demandas eventualmente selecionadas e a ordem criada é preservada para auditoria e idempotência;
- a Purchase Order não possui `serviceOrderId` ou `serviceExecutionId` direto.

### Situação das demandas

- demanda aberta pode ser selecionada;
- demanda já incluída em Purchase Order não pode ser reutilizada;
- demanda de reparo resolvida por reserva anterior à compra não pode ser selecionada;
- resolução de uma demanda não apaga seu histórico;
- uma condição repetida usa a demanda aberta existente em vez de multiplicar pedidos equivalentes;
- falha externa mantém as demandas abertas e disponíveis para retry.

Os nomes exatos dos estados internos das demandas serão definidos na especificação técnica. Os comportamentos
`selecionável`, `incluída em ordem` e `resolvida sem compra` são obrigatórios, independentemente do desenho persistente.

### Atomicidade e concorrência

- todas as demandas e linhas são validadas antes de concluir a criação;
- uma Purchase Order não pode ser confirmada com apenas parte das demandas que tenham sido selecionadas;
- duas criações concorrentes não podem consumir a mesma demanda;
- uma falha local antes da confirmação externa não envia uma ordem incompleta;
- uma falha entre a aceitação externa e o registro local deve ser recuperável pela mesma identidade idempotente;
- o resultado confirmado nunca produz mais de uma ordem externa e uma ordem local para a mesma operação;
- duas criações ad hoc com identidades de operação diferentes são compras distintas, ainda que possuam linhas iguais.

### Efeito sobre Stock e reparos

- criar Purchase Order não altera `availableQuantity`;
- criar Purchase Order não cria Stock Reservation;
- criar Purchase Order não muda Service Execution de `AWAITING_ITEMS` para `READY`;
- receber materiais e aumentar saldo pertencem a RF29;
- tentar novamente reservas depois do recebimento pertence à integração de RF29 com o ciclo já existente;
- material futuro não fica prometido à Service Execution que originou a demanda.

### Autorização e exposição

- somente o papel operacional de Stock Manager pode consultar demandas e criar ou consultar Purchase Orders;
- enquanto o papel de domínio for representado tecnicamente por `MANAGER`, essa equivalência deve ser preservada no
  contrato HTTP;
- nenhuma operação aceita identidade, papel, preço, dados do Customer ou estado de Service Order enviados livremente
  pelo cliente;
- somente identificadores e quantidades necessários são enviados ao External Supplier System.

## Falhas esperadas

O fluxo deve distinguir pelo menos:

- demanda inexistente;
- demanda não selecionável, já utilizada ou resolvida;
- Stock Item inexistente ou inativo;
- quantidade ausente, não inteira, não positiva ou abaixo da necessidade selecionada;
- Purchase Order sem linhas;
- confirmação concorrente da mesma demanda;
- rejeição funcional do External Supplier System;
- integração externa indisponível ou com resposta inconclusiva;
- conflito de idempotência;
- Purchase Order não encontrada na consulta.

As falhas não expõem payload externo, credenciais, stack trace ou detalhes internos. Códigos HTTP e códigos de erro
estáveis serão definidos na especificação técnica.

## Decisões funcionais propostas para aprovação

- [x] A criação é manual: gatilhos apenas registram Purchase Demands e nunca enviam uma ordem automaticamente.
- [x] O Stock Manager pode criar uma Purchase Order ad hoc, selecionar demandas ou combinar as duas formas.
- [x] `PENDING_REPAIR` e `LOW_STOCK` são as duas origens canônicas de demanda.
- [x] RF30 continua separada e fornece as demandas `LOW_STOCK`; RF27 não introduz nível mínimo por conta própria.
- [x] RF28 e RF29 ficam fora desta feature; a ordem criada termina em `OPEN` e não altera saldo.
- [x] Demandas do mesmo Stock Item são consolidadas, e o Stock Manager pode comprar acima, mas não abaixo, da soma
  sugerida selecionada.
- [x] Linhas ad hoc não dependem de quantidade sugerida e não afetam demandas que não tenham sido selecionadas.
- [x] A Purchase Order não se vincula diretamente à Service Order; a origem fica nas Purchase Demands.
- [x] O MVP usa uma integração externa configurada e não inclui cadastro, seleção ou cotação de Supplier.
- [x] Uma Purchase Order só é considerada criada depois da confirmação do External Supplier System.
- [x] A criação é idempotente também diante de timeout e resposta externa perdida.
- [x] Preço de compra, impostos, frete, pagamento e aprovação financeira não fazem parte de RF27.

## Fora de escopo

- definir nível mínimo, alvo de reposição ou mecanismo de detecção de baixo estoque de RF30;
- fechar, cancelar ou reabrir Purchase Order de RF28;
- receber entrega, registrar reposição, alterar saldo ou tentar novamente reservas de RF29;
- reservar antecipadamente unidades futuras para uma Service Order;
- criar vínculo direto entre Purchase Order e Service Order ou decidir prioridade de atendimento após recebimento;
- cadastro, escolha, ranking, cotação ou negociação de Suppliers;
- comparar preços, armazenar custo unitário, calcular total, imposto, frete ou pagamento;
- editar ou excluir uma Purchase Order confirmada;
- atendimento ou recebimento parcial;
- criar produto automaticamente no fornecedor ou cadastrar mapeamentos pela API desta feature;
- notificações novas além dos resultados de negócio necessários aos consumidores reais;
- autenticação técnica, credenciais ou disponibilidade operacional do fornecedor fora da integração desta feature.

## Critérios de aceite

- [ ] Insuficiência de quantidade em uma tentativa de reserva cria ou atualiza uma única demanda `PENDING_REPAIR` por
  Service Execution e Stock Item.
- [ ] Item inexistente, inativo ou entrada inválida não gera demanda de compra automática.
- [ ] Uma reserva concluída antes da compra torna a demanda de reparo correspondente não selecionável.
- [ ] Uma demanda `LOW_STOCK` emitida por RF30 aparece na mesma visão operacional sem acoplar RF27 à regra de mínimo.
- [ ] O Stock Manager consulta demandas abertas e distingue origem, item, quantidade sugerida e origem operacional.
- [ ] O Stock Manager cria uma Purchase Order válida sem Purchase Demand anterior.
- [ ] O Stock Manager seleciona demandas das duas origens, adiciona itens ad hoc e define quantidades por Stock Item.
- [ ] Demandas e adições manuais do mesmo Stock Item produzem uma única Purchase Line consolidada.
- [ ] Ordem sem linhas, demanda inválida ou quantidade abaixo da soma sugerida rejeita toda a criação.
- [ ] O External Supplier System recebe somente produtos e quantidades traduzidos pela integração.
- [ ] A aceitação externa gera uma única Purchase Order local `OPEN`, com referência externa e linhas imutáveis.
- [ ] Rejeição ou indisponibilidade externa não consome demandas nem cria uma Purchase Order aberta parcial.
- [ ] Retry da mesma confirmação não duplica a ordem externa, a ordem local ou o uso das demandas.
- [ ] Confirmações concorrentes não utilizam a mesma demanda em duas Purchase Orders.
- [ ] É possível consultar a ordem criada por ID sem alterar seu estado.
- [ ] Criar a ordem não muda saldo, reserva ou estado de Service Execution.
- [ ] A Purchase Order não contém vínculo direto com Service Order e não expõe dados pessoais ou comerciais dela.
- [ ] Somente o Stock Manager pode executar as operações da feature quando a autorização correspondente estiver ativa.
- [ ] RF28, RF29 e a detecção de RF30 permanecem fora da implementação desta feature.

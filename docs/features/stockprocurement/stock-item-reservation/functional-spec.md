# Especificação Funcional: Ciclo de Reserva Atômica de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-item-reservation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-21 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Referências | Miro, `stock-domain-foundation`, RFC-001, ADR-003 e BL-002/BL-003/BL-004 (links abaixo) |

Referências:

- [Story de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831)
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720)
- [Requisitos e refinamento no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679721508363)
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027)
- [Aggregates atualizados no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870345674)
- `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`
- `docs/RFC-001-stock-item-foundation.md`
- `docs/ADR-003-notifications-boundary.md`
- `docs/backlog.md`, especialmente BL-002, BL-003 e BL-004

## Estado desta descoberta

Esta especificação propõe o primeiro incremento operacional posterior ao catálogo de Stock Items. O escopo resolve o
ciclo central de BL-003 e somente a parte de BL-002 necessária para refletir, com rastreabilidade, as alterações de
saldo causadas por uma reserva. Recebimentos, retiradas administrativas, ajustes e nível mínimo continuam como feature
posterior.

A revisão do código novo de Service Lifecycle e da documentação consolidada do Miro confirmou duas regras que precisam
ser tratadas juntas:

- uma Service Execution pode precisar de vários Stock Requirements desde o diagnóstico;
- uma necessidade descoberta depois da geração da Estimate não altera o escopo comercial existente: abre novo
  Diagnosis, cria nova Service Execution e origina nova Estimate.

Portanto, a coleção de Stock Requirements será preservada, mas seu conteúdo será congelado quando a Estimate for
gerada. A feature aprovada `attach-stock-requirement` permite hoje anexos em estados posteriores e precisará voltar para
revisão humana antes da implementação deste fluxo.

As decisões mais recentes de `stock-domain-foundation` e da RFC-001 permanecem válidas e prevalecem sobre descrições
arquiteturais anteriores:

- Stock é a capability, não uma entidade física ou aggregate com ID;
- cada Stock Item mantém identidade própria;
- Stock Requirement pertence a Service Lifecycle;
- uma reserva efetiva cria uma Stock Reservation vinculada a uma Service Execution;
- não será criado um contador de quantidade reservada sem a origem correspondente.

Esta especificação funcional foi explicitamente aprovada por Matheus Apostulo em 2026-08-20, depois que a revisão
retirou a liberação e a regressão de `READY` para `AWAITING_ITEMS` do escopo. A especificação técnica deverá detalhar
as decisões de implementação e passar por aprovação humana própria antes de qualquer alteração de código.

## Problema e resultado esperado

O catálogo atual informa a disponibilidade pontual de cada Stock Item, mas uma Service Execution autorizada ainda não
consegue comprometer os materiais necessários. Marcar cada requirement como reservado sem registrar a origem do
compromisso permite perda de rastreabilidade, dupla reserva e saldo negativo quando execuções concorrentes disputam os
mesmos itens.

O resultado desta feature é um compromisso de estoque explícito e verificável:

- cada reserva efetiva possui identidade e referencia exatamente uma Service Execution;
- a reserva registra todos os Stock Items e quantidades comprometidos para essa execução;
- todas as linhas são reservadas juntas ou nenhuma delas é reservada;
- a aprovação de uma linha da Estimate dispara automaticamente a tentativa de reserva daquela Service Execution;
- tentativas repetidas não criam outra reserva nem descontam saldo novamente;
- execuções concorrentes nunca comprometem mais unidades do que a quantidade disponível;
- a reserva permanece ativa durante a separação e a espera pela retirada e é encerrada pelo consumo dos materiais;
- indisponibilidade é informada sem criar uma reserva parcial nem desfazer a aprovação comercial;
- os resultados de reserva completa ou indisponibilidade podem acionar notificações em features consumidoras.

## Linguagem ubíqua

### Stock Reservation

Stock Reservation representa o compromisso de destinar Stock Items a uma Service Execution autorizada. Ela possui ID
próprio, usa `serviceExecutionId` como referência de origem e contém uma ou mais linhas de reserva. Esse compromisso
torna as quantidades indisponíveis para outras Service Executions antes da separação ou retirada física.

Existe no máximo uma Stock Reservation por Service Execution. Uma necessidade adicional descoberta depois da geração
da Estimate segue o fluxo de reparo adicional e origina outra Service Execution; não altera a reserva existente.

### Reservation Line

Cada linha identifica um `stockItemId` e uma quantidade inteira positiva. Dentro da mesma reserva existe no máximo uma
linha por Stock Item. Se a solicitação repetir o mesmo item, as quantidades são somadas antes da validação de
disponibilidade e registradas como uma única linha.

A linha não congela nome, SKU, tipo ou preço. Esses dados pertencem ao catálogo atual ou aos snapshots comerciais de
Service Lifecycle; a reserva preserva somente a identidade do item e a quantidade comprometida.

### Conjunto de Stock Requirements

Uma Service Execution mantém uma coleção porque um único serviço pode exigir vários materiais. Uma troca de óleo, por
exemplo, pode precisar de óleo, filtro e anel de vedação sem que cada material represente uma execução diferente.

Stock Requirement continua sendo um value object sem identidade própria. A unidade de autorização e de reserva é a
Service Execution; por isso não será introduzido `stockRequirementId` apenas para aplicar o resultado da reserva.

O conjunto segue estas regras:

- durante a composição do Diagnosis, uma Service Execution `PENDING` pode receber vários Stock Requirements;
- a geração da Estimate congela o conjunto que foi apresentado comercialmente ao Customer;
- depois da geração da Estimate, nenhum requirement pode ser anexado, removido ou alterado naquela execução;
- uma necessidade descoberta posteriormente segue o fluxo de reparo adicional com novo Diagnosis, nova Service
  Execution e nova Estimate;
- requirements repetidos para o mesmo `stockItemId` podem existir em Service Lifecycle, mas são consolidados por
  `stockItemId` antes da tentativa de reserva.

`attachStockRequirement` continua útil para compor o diagnóstico antes da Estimate. Ele não substitui a criação da
Stock Reservation: anexar registra uma necessidade; reservar valida estoque, controla concorrência, altera saldo e cria
um compromisso rastreável.

### Quantidade disponível e quantidade comprometida

Quantidade disponível é o saldo que ainda pode ser destinado a novas operações. Quantidade comprometida é a soma das
linhas pertencentes a reservas `ACTIVE`; ela sempre possui origem identificável e não existe como contador independente
sem correspondência em Stock Reservations.

O comportamento observável do saldo é:

| Operação | Quantidade disponível | Quantidade comprometida | Quantidade física |
|---|---:|---:|---:|
| Criar reserva `ACTIVE` | diminui | aumenta | não muda |
| Consumir reserva | não muda | diminui | diminui |

A quantidade física representa a soma da quantidade disponível com a quantidade comprometida. Ela serve para esclarecer
as regras de negócio, sem determinar como os valores serão persistidos.

### Estados da reserva

Os estados canônicos são:

- `ACTIVE`: quantidades comprometidas, inclusive durante a separação e a espera pela retirada física;
- `CONSUMED`: materiais retirados para uso na Service Execution e não devolvidos à disponibilidade.

`CONSUMED` é terminal. O cancelamento da Service Execution e a eventual liberação de sua reserva serão definidos em
feature posterior; não existe liberação operacional ou automática nesta entrega.

### Status `AWAITING_ITEMS`

`AWAITING_ITEMS` é o termo canônico para uma Service Execution autorizada que ainda não teve todos os seus Stock Items
reservados. Ele abrange `PART`, `CONSUMABLE` e `SUPPLY`.

O código e contratos atuais usam `AWAITING_PART`, termo mais restrito e inconsistente com Stock Item. Após aprovação
desta especificação, a implementação deverá substituir `AWAITING_PART` por `AWAITING_ITEMS` nos estados de Service
Execution e Service Order e atualizar todos os contratos e documentos afetados.

## Escopo funcional desta entrega

### Criar reserva

Quando uma linha da Estimate é aprovada, Service Lifecycle autoriza a Service Execution e solicita automaticamente a
reserva de seu conjunto congelado de Stock Requirements. A solicitação informa:

- `serviceExecutionId` obrigatório;
- uma ou mais linhas com `stockItemId` e quantidade inteira positiva.

Stock & Procurement valida a solicitação inteira e cria uma Stock Reservation `ACTIVE` somente quando todos os itens
podem ser comprometidos. O sucesso reduz a disponibilidade de todas as linhas na mesma operação de negócio.

Uma Service Execution sem Stock Requirements fica `READY` após a aprovação e não cria uma reserva vazia.

Quando uma chamada aprova várias linhas da Estimate, cada Service Execution origina uma tentativa de reserva
independente. A atomicidade abrange todos os Stock Items de uma execução, não todas as execuções decididas no mesmo
lote.

A indisponibilidade não desfaz a aprovação do Customer. A execução continua autorizada em `AWAITING_ITEMS` e pode ter a
reserva completa solicitada novamente pelo Stock Manager após mudança de disponibilidade. O Stock Manager não seleciona
um subconjunto das linhas nem ignora a regra de tudo-ou-nada.

### Consultar reserva

Deve ser possível consultar a reserva por seu ID ou por `serviceExecutionId`. A consulta informa a identidade, a origem,
as linhas, as quantidades e o estado atual sem alterar saldos.

### Consumir reserva

Uma reserva `ACTIVE` pode ser consumida quando os materiais reservados saem fisicamente do estoque para uso na Service
Execution. O consumo encerra o compromisso sem devolver as quantidades à disponibilidade e muda o estado para
`CONSUMED`.

Não existe consumo parcial nesta feature.

### Comunicar o resultado

O resultado de uma tentativa distingue:

- reserva completa, com a Stock Reservation criada ou já existente;
- itens indisponíveis, com as linhas que impediram o atendimento;
- referência inválida, item inativo ou quantidade inválida;
- conflito com uma reserva já existente para a mesma Service Execution.

Quando a reserva é concluída, o resultado informa `reservationId`, `serviceExecutionId` e o conjunto completo de linhas.
Service Lifecycle associa a identidade da reserva à execução e confirma todos os seus Stock Requirements em uma única
operação de negócio, levando-a a `READY`.

O resultado não será aplicado item a item por `stockItemId`. A operação atual `applyStockReservation` é insuficiente
porque não identifica a Stock Reservation, não representa o sucesso do conjunto completo e pode deixar estado parcial.

Quando não há disponibilidade, nenhuma Stock Reservation é criada e a execução permanece `AWAITING_ITEMS`. A reação de
Purchase Order continua fora desta entrega.

### Resultados de negócio e notificações

A reserva disponibiliza dois resultados de negócio para reações consumidoras. Os nomes exatos dos eventos e o mecanismo
de entrega serão definidos na especificação técnica:

- Stock Items reservados: identifica `reservationId`, `serviceExecutionId` e as linhas comprometidas;
- Stock Items indisponíveis: identifica `serviceExecutionId`, os itens solicitados e as disponibilidades observadas.

Quando os itens forem reservados, Stock & Procurement poderá notificar o Stock Manager para priorizar a separação
física, enquanto Service Lifecycle poderá notificar o Technician atribuído para buscar os materiais. A separação é uma
atividade operacional e não introduz outro estado da reserva nesta feature. A demora na separação ou retirada não
libera as quantidades nem faz a Service Execution regredir de `READY` para `AWAITING_ITEMS`.

Se ainda não houver Technician atribuído, a reserva continua válida e a notificação deverá ser feita quando ocorrer a
atribuição a uma execução já `READY`; a ausência de destinatário não desfaz a reserva.

Quando os itens estiverem indisponíveis, Stock & Procurement poderá notificar o Stock Manager para reposição e nova
tentativa. Identificação de nível baixo e sua notificação continuam na futura feature de inventário de BL-002.

Conforme `docs/ADR-003-notifications-boundary.md`, Notification não é um bounded context no MVP. Cada consumidor define
seu próprio outbound port quando a respectiva feature de entrega for implementada:

- Service Lifecycle é dono da porta de notificação ao Technician;
- Stock & Procurement é dono da porta de notificação ao Stock Manager;
- esta feature fornece os resultados de negócio e não cria uma abstração compartilhada ou placeholder sem uso.

## Atores e cenários

- O Technician registra, durante o Diagnosis, vários Stock Requirements para uma mesma Service Execution quando o
  serviço depende de vários materiais.
- O Customer aprova uma linha da Estimate e o sistema solicita automaticamente a reserva de todos os Stock Requirements
  congelados daquela Service Execution.
- O sistema cria uma única Stock Reservation e compromete todas as quantidades solicitadas.
- Service Lifecycle repete a mesma solicitação após timeout ou retry e recebe a reserva existente, sem novo desconto.
- Duas Service Executions disputam o mesmo saldo; somente as solicitações que possam ser integralmente atendidas são
  concluídas.
- Uma solicitação contém vários itens e pelo menos um deles não possui saldo suficiente; nenhuma linha é reservada.
- Uma aprovação em lote reserva cada Service Execution de forma independente; indisponibilidade em uma execução não
  desfaz decisões comerciais nem cria reserva parcial em outra.
- Um Stock Manager consulta a reserva e sua origem para verificar quais quantidades estão comprometidas.
- Um Stock Manager solicita novamente a reserva completa de uma execução em `AWAITING_ITEMS` depois que o saldo muda.
- Um Stock Manager prioriza a separação física dos itens reservados; eventual demora não libera o compromisso nem altera
  o estado `READY` da Service Execution.
- Um Stock Manager registra o consumo integral quando os materiais saem do estoque para a Service Execution.
- Uma necessidade descoberta depois da Estimate gera novo Diagnosis, nova Service Execution e nova Estimate, sem
  alterar os requirements ou a reserva anteriores.

Enquanto autenticação e autorização não existirem no projeto, os papéis são requisitos funcionais documentados, mas não
serão usados para simular identidade ou permissão dentro do domínio.

## Regras de negócio

### Identidade e origem

- toda Stock Reservation possui ID estável;
- `serviceExecutionId` é obrigatório e imutável;
- a autorização da Service Execution é precondição governada por Service Lifecycle; Stock & Procurement não copia nem
  altera o estado da execução ao criar a reserva;
- o conjunto solicitado deriva dos Stock Requirements congelados da Service Execution, e não de linhas escolhidas
  livremente pelo Stock Manager;
- Stock Requirement permanece um value object sem `stockRequirementId`;
- existe no máximo uma Stock Reservation para a mesma Service Execution, independentemente do estado atual;
- uma reserva contém pelo menos uma linha;
- cada linha referencia um Stock Item existente e contém quantidade inteira maior que zero;
- linhas repetidas para o mesmo Stock Item são consolidadas antes de qualquer validação ou alteração de saldo;
- a reserva não pode ser alterada, receber novas linhas ou trocar quantidades depois de criada.

### Integridade dos Stock Requirements

- uma Service Execution pode conter vários Stock Requirements antes de sua Estimate ser gerada;
- gerar a Estimate congela o conjunto de requirements daquela execução;
- anexar, remover ou alterar requirements depois desse momento é rejeitado, inclusive nos estados `PENDING`,
  `AUTHORIZED`, `AWAITING_ITEMS`, `READY` e `IN_PROGRESS`;
- uma necessidade posterior nunca reabre ou altera a Stock Reservation existente;
- Stock & Procurement usa somente `stockItemId` e quantidade como entrada operacional da reserva;
- `type`, nome e preço presentes nos snapshots de Service Lifecycle não substituem a validação do Stock Item canônico;
- a correção do contrato atual que aceita snapshots comerciais enviados pelo cliente continua registrada em BL-004.

### Elegibilidade dos Stock Items

- `PART`, `CONSUMABLE` e `SUPPLY` podem ser reservados pelas mesmas regras;
- somente Stock Items ativos participam de uma nova reserva;
- a quantidade disponível deve ser maior ou igual à quantidade total solicitada para o item;
- reservar exatamente todo o saldo disponível é permitido e deixa a disponibilidade em zero;
- desativar um Stock Item não cancela uma reserva já `ACTIVE` e não impede seu consumo;
- mudanças posteriores de nome ou preço não alteram a reserva.

### Atomicidade

- a solicitação inteira é tratada como uma única operação de negócio;
- a fronteira de tudo-ou-nada é uma Service Execution e todos os seus Stock Requirements consolidados;
- Service Executions diferentes, ainda que aprovadas na mesma chamada, possuem reservas independentes;
- todos os itens são validados antes da confirmação do compromisso;
- se qualquer linha for inválida, inexistente, inativa ou insuficiente, nenhuma reserva é criada e nenhum saldo muda;
- se ocorrer uma falha durante a confirmação, todas as alterações da tentativa são desfeitas;
- uma reserva somente pode ficar `ACTIVE` quando todas as suas linhas estiverem comprometidas;
- não existem reserva parcial, fila parcial, substituição automática de item ou atendimento parcial de quantidade.

### Concorrência

- solicitações concorrentes observam a disponibilidade efetivamente confirmada, não apenas o saldo visto em consulta
  anterior;
- a soma dos compromissos confirmados nunca pode superar a quantidade disponível para novas reservas;
- o saldo disponível nunca pode ficar negativo;
- quando duas solicitações disputam as últimas unidades e ambas não podem ser atendidas, ao menos uma falha
  integralmente;
- a ordem entre solicitações concorrentes não é garantida por esta feature.

### Idempotência

- repetir uma criação com o mesmo `serviceExecutionId` e as mesmas linhas consolidadas retorna a reserva já existente e
  não altera novamente os saldos;
- repetir a criação com o mesmo `serviceExecutionId` e linhas diferentes é conflito e não altera a reserva existente;
- repetir a criação depois de a reserva chegar a `CONSUMED` nunca cria uma segunda reserva nem reabre a anterior;
- repetir o consumo de uma reserva `CONSUMED` é sucesso sem novo efeito de saldo;
- uma reserva `CONSUMED` não pode voltar a `ACTIVE`.

Uma tentativa que falha por indisponibilidade não cria Stock Reservation. Portanto, uma tentativa posterior para a
mesma Service Execution pode ser executada novamente com o mesmo conjunto congelado quando a disponibilidade mudar.

### Consumo

- somente uma reserva `ACTIVE` pode transicionar pela primeira vez para `CONSUMED`;
- o consumo sempre abrange todas as linhas;
- o consumo não devolve unidades à disponibilidade, pois elas já deixaram de estar disponíveis na criação da reserva;
- a reserva `CONSUMED` e suas linhas permanecem consultáveis para preservar a rastreabilidade;
- uma reserva nunca é excluída fisicamente como parte deste ciclo.

### Falhas e indisponibilidade

- uma falha informa o motivo sem expor detalhes técnicos de persistência ou concorrência;
- quando houver saldo insuficiente, o resultado identifica cada Stock Item indisponível, sua quantidade solicitada e a
  quantidade disponível observada na tentativa;
- ausência de Stock Item e item inativo são diferentes de saldo insuficiente;
- uma tentativa malsucedida não cria uma Stock Reservation em estado de falha;
- a falha da reserva não reverte a aprovação da Estimate ou a autorização da Service Execution;
- a execução afetada permanece `AWAITING_ITEMS` até uma tentativa completa ter sucesso;
- Stock & Procurement não cria Purchase Order nem promete prazo de reposição nesta feature.

### Integração com Service Lifecycle

- a aprovação de uma linha dispara a tentativa de reserva somente para a Service Execution correspondente;
- uma Service Execution sem requirements fica `READY` sem criar Stock Reservation;
- o sucesso é confirmado em Service Lifecycle por `reservationId` e pelo conjunto completo, não por
  `stockRequirementId` ou chamadas independentes por `stockItemId`;
- indicadores `reserved` dos Stock Requirements, caso permaneçam no modelo, mudam juntos e não são fonte de verdade
  independente da Stock Reservation;
- aplicar o mesmo sucesso novamente é idempotente;
- Service Lifecycle não marca requirements como reservados antes da confirmação de Stock & Procurement;
- indisponibilidade mantém a execução autorizada em `AWAITING_ITEMS`;
- depois do sucesso, a Service Execution mantém `reservationId` como referência estável mesmo após o consumo;
- a separação e a retirada podem demorar sem liberar a reserva ou regredir a execução de `READY` para
  `AWAITING_ITEMS`;
- falha na entrega de uma notificação não desfaz aprovação, reserva ou alteração de saldo.

## Relação com os itens de backlog

### BL-002 — Operações de inventário de Stock Item

Esta feature passa a alterar disponibilidade exclusivamente pelos efeitos de criar e consumir uma reserva. Ela
não encerra BL-002: recebimentos, retiradas administrativas, ajustes, justificativas, nível mínimo, baixo estoque e
histórico geral de movimentações permanecem pendentes. O resultado de itens indisponíveis prepara a futura notificação
ao Stock Manager, mas baixo estoque somente poderá ser identificado quando nível mínimo possuir uma regra aprovada.

### BL-003 — Ciclo de Stock Reservation

Esta feature cobre identidade e origem da reserva, linhas, atomicidade, concorrência, idempotência e consumo.
Também cobre a tentativa automática após aprovação e a nova tentativa explícita do Stock Manager. A reavaliação
automática de todas as demandas após reposição permanece dependente de Procurement.

### BL-004 — Integração de Stock Requirement e Estimate

Esta feature cobre o recorte necessário de BL-004 para congelar requirements na geração da Estimate, disparar a reserva
após aprovação e aplicar seu resultado na Service Execution. A integração deverá usar somente IDs e contratos públicos
entre módulos, sem importar pacotes internos.

A substituição dos snapshots enviados pelo cliente por consulta aos dados canônicos de Stock Item continua pendente em
BL-004. Independentemente desse contrato comercial, Stock & Procurement nunca confia em nome, tipo ou preço recebidos de
Service Lifecycle para decidir uma reserva.

## Impacto em especificações aprovadas

Com a aprovação destas decisões, documentos já aprovados de Service Lifecycle passam a ter mudança material e não podem
ser tratados como vigentes sem nova revisão humana:

- `attach-stock-requirement`: restringir anexos à composição anterior à geração da Estimate e remover anexos em
  `AUTHORIZED`, `AWAITING_ITEMS`, `READY` e `IN_PROGRESS`;
- `decide-estimate-lines`: adicionar a tentativa de reserva após cada aprovação e preservar a decisão quando houver
  indisponibilidade;
- `assign-technician`: prever a notificação ao Technician atribuído a uma execução que já esteja `READY` com reserva;
- `start-execution`, `track-execution` e demais contratos de status: substituir `AWAITING_PART` por `AWAITING_ITEMS`;
- OpenAPI, Postman e documentação arquitetural: refletir o novo status e os efeitos de integração quando implementados.

Esses documentos deverão voltar para `Draft` e seus documentos técnicos e planos deverão ser tratados como stale até
nova revisão e aprovação humana.

## Fora de escopo

- recebimento, retirada administrativa, ajuste de inventário ou justificativa manual;
- nível mínimo, identificação de baixo estoque ou notificação motivada por baixo estoque;
- Purchase Order, fornecedor, prazo de reposição ou nova tentativa automática após recebimento;
- reserva ou consumo parcial e backorder parcial;
- cancelamento da Service Execution, liberação da reserva, devolução de saldo ou estado `RELEASED`;
- liberação automática por timeout, atraso na separação ou demora do Technician para buscar os materiais;
- alteração, substituição de linhas ou reabertura de reserva terminal;
- reservar itens inativos ou substituí-los automaticamente;
- múltiplos depósitos ou localizações físicas;
- unidade de medida, quantidades fracionárias, lote, validade ou número de série;
- snapshot de nome, SKU, tipo ou preço dentro da reserva;
- definição de endpoints, DTOs, códigos HTTP, eventos, locks ou estratégia de persistência;
- canal, template, retry ou histórico de entrega de notificações;
- criação de bounded context ou abstração compartilhada de Notification;
- correção completa dos snapshots comerciais enviados pelo cliente em Diagnosis/Stock Requirement;
- autenticação e autorização por papel.

## Decisões funcionais aprovadas

- [x] Stock Reservation possui identidade própria e `serviceExecutionId` único como origem.
- [x] Uma Service Execution mantém vários Stock Requirements, mas o conjunto é congelado quando a Estimate é gerada.
- [x] Necessidade descoberta depois da Estimate cria novo Diagnosis, nova Service Execution e nova Estimate.
- [x] Stock Requirement permanece value object sem ID; o sucesso é relacionado por `reservationId` e
  `serviceExecutionId`.
- [x] A reserva consolida linhas repetidas e compromete todas as linhas ou nenhuma.
- [x] A quantidade comprometida possui origem nas reservas `ACTIVE`, sem contador reservado anônimo.
- [x] Criar uma reserva reduz a quantidade disponível e consumir não a repõe.
- [x] Os estados permitidos nesta feature são `ACTIVE` e `CONSUMED`, sendo o segundo terminal.
- [x] Criação e consumo são idempotentes conforme a origem e o estado atual.
- [x] A aprovação dispara automaticamente uma tentativa independente e atômica por Service Execution.
- [x] Indisponibilidade não desfaz aprovação e mantém a execução em `AWAITING_ITEMS`.
- [x] Uma reserva bem-sucedida leva a execução a `READY`, sem regressão por demora na separação ou retirada.
- [x] `AWAITING_ITEMS` substitui `AWAITING_PART` como termo canônico em Service Lifecycle.
- [x] Resultados de reserva podem alimentar notificações por portas pertencentes aos módulos consumidores, sem um
  bounded context genérico de Notification.
- [x] A feature não inclui liberação, cancelamento, movimentações administrativas, nível mínimo ou Procurement.

## Critérios de aceite

- [x] Uma solicitação válida para uma Service Execution e vários Stock Items cria uma única Stock Reservation `ACTIVE`.
- [x] A reserva criada contém uma linha consolidada para cada Stock Item e preserva as quantidades solicitadas.
- [x] Aprovar uma linha com Stock Requirements dispara a tentativa de reserva da Service Execution correspondente.
- [x] Aprovar uma execução sem Stock Requirements a deixa `READY` sem criar reserva vazia.
- [x] Aprovar várias execuções trata cada reserva separadamente, com atomicidade dentro de cada execução.
- [x] Todos os saldos disponíveis são reduzidos juntos quando a reserva é criada.
- [x] Se qualquer item estiver ausente, inativo ou insuficiente, nenhuma reserva é criada e nenhum saldo é alterado.
- [x] A falha por indisponibilidade preserva a aprovação e deixa a Service Execution em `AWAITING_ITEMS`.
- [x] Quantidade zero ou negativa e solicitação sem linhas são rejeitadas sem alteração de saldo.
- [x] Reservar exatamente a quantidade disponível é permitido e deixa o item com disponibilidade zero.
- [x] Repetir a mesma solicitação retorna a mesma reserva sem novo desconto de saldo.
- [x] Repetir o `serviceExecutionId` com linhas diferentes é rejeitado sem alterar a reserva ou os saldos existentes.
- [x] Depois de uma falha sem reserva, o Stock Manager pode repetir o conjunto completo quando a disponibilidade mudar.
- [x] Solicitações concorrentes nunca produzem saldo negativo ou compromissos acima da disponibilidade.
- [x] Quando duas solicitações concorrentes não podem ser atendidas juntas, cada solicitação termina integralmente em
  sucesso ou falha.
- [x] Uma reserva pode ser consultada por ID e por `serviceExecutionId` sem modificar saldo.
- [x] O sucesso associa `reservationId` à Service Execution e confirma todo o conjunto em uma única operação
  idempotente.
- [x] Stock Requirement não recebe ID próprio apenas para aplicar o resultado da reserva.
- [x] Depois da geração da Estimate, anexar, remover ou alterar Stock Requirements daquela execução é rejeitado.
- [x] Uma necessidade posterior segue novo Diagnosis, nova Service Execution e nova Estimate.
- [x] Consumir uma reserva `ACTIVE` não devolve quantidades e resulta em `CONSUMED`.
- [x] Repetir o mesmo consumo não aplica o efeito de saldo novamente.
- [x] Uma reserva `CONSUMED` não pode voltar a `ACTIVE` nem originar uma segunda reserva para a mesma execução.
- [x] Reservas `CONSUMED` continuam consultáveis com sua origem e suas linhas.
- [x] A demora na separação ou retirada não libera saldo nem faz a execução regredir de `READY`.
- [x] Os resultados de itens reservados e indisponíveis possuem dados suficientes para as notificações consumidoras.
- [x] Falha de notificação não desfaz decisão comercial, reserva ou saldo.
- [x] Os contratos deixam de expor `AWAITING_PART` e usam `AWAITING_ITEMS` como status canônico.
- [x] Nenhuma reserva parcial, Purchase Order, movimentação administrativa ou entidade `Stock` é criada por esta
  feature.

Evidências de implementação registradas em 2026-08-21: `ReserveStockItemsUseCaseTest`,
`StockReservationConcurrencyIntegrationTest`, `DecideEstimateLinesUseCaseTest`,
`RetryStockReservationUseCaseTest`, `StockReservationControllerTest`,
`StockReservationNotificationAfterCommitTest` e `StockReservationApiApplicationModuleTest`. A conclusão formal da
feature continua condicionada ao Checkpoint 1 do plano, que depende da inspeção dos dois widgets restantes no Miro.

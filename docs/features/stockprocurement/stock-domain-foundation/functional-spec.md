# Especificação Funcional: Fundação do Catálogo de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-domain-foundation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-15 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-15 |
| Referências | Itens do Miro e feature anterior de alinhamento de contexto (links abaixo) |

Referências:

- [Story de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831)
- [Bounded Contexts no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679675285092)
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027)
- [Aggregates atualizados no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870345674)
- `docs/RFC-001-stock-item-foundation.md`
- `docs/features/platform/context-alignment-and-project-standards/`

## Estado desta descoberta

A versão anterior reunia catálogo, movimentações, baixo estoque e todo o ciclo de reservas. Em nova revisão, o time
decidiu uma entrega menor: estabelecer o catálogo de Stock Items e permitir sua seleção durante o diagnóstico. Essa
alteração material tornou a especificação técnica anterior desatualizada. A presente versão funcional reduzida foi
explicitamente aprovada em 2026-08-15.

As decisões de linguagem permanecem válidas:

- Stock Item é o conceito unificado para os materiais controlados pela oficina;
- os tipos canônicos são `PART`, `CONSUMABLE` e `SUPPLY`;
- Stock nomeia a capability e não uma entidade física com ID;
- Stock Requirement pertence a Service Lifecycle;
- uma reserva efetiva criará uma Stock Reservation, mas esse ciclo não será implementado nesta feature;
- Purchase Order terá discovery e especificação próprias.

A especificação técnica deverá ser reescrita no escopo reduzido e passar por aprovação humana explícita própria.

## Problema e resultado esperado

O scaffolding atual representa somente `Part`. O processo de diagnóstico, porém, precisa selecionar peças, consumíveis e
insumos a partir de um catálogo confiável de materiais. Sem esse catálogo unificado, o Technician não consegue localizar
um item por nome ou SKU, conferir seu tipo, preço e disponibilidade atual e referenciá-lo por um ID estável.

O resultado desta feature é uma fundação pequena e utilizável:

- Stock Managers mantêm o cadastro de Stock Items;
- Technicians consultam e pesquisam itens ativos durante o diagnóstico;
- Stock & Procurement fornece os dados canônicos do item;
- Service Lifecycle referencia o item escolhido por `stockItemId` e quantidade;
- o código deixa de representar o domínio apenas como `Part`;
- nenhuma entidade artificial `Stock` é criada.

Operações de recebimento, retirada, reserva, liberação, consumo e reposição serão implementadas em features posteriores.

## Linguagem ubíqua

### Stock Item

Stock Item é qualquer material controlado pelo estoque da oficina. Ele é um conceito real do domínio, com identidade e
cadastro próprios, e não apenas uma interface comum entre classes.

Peças, consumíveis e insumos compartilham o mesmo ciclo de cadastro no MVP. Uma separação futura somente será
considerada se surgirem invariantes próprias, como compatibilidade veicular, lote, validade ou regras de faturamento
distintas.

### Stock Item Type

Todo Stock Item possui exatamente um tipo obrigatório e imutável:

- `PART`: peça instalada ou substituída no veículo;
- `CONSUMABLE`: material consumido durante a execução do serviço;
- `SUPPLY`: insumo utilizado para apoiar o trabalho da oficina.

Stock & Procurement é a fonte de verdade dessa classificação. O tipo não será inferido por nome ou SKU.

### Stock

Stock é a capability de cadastrar materiais e, futuramente, controlar disponibilidade, reserva, consumo e reposição. No
MVP não representa depósito, filial, almoxarifado ou localização física.

Não há requisito para identificar “o Stock” com um ID. Consultas são feitas sobre Stock Items. Se surgirem múltiplos
locais, um conceito explícito como `StockLocation` ou `Warehouse` deverá passar por discovery própria.

### Stock Requirement e seleção do Technician

Stock Requirement expressa o material necessário para uma Service Execution e pertence a Service Lifecycle. Ao criar os
requirements durante o diagnóstico, o Technician precisa consultar o catálogo de Stock Items ativos.

A consulta deve permitir:

- buscar por parte do nome ou pelo SKU;
- selecionar um ou mais tipos;
- filtrar pela existência de quantidade disponível;
- visualizar nome, SKU, tipo, preço atual e quantidade disponível;
- selecionar o item pelo ID estável e informar a quantidade necessária.

Sem filtros de busca, tipo ou disponibilidade, a consulta retorna a visão ampla de todos os Stock Items ativos. Cada
filtro aplicado restringe o resultado anterior. Filtros de naturezas diferentes são combinados com `AND`; múltiplos
tipos selecionados são combinados com `OR`.

A disponibilidade exibida é informativa. Ela pode mudar depois da consulta e não bloqueia saldo. O Stock Requirement
mantém a referência ao Stock Item mesmo depois que uma futura Estimate copiar os dados comerciais necessários.

Nesta feature, será entregue a consulta necessária do lado de Stock & Procurement. A adequação do contrato de
diagnóstico e a criação da Estimate serão tratadas pela feature de integração com Service Lifecycle.

### Stock Reservation

Stock Reservation representa o compromisso de separar quantidades para uma Service Execution autorizada. Ela somente
nasce depois da aprovação correspondente e referencia Stock Items por ID e quantidade.

Essa decisão de domínio fica registrada, mas Stock Reservation, reserva atômica, liberação, consumo e concorrência
estão fora do escopo de implementação atual. A feature futura deverá usar esta decisão como entrada de discovery, sem
tratar um contador `reserved` sem origem como solução suficiente.

### Snapshot comercial

Stock & Procurement mantém os dados atuais do Stock Item. A futura Estimate será responsável por congelar nome, tipo e
preço no momento de sua criação. Alterações posteriores do cadastro não poderão modificar uma Estimate pendente ou
aprovada.

Revisão e versionamento de Estimate permanecem como ideia futura em `docs/backlog.md`.

## Escopo funcional desta entrega

### Cadastro

- cadastrar Stock Item;
- consultar um Stock Item por ID, inclusive quando inativo;
- listar e pesquisar Stock Items;
- atualizar nome e preço;
- desativar logicamente um Stock Item;
- substituir o scaffolding e a linguagem HTTP de `Part`.

### Consulta para diagnóstico

- iniciar com a listagem ampla de todos os itens ativos;
- buscar por nome ou SKU com um único termo textual;
- filtrar por um ou mais valores entre `PART`, `CONSUMABLE` e `SUPPLY`;
- filtrar itens com ou sem quantidade disponível;
- combinar busca textual, tipos, disponibilidade e estado ativo;
- retornar a disponibilidade atual sem reservar unidades.

O mesmo contrato de leitura poderá atender às telas administrativas e à seleção do Technician. Autorização por papel não
será implementada enquanto o projeto não possuir autenticação, mas essa necessidade deverá permanecer documentada.

A necessidade será atendida por uma operação de coleção com parâmetros opcionais, além da consulta individual por ID.
Não serão criados endpoints diferentes para busca por nome, SKU, tipo ou disponibilidade.

## Dados necessários

### Identidade e classificação

- ID estável para referências entre contextos;
- SKU obrigatório, imutável e único, inclusive entre itens inativos;
- nome obrigatório e mutável;
- tipo obrigatório e imutável;
- estado ativo para remoção lógica.

### Valor comercial

- preço atual decimal e não negativo;
- moeda explícita `BRL` no MVP.

Preço zero é válido. Ausência de preço não é válida.

### Disponibilidade inicial

- quantidade disponível inicial não negativa;

Esta feature cadastra e consulta a quantidade disponível, mas não oferece operações para alterá-la depois da criação.
Recebimentos, retiradas e ajustes terão comandos e rastreabilidade próprios em outra feature; não serão simulados como
update de cadastro. `reservedQuantity` e `minimumQuantity` não farão parte do modelo persistido ou dos contratos atuais,
pois não sustentariam nenhum comportamento desta entrega.

Quantidades são inteiras no MVP. Unidade de medida e quantidades fracionárias dependem de discovery futura.

## Atores e cenários

- Um Stock Manager cadastra peça, consumível ou insumo com SKU, nome, tipo, preço e quantidade disponível inicial.
- Um Stock Manager consulta um item ativo ou inativo por ID.
- Um Stock Manager parte da listagem ampla e combina filtros de texto, tipos, disponibilidade e estado ativo.
- Um Stock Manager atualiza nome e preço sem alterar SKU, tipo ou saldo.
- Um Stock Manager desativa um item sem apagar seu histórico.
- Um Technician pesquisa itens ativos durante o diagnóstico e seleciona o ID e a quantidade do requirement.
- A consulta informa disponibilidade, mas não promete que ela continuará válida até a aprovação.

## Regras de negócio

### Criação e identidade

- SKU, nome, tipo e preço são obrigatórios.
- SKU é normalizado para comparação e não pode se repetir entre itens ativos ou inativos.
- SKU e tipo não mudam depois da criação.
- Um novo item começa ativo.
- A quantidade disponível inicial não pode ser negativa.
- Preço usa precisão decimal, moeda `BRL` e não pode ser negativo.

### Atualização e desativação

- nome e preço podem ser atualizados;
- ID, SKU, tipo, quantidade disponível e estado ativo não podem ser enviados como atualização cadastral;
- item inativo permanece consultável por ID;
- item inativo não aceita atualização;
- desativação é irreversível nesta feature;
- exclusão física não é permitida.

### Consulta

- busca textual ignora diferenças entre maiúsculas e minúsculas;
- o termo é comparado com nome e SKU;
- ausência de busca, tipo e disponibilidade não restringe a listagem de itens ativos do Technician;
- múltiplos tipos selecionados aceitam item correspondente a qualquer um deles;
- texto, conjunto de tipos, disponibilidade e estado são combinados cumulativamente;
- disponibilidade aceita itens com saldo positivo ou itens sem saldo disponível;
- o resultado informa disponibilidade pontual e não altera saldo;
- a seleção para Stock Requirement usa o ID retornado, nunca nome ou SKU como referência entre contextos.

### Baixo estoque

Nível mínimo, identificação, filtro, notificação e reposição por baixo estoque serão definidos e implementados junto das
operações de inventário. Nenhum atributo ou comportamento preparatório de baixo estoque será criado neste CRUD.

## Contratos existentes

Os endpoints `/api/parts` e seus dados são scaffolding e podem ser removidos sem alias ou período de depreciação.

O contrato substituto usará `/api/stock-items`, exigirá tipo e oferecerá uma operação de coleção com filtros opcionais
de texto, múltiplos tipos, disponibilidade e estado. Paths, parâmetros, DTOs, validações, respostas e códigos HTTP
exatos serão definidos na nova especificação técnica derivada deste documento aprovado.

A API de Service Order atual ainda permite enviar snapshots de Stock Requirement. Corrigir esse contrato exige a
integração consumidora e ficará fora desta entrega. Até essa feature ser implementada, o scaffolding de diagnóstico não
deve ser tratado como contrato final ou fonte confiável de dados canônicos.

## Features posteriores

As decisões já descobertas serão preservadas, mas implementadas separadamente:

- operações de inventário: recebimento, retirada administrativa, ajustes, nível mínimo e baixo estoque;
- ciclo de Stock Reservation: reserva atômica, idempotência, liberação, consumo e concorrência;
- integração com Service Lifecycle: Stock Requirement por ID/quantidade, snapshot na Estimate e gatilho pós-aprovação;
- Procurement: Purchase Order, fornecedor, recebimento de pedido e integração externa;
- versionamento de Estimate para alterações comerciais.

Esses itens estão registrados em `docs/backlog.md`. Cada entrega não trivial deverá possuir suas próprias specs e
aprovações antes da implementação.

## Fora de escopo

- criar entidade, aggregate ou ID de `Stock`;
- implementar Stock Reservation, contador reservado ou nível mínimo;
- receber, retirar, ajustar, reservar, liberar ou consumir quantidades;
- detectar ou notificar baixo estoque;
- alterar o fluxo de diagnóstico, Service Order ou Estimate;
- implementar Purchase Order, fornecedor ou integração externa;
- autenticação e autorização por papel;
- múltiplos depósitos ou localizações físicas;
- unidade de medida, quantidade fracionária, lote, validade, fabricante e compatibilidade veicular;
- reativação de item;
- paginação, salvo se o volume esperado for revisto antes da aprovação técnica.

## Decisões funcionais aprovadas

- [x] Esta feature fica limitada ao CRUD lógico e à consulta pesquisável de Stock Items.
- [x] O Technician parte de todos os itens ativos e restringe a lista com filtros cumulativos antes de formar
  Stock Requirements.
- [x] A resposta informa preço e disponibilidade atuais sem reservar ou garantir saldo futuro.
- [x] Somente a quantidade disponível inicial é cadastrada; movimentações, reserva e nível mínimo ficam para features
  posteriores.
- [x] Stock Reservation permanece como decisão de domínio, sem implementação nesta entrega.
- [x] A integração que remove snapshots enviados pelo cliente de SO fica para feature própria.
- [x] Não existe entidade ou ID de Stock.

## Critérios de aceite

- [x] Peças, consumíveis e insumos podem ser cadastrados como Stock Items de tipo explícito.
- [x] Cada item possui ID, SKU único, nome, tipo, preço `BRL`, quantidade disponível e estado ativo.
- [x] É possível consultar por ID um item ativo ou inativo.
- [x] Sem filtros adicionais, a visão do Technician lista todos os Stock Items ativos.
- [x] É possível combinar texto, múltiplos tipos, disponibilidade e estado ativo.
- [x] A busca encontra correspondências de nome ou SKU sem diferenciar maiúsculas e minúsculas.
- [x] A listagem oferece dados suficientes para o Technician escolher item e quantidade durante o diagnóstico.
- [x] Atualização altera somente nome e preço.
- [x] Desativação é lógica e não permite atualização posterior.
- [x] Nenhuma consulta modifica ou reserva quantidade.
- [x] Nenhum endpoint, modelo ou tabela representa um Stock físico com ID.
- [x] `/api/parts` é removido e o contrato `/api/stock-items` é documentado em OpenAPI e Postman.
- [x] Nenhuma operação de inventário, reserva ou Procurement é implementada nesta feature.

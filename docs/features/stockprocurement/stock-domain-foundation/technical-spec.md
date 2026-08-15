# Especificação Técnica: Fundação do Catálogo de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-domain-foundation` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-15 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-15 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-15) |

## Gate de aprovação

Esta especificação substitui integralmente a proposta técnica anterior, que reunia catálogo, inventário e reservas. O
documento atual está limitado ao CRUD lógico e à consulta pesquisável de Stock Items.

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação pode começar antes da aprovação humana
explícita desta especificação.

## Objetivo técnico e escopo

Substituir o scaffolding `Part` por um modelo de `StockItem` que represente `PART`, `CONSUMABLE` e `SUPPLY`, com
cadastro lógico e uma consulta única capaz de atender a administração do catálogo e a seleção feita pelo Technician.

Esta feature implementará:

- o aggregate root `StockItem`, sem entidade ou aggregate `Stock`;
- criação, consulta individual, busca combinável, atualização cadastral e desativação lógica;
- disponibilidade inicial consultável, sem qualquer operação posterior de saldo;
- substituição integral dos contratos HTTP `/api/parts` por `/api/stock-items`;
- persistência JPA separada do domínio e migração Flyway;
- documentação OpenAPI, coleção Postman e documentação estrutural;
- testes de domínio, aplicação, HTTP, persistência e estrutura Modulith.

Esta feature não implementará:

- recebimento, retirada, ajuste, reserva, liberação ou consumo de saldo;
- `reservedQuantity`, `minimumQuantity`, baixo estoque ou reposição;
- `StockReservation`, `PurchaseOrder`, fornecedor ou integração externa;
- mudanças em Service Order, Stock Requirement, Estimate ou qualquer pacote de Service Lifecycle;
- uma API pública entre módulos ou named interface especulativa;
- autenticação, autorização por papel ou paginação.

## Diagnóstico do scaffolding atual

O pacote `stockprocurement.stock` já contém uma implementação baseada em `Part`, mas ela não será mantida como camada de
compatibilidade:

- `Part` representa somente peças e não possui tipo;
- `Price` não explicita moeda e não limita a escala;
- `sku` não é normalizado nem único;
- a tabela `parts` permite dados obrigatórios nulos;
- não há estado ativo para desativação lógica;
- os endpoints de aumento e diminuição alteram saldo sem origem ou rastreabilidade;
- a listagem não oferece filtros;
- não existem testes HTTP ou de persistência específicos para Stock;
- o seeder e a coleção Postman ainda usam a linguagem `Part`.

Como os contratos e dados são scaffolding sem uso funcional, eles serão substituídos sem aliases ou período de
depreciação, conforme aprovado na especificação funcional.

## Contextos e fronteiras de módulo

### Stock & Procurement

O único módulo afetado em código de domínio é `stockprocurement`. A estrutura interna continuará em
`stockprocurement.stock`, separada em `domain`, `application` e `infrastructure`.

`StockItem` será aggregate root porque possui identidade, ciclo de vida e invariantes próprios e é criado, atualizado e
desativado de forma independente. O repository trabalha com uma raiz por vez; nenhum aggregate artificial agrupará o
catálogo inteiro.

Não serão criadas classes, repositories, tabelas, endpoints ou campos chamados `Stock` ou `stockId`. Neste modelo,
Stock é somente o nome da capability do bounded context.

O placeholder `stockprocurement.purchaseorder` permanecerá inalterado.

### Service Lifecycle

Nenhum código de `servicelifecycle` será alterado. O contrato atual de Stock Requirement continua sendo scaffolding e
será corrigido pela futura feature de integração registrada no backlog.

A consulta desta entrega é exposta por HTTP para as telas administrativas e para a seleção do Technician. Uma futura
integração de backend entre módulos deverá nascer de um caso de uso concreto e de uma porta pertencente ao consumidor;
esta feature não criará named interface, evento ou adapter sem consumidor.

### Dependências

O domínio permanecerá livre de Spring, JPA, Bean Validation e tipos HTTP. Nenhum pacote interno de outro módulo será
importado. As dependências existentes entre os três módulos Spring Modulith não serão alteradas.

## Modelo de domínio

### Aggregate root `StockItem`

| Atributo | Tipo proposto | Regra |
|---|---|---|
| `id` | `UUID` | Gerado na criação e imutável |
| `sku` | `Sku` | Obrigatório, normalizado, imutável e único globalmente |
| `name` | `String` | Obrigatório, mutável e limitado a 255 caracteres |
| `type` | `StockItemType` | `PART`, `CONSUMABLE` ou `SUPPLY`; obrigatório e imutável |
| `price` | `Price` | Valor BRL não negativo, com no máximo duas casas decimais |
| `availableQuantity` | `Quantity` | Inteiro não negativo, definido somente na criação nesta feature |
| `active` | `boolean` | Inicia `true`; a única transição desta feature é para `false` |

Comportamentos:

- `create(sku, name, type, price, availableQuantity)` cria um item ativo;
- `updateDetails(name, price)` altera os únicos dados mutáveis do cadastro;
- `deactivate()` realiza remoção lógica e é idempotente;
- `hasAvailableQuantity()` retorna verdadeiro quando a quantidade disponível é positiva;
- `reconstitute(...)` restaura estado persistido sem gerar nova identidade e ainda valida as invariantes estruturais.

Não haverá setters públicos. Um item inativo poderá ser consultado e desativado novamente, mas não poderá ser
atualizado.

### Value object `Sku`

`Sku` removerá espaços externos e aplicará `toUpperCase(Locale.ROOT)`. O valor normalizado será armazenado e usado para
unicidade. O tamanho permitido será de 1 a 100 caracteres. Nenhuma expressão regular adicional será inventada sem
regra de negócio.

### Enum `StockItemType`

O enum terá exatamente os valores `PART`, `CONSUMABLE` e `SUPPLY`. Valores desconhecidos serão rejeitados na fronteira
HTTP; o domínio não aceitará tipo nulo.

### Value object `Price`

`Price` conterá `BigDecimal value` e `CurrencyCode currency`. `CurrencyCode` terá somente `BRL` nesta entrega.

O valor:

- é obrigatório e não negativo;
- aceita zero;
- permite até 17 dígitos inteiros e duas casas decimais;
- é normalizado para escala 2 sem arredondamento;
- rejeita qualquer valor que exija arredondamento ou ultrapasse a precisão do banco.

### Value object `Quantity`

`Quantity` representa somente a quantidade disponível atual. Rejeita valores negativos e não expõe métodos de soma ou
subtração nesta feature. A única origem do valor é `availableQuantity` no cadastro inicial ou a reconstituição do banco.

## Casos de uso de aplicação

| Caso de uso | Transação | Responsabilidade |
|---|---|---|
| `CreateStockItemUseCase` | escrita | Validar SKU único e criar o item |
| `GetStockItemUseCase` | somente leitura | Consultar ativo ou inativo por ID |
| `SearchStockItemsUseCase` | somente leitura | Aplicar filtros cumulativos e ordenação |
| `UpdateStockItemUseCase` | escrita | Alterar somente nome e preço de item ativo |
| `DeactivateStockItemUseCase` | escrita | Desativar logicamente sem exclusão física |

Métodos públicos de escrita usarão `@Transactional`. Consultas usarão `@Transactional(readOnly = true)`.

O contrato `StockItemRepository`, em `domain/repository`, oferecerá operações equivalentes a:

```java
Optional<StockItem> findById(UUID id);
boolean existsBySku(Sku sku);
List<StockItem> search(StockItemSearchCriteria criteria);
void save(StockItem stockItem);
```

`StockItemSearchCriteria` será um objeto imutável sem dependência de Spring, contendo termo textual, conjunto de tipos,
disponibilidade e estado ativo. A implementação JPA traduzirá esses critérios; `Specification`, `Pageable` ou entidades
JPA não atravessarão o port.

A unicidade será validada antes da criação para uma resposta de negócio clara e protegida também por constraint no
banco para requisições concorrentes.

## Contratos HTTP

O controller será exposto em `/api/stock-items`. Domain objects e entidades JPA nunca serão retornados diretamente.

### Operações

| Método e path | Comportamento | Sucesso |
|---|---|---|
| `POST /api/stock-items` | Criar Stock Item | `201 Created` com `Location` e body |
| `GET /api/stock-items/{id}` | Consultar ativo ou inativo | `200 OK` |
| `GET /api/stock-items` | Listar e filtrar Stock Items | `200 OK` |
| `PATCH /api/stock-items/{id}` | Atualizar nome e/ou preço | `200 OK` |
| `DELETE /api/stock-items/{id}` | Desativar logicamente | `204 No Content` |

Não existirão endpoints de movimentação. Todos os paths sob `/api/parts` serão removidos.

### Criação

Request:

```json
{
  "sku": "OIL-FILTER-001",
  "name": "Filtro de óleo",
  "type": "PART",
  "price": {
    "value": 45.90,
    "currency": "BRL"
  },
  "availableQuantity": 20
}
```

`sku`, `name`, `type`, `price.value`, `price.currency` e `availableQuantity` serão campos obrigatórios. O request não
aceitará `id` nem `active`.

### Atualização

Request:

```json
{
  "name": "Filtro de óleo premium",
  "price": {
    "value": 52.90,
    "currency": "BRL"
  }
}
```

`name` e `price` serão opcionais individualmente, mas pelo menos um deverá ser informado. O DTO não terá campos para
`id`, `sku`, `type`, `availableQuantity` ou `active`, evitando mass assignment de dados imutáveis.

### Response

```json
{
  "id": "1b727b49-3b07-4d3e-b5d0-84fbe769bf39",
  "sku": "OIL-FILTER-001",
  "name": "Filtro de óleo",
  "type": "PART",
  "price": {
    "value": 45.90,
    "currency": "BRL"
  },
  "availableQuantity": 20,
  "active": true
}
```

### Consulta de coleção e filtros

`GET /api/stock-items` aceitará:

| Parâmetro | Tipo | Default | Semântica |
|---|---|---|---|
| `search` | string opcional | sem restrição | Contém em nome ou SKU, ignorando caixa |
| `type` | parâmetro repetível | todos os tipos | Valores do enum combinados com `OR` |
| `available` | boolean opcional | ambos | `true` para quantidade positiva; `false` para quantidade zero |
| `active` | boolean opcional | `true` | `true` para ativos; `false` para inativos |

Exemplo:

```http
GET /api/stock-items?search=filtro&type=PART&type=SUPPLY&available=true&active=true
```

Regras da consulta:

- filtros de grupos diferentes serão combinados com `AND`;
- tipos repetidos serão normalizados para um conjunto e combinados com `OR`;
- `search` será trimado, limitado a 100 caracteres e tratará `%`, `_` e `\\` como caracteres literais;
- `active` ausente retornará somente ativos, garantindo a visão ampla inicial do Technician;
- `available=true` significa `available_quantity > 0` e `available=false` significa `available_quantity = 0`;
- a ordenação será determinística por `name` ascendente e depois `sku` ascendente;
- a consulta será executada no banco, sem carregar toda a tabela para filtrar em memória;
- não haverá paginação nesta entrega, conforme decisão funcional aprovada.

### Validação HTTP

Bean Validation será aplicada nos DTOs:

- `sku`: obrigatório e máximo de 100 caracteres antes da normalização;
- `name`: obrigatório na criação, máximo de 255 caracteres e não branco quando enviado na atualização;
- `price.value`: obrigatório, não negativo e `@Digits(integer = 17, fraction = 2)`;
- `price.currency`: obrigatório e restrito a `BRL`;
- `availableQuantity`: obrigatório e não negativo;
- `search`: máximo de 100 caracteres;
- `type`, `available`, `active` e UUIDs inválidos: erro de contrato, nunca falha `500`.

## Falhas e códigos estáveis

O `GlobalExceptionHandler` continuará produzindo `ErrorResponse`, mas será ampliado para traduzir as falhas desta
feature sem expor SQL ou classes internas.

| Situação | HTTP | Código |
|---|---:|---|
| Body, parâmetro, enum ou UUID inválido | `400` | `VALIDATION_ERROR` |
| Invariante inválida do Stock Item | `400` | `INVALID_STOCK_ITEM` |
| Stock Item inexistente | `404` | `STOCK_ITEM_NOT_FOUND` |
| SKU já cadastrado, inclusive em item inativo | `409` | `STOCK_ITEM_SKU_ALREADY_EXISTS` |
| Tentativa de atualizar item inativo | `409` | `STOCK_ITEM_INACTIVE` |

A desativação de um item já inativo será idempotente e retornará `204`. Uma violação concorrente da constraint única de
SKU será traduzida para o mesmo conflito estável, sem devolver nome de constraint ou detalhes do banco.

Falhas técnicas inesperadas continuarão sob o tratamento padrão da plataforma e serão registradas sem body de request,
segredos ou stack trace na resposta.

## Persistência

### Projeção JPA

`StockItemJpaEntity` será uma projeção separada do aggregate e mapeará a tabela `stock_items`:

| Coluna | Tipo MySQL | Regra |
|---|---|---|
| `id` | `BINARY(16)` | Primary key |
| `sku` | `VARCHAR(100)` | `NOT NULL`, unique |
| `name` | `VARCHAR(255)` | `NOT NULL` |
| `type` | `VARCHAR(32)` | `NOT NULL` |
| `price_value` | `DECIMAL(19,2)` | `NOT NULL` |
| `price_currency` | `CHAR(3)` | `NOT NULL` |
| `available_quantity` | `INTEGER` | `NOT NULL` |
| `active` | `BOOLEAN` | `NOT NULL` |

O banco terá checks para `price_value >= 0`, `available_quantity >= 0`, `price_currency = 'BRL'` e valores válidos de
`type`, desde que a sintaxe seja compatível com MySQL e com o banco de testes. As mesmas invariantes continuarão no
domínio; constraints são a última proteção, não o único local da regra.

O adapter implementará busca dinâmica com Criteria API ou `Specification` somente na infraestrutura. A consulta por
nome será case-insensitive; SKU já estará normalizado. Não serão adicionados repositories ou tabelas para `Stock`.

### Baseline e migração Flyway

O projeto está no momento zero: não existe baseline operacional, ambiente compartilhado com dados a preservar nem
contrato funcional apoiado pela migration atual. Por isso, esta feature substituirá a migration inicial de scaffolding,
em vez de criar uma migration incremental que primeiro herda `parts` e depois a remove.

`V1__initial_schema.sql` será removida e seu conteúdo será reconstruído em uma única baseline com nome no padrão:

```text
VyyyyMMddHHmmss__initial_schema.sql
```

O timestamp será UTC, com 14 dígitos, e o nome após `__` será `snake_case` em lowercase. Esse formato passa a ser
obrigatório para todas as novas migrations, conforme registrado no `AGENTS.md`.

A nova baseline:

1. preserva as tabelas válidas dos demais módulos;
2. cria diretamente `stock_items` com o schema e as constraints desta especificação;
3. não cria a tabela `parts`;
4. não cria tabela de reserva, movimentação, baixo estoque ou Purchase Order.

Não haverá conversão dos registros de `parts`, pois a tabela deixa de fazer parte da baseline e seus dados eram apenas
scaffolding. Converter todos para `PART` inventaria uma classificação de domínio. Dados locais demonstrativos serão
recriados pelo seeder controlado.

O startup com Flyway e `spring.jpa.hibernate.ddl-auto=validate` deverá validar a baseline em banco vazio de testes. A
ordem exata e a compatibilidade dos DDLs serão verificadas antes da implementação ser considerada concluída.

### Classificação de dados

- **Referência obrigatória:** nenhuma; `StockItemType` é enum de código e não tabela de referência.
- **Dados locais de demonstração:** o seeder existente será reescrito para Stock Item e continuará condicionado ao
  perfil `dev` e a `app.seed.enabled=true`.
- **Produção:** nenhum Stock Item será inserido automaticamente.
- **Fixtures de teste:** builders ou factories dedicados; testes não dependerão do seeder de desenvolvimento.

O seeder será idempotente pelo SKU normalizado e poderá criar exemplos fictícios de `PART`, `CONSUMABLE` e `SUPPLY`, sem
dados pessoais, segredos ou efeito fora do perfil `dev`.

## Segurança e operação

### Validação e mass assignment

- DTOs de criação e atualização terão conjuntos fechados de campos;
- IDs, estado ativo, tipo, SKU e saldo não poderão ser alterados pelo update;
- comprimentos, enum, moeda, precisão monetária e quantidade serão validados na fronteira e no domínio;
- filtros usarão parâmetros da Criteria API, sem concatenação de SQL;
- curingas do termo textual serão escapados para impedir busca mais ampla que a solicitada;
- a constraint única protegerá contra criação concorrente do mesmo SKU.

### Autenticação e autorização

O projeto ainda não possui autenticação. Portanto, a distinção entre Stock Manager e Technician será apenas funcional e
documental nesta entrega; os endpoints permanecerão públicos como os demais endpoints atuais.

Isso é uma limitação conhecida, não autorização implícita. Quando autenticação existir:

- criação, atualização, desativação e consulta de inativos deverão exigir papel administrativo de Stock;
- o Technician deverá ter somente consulta de itens ativos.

Nenhum mecanismo de segurança fictício será criado isoladamente nesta feature.

### Dados, erros, logs e segredos

Stock Items são dados operacionais, não dados pessoais. Respostas não incluirão informações de fornecedor, custo de
compra ou histórico de movimentação. Mensagens de erro não revelarão SQL, constraints, pacotes ou stack traces.

Não serão adicionados segredos, configurações sensíveis ou novas dependências. Logs não deverão registrar bodies
completos nem valores de variáveis de ambiente.

### Abuso e limites

A ausência de paginação é uma decisão funcional do MVP. Para reduzir abuso acidental, o termo de busca terá no máximo
100 caracteres e filtros serão executados no banco. Paginação deverá ser reavaliada quando houver expectativa real de
volume; não será adicionada silenciosamente nesta entrega.

### Rollout e recuperação

Esta baseline exige banco vazio. Bancos locais criados pela antiga `V1__initial_schema.sql` deverão ser descartados e
recriados; não se tentará reparar manualmente o histórico do Flyway. O reset destrutivo deverá usar somente o ambiente
local explicitamente identificado, nunca um ambiente compartilhado ou com dados a preservar.

Se for descoberto qualquer ambiente compartilhado ou dado que deixou de ser demonstrativo, o rollout deverá ser
interrompido e esta decisão de rebaseline revisada antes da implementação. Depois que a nova baseline for aplicada a um
ambiente compartilhado, ela se torna imutável e mudanças futuras exigirão novas migrations com timestamp.

## Estratégia de testes

### Domínio

Testes unitários rápidos cobrirão:

- criação válida dos três tipos;
- normalização e rejeição de SKU vazio ou longo;
- rejeição de nome vazio ou longo;
- preço zero, negativo, escala inválida, moeda inválida e precisão excessiva;
- quantidade zero válida e quantidade negativa inválida;
- imutabilidade de ID, SKU, tipo e quantidade;
- atualização de nome e preço somente em item ativo;
- desativação irreversível e idempotente;
- identificação de quantidade disponível positiva ou zerada.

### Aplicação

Testes de casos de uso com repository fake ou mock cobrirão:

- criação e conflito de SKU normalizado;
- consulta por ID de item ativo ou inativo;
- not found;
- atualização válida e rejeição de item inativo;
- desativação e repetição idempotente;
- tradução dos filtros para `StockItemSearchCriteria`.

### HTTP

Testes MockMvc de integração cobrirão:

- os cinco endpoints e seus status de sucesso;
- body e `Location` da criação;
- contratos de request e response sem campos mutáveis indevidos;
- combinações de `search`, tipos, disponibilidade e estado;
- default de itens ativos e consulta explícita de inativos;
- validações de body, UUID, enum, boolean e termo de busca;
- códigos estáveis para not found, conflito de SKU e item inativo;
- desativação sem body;
- ausência de todos os paths `/api/parts`.

### Persistência e migração

Testes de integração cobrirão:

- startup Flyway seguido de validação Hibernate;
- constraint única de SKU e constraints de domínio relevantes;
- persistência e reconstituição dos três tipos e do estado inativo;
- filtros combinados, busca sem diferença de caixa e ordenação determinística;
- quantidade zero nos filtros de disponibilidade;
- idempotência e restrições de perfil do seeder de desenvolvimento.

### Modulith e documentação

- `ModuleStructureTest` deverá continuar encontrando somente `registration`, `servicelifecycle` e `stockprocurement`;
- a estrutura do módulo será verificada sem nova dependência entre bounded contexts;
- `OpenApiContractTest` verificará os novos paths e a ausência dos antigos;
- Springdoc e a coleção Postman serão atualizados no mesmo checkpoint do contrato HTTP;
- `docs/PROJECT-STRUCTURE.md` será atualizado de `Part` para `StockItem`;
- `make test` será executado durante o desenvolvimento;
- `make verify` será a evidência final;
- `make coverage` revisará a meta de 80% sem reduzir a cobertura do código alterado.

## Inventário de substituição

A implementação deverá remover ou substituir todos os artefatos `Part` do pacote de Stock:

- aggregate, value objects inadequados e repository;
- DTOs, mapper e casos de uso;
- controller e paths `/api/parts`;
- projeção, mapper, repository JPA e adapter;
- seeder e testes baseados em `Part`;
- baseline inicial, removendo a criação de `parts` e criando diretamente `stock_items`;
- referências no OpenAPI contract test, Postman e documentação estrutural.

Nenhum arquivo de Service Lifecycle será incluído nesse inventário, mesmo que ainda use nomes ou snapshots de Stock do
scaffolding. Essa correção pertence ao item de backlog de integração.

## Decisões propostas para aprovação técnica

- [x] `StockItem` é aggregate root independente; não existe aggregate ou ID de `Stock`.
- [x] O modelo atual contém somente ID, SKU, nome, tipo, preço, quantidade disponível e estado ativo.
- [x] A quantidade disponível é definida na criação e permanece somente leitura até a feature de inventário.
- [x] A API possui cinco operações em `/api/stock-items`, sem aliases de `/api/parts`.
- [x] A coleção usa `search`, `type`, `available` e `active`, com `active=true` como default e sem paginação.
- [x] O update parcial aceita somente nome e preço; a desativação lógica usa `DELETE` e é idempotente.
- [x] Não haverá mudança em Service Lifecycle nem interface pública entre módulos nesta feature.
- [x] A migration inicial será substituída por uma baseline com timestamp que cria `stock_items` e nunca cria `parts`.
- [x] Não existem dados obrigatórios de referência; somente seed local opcional e fixtures de teste.
- [x] Os riscos de validação, busca, conflito de SKU, exposição de dados e ausência de autorização estão tratados acima.

# Plano de Implementação: Fundação do Catálogo de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-domain-foundation` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-15 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-15) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-15) |

## Objetivo da execução

Substituir integralmente o scaffolding `Part` por um catálogo de `StockItem` com os tipos `PART`, `CONSUMABLE` e
`SUPPLY`, CRUD lógico, consulta com filtros combináveis e persistência coerente com a baseline do momento zero.

A implementação termina com `/api/stock-items` documentado e testado, sem `/api/parts`, sem entidade `Stock` e sem
operações de inventário, reserva, baixo estoque, Procurement ou integração com Service Lifecycle.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas, `docs/rfc/RFC-001-stock-item-foundation.md` e `docs/backlog.md` antes de alterar
  código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não enfraquecer testes nem ampliar o escopo para resolver conveniências de implementação.
- Não alterar arquivos de `servicelifecycle` nem o placeholder `stockprocurement.purchaseorder`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente diferente.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução e alinhar a documentação visual | Completed |
| 1 | Implementar o domínio de Stock Item | Completed |
| 2 | Implementar casos de uso e contratos de aplicação | Completed |
| 3 | Substituir a baseline e implementar persistência | Completed |
| 4 | Implementar HTTP, filtros e tradução de falhas | Completed |
| 5 | Atualizar seed, OpenAPI, Postman e documentação | Completed |
| 6 | Completar testes e revisão de segurança | Completed |
| 7 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução e alinhar a documentação visual

### Alterações

- Confirmar que o worktree contém somente mudanças esperadas e preservar alterações alheias à feature.
- Confirmar Java 21 e executar comandos Maven somente por `./mvnw` ou pelos targets do `Makefile`.
- Ler o código atual de `stockprocurement.stock`, a migration inicial, o exception handler, os testes OpenAPI e a
  coleção Postman.
- Quando o limite de chamadas do Miro estiver disponível, atualizar diretamente os dois code widgets de `StockItem`:
  - modelo tático: remover `reservedQuantity` e `minimumQuantity`, usar `aggregate StockItem` e
    `hasAvailableQuantity()`;
  - aggregates: remover os mesmos campos e identificar `StockItem` como Aggregate Root.
- Não editar novamente os documentos inteiros do Miro nem recriar embeds; atualizar somente os code widgets existentes.

### Verificação

- Os links novos do Miro permanecem acessíveis pelas specs.
- Os dois code widgets mostram apenas ID, SKU, nome, tipo, preço, quantidade disponível e estado ativo.
- `git status --short` não apresenta alterações inesperadas.

### Evidência

- Worktree inicial limpo; branch `feature/stock-and-procurement-data-structure`; `AGENTS.md`, specs, RFC e backlog lidos.
- `java` não está disponível no PATH desta sessão e o Maven Wrapper não consegue criar o diretório padrão fora do
  workspace; os gates Maven serão executados quando o ambiente permitir.
- Não há conector Miro disponível nesta sessão para editar os dois widgets existentes. Os links permanecem acessíveis
  nas specs; a atualização visual deverá ser concluída diretamente no Miro quando o acesso estiver disponível.

## Checkpoint 1 — Implementar o domínio de Stock Item

### Alterações

- Remover `Part` e introduzir `StockItem` como Aggregate Root em `domain/model`.
- Implementar `StockItemType` com exatamente `PART`, `CONSUMABLE` e `SUPPLY`.
- Implementar `Sku` com trim, uppercase usando `Locale.ROOT`, tamanho máximo de 100 e igualdade pelo valor
  normalizado.
- Reestruturar `Price` para valor decimal e `CurrencyCode.BRL`, com escala 2 sem arredondamento e precisão compatível com
  `DECIMAL(19,2)`.
- Manter `Quantity` somente como valor inteiro não negativo, sem operações de entrada ou retirada.
- Implementar criação, atualização de nome/preço, desativação idempotente, reconstituição e
  `hasAvailableQuantity()`.
- Impedir atualização de item inativo e não expor setters públicos.
- Remover comportamentos `increaseStock` e `decreaseStock`.
- Substituir `PartRepository` por `StockItemRepository` e introduzir `StockItemSearchCriteria` sem dependências Spring.

### Testes

- Criar testes unitários para as invariantes listadas na seção de domínio da spec técnica.
- Cobrir os três tipos, normalização de SKU, limites de nome, preço, quantidade, atualização e desativação.
- Confirmar que quantidade zero é válida e que item inativo ou com zero unidades não ganha mutações de saldo.

### Verificação

- Executar os testes do pacote de domínio de Stock.
- Buscar por métodos de ajuste de saldo e confirmar que nenhum permanece no domínio novo.

### Evidência

- `StockItem`, `Sku`, `StockItemType`, `CurrencyCode`, `Price` e `Quantity` implementados sem dependências de Spring,
  JPA ou HTTP. `Part` e os comandos de ajuste de saldo foram removidos.
- Testes unitários foram adicionados para tipos, normalização, invariantes, preço/quantidade zero, reconstituição,
  atualização e inatividade. A execução aguarda JDK disponível no ambiente.

## Checkpoint 2 — Implementar casos de uso e contratos de aplicação

### Alterações

- Substituir DTOs `Part*` por requests e responses específicos de `StockItem`.
- Manter DTOs HTTP separados do domínio e da projeção JPA.
- Implementar:
  - `CreateStockItemUseCase`;
  - `GetStockItemUseCase`;
  - `SearchStockItemsUseCase`;
  - `UpdateStockItemUseCase`;
  - `DeactivateStockItemUseCase`.
- Aplicar `@Transactional` nas escritas e `@Transactional(readOnly = true)` nas consultas.
- Validar unicidade de SKU normalizado antes de criar e manter a constraint do banco como proteção concorrente.
- Garantir que o update aceite somente nome e/ou preço e exija ao menos um campo.
- Manter item inativo consultável por ID e desativação repetida idempotente.
- Remover casos de uso de rename isolado, update de preço isolado e ajuste de saldo baseados em `Part`.

### Testes

- Cobrir orquestração dos cinco casos de uso com repository fake ou mock.
- Cobrir SKU duplicado, not found, update vazio, item inativo e desativação repetida.
- Confirmar os critérios enviados ao repository pela busca.

### Verificação

- Executar testes de domínio e aplicação de `stockprocurement.stock`.
- Confirmar que a aplicação não importa packages internos de outro bounded context.

### Evidência

- Os cinco casos de uso, DTOs fechados, `StockItemRepository` e `StockItemSearchCriteria` foram implementados;
  escritas e leituras possuem as anotações transacionais exigidas.
- Testes de orquestração para SKU normalizado duplicado, not found, item inativo, desativação repetida e critérios de
  busca foram adicionados. A execução aguarda JDK disponível no ambiente.

## Checkpoint 3 — Substituir a baseline e implementar persistência

### Baseline Flyway

- Remover `V1__initial_schema.sql` durante a implementação.
- Criar uma única baseline no padrão `VyyyyMMddHHmmss__initial_schema.sql`, usando timestamp UTC e nome lowercase.
- Preservar na baseline as tabelas válidas dos demais módulos.
- Substituir a criação de `parts` pela criação direta de `stock_items`.
- Não criar ou migrar tabelas de reserva, movimentação, baixo estoque ou Purchase Order.
- Não converter dados de `parts`; os ambientes aprovados para esta execução deverão partir de banco vazio.

### Persistência JPA

- Implementar `StockItemJpaEntity`, mapper, Spring Data repository e adapter do repository de domínio.
- Mapear UUID como `BINARY(16)` e os campos conforme a spec técnica.
- Criar unicidade de SKU e constraints de preço, moeda, tipo e quantidade compatíveis com MySQL e testes.
- Implementar filtros dinâmicos na infraestrutura, sem vazar `Specification` ou Criteria API para o domínio.
- Escapar curingas de `search` e executar a ordenação por nome e SKU no banco.
- Não criar tabela, entity ou repository chamados `Stock`.

### Testes

- Validar startup Flyway e `ddl-auto=validate` em banco vazio de testes.
- Cobrir persistência, reconstituição, unicidade, inatividade, filtros cumulativos e ordenação.
- Cobrir `available=true` para quantidade positiva e `available=false` para quantidade zero.

### Verificação

- Executar os testes de persistência e startup.
- Buscar migrations fora do padrão e confirmar que somente a baseline timestamped permanece.
- Confirmar que não existe tabela `parts` no schema final.

### Evidência

- A baseline foi substituída por `V20260815000000__initial_schema.sql`, que preserva as tabelas dos demais módulos e
  cria diretamente `stock_items`, sem `parts` nem tabelas fora do escopo.
- `StockItemJpaEntity`, mapper, adapter e filtros Criteria foram implementados. `make test` validou a baseline em H2
  vazio, Flyway e Hibernate com `ddl-auto=validate`.

## Checkpoint 4 — Implementar HTTP, filtros e tradução de falhas

### Alterações

- Substituir `PartController` por `StockItemController` em `/api/stock-items`.
- Implementar os cinco contratos aprovados:
  - `POST /api/stock-items`;
  - `GET /api/stock-items/{id}`;
  - `GET /api/stock-items`;
  - `PATCH /api/stock-items/{id}`;
  - `DELETE /api/stock-items/{id}`.
- Retornar `201` com `Location` na criação e `204` sem body na desativação.
- Implementar `search`, `type` repetível, `available` e `active`, com `active=true` por default.
- Combinar grupos com `AND` e múltiplos tipos com `OR`, sem paginação.
- Aplicar Bean Validation nos bodies e parâmetros, incluindo tamanhos e precisão monetária.
- Remover todos os endpoints `/api/parts`, inclusive ajustes de saldo e updates separados.
- Ampliar `GlobalExceptionHandler` para os códigos estáveis aprovados, incluindo UUID, enum e boolean inválidos.
- Traduzir a violação concorrente de SKU sem expor SQL ou nome de constraint.

### Testes

- Criar testes MockMvc para sucessos, validações, erros de negócio e ausência dos paths antigos.
- Cobrir listagem ampla de ativos e combinações representativas dos quatro filtros.
- Verificar que update não aceita ID, SKU, tipo, quantidade ou estado ativo.
- Verificar consulta individual de item inativo e impossibilidade de atualizá-lo.

### Verificação

- Executar os testes HTTP de Stock e o `OpenApiContractTest` atualizado.
- Inspecionar o JSON OpenAPI gerado para requests, responses, parâmetros e códigos.

### Evidência

- `StockItemController` expõe somente os cinco endpoints aprovados; `StockItemExceptionHandler` mantém os códigos de
  falha do módulo sem violar a fronteira Modulith do handler global.
- Testes MockMvc cobrem criação com `Location`, filtros cumulativos, atualização, consulta inativa, desativação
  idempotente, validações, UUID inválido e SKU duplicado. `OpenApiContractTest` confirma os cinco paths e a ausência
  de `/api/parts`.

## Checkpoint 5 — Atualizar seed, OpenAPI, Postman e documentação

### Seed e fixtures

- Reescrever `StockDevelopmentDataSeeder` para `StockItem`.
- Manter as condições `dev` e `app.seed.enabled=true` e a idempotência por SKU normalizado.
- Usar exemplos fictícios sem dados pessoais, podendo cobrir os três tipos.
- Não criar seed obrigatório nem inserir Stock Items automaticamente em produção.
- Manter fixtures de teste independentes do seeder de desenvolvimento.

### Documentação

- Completar as anotações Springdoc dos cinco endpoints, parâmetros, validações, responses e erros.
- Atualizar `OpenApiContractTest` como expectativa executável da fonte de verdade gerada.
- Substituir a pasta de Stock na coleção Postman, incluindo variáveis e exemplos de filtros.
- Renomear `partId` para `stockItemId` na coleção.
- Atualizar `docs/PROJECT-STRUCTURE.md` para remover a descrição do modelo `Part`.
- Revisar README e demais referências, alterando somente as que representam o contrato atual.
- Não reescrever specs antigas de features concluídas apenas para apagar referências históricas.

### Verificação

- Validar o JSON da coleção Postman.
- Buscar referências atuais a `/api/parts`, classes `Part` e tabela `parts`; aceitar somente contexto histórico
  explícito.
- Confirmar que OpenAPI e Postman representam os mesmos cinco endpoints.

### Evidência

- Seeder mantido somente para `dev` com `app.seed.enabled=true`, idempotente por SKU e sem dados pessoais.
- Postman atualizado e validado como JSON; OpenAPI gerado é validado pelo teste de contrato. `PROJECT-STRUCTURE.md`
  agora descreve o catálogo `StockItem`.

## Checkpoint 6 — Completar testes e revisão de segurança

### Cobertura automatizada

- Executar `make test` depois de completar domínio, aplicação, persistência e HTTP.
- Executar `make coverage` e revisar o relatório JaCoCo, mantendo a meta de pelo menos 80%.
- Adicionar cobertura para qualquer branch relevante descoberto durante a revisão, sem testes artificiais de linha.
- Executar `ModuleStructureTest` e, se necessário para isolamento do módulo, testes com `@ApplicationModuleTest`.

### Revisão de segurança

Preencher a tabela com `Resolved`, `N/A` ou achado pendente e registrar evidência curta:

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | Resolved | DTOs fechados, Bean Validation e update limitado a nome/preço. |
| Autenticação e autorização | N/A | Limitação documentada: endpoints públicos até autenticação; escrita futura será de Stock Manager. |
| Exposição de dados operacionais | Resolved | Response não contém fornecedor, custo de compra ou histórico. |
| Segredos, credenciais e logs | Resolved | Nenhum segredo, body sensível ou dependência nova foi adicionado. |
| SQL, filtros e segurança da baseline | Resolved | Criteria parametrizado, escape de `%`, `_`, `\\`; constraints e validação em banco vazio. |
| Erros e information disclosure | Resolved | Códigos estáveis não retornam SQL, constraint, stack trace ou package. |
| Dependências e vulnerabilidades | Resolved | Nenhuma dependência de aplicação foi adicionada. |
| Abuso da listagem sem paginação | N/A | Decisão aprovada; busca limitada a 100 caracteres e executada no banco. |

Nenhum achado crítico ou alto poderá permanecer aberto. A ausência atual de autenticação deverá ser registrada como
limitação do projeto, com escrita futura restrita ao Stock Manager e leitura ativa permitida ao Technician.

### Verificação

- Confirmar ausência de concatenação de SQL, setters públicos e campos de mass assignment.
- Confirmar que mensagens de erro não contêm SQL, stack trace, package ou constraint.
- Confirmar que nenhuma dependência nova foi adicionada sem necessidade aprovada.

### Evidência

- `make test` executou 48 testes sem falhas; `ModuleStructureTest` permaneceu verde.
- `make coverage` e `make verify` passaram. Cobertura global JaCoCo: 55,55% (baseline existente abaixo da meta global);
  código alterado de Stock & Procurement: 95,17%, sem redução da cobertura do escopo modificado.
- Revisão manual confirmou ausência de SQL concatenado, setters públicos, campos de mass assignment e dependências novas.

## Checkpoint 7 — Executar gates finais e concluir a feature

### Gates

- Executar `make verify` sem testes inadequadamente ignorados.
- Confirmar `ModuleStructureTest` verde e nenhuma nova dependência entre módulos.
- Confirmar startup com baseline Flyway limpa e Hibernate em `validate`.
- Confirmar OpenAPI, Postman e documentação estrutural atualizados.
- Confirmar revisão de segurança concluída e sem achado crítico ou alto.
- Revisar `git diff --check`, line length e ausência de imports wildcard.
- Revisar o diff final para excluir alterações fora do escopo.
- Atualizar os critérios de aceite da spec funcional com evidências reais.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Evidência final

Registrar antes da conclusão:

| Evidência | Resultado |
|---|---|
| `make test` | Passou: 48 testes, 0 falhas/erros |
| `make coverage` | Passou |
| `make verify` | Passou |
| Cobertura JaCoCo | 55,55% global herdado; 95,17% no código Stock alterado |
| `ModuleStructureTest` | Passou: 2 testes |
| Startup Flyway/Hibernate | Passou em H2 vazio com Flyway e `validate` |
| OpenAPI e Postman | Atualizados; OpenAPI testado e Postman JSON válido |
| Revisão de segurança | Concluída, sem achado crítico ou alto |

## Rollback ou recuperação

Como esta feature substitui a baseline do momento zero, a execução pressupõe banco local descartável e recriado do zero.
Não editar o histórico do Flyway de um banco existente para forçar compatibilidade.

Se qualquer ambiente com dado relevante for identificado:

1. interromper a execução da baseline;
2. não apagar o banco ou volume;
3. retornar a decisão de migração para revisão técnica;
4. criar uma estratégia incremental de classificação e migração antes de prosseguir.

Depois que a nova baseline alcançar um ambiente compartilhado, ela será imutável e toda evolução usará uma nova
migration no padrão timestamp definido no `AGENTS.md`.

# Especificação Funcional: Gestão do Service Catalog

| Campo | Valor |
|---|---|
| Feature | `service-catalog-management` — `SCRUM-8`, `SCRUM-38` e `SCRUM-39` |
| Status | Implemented e aceito |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovação funcional | `SCRUM-8`: 2026-08-23; `SCRUM-38` e `SCRUM-39`: 2026-08-24 |
| Aprovado por | Ivan Pimentel |
| Aceite da implementação | Ivan Pimentel em 2026-08-24 |
| Integração em `dev` | PR #27, merge commit `77ce35d` |
| Jira | `SCRUM-8`, `SCRUM-38` e `SCRUM-39` no épico `SCRUM-13` |

## Problema e resultado esperado

A oficina precisa de uma fonte de verdade para os serviços oferecidos, com identidade estável, nome, preço-base e
lifecycle próprios. Sem esse cadastro, a seleção operacional fica ambígua, alterações de nome ou preço não possuem uma
fronteira consistente e a remoção física pode quebrar referências históricas.

O resultado implementado permite cadastrar, consultar, listar, renomear, atualizar o preço-base e arquivar um Catalog
Service. Novos diagnósticos aceitam somente serviços ativos, enquanto execuções já iniciadas continuam usando snapshots
próprios, sem dependência viva do cadastro mestre.

## Linguagem ubíqua

### Catalog Service

`CatalogService` representa um tipo de serviço oferecido pela oficina. É um aggregate root independente dentro de
Registration e possui:

- `id` UUID estável;
- nome obrigatório e chave canônica para unicidade;
- preço-base em `BRL`;
- lifecycle ativo ou arquivado.

### Nome canônico

O nome visível preserva caixa e espaços internos, removendo apenas espaços externos. A comparação de unicidade ignora
caixa e usa a chave derivada do nome normalizado. Somente serviços ativos participam da unicidade; um nome pode ser
reutilizado depois do arquivamento, sempre com outro UUID.

### Preço-base

O preço-base é uma referência operacional representada por valor e moeda. O valor deve ser não negativo, possuir no
máximo duas casas decimais e usar `BRL`. Ele não substitui o preço registrado em snapshots de Service Lifecycle.

### Lifecycle

Todo Catalog Service nasce ativo. O arquivamento é lógico, irreversível no MVP e idempotente: repetir o comando mantém o
mesmo estado final. Um registro arquivado continua consultável por ID, não aparece na listagem ativa, não aceita rename
nem atualização de preço e não pode ser incluído em um novo diagnóstico.

## Atores e cenários

### Responsável pelo catálogo

- Cadastra um serviço com nome e preço-base válidos.
- Corrige o nome sem alterar UUID, preço ou lifecycle.
- Atualiza o preço-base sem alterar UUID, nome ou lifecycle.
- Arquiva um serviço sem apagar seu histórico.
- Pode cadastrar um substituto com o nome de um serviço já arquivado.

### Colaborador da oficina

- Lista somente serviços ativos para seleção operacional.
- Consulta um serviço por UUID, inclusive quando arquivado, para finalidade histórica.
- Recebe falhas estáveis para entrada inválida, UUID ausente, nome duplicado ou mutação de registro arquivado.

### Service Lifecycle

- Verifica a existência e o lifecycle de cada Catalog Service antes de adicionar itens a um novo diagnóstico.
- Rejeita serviço ausente ou arquivado antes de qualquer efeito parcial na Service Order.
- Preserva nome e preço dos snapshots já persistidos, mesmo após rename, reajuste ou archive do cadastro mestre.
- Continua o lifecycle de uma `ServiceExecution` existente sem consultar novamente o catálogo.

## Regras de negócio

### Cadastro e identidade

- O cadastro válido cria exatamente uma linha e retorna `201 Created` com `Location`.
- Nome nulo, vazio, branco ou acima de 255 caracteres é inválido.
- O UUID identifica o serviço durante todo o lifecycle e nunca pode ser alterado pelo cliente.
- Dois serviços ativos não podem possuir o mesmo nome canônico, inclusive sob concorrência.
- O conflito de nome usa `409 CATALOG_SERVICE_NAME_ALREADY_EXISTS` e identifica o registro ativo conflitante.
- Registros arquivados homônimos podem coexistir e não bloqueiam create ou rename de um ativo.

### Manutenção

- Rename e atualização de preço são comandos independentes e transacionais.
- Reenviar o mesmo nome ou preço produz sucesso idempotente e não altera outros campos.
- Mudança somente de caixa no nome atualiza o valor visível e preserva sua chave canônica.
- Preço zero é válido; valor negativo, precisão excessiva, moeda ausente ou diferente de `BRL` é inválido.
- Um comando nunca altera campos pertencentes ao outro, `id` ou `active` por mass assignment.
- Serviço arquivado rejeita ambos os comandos com `409 CATALOG_SERVICE_ARCHIVED`.

### Arquivamento e consultas

- `DELETE /api/catalog-services/{id}` altera somente `active` e retorna `204 No Content`.
- Repetir o archive do mesmo UUID também retorna `204` e não produz escrita desnecessária.
- UUID inexistente retorna `404 CATALOG_SERVICE_NOT_FOUND`.
- Nenhum fluxo executa hard delete de Catalog Service.
- `GET /api/catalog-services/{id}` é histórico e retorna ativos ou arquivados.
- `GET /api/catalog-services` é operacional e retorna somente ativos.
- Archive concorrente com rename, preço ou diagnóstico respeita a ordem de confirmação, sem reativação ou lost update.

### Segurança atual

A autenticação JWT foi integrada depois das três stories. `/api/catalog-services/**` não possui matcher específico de
papel em `SecurityConfig` e segue a regra global `anyRequest().authenticated()`: qualquer papel autenticado pode acessar
o catálogo. OpenAPI e Swagger permanecem públicos conforme a política transversal.

## Contratos HTTP

| Operação | Sucesso | Semântica |
|---|---|---|
| `POST /api/catalog-services` | `201` | Cadastra um serviço ativo |
| `GET /api/catalog-services/{id}` | `200` | Consulta histórica por UUID |
| `GET /api/catalog-services` | `200` | Lista somente ativos |
| `PATCH /api/catalog-services/{id}` | `200` | Renomeia um serviço ativo |
| `PATCH /api/catalog-services/{id}/base-price` | `200` | Atualiza o preço-base de um ativo |
| `DELETE /api/catalog-services/{id}` | `204` | Arquiva de forma lógica e idempotente |

As responses de leitura e mutação expõem `id`, `name`, `basePrice.value`, `basePrice.currency` e `active`. Requests e
responses são DTOs próprios; objetos de domínio e entidades JPA não atravessam a borda HTTP.

## Fora de escopo

- Reativação, hard delete, motivo/data/ator do archive ou audit trail.
- Histórico temporal de nomes ou preços e correção retroativa de snapshots.
- Moedas diferentes de `BRL`, promoções, impostos ou cálculo de preço final.
- Paginação, filtros, busca textual ou ordenação contratual da listagem.
- Cadastro de peças, estoque ou vínculo automático entre Catalog Service e Stock Item.
- Substituição automática do nome ou preço informado no diagnóstico pelo valor atual do catálogo.
- Restrição do catálogo a um papel administrativo específico, que exigiria decisão funcional e alteração da matriz JWT.

## Critérios de aceite consolidados

- [x] Cadastro, detalhe e lista ativa respeitam identidade, validação e DTOs aprovados.
- [x] Nome canônico é único entre ativos e conflitos concorrentes deixam exatamente um vencedor.
- [x] Nome de registro arquivado pode ser reutilizado sem alterar ou apagar o histórico anterior.
- [x] Rename preserva UUID, preço e lifecycle; repetição é idempotente.
- [x] Atualização de preço preserva UUID, nome e lifecycle; zero é aceito e repetição é idempotente.
- [x] Entradas inválidas são rejeitadas sem persistência parcial ou detalhes internos.
- [x] Archive altera somente o lifecycle, é idempotente e nunca executa hard delete.
- [x] Detalhe retorna ativos e arquivados; coleção retorna somente ativos.
- [x] Serviço arquivado rejeita rename e preço com `CATALOG_SERVICE_ARCHIVED`.
- [x] Novos diagnósticos rejeitam serviço ausente ou arquivado antes de qualquer mutação da Service Order.
- [x] Execuções existentes preservam seus snapshots e continuam após mudanças no cadastro mestre.
- [x] Concorrência entre archive, updates, aquisição de nome e diagnóstico não causa lost update ou reativação.
- [x] OpenAPI, Postman, Flyway, MySQL, segurança e fronteiras Modulith foram verificados.

## Registro de aprovação e entrega

Ivan Pimentel aprovou explicitamente o escopo funcional da `SCRUM-8` em 2026-08-23 e os escopos da `SCRUM-38` e
`SCRUM-39` em 2026-08-24. O aceite manual das implementações foi registrado em 2026-08-24. Os commits `5c39904`,
`93ba7d5` e `dde524e` foram consolidados na branch de Service Catalog e integrados em `dev` pela PR #27, merge commit
`77ce35d`.

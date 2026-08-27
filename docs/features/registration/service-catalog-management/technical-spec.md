# Especificação Técnica: Gestão do Service Catalog

| Campo | Valor |
|---|---|
| Feature | `service-catalog-management` — `SCRUM-8`, `SCRUM-38` e `SCRUM-39` |
| Status | Implemented e aceito |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovação técnica | `SCRUM-8`: 2026-08-23; `SCRUM-38` e `SCRUM-39`: 2026-08-24 |
| Aprovado por | Ivan Pimentel |
| Especificação funcional | `./functional-spec.md` |
| Integração em `dev` | PR #27, merge commit `77ce35d` |

## Contexto e desenho

Service Catalog permanece uma capability do bounded context `registration`, ao lado de Customer e Vehicle. A
implementação vive em `registration.servicecatalog` e não cria módulo de topo, bounded context ou domínio compartilhado.

O slice mantém o domínio livre de Spring, JPA e transporte:

- `domain.model`: `CatalogService`, `CatalogServiceName`, `Money`, `CurrencyCode` e falha de lifecycle;
- `domain.repository`: porta `CatalogServiceRepository`;
- `application.dto`: requests, response, `MoneyDto` e mapper;
- `application.usecase`: cadastro, consultas, rename, preço, archive e disponibilidade para novo trabalho;
- `application.api`: named interface mínima de elegibilidade;
- `infrastructure.persistence`: entidades, mappers, Spring Data e adapter da porta;
- `infrastructure.web`: controller REST e tradução de falhas.

## Modelo de domínio

`CatalogService` é o aggregate root e controla as mutações por métodos de negócio. `id` é imutável; `name`, `basePrice`
e `active` não possuem setters públicos.

### CatalogServiceName

- remove espaços externos e preserva caixa e espaços internos no valor visível;
- rejeita valor nulo, branco ou maior que 255 caracteres;
- deriva uma chave com `trim` e lower-case usando `Locale.ROOT`;
- não remove acentos nem colapsa espaços internos.

### Money

- usa `BigDecimal` e `CurrencyCode`;
- exige valor não negativo, precisão compatível com `DECIMAL(19,2)` e no máximo duas casas;
- aceita apenas `BRL` no MVP;
- permanece um value object próprio de Service Catalog.

### Lifecycle e mutações

- `create(...)` gera UUID e inicia `active=true`;
- `rename(...)` rejeita aggregate arquivado, atualiza o nome e informa se houve mudança;
- `updateBasePrice(...)` rejeita aggregate arquivado e informa se houve mudança;
- `archive()` altera somente `active`, retorna `true` na primeira transição e `false` nas repetições;
- `reconstitute(...)` restaura explicitamente o estado persistido.

## Aplicação e transações

| Caso de uso | Transação | Responsabilidade |
|---|---|---|
| `CreateCatalogServiceUseCase` | `@Transactional` | Validar nome/preço, impedir nome ativo duplicado e salvar |
| `GetCatalogServiceUseCase` | `readOnly = true` | Consultar qualquer lifecycle por UUID |
| `ListCatalogServicesUseCase` | `readOnly = true` | Listar somente ativos |
| `RenameCatalogServiceUseCase` | `@Transactional` | Bloquear a linha, validar lifecycle/unicidade e renomear |
| `UpdateCatalogServiceBasePriceUseCase` | `@Transactional` | Bloquear a linha e atualizar preço de ativo |
| `ArchiveCatalogServiceUseCase` | `@Transactional` | Bloquear a linha e arquivar idempotentemente |
| `CheckCatalogServiceAvailabilityUseCase` | `MANDATORY` | Bloquear a linha durante o diagnóstico consumidor |

Create e rename fazem pre-check determinístico por nome ativo. A constraint do banco continua sendo a autoridade final
contra corridas. O adapter traduz somente a violação da constraint conhecida para
`CatalogServiceNameAlreadyExistsException`; falhas técnicas diferentes não são mascaradas como regra de negócio.

Rename, preço e archive carregam o aggregate por `findByIdForUpdate` com lock pessimista. Repetições idempotentes evitam
`save` quando não há transição observável.

## Persistência e dados

### Migration inicial

`V20260824025720__create_catalog_services.sql` cria `catalog_services` com:

- `id BINARY(16)` como PK;
- `name VARCHAR(255)` e `normalized_name_key VARBINARY(1020)`;
- `base_price_value DECIMAL(19,2)` e `base_price_currency CHAR(3)`;
- `active BOOLEAN NOT NULL DEFAULT TRUE`;
- checks para valor não negativo e moeda `BRL`;
- unicidade inicial da chave normalizada.

### Unicidade somente entre ativos

`V20260824050859__allow_archived_catalog_service_name_reuse.sql` adiciona
`active_normalized_name_key`, coluna derivada anulável que contém a chave somente quando `active=true`. A migration cria
`uk_catalog_services_active_normalized_name_key` antes de remover a constraint global anterior. Múltiplos arquivados
homônimos permanecem válidos porque a coluna derivada resulta em `NULL` para eles.

As migrations são imutáveis após baseline operacional. A classificação é **no seed required**: não há referência
obrigatória, demonstração automática ou dado pessoal. `spring.jpa.hibernate.ddl-auto=validate` permanece obrigatório.

## Contratos HTTP e falhas

Base path: `/api/catalog-services`.

| Método e path | Request | Sucesso |
|---|---|---|
| `POST /api/catalog-services` | `CreateCatalogServiceRequest` | `201` + `Location` + response |
| `GET /api/catalog-services/{id}` | — | `200` + response histórica |
| `GET /api/catalog-services` | — | `200` + array de ativos |
| `PATCH /api/catalog-services/{id}` | `RenameCatalogServiceRequest` | `200` + response |
| `PATCH /api/catalog-services/{id}/base-price` | `UpdateCatalogServiceBasePriceRequest` | `200` + response |
| `DELETE /api/catalog-services/{id}` | — | `204` sem body |

`CatalogServiceResponse` expõe `UUID id`, `String name`, `MoneyDto basePrice` e `boolean active`. Os DTOs fechados e
Bean Validation impedem mass assignment de identidade, lifecycle ou campos pertencentes a outro comando.

| Situação | HTTP | Código estável |
|---|---|---|
| JSON, binding ou UUID inválido | `400` | `VALIDATION_ERROR` |
| Invariante de nome ou dinheiro inválida | `400` | `INVALID_CATALOG_SERVICE` |
| UUID não encontrado | `404` | `CATALOG_SERVICE_NOT_FOUND` |
| Nome ativo duplicado | `409` | `CATALOG_SERVICE_NAME_ALREADY_EXISTS` |
| Rename ou preço em arquivado | `409` | `CATALOG_SERVICE_ARCHIVED` |

Os handlers não expõem entidade JPA, SQL, constraint, stack trace ou tipo interno. OpenAPI gerada é a fonte de verdade;
não existe YAML manual. A collection Postman contém as seis operações e exemplos separados para archive.

## Integração com Service Lifecycle

`registration.servicecatalog.application.api` é publicada como named interface
`catalog-service-availability-api`. Ela expõe somente:

- `CatalogServiceAvailabilityApi.checkForNewWork(UUID)`;
- enum `ACTIVE`, `ARCHIVED` e `NOT_FOUND`.

Service Lifecycle define `CatalogServiceEligibilityPort` e o adapter
`RegistrationCatalogServiceEligibilityAdapter`, preservando a direção consumidora. `PerformDiagnosisUseCase` deduplica
e ordena os UUIDs antes dos checks, valida todos os serviços antes de mutar a Service Order e mantém a transação aberta
para que o lock adquirido pelo use case produtor dure até o commit consumidor.

Serviço ausente produz `404 CATALOG_SERVICE_NOT_FOUND`; arquivado produz `409 CATALOG_SERVICE_ARCHIVED`. Comandos que
continuam uma `ServiceExecution` existente não executam novo lookup e preservam o snapshot já persistido.

## Segurança e operação

- JWT é obrigatório no estado integrado em `dev`.
- `/api/catalog-services/**` não possui matcher específico e usa `anyRequest().authenticated()`.
- Uma futura restrição a `MANAGER`/`ADMIN` exige decisão funcional e atualização coordenada da matriz JWT e dos testes.
- DTOs fechados, validação no boundary e invariantes de domínio mitigam mass assignment e payload abusivo.
- O catálogo não contém dados pessoais, secrets ou credenciais e não adiciona dependência externa.
- Locks são locais ao banco, adquiridos em ordem estável no diagnóstico e limitados à transação.
- Erros e logs não devem registrar SQL, nomes de constraints ou conteúdo sensível.

## Estratégia de testes

- domínio: nome, dinheiro, lifecycle, idempotência e preservação de estado;
- aplicação: todos os use cases, precedência de falhas e ausência de save parcial;
- persistência: round-trip, queries ativas/históricas, locks e tradução da constraint;
- concorrência: create/rename homônimos, archive × update, archive × diagnóstico e reutilização de nome;
- HTTP: requests, responses, status, `Location`, validação e códigos estáveis;
- contratos: OpenAPI gerada e JSON da collection Postman;
- módulos: `ModuleStructureTest` e `CatalogServiceEligibilityApplicationModuleTest`;
- operação: Flyway e Hibernate validate em H2 e MySQL 8.0.46.

## Evidência de implementação

| Story | Commit | Evidência de fechamento |
|---|---|---|
| `SCRUM-8` | `5c39904` | 357 testes; cobertura global de linhas 93,60%; MySQL e contratos verdes |
| `SCRUM-38` | `93ba7d5` | 386 testes; cobertura global de linhas 93,72%; PATCHes e concorrência verdes |
| `SCRUM-39` | `dde524e` | 415 testes; cobertura global de linhas 93,86%; H2/MySQL e Modulith verdes |

As três entregas foram reconciliadas no commit `56e6a8d` e integradas em `dev` pela PR #27 (`77ce35d`). A autenticação
JWT foi integrada posteriormente pela PR #30 (`3c90b88`). Nenhum achado crítico ou alto permaneceu aberto nas revisões
de segurança registradas.

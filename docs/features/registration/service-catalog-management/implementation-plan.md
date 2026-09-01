# Plano de Implementação: Gestão do Service Catalog

| Campo | Valor |
|---|---|
| Feature | `service-catalog-management` — `SCRUM-8`, `SCRUM-38` e `SCRUM-39` |
| Status | Implemented |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` — aprovada |
| Especificação técnica | `./technical-spec.md` — aprovada |
| Aprovação do plano | `SCRUM-8`: 2026-08-23; `SCRUM-38` e `SCRUM-39`: 2026-08-24 |
| Autorização para código | Ivan Pimentel, registrada antes de cada implementação |
| Aceite manual | Ivan Pimentel em 2026-08-24 |
| Integração em `dev` | PR #27, merge commit `77ce35d` |

## Objetivo de entrega

Entregar Service Catalog como uma capability coesa de Registration, unificando as três stories do épico:

- `SCRUM-8`: cadastro, detalhe e listagem ativa;
- `SCRUM-38`: rename e atualização de preço-base;
- `SCRUM-39`: archive lógico, unicidade somente entre ativos e elegibilidade para novos diagnósticos.

Cada story seguiu os gates de especificação funcional, técnica, plano, autorização de código, testes, segurança e aceite
humano. Esta consolidação registra o estado final já integrado, sem alterar decisões de negócio aprovadas.

## Checkpoint 0 — gates e baseline

Status: `Completed`.

- [x] Especificações funcionais aprovadas antes das respectivas especificações técnicas.
- [x] Especificações técnicas aprovadas antes dos respectivos planos.
- [x] Planos aprovados e código autorizado explicitamente antes de cada implementação.
- [x] Baselines, branches, worktrees e interferências de outros módulos verificados.
- [x] Customer, Vehicle e Service Catalog mantidos sob o mesmo bounded context `registration`.

Evidência: aprovações de Ivan Pimentel em 2026-08-23 e 2026-08-24, preservadas nos metadados consolidados. Nenhum novo
bounded context ou módulo de topo foi criado.

## Checkpoint 1 — domínio e aplicação do catálogo

Status: `Completed`.

- [x] Implementar `CatalogService` como aggregate root livre de framework.
- [x] Implementar `CatalogServiceName`, `Money` e `CurrencyCode` com invariantes próprias.
- [x] Implementar create, get e list com DTOs separados do domínio.
- [x] Implementar rename e preço como comandos independentes e idempotentes.
- [x] Implementar archive lógico, irreversível e idempotente.
- [x] Bloquear rename e preço de registros arquivados.
- [x] Cobrir regras, falhas e preservação de campos em testes unitários.

Evidência: commits `5c39904`, `93ba7d5` e `dde524e`; testes de domínio e use cases verdes em cada checkpoint.

## Checkpoint 2 — persistência, migrations e concorrência

Status: `Completed`.

- [x] Criar `catalog_services` por Flyway com PK, checks e chave normalizada.
- [x] Manter `spring.jpa.hibernate.ddl-auto=validate`.
- [x] Implementar adapter JPA e mapeamento sem contaminar o domínio.
- [x] Usar lock pessimista para rename, preço, archive e elegibilidade.
- [x] Traduzir somente a constraint conhecida de nome para conflito funcional.
- [x] Migrar a unicidade para somente ativos sem editar a migration anterior.
- [x] Provar múltiplos arquivados homônimos e exatamente um ativo em H2 e MySQL.
- [x] Classificar a mudança como **no seed required**.

Migrations:

- `V20260824025720__create_catalog_services.sql`;
- `V20260824050859__allow_archived_catalog_service_name_reuse.sql`.

A segunda migration cria a constraint ativa antes de remover a global. Os testes concorrentes provaram um vencedor para
nomes ativos, ausência de lost update e serialização correta entre archive e updates.

## Checkpoint 3 — HTTP, falhas e contratos

Status: `Completed`.

- [x] Expor POST, GET por ID, GET coleção, dois PATCHes e DELETE lógico.
- [x] Retornar `Location` no cadastro e DTOs próprios em todas as responses com body.
- [x] Implementar códigos estáveis de validação, not found, duplicidade e lifecycle.
- [x] Atualizar anotações Springdoc e expectativas da OpenAPI gerada.
- [x] Consolidar as seis operações na pasta `Service Catalog` do Postman.
- [x] Validar o JSON da collection e confirmar ausência de YAML OpenAPI duplicado.

Evidência: `CatalogServiceControllerTest`, `OpenApiContractTest` e parser JSON verdes. A collection final possui
requests de cadastro, detalhe, listagem, rename, preço e archive, com variável separada para o cenário de arquivamento.

## Checkpoint 4 — integração modular com Service Lifecycle

Status: `Completed`.

- [x] Publicar `catalog-service-availability-api` como named interface de Registration.
- [x] Expor somente `ACTIVE`, `ARCHIVED` e `NOT_FOUND`, sem modelo interno do aggregate.
- [x] Criar port consumidor e adapter in-process em Service Lifecycle.
- [x] Exigir transação consumidora para manter o lock até o commit do diagnóstico.
- [x] Deduplicar e ordenar UUIDs antes dos checks.
- [x] Validar todos os serviços antes de qualquer mutação da Service Order.
- [x] Preservar snapshots e continuidade de execuções já existentes.
- [x] Verificar fronteiras com testes Modulith.

Evidência: `ModuleStructureTest`, `CatalogServiceEligibilityApplicationModuleTest`, testes de diagnóstico e quatro
cenários concorrentes passaram em H2 e MySQL 8.0.46.

## Checkpoint 5 — segurança

Status: `Completed` — nenhum achado crítico ou alto aberto.

| Item | Estado final | Evidência ou mitigação |
|---|---|---|
| Input validation | Mitigado | Bean Validation, value objects e testes negativos |
| Mass assignment | Mitigado | DTOs fechados não aceitam `id`, `active` ou campos cruzados |
| Autenticação | Resolvido depois do slice | JWT obrigatório pela regra global de `SecurityConfig` |
| Autorização por papel | Decisão atual documentada | Catálogo aceita qualquer papel autenticado |
| Exposição de dados | Revisado | Sem dados pessoais; responses limitadas ao cadastro do serviço |
| Erros e logs | Revisado | Códigos estáveis sem SQL, constraint, stack trace ou tipo interno |
| SQL e migration | Revisado | Flyway forward-only, ordem segura das constraints e `validate` |
| Concorrência e abuso | Mitigado | Locks, ordem estável, transações curtas e payloads limitados |
| Hard delete | Mitigado | Apenas UPDATE de lifecycle; ausência de delete físico verificada |
| Dependências e secrets | N/A | Nenhuma dependência, credencial ou integração externa adicionada |

A implementação original registrou JWT como dívida transversal. A PR #30 resolveu o acesso anônimo; como não há matcher
específico para `/api/catalog-services/**`, permanece permitida a qualquer papel autenticado. Alterar essa matriz requer
decisão funcional própria, testes de segurança e atualização do SDD de JWT.

## Checkpoint 6 — gates de qualidade e operação

Status: `Completed`.

| Entrega | Testes no fechamento | Cobertura global de linhas | Evidência operacional |
|---|---:|---:|---|
| `SCRUM-8` | 357 | 93,60% | Flyway, Hibernate validate e fluxo HTTP no MySQL 8.0.46 |
| `SCRUM-38` | 386 | 93,72% | PATCHes, unicidade, idempotência e snapshots no MySQL |
| `SCRUM-39` | 415 | 93,86% | Migration, archive, elegibilidade e concorrência em H2/MySQL |

- [x] Suítes focadas executadas durante cada checkpoint.
- [x] Gate Maven equivalente a `make verify` concluído sem falhas, erros ou skips inadequados.
- [x] Cobertura global permaneceu acima da meta de 80%.
- [x] `ModuleStructureTest` permaneceu verde.
- [x] OpenAPI e Postman foram sincronizados com os contratos.
- [x] MySQL 8.0.46 validou migrations, constraints, lifecycle e concorrência.
- [x] Nenhum dado pessoal real, secret ou log sensível foi introduzido.

## Checkpoint 7 — aceite, commits e integração

Status: `Completed`.

- [x] Consolidar evidências e revisão de segurança.
- [x] Obter aceite manual explícito de Ivan Pimentel.
- [x] Criar commits Conventional Commits focados por story.
- [x] Reconciliar a branch com `dev` sem reescrever histórico publicado.
- [x] Publicar a branch e integrar a PR #27 em `dev`.

Commits:

```text
5c39904 feat(registration): register and query catalog services
93ba7d5 feat(registration): update catalog service name and price
dde524e feat(registration): archive catalog services
56e6a8d chore(registration): reconcile service catalog branch with dev
77ce35d Merge pull request #27 from SystemIvan/feat/registration-service-catalog-management
```

## Rollback ou recuperação

- A migration inicial cria uma tabela independente; versões anteriores ignoram a tabela, que deve ser preservada.
- Depois da reutilização de nomes arquivados, não retornar automaticamente ao binário que pressupõe unicidade global.
- Preferir correção forward-only; nunca editar migration aplicada a uma baseline operacional.
- Não recriar a constraint global sem provar ausência de homônimos em todos os lifecycles.
- Não apagar, reativar ou renomear registros históricos automaticamente para viabilizar rollback.
- Falha depois de update confirmado exige novo comando explícito; não editar o banco manualmente como recuperação
  normal.

## Completion checklist

- [x] SDDs aprovados e checkpoints concluídos.
- [x] Service Catalog permanece unificado sob `registration`, como Customer e Vehicle.
- [x] Domínio, aplicação, persistência e contratos implementados.
- [x] Flyway e classificação de seed registradas.
- [x] OpenAPI e Postman atualizados.
- [x] Fronteiras Modulith e integração consumidora verificadas.
- [x] Revisão de segurança concluída sem achado crítico/alto aberto.
- [x] Cobertura revisada e acima de 80% nos fechamentos registrados.
- [x] Aceite humano, commits e integração em `dev` registrados.

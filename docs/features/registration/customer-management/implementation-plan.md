# Plano de Implementação: Gestão de Customers

| Campo | Valor |
|---|---|
| Feature | `customer-management` — `SCRUM-34` e `SCRUM-33` |
| Status | Implemented e aceito |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Especificação técnica | `./technical-spec.md` |
| Integração em `dev` | PR #12, merge commit `d21fd3f` |

## Estado integrado em `dev`

Os checkpoints de `SCRUM-34` e `SCRUM-33`, somados à identidade entregue em `SCRUM-6`, estão publicados em `dev`.
OpenAPI, Postman, migrations e testes acompanharam cada contrato. A proteção JWT foi incorporada depois deste plano:
os endpoints de Customer agora exigem `MANAGER` ou `ADMIN`, resolvendo a dívida transversal registrada nas revisões de
segurança originais.

## Checkpoints

- [x] Arquitetura e contratos implementados sem violação de fronteiras.
- [x] Persistência, migration e classificação de seed concluídas.
- [x] Comportamento de domínio e aplicação implementado.
- [x] Testes automatizados e gate equivalente a `make verify` aprovados.
- [x] Revisão de segurança concluída, com achados e mitigações registrados.
- [x] OpenAPI, Postman e documentação da feature atualizados.

## Evidências de verificação

- Testes focados de domínio, aplicação e persistência: 19 testes verdes no checkpoint inicial.
- `CustomerContactControllerTest`: 2 testes verdes, incluindo erro estável e ausência de persistência parcial.
- `OpenApiContractTest`: schemas de Address/update parcial e respostas `200`, `400` e `404` verificados.
- `./mvnw test`: 72 testes, zero falhas, erros ou skips, com Java 21.0.12.
- `./mvnw verify`: build, JAR e relatório JaCoCo concluídos; `ModuleStructureTest` permaneceu verde.
- Cobertura de linhas das classes alteradas: 96,67%; cobertura de branches: 78,81%.
- Cobertura global: 68,06%, abaixo da meta histórica de 80%; o déficit já pertence ao projeto e o código alterado ficou
  acima da meta de linhas.
- Flyway aplicou três migrations em H2 vazio e a migration nova em MySQL 8.0.46; Hibernate validate ficou verde.
- Collection Postman validada por parser JSON e atualizada com exemplos separados de email e Address.
- `git diff --check` e inspeção de linhas Java acima de 120 colunas passaram.
- Teste manual local: criação sem Address, updates isolados de email/Address/telefone, normalização, repetição
  idempotente, `address: null` com HTTP 400 e identidade preservada.
- MySQL confirmou `V20260817015442` com sucesso, a constraint `ck_customers_address_complete` e os valores E.164, UF e
  CEP normalizados.
- Ivan concluiu e aprovou o teste manual do `SCRUM-34` pelo Postman em 2026-08-17.

## Revisão de segurança

- Validação e mass assignment: mitigados por DTOs fechados, Bean Validation e value objects.
- Dados pessoais: somente dados fictícios de teste; nenhuma mensagem ou log novo expõe valores de contato.
- SQL/migration: mudança aditiva, sem SQL dinâmico, backfill ou alteração destrutiva.
- Erros: códigos estáveis sem stack trace, SQL ou nomes de classes internas.
- Dependências e segredos: nenhuma dependência ou credencial adicionada ao repositório.
- Autenticação/autorização: não fazia parte do slice original; atualmente `/api/customers/**` exige JWT com `MANAGER`
  ou `ADMIN`.
- Achados críticos/altos: nenhum.

## Rollback ou recuperação

Após aplicação em ambiente compartilhado, a migration é imutável. Uma falha deve ser corrigida por nova migration
forward-only. O binário anterior pode operar enquanto as colunas adicionais permanecem nullable; remover colunas ou a
constraint não faz parte do rollback automático. Em ambiente local descartável, o banco pode ser reconstruído do zero.

## SCRUM-33 — plano aprovado de arquivamento lógico

O gate documental foi concluído por aprovação explícita de Ivan em 2026-08-17. A implementação foi iniciada pelo
lifecycle de domínio, sem bloqueio de Jira, AD-017 ou outro módulo.

### Checkpoint 0 — aprovação e baseline

- [x] Ivan aprovou o pacote funcional da `SCRUM-33` em `functional-spec.md`.
- [x] Ivan aprovou `active`, DELETE `204`, queries ativas/históricas e `409 CUSTOMER_ARCHIVED`.
- [x] Seção técnica da `SCRUM-33` marcada como `Approved` com aprovador e data.
- [x] Branch confirmada em `8e682e2`, com worktree rastreado limpo antes do início da implementação.

Evidência esperada: aprovação explícita registrada nos documentos locais antes da primeira mudança de código.

### Checkpoint 1 — lifecycle no domínio e contrato aditivo

- [x] Adicionar `active` ao aggregate, com criação ativa e reconstituição explícita.
- [x] Implementar `Customer.archive()` idempotente.
- [x] Bloquear rename e contato de Customer arquivado.
- [x] Adicionar `active` ao `CustomerResponse` e ao mapper, sem expor domínio/JPA.
- [x] Cobrir criação, reconstituição, repetição do archive e mutações permitidas/proibidas em testes de domínio.

Evidência: 14 testes focados verdes em `CustomerTest` e `CustomerMapperTest` (12 de domínio e 2 do mapper), cobrindo
criação ativa, reconstituição arquivada, archive repetido, bloqueio de rename/contato e projeção aditiva de `active`.

### Checkpoint 2 — migration e queries de persistência

- [x] Criar `V20260817033647__add_customer_active.sql` com `active BOOLEAN NOT NULL DEFAULT TRUE`.
- [x] Atualizar entidade e mapper JPA para round-trip do lifecycle.
- [x] Separar `findById`, `findAllActive`, `findActiveByTaxId` e `existsByTaxId` na porta/adapter.
- [x] Garantir que nenhum adapter invoque exclusão física.
- [x] Testar migration/startup, linha preservada, filtros ativos e reserva de `TaxId` arquivado.

Classificação: nenhum seed requerido; todos os registros existentes tornam-se ativos. Evidência: 27 testes focados
verdes; Flyway aplicou quatro migrations em H2 vazio, Hibernate validate passou e o teste de persistência confirmou
round-trip, preservação da linha, filtros ativos e reserva do `TaxId` arquivado.

### Checkpoint 3 — use cases e falhas estáveis

- [x] Implementar `ArchiveCustomerUseCase` transacional e idempotente.
- [x] Alterar listagem para somente ativos e identificação por `TaxId` para lookup ativo.
- [x] Preservar `GetCustomerUseCase` como consulta histórica.
- [x] Manter unicidade de `TaxId` em todos os estados.
- [x] Mapear not found e lifecycle inválido para `409 CUSTOMER_ARCHIVED`.
- [x] Cobrir archive ativo/repetido/ausente, consultas e falhas sem save indevido.

Evidência: 14 testes focados de aplicação verdes, incluindo archive ativo/repetido/ausente, consultas histórica e
operacionais, reserva do `TaxId` arquivado e mutações proibidas sem `save` indevido.

### Checkpoint 4 — HTTP, OpenAPI e Postman

- [x] Expor `DELETE /api/customers/{id}` com `204 No Content` e `404` para ID inexistente.
- [x] Demonstrar idempotência repetindo o DELETE.
- [x] Documentar `active` nas respostas e as semânticas de GET/list/identify.
- [x] Atualizar Springdoc, `OpenApiContractTest` e a collection Postman no mesmo commit.
- [x] Cobrir DELETE, lookup histórico, filtros operacionais e conflito de lifecycle via MockMvc.

Evidência: 9 testes HTTP/contrato verdes; DELETE `204` repetido e `404` para ID ausente, GET histórico, filtros
operacionais, reserva do `TaxId`, conflitos `CUSTOMER_ARCHIVED`, schema `active` e operação OpenAPI verificados. A
collection Postman atualizada foi validada por parser JSON.

### Checkpoint 5 — segurança, gates e validação operacional

- [x] Revisar exposição histórica de dados, autorização futura, mass assignment, logs e códigos de erro.
- [x] Confirmar ausência de hard delete e de imports internos entre módulos.
- [x] Executar testes focados, `test` e `verify` com o Maven 3.9.16 do wrapper e Java 21.
- [x] Revisar JaCoCo sem reduzir cobertura do código alterado e manter `ModuleStructureTest` verde.
- [x] Validar no MySQL: mesma linha/ID/TaxId, `active=0`, GET histórico e ausência em seleção ativa.
- [x] Atualizar evidências, marcar a story implementada e criar o commit local convencional `027cc75`.

Evidência: 86 testes verdes em `test` e `verify`, JAR/JaCoCo gerados e dois testes de `ModuleStructureTest` verdes.
As classes alteradas atingiram 98,86% de linhas e 87,5% de branches; o projeto ficou em 68,99% de linhas, ainda abaixo
da meta histórica de 80% por déficit preexistente. O launcher híbrido `mvnw.cmd` falhou no PowerShell ao indexar
`Target[0]`; os mesmos comandos foram executados pelo Maven 3.9.16 provisionado pelo próprio wrapper, sem Maven global.

No MySQL 8.0.46, o registro fictício `ca0416e2-86da-4eaa-b27e-d4a9262f51e6` manteve a mesma linha e o mesmo `TaxId`
`95309807608` com `active=0`; dois DELETEs retornaram `204`, GET histórico retornou o registro arquivado, identify
retornou `404` e a listagem ativa não incluiu o ID. A migration `20260817033647` consta com `success=1`.

Commit local: `027cc75 feat(registration): archive customers logically`. Nenhum push, PR, Jira ou Miro foi alterado.
Ivan confirmou a conclusão da validação manual e aceitou a `SCRUM-33` em 2026-08-17.

### Revisão de segurança concluída

- Validação e mass assignment: DELETE não aceita body; os demais contratos continuam fechados e não permitem alterar
  `active`, ID ou `TaxId` diretamente.
- Autenticação/autorização: N/A no slice original. A entrega transversal posterior passou a proteger o comando e o GET
  histórico administrativo antes da entrega geral.
- Exposição histórica: GET por ID deliberadamente retorna dados de contato de arquivados; nenhuma listagem histórica foi
  adicionada, reduzindo enumeração acidental.
- Enumeração: identificação de arquivado usa o mesmo `404 CUSTOMER_NOT_FOUND` de uma ausência operacional.
- Persistência: migration aditiva, sem deleção ou backfill destrutivo; a constraint de unicidade continua reservando o
  `TaxId` em todos os estados.
- Erros e logs: `CUSTOMER_ARCHIVED` não inclui nome, documento ou contato; nenhum log novo registra dados pessoais, SQL
  ou tipos internos.
- Dependências e segredos: nenhuma dependência, credencial ou segredo foi adicionado ao repositório.
- Fronteiras: nenhum import interno entre módulos foi introduzido e nenhum adapter Customer executa hard delete.
- Achados críticos/altos: nenhum.

### Rollback ou recuperação da SCRUM-33

Antes de baseline compartilhada, ambiente local pode ser reconstruído. Depois da aplicação compartilhada, a migration é
imutável: correções usam nova migration forward-only. O binário anterior ignora a coluna adicional; rollback do binário
não deve reativar registros nem remover `active`. Uma reversão funcional de arquivamento não está autorizada nesta
story.

## Follow-up não bloqueante

- Validar UUID textual estritamente em 36 caracteres numa mudança transversal futura. O comportamento atual do parser
  Java e a decisão de adiá-lo foram documentados; não incluir essa correção no commit do `SCRUM-33`.

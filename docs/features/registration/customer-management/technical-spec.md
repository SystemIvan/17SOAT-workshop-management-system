# Especificação Técnica: Gestão de Customers

| Campo | Valor |
|---|---|
| Feature | `customer-management` — `SCRUM-6`, `SCRUM-34` e `SCRUM-33` |
| Status | Implemented e aceito |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovado por | Ivan Pimentel (`SCRUM-34` e `SCRUM-33`) |
| Aprovado em | `SCRUM-34`: 2026-08-16; `SCRUM-33`: 2026-08-17 |
| Especificação funcional | `./functional-spec.md` |
| Integração em `dev` | PR #12, merge commit `d21fd3f` |

## Estado técnico integrado

O slice completo está integrado em `dev`: `Customer` usa `TaxId`, `ContactInfo`, Address opcional e lifecycle lógico;
Flyway mantém o schema sob `ddl-auto=validate`; OpenAPI e Postman cobrem os contratos de cadastro, identificação,
consulta, listagem, rename, contato e archive. A segurança transversal, integrada depois destas stories, protege
`/api/customers/**` com JWT e os papéis `MANAGER` ou `ADMIN`.

## Contexto e desenho

A mudança permanece integralmente em `registration.customer` e não cria dependências com outros módulos. O aggregate
`Customer` continua sendo a fronteira de consistência. `Email`, `Phone` e `Address` são value objects imutáveis e livres
de Spring, JPA ou detalhes HTTP. `ContactInfo` reúne esses valores e aplica atualização parcial por método de negócio.

`Address` é opcional no Customer, mas atômico quando presente. `street`, `number`, `city`, `state` e `postalCode` são
obrigatórios; `complement` e `neighborhood` são opcionais. A UF é normalizada para uppercase e o CEP para oito dígitos.
Telefone brasileiro com 10 ou 11 dígitos recebe `+55`; telefone internacional deve usar E.164 e todos são armazenados e
retornados na forma canônica.

## Interfaces e fluxo de dados

- `POST /api/customers` aceita `contactInfo.address` opcional e a resposta passa a incluir `address`, com `null` quando
  ausente.
- `PATCH /api/customers/{id}/contact-info` mantém o wrapper `contactInfo` e aceita qualquer subconjunto de `email`,
  `phone` e `address`.
- Campo omitido é representado internamente por `Optional.empty()` e preserva o valor atual.
- `null` explícito, tipo JSON incorreto ou comando sem campos são rejeitados com `400 VALIDATION_ERROR`.
- Invariantes de domínio inválidas usam o erro existente `400 INVALID_CUSTOMER`; ID inexistente retorna
  `404 CUSTOMER_NOT_FOUND`.
- `address` presente substitui o value object completo. Remoção de endereço não pertence a esta story.
- O use case constrói todos os value objects antes de alterar o aggregate, evitando persistência parcial quando algum
  valor for inválido.
- Campos externos à request, inclusive `document`, não são mapeados para o comando e não alteram a identidade.

Um desserializador Jackson 3 específico diferencia campo omitido de `null` explícito sem transformar DTOs em objetos
mutáveis. Requests e responses continuam records separados do domínio e das entidades JPA.

## Persistência e dados de bootstrap

A migração `V20260817015442__add_customer_address.sql` adiciona sete colunas nullable em `customers`: `address_street`,
`address_number`, `address_complement`, `address_neighborhood`, `address_city`, `address_state` e
`address_postal_code`. A constraint `ck_customers_address_complete` permite todas as colunas nulas ou exige as cinco
partes obrigatórias, impedindo Address parcial no banco.

Classificação: **nenhum seed requerido**. Clientes existentes permanecem com Address ausente; não há backfill nem dado
de referência obrigatório. A migração é aditiva e Hibernate permanece com `ddl-auto=validate`.

## Segurança e operação

- Bean Validation limita formato e tamanho na borda; o domínio reaplica as invariantes relevantes.
- O DTO fechado evita mass assignment de ID, nome e `TaxId`.
- Mensagens de falha não ecoam email, telefone, endereço, SQL, constraint ou tipo interno.
- Não foram adicionadas dependências, credenciais, logs de dados pessoais ou integrações externas.
- Na entrega original, autenticação e autorização eram responsabilidade de outro slice. No estado integrado em `dev`,
  JWT é obrigatório e `/api/customers/**` aceita somente `MANAGER` ou `ADMIN`.
- Rollout exige aplicar Flyway antes de validar o novo mapeamento JPA. As colunas opcionais preservam compatibilidade
  com linhas existentes.

## Estratégia de testes

- domínio: normalização e rejeição de Email, Phone e Address; preservação de omitidos; PATCH vazio;
- aplicação: updates individuais, identidade imutável, idempotência, falhas e ausência de save parcial;
- persistência: round-trip com Address, update parcial, Flyway em banco vazio e Hibernate validate;
- HTTP: create com/sem Address, updates parciais, `null`, inválidos, not found e erros estáveis;
- contrato: schemas e respostas OpenAPI, além de exemplos parciais na collection Postman;
- estrutura: `ModuleStructureTest` e suíte completa do projeto;
- operação: fluxo manual em MySQL 8.0.46 com inspeção da migration, constraint e valores normalizados.

## SCRUM-33 — arquivamento lógico (Implemented)

Esta seção transforma os critérios atuais do Jira no desenho técnico aprovado por Ivan em 2026-08-17. Ela não altera a
especificação implementada do `SCRUM-34` e foi implementada após a conclusão dos gates documentais.

### Impacto de contexto e domínio

- Somente `registration.customer` é alterado; nenhuma dependência entre módulos é criada.
- `Customer` recebe estado booleano `active`, iniciado como `true` em `create` e restaurado pela persistência.
- `Customer.archive()` é o único comando de lifecycle e define `active=false` de forma idempotente.
- `rename` e `updateContactInfo` chamam uma guarda de Customer ativo e lançam
  `CustomerArchivedException` com mensagem em português quando a mutação não for permitida.
- Reativação, data/motivo/ator do arquivamento e audit trail permanecem fora do escopo.

O booleano segue o padrão já aplicado em Stock Item e evita criar um enum de dois estados sem comportamento adicional.
Se estados futuros forem aprovados, uma migration e revisão de contrato próprias serão necessárias.

### Porta de repositório e semântica de consultas

A porta de domínio permanece sem `delete` e passa a expor intenções distintas:

- `findById(UUID)`: consulta histórica, encontra Customer ativo ou arquivado;
- `findActiveByTaxId(TaxId)`: identificação operacional, encontra somente ativo;
- `findAllActive()`: listagem/seleção padrão, retorna somente ativos;
- `existsByTaxId(TaxId)`: consulta todos os estados e mantém o documento reservado;
- `save(Customer)`: persiste criação, alterações e arquivamento.

O adapter JPA implementa os filtros em query, sem carregar todos os Customers para filtrar em memória. Nenhum adapter
chama `delete`, `deleteById` ou SQL de remoção. A seleção de Customer ativo por Service Order continua fora deste módulo
e depende do futuro contrato consumidor de Service Lifecycle.

### Aplicação e transações

- `ArchiveCustomerUseCase.execute(UUID)` é transacional, usa a consulta histórica, chama `archive()` e salva o
  aggregate.
- `ListCustomersUseCase` usa `findAllActive()`.
- `IdentifyCustomerByTaxIdUseCase` usa `findActiveByTaxId()` e trata arquivado como não elegível/não encontrado.
- `GetCustomerUseCase` continua histórico e retorna ambos os estados.
- `CreateCustomerUseCase` continua verificando `existsByTaxId()` em todos os estados.
- Rename e atualização de contato de arquivado retornam conflito de lifecycle.

### Contratos HTTP aprovados

- `DELETE /api/customers/{id}` arquiva logicamente e retorna `204 No Content`.
- Repetir o mesmo DELETE retorna `204`, mantendo o mesmo estado final.
- ID inexistente no DELETE retorna `404 CUSTOMER_NOT_FOUND`.
- `GET /api/customers/{id}` retorna Customer ativo ou arquivado.
- `GET /api/customers` e `GET /api/customers/identify` retornam somente ativos.
- `CustomerResponse` recebe o campo aditivo `active: boolean`; Customer arquivado é observável no lookup histórico.
- Rename ou contato de arquivado retornam `409 CUSTOMER_ARCHIVED`.

O uso de DELETE representa a intenção CRUD de remoção sem prometer exclusão física e replica o contrato de Stock Item.
OpenAPI, `OpenApiContractTest` e a collection Postman devem ser atualizados no mesmo commit.

### Persistência e classificação de dados

Uma nova migration imutável, criada no momento da implementação como
`V<timestamp_utc>__add_customer_active.sql`, adicionará `active BOOLEAN NOT NULL DEFAULT TRUE` em `customers`.

- Classificação de seed: **nenhum seed requerido**.
- Tratamento dos dados existentes: todos os Customers atuais são classificados como ativos, pois não existe estado
  arquivado na baseline.
- Não há remoção, cópia ou reescrita de `TaxId`, contato ou Address.
- Hibernate continua em `ddl-auto=validate`; a referência antiga do Jira a AD-017 não substitui a política Flyway atual.

O binário anterior tolera a coluna adicional. Depois que a migration entrar em baseline compartilhada, qualquer correção
deve usar nova migration forward-only.

### Falhas e segurança aprovadas

- Customer ausente: `404 CUSTOMER_NOT_FOUND`.
- Customer arquivado em identificação operacional: mesmo `404`, sem revelar elegibilidade para novo trabalho.
- Customer arquivado em mutação proibida: `409 CUSTOMER_ARCHIVED` com mensagem sem dados pessoais.
- O endpoint nunca retorna entidade JPA nem classe de domínio e não expõe SQL, constraint ou stack trace.
- O archive não aceita body, reduzindo mass assignment e entradas desnecessárias.
- Autenticação/autorização administrativa foi entregue posteriormente pelo slice transversal de JWT; o matcher atual
  restringe `/api/customers/**` a `MANAGER` e `ADMIN`.
- Nenhuma verificação hard-delete ou importação interna de outro módulo será adicionada.

### Estratégia de testes aprovada

- domínio: Customer nasce ativo, `archive()` é idempotente e mutações de arquivado são bloqueadas;
- aplicação: archive ativo/repetido/not found; listagem e identificação somente de ativos; GET histórico;
- persistência: migration/startup, round-trip de `active`, linha preservada, queries ativas e unicidade do `TaxId`
  arquivado;
- HTTP: DELETE `204`, repetição, not found, resposta histórica e conflito de lifecycle;
- contrato: OpenAPI e Postman para DELETE e campo `active`;
- estrutura: `ModuleStructureTest`, `./mvnw test` e `./mvnw verify`;
- operação: teste manual no MySQL comprovando `active=0` com a mesma linha e o mesmo `TaxId`.

### Gate técnico da SCRUM-33

- [x] Ivan aprovou `active: boolean` como representação aditiva de lifecycle.
- [x] Ivan aprovou `DELETE /api/customers/{id}` com `204` idempotente.
- [x] Ivan aprovou `409 CUSTOMER_ARCHIVED` para rename/contato de arquivado.
- [x] Ivan aprovou as semânticas histórica e operacional da porta de repositório.
- [x] Plano revisado e liberado para implementação após a aprovação técnica.

## Dívida técnica não bloqueante: UUID textual estrito

O Spring usa o parser de UUID do Java, que aceita algumas formas com grupos encurtados e as converte em outro UUID.
Por isso, um path com 35 caracteres pode chegar ao use case e resultar em `CUSTOMER_NOT_FOUND`. A validação do body
antes da consulta de existência permanece correta. Ivan decidiu adiar uma validação global da forma canônica de 36
caracteres porque ela não é necessária para o MVP atual. Essa correção deve ser transversal e feita em commit próprio.

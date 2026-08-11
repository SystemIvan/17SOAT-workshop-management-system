# PR: Alinhamento de contextos e padrões do projeto

## Resumo

Esta PR reorganiza o monólito modular para refletir o Context Map do Miro e cria uma base de desenvolvimento mais
previsível para as próximas features.

Os módulos diretos da aplicação passam a ser:

- `registration`: Customer, Vehicle e Service Catalog;
- `servicelifecycle`: Service Order, Estimate e Technician;
- `stockprocurement`: Stock e Purchase Order.

Os recursos ainda não implementados foram mantidos apenas como placeholders documentados. Não foi criado CRUD, tabela ou
regra de negócio especulativa para eles.

## Mudanças realizadas

- Movidos os pacotes existentes para seus bounded contexts e reforçada a verificação do Spring Modulith.
- Adicionado Flyway como responsável pelo schema, com migração inicial e Hibernate em modo `validate`.
- Criados seeds idempotentes de Customer e Stock, disponíveis somente com perfil `dev` e
  `app.seed.enabled=true`.
- Adicionados Swagger/OpenAPI em `/swagger-ui.html` e `/v3/api-docs`, com teste que assegura a presença dos endpoints.
- Criada collection Postman em `docs/api/postman/`.
- Criados templates de specification por feature, plano de implementação com checkpoints e regras atualizadas no
  `AGENTS.md`.
- Criado Makefile e atualizadas as instruções de Docker, ambiente e README.
- Documentada a decisão de manter Notifications como capacidade de saída, e não como bounded context independente.

## Compatibilidade e impacto

Os endpoints e DTOs existentes foram preservados. A mudança de schema exige recriar bancos locais que antes eram geridos
por `ddl-auto=update`; use `make docker-reset` apenas para o ambiente local quando necessário.

## Validação executada

- [x] `./mvnw verify` executado com 51 testes, 0 falhas e 0 erros.
- [x] Fronteiras Modulith verificadas para os três módulos esperados.
- [x] Migração Flyway aplicada e validada com Hibernate no banco de teste H2 em modo MySQL.
- [x] Contrato OpenAPI coberto por teste MockMvc.
- [x] Collection Postman validada como JSON.
- [x] `git diff --check` sem problemas de whitespace.

## Segurança e pontos de atenção

- Seeds usam somente dados sintéticos e nunca são carregados fora do perfil de desenvolvimento.
- Não foram adicionados segredos nem novos contratos HTTP de negócio.
- Autenticação/autorização ainda não existe na aplicação; é um risco pré-existente e deve ser tratado em uma feature
  dedicada antes de uso produtivo.
- A cobertura atual de linhas é 48,37%, abaixo da meta de 80% do projeto. Esta PR não reduz a cobertura e acrescenta
  testes de estrutura, migração, OpenAPI e seeds.

## Resumo para WhatsApp

Pessoal, alinhamos a estrutura do projeto com o desenho DDD do Miro. Agora temos três contextos claros:
Registration (cliente/veículo/catálogo), Service Lifecycle (ordem de serviço/orçamento/técnico) e Stock & Procurement
(estoque/compras).

Também deixamos a base mais organizada para as próximas features: banco agora é versionado com Flyway, temos dados de
exemplo só no ambiente de desenvolvimento, Swagger e collection do Postman para a API, Makefile para os comandos mais
comuns e um fluxo padrão de specs + plano + validações.

Tudo passou nos testes (51 verdes). Como próximos débitos técnicos, precisamos evoluir a cobertura até 80% e implementar
autenticação/autorização antes de pensar em produção.

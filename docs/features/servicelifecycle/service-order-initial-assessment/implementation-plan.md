# Plano de Implementação: Registrar triagem inicial da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-initial-assessment` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Branch | `feat/servicelifecycle-service-order-intake-diagnosis-status` |
| Plano agregado | `../service-order-intake-diagnosis-status/implementation-plan.md` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-22) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-22) |

## Objetivo e ordem

Adicionar `initialAssessment` obrigatório para novas Service Orders, persistir o texto sem interpretação e expô-lo
nos contratos detalhados. Registros legados permanecem com valor nulo, sem backfill inventado.

Esta é a primeira das quatro features coordenadas da RFC-002. Cada checkpoint deve ser concluído e receber evidência
antes do próximo. Mudança material nas specs aprovadas interrompe o código e reabre o gate SDD correspondente.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Reconciliar documentação afetada | Completed |
| 1 | Implementar domínio, aplicação e DTOs | Completed |
| 2 | Implementar migration e persistência | Completed |
| 3 | Atualizar HTTP, OpenAPI e Postman | Completed |
| 4 | Completar testes automatizados | Completed |
| 5 | Concluir segurança e gates finais | Completed |

## Checkpoint 0 — Reconciliar documentação afetada

- Acrescentar ao SDD de `service-order-creation` uma referência explícita ao delta aprovado nesta feature.
- Atualizar sua descrição de request/response sem duplicar a especificação de `initialAssessment`.
- Se a alteração materializar regra nova dentro da spec anterior, devolvê-la a `Draft`, invalidar seus documentos
  downstream e obter as aprovações exigidas antes de alterar código.
- Registrar que o request incompatível foi aprovado e incluir a mudança nas notas de rollout.

Evidência: links, status e aprovações dos documentos reconciliados.

## Checkpoint 1 — Implementar domínio, aplicação e DTOs

- Adicionar `initialAssessment` ao factory, estado, reconstituição e accessor de `ServiceOrder`.
- Rejeitar nulo ou `String.isBlank()` em novas criações e não criar método de atualização.
- Ampliar `CreateServiceOrderRequest`, `ServiceOrderResponse`, `CreateServiceOrderUseCase` e `ServiceOrderMapper`.
- Traduzir falhas de argumento de Service Lifecycle para `400 VALIDATION_ERROR`, sem reutilizar
  `INVALID_STOCK_ITEM`.
- Atualizar builders e fixtures de Service Order sem flexibilizar o campo em novas criações.

Evidência: arquivos alterados e testes rápidos de domínio/aplicação executados.

## Checkpoint 2 — Implementar migration e persistência

- Gerar timestamp UTC no início do checkpoint e criar migration
  `VyyyyMMddHHmmss__add_initial_assessment_to_service_orders.sql`.
- Adicionar `initial_assessment TEXT NULL`; não alterar migrations existentes nem preencher dados legados.
- Atualizar `ServiceOrderJpaEntity` e `ServiceOrderPersistenceMapper` nos dois sentidos.
- Validar upgrade, round-trip de texto, leitura de nulo legado e startup com `ddl-auto=validate`.
- Classificação: **no seed required**; nenhum seeder ou fixture de desenvolvimento será criado.

Evidência: nome da migration e resultados dos testes de persistência/startup.

## Checkpoint 3 — Atualizar HTTP, OpenAPI e Postman

- Exigir `initialAssessment` em `POST /api/service-orders` e documentar `201`/`400`.
- Expor o campo em `ServiceOrderResponse`, anulável apenas para legado.
- Atualizar `OpenApiContractTest` e a coleção Postman com um exemplo sem dados pessoais reais.
- Confirmar que entidades de domínio/JPA não atravessam o boundary HTTP.

Evidência: assertions do contrato gerado e diff validado da coleção Postman.

## Checkpoint 4 — Completar testes automatizados

- Cobrir domínio, use case, MockMvc, mapper e persistência conforme a spec técnica.
- Incluir erros para campo ausente, nulo, vazio e composto somente por espaços.
- Executar testes focados durante o checkpoint e `make test` ao concluí-lo.
- Executar `make coverage` e confirmar que a cobertura do código alterado não diminuiu.

Evidência: comandos, quantidade de testes e resultados.

## Checkpoint 5 — Concluir segurança e gates finais

### Revisão de segurança planejada

- **Entrada/mass assignment:** DTO dedicado e `@NotBlank`; verificar que somente o campo explícito chega ao aggregate.
- **Autenticação/autorização:** lacuna pré-existente; nenhum controle novo aprovado. Registrar como risco de plataforma.
- **Exposição:** triagem pode conter dados pessoais; não registrar payload ou texto em logs/erros/notificações.
- **Injeção/renderização:** texto permanece opaco em JSON; documentar escaping obrigatório para clientes HTML.
- **SQL/migration:** coluna aditiva e anulável; sem backfill e sem alteração destrutiva.
- **Erros/disclosure:** resposta estável sem stack trace, SQL ou tipo interno.
- **Dependências:** N/A — nenhuma dependência nova planejada.
- **Abuso:** revisar limites globais de payload; não inventar limite funcional não aprovado.

Não há achado crítico/alto identificado no desenho. Reavaliar com o código pronto e registrar mitigação ou `N/A`.

- Executar `make verify` e confirmar `ModuleStructureTest` verde.
- Atualizar README/arquitetura apenas se o código revelar mudança estrutural.
- Marcar a feature `Implemented` somente após todos os checkpoints e evidências.

## Evidências de verificação

- 2026-08-22 — A reconciliação do plano agregado atualizou `service-order-creation` com referência ao delta,
  `initialAssessment` no request/response e a classificação **material**. A funcional anterior voltou a `Draft`;
  a técnica e o plano históricos ficaram `Stale`. A incompatibilidade de request permanece registrada na spec desta
  feature. Reaprovação funcional e técnica registrada para Matheus Apostulo em 2026-08-22; checkpoint concluído.
- 2026-08-22 — Checkpoint 1: `ServiceOrder` passou a exigir `initialAssessment` apenas na criação, mantendo a
  reconstituição de legado anulável; DTOs, use case, mapper e tratamento `VALIDATION_ERROR` foram atualizados.
  `./mvnw -q test -Dtest=ServiceOrderTest,CreateServiceOrderUseCaseTest` passou.
- 2026-08-22 — Checkpoint 2: criada a migration UTC
  `V20260822164906__add_initial_assessment_to_service_orders.sql`, com `initial_assessment TEXT NULL` e sem seed.
  `./mvnw -q test -Dtest=ServiceOrderRepositoryImplTest` passou, inclusive com round-trip e leitura de legado nulo;
  Flyway e a validação Hibernate iniciaram corretamente.
- 2026-08-22 — Checkpoint 3: `POST /api/service-orders`, OpenAPI e a coleção Postman passaram a exigir/documentar
  `initialAssessment`; `./mvnw -q test -Dtest=ServiceOrderControllerCreateTest,OpenApiContractTest` passou e a
  coleção Postman foi validada como JSON.
- 2026-08-22 — Checkpoint 4: `make test` e `make coverage` passaram sem falhas nos relatórios Surefire.
  O relatório JaCoCo registrou 93,54% de cobertura de linhas (2.287 cobertas de 2.445).
- 2026-08-22 — Checkpoint 5: revisão de segurança concluída sem achado crítico ou alto. Entrada é limitada pelo
  DTO com `@NotBlank` e pela invariável repetida no aggregate; o texto não é registrado em logs nem retornado em
  erros. A migration é aditiva, anulável e validada pelo Flyway. Nenhuma dependência nova foi incluída. Autenticação,
  autorização e limite global de payload são riscos de plataforma pré-existentes, fora do escopo aprovado desta
  feature; clientes HTML devem fazer escaping do texto opaco retornado em JSON. Não houve alteração estrutural que
  exigisse README/arquitetura. `make verify` passou, com `ModuleStructureTest` verde e sem falhas nos relatórios
  Surefire.

## Rollback ou recuperação

Reverter a aplicação restaura o contrato anterior, mas torna clientes que já enviam o campo apenas supercompatíveis.
A coluna deve permanecer no banco para evitar perda de triagens registradas. Uma remoção futura exige migration própria.

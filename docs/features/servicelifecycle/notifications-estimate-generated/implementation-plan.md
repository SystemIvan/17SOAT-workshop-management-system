# Plano de Implementação: Notificação de Estimate Gerada (Customer)

| Campo | Valor |
|---|---|
| Feature | `notifications-estimate-generated` |
| Status | In Progress |
| Responsável | `Leandro Nascimento` |
| Atualizado em | `2026-08-17` |
| Especificação técnica | `./technical-spec.md` |

## Checkpoints

- [x] Criar o pacote `servicelifecycle.estimate.notification` com o evento mock `EstimateGeneratedEvent`
      (`.event`), o port `CustomerEstimateNotificationPort` (`.application.port`), o listener
      `EstimateGeneratedNotificationListener` anotado `@ApplicationModuleListener` (`.application`) e o adapter
      `SimulatedEmailCustomerEstimateNotificationAdapter` (`.infrastructure`), conforme `technical-spec.md`. Exigiu
      adicionar `spring-modulith-events-api` como dependência direta no `pom.xml` — não vinha transitiva do
      `starter-core` como a `technical-spec.md` original presumia; a spec foi corrigida durante a implementação.
- [x] Confirmar/adicionar a exposição `@org.springframework.modulith.NamedInterface` (`customer-repository`,
      `customer-model`) em `registration.customer.domain.{repository,model}` — `notifications-so-finalized` ainda
      não estava mergeada em `dev`, então esta feature adicionou os dois `package-info.java` do zero.
- [x] Rodar `ModuleStructureTest` (`ApplicationModules.verify()`) e confirmar que a nova dependência
      `servicelifecycle → registration.customer` não introduz ciclo nem viola fronteira.
- [x] Implementar `EstimateGeneratedNotificationListenerTest` (unitário, Mockito) cobrindo a invocação do port
      com os 4 campos esperados e a não propagação de exceção lançada pelo port. Implementar também
      `EstimateGeneratedNotificationApplicationModuleTest` (`@ApplicationModuleTest` + `@EnableScenarios`,
      `Scenario.publish(...)`) provando que o evento publicado via `ApplicationEventPublisher` real chega ao
      listener através do `@ApplicationModuleListener` — não apenas a lógica de negócio do listener isolada.
- [x] Implementar `SimulatedEmailCustomerEstimateNotificationAdapterTest`: cliente encontrado (log `INFO`, sem
      e-mail/nome em claro, `expiresAt` presente no log) e cliente não encontrado (log `WARN`, sem exceção
      lançada).
- [x] Persistência, migrations e classificação de seeds concluídas — classificação "nenhum seed necessário"
      (nenhuma tabela/coluna nova); nenhuma migration Flyway criada.
- [x] `make verify` aprovado; cobertura do código novo/alterado ≥80% (`make coverage`).
- [x] Revisão de segurança registrada com os achados já identificados em `technical-spec.md`: mascaramento de
      e-mail confirmado por teste (log nunca contém e-mail/nome em claro), ausência de registro de publicação de
      eventos (sem retry/garantia de entrega — risco aceito).
- [ ] Confirmar com o Matheus Campagnone, antes desta feature ser considerada integrável, que o use case de
      geração de Estimate publicará o evento via `ApplicationEventPublisher` — registrar a confirmação (ou o
      ajuste de design necessário, se a resposta for diferente) neste documento antes de marcar a feature
      `Implemented`. **Em aberto** — é o único checkpoint pendente; a feature fica em `In Progress` até essa
      confirmação, mesmo com todo o código implementado e testado.
- [x] OpenAPI, Postman e documentação do projeto — N/A (nenhum endpoint HTTP novo ou alterado).

## Evidências de verificação

- `./mvnw -o test -Dtest=SimulatedEmailCustomerEstimateNotificationAdapterTest` → 2/2 verde.
- `./mvnw -o test -Dtest=EstimateGeneratedNotificationListenerTest` → 2/2 verde.
- `./mvnw -o test -Dtest=EstimateGeneratedNotificationApplicationModuleTest` → 1/1 verde (evento real publicado via
  `ApplicationEventPublisher`, capturado pelo `@ApplicationModuleListener`, port invocado).
- `./mvnw -o test -Dtest=ModuleStructureTest` → 2/2 verde (`ApplicationModules.verify()` aceita
  `servicelifecycle → registration.customer`).
- `./mvnw -o clean verify` → `BUILD SUCCESS`, 53/53 testes no projeto inteiro, sem falhas.
- `make coverage` (JaCoCo): pacote `...estimate.notification.application` 100% instruções;
  `...estimate.notification.event` 100%; `...estimate.notification.infrastructure` 96% instruções / 50% branches
  (os 2 ramos não cobertos são defensivos em `maskEmail` — e-mail sem `@` ou domínio vazio — inalcançáveis a
  partir de um `Customer` real, já que `ContactInfo` valida `email.contains("@")` na criação; mesmo padrão
  herdado do adapter da story #7). Todos acima da meta de 80% do projeto.

## Rollback ou recuperação

Mudança puramente aditiva, sem migration Flyway e sem estado persistido novo (`technical-spec.md`, Persistência
e dados de bootstrap). Rollback é um `git revert` do commit/PR — não há dado externo ou schema para recuperar.

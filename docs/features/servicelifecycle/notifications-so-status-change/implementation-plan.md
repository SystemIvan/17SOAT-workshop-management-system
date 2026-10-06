# Plano de Implementação: Notificar o cliente por e-mail a cada mudança de status nominal da OS

| Campo | Valor |
|---|---|
| Feature | `notifications-so-status-change` |
| Status | In Progress |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Especificação técnica | `./technical-spec.md` (Approved, 2026-10-05) |

## Objetivo

Enviar ao cliente um e-mail real (SMTP; Mailpit na demo) a cada mudança de status **nominal** da OS (RF39),
depois do commit e sem que uma falha de envio desfaça a transição. Consolidar o aviso de RF33 nesse fluxo
(opção (a)) e fazer o e-mail de "orçamento gerado" chegar pelo mesmo canal, com serviços, valores, total e
validade. Nenhuma mudança de schema nem de contrato HTTP.

Branch: `feat/servicelifecycle-notifications-so-status-change`. Um commit por checkpoint (Conventional Commits),
sem misturar refatoração com comportamento.

## Checkpoint 1 — Evento de domínio e detecção no aggregate ✅ (2026-10-05)

- Novo pacote `servicelifecycle.serviceorder.domain.event` com o record `ServiceOrderStatusChanged(serviceOrderId,
  customerId, previousStatus, currentStatus, occurredAt)`, com validação de não nulos, sem Spring.
- `ServiceOrder`:
  - campo `statusAtLoad`, definido em `create(...)` (`RECEIVED`) e em `reconstitute(...)` (status persistido);
  - `pullStatusChange()`: retorna `Optional<ServiceOrderStatusChanged>` quando `statusSnapshot != statusAtLoad`
    e rebaseia `statusAtLoad`.
- Verificar que nenhum use case grava a mesma OS duas vezes na mesma transação com status intermediário
  diferente. Se algum gravar, registrar aqui e tratar antes do Checkpoint 2.

Testes (`ServiceOrderTest`):
- `create` seguido de `pullStatusChange()` retorna vazio;
- reconstituída em `IN_DIAGNOSIS`, levada a `AWAITING_APPROVAL`: retorna o evento correto, e a segunda chamada
  retorna vazio;
- comando que não muda o status interno não gera evento;
- `AWAITING_ITEMS → IN_PROGRESS` gera o evento interno;
- um comando que recalcula várias vezes gera um único evento (anterior → final).

Commit: `feat(servicelifecycle): record service order status transitions in the aggregate`

## Checkpoint 2 — Publicação no repositório ✅ (2026-10-05)

- `ServiceOrderRepositoryImpl` recebe `ApplicationEventPublisher` por construtor. Depois de
  `jpaRepository.save(...)`, chama `serviceOrder.pullStatusChange().ifPresent(eventPublisher::publishEvent)`.
- A interface de domínio `ServiceOrderRepository` não muda.

Testes:
- teste do repositório (contexto JPA + H2): `save` de OS com transição publica exatamente um evento, e `save`
  sem transição não publica (captura com `@RecordApplicationEvents` ou `PublishedEvents` do Modulith);
- suíte existente continua verde, já que o construtor novo é resolvido pelo Spring.

Commit: `feat(servicelifecycle): publish ServiceOrderStatusChanged when a service order is saved`

## Checkpoint 3 — Porta, listener e consolidação do RF33 ✅ (2026-10-05)

- `CustomerNotificationPort`: troca `notifyServiceOrderFinalized(...)` por
  `notifyServiceOrderStatusChanged(UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus)`.
- Novo `ServiceOrderStatusChangedNotificationListener` (`serviceorder.application.listener`,
  `@ApplicationModuleListener`):
  - mapeia os dois status com `ServiceOrderStatusLabel.from(...)` e retorna se forem iguais;
  - chama a porta num `try/catch (RuntimeException)` e registra `WARN` só com IDs e
    `ex.getClass().getSimpleName()`.
- `FinalizeServiceOrderUseCase`: remove a dependência da porta e o `notifyCustomer`.
- `SimulatedEmailCustomerNotificationAdapter`: implementa o método novo (log com nome nominal, IDs e e-mail
  mascarado).
- Extrair a máscara para `serviceorder.infrastructure.notification.EmailMasking` (público, final) e usá-la nos dois
  adapters simulados. O comportamento da máscara não muda.

Testes:
- `ServiceOrderStatusChangedNotificationListenerTest`:
  - mudança nominal chama a porta uma vez com o label novo;
  - `AWAITING_ITEMS ↔ IN_PROGRESS` não chama;
  - exceção da porta não propaga e o log não traz dado pessoal;
- `FinalizeServiceOrderUseCaseTest` atualizado, sem testes de notificação direta;
- `SimulatedEmailCustomerNotificationAdapterTest` atualizado;
- `EmailMaskingTest`;
- `FinalizeServiceOrderFlowApplicationModuleTest` revisado para o novo fluxo.

Commits:
1. `refactor(servicelifecycle): extract e-mail masking shared by notification adapters`
2. `feat(servicelifecycle): notify customer on nominal service order status change`

## Checkpoint 4 — Canal SMTP ✅ (2026-10-05)

- `pom.xml`: `spring-boot-starter-mail`, com versão do parent.
- `application.properties`:
  - `app.notification.email.channel=${APP_NOTIFICATION_EMAIL_CHANNEL:log}`;
  - `app.notification.email.from=${APP_NOTIFICATION_EMAIL_FROM:no-reply@workshop.local}`;
  - `spring.mail.host/port/username/password` via variável de ambiente, sem padrão de senha;
  - timeouts SMTP de 5000 ms.
- `src/test/resources/application.properties`: sem `spring.mail.*`; o canal fica `log` (padrão).
- Seleção de adapters com `@ConditionalOnProperty(prefix = "app.notification.email", name = "channel", ...)`:
  - `log` com `matchIfMissing = true` nos dois adapters simulados atuais (status e orçamento);
  - `smtp` nos novos.
- `SmtpCustomerNotificationAdapter` (`serviceorder.infrastructure.notification`): `SimpleMailMessage` em texto
  puro, assunto com o ID da OS e o nome nominal legível em pt-BR, `WARN` só com IDs quando o cliente não for
  encontrado ou estiver sem e-mail. Não captura `MailException`.
- `SmtpCustomerEstimateNotificationAdapter` (`estimate.notification.infrastructure`):
  - carrega `Estimate` e `Customer`;
  - corpo com ID da OS e do orçamento, uma linha por `EstimateLine` (`serviceName` e `lineTotal()`), total,
    validade (`America/Sao_Paulo`) e valores formatados em pt-BR com 2 casas;
  - montagem do corpo num método próprio (ponto de extensão para `estimate-approval-link`);
  - sem `Reply-To` e sem instrução de resposta.
- Se ficar natural, subir o cálculo do total para `Estimate.total()` e reaproveitá-lo em
  `EstimateResponse.calculateTotal`, num commit `refactor` separado e anterior.

Testes:
- `SmtpCustomerNotificationAdapterTest` com `JavaMailSender` mockado: destinatário, assunto e corpo; cliente não
  encontrado gera `WARN` e nenhum envio; e-mail em claro ausente dos logs;
- `SmtpCustomerEstimateNotificationAdapterTest`: assunto, linhas, valores, total, validade, sem instrução de
  resposta; orçamento ou cliente ausente gera `WARN` sem envio; valores e e-mail em claro ausentes dos logs;
- teste de contexto verificando que, sem a propriedade, os beans das duas portas são os adapters simulados e que,
  com `channel=smtp` e host definido, são os SMTP.

Commits:
1. (opcional) `refactor(servicelifecycle): move estimate total calculation into the Estimate aggregate`
2. `feat(servicelifecycle): add SMTP customer notification channel selectable by property`

## Checkpoint 5 — Testes de módulo e ciclo ponta a ponta

`ServiceOrderStatusChangeNotificationModuleTest` (`@ApplicationModuleTest(DIRECT_DEPENDENCIES)`, H2 + Flyway,
`CustomerNotificationPort` e `CustomerEstimateNotificationPort` como `@MockitoBean`, `Scenario` API):
- Diagnóstico → Aguardando Aprovação via `GenerateEstimateUseCase`: uma chamada com `AGUARDANDO_APROVACAO` e uma
  chamada separada à porta de orçamento;
- aprovação via `ApplyExternalStatusUpdateUseCase` (`APPROVED`): uma chamada com `EXECUCAO`;
- `CompleteExecutionUseCase` (→ Finalizada) e `FinalizeServiceOrderUseCase` (→ Entregue): uma chamada cada;
- comando inválido (finalizar OS não `COMPLETED`): nenhuma chamada;
- porta lançando exceção: o status continua persistido (relido do repositório);
- `RestockedStockReservationRetryListener` levando `AWAITING_ITEMS → IN_PROGRESS`: evento chega ao listener e a
  porta não é chamada. O retry usa `REQUIRES_NEW`, então o commit próprio dispara o listener.

Commit: `test(servicelifecycle): cover status change notifications across the service order lifecycle`

## Checkpoint 6 — Infraestrutura da demo e documentação

- `docker-compose.yml`:
  - serviço `mailpit` (`axllent/mailpit` com tag fixa, UI `8025`, SMTP `1025`, `workshop-network`);
  - no `app`: `APP_NOTIFICATION_EMAIL_CHANNEL=smtp`, `SPRING_MAIL_HOST=mailpit`, `SPRING_MAIL_PORT=1025` e
    `depends_on: mailpit`.
- `README.md`: seção "Notificações por e-mail (RF52)" com:
  - como abrir o Mailpit;
  - roteiro da demo: criar OS, diagnóstico, gerar orçamento (2 e-mails), aprovar por canal existente (e-mail
    "Execução"), concluir (e-mail "Finalizada"), finalizar (e-mail "Entregue");
  - os e-mails esperados em cada passo e a variável para voltar ao canal `log`.
- `DOCKER.md`: serviço `mailpit` e variáveis; quais chaves vão para ConfigMap e quais vão para Secret no K8s.
- `docs/features/notifications-so-finalized/`: nota apontando a consolidação nesta feature.
- `docs/PROJECT-STRUCTURE.md`/`docs/Architecture.md`: registrar o evento `ServiceOrderStatusChanged` e o canal
  configurável, se esses documentos listarem eventos e adapters.
- OpenAPI e Postman: **N/A**. Nenhum contrato HTTP muda; `OpenApiContractTest` continua verde sem alteração.

Commits:
1. `chore(platform): add Mailpit to docker-compose for e-mail notifications demo`
2. `docs(servicelifecycle): document RF52 e-mail notifications and Mailpit demo`

## Checkpoint 7 — Verificação e revisão de segurança

- `make test`, `make verify`, `make coverage` (código novo e alterado ≥ 80%) e `ModuleStructureTest` verde.
- `make sca` (OWASP dependency-check) para a dependência nova. Achado alto ou crítico bloqueia a feature.
- Validação manual com `docker compose up`, seguindo o roteiro do README e conferindo cada e-mail no Mailpit.
- Revisão de segurança registrada abaixo.

## Revisão de segurança

| Item | Achado / mitigação | Status |
|---|---|---|
| Validação de entrada e mass assignment | N/A: nenhum endpoint ou request novo. | — |
| Autenticação e autorização | N/A: nenhum endpoint novo; quem muda status continua o mesmo. | — |
| Exposição de dados do cliente | E-mail enviado só ao e-mail cadastrado do dono da OS; evento carrega só IDs. | Pendente |
| Segredos e credenciais | Credenciais SMTP só por variável de ambiente ou Secret, sem padrão de senha, nunca logadas. | Pendente |
| Logs sensíveis | Logs só com IDs, nome nominal e e-mail mascarado; falha de envio loga só o tipo da exceção. Verificado por `ListAppender`. | Pendente |
| Injeção de cabeçalho/conteúdo | Assuntos só com UUID e texto fixo; `serviceName` só no corpo em texto puro. | Pendente |
| SQL, persistência e migração | N/A: sem schema novo; `statusAtLoad` não é persistido. | — |
| Respostas de erro | N/A: nenhuma resposta HTTP muda. | — |
| Dependências novas | `spring-boot-starter-mail`: resultado do `make sca`. | Pendente |
| Abuso | Um e-mail por transição nominal; transições exigem comandos autenticados. | Pendente |

## Evidências de verificação

### Checkpoint 1

- Verificação de gravação dupla: cada use case que altera a OS chama `ServiceOrderRepository.save` uma única vez
  por transação (`grep` em `servicelifecycle`; `ApplyExternalStatusUpdateUseCase` delega ao
  `DecideEstimateLinesUseCase`, que grava uma vez). Nenhum ajuste necessário antes do Checkpoint 2.
- `mvn test -Dtest=ServiceOrderTest,ModuleStructureTest`: `ServiceOrderTest` 30 testes (5 novos `rf52_*`) e
  `ModuleStructureTest` 2 testes, 0 falhas.
- `mvn test` (suíte completa): 768 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`.
- Observação de ambiente: `./mvnw` falha nesta máquina com `ClassNotFoundException` (`MavenCli` e
  `HttpRequestRetryHandler`), porque jars somem das distribuições extraídas em `~/.m2/wrapper/dists`, embora o zip
  baixado esteja íntegro. Os testes rodaram com a mesma versão do wrapper (Maven 3.9.16), extraída do zip original
  numa pasta temporária. O problema é da máquina, não do projeto.

### Checkpoint 2

- `ServiceOrderRepositoryImpl` recebe `ApplicationEventPublisher` por construtor e publica
  `pullStatusChange()` depois de `jpaRepository.save(...)`. A interface `ServiceOrderRepository` não mudou, e
  nenhum teste instanciava o repositório manualmente.
- Novo `ServiceOrderStatusChangePublicationTest` (`@SpringBootTest` + `@RecordApplicationEvents`, H2 + Flyway),
  com 4 testes:
  - criar a OS não publica nada;
  - uma transição publica um único evento, mesmo salvando duas vezes;
  - OS recarregada publica a transição a partir do status persistido;
  - salvar sem mudança de status não publica nada.
- `mvn test` (suíte completa): 772 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`.
  `ModuleStructureTest` verde.
- O comportamento depois do commit e com rollback (listener só roda se a transação confirmar) é coberto no
  Checkpoint 5, quando existir o listener. `@RecordApplicationEvents` registra a publicação, não a entrega.

### Checkpoint 3

- Commit de refatoração `188180f`: `EmailMasking` (público, final, em `serviceorder.infrastructure.notification`)
  substitui as duas cópias privadas de `maskEmail` nos adapters simulados (status e orçamento). O comportamento não
  mudou; o caso `null` passou a devolver `***`. `EmailMaskingTest` com 4 testes. Suíte: 776 testes, 0 falhas.
- `CustomerNotificationPort`: `notifyServiceOrderFinalized` foi substituído por
  `notifyServiceOrderStatusChanged(serviceOrderId, customerId, ServiceOrderStatusLabel)`.
- Novo `ServiceOrderStatusChangedNotificationListener` (`@ApplicationModuleListener`): ignora transições com o
  mesmo nominal e captura falhas da porta. O `WARN` traz só IDs, o nominal e o tipo da exceção, sem a mensagem nem
  o stack trace, que podem conter o endereço do destinatário.
- `FinalizeServiceOrderUseCase`: removida a dependência da porta e o `notifyCustomer`. O aviso de "Entregue" passa
  pelo fluxo genérico (opção (a)).
- `SimulatedEmailCustomerNotificationAdapter`: implementa o método novo. O `WARN` cobre cliente não encontrado ou
  sem e-mail.
- Novo `CustomerEmail` (record package-private), que resolve o e-mail do Customer e trata o caso sem e-mail. Será
  reaproveitado pelo adapter SMTP.
- Testes:
  - `ServiceOrderStatusChangedNotificationListenerTest`, 10 testes: 7 transições nominais parametrizadas,
    `AWAITING_ITEMS ↔ IN_PROGRESS` ignorados, e falha da porta sem PII no log;
  - `FinalizeServiceOrderUseCaseTest` reescrito, 4 testes, sem a porta;
  - `SimulatedEmailCustomerNotificationAdapterTest`, 2 testes;
  - `CustomerEmailTest`, 3 testes.
- `FinalizeServiceOrderFlowApplicationModuleTest` continua válido sem alteração: prova a fiação real entre os
  módulos, e o teste é `@Transactional` com rollback, então o listener não dispara. O fluxo da notificação depois do
  commit é coberto no Checkpoint 5.
- `mvn test` (suíte completa): 785 testes, 0 falhas, 0 erros, 0 ignorados, mais os 3 de `CustomerEmailTest`.
  `ModuleStructureTest` verde.

### Checkpoint 4

- Commit de refatoração `42ce40d`: `Estimate.total()` no aggregate, reaproveitado por `EstimateResponse`. O
  contrato HTTP não mudou: `OpenApiContractTest` com 19 testes verdes. `EstimateTest` ganhou o teste do total.
- `pom.xml`: `spring-boot-starter-mail` 4.1.0 (versão do parent), que traz `jakarta.mail-api` 2.1.5 e
  `angus-mail` 2.0.5 (`mvn dependency:tree`).
- `application.properties`:
  - `app.notification.email.channel` (padrão `log`) e `app.notification.email.from`;
  - timeouts SMTP de 5000 ms;
  - `spring.mail.host/port/username/password` **não** foram declarados: vêm só do ambiente, para que nenhuma
    credencial tenha padrão versionado e para que nenhum `JavaMailSender` seja criado sem host.
- `src/test/resources/application.properties`: `channel=log` e `from`. Esse arquivo substitui o principal nos
  testes; sem a propriedade, o contexto SMTP falhava com `PlaceholderResolutionException` (achado e corrigido
  durante o checkpoint).
- Adapters simulados com `@ConditionalOnProperty(channel=log, matchIfMissing=true)`. Os novos
  `SmtpCustomerNotificationAdapter` e `SmtpCustomerEstimateNotificationAdapter` usam `channel=smtp`. Com `smtp` e
  sem `spring.mail.host` não existe `JavaMailSender`, e a aplicação falha na subida em vez de descartar e-mails em
  silêncio. Esse comportamento está documentado, mas não tem teste automatizado.
- E-mail de status: texto puro, assunto `OS <id>: status atualizado para <nome legível>`, nomes RF39 em pt-BR
  mapeados localmente no adapter (as constantes do enum continuam no contrato JSON).
- E-mail de orçamento: serviços com `lineTotal()`, total (`Estimate.total()`), validade em
  `America/Sao_Paulo` ou "Validade: não definida" quando `expiresAt` é nulo, e valores em pt-BR. Orienta
  "entre em contato com a oficina" e não tem `Reply-To` nem pedido de resposta. O corpo é montado num método
  próprio (`body`), ponto de extensão para `estimate-approval-link`.
- `CustomerEmail` passou a ser público, porque o adapter de orçamento está em outro pacote do mesmo módulo.
- Achado de segurança corrigido: `EstimateGeneratedNotificationListener` logava a exceção completa. Com SMTP real,
  a mensagem pode trazer o destinatário. Agora loga só o tipo, como o listener de status. Teste novo em
  `EstimateGeneratedNotificationListenerTest`.
- Testes:
  - `SmtpCustomerNotificationAdapterTest`, 11 testes;
  - `SmtpCustomerEstimateNotificationAdapterTest`, 7 testes;
  - `EmailNotificationChannelSelectionTest`, 2 contextos: `log` → adapters simulados e nenhum `JavaMailSender`;
    `smtp` + host → adapters SMTP;
  - `EstimateGeneratedNotificationListenerTest`, com 1 teste novo.
- `mvn test` (suíte completa): 810 testes, 0 falhas, 0 erros, 0 ignorados; `ModuleStructureTest` verde.

## Rollback ou recuperação

Sem migration nem estado persistido novo. Para recuperar, há dois caminhos:
- **Operacional:** `APP_NOTIFICATION_EMAIL_CHANNEL=log` volta ao log simulado sem novo deploy de código.
- **Código:** `git revert` dos commits da feature.

E-mails perdidos por queda entre commit e envio não são recuperáveis. Essa limitação está aceita na
technical-spec.

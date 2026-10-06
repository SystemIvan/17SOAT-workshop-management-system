# Plano de Implementação: Notificar o cliente por e-mail a cada mudança de status nominal da OS

| Campo | Valor |
|---|---|
| Feature | `notifications-so-status-change` |
| Status | Draft |
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

## Checkpoint 1 — Evento de domínio e detecção no aggregate

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

## Checkpoint 2 — Publicação no repositório

- `ServiceOrderRepositoryImpl` recebe `ApplicationEventPublisher` por construtor. Depois de
  `jpaRepository.save(...)`, chama `serviceOrder.pullStatusChange().ifPresent(eventPublisher::publishEvent)`.
- A interface de domínio `ServiceOrderRepository` não muda.

Testes:
- teste do repositório (contexto JPA + H2): `save` de OS com transição publica exatamente um evento, e `save`
  sem transição não publica (captura com `@RecordApplicationEvents` ou `PublishedEvents` do Modulith);
- suíte existente continua verde, já que o construtor novo é resolvido pelo Spring.

Commit: `feat(servicelifecycle): publish ServiceOrderStatusChanged when a service order is saved`

## Checkpoint 3 — Porta, listener e consolidação do RF33

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

## Checkpoint 4 — Canal SMTP

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

A preencher durante a implementação: comandos, resultados, cobertura e prints do Mailpit.

## Rollback ou recuperação

Sem migration nem estado persistido novo. Para recuperar, há dois caminhos:
- **Operacional:** `APP_NOTIFICATION_EMAIL_CHANNEL=log` volta ao log simulado sem novo deploy de código.
- **Código:** `git revert` dos commits da feature.

E-mails perdidos por queda entre commit e envio não são recuperáveis. Essa limitação está aceita na
technical-spec.

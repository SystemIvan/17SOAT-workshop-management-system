# Especificação Técnica: Notificar o cliente por e-mail a cada mudança de status nominal da OS

| Campo | Valor |
|---|---|
| Feature | `notifications-so-status-change` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-05 |
| Especificação funcional | `./functional-spec.md` (Approved em 2026-10-05, versão revisada) |

## Contexto e desenho

**Módulo dono:** `servicelifecycle`. A mudança de status acontece no aggregate `ServiceOrder`
(`servicelifecycle.serviceorder`), e a notificação é um efeito de saída desse mesmo módulo. Conforme ADR-004,
Notifications não é bounded context: a porta `CustomerNotificationPort` continua em
`servicelifecycle.serviceorder.application.port`, e o adapter fica em `infrastructure`. A única travessia de módulo
é a leitura do Customer em `registration`, que já existe hoje via `@NamedInterface` (`customer-repository`,
`customer-model`, introduzidas por `notifications-so-finalized`). Nenhuma dependência nova entre módulos.

### Onde o status muda hoje (fato, código atual)

`statusSnapshot` só muda em `ServiceOrder.recomputeStatusSnapshot(...)`, chamado ao final de cada comando do
aggregate (AD-010). Os comandos são disparados por vários use cases, nem todos em `serviceorder`:

- `serviceorder.application.usecase`: `AssignDiagnosisAssigneeUseCase`, `PerformDiagnosisUseCase`,
  `AssignTechnicianUseCase`, `AttachStockRequirementUseCase`, `RetryStockReservationUseCase`,
  `StartExecutionUseCase`, `UpdateExecutionProgressUseCase`, `CompleteExecutionUseCase`,
  `FinalizeServiceOrderUseCase`;
- `serviceorder.application.listener.RestockedStockReservationRetryListener` (reação a reposição de estoque);
- `estimate.application.usecase`: `GenerateEstimateUseCase`, `DecideEstimateLinesUseCase`,
  `ApplyExternalStatusUpdateUseCase` (RF40).

Todos persistem pelo único caminho `ServiceOrderRepository.save(ServiceOrder)`
(`ServiceOrderRepositoryImpl`).

### Decisão de desenho 1 — detectar a transição no aggregate, publicar no `save`

Instrumentar cada use case para comparar "status antes/depois" espalharia a regra em ~13 lugares e falharia em
silêncio no próximo use case que alguém criar. Em vez disso:

1. **O aggregate registra a transição.** `ServiceOrder` passa a guardar `statusAtLoad` — o `statusSnapshot`
   com que ele foi criado (`create`, sempre `RECEIVED`) ou reconstituído (`reconstitute`). Um novo método de
   domínio `pullStatusChange()` retorna `Optional<ServiceOrderStatusChanged>` quando `statusSnapshot` difere de
   `statusAtLoad` e, em seguida, rebaseia `statusAtLoad = statusSnapshot` (o evento é entregue uma única vez).
   Como a comparação é entre o estado carregado e o estado final, um comando que recalcula o status várias
   vezes gera no máximo um evento (regra 4 da functional-spec). A criação não gera evento porque
   `statusAtLoad` nasce igual a `RECEIVED` (regra 3).
2. **O repositório publica.** `ServiceOrderRepositoryImpl.save(...)` persiste e, em seguida, chama
   `serviceOrder.pullStatusChange().ifPresent(eventPublisher::publishEvent)`. Como `save` roda dentro da
   transação do use case, o evento fica preso a ela: se houver rollback, o listener não roda (regra 8).

O evento de domínio é o **status interno** (`ServiceOrderStatus`), não o nominal: o domínio continua sem saber
do mapeamento de apresentação do RF39, que fica em `application/dto/ServiceOrderStatusLabel`. A filtragem
"mudou o nominal?" é feita pelo listener (decisão de desenho 2).

Alternativa considerada e descartada: `@DomainEvents`/`AbstractAggregateRoot` do Spring Data. Exigiria que o
modelo de domínio fosse a entidade JPA (aqui ele não é — `ServiceOrderJpaEntity` é separada) e acoplaria o
domínio ao Spring Data.

### Decisão de desenho 2 — listener `@ApplicationModuleListener` filtra no nível nominal

Novo `ServiceOrderStatusChangedNotificationListener` em `servicelifecycle.serviceorder.application.listener`,
anotado com `@ApplicationModuleListener` (mesmo padrão de `EstimateGeneratedNotificationListener`): roda de forma
assíncrona, depois do commit e em transação própria. Ele mapeia `previousStatus` e `currentStatus` com
`ServiceOrderStatusLabel.from(...)`. Se os dois nomes nominais forem iguais (ex.: `AWAITING_ITEMS` →
`IN_PROGRESS`), retorna sem notificar (regra 1). Se forem diferentes, chama
`CustomerNotificationPort.notifyServiceOrderStatusChanged(...)` dentro de `try/catch (RuntimeException)` que
apenas loga (regra 9) — mesmo motivo registrado em `notifications-estimate-generated`: evitar que a exceção caia
no handler assíncrono genérico.

### Decisão de desenho 3 — consolidação com RF33 (opção (a) da functional-spec)

`FinalizeServiceOrderUseCase` deixa de chamar `CustomerNotificationPort` diretamente e perde essa dependência. A
transição `COMPLETED` → `DELIVERED` que ele produz passa a ser notificada pelo fluxo genérico, como qualquer
outra. O método `notifyServiceOrderFinalized` sai da porta. Resultado: a duplicidade é impossível por construção
— só existe um caminho de envio para mudança de status.

### Decisão registrada — canal de envio: SMTP real + Mailpit

Decidido por Santiago Silvestre em 2026-10-05, entre:

- **(A) SMTP real + Mailpit (escolhida):** `spring-boot-starter-mail` + `JavaMailSender`, com Mailpit no
  `docker-compose` para a demo. Cumpre "o e-mail chegando" no vídeo e o canal vira configuração.
- (B) Manter o log simulado.

Para que testes e execuções sem SMTP continuem funcionando sem servidor de e-mail, a escolha do adapter é feita
por propriedade, para as **duas** portas (status e orçamento):

- `app.notification.email.channel=log` (padrão, `matchIfMissing = true`) → adapters simulados atuais
  (`SimulatedEmailCustomerNotificationAdapter`, `SimulatedEmailCustomerEstimateNotificationAdapter`);
- `app.notification.email.channel=smtp` → `SmtpCustomerNotificationAdapter` e
  `SmtpCustomerEstimateNotificationAdapter`.

Todos usam `@ConditionalOnProperty`, então sempre existe exatamente um bean de cada porta.

### Decisão de desenho 4 — e-mail de orçamento real, informativo

O gatilho não muda: `EstimateGeneratedNotificationListener` continua reagindo a `EstimateGenerated` e chamando
`CustomerEstimateNotificationPort.notifyEstimateGenerated(...)`, com a mesma assinatura. Muda o adapter:

- `SmtpCustomerEstimateNotificationAdapter`, em `servicelifecycle.estimate.notification.infrastructure`, carrega
  o `Estimate` por `EstimateRepository.findById(estimateId)` (mesmo módulo, sem travessia) para obter linhas,
  valores e total. Também carrega o `Customer` por `CustomerRepository` para obter o e-mail;
- orçamento ou cliente não encontrado gera `WARN` só com IDs, sem envio e sem exceção;
- a mensagem é texto puro (`SimpleMailMessage`), com estes campos:
  - **De** (`From`): `app.notification.email.from`.
  - **Assunto:** `"Orçamento da OS <serviceOrderId> aguardando sua aprovação"`.
  - **Corpo:**
    - ID da OS e do orçamento;
    - uma linha por `EstimateLine`, com `serviceName` e `EstimateLine.lineTotal()` (serviço + itens de
      estoque);
    - valor total, igual à soma dos `lineTotal()`. É o mesmo cálculo de `EstimateResponse.calculateTotal`; se
      couber, ele sobe para um método de domínio `Estimate.total()`, reaproveitado pelos dois;
    - validade (`expiresAt` formatado em `America/Sao_Paulo`).
- valores formatados em `BigDecimal` com 2 casas e locale `pt-BR`. Nunca `double`.
- sem `Reply-To` e sem instrução de resposta (regra 3 de "E-mail de orçamento" da functional-spec).

**Ponto de extensão para `estimate-approval-link`:** a montagem do corpo fica num método próprio do adapter, que
recebe o `Estimate`. A feature seguinte acrescenta ali o bloco de links "Aprovar"/"Recusar". Esta feature não cria
nenhuma abstração antecipada para isso (`AGENTS.md`: sem abstrações especulativas). O endpoint do RF40 e o filtro
HMAC não mudam.

`SimulatedEmailCustomerEstimateNotificationAdapter` continua existindo para o canal `log`, sem mudança de
conteúdo.

## Interfaces e fluxo de dados

**Evento de domínio** — `servicelifecycle.serviceorder.domain.event.ServiceOrderStatusChanged` (pacote novo,
mesmo padrão de `estimate.domain.event.EstimateGenerated`; record Java puro, sem Spring):

```java
public record ServiceOrderStatusChanged(
        UUID serviceOrderId,
        UUID customerId,
        ServiceOrderStatus previousStatus,
        ServiceOrderStatus currentStatus,
        Instant occurredAt) {
}
```

Carrega só IDs opacos e enums, nenhum dado pessoal.

**Aggregate** — `ServiceOrder`:

```java
private ServiceOrderStatus statusAtLoad;   // definido em create(...) e reconstitute(...)

public Optional<ServiceOrderStatusChanged> pullStatusChange() { ... }
```

`occurredAt` usa `Instant.now()`, como os outros instantes do aggregate. Nenhum outro comando muda de assinatura.

**Repositório** — `ServiceOrderRepositoryImpl` recebe `ApplicationEventPublisher` por construtor e publica depois
de `jpaRepository.save(...)`. A interface `ServiceOrderRepository` (domínio) não muda.

**Porta** — `CustomerNotificationPort`:

```java
public interface CustomerNotificationPort {
    void notifyServiceOrderStatusChanged(UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus);
}
```

`notifyServiceOrderFinalized` é removido (decisão de desenho 3).

**Adapter SMTP** (opção A) — `servicelifecycle.serviceorder.infrastructure.notification.SmtpCustomerNotificationAdapter`:

- busca o `Customer` por `CustomerRepository.findById(customerId)`;
- se não encontrar, ou se o e-mail for nulo/vazio: `WARN` com `serviceOrderId` e `customerId`, sem envio e sem
  exceção (regra 10);
- se encontrar: monta um `SimpleMailMessage` em texto puro. O remetente vem de `app.notification.email.from`, o
  destinatário é o e-mail do Customer, o assunto é `"OS <serviceOrderId>: status atualizado para <nome nominal>"`
  e o corpo traz a identificação da OS e o nome nominal legível ("Aguardando Aprovação", não
  `AGUARDANDO_APROVACAO`). Em seguida chama `JavaMailSender.send(...)` e loga um `INFO` com o e-mail mascarado e
  os IDs;
- `MailException` não é capturada aqui: ela sobe até o listener, que loga `WARN` com IDs e o tipo da exceção.

O nome legível sai de um mapeamento local do adapter (`ServiceOrderStatusLabel` → texto pt-BR). Não alteramos
o enum do RF39, que hoje é exposto em JSON pelos nomes das constantes. Mudar isso seria mudança de contrato HTTP,
fora de escopo.

A máscara de e-mail (`j***@e***`) hoje é um método privado duplicado nos dois adapters simulados e ainda seria
copiada para os dois adapters SMTP. Para não ter quatro cópias, ela vira uma classe utilitária pública e final,
`EmailMasking`, em `servicelifecycle.serviceorder.infrastructure.notification`, usada pelos quatro adapters.
`estimate` e `serviceorder` são o mesmo módulo Modulith, então não há travessia de fronteira.

**Fluxo:**

```text
Use case (@Transactional) → aggregate.comando() → recomputeStatusSnapshot
  → ServiceOrderRepository.save → publishEvent(ServiceOrderStatusChanged)   [dentro da transação]
commit ──► ServiceOrderStatusChangedNotificationListener (@ApplicationModuleListener, async)
  → nominal(previous) == nominal(current)? → fim
  → CustomerNotificationPort.notifyServiceOrderStatusChanged(...)
      → Customer não encontrado/sem e-mail → WARN só com IDs
      → JavaMailSender.send(...) → falha → WARN só com IDs (status já está persistido)
```

**Contrato HTTP:** sem mudança. Nenhum endpoint, request, response ou status code muda, então OpenAPI e Postman
não são alterados.

## Persistência e dados de bootstrap

Nenhuma mudança de schema e nenhuma migration. `statusAtLoad` é estado transitório em memória e não é mapeado em
`ServiceOrderJpaEntity`. Classificação: **nenhum seed necessário**.

**Limitação operacional (herdada de `notifications-estimate-generated`):** o projeto não usa
`spring-modulith-events-jpa`, então o registro de publicação dos eventos fica só em memória. Se a aplicação cair
entre o commit e a execução do listener, o e-mail daquela transição se perde sem retry. Isso é aceitável dentro
da regra 12 ("sem fila nem reenvio"), mas registro aqui como limitação conhecida.

**Configuração nova (opção A):**

| Propriedade | Variável de ambiente | Padrão | Observação |
|---|---|---|---|
| `app.notification.email.channel` | `APP_NOTIFICATION_EMAIL_CHANNEL` | `log` | `smtp` no docker-compose. |
| `app.notification.email.from` | `APP_NOTIFICATION_EMAIL_FROM` | `no-reply@workshop.local` | Sem dado pessoal. |
| `spring.mail.host` | `SPRING_MAIL_HOST` | — | `mailpit` no compose. ConfigMap no K8s. |
| `spring.mail.port` | `SPRING_MAIL_PORT` | — | `1025` no compose. ConfigMap no K8s. |
| `spring.mail.username` / `spring.mail.password` | `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | vazio | Mailpit não exige autenticação. Secret no K8s; nunca versionado. |
| `spring.mail.properties.mail.smtp.connectiontimeout` / `timeout` / `writetimeout` | — | `5000` ms | Evita que uma thread do listener fique presa indefinidamente com o SMTP fora do ar. |

**docker-compose:** novo serviço `mailpit` (`axllent/mailpit`, com tag fixa, mesmo critério do
`wiremock:3.13.1`) na `workshop-network`, expondo a UI na porta `8025` e o SMTP na `1025`. O `app` ganha as
variáveis acima e `depends_on: mailpit (service_started)`.

**Kubernetes:** este repositório não contém manifests K8s (verificado: nenhum `Deployment`, `ConfigMap` ou
`Secret` versionado). A spec só documenta, no README/`DOCKER.md`, quais chaves vão para ConfigMap (host, porta,
canal, remetente) e quais vão para Secret (usuário e senha). Criar manifests está fora do escopo.

## Segurança e operação

- **Autorização:** nada muda. Não há endpoint novo, e quem pode mudar o status da OS continua o mesmo.
- **Dados pessoais em log (regra 11):** o evento só carrega IDs. Os logs do listener e dos adapters contêm apenas
  `serviceOrderId`, `customerId`, o nome nominal e, no `INFO` de envio, o e-mail mascarado. No `WARN` de falha
  de envio, logar `ex.getClass().getSimpleName()` em vez da exceção completa, porque mensagens de
  `MailSendException`/`SMTPAddressFailedException` podem trazer o endereço do destinatário em claro. O teste do
  adapter vai verificar isso com `ListAppender`.
- **Dependência nova (opção A):** `org.springframework.boot:spring-boot-starter-mail`, com versão gerenciada pelo
  parent Spring Boot 4.1.0 (traz Jakarta Mail/Angus Mail). Ela tem que passar pelo OWASP dependency-check, que roda
  no alvo `make sca` (não faz parte do `make verify`; usa `owasp-suppressions.xml`). Essa execução entra como
  checkpoint de segurança do plano. Qualquer achado alto ou crítico bloqueia a feature.
- **Credenciais SMTP:** só por variável de ambiente ou Secret. Nada de padrão com senha em
  `application.properties`, porque o Mailpit não precisa de credencial. Elas nunca são logadas.
- **Injeção de cabeçalho:** os assuntos são montados só com UUID e textos fixos. O destinatário vem do cadastro,
  já validado por `Email` em `registration`. O único texto livre é
  `serviceName`, cadastrado por usuário interno no catálogo, e ele vai **só no corpo**, nunca em cabeçalho.
  Corpo em texto puro, então não há risco de HTML/script.
- **Conteúdo do e-mail de orçamento:** contém dado comercial (serviços e valores) enviado só ao e-mail do dono
  da OS. Esses valores não aparecem em log: o `INFO` de envio traz só IDs e o e-mail mascarado.
- **Decisão pelo e-mail:** esta feature não cria nenhum caminho de entrada. O e-mail é só de saída, e os riscos
  dos links de aprovação ficam na spec de `estimate-approval-link`.
- **Abuso / volume:** no máximo um e-mail por transição nominal, e o número de transições é limitado pelos
  comandos de negócio. Não há como gerar e-mails em massa sem antes mudar o status por um endpoint autenticado.
- **Falha do SMTP:** como o envio é assíncrono e depois do commit, a resposta HTTP do comando não depende do SMTP.
  Os timeouts acima limitam o tempo preso no executor do listener.
- **Rollout e rollback:** a mudança é aditiva. O padrão é o canal `log`, então ambientes sem SMTP configurado
  continuam como hoje (log simulado). Rollback é `git revert` ou `APP_NOTIFICATION_EMAIL_CHANNEL=log`.
- **Mudança de comportamento:** o log simulado e o e-mail de "OS finalizada" (assunto "Your vehicle is ready for
  pickup") deixam de existir. A docs de `notifications-so-finalized` ganha uma nota apontando para esta feature.

## Estratégia de testes

- **Domínio — `ServiceOrderTest`** (unitário, novo cenário):
  - `create` seguido de `pullStatusChange()` retorna vazio;
  - reconstituir em `IN_DIAGNOSIS`, executar um comando que leve a `AWAITING_APPROVAL` e chamar
    `pullStatusChange()` retorna o evento `IN_DIAGNOSIS → AWAITING_APPROVAL`; uma segunda chamada retorna vazio;
  - um comando que recalcula sem mudar o status interno não gera evento;
  - `AWAITING_ITEMS → IN_PROGRESS` gera o evento interno (a filtragem nominal é testada no listener).
- **Listener — `ServiceOrderStatusChangedNotificationListenerTest`** (unitário, com a porta mockada):
  - mudança nominal chama a porta uma vez com o `ServiceOrderStatusLabel` novo;
  - `AWAITING_ITEMS → IN_PROGRESS` e `IN_PROGRESS → AWAITING_ITEMS` não chamam a porta;
  - porta lançando `RuntimeException` não propaga, e o log não traz dado pessoal.
- **Adapters (unitário, `ListAppender`):**
  - `SmtpCustomerNotificationAdapterTest` com `JavaMailSender` mockado: envia uma mensagem com destinatário,
    assunto e corpo esperados; cliente não encontrado gera `WARN` só com IDs e nenhum envio; o log de `INFO` não
    contém o e-mail em claro;
  - `SimulatedEmailCustomerNotificationAdapterTest` atualizado para o método novo;
  - `SmtpCustomerEstimateNotificationAdapterTest`: assunto contendo o ID da OS e corpo com linhas, valores,
    total e validade, sem instrução de resposta; orçamento ou cliente não
    encontrado gera `WARN` só com IDs e nenhum envio; valores e e-mail em claro não aparecem em log;
  - `EmailMaskingTest`.
- **Use case — `FinalizeServiceOrderUseCaseTest`:** atualizado. Sai a dependência da porta e os testes de
  notificação direta. A finalização continua coberta pelos testes de regra existentes.
- **Módulo — `@ApplicationModuleTest`** (`ServiceOrderStatusChangeNotificationModuleTest`, com
  `DIRECT_DEPENDENCIES`, H2 + Flyway, `CustomerNotificationPort` como `@MockitoBean`), usando a `Scenario` API:
  - fluxo Diagnóstico → Aguardando Aprovação via `GenerateEstimateUseCase`: a porta recebe exatamente uma
    chamada com `AGUARDANDO_APROVACAO`, e `CustomerEstimateNotificationPort` continua sendo chamado
    separadamente;
  - `CompleteExecutionUseCase` (→ Finalizada) e `FinalizeServiceOrderUseCase` (→ Entregue) geram, cada um,
    exatamente uma chamada;
  - comando que viola regra de negócio (ex.: finalizar uma OS não `COMPLETED`) não gera chamada;
  - porta lançando exceção: o novo status continua persistido (lido de volta pelo repositório);
  - `FinalizeServiceOrderFlowApplicationModuleTest` é revisado para o novo fluxo (ele hoje prova a fiação
    direta da porta no finalize).
- **Fiação do listener de reposição:** `RestockedStockReservationRetryListener` roda em
  `@TransactionalEventListener(AFTER_COMMIT)`. O plano vai incluir um teste de módulo confirmando que a
  transição `AWAITING_ITEMS → IN_PROGRESS` disparada por ele chega ao listener novo e é filtrada, para não
  depender de suposição sobre propagação de transação nesse caminho.
- **Fronteiras:** `ModuleStructureTest` verde, sem nenhuma `@NamedInterface` nova.
- **HTTP:** sem mudança de contrato. `OpenApiContractTest` tem que continuar verde sem alteração.
- **Cobertura:** código novo e alterado ≥ 80% (`make coverage`). `make verify` é obrigatório antes de concluir.
- **Ciclo ponta a ponta (`@ApplicationModuleTest`):** gerar orçamento → `CustomerEstimateNotificationPort`
  chamado uma vez → `ApplyExternalStatusUpdateUseCase` com `APPROVED` → `CustomerNotificationPort` chamado com
  `EXECUCAO`.
- **Validação manual (demo):** `docker compose up`, levar uma OS até a geração do orçamento pelo Postman e
  conferir no Mailpit (`http://localhost:8025`) o e-mail de orçamento (serviços, valores, total e validade) e o
  de "Aguardando Aprovação". Depois, aprovar por um canal existente (interno ou a requisição do RF40 *"Update
  service order status via external channel (HMAC)"*), conferir o e-mail de "Execução" e seguir até "Finalizada"
  e "Entregue". Com `estimate-approval-link` entregue, a aprovação da demo passa a ser o clique no link do
  e-mail. O passo a passo fica no `README.md`. A coleção Postman não muda; se a
  revisão do plano decidir adicionar descrições às requisições, o README é atualizado junto, como exige o
  `AGENTS.md`.

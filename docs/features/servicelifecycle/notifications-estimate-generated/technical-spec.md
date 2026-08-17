# Especificação Técnica: Notificação de Estimate Gerada (Customer)

| Campo | Valor |
|---|---|
| Feature | `notifications-estimate-generated` |
| Status | Approved |
| Responsável | `Leandro Nascimento` |
| Atualizado em | `2026-08-17` |
| Aprovado por | `Leandro Nascimento` |
| Aprovado em | `2026-08-17` |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

**Módulo dono:** `servicelifecycle`. `Estimate` (pacote `servicelifecycle.estimate`) e `ServiceOrder`/`Technician`
vivem no mesmo módulo Spring Modulith de topo (`AGENTS.md`: "`servicelifecycle`: Service Order, Estimate and
supporting Technician capabilities"), então não há travessia de módulo Modulith para reagir à geração da
Estimate — mas há uma travessia de **propriedade de código**: quem gera a Estimate é o use case de Matheus
Campagnone, em uma branch própria que esta feature deliberadamente não importa (`functional-spec.md`, Decision
record A).

**Decisão arquitetural — evento de domínio via `@ApplicationModuleListener`, não chamada síncrona (decidida).**
Nas stories #7 (`notifications-so-finalized`) e #1 (`notifications-technician-new-so`), o próprio use case que
mudava o estado (finalize, create) chamava o port de notificação diretamente, porque este projeto era dono
daquele código. Aqui esse pressuposto não vale: o código que gera a Estimate pertence a outro desenvolvedor, e
esta feature explicitamente não o edita (`functional-spec.md`). Uma chamada síncrona ao port exigiria adicionar
uma dependência de saída dentro do use case dele, o que violaria o isolamento entre as duas branches que
motivou a estratégia de mock. A alternativa correta é reagir a um **evento de domínio de verdade**, publicado
pelo produtor e consumido de forma desacoplada — daí a decisão por `@ApplicationModuleListener` (Spring
Modulith), o primeiro uso de evento de aplicação/`ApplicationEventPublisher` no projeto. **Correção registrada
durante a implementação:** ao contrário do que esta seção originalmente afirmava, `spring-modulith-starter-core`
não traz `spring-modulith-events-api` transitivamente — `@ApplicationModuleListener` não compilava sem ela. O
`pom.xml` ganhou uma nova dependência direta, `org.springframework.modulith:spring-modulith-events-api:2.1.0`
(mesma versão das demais dependências Modulith do projeto), confirmada resolvível e suficiente para compilar e
rodar os testes.

Consequência prática: `@ApplicationModuleListener` roda de forma assíncrona, após o commit da transação que
publicou o evento (`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` combinados pelo Modulith).
Isso satisfaz de forma nativa a regra de negócio "falha de notificação nunca desfaz a geração da Estimate"
(`functional-spec.md`, Cenário 3) — não é preciso um `try/catch` ao redor de uma chamada síncrona como nas
stories #1/#7, porque o listener já roda fora e depois da transação de origem.

### Risco e pendência — confirmar mecanismo real de publicação do evento

Esta feature assume que, quando `feat/servicelifecycle-estimate-generation` mergear, o use case de geração de
Estimate publicará o evento via `ApplicationEventPublisher.publishEvent(...)` (mecanismo padrão que
`@ApplicationModuleListener` exige para disparar) — é o único jeito de um listener deste tipo ser acionado.
**Isso ainda não foi confirmado com o Matheus** (o que foi confirmado até aqui é apenas o contrato de campos do
evento, não o mecanismo de publicação). Se o módulo `estimate` publicar o evento por outro caminho (ex.:
chamada direta a um port, fila externa, ou nenhum evento de aplicação de fato), o listener desta feature nunca
dispara e a notificação nunca é enviada, mesmo com todo o resto implementado e testado. **Ação pendente, fora
desta implementação:** confirmar explicitamente com o Matheus, antes do merge da branch dele, que o use case de
geração de Estimate publica o evento via `ApplicationEventPublisher`. Se ele preferir outro mecanismo, o design
de escuta (mas não o port/adapter/regra de negócio) desta feature precisa ser revisto.

## Interfaces e fluxo de dados

Toda a nova estrutura fica em um pacote próprio, `servicelifecycle.estimate.notification`, deliberadamente
separado de `servicelifecycle.estimate.domain`/`.application`/`.infrastructure` (onde o código real do Matheus
deve aparecer) para não colidir com a estrutura que a branch dele vai introduzir.

**Evento (mock)** — `servicelifecycle.estimate.notification.event.EstimateGeneratedEvent`, record local,
documentado como mock do contrato confirmado (`functional-spec.md`, Decision record A):

```java
public record EstimateGeneratedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID estimateId,
        UUID serviceOrderId,
        UUID diagnosisId,
        UUID customerId,
        Instant expiresAt) {
}
```

Na reconciliação (fora de escopo desta spec, passo separado após o merge da branch do Matheus), este record é
removido e o listener passa a escutar o evento real publicado por `servicelifecycle.estimate`; nenhuma outra
peça (port, adapter, regra de negócio) muda.

**Listener** — `servicelifecycle.estimate.notification.application.EstimateGeneratedNotificationListener`:

```java
@Component
class EstimateGeneratedNotificationListener {

    private final CustomerEstimateNotificationPort notificationPort;

    @ApplicationModuleListener
    void on(EstimateGeneratedEvent event) {
        try {
            notificationPort.notifyEstimateGenerated(
                    event.estimateId(), event.serviceOrderId(), event.customerId(), event.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("Failed to notify customer {} about estimate {}", event.customerId(), event.estimateId(), ex);
        }
    }
}
```

O `try/catch` aqui não protege uma transação (o listener já roda após o commit, fora dela) — protege contra uma
exceção não tratada subir para o mecanismo assíncrono do Modulith/Spring e ser apenas logada de forma genérica
pelo `AsyncUncaughtExceptionHandler` padrão; capturando explicitamente, o log fica no mesmo formato/nível dos
adapters das stories #1/#7.

**Port** — `servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort`:

```java
public interface CustomerEstimateNotificationPort {
    void notifyEstimateGenerated(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt);
}
```

Recebe `expiresAt` como `Instant` bruto e o repassa como recebido — nenhum cálculo de prazo acontece nesta
camada (`functional-spec.md`, Decision record B).

**Adapter** — `servicelifecycle.estimate.notification.infrastructure.SimulatedEmailCustomerEstimateNotificationAdapter`,
reaproveitando o mesmo canal já confirmado com o dono do épico para notificações ao Customer na story #7
(e-mail simulado, escrito em log estruturado — não SMTP real, não endpoint HTTP):

- Resolve o `Customer` via `registration.customer.domain.repository.CustomerRepository.findById(customerId)`
  (mesmo caminho aberto pela story #7).
- Encontrado: monta um e-mail simulado (assunto: Estimate aguardando aprovação; corpo referencia `estimateId`,
  `serviceOrderId` e o prazo `expiresAt`) e loga em `INFO`, mascarando o e-mail (ver Segurança e operação).
- Não encontrado: loga `WARN` (referência inconsistente) e retorna normalmente — nunca lança.

**Dependência cruzando módulos (`servicelifecycle` → `registration.customer`):** reaproveita a exposição via
`@org.springframework.modulith.NamedInterface` (`customer-repository`, `customer-model`) introduzida pela story
#7 em `registration.customer.domain.{repository,model}.package-info.java`. Como esta branch parte de `dev` e
`notifications-so-finalized` ainda não foi mergeada, essa anotação **não existe ainda no código-base desta
branch** — esta feature a adiciona de forma idêntica à da story #7. Se `notifications-so-finalized` mergear
primeiro, a anotação já vai existir e esta feature apenas a reaproveita sem duplicar; se esta feature mergear
primeiro, a story #7 é quem reaproveita. Em qualquer ordem, o merge das duas é um conflito trivial (mesma
anotação, mesmo arquivo) — não uma divergência de padrão.

**Contrato HTTP:** inalterado. Nenhum endpoint novo ou modificado.

## Persistência e dados de bootstrap

Nenhum. Nenhuma tabela, coluna ou dado persistido é introduzido — classificação: **nenhum seed necessário**. O
projeto não tem `spring-modulith-events-jpa` (nem qualquer registro de publicação de eventos) configurado, então
os eventos do Modulith são apenas em memória: se a aplicação cair entre o commit da Estimate e a execução do
listener, a notificação é perdida silenciosamente, sem retry automático. Isso é aceitável dentro da regra de
negócio já assumida ("nenhum histórico de entrega, melhor esforço", `functional-spec.md`), mas fica registrado
aqui como uma limitação operacional explícita, não uma omissão.

## Segurança e operação

- **Autorização:** inalterada — nenhum endpoint novo, nenhuma mudança de quem pode disparar a geração da
  Estimate.
- **Dados sensíveis em log:** mesmo princípio da story #7 — o log em `INFO` do adapter não deve conter o
  e-mail bruto nem o nome completo do Customer; identifica o destinatário por `customerId` e `estimateId`, com
  e-mail mascarado (ex.: `j***@e***.com`) se útil para verificação manual.
- **Nova dependência:** leitura de `CustomerRepository` a partir de `servicelifecycle` (mesma exposição já
  usada pela story #7) — sem escrita, sem superfície de ataque nova além de uma leitura adicional de dado já
  existente.
- **Confiabilidade de entrega:** ver "Persistência e dados de bootstrap" — sem registro de publicação
  persistido, não há garantia de entrega nem retry; risco aceito conforme a spec funcional.
- **Pendência de integração:** ver "Risco e pendência" acima — o mecanismo de publicação do evento pelo módulo
  `estimate` ainda não foi confirmado com o Matheus.
- **Rollout/recuperação:** aditivo e retrocompatível; nenhuma migration. Rollback é um `git revert` simples,
  já que nenhum estado persistido ou externo é criado.

## Estratégia de testes

- **Listener (`EstimateGeneratedNotificationListenerTest`), novo:** usar `@ApplicationModuleTest` com a
  `Scenario` API do `spring-modulith-starter-test` (já é dependência de teste do projeto) —
  `scenario.publish(new EstimateGeneratedEvent(...)).andWaitForStateChange(...)` publicando o evento mock
  diretamente (simulando o que o módulo `estimate` fará quando integrado) e assertando que
  `CustomerEstimateNotificationPort.notifyEstimateGenerated(...)` foi chamado com os 4 parâmetros esperados.
  Cobrir também o caso em que o port lança `RuntimeException`: o listener não deve propagar a exceção.
- **Adapter (`SimulatedEmailCustomerEstimateNotificationAdapterTest`), novo:** mesmo padrão da story #7 —
  cliente encontrado (log `INFO` sem e-mail/nome em claro) e cliente não encontrado (log `WARN`, sem exceção),
  via Logback `ListAppender`.
- **Fronteira de módulo:** rodar `ModuleStructureTest` (`ApplicationModules.verify()`) após adicionar a
  dependência `servicelifecycle → registration.customer`; confirmar que a anotação `@NamedInterface`
  reaproveitada (ou adicionada, conforme ordem de merge) resolve qualquer violação.
- **Cobertura:** manter a meta de 80% do projeto no código novo/alterado (`make coverage`).
- **HTTP:** sem mudança de contrato, sem novos testes MockMvc.

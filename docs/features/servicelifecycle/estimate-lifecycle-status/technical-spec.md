# Especificação Técnica: Status de Ciclo de Vida da Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-lifecycle-status` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-23 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-23 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

Módulo afetado: `servicelifecycle` (sub-área `estimate`; `serviceorder` é lido, não alterado). Nenhum outro
bounded context é tocado.

Hoje `Estimate` (`estimate/domain/model/Estimate.java`) não tem status. `GenerateEstimateUseCase` cria a
Estimate e publica `EstimateGenerated` na mesma transação; `DecideEstimateLinesUseCase` aplica decisões por
`ServiceExecution` diretamente na `ServiceOrder` (fonte de verdade da execução) sem olhar para nenhum estado
da Estimate.

Esta feature adiciona um `EstimateStatus` (`DRAFT`, `SENT`, `CLOSED`, `EXPIRED`) ao agregado `Estimate` e
conecta as duas únicas transições hoje determináveis (`DRAFT → SENT` na geração; `SENT → CLOSED` quando a
última linha pendente é decidida). Nenhum use case novo é criado; os dois use cases existentes
(`GenerateEstimateUseCase`, `DecideEstimateLinesUseCase`) ganham um passo adicional.

`EXPIRED` entra apenas como valor de enum (persistível e exposto em leitura); nenhum método de domínio ou
gatilho de transição para `EXPIRED` é adicionado nesta feature — isso permanece bloqueado por `AD-013`
(`Team Decision Required`). Adicionar um método `expire()` sem nenhum caller violaria a regra do projeto
contra abstrações especulativas (`AGENTS.md` — Code style).

### Modelo de domínio

`Estimate` ganha o campo `status` (`EstimateStatus`, novo enum em `estimate/domain/model`) e dois métodos de
transição:

- `Estimate.create(...)`: passa a construir a Estimate com `status = DRAFT` (em vez de nenhum status).
- `Estimate.markSent()`: exige `status == DRAFT`; transiciona para `SENT`; lança `IllegalStateException` caso
  contrário. Chamado por `GenerateEstimateUseCase` logo após `Estimate.create(...)` e antes de
  `estimateRepository.save(estimate)` — a Estimate nunca é persistida em `DRAFT`.
- `Estimate.close()`: exige `status == SENT`; transiciona para `CLOSED`; lança `IllegalStateException` caso
  contrário. Chamado por `DecideEstimateLinesUseCase` depois de aplicar as decisões da requisição, quando
  nenhuma linha da Estimate tem mais uma `ServiceExecution` `PENDING`.
- `Estimate.reconstitute(...)` passa a receber `status` (vindo da persistência) em vez de assumir um valor
  fixo, para reidratar corretamente `SENT`/`CLOSED` (e, no futuro, `EXPIRED`).
- Novo getter `Estimate.status()`.

`DecideEstimateLinesUseCase.execute` ganha uma validação adicional, logo após carregar a `Estimate` e antes
das validações já existentes (duplicidade de `serviceExecutionId`, linhas pertencentes à Estimate):

```
if (estimate.status() != EstimateStatus.SENT) {
    throw new IllegalStateException("Estimate is not open for decisions: " + estimateId);
}
```

Depois de aplicar todas as decisões da requisição (`authorizeExecutionFromEstimate`/
`rejectExecutionFromEstimate`), o use case verifica se **todas** as `ServiceExecution` referenciadas pelas
linhas da Estimate (não apenas as decididas nesta chamada) deixaram de estar `PENDING`, usando o mapa
`executionsById` já carregado a partir do `ServiceOrder`. Em caso positivo, chama `estimate.close()` e inclui
a Estimate no `estimateRepository.save(estimate)` já existente no fluxo (hoje o use case não salva a
Estimate porque ela não muda; passa a salvar sempre que `close()` for aplicado).

### Fluxo de geração

`GenerateEstimateUseCase.execute` insere `estimate.markSent()` entre `Estimate.create(...)` e
`estimateRepository.save(estimate)`. Nenhuma outra regra de `estimate-generation` (congelamento de
`StockRequirement`, `expiresAt`, evento `EstimateGenerated`) muda.

## Interfaces e fluxo de dados

- `EstimateResponse` (DTO de leitura) ganha o campo `status` (string, valores possíveis:
  `DRAFT`\*, `SENT`, `CLOSED`, `EXPIRED`). \*`DRAFT` nunca é observável via API porque a Estimate só é
  persistida depois de `markSent()`; o valor existe no enum de resposta apenas por completude de contrato.
- `GET /api/estimates/{estimateId}` e `POST /api/service-orders/{serviceOrderId}/estimates` (ambos já
  existentes em `EstimateController`) passam a retornar `status` no corpo — mudança aditiva, compatível com
  consumidores atuais.
- `POST /api/estimates/{estimateId}/decisions` (`decide-estimate-lines`) ganha um novo caso de falha: `409
  INVALID_STATE_TRANSITION` quando a Estimate não está `SENT` (mesmo `ErrorResponse`/mesmo handler já usado
  para `ServiceExecution` fora de `PENDING` — `ServiceLifecycleExceptionHandler#handleInvalidState`, sem
  nenhuma mudança no handler). A doc do Springdoc no controller (`@ApiResponses` do endpoint `decide`) é
  atualizada para descrever também esse caso no `409`.
- Nenhum evento de domínio novo é publicado. `Estimate.close()` não emite evento próprio nesta entrega — a
  Estimate já não é a fonte de eventos de execução (isso continua em `ServiceOrder`/`ServiceExecution`); se
  um consumidor futuro precisar reagir ao fechamento da Estimate, isso é um gap de implementação separado,
  não coberto aqui.
- Atualizar `docs/api/postman/workshop-management-system.postman_collection.json`: exemplos de resposta de
  `GET /api/estimates/{estimateId}` e `POST /api/service-orders/{serviceOrderId}/estimates` passam a incluir
  `status`; adicionar (ou documentar) o cenário 409 de `POST /api/estimates/{estimateId}/decisions` para
  Estimate não `SENT`.

## Persistência e dados de bootstrap

Classificação: **nenhum seed necessário** (mudança estrutural de schema, não dado de referência/demonstração
nem fixture).

- Nova migração Flyway `V<timestamp>__add_status_to_estimates.sql`:
  ```sql
  ALTER TABLE estimates
      ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENT';
  ```
  `DEFAULT 'SENT'` garante que toda linha hoje existente (todas geradas antes desta feature, portanto
  sempre efetivamente "enviadas") seja migrada com o status correto sem exigir um `UPDATE` separado. Novas
  inserções sempre informam `status` explicitamente pelo mapper (nunca dependem do default).
- `EstimateJpaEntity` ganha o campo `status` (`@Enumerated(EnumType.STRING)`, `@Column(name = "status",
  nullable = false)`).
- `EstimatePersistenceMapper` passa `status` nos dois sentidos (domínio ⇄ entidade JPA).
- `EstimateRepositoryImpl` não muda (delega ao mapper).

## Segurança e operação

- Não introduz nenhum dado sensível novo nem novo campo de entrada externo controlado pelo cliente — `status`
  é somente computado pelo domínio, nunca aceito via request body.
- Não há mudança de autorização: os mesmos endpoints, com o mesmo controle de acesso já existente,
  continuam válidos.
- Sem novos segredos, credenciais ou dependências.
- Rollout: migração aditiva (`ADD COLUMN ... DEFAULT`), sem exigir janela de manutenção nem reprocessamento
  manual de dados; reversível apenas via nova migração (o projeto não usa `DOWN` migrations).
- Nenhuma mudança de log sensível; mensagens de exceção seguem o padrão já existente (sem dados pessoais).

## Estratégia de testes

- **Domínio (`Estimate`):** testes unitários para `create()` (status inicial `DRAFT`), `markSent()` (sucesso
  a partir de `DRAFT`; falha a partir de `SENT`/`CLOSED`), `close()` (sucesso a partir de `SENT`; falha a
  partir de `DRAFT`/`CLOSED`), e `reconstitute()` preservando o status persistido.
- **Aplicação (`GenerateEstimateUseCase`):** teste garantindo que a Estimate é persistida com `status =
  SENT` (não `DRAFT`).
- **Aplicação (`DecideEstimateLinesUseCase`):**
  - decidir uma linha de uma Estimate `SENT` com outras linhas ainda `PENDING` mantém a Estimate `SENT`;
  - decidir a última linha `PENDING` fecha a Estimate (`status = CLOSED`) na mesma transação;
  - decidir qualquer linha de uma Estimate já `CLOSED` falha com `IllegalStateException`/`409
    INVALID_STATE_TRANSITION` e não aplica nenhuma decisão da chamada (nem nas `ServiceExecution`, nem
    reserva de estoque) — teste de regressão espelhando
    `rejectsWhenServiceExecutionIsNotPendingAndAppliesNoDecision`, já existente para o caso de
    `ServiceExecution`.
- **HTTP (`EstimateController`):** `MockMvc` cobrindo `status` no corpo de `GET /api/estimates/{id}` e de
  `POST /api/service-orders/{id}/estimates`; cenário `409` de `POST /api/estimates/{id}/decisions` para
  Estimate não `SENT`.
- **Persistência:** teste de mapeamento (`EstimatePersistenceMapper`) cobrindo os quatro valores de
  `EstimateStatus`; teste de migração/startup validando a nova coluna via H2 em modo MySQL, incluindo que
  uma linha pré-existente (inserida antes da migração, no cenário de teste) recebe `SENT` pelo `DEFAULT`.
- Nenhuma mudança de cobertura em `estimate-generation`/`decide-estimate-lines` além dos pontos listados
  acima; os testes hoje existentes de congelamento de `StockRequirement`, reserva de estoque e notificação
  de técnico continuam válidos sem alteração.

# Especificação Funcional: Notificação de Estimate Gerada (Customer)

| Campo | Valor |
|---|---|
| Feature | `notifications-estimate-generated` |
| Status | Approved |
| Responsável | `Leandro Nascimento` |
| Atualizado em | `2026-08-17` |
| Aprovado por | `Leandro Nascimento` |
| Aprovado em | `2026-08-17` |
| Referências | `RF31`, `docs/EPIC-5-notifications-plan-v2.md` (item #2), `docs/adr/ADR-003-notifications-boundary.md`, contrato de evento confirmado por Matheus Campagnone (dono de `servicelifecycle.estimate`, branch `feat/servicelifecycle-estimate-generation`, ainda não mergeada em `dev`), `docs/features/servicelifecycle/notifications-so-finalized/` e `docs/features/servicelifecycle/notifications-technician-new-so/` (referência de padrão, branches ainda não mergeadas) |

## Problema e resultado esperado

Hoje, quando uma Estimate é gerada para uma Service Order, o Customer não recebe nenhuma comunicação automática.
Ele só descobre que existe uma proposta aguardando aprovação — e o prazo que tem para aprová-la — se checar
manualmente. O resultado esperado: no momento em que uma Estimate é gerada com sucesso, o Customer dono da
Service Order é automaticamente notificado de que há uma Estimate aguardando aprovação, incluindo o prazo de
expiração já calculado pelo módulo `servicelifecycle.estimate`, fechando o RF31.

### Decision record A — contrato do evento é mockado, não suposto (registrado em 2026-08-17)

A feature de `Estimate` está implementada e testada (59 testes, `mvnw verify` verde) na branch
`feat/servicelifecycle-estimate-generation`, mas essa branch ainda não foi mergeada em `dev`. Em vez de acoplar
esta feature àquela branch (importando uma classe de evento que ainda pode mudar antes do merge), o dono do
módulo (Matheus Campagnone) confirmou o contrato de dados do evento de geração de Estimate:

```
eventId, occurredAt, estimateId, serviceOrderId, diagnosisId, customerId, expiresAt
```

Esta feature constrói e testa contra uma representação própria e local desse contrato (documentada como mock,
não importada da branch de origem). Quando `feat/servicelifecycle-estimate-generation` for mergeada em `dev`,
a reconciliação (troca do mock pelo evento real publicado pelo módulo `estimate`) é um passo separado, fora do
escopo desta spec — ver "Fora de escopo".

### Decision record B — `expiresAt` é usado como recebido, nunca recalculado (registrado em 2026-08-17)

O plano original do Épico 5 (item #2) descrevia a regra de prazo como "24h se estoque disponível / 48h se
precisar de reposição". Essa regra é responsabilidade do módulo `servicelifecycle.estimate`, que já calcula e
entrega o resultado pronto no campo `expiresAt` do contrato do evento (Decision record A). Esta feature não
reimplementa, não valida e não reinterpreta essa regra — ela apenas lê `expiresAt` e o usa diretamente na
mensagem de notificação. Isso simplifica esta feature para um simples repasse de dado (a Estimate expira em
`expiresAt`) e evita duplicar uma regra de negócio que não pertence a este módulo consumidor.

## Atores e cenários

- **Customer** — dono da Service Order para a qual a Estimate foi gerada; destinatário da notificação.
- **Sistema (`servicelifecycle`)** — reage à geração da Estimate como efeito colateral, fora da transação que a
  gerou (a Estimate é gerada por código de outro módulo/dev, que esta feature não controla nem altera).

**Cenário 1 — Caminho feliz.** Uma Estimate é gerada com sucesso para uma Service Order (evento do contrato da
Decision record A é emitido). O Customer identificado por `customerId` é notificado de que uma Estimate está
aguardando aprovação, e a notificação informa o prazo de expiração usando `expiresAt` diretamente (Decision
record B).

**Cenário 2 — Geração da Estimate falha ou é rejeitada.** Se a geração da Estimate não for concluída com
sucesso pelo módulo `estimate` (regra de negócio dele, fora do escopo desta feature), nenhum evento é emitido e
nenhuma notificação é disparada.

**Cenário 3 — Notificação não pode ser entregue.** A geração e persistência da Estimate pelo módulo de origem
não depende do resultado desta notificação — como esta feature reage a um evento já publicado por uma
transação que não controla, uma falha de entrega aqui nunca desfaz, bloqueia ou reprocessa a geração da
Estimate.

**Cenário 4 — `customerId` não corresponde a um Customer existente.** Inconsistência de dado (referência
órfã): a notificação não é entregue, mas isso não deve lançar exceção não tratada — mesmo padrão de tolerância
a falha estabelecido em `notifications-so-finalized`.

## Regras de negócio

- A notificação é disparada em reação à geração bem-sucedida de uma Estimate, identificada pelo contrato de
  evento da Decision record A.
- Uma notificação é gerada por evento de geração de Estimate recebido.
- O destinatário é o Customer identificado por `customerId`, campo do próprio contrato do evento — não é
  necessário consultar a Service Order ou a Estimate para descobrir o dono.
- A mensagem da notificação usa `expiresAt` exatamente como recebido no evento, sem recalcular, arredondar ou
  reinterpretar a regra de 24h/48h (Decision record B).
- Falha ao entregar a notificação a este Customer nunca falha, desfaz ou bloqueia a geração da Estimate no
  módulo de origem — esta feature não participa da transação que gera a Estimate.
- Nenhum histórico ou estado de entrega de notificação é persistido por esta feature.
- Esta feature não introduz nem altera nenhum campo do agregado `Estimate` nem do agregado `Customer`.

## Fora de escopo

- A classe de evento real publicada por `servicelifecycle.estimate` — esta feature usa uma representação mock
  local do contrato confirmado (Decision record A); a troca do mock pelo evento real após o merge de
  `feat/servicelifecycle-estimate-generation` é um passo de reconciliação separado.
- O cálculo do prazo de expiração (regra de 24h disponível / 48h+reposição) — pertence ao módulo `estimate`
  (Decision record B).
- A decisão entre reagir via `@ApplicationModuleListener`/evento de domínio ou chamada síncrona a partir do
  código do módulo `estimate` — decisão arquitetural tratada em `technical-spec.md`, pendente de revisão.
- Escolher ou implementar o canal de entrega (e-mail simulado, log, endpoint simulando push) — decidido em
  `technical-spec.md`, mesmo padrão das duas notificações anteriores.
- Notificar sobre aprovação, rejeição ou expiração efetiva da Estimate — são eventos/notificações distintos,
  não mapeados neste item do plano.
- Qualquer nova exposição de dados do Customer além do necessário para identificar o destinatário e (na
  técnica) resolver e-mail/nome — reaproveita o caminho já aberto em `notifications-so-finalized`
  (`registration.customer` via `@NamedInterface`), sem criar nova superfície de módulo.
- Idempotência ou deduplicação de eventos repetidos — nenhum estado de entrega é persistido (ver Regras de
  negócio), então não há como detectar repetição nesta feature.
- As outras 5 notificações restantes do Épico 5.

## Critérios de aceite

- [ ] Um evento de geração de Estimate (contrato da Decision record A) resulta em exatamente uma notificação
      para o Customer identificado pelo `customerId` do evento.
- [ ] A mensagem da notificação contém o valor de `expiresAt` recebido no evento, sem nenhum recálculo do prazo
      de 24h/48h.
- [ ] Nenhuma notificação é disparada quando nenhum evento de geração de Estimate é recebido (cenário de
      falha/rejeição na origem).
- [ ] Uma falha ao entregar a notificação a um Customer não lança exceção não tratada e não afeta o processo
      que gerou a Estimate (que já está fora do controle desta feature).
- [ ] Um evento com `customerId` que não corresponde a nenhum Customer existente não lança exceção não tratada;
      a notificação simplesmente não é entregue.
- [ ] Nenhum novo estado persistido (tabela/coluna) é introduzido por esta feature.

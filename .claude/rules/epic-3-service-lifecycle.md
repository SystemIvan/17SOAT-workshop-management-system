---
paths:
  - "src/main/java/br/com/fiap/workshop_management_system/servicelifecycle/**"
  - "src/test/java/br/com/fiap/workshop_management_system/servicelifecycle/**"
  - "docs/features/servicelifecycle/**"
  - "docs/Architecture-Decisions.md"
  - "docs/adr/ADR-002-realtime-updates-strategy.md"
---

# Épico 3 — regras específicas de `servicelifecycle`

Fonte: `docs/Architecture.md` §4.3/4.4, `docs/Architecture-Decisions.md`,
`docs/adr/ADR-002-realtime-updates-strategy.md`. Não repita este conteúdo em `CLAUDE.md`/`AGENTS.md`.

## Invariantes implementados (fato, código atual)

- `ServiceExecution` states: `pending → authorized → ready/awaiting_part → in_progress → completed`,
  com `rejected` terminal.
- `ServiceOrder.statusSnapshot` é recalculado em comandos e apenas lido nas consultas (ratificado como
  AD-010, ver abaixo).

## Decisões em aberto — não tratar como resolvidas

- **AD-006** (Technician: aggregate/módulo vs. ator autenticado): **Resolved** em 2026-08-23 — o time
  ratificou Option A (Technician continua aggregate rico, com `specialties` e `status`/disponibilidade,
  como já implementado). Continue referenciando Technician só por UUID entre módulos (isso não mudou).
  A atribuição de Technician (`AssignTechnicianUseCase`, `AssignDiagnosisAssigneeUseCase`) valida hoje
  apenas a existência do `technicianId` — validar especialidade/disponibilidade contra os dados do
  aggregate é dívida técnica registrada e não implementada, ver
  `../../docs/tech-debt/TD-002-technician-assignment-does-not-validate-specialty-or-availability.md`; não
  trate essa lacuna como decisão pendente nem a implemente sem retomar esse registro.
- **AD-010** (`statusSnapshot` recalculado em comando vs. calculado em leitura): **Resolved** em
  2026-08-24 — o time ratificou Option B (armazenar `statusSnapshot` em `ServiceOrder` e recalcular após
  cada comando relevante), como já implementado. Leituras continuam apenas lendo o campo persistido;
  não implemente recálculo em leitura sem uma nova decisão do time.
- **AD-015** (estratégia de tracking — polling puro vs. polling+cache vs. SSE/WebSocket): **Resolved**
  em 2026-08-23 — o time ratificou Option A (polling puro, sem cache) para o MVP. Ver
  `docs/Architecture-Decisions.md` e `../../docs/adr/ADR-002-realtime-updates-strategy.md`. Não implemente
  cache, SSE ou WebSocket sem uma nova decisão do time — isso reabriria o escopo, não é coberto pela
  ratificação de AD-015.
- **`../../docs/adr/ADR-002-realtime-updates-strategy.md`**: `Status: Accepted`. O item "Time concorda com
  Polling para MVP" do Approval Checklist foi marcado em 2026-08-23 (ratificação do time). Os outros
  dois itens do checklist (entendimento do plano de migração WebSocket; documentação atualizada em
  `AGENTS.md`) continuam em aberto — não tratar esses dois como resolvidos.

## Regras de fronteira já aceitas (aplicam-se aqui)

- Nenhuma importação de pacote interno de outro módulo (`registration`, `stockprocurement`); use IDs,
  eventos de domínio ou port/adapter. **AD-011 foi resolvida em 2026-08-25** — o time ratificou Option A
  (portas Java in-process no consumidor + API pública `application/api` no módulo dono, chamada dentro do
  mesmo processo; reações assíncronas continuam via `ApplicationEventPublisher`/`@EventListener`), ver
  `../../docs/adr/ADR-005-inter-module-integration-contract.md`. Nenhum endpoint REST interno entre módulos
  deve ser criado para isso enquanto o sistema for um monólito modular; a exposição OHS/REST descrita no
  Context Map do Miro passa a valer só numa eventual extração futura para microsserviços, decidida em ADR
  própria quando ocorrer.
- `ModuleStructureTest` deve continuar verde após qualquer mudança estrutural.

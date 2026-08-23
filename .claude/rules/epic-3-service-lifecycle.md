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
- `ServiceOrder.statusSnapshot` é recalculado em comandos e apenas lido nas consultas.

## Decisões em aberto — não tratar como resolvidas

- **AD-006** (Technician: aggregate/módulo vs. ator autenticado): **Resolved** em 2026-08-23 — o time
  ratificou Option A (Technician continua aggregate rico, com `specialties` e `status`/disponibilidade,
  como já implementado). Continue referenciando Technician só por UUID entre módulos (isso não mudou).
  A atribuição de Technician (`AssignTechnicianUseCase`, `AssignDiagnosisAssigneeUseCase`) valida hoje
  apenas a existência do `technicianId` — validar especialidade/disponibilidade contra os dados do
  aggregate é dívida técnica registrada e não implementada, ver
  `../../docs/tech-debt/TD-002-technician-assignment-does-not-validate-specialty-or-availability.md`; não
  trate essa lacuna como decisão pendente nem a implemente sem retomar esse registro.
- **AD-010** (`statusSnapshot` recalculado em comando vs. calculado em leitura): Team Decision
  Required. Preserve o comportamento implementado (Option B); não redesenhe nem declare a decisão
  compartilhada como aprovada.
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
  eventos de domínio ou port/adapter (AD-011 ainda pendente — use mocks/interfaces próprias enquanto
  isso).
- `ModuleStructureTest` deve continuar verde após qualquer mudança estrutural.

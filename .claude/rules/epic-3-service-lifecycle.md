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

- **AD-006** (Technician: aggregate/módulo vs. ator autenticado): Team Decision Required. Enquanto
  não resolvida, referencie Technician só por UUID entre módulos e não amplie comportamento de
  domínio do Technician além do que já existe. Evidência adicional (Miro, doc "4. Aggregates —
  Modelo Atualizado", posterior ao RFC-001 de 2026-08-15): classifica Technician como
  "Ator/capability de apoio... não é aggregate nesta modelagem" — reforça o lado B do conflito, mas
  não resolve a decisão; o código continua com Technician como aggregate rico.
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

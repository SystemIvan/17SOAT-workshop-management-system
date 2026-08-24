---
paths:
  - "src/main/java/br/com/fiap/workshop_management_system/servicelifecycle/**"
  - "src/test/java/br/com/fiap/workshop_management_system/servicelifecycle/**"
  - "docs/features/servicelifecycle/**"
  - "docs/Architecture-Decisions.md"
  - "docs/ADR-001-realtime-updates-strategy.md"
---

# Épico 3 — regras específicas de `servicelifecycle`

Fonte: `docs/Architecture.md` §4.3/4.4, `docs/Architecture-Decisions.md`,
`docs/ADR-001-realtime-updates-strategy.md`. Não repita este conteúdo em `CLAUDE.md`/`AGENTS.md`.

## Invariantes implementados (fato, código atual)

- `ServiceExecution` states: `pending → authorized → ready/awaiting_part → in_progress → completed`,
  com `rejected` terminal.
- `ServiceOrder.statusSnapshot` é recalculado em comandos e apenas lido nas consultas (ratificado como
  AD-010, ver abaixo).

## Decisões em aberto — não tratar como resolvidas

- **AD-006** (Technician: aggregate/módulo vs. ator autenticado): Team Decision Required. Enquanto
  não resolvida, referencie Technician só por UUID entre módulos e não amplie comportamento de
  domínio do Technician além do que já existe. Evidência adicional (Miro, doc "4. Aggregates —
  Modelo Atualizado", posterior ao RFC-001 de 2026-08-15): classifica Technician como
  "Ator/capability de apoio... não é aggregate nesta modelagem" — reforça o lado B do conflito, mas
  não resolve a decisão; o código continua com Technician como aggregate rico.
- **AD-010** (`statusSnapshot` recalculado em comando vs. calculado em leitura): **Resolved** em
  2026-08-24 — o time ratificou Option B (armazenar `statusSnapshot` em `ServiceOrder` e recalcular após
  cada comando relevante), como já implementado. Leituras continuam apenas lendo o campo persistido;
  não implemente recálculo em leitura sem uma nova decisão do time.
- **AD-015** (estratégia de tracking — polling puro vs. polling+cache vs. SSE/WebSocket): Team
  Decision Required. Não implemente cache ou WebSocket sem aprovação do time.
- **`docs/ADR-001-realtime-updates-strategy.md`**: `Status` foi atualizado para `Accepted` por Santiago
  Silvestre (decision maker registrado) em 2026-08-16. O Approval Checklist do próprio documento
  (seção final) continua com todos os itens desmarcados — "Time concorda com Polling para MVP" não
  foi confirmado pelo time. Trate a opção "Polling" como aceita por Santiago, mas **não** como AD-015
  resolvida: `Architecture-Decisions.md` continua listando AD-015 como Team Decision Required (Scope:
  Shared architecture) até o time ratificar.

## Regras de fronteira já aceitas (aplicam-se aqui)

- Nenhuma importação de pacote interno de outro módulo (`registration`, `stockprocurement`); use IDs,
  eventos de domínio ou port/adapter (AD-011 ainda pendente — use mocks/interfaces próprias enquanto
  isso).
- `ModuleStructureTest` deve continuar verde após qualquer mudança estrutural.

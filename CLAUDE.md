@AGENTS.md

# Shared operational context

Complements `AGENTS.md` (project rules) without repeating it. This section is project-wide — valid for
any bounded context or epic, not tied to one contributor.

## Known documentation gap

`AGENTS.md` references `docs/adr/ADR-002-notifications-boundary.md`; the actual file is
`docs/adr/ADR-003-notifications-boundary.md`. Already tracked as a gap in `docs/Architecture-Decisions.md`.
Do not create a duplicate `ADR-002` file to "fix" the mismatch without checking whether the numbering
gap itself is intentional (e.g. a superseded/renumbered ADR) — read `Architecture-Decisions.md` first.

## Per-epic local rules

Epic-specific architectural context (open decisions, current invariants, blocking Miro items) belongs in
`.claude/rules/epic-<n>-<bounded-context>.md`, loaded only when editing code/docs for that context — not
duplicated here. See `.claude/rules/epic-3-service-lifecycle.md` for the existing example. When starting
work on a new epic, create the equivalent rules file instead of growing this shared file with
epic-specific detail.

Two project-wide (not epic-bound) assets consume these per-epic rules files dynamically: the
`feature-sdd` skill (`.claude/skills/feature-sdd/`) for starting/reviewing SDD specs in any bounded
context, and the `architecture-reviewer` agent (`.claude/agents/architecture-reviewer.md`) for checking
a change against `docs/Architecture-Decisions.md` and the relevant epic's rules file. Keep these two
generic; put epic-specific content in the rules files, not by forking the skill/agent per epic.

## Miro board access

The project's Miro board is the source of domain modeling, RFCs and the granular RF-by-RF requirement
breakdown (only the RF ranges per epic are documented locally, e.g. `docs/Architecture.md` §2.3; exact
per-RF text lives on the board). The `claude_ai_Miro` MCP connector is on a Free-plan quota (100 calls/day
that resets daily); once exhausted, `WebFetch` against the board URL only returns the SPA's empty JS
shell, not real content. If the board is unreachable, do not invent or guess board content — say so
explicitly and either retry in a later session or ask a teammate to paste the relevant excerpt/screenshot.

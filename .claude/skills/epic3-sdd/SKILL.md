---
name: epic3-sdd
description: Inicia ou revisa functional-spec/technical-spec/implementation-plan para uma feature do Épico 3 (Execução e Tracking, RF19-RF24) no bounded context servicelifecycle. Use quando Santiago pedir para começar, continuar ou revisar uma spec do Épico 3.
--- ytr

# SDD do Épico 3 (servicelifecycle)

O processo genérico (gates, aprovação humana, status permitidos) já está definido em `AGENTS.md` e
`docs/features/README.md`. Esta skill só adiciona o que é específico do Épico 3; não repita o
processo genérico.

## Ao iniciar uma nova feature

1. Confirme o RF (RF19–RF24) e o slug da feature com Santiago.
2. Copie `docs/features/_template/` para `docs/features/servicelifecycle/<feature-slug>/`.
3. Antes de escrever `functional-spec.md`, releia `.claude/rules/epic-3-service-lifecycle.md` e
   confirme quais decisões do `Architecture-Decisions.md` (AD-006, AD-010, AD-011, AD-015, AD-016) e
   o `docs/ADR-001-realtime-updates-strategy.md` tocam a feature. Trate-as como abertas, não como
   resolvidas, a menos que o documento fonte diga o contrário.
4. Escreva somente `functional-spec.md` neste primeiro passo. Pare e peça aprovação humana explícita
   antes de criar `technical-spec.md`.

## Ao revisar uma spec existente

- Verifique se toda decisão arquitetural referenciada ainda está com o status descrito em
  `docs/Architecture-Decisions.md` (o registro pode ter mudado desde a última leitura).
- Verifique se termos usados batem com `docs/Architecture.md` §4.5 (Ubiquitous Language): Service
  Order, Diagnosis, Estimate, Estimate Line, Service Execution, Stock Requirement, Technician.
- Nunca marque `Status: Approved` em nome de um humano; registre aprovador e data apenas quando o
  humano confirmar explicitamente.

## Referências

- Template: `docs/features/_template/`
- Processo geral: `AGENTS.md`, `docs/features/README.md`
- Contexto de domínio: `docs/Architecture.md`, `docs/Architecture-Decisions.md`
- Decisão local de tracking: `docs/ADR-001-realtime-updates-strategy.md`

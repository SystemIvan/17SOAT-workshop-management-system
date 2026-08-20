---
name: feature-sdd
description: Inicia ou revisa functional-spec/technical-spec/implementation-plan para qualquer feature do projeto (qualquer bounded context/épico), seguindo o gate SDD do AGENTS.md. Use quando o usuário pedir para começar, continuar ou revisar uma spec de feature, não importa o bounded context.
---

# SDD de features (qualquer bounded context)

O processo genérico (gates, aprovação humana, status permitidos) já está definido em `AGENTS.md` e
`docs/features/README.md`. Esta skill só adiciona o que muda conforme o bounded context/épico da
feature; não repita o processo genérico aqui.

## Ao iniciar uma nova feature

1. Identifique o bounded context (`registration`, `servicelifecycle`, `stockprocurement` — ver
   `AGENTS.md` §"Bounded contexts") e, se souber, o épico/faixa de RF (`docs/Architecture.md` §2.3).
2. Verifique se existe `.claude/rules/epic-<n>-<bounded-context>.md` para esse épico/contexto. Se
   existir, releia-o antes de escrever qualquer spec — ele lista decisões arquiteturais em aberto e
   invariantes já implementados que a feature não pode contradizer. Se não existir ainda, prossiga
   normalmente, mas considere sugerir a criação do arquivo (ver `CLAUDE.md` §"Per-epic local rules")
   quando a feature tocar decisões arquiteturais não triviais.
3. Confirme com o responsável o slug da feature e copie `docs/features/_template/` para
   `docs/features/<bounded-context>/<feature-slug>/`.
4. Escreva somente `functional-spec.md` neste primeiro passo. Pare e peça aprovação humana explícita
   antes de criar `technical-spec.md`. Nunca marque `Status: Approved` em nome de um humano; registre
   aprovador e data apenas quando o humano confirmar explicitamente.

## Ao revisar uma spec existente

- Verifique se toda decisão arquitetural referenciada ainda está com o status descrito em
  `docs/Architecture-Decisions.md` (o registro pode ter mudado desde a última leitura) e, se aplicável,
  no arquivo de rules do épico.
- Verifique se os termos usados batem com o Ubiquitous Language documentado em `docs/Architecture.md`
  §4.5 para o bounded context em questão.
- Se a spec aprovada mudou de forma material, ela deve voltar para `Draft` e specs/planos posteriores
  ficam obsoletos até reaprovação (`AGENTS.md` §"Feature specification workflow").

## Referências

- Template: `docs/features/_template/`
- Processo geral: `AGENTS.md`, `docs/features/README.md`
- Contexto de domínio: `docs/Architecture.md`, `docs/Architecture-Decisions.md`
- Regras por épico (quando existirem): `.claude/rules/epic-<n>-<bounded-context>.md`

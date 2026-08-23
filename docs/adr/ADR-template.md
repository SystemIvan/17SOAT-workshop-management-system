# ADR NNN: [Título curto da decisão]

**Status:** Proposed <!-- Proposed | Accepted | Rejected | Deprecated | Superseded by ADR-XXX -->  
**Date:** [AAAA-MM-DD] <!-- ISO 8601; use [AAAA-MM] se apenas o mês/ano for conhecido — não invente o dia -->  
**Deciders:** [Time / pessoas responsáveis pela decisão]  
**Affected By:** [Módulos/bounded contexts impactados pela decisão]

---

## Context

[Descreva o problema ou a força que motiva esta decisão. Qual é o cenário atual, quais restrições
técnicas ou de negócio se aplicam, e por que uma decisão precisa ser tomada agora. Evite antecipar a
solução nesta seção — apenas o contexto.]

## Problem Statement

[Opcional, para decisões maiores. Liste requisitos funcionais, restrições do projeto (prazo, equipe,
infraestrutura) e cenários de uso concretos que a decisão precisa atender.]

## Considered Options

### Option 1: [Nome da opção]

[Descrição, prós e contras.]

### Option 2: [Nome da opção]

[Descrição, prós e contras.]

<!-- Adicione quantas opções forem relevantes. Marque a opção escolhida, ex.: "### Option 1: Nome ✅ SELECIONADO" -->

## Decision

[Declare a decisão de forma direta e verificável: "Será utilizado/adotado X." Explique brevemente o
porquê, referenciando os fatores decisivos (não repita as opções descartadas em detalhe).]

<!-- Não inclua aqui blocos de código, esboços de classe/YAML ou estrutura de pastas. Isso pertence ao
technical-spec.md da feature que consome a decisão (ver AGENTS.md — Feature specification workflow); o
ADR registra a decisão e o racional, não a implementação, para não ficar desatualizado frente ao código. -->

## Consequências

### Positivas ✅

- [Consequência positiva 1]

### Negativas ❌

- [Trade-off ou risco aceito 1]

### Mitigação de Riscos

- [Como os riscos listados acima serão monitorados ou reduzidos]

## Related ADRs

- **ADR-XXX:** [Título] — [como se relaciona]

<!-- Ao adicionar uma referência cruzada, atualize também o ADR referenciado se ele precisar apontar de volta. -->

## References

- [Link ou documento externo relevante]

## Approval Checklist

<!-- Opcional. Use quando a decisão depender de confirmação explícita do time antes de ser marcada Accepted. -->

- [ ] [Item de aprovação 1]
- [ ] [Item de aprovação 2]

---

**Last Updated:** [AAAA-MM-DD]  
**Decision Maker:** [Nome]  
**Status:** [repita o status atual, com uma nota curta se ele mudou desde a criação]

<!--
Convenções deste projeto (ver os ADRs 001-004 para exemplos já corrigidos):
- Título: "# ADR NNN: Nome da decisão" (espaço após ADR, sem escapar caracteres markdown).
- Corpo em português (pt-BR), como o restante da documentação do projeto (AGENTS.md).
- "## Consequências" em português, não "## Consequences".
- Datas em ISO 8601 (AAAA-MM-DD); use AAAA-MM só quando o dia realmente não for conhecido.
- Numeração sequencial em docs/adr/ — não reutilize números já usados; confirme o próximo número livre
  antes de criar o arquivo.
-->


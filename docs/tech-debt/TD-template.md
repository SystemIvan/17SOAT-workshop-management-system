# TD NNN: [Título curto da dívida]

**Status:** Open <!-- Open | Accepted (dívida assumida deliberadamente, sem plano de pagamento) | In Progress | Resolved | Superseded by TD-XXX -->  
**Date:** [AAAA-MM-DD] <!-- ISO 8601; data em que a dívida foi identificada/registrada -->  
**Reported by:** [Nome de quem identificou]  
**Affected areas:** [Módulos/bounded contexts/camadas impactados]  
**Related decisions:** [ADR-XXX / AD-XXX em `docs/Architecture-Decisions.md`, se houver]

---

## Contexto

[Como o código/decisão chegou ao estado atual. O que motivou a escolha original — prazo, escopo do MVP,
falta de decisão do time no momento — e por que ela era razoável então.]

## A dívida

[Descreva objetivamente o desvio entre o estado atual e o estado desejável. Evite prescrever a solução
aqui — apenas o que está errado/subótimo e por quê isso importa.]

## Evidência

[Trechos de código, arquivos, testes ou comportamento observado que demonstram a dívida. Referências
concretas (`caminho/Arquivo.java:linha`), não impressões gerais.]

## Impacto se não for pago

[O que piora com o tempo, que tipo de mudança futura fica mais cara/arriscada, ou que inconsistência
arquitetural se perpetua.]

## Opções de encaminhamento

### Opção A: [Nome]

[Descrição, esforço aproximado, prós e contras.]

### Opção B: [Nome]

[Descrição, esforço aproximado, prós e contras.]

<!-- Adicione quantas opções forem relevantes. -->

## Recomendação

[Direção recomendada e por quê. Se a decisão depender do time (mudança de bounded context, contrato
público, etc.), registre isso explicitamente — dívida técnica que muda fronteira de módulo segue o mesmo
gate de decisão de time usado em `docs/Architecture-Decisions.md`, não pode ser resolvida
unilateralmente.]

## Custo de não decidir agora

[O trabalho pode continuar sem resolver esta dívida? Que suposição temporária, se houver, deve ser
respeitada enquanto ela não é paga (ex.: não expandir determinado acoplamento, não adicionar mais
consumidores diretos).]

---

**Last Updated:** [AAAA-MM-DD]  
**Status:** [repita o status atual, com uma nota curta se ele mudou desde o registro]

<!--
Convenções deste projeto (ver TD-001 para um exemplo já preenchido):
- Título: "# TD NNN: Nome da dívida" (mesmo padrão de `docs/adr/ADR-template.md`).
- Corpo em português (pt-BR), como o restante da documentação narrativa do projeto (AGENTS.md, backlog.md, ADRs).
- Datas em ISO 8601 (AAAA-MM-DD).
- Numeração sequencial em docs/tech-debt/ — não reutilize números já usados; confirme o próximo número
  livre antes de criar o arquivo.
- Um registro de dívida técnica documenta um desvio já existente no código/arquitetura atual — não é o
  mesmo que `docs/backlog.md` (ideias de evolução/features futuras ainda não implementadas) nem que
  `docs/Architecture-Decisions.md` (decisões arquiteturais ainda em aberto sem uma escolha implementada).
  Uma dívida técnica pode nascer de uma decisão já tomada (ex.: MVP escolheu o caminho mais rápido) ou de
  uma decisão de time ainda pendente cujo código atual já assumiu um dos lados (ver AD-XXX relacionada).
- Se pagar a dívida exigir mudar fronteira de bounded context, contrato público ou dado persistido
  compartilhado, trate como uma decisão de time (mesmo gate de `docs/Architecture-Decisions.md`), não como
  uma refatoração livre — e a implementação, quando aprovada, ainda segue o workflow de SDD normal do
  `AGENTS.md` (functional-spec → aprovação → technical-spec → aprovação → implementation-plan).
-->

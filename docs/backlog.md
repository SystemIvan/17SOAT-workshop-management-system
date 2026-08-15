# Backlog de evolução

Este documento registra ideias e oportunidades deliberadamente deixadas fora do MVP. Um item do backlog não representa
compromisso de implementação nem substitui o processo de SDD: antes de ser desenvolvido, deverá passar por discovery e
por suas próprias aprovações funcional e técnica.

## BL-001 — Revisão e versionamento de Estimate pendente

- **Contexto:** Service Lifecycle, com dados consultados de Stock & Procurement.
- **Status:** ideia futura.
- **Origem:** `docs/features/stock-domain-foundation/functional-spec.md`.
- **Problema:** uma Estimate mantém congelados os snapshots de nome, tipo e preço dos Stock Items desde sua criação. O
  MVP não permite atualizar uma oferta pendente quando preço ou escopo mudam.
- **Evolução a avaliar:** permitir revisão explícita, preservar o histórico das versões apresentadas e garantir que o
  Customer aprove exatamente a versão vigente.
- **Pontos para discovery:** eventos que permitem revisão, efeito sobre aprovações parciais, expiração da versão
  anterior, auditoria, comunicação ao Customer e comportamento dos Stock Requirements entre versões.

## BL-002 — Operações de inventário de Stock Item

- **Contexto:** Stock & Procurement.
- **Status:** feature futura.
- **Origem:** `docs/features/stock-domain-foundation/functional-spec.md`.
- **Problema:** o catálogo mantém somente a quantidade disponível inicial e não altera saldos após a criação.
- **Evolução a avaliar:** recebimento, retirada administrativa, justificativa, ajustes, nível mínimo, rastreabilidade e
  identificação de baixo estoque.
- **Pontos para discovery:** tipos de movimentação, correções, auditoria mínima, responsáveis, concorrência e unidade de
  medida.

## BL-003 — Ciclo de Stock Reservation

- **Contexto:** Stock & Procurement, consumido por Service Lifecycle.
- **Status:** feature futura.
- **Origem:** `docs/features/stock-domain-foundation/functional-spec.md`.
- **Problema:** uma Service Execution aprovada precisa separar itens sem depender de um contador reservado sem origem.
- **Evolução a avaliar:** Stock Reservation com referência à Service Execution, linhas de item/quantidade, reserva
  atômica, idempotência, liberação e consumo.
- **Pontos para discovery:** identidade da reserva, fronteira transacional, locks, indisponibilidade após aprovação,
  repetição de comandos e vínculo com Procurement.

## BL-004 — Integração de Stock Requirement e Estimate

- **Contexto:** Service Lifecycle consumindo Stock & Procurement.
- **Status:** feature futura.
- **Origem:** `docs/features/stock-domain-foundation/functional-spec.md`.
- **Problema:** o scaffolding atual permite que o cliente envie nome, tipo e preço de Stock Item no diagnóstico.
- **Evolução a avaliar:** Stock Requirement recebe somente `stockItemId` e quantidade; a Estimate consulta os dados
  canônicos e congela seu snapshot comercial na criação.
- **Pontos para discovery:** porta consumidora, tratamento de item inativo, disponibilidade informativa, contrato HTTP e
  gatilho de Stock Reservation após aprovação.

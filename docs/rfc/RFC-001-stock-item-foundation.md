# RFC-001 — Fundação de Stock Item

| Campo | Valor |
|---|---|
| Status | Accepted |
| Data | 2026-08-15 |
| Contextos | Stock & Procurement; Service Lifecycle |

## Contexto

O termo Stock Item surgiu no Domain Storytelling para representar peças, consumíveis e insumos durante diagnóstico,
orçamento e execução. O modelo anterior tratava `Stock` como aggregate com ID e continha apenas um scaffolding de
`Part`, sem esclarecer a propriedade de Stock Requirement, snapshots e reservas.

## Decisões

- Stock é a capability; não existe entidade ou `stockId` no MVP.
- Stock Item é o conceito unificado e possui tipo obrigatório `PART`, `CONSUMABLE` ou `SUPPLY`.
- Stock Requirement pertence a Service Lifecycle e referencia Stock Item por ID e quantidade.
- O Technician parte da lista ampla de Stock Items ativos e a restringe com filtros combináveis.
- A disponibilidade consultada é informativa e não reserva saldo.
- A futura Estimate congela seu snapshot comercial na criação.
- A reserva efetiva cria uma Stock Reservation vinculada à Service Execution.
- O primeiro incremento implementa somente catálogo, busca, atualização e desativação de Stock Items.
- O primeiro incremento persiste somente a quantidade disponível inicial; contador reservado, nível mínimo e demais
  dados de inventário nascem com as features que implementarem seus comportamentos.

## Entregas separadas

- operações de inventário e baixo estoque;
- ciclo de Stock Reservation;
- integração de Stock Requirement e Estimate;
- Procurement e Purchase Order;
- revisão e versionamento de Estimate.

Essas entregas estão rastreadas em `docs/backlog.md` e exigirão suas próprias specs quando priorizadas.

## Consequências

- o scaffolding `/api/parts` poderá ser substituído sem compatibilidade;
- nenhum aggregate artificial será criado para agrupar todos os Stock Items;
- a consulta ampla será uma operação de coleção com filtros opcionais, não um endpoint diferente para cada filtro;
- as decisões permanecem válidas mesmo que a implementação seja dividida em várias features.

## Referências

- `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`;
- `docs/backlog.md`;
- [Ubiquitous Language no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684049703).

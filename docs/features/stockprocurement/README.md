# Mapa de Evolução de Stock & Procurement

Este documento preserva o contexto compartilhado entre as features de Stock & Procurement. Ele é um índice de discovery
e dependências, não substitui `functional-spec.md`, `technical-spec.md`, `implementation-plan.md` nem aprovação humana.

## Fontes de contexto

- [RF25–RF30 no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679722227775);
- [Domain Storytelling de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831);
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720);
- [Context Map no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684515255);
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027);
- [Aggregates atualizados no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870345674);
- `docs/rfc/RFC-001-stock-item-foundation.md`;
- `docs/backlog.md`, especialmente BL-002, BL-003 e BL-004.

## Sequência funcional

1. Stock Item Foundation mantém o catálogo e a disponibilidade atual.
2. Diagnosis e geração de Estimate observam a disponibilidade e registram a necessidade concreta de compra sem reservar
   unidades.
3. Stock Item Reservation tenta comprometer atomicamente os requirements de uma Service Execution autorizada.
4. Uma insuficiência pode originar ou atualizar uma Purchase Demand `PENDING_REPAIR` já no Diagnosis; rejeição ou
   expiração comercial não a resolve.
5. RF30 poderá originar uma Purchase Demand `LOW_STOCK` a partir de nível mínimo e alvo de reposição aprovados.
6. RF27 permite ao Stock Manager criar uma Purchase Order ad hoc, a partir de demandas ou combinando as duas formas.
7. O External Supplier System confirma a ordem e devolve uma referência externa.
8. RF28 fecha a Purchase Order quando a entrega for confirmada pelo Stock Manager.
9. RF29 registra o recebimento, aumenta a disponibilidade e alimenta novas tentativas de reserva.
10. A priorização posterior decide quais Service Executions pendentes tentar atender primeiro.

## Recorte das features

| Feature | Responsabilidade | Situação |
|---|---|---|
| `stock-domain-foundation` | Catálogo e consulta de Stock Items | Implemented |
| `stock-item-reservation` | Reserva atômica, consulta e consumo | Implemented |
| RF27 — `purchase-order-creation` | Demandas, criação manual e ordem externa `OPEN` | Implemented |
| RF30 — low stock | Nível mínimo, alvo e detecção de baixo estoque | Spec futura |
| RF28 — close Purchase Order | Confirmação de entrega e fechamento da ordem | Spec futura |
| RF29 — receive and restock | Entrada de materiais, saldo e reação das reservas | Spec futura |

Cada linha futura exige seu próprio ciclo completo de SDD. A situação desta tabela não aprova comportamento nem permite
implementar placeholders.

## Decisões compartilhadas a preservar

- Stock é uma capability, não uma entidade ou Aggregate Root com ID.
- Stock Item é a referência canônica dos materiais e usa `PART`, `CONSUMABLE` ou `SUPPLY`.
- Stock Requirement pertence a Service Lifecycle; Stock Reservation e Purchase Order pertencem a Stock & Procurement.
- Purchase Demand representa uma necessidade de compra, não uma ordem já enviada ao fornecedor.
- Gatilhos de reparo pendente ou baixo estoque não criam Purchase Order automaticamente.
- O Stock Manager pode criar uma ordem inteiramente ad hoc e pode combinar linhas livres com demandas selecionadas.
- Purchase Order não possui vínculo direto com Service Order. A eventual origem de reparo fica na Purchase Demand.
- Materiais recebidos podem atender Service Orders diferentes daquela que originou a compra, conforme prioridade futura.
- External Supplier System é upstream; Stock & Procurement protege o domínio com Gateway e Anti-Corruption Layer.
- RF27 não altera saldo; RF28 não deve assumir recebimento; RF29 é responsável pela entrada no Stock.

## Dependências e pontos para as próximas discoveries

### RF30 — Identificar baixo estoque

- definir quem configura `minimumQuantity` e se existe `targetQuantity` distinto;
- decidir quando a condição é avaliada e como uma ocorrência deixa de estar aberta;
- evitar demandas duplicadas enquanto o item permanecer abaixo do limite;
- definir a quantidade de reposição sugerida entregue a RF27.

### RF28 — Fechar Purchase Order

- definir se fechar significa confirmar entrega integral;
- decidir como tratar rejeição, cancelamento, divergência e entrega parcial;
- preservar a referência externa e a imutabilidade das linhas confirmadas.

### RF29 — Receber e repor

- registrar uma movimentação rastreável em vez de atualizar saldo por CRUD;
- definir idempotência do recebimento e proteção contra entrada duplicada;
- decidir a ordem de retry das Service Executions em `AWAITING_ITEMS`;
- preservar a decisão do Miro de não reservar previamente o material futuro para uma Service Order específica.

## Demonstração do External Supplier System

RF27 usa um simulador HTTP mínimo e determinístico com WireMock `3.13.1`, mappings versionados em
`docker/wiremock/mappings` e inicialização pelo Docker Compose. Isso exercita o cliente HTTP e a Anti-Corruption Layer
reais sem criar uma segunda aplicação, banco, autenticação ou domínio de Supplier.

O simulador demonstra pelo Postman:

- criação aceita com referência externa estável;
- rejeição funcional de produto ou quantidade;
- retry idempotente após resposta perdida ou timeout.

Os mesmos cenários são cobertos com um servidor WireMock isolado nos testes. O adapter aceita somente hosts locais no
MVP; fornecedor real e credenciais permanecem bloqueados até existir autenticação/autorização.

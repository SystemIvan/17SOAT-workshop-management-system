# Especificação Funcional: Mapeamento do status da OS para os 6 estados nominais da Fase 2

| Campo | Valor |
|---|---|
| Feature | `status-nominal-mapping` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-21 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-21 |
| Referências | RF39; enunciado do Tech Challenge Fase 2; `docs/features/servicelifecycle/service-order-status-projection/functional-spec.md` (define `statusSnapshot` e seus 7 valores, que esta feature reaproveita sem alterar); `.claude/rules/epic-3-service-lifecycle.md` |

## Nota sobre a origem do requisito

RF39 é um requisito novo da Fase 2 do Tech Challenge, ainda **não registrado no board do Miro** — assim
como RF38 (`list-service-orders-sorting`), essa atualização do board é uma task separada, sem gate de
`functional-spec.md`. Até essa task ser concluída, a única fonte deste requisito é o enunciado da Fase 2.

## Problema e resultado esperado

O enunciado da Fase 2 pede 6 estados nominais para a Ordem de Serviço: **Recebida**, **Diagnóstico**,
**Aguardando Aprovação**, **Execução**, **Finalizada**, **Entregue**. O domínio atual
(`ServiceOrderStatus`, projetado em `statusSnapshot` conforme `service-order-status-projection`) tem 7
valores: `RECEIVED`, `IN_DIAGNOSIS`, `AWAITING_APPROVAL`, `AWAITING_ITEMS`, `IN_PROGRESS`, `COMPLETED`,
`DELIVERED`. Um consumidor externo (portal do cliente, integração) que só conhece o enunciado não tem hoje
nenhuma forma de interpretar a OS nesses 6 termos sem conhecer os 7 valores internos do domínio.

Resultado esperado: `GET /api/service-orders/{id}/status` e a listagem (`GET /api/service-orders`) passam
a expor, além do que já expõem hoje, uma representação do status compatível com os 6 nomes do enunciado,
por meio de um mapeamento explícito — nunca removendo ou renomeando um estado interno do domínio.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Consumidor externo (portal do cliente, integração) | Consulta o status de uma OS e recebe um dos 6 nomes do enunciado, sem precisar conhecer os 7 valores internos do domínio. |
| Consumidor externo | Consulta a listagem de OS e cada item traz a mesma representação de 6 estados usada na consulta individual. |
| Manager / Service Advisor / Technician | Continuam usando os campos internos já existentes (`status`, `statusSnapshot`) sem qualquer mudança de comportamento, para as operações internas já implementadas. |

### Cenário principal — consulta individual

1. Uma OS está com `statusSnapshot` `AWAITING_ITEMS` (aguardando item de estoque durante uma execução).
2. O consumidor externo chama `GET /api/service-orders/{id}/status`.
3. A resposta traz, além do que já existe hoje, a representação nominal **"Execução"** — o estado interno
   `AWAITING_ITEMS` não é removido nem alterado, apenas mapeado para o nome externo mais próximo.

### Cenário alternativo — todos os 7 valores internos são cobertos

1. Para cada um dos 7 valores possíveis de `statusSnapshot`, o mapeamento produz sempre um dos 6 nomes do
   enunciado — nunca um valor ausente ou não mapeado.

## Regras de negócio

1. **Mapeamento de `statusSnapshot` para o nome nominal externo** (decisão de produto tomada nesta sessão,
   opção (a) do requisito — ver seção "Decisão registrada" abaixo):

   | `statusSnapshot` interno | Nome nominal externo (enunciado Fase 2) |
   |---|---|
   | `RECEIVED` | Recebida |
   | `IN_DIAGNOSIS` | Diagnóstico |
   | `AWAITING_APPROVAL` | Aguardando Aprovação |
   | `IN_PROGRESS` | Execução |
   | `AWAITING_ITEMS` | Execução |
   | `COMPLETED` | Finalizada |
   | `DELIVERED` | Entregue |

2. O mapeamento é aplicado a partir do `statusSnapshot` já projetado por `service-order-status-projection`
   — esta feature não altera a lógica de precedência que calcula `statusSnapshot`, apenas adiciona uma
   segunda representação de apresentação em cima dele.
3. O mapeamento é total e determinístico: todo valor de `ServiceOrderStatus` tem exatamente um nome
   nominal externo correspondente; não existe valor interno sem mapeamento.
4. O estado interno `AWAITING_ITEMS` e sua regra de negócio (bloqueio da execução até reserva de estoque)
   continuam existindo e sendo usados normalmente nas operações internas (ex.: ordenação da listagem
   definida em `list-service-orders-sorting`, regras de `attach-stock-requirement`/`retry-stock-reservation`).
   Apenas a representação exposta ao consumidor externo funde `AWAITING_ITEMS` em "Execução".
5. Os campos HTTP já existentes (`status`, depreciado, e `statusSnapshot`) continuam sendo retornados sem
   alteração de nome, tipo ou semântica — esta feature é aditiva, não um breaking change. (Ver
   "Fora de escopo".)

## Decisão registrada — tratamento de `AWAITING_ITEMS`

O requisito (RF39, ver descrição colada pelo responsável) exigia decidir explicitamente entre duas opções
antes de qualquer implementação:

- (a) mapear `AWAITING_ITEMS` para "Execução" na resposta externa, preservando o valor interno para uso
  operacional; ou
- (b) manter `AWAITING_ITEMS` como um 7º valor exposto, com justificativa da divergência do enunciado.

**Decisão: opção (a).** Confirmada por Santiago Silvestre em 2026-09-21. `AWAITING_ITEMS` é mapeado para
"Execução" na representação nominal externa; o valor interno `AWAITING_ITEMS` não é removido do domínio
nem do `statusSnapshot` já existente, e continua orientando a ordenação/regras internas.

## Fora de escopo

- alterar a lógica de precedência de `statusSnapshot` definida em `service-order-status-projection`;
- remover, renomear ou alterar o tipo/semântica dos campos HTTP já existentes `status` e `statusSnapshot`
  (nenhuma mudança incompatível de contrato é aprovada por esta especificação);
- expor o nome nominal externo em endpoints além de `GET /api/service-orders/{id}/status` e
  `GET /api/service-orders` — outros endpoints de comando (ex.: `finalize`, `assign-technician`) não são
  alterados;
- traduzir ou internacionalizar qualquer outro texto da API além dos 6 nomes nominais definidos aqui;
- alterar o estado individual ou as transições permitidas de uma `ServiceExecution`;
- qualquer migração de dado — é mapeamento de apresentação, não muda o schema persistido
  (`ServiceOrderJpaEntity` / `statusSnapshot` continuam armazenando os 7 valores internos);
- registrar RF39 no board do Miro (task separada, ver nota acima).

## Critérios de aceite

- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `RECEIVED` expõe o nome
      nominal "Recebida".
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `IN_DIAGNOSIS` expõe
      "Diagnóstico".
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `AWAITING_APPROVAL` expõe
      "Aguardando Aprovação".
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `IN_PROGRESS` expõe
      "Execução".
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `AWAITING_ITEMS` expõe
      "Execução" (mesmo nome nominal de `IN_PROGRESS`, decisão (a) registrada acima), sem que o valor
      interno `AWAITING_ITEMS` deixe de existir em `statusSnapshot`.
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `COMPLETED` expõe
      "Finalizada".
- [ ] `GET /api/service-orders/{id}/status` para uma OS com `statusSnapshot` `DELIVERED` expõe "Entregue".
- [ ] `GET /api/service-orders` aplica o mesmo mapeamento a cada item da listagem retornada.
- [ ] Os campos `status` e `statusSnapshot` continuam presentes e inalterados nas duas respostas.
- [ ] Nenhum valor de `ServiceOrderStatus` fica sem representação nominal externa (mapeamento total).

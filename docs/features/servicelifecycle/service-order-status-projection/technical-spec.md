# Especificação Técnica: Corrigir projeção de status da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-status-projection` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

A mudança fica no aggregate `ServiceOrder`, dentro de `servicelifecycle`. Ela não altera os estados nem comandos de
`ServiceExecution`; corrige apenas a projeção persistida e os contratos de leitura que a apresentam.

`recomputeStatusSnapshot` passa a avaliar, nesta ordem:

1. preservar `DELIVERED` como estado terminal de maior precedência;
2. retornar `COMPLETED` quando existir ao menos uma execução e todas estiverem em `COMPLETED` ou `REJECTED`;
3. retornar `IN_PROGRESS` quando houver execução `READY` ou `IN_PROGRESS`;
4. retornar `AWAITING_ITEMS` quando houver execução nesse estado;
5. retornar `AWAITING_APPROVAL` quando houver Estimate enviada com linhas pendentes;
6. retornar `IN_DIAGNOSIS` quando `openDiagnosisId` estiver preenchido;
7. retornar `RECEIVED` nos demais casos.

O helper atual `allNonRejectedExecutionsCompleted()` será substituído por uma verificação de estados terminais que
também cobre o caso de todas rejeitadas. Uma lista vazia continua não satisfazendo `COMPLETED`.

Mapear `READY` para o resumo `IN_PROGRESS` não chama `ServiceExecution.start()` e não altera o estado individual.

## Interfaces e fluxo de dados

### Response compatível

`ServiceOrderResponse` passa a expor os dois campos durante a transição:

```json
{
  "status": "IN_PROGRESS",
  "statusSnapshot": "IN_PROGRESS",
  "executions": [
    { "status": "READY" },
    { "status": "AWAITING_ITEMS" }
  ]
}
```

- `statusSnapshot` é o nome canônico novo.
- `status` permanece como alias compatível, contém sempre o mesmo valor e é marcado como deprecated no OpenAPI.
- `ServiceOrderMapper` obtém o status uma vez e preenche ambos os componentes.
- `ServiceOrderStatusResponse` do `GET /api/service-orders/{id}/status` mantém `status`, sem mudança de schema.
- Nenhum endpoint novo é criado e nenhum campo existente é removido.

Todos os use cases que retornam `ServiceOrderResponse` refletem a projeção corrigida. Os comandos continuam sendo os
únicos responsáveis por recalcular e persistir o snapshot; queries permanecem somente leitura.

### Falhas

Não há nova categoria de falha HTTP. Consultas inexistentes continuam retornando `404 NOT_FOUND`, e conflitos dos
comandos que originam os estados continuam usando `409 INVALID_STATE_TRANSITION`.

## Persistência e dados de bootstrap

Não há coluna nova. Uma migration Flyway versionada deve recalcular `service_orders.status_snapshot` já persistido para
evitar que linhas legadas permaneçam com a semântica anterior.

A atualização em lote deve reproduzir a precedência do domínio por `CASE` e subconsultas sobre
`service_executions`:

1. preservar linhas cujo snapshot já seja `DELIVERED`;
2. marcar `COMPLETED` quando houver execução e não houver execução fora de `COMPLETED`/`REJECTED`;
3. marcar `IN_PROGRESS` quando houver `READY`/`IN_PROGRESS`;
4. aplicar `AWAITING_ITEMS`, `AWAITING_APPROVAL`, `IN_DIAGNOSIS` e `RECEIVED` na mesma ordem do domínio.

O SQL deve ser compatível com MySQL e com o H2 em modo MySQL usado nos testes. A migration é forward-only; rollback da
aplicação não deve tentar restaurar snapshots antigos e semanticamente incorretos.

Classificação de dados: **no seed required**. A migration corrige uma projeção operacional derivada, sem criar dados de
referência, demonstração ou fixture.

## Segurança e operação

- A feature não amplia quais dados da SO são expostos; adiciona somente um alias do status já público no contrato.
- O detalhe de execuções continua necessário para não induzir o consumidor a tratar o snapshot como estado de cada
  serviço.
- A API não registra payloads nem dados de Customer como parte do recálculo.
- Autenticação e autorização dos endpoints existentes permanecem fora de escopo; não é criado novo acesso.
- A migration pode atualizar todas as Service Orders e deve ser testada com volume representativo. Antes do deploy,
  revisar plano de execução e tempo de lock no MySQL.
- A mudança semântica de `READY` e de todas rejeitadas foi aprovada funcionalmente e deve aparecer nas notas de rollout.

## Estratégia de testes

- Domínio: uma tabela de testes cobre cada ramo isolado e todas as combinações relevantes de precedência.
- Limites: nenhuma execução permanece `RECEIVED`; todas rejeitadas ficam `COMPLETED`; mistura de concluídas e rejeitadas
  fica `COMPLETED`; qualquer execução não terminal impede esse resultado.
- Precedência: `DELIVERED` é preservado; `READY`/`IN_PROGRESS` vence `AWAITING_ITEMS`; os demais estados seguem a ordem
  aprovada.
- Aplicação/HTTP: consultas resumida e detalhada retornam a projeção corrigida; resposta detalhada contém alias igual,
  `statusSnapshot` e status individual divergente quando aplicável.
- Persistência/migration: fixtures anteriores ao upgrade são recalculadas e a aplicação inicia com
  `ddl-auto=validate`.
- Contrato: OpenAPI inclui `statusSnapshot`, mantém `status` deprecated e Postman documenta os dois valores.
- Regressão: `start-execution` continua necessário para mudar `READY` para `IN_PROGRESS` na execução.
- Qualidade: executar `make test`, `make verify`, revisar cobertura e manter `ModuleStructureTest` verde.

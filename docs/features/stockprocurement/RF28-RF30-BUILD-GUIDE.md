# Guia de retomada — RF28, RF29 e RF30

Use este arquivo para retomar a implementação em uma sessão sem histórico. As specs e os planos referenciados abaixo
são a fonte de verdade; este guia não os substitui.

## Estado aprovado

- RF28, RF29 e RF30 possuem `functional-spec.md` e `technical-spec.md` aprovadas por Matheus Apostulo em 2026-08-25.
- Os três `implementation-plan.md` estão em `Draft`, com todos os checkpoints `Pending`.
- Nenhum código ou migration dessas features foi iniciado.
- A branch documental atual é `feat/stockprocurement-rf28-rf30-sdds`.

## Ordem de implementação

### 1. RF28 + RF29

Criar a partir de `dev` atualizado:

```text
feat/stockprocurement-purchase-order-receiving
```

Ler nesta ordem:

1. `purchase-order-closing/functional-spec.md`;
2. `purchase-order-closing/technical-spec.md`;
3. `purchase-order-closing/implementation-plan.md`;
4. `stock-receiving-and-restocking/functional-spec.md`;
5. `stock-receiving-and-restocking/technical-spec.md`;
6. `stock-receiving-and-restocking/implementation-plan.md`.

Executar todos os checkpoints de RF28 antes de habilitar o endpoint de Receipt de RF29.

Decisões imutáveis sem nova aprovação:

- RF28 faz `OPEN → CLOSED`, registra autor/instante e não altera saldo;
- fechamento é integral, terminal e idempotente;
- RF29 aceita somente Purchase Order `CLOSED` e deriva todas as linhas da ordem;
- existe no máximo um Stock Receipt por Purchase Order;
- Receipt soma todos os saldos atomicamente e aceita item inativo sem reativá-lo;
- retries ocorrem after-commit em `URGENT`, `HIGH`, `NORMAL`, `LOW`;
- material não fica prometido à Service Order que originou a compra.

### 2. RF30

Depois de RF28/RF29 integradas em `dev`, criar:

```text
feat/stockprocurement-low-stock-detection
```

Ler nesta ordem:

1. `low-stock-detection/functional-spec.md`;
2. `low-stock-detection/technical-spec.md`;
3. `low-stock-detection/implementation-plan.md`.

Decisões imutáveis sem nova aprovação:

- policy opcional por Stock Item com `minimumQuantity` e `targetQuantity`;
- baixo estoque usa `availableQuantity < minimumQuantity`;
- sugestão usa `targetQuantity - availableQuantity`;
- existe no máximo uma ocorrência aberta por Stock Item;
- a mesma ocorrência não duplica Purchase Demand ou sinalização;
- Receipt do ciclo pode encerrar a ocorrência e abrir outra se o saldo continuar baixo;
- RF30 cria demanda `LOW_STOCK`, nunca Purchase Order automática;
- não existem scheduler, policy global ou bounded context Notifications.

## Protocolo de execução

1. Ler o `AGENTS.md` da raiz e os três documentos da feature em execução.
2. Confirmar `git status --short --branch` e preservar mudanças alheias.
3. Confirmar que functional e technical specs continuam `Approved`.
4. Manter somente um checkpoint `In Progress` por vez.
5. Implementar, testar e registrar evidência antes de marcar o checkpoint `Completed`.
6. Se surgir mudança material, devolver a spec afetada para `Draft` e pedir nova aprovação.
7. Não alterar migration já aplicada e não criar dados de negócio em seed de produção.

## Gates obrigatórios

- testes de domínio, aplicação, HTTP, persistência e concorrência previstos no plano;
- `ModuleStructureTest` e `@ApplicationModuleTest` quando houver interação entre módulos;
- OpenAPI, Postman e README atualizados junto de qualquer mudança HTTP;
- revisão de segurança preenchida, sem finding crítico/alto aberto;
- `make test` durante o desenvolvimento;
- `make verify` antes da conclusão;
- `make coverage` e revisão da meta de 80%;
- plano marcado `Implemented` somente com todas as evidências registradas.

## Resultado esperado por branch

```text
purchase-order-receiving
    RF28: OPEN → CLOSED, sem saldo
    RF29: CLOSED → Receipt → saldo → retries priorizados

low-stock-detection
    policy → occurrence → LOW_STOCK demand → decisão manual de compra
```


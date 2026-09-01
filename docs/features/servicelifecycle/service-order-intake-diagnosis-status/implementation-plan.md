# Plano Agregado de Execução: Entrada, diagnóstico e status da Service Order

| Campo | Valor |
|---|---|
| Natureza | Coordenação de quatro features; não cria uma quinta feature |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Branch | `feat/servicelifecycle-service-order-intake-diagnosis-status` |
| Origem | `docs/rfc/RFC-002-service-order-intake-diagnosis-status-plan.md` |

## Objetivo

Coordenar a implementação das quatro features aprovadas pela RFC-002 sem fundir suas regras, contratos, migrations ou
evidências. As especificações e os planos individuais continuam sendo as fontes de verdade do comportamento.

Este documento controla somente dependências, ordem global, gate compartilhado de documentação e validação integrada.
Nenhuma decisão funcional ou técnica nova pode ser introduzida aqui.

## Planos coordenados

| Ordem | Feature | Plano individual | Dependência |
|---:|---|---|---|
| 1 | `service-order-initial-assessment` | `../service-order-initial-assessment/implementation-plan.md` | Gate 0 |
| 2 | `assign-diagnosis-assignee` | `../assign-diagnosis-assignee/implementation-plan.md` | Feature 1 |
| 3 | `diagnosis-authorship` | `../diagnosis-authorship/implementation-plan.md` | Feature 2 |
| 4 | `service-order-status-projection` | `../service-order-status-projection/implementation-plan.md` | Feature 3 |

A dependência entre as features 1 e 2 é operacional, para manter a branch sempre testável. A dependência rígida está
entre atribuição e autoria: `diagnosis-authorship` reutiliza a precondição e o lock implementados por
`assign-diagnosis-assignee`. A projeção é executada por último para validar o conjunto final de estados.

## Estado global

| Etapa | Resultado esperado | Status |
|---:|---|---|
| 0 | SDDs existentes reconciliados e aprovados quando necessário | Completed |
| 1 | Triagem inicial implementada e verificada | Completed |
| 2 | Responsável planejado implementado e verificado | Completed |
| 3 | Autoria efetiva implementada e verificada | Completed |
| 4 | Projeção de status implementada e verificada | Completed |
| 5 | Validação integrada, segurança e documentação concluídas | Completed |

## Regras de execução

- Ler integralmente o `AGENTS.md`, a RFC, este plano e o plano individual antes de iniciar cada etapa.
- Confirmar a branch e executar `git status --short`, preservando mudanças alheias.
- Manter no máximo um checkpoint individual como `In Progress`.
- Atualizar o plano individual no mesmo momento em que a etapa agregada mudar de estado.
- Não marcar uma etapa global como concluída enquanto todos os checkpoints individuais correspondentes estiverem
  concluídos e com evidência.
- Mudança material em spec aprovada retorna o documento a `Draft`, invalida downstream e interrompe código afetado.
- Gerar cada timestamp Flyway em UTC no início do checkpoint de persistência; nunca editar migration já aplicada.
- Usar `./mvnw` pelos targets do `Makefile`; não depender de Maven global.

## Etapa 0 — Reconciliação compartilhada dos SDDs existentes

Executar esta etapa uma única vez para evitar reabrir repetidamente os mesmos documentos:

1. Consolidar todos os deltas aprovados nas specs existentes de:
   - `service-order-creation`;
   - `perform-diagnosis`;
   - `track-execution`.
2. Adicionar referências às quatro novas features, sem copiar integralmente suas regras.
3. Atualizar request, response, precondições e semântica de status que ficariam desatualizados.
4. Classificar cada mudança como material ou apenas rastreabilidade.
5. Para mudança material, aplicar `Draft`, invalidar documentos downstream e obter aprovação humana na ordem SDD.
6. Registrar a mesma evidência nos checkpoints 0 dos quatro planos individuais.

Nenhuma alteração de código começa antes da conclusão desta etapa.

## Etapas 1 a 4 — Execução das features

### Etapa 1 — Triagem inicial

Executar integralmente o plano `service-order-initial-assessment`. Ao final, novas Service Orders exigem e persistem
`initialAssessment`, enquanto registros legados permanecem legíveis com valor nulo.

### Etapa 2 — Responsável planejado

Executar integralmente o plano `assign-diagnosis-assignee`. Ao final, existe atribuição/reatribuição por endpoint, lock
da Service Order e bloqueio de Diagnosis sem responsável planejado.

### Etapa 3 — Autoria efetiva

Executar integralmente o plano `diagnosis-authorship`. Ao final, cada lote registra autor declarado e instante único,
sem inferir autoria da atribuição planejada.

### Etapa 4 — Projeção de status

Executar integralmente o plano `service-order-status-projection`. Ao final, snapshots novos e legados seguem a
precedência aprovada, e `statusSnapshot` é exposto sem remover `status`.

## Etapa 5 — Validação integrada e conclusão

- Executar o fluxo ponta a ponta:
  1. criar SO com `initialAssessment`;
  2. atribuir `diagnosisAssigneeId`;
  3. registrar Diagnosis com `diagnosedByTechnicianId` diferente do planejamento;
  4. consultar autor, instante, planejamento, snapshot e estados individuais;
  5. decidir linhas para validar `READY`, `AWAITING_ITEMS` e todas rejeitadas.
- Confirmar a ordem e a imutabilidade das quatro migrations novas.
- Executar `make test`, `make coverage` e `make verify`.
- Confirmar `ModuleStructureTest` verde e ausência de dependências cíclicas.
- Revisar o OpenAPI gerado e executar as assertions de `OpenApiContractTest`.
- Validar a coleção Postman como JSON e executar o fluxo manual quando o ambiente estiver disponível.
- Consolidar as quatro revisões de segurança e confirmar que não resta achado crítico/alto.
- Atualizar README/arquitetura somente quando houver mudança estrutural observável.
- Marcar cada feature `Implemented` antes de marcar este plano agregado `Implemented`.

## Ordem esperada das migrations

Os timestamps reais serão definidos durante a implementação, mantendo esta ordem lógica:

1. `add_initial_assessment_to_service_orders`;
2. `add_diagnosis_assignee_to_service_orders`;
3. `add_diagnosis_authorship_to_service_executions`;
4. `recompute_service_order_status_snapshots`.

A migration de status deve ser a última porque recalcula dados depois que todo o modelo novo já estiver disponível.

## Evidências globais

| Data | Etapa | Evidência | Resultado |
|---|---|---|---|
| 2026-08-22 | 0 | `service-order-creation`, `perform-diagnosis` e `track-execution` foram reconciliadas com links aos deltas aprovados; requests, responses, precondições e projeção foram atualizados sem duplicar as regras das quatro features. | Três mudanças materiais classificadas e reaprovadas por Matheus Apostulo em 2026-08-22, na ordem funcional → técnica. Os planos históricos permanecem `Stale` por não cobrirem os deltas; os quatro planos novos estão liberados para a Etapa 1. |
| 2026-08-22 | 1 | `initialAssessment` obrigatório foi implementado de ponta a ponta: domínio, DTOs, HTTP/OpenAPI, Postman, persistência e migration `V20260822164906__add_initial_assessment_to_service_orders.sql`. Registros legados continuam legíveis com valor nulo. | Testes focados, `make test`, `make coverage` (93,54% de linhas) e `make verify` passaram; `ModuleStructureTest` verde e revisão de segurança sem achado crítico/alto. |
| 2026-08-22 | 2–4 | Atribuição planejada, autoria efetiva e projeção de status foram implementadas com migrations ordenadas `V20260822170252`, `V20260822173918` e `V20260822180858`. O fluxo preserva a atribuição, registra autor/instante por lote e expõe `statusSnapshot` mantendo `status` deprecated. | Testes de domínio, aplicação, MockMvc, JPA/Flyway e lock concorrente passaram. `make test`, `make coverage` e `make verify` verdes; cobertura: 92,75%; `ModuleStructureTest` verde. |
| 2026-08-22 | 5 | Revisão integrada de segurança, contratos e dados concluída. | Sem achado crítico/alto aberto. Requests dedicados e Bean Validation limitam mass assignment; autoria é declaratória e o risco médio sem autenticação permanece registrado; migrations são aditivas/forward-only e a atualização em lote de status requer janela monitorada. Nenhuma alteração arquitetural estrutural exigiu README adicional. |

Registrar aqui apenas evidências integradas. Evidências detalhadas permanecem nos planos individuais.

## Estratégia de commits

Manter commits coesos e na mesma ordem das etapas, usando Conventional Commits. Sugestão de divisão:

1. `docs(servicelifecycle): align service order intake diagnosis specs`;
2. `feat(servicelifecycle): require service order initial assessment`;
3. `feat(servicelifecycle): assign diagnosis technician`;
4. `feat(servicelifecycle): record diagnosis authorship`;
5. `fix(servicelifecycle): correct service order status projection`;
6. `test(servicelifecycle): verify intake diagnosis status flow`.

Não misturar limpeza ou formatação não relacionada nesses commits.

## Rollback ou recuperação coordenada

- Preferir `git revert` por commit/feature, preservando histórico.
- Não remover colunas aditivas durante rollback da aplicação; isso evita perda de triagem, atribuição e autoria.
- Não reverter a correção de snapshots com SQL destrutivo. Uma mudança semântica futura exige nova migration
  forward-only e novo SDD.
- Se uma etapa falhar antes de deploy, manter as etapas anteriores verificadas e retornar somente o plano afetado a
  `In Progress`.

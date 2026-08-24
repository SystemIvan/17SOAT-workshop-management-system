# Plano de Implementação: Corrigir projeção de status da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-status-projection` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Branch | `feat/servicelifecycle-service-order-intake-diagnosis-status` |
| Plano agregado | `../service-order-intake-diagnosis-status/implementation-plan.md` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-22) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-22) |

## Objetivo e ordem

Corrigir a precedência do snapshot, migrar projeções já armazenadas e expor `statusSnapshot` sem remover `status`.
Executar por último para validar a projeção contra os estados finais produzidos pelas outras três features.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Reconciliar documentação afetada | Completed |
| 1 | Corrigir regra de domínio | Completed |
| 2 | Migrar snapshots persistidos | Completed |
| 3 | Implementar response compatível e documentação | Completed |
| 4 | Validar matriz de precedência e regressão | Completed |
| 5 | Concluir segurança e gates finais | Completed |

## Checkpoint 0 — Reconciliar documentação afetada

- Atualizar os SDDs de `track-execution` com referência ao delta aprovado e ao alias compatível.
- Atualizar specs de comandos somente quando afirmarem uma projeção conflitante.
- Aplicar `Draft`/stale e nova aprovação humana se uma mudança material for necessária.
- Registrar nas notas de rollout as mudanças deliberadas para `READY` e todas rejeitadas.

Evidência: links, status e aprovações dos documentos reconciliados.

## Checkpoint 1 — Corrigir regra de domínio

- Refatorar `recomputeStatusSnapshot` para preservar `DELIVERED` e seguir exatamente a precedência aprovada.
- Substituir `allNonRejectedExecutionsCompleted` por regra terminal com lista não vazia.
- Mapear `READY` ou `IN_PROGRESS` para snapshot `IN_PROGRESS`, sem chamar `start()`.
- Garantir que todos os comandos que alteram condições continuem recalculando o snapshot uma única vez.

Evidência: tabela de testes unitários por ramo e combinação.

## Checkpoint 2 — Migrar snapshots persistidos

- Criar `VyyyyMMddHHmmss__recompute_service_order_status_snapshots.sql` com timestamp UTC.
- Implementar `CASE` e subconsultas na ordem definida, preservando `DELIVERED`.
- Validar MySQL e H2 em modo MySQL, inclusive SO vazia, todas rejeitadas, mistura terminal e estados simultâneos.
- Avaliar duração e locks da atualização em lote antes do deploy.
- Classificação: **no seed required**; a migration corrige somente projeção operacional derivada.

Evidência: teste de upgrade, plano SQL revisado e resultado de startup com Hibernate validate.

## Checkpoint 3 — Implementar response compatível e documentação

- Adicionar `statusSnapshot` a `ServiceOrderResponse` e manter `status` com o mesmo valor.
- Marcar `status` como deprecated no OpenAPI sem removê-lo.
- Manter `ServiceOrderStatusResponse` inalterado.
- Atualizar todos os mappers, assertions OpenAPI e exemplos Postman.
- Confirmar que nenhuma entidade de domínio/JPA é exposta.

Evidência: testes de igualdade dos aliases e contrato gerado.

## Checkpoint 4 — Validar matriz de precedência e regressão

- Cobrir todos os critérios funcionais no domínio, aplicação e MockMvc.
- Incluir `READY` com `AWAITING_ITEMS`, `IN_PROGRESS` com Diagnosis aberto e todos os estados terminais.
- Provar que SO sem execução permanece `RECEIVED` e todas rejeitadas ficam `COMPLETED`.
- Provar que `start-execution` continua necessário para alterar o status individual.
- Executar testes focados, `make test` e `make coverage`.

Evidência: matriz de cenários, comandos e resultados.

## Checkpoint 5 — Concluir segurança e gates finais

### Revisão de segurança planejada

- **Entrada/mass assignment:** N/A — a feature não adiciona request mutável.
- **Autenticação/autorização:** risco pré-existente; nenhum endpoint novo ou ampliação de acesso.
- **Exposição:** alias do status já exposto; manter detalhe de execução para evitar interpretação enganosa.
- **Segredos/logs:** N/A — recálculo não manipula segredos nem exige novo log.
- **SQL/migration:** revisar tempo, locks, atomicidade e recuperação da atualização em lote.
- **Erros/disclosure:** nenhuma nova falha; manter respostas existentes sem detalhes internos.
- **Dependências:** N/A — nenhuma dependência nova.
- **Abuso:** N/A para comando; consultas continuam sujeitas aos controles de plataforma existentes.

Não há achado crítico/alto identificado no desenho. Reavaliar com migration e contrato prontos.

- Executar `make verify`, confirmar cobertura e `ModuleStructureTest` verde.
- Atualizar documentação arquitetural apenas se houver mudança estrutural.
- Marcar `Implemented` somente após todos os checkpoints e evidências.

## Evidências de verificação

- 2026-08-22 — A reconciliação do plano agregado atualizou `track-execution` com o alias compatível
  `status`/`statusSnapshot`, a precedência aprovada para `READY` e todas rejeitadas, e a classificação **material**.
  A funcional anterior voltou a `Draft`; a técnica e o plano históricos permanecem `Stale`. As notas de rollout desta
  feature continuam sendo a fonte para as duas mudanças deliberadas. Reaprovação funcional e técnica registrada para
  Matheus Apostulo em 2026-08-22; checkpoint concluído.
- 2026-08-22 — `recomputeStatusSnapshot` preserva `DELIVERED`, reconhece uma lista não vazia de execuções terminais
  (`COMPLETED` ou `REJECTED`) e dá precedência a `READY`/`IN_PROGRESS` sobre `AWAITING_ITEMS`. Os testes de domínio
  cobrem SO vazia, READY, execução em andamento, todas rejeitadas, mistura terminal e preservação de entrega.
- 2026-08-22 — Migration `V20260822180858__recompute_service_order_status_snapshots.sql` recalcula somente a
  projeção derivada com `CASE` e subconsultas, sem seed ou backfill de dados de negócio. O teste de upgrade Flyway
  em H2 modo MySQL cobre a mudança de precedência; em produção, executar em janela monitorada por ser um `UPDATE`
  em lote que pode reter locks proporcionais ao volume.
- 2026-08-22 — `statusSnapshot` foi adicionado ao response e `status` foi mantido como alias deprecated no OpenAPI.
  Mapper, assertions OpenAPI e Postman foram atualizados, sem expor entidades de domínio/JPA. Revisão de segurança:
  sem request mutável, segredo, dependência ou erro novo; nenhum achado crítico ou alto aberto. `make test`,
  `make coverage` e `make verify` passaram; cobertura de linhas: 92,75%; `ModuleStructureTest` permaneceu verde.

## Rollback ou recuperação

Rollback da aplicação restaura a semântica antiga, mas não deve desfazer a correção de dados. Manter a migration
aplicada; uma reversão semântica futura exige nova decisão funcional e nova migration forward-only.

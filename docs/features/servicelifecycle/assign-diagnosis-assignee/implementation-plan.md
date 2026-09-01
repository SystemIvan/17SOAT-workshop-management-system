# Plano de Implementação: Atribuir responsável planejado pelo diagnóstico

| Campo | Valor |
|---|---|
| Feature | `assign-diagnosis-assignee` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Branch | `feat/servicelifecycle-service-order-intake-diagnosis-status` |
| Plano agregado | `../service-order-intake-diagnosis-status/implementation-plan.md` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-22) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-22) |

## Objetivo e ordem

Permitir que o Service Advisor atribua ou reatribua o Technician planejado para o próximo Diagnosis e bloquear um
Diagnosis sem essa atribuição. Esta feature deve ser executada depois de `service-order-initial-assessment` e antes de
`diagnosis-authorship`, que reutilizará o lock de Service Order.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Reconciliar documentação afetada | Completed |
| 1 | Implementar lock do aggregate e regra de domínio | Completed |
| 2 | Implementar migration, persistência e caso de uso | Completed |
| 3 | Implementar contrato HTTP e documentação | Completed |
| 4 | Validar comportamento e concorrência | Completed |
| 5 | Concluir segurança e gates finais | Completed |

## Checkpoint 0 — Reconciliar documentação afetada

- Referenciar esta feature nos SDDs de `perform-diagnosis` e `track-execution`.
- Atualizar as descrições afetadas de precondição e response sem duplicar as regras desta spec.
- Se qualquer mudança for material para uma spec anterior, aplicar `Draft`/stale e obter nova aprovação humana antes
  de modificar código.
- Registrar a necessidade de atribuição para Service Orders legadas nas notas de rollout.

Evidência: links, status e aprovações dos documentos reconciliados.

## Checkpoint 1 — Implementar lock do aggregate e regra de domínio

- Adicionar `diagnosisAssigneeId`, accessor, reconstituição e `assignDiagnosisAssignee` a `ServiceOrder`.
- Permitir substituição apenas quando `openDiagnosisId == null` e não alterar status ou execuções.
- Exigir responsável no início de `performDiagnosis`, antes de criar qualquer item.
- Adicionar `findByIdForUpdate(UUID)` ao port e ao adapter JPA com `PESSIMISTIC_WRITE`.
- Usar a leitura bloqueante nos comandos de atribuição e Diagnosis, com ordem consistente de aquisição.

Evidência: testes unitários do aggregate e teste do lock do repository.

## Checkpoint 2 — Implementar migration, persistência e caso de uso

- Criar migration imutável `VyyyyMMddHHmmss__add_diagnosis_assignee_to_service_orders.sql` com timestamp UTC.
- Adicionar `diagnosis_assignee_id BINARY(16) NULL`, sem FK, índice ou backfill.
- Mapear o campo na projection JPA, mapper de persistência, DTO de response e fixtures.
- Criar `AssignDiagnosisAssigneeUseCase` transacional e validar existência via `TechnicianRepository`.
- Classificação: **no seed required**; Service Orders legadas precisam de comando explícito antes do Diagnosis.

Evidência: migration, round-trip e testes do caso de uso.

## Checkpoint 3 — Implementar contrato HTTP e documentação

- Criar `AssignDiagnosisAssigneeRequest(@NotNull UUID technicianId)`.
- Expor `PUT /api/service-orders/{id}/diagnosis-assignee` com `200`, `400`, `404` e `409` documentados.
- Atualizar `ServiceOrderResponse`, OpenAPI gerado, `OpenApiContractTest` e Postman.
- Posicionar o comando Postman antes do Diagnosis e manter separado de `assign-technician`.

Evidência: testes MockMvc e assertions do contrato gerado.

## Checkpoint 4 — Validar comportamento e concorrência

- Cobrir atribuição inicial, idempotência, reatribuição, Technician/SO inexistente e Diagnosis aberto.
- Cobrir bloqueio de Diagnosis sem responsável e permissão para autor efetivo divergente.
- Criar teste de integração concorrente que prove serialização entre atribuição e Diagnosis.
- Confirmar ausência de mudança em `assignedTechnicianId`, notificações e `statusSnapshot`.
- Executar testes focados, `make test` e `make coverage`.

Evidência: comandos, cenários concorrentes e resultados.

## Checkpoint 5 — Concluir segurança e gates finais

### Revisão de segurança planejada

- **Entrada/mass assignment:** request contém somente `technicianId`, validado como UUID não nulo.
- **Autenticação/autorização:** risco pré-existente de atribuição indevida; registrar sem simular identidade.
- **Exposição:** apenas IDs operacionais novos; não expor nome, agenda ou especialidades do Technician.
- **Segredos/logs:** N/A para segredos; evitar logs com Customer e triagem.
- **SQL/concorrência:** coluna aditiva; revisar lock, deadlock, duração da transação e ordem de aquisição.
- **Erros/disclosure:** `VALIDATION_ERROR`, `NOT_FOUND` e `INVALID_STATE_TRANSITION`, sem detalhes internos.
- **Dependências:** N/A — nenhuma dependência nova.
- **Abuso:** reatribuições repetidas são possíveis; ausência de rate limit é risco de plataforma já existente.

Não há achado crítico/alto identificado no desenho. Reavaliar com o código pronto e registrar mitigação ou `N/A`.

- Executar `make verify`, confirmar cobertura e `ModuleStructureTest` verde.
- Marcar `Implemented` somente após todos os checkpoints e evidências.

## Evidências de verificação

- 2026-08-22 — A reconciliação do plano agregado atualizou `perform-diagnosis` com a precondição de
  `diagnosisAssigneeId`, o request/responses afetados e a classificação **material**. A funcional anterior voltou a
  `Draft`; a técnica e o plano históricos ficaram `Stale`. A exigência para Service Orders legadas permanece nas notas
  de rollout desta feature. Reaprovação funcional e técnica registrada para Matheus Apostulo em 2026-08-22;
  checkpoint concluído.
- 2026-08-22 — `diagnosisAssigneeId` foi incluído no aggregate, na persistência e no endpoint
  `PUT /api/service-orders/{id}/diagnosis-assignee`; Diagnosis sem atribuição é rejeitado. O adapter JPA usa
  `PESSIMISTIC_WRITE`, comprovado por `ServiceOrderRepositoryImplTest` com duas transações concorrentes sobre a mesma
  Service Order. O contrato OpenAPI e a coleção Postman foram atualizados.
- 2026-08-22 — Revisão de segurança concluída: request dedicado impede mass assignment; IDs inexistentes retornam
  erro estável; não há novo dado pessoal, segredo ou dependência. A ausência de autenticação/autorização fina e de
  rate limit é risco pré-existente da plataforma, sem ampliação nesta feature. Nenhum achado crítico ou alto aberto.
- 2026-08-22 — `make test`, `make coverage` e `make verify` passaram; cobertura de linhas: 92,75%;
  `ModuleStructureTest` permaneceu verde.

## Rollback ou recuperação

Reverter a aplicação desativa o novo comando e a guarda de Diagnosis. A coluna deve permanecer para preservar
atribuições já gravadas. Locks são apenas comportamentais e desaparecem com o rollback do adapter.

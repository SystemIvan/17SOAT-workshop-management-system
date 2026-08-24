# Plano de Implementação: Registrar autoria efetiva do diagnóstico

| Campo | Valor |
|---|---|
| Feature | `diagnosis-authorship` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Branch | `feat/servicelifecycle-service-order-intake-diagnosis-status` |
| Plano agregado | `../service-order-intake-diagnosis-status/implementation-plan.md` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-22) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-22) |

## Objetivo e ordem

Registrar em cada Service Execution o Technician que efetivamente realizou o Diagnosis e o instante único do lote.
Executar depois de `assign-diagnosis-assignee`, reutilizando a leitura bloqueante da Service Order.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Reconciliar documentação afetada | Completed |
| 1 | Implementar autoria e relógio no domínio/aplicação | Completed |
| 2 | Implementar migration e persistência | Completed |
| 3 | Atualizar contrato HTTP, OpenAPI e Postman | Completed |
| 4 | Completar testes de auditoria e regressão | Completed |
| 5 | Concluir segurança e gates finais | Completed |

## Checkpoint 0 — Reconciliar documentação afetada

- Referenciar esta feature nos SDDs de `perform-diagnosis` e `track-execution`.
- Atualizar o request de Diagnosis e o detalhe de Service Execution nos documentos afetados.
- Aplicar o fluxo `Draft`/stale e obter aprovação humana se a revisão de uma spec existente for material.
- Registrar que a autoria informada é declaratória e provisória, não identidade autenticada.

Evidência: links, status e aprovações dos documentos reconciliados.

## Checkpoint 1 — Implementar autoria e relógio no domínio/aplicação

- Adicionar `diagnosedByTechnicianId` e `diagnosedAt` imutáveis a `ServiceExecution`.
- Alterar o factory e a reconstituição, aceitando nulo somente em execuções legadas.
- Fazer `ServiceOrder.performDiagnosis` validar autor/instante antes do primeiro item e repeti-los no lote.
- Ampliar `PerformDiagnosisUseCase` com `TechnicianRepository`, lock da SO e `Clock.systemUTC()` testável.
- Obter e truncar um único `Instant` a microssegundos por comando.

Evidência: testes unitários com `Clock.fixed` e arquivos alterados.

## Checkpoint 2 — Implementar migration e persistência

- Criar `VyyyyMMddHHmmss__add_diagnosis_authorship_to_service_executions.sql` com timestamp UTC.
- Adicionar `diagnosed_by_technician_id BINARY(16) NULL` e `diagnosed_at TIMESTAMP(6) NULL`.
- Não criar FK, índice ou backfill; não inferir autoria da atribuição planejada.
- Atualizar projection JPA, persistence mapper e fixtures, preservando precisão de microssegundos.
- Classificação: **no seed required**; testes usam fixtures e relógio fixo.

Evidência: migration, round-trip e leitura de registro legado.

## Checkpoint 3 — Atualizar contrato HTTP, OpenAPI e Postman

- Exigir `diagnosedByTechnicianId` em `PerformDiagnosisRequest`; não aceitar `diagnosedAt` como entrada.
- Adicionar autor e instante a `ServiceExecutionResponse`, anuláveis apenas para legado.
- Documentar `200`, `400`, `404` e `409` no endpoint de Diagnosis.
- Atualizar `OpenApiContractTest` e Postman com Technician de demonstração, sem dado pessoal real.

Evidência: MockMvc, schema OpenAPI e diff da coleção.

## Checkpoint 4 — Completar testes de auditoria e regressão

- Cobrir valores iguais em todas as execuções do lote e divergência permitida do responsável planejado.
- Cobrir autor ausente/inexistente, timestamp não controlável pelo request e atomicidade em falha.
- Cobrir imutabilidade, serialização ISO-8601 UTC, round-trip e legado nulo.
- Revalidar `diagnosisId`, Stock Requirements, Estimate e `assignedTechnicianId`.
- Executar testes focados, `make test` e `make coverage`.

Evidência: comandos, cenários e resultados.

## Checkpoint 5 — Concluir segurança e gates finais

### Revisão de segurança planejada

- **Entrada/mass assignment:** request dedicado; `diagnosedAt` não é aceito do chamador.
- **Autenticação/autorização:** autoria é declarada e falsificável sem auth. Classificação inicial: risco médio aceito
  para o MVP aprovado; não apresentar o campo como prova autenticada.
- **Exposição:** UUID e timestamp operacionais; não incluir nome ou dados pessoais do Technician.
- **Segredos/logs:** N/A para segredos; autor não deve ser logado junto com triagem/Customer.
- **SQL/migration:** colunas aditivas e nulas para legado; sem backfill enganoso.
- **Erros/disclosure:** falhas estáveis sem stack trace, SQL ou detalhes de repository.
- **Dependências:** N/A — somente JDK `Clock`/`Instant`.
- **Abuso:** validar existência reduz IDs inválidos, mas não falsificação; registrar limitação no OpenAPI.

Não há achado crítico/alto identificado no desenho. Reavaliar com o código pronto; escalar se a exposição real mudar.

- Executar `make verify`, confirmar cobertura e `ModuleStructureTest` verde.
- Marcar `Implemented` somente após todos os checkpoints e evidências.

## Evidências de verificação

- 2026-08-22 — A reconciliação do plano agregado atualizou `perform-diagnosis` com
  `diagnosedByTechnicianId`, `diagnosedAt`, retorno por Service Execution e a classificação **material**. A funcional
  anterior voltou a `Draft`; a técnica e o plano históricos ficaram `Stale`. A autoria declaratória, sem identidade
  autenticada, permanece registrada nesta feature. Reaprovação funcional e técnica registrada para Matheus Apostulo
  em 2026-08-22; checkpoint concluído.
- 2026-08-22 — Cada `ServiceExecution` passou a receber `diagnosedByTechnicianId` e `diagnosedAt` imutáveis. O caso
  de uso valida o Technician, usa um único `Clock.systemUTC()` truncado a microssegundos e não aceita timestamp de
  entrada. O teste unitário cobre lote, relógio fixo e falha atômica para autor inexistente.
- 2026-08-22 — Migration `V20260822173918__add_diagnosis_authorship_to_service_executions.sql` adiciona colunas
  nulas sem backfill; o round-trip JPA confirma UUID e timestamp. OpenAPI, MockMvc e Postman registram o request e o
  response atualizados.
- 2026-08-22 — Revisão de segurança concluída: autoria permanece declaratória (risco médio de falsificação aceito
  para o MVP, sem ser apresentada como identidade autenticada); UUID/timestamp não expõem dados pessoais; não há
  novos segredos ou dependências. Nenhum achado crítico ou alto aberto. `make test`, `make coverage` e `make verify`
  passaram; cobertura de linhas: 92,75%; `ModuleStructureTest` permaneceu verde.

## Rollback ou recuperação

Reverter a aplicação deixa de preencher e expor a autoria. As colunas devem permanecer para preservar registros já
capturados; uma remoção futura exige migration deliberada.

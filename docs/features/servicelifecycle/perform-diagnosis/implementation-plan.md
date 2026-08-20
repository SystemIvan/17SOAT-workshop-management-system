# Plano de Implementação: Registrar Diagnóstico

| Campo | Valor |
|---|---|
| Feature | `perform-diagnosis` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Especificação técnica | `./technical-spec.md` |

> **Nota:** plano retroativo. O domínio, a persistência e o contrato HTTP já estavam implementados em
> produção antes deste gate SDD; os checkpoints abaixo registram o que já existia e o que foi
> adicionado especificamente por este esforço de documentação (testes de application e web).

## Checkpoints

- [x] Arquitetura e contratos implementados sem violação de fronteiras — `ServiceOrder.performDiagnosis`,
  `ServiceExecution`, `PerformDiagnosisUseCase`, `ServiceOrderController` já existiam; nenhuma
  importação de pacote interno de `registration`/`stockprocurement` em `serviceorder`.
- [x] Persistência, migrations e classificação de seeds concluídas — tabela `service_executions` já
  parte da migration de baseline (`V20260815000000__initial_schema.sql`); nenhuma migration nova
  necessária por esta feature. Classificação: não se aplica (dado transacional, não seed); não há seed
  automático de diagnóstico em produção.
- [x] Comportamento de domínio e aplicação implementado — `performDiagnosis` cria um `ServiceExecution`
  por item, guarda contra diagnóstico duplo aberto, deriva `status = IN_DIAGNOSIS`.
- [x] Testes automatizados e `make verify` aprovados — ver "Evidências de verificação".
- [x] Revisão de segurança concluída, com achados e mitigações registrados — ver seção abaixo.
- [x] OpenAPI, Postman e documentação do projeto atualizados — endpoint já documentado via
  `@Operation`/springdoc e já presente na collection Postman e no `OpenApiContractTest`; nenhuma mudança
  de contrato nesta feature, então nenhuma atualização foi necessária.

## Revisão de segurança

- **Validação de entrada / mass assignment**: `PerformDiagnosisRequest`/`DiagnosisItemRequest`/
  `StockRequirementRequest` usam Bean Validation (`@NotEmpty`, `@NotNull`, `@NotBlank`, `@Positive`,
  `@Valid` em cascata); DTOs dedicados, sem exposição de entidade de domínio/JPA na API. OK.
- **Autenticação/autorização**: não há mecanismo de autenticação no projeto; este endpoint segue o mesmo
  padrão (sem controle de acesso) já presente nos demais endpoints. Risco pré-existente de plataforma,
  já registrado em `service-order-creation/implementation-plan.md`, não agravado por esta feature.
- **Exposição de dados**: a resposta expõe os novos `ServiceExecution` (nome, preço, status,
  `stockRequirements`) — nenhum dado pessoal de Customer/Technician. OK.
- **Segredos/logs sensíveis**: nenhum segredo manipulado; nenhum log novo introduzido por este fluxo.
- **SQL/persistência/migration**: nenhuma migration nova; persistência via Spring Data JPA, sem SQL
  manual. OK.
- **Erros e disclosure**: `404`/`409`/`400` mapeados pelos handlers já existentes
  (`GlobalExceptionHandler`, `ServiceLifecycleExceptionHandler`), sem stack trace nem detalhe de SQL.
- **Dependências novas**: nenhuma.
- **Abuso**: `catalogServiceId`/`stockItemId` arbitrários (não existentes em `registration`/
  `stockprocurement`) são aceitos sem erro — mesmo padrão de risco já registrado em RF09/RF10, não uma
  falha introduzida por esta feature. Qualquer chamador pode registrar diagnóstico para qualquer
  Service Order (sem autenticação), mesmo padrão de risco de todos os outros endpoints de mutação do
  projeto.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=PerformDiagnosisUseCaseTest` — 2 testes novos, 0 falhas (fluxo válido com
  múltiplos itens; Service Order inexistente).
- `./mvnw test -Dtest=ServiceOrderControllerDiagnosisTest` — 4 testes novos, 0 falhas (`200` com
  execuções refletidas, `404` para Service Order inexistente, `400` para `items` vazio,
  `409`/`INVALID_STATE_TRANSITION` para diagnóstico já aberto).
- `./mvnw test -Dtest=ServiceOrderTest` — cobertura de domínio já existente para `performDiagnosis`
  (movimentação para `IN_DIAGNOSIS`, rejeição de diagnóstico duplo aberto) revalidada, sem regressão.
- `./mvnw verify` (equivalente a `make verify`) — 2026-08-20, `BUILD SUCCESS`, sem falhas, JaCoCo
  executado.
- `ModuleStructureTest` — verde; nenhuma fronteira de módulo violada pela feature.
- Postman: entrada "Perform diagnosis" já presente em `Service Orders` antes deste gate; nenhuma
  mudança de contrato necessária.
- OpenAPI: endpoint já documentado via `@Operation` em `ServiceOrderController.performDiagnosis` e já
  coberto por `OpenApiContractTest.documentEveryCurrentHttpOperation`
  (`$.paths['/api/service-orders/{id}/diagnosis'].post`); nenhuma mudança necessária.

## Rollback ou recuperação

N/A — este gate SDD é documentação retroativa de código e schema já em produção; não há deploy nem
migration novos associados a este plano. Os testes adicionados (`PerformDiagnosisUseCaseTest`,
`ServiceOrderControllerDiagnosisTest`) podem ser revertidos isoladamente via `git revert` sem qualquer
impacto em dado persistido.

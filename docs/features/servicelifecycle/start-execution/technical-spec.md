# Especificação Técnica: Iniciar execução de um serviço

| Campo | Valor |
|---|---|
| Feature | `start-execution` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-26 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-26 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-26) |

## Objetivo e escopo

Impedir a transição de `ServiceExecution` de `READY` para `IN_PROGRESS` quando não houver
`assignedTechnicianId`. A regra pertence ao domínio da `ServiceExecution`, pois deve valer para todos os
adaptadores e fluxos que iniciem uma execução.

Não há mudança de path, verbo, request ou forma da resposta do endpoint existente
`POST /api/service-orders/{id}/executions/{executionId}/start`. A mudança contratual é somente o novo
caso de falha: execução `READY` sem Technician atribuído retorna `409 INVALID_STATE_TRANSITION`.

Não estão no escopo: validar disponibilidade/especialidade do Technician (AD-006), limitar o ator que
inicia a execução (AD-016), alterar `AssignTechnicianUseCase`, criar histórico de atribuição ou alterar
o fluxo de conclusão. Uma execução que já tenha iniciado continua podendo ser concluída conforme RF22.

## Contexto e desenho

O bounded context continua sendo `servicelifecycle`; não há dependência nova entre módulos.

`StartExecutionUseCase` carrega a `ServiceOrder`, chama `ServiceOrder.startExecution(executionId)` e
persiste o aggregate. `ServiceOrder` delega a transição a `ServiceExecution.start()`. Este último método
passará a:

1. exigir o status `READY`, como hoje;
2. exigir `assignedTechnicianId != null`;
3. somente então alterar o status para `IN_PROGRESS`.

A ordem preserva o erro de status para qualquer estado diferente de `READY`; para `READY` sem
atribuição, uma `IllegalStateException` de regra de negócio é lançada antes de qualquer mutação.
`diagnosisAssigneeId` não participa da validação porque representa o responsável pelo diagnóstico, não
o responsável pela execução.

## Interfaces, falhas e documentação

| Situação | HTTP | Código estável | Efeito |
|---|---:|---|---|
| Execução `READY` com `assignedTechnicianId` | 200 | — | status vira `IN_PROGRESS` |
| Execução `READY` sem `assignedTechnicianId` | 409 | `INVALID_STATE_TRANSITION` | estado não muda |
| Execução em status diferente de `READY` | 409 | `INVALID_STATE_TRANSITION` | estado não muda |
| Service Order/execução inexistente | 404 | `NOT_FOUND` | — |

O `ServiceLifecycleExceptionHandler` já converte `IllegalStateException` para
`409 INVALID_STATE_TRANSITION`; nenhum handler novo é necessário. Atualizar as anotações Springdoc do
endpoint, a collection Postman e o README com a etapa obrigatória de atribuir um Technician antes de
iniciar.

## Persistência e dados

Classificação: **nenhum seed necessário**. `assignedTechnicianId` já existe na entidade/tabela de
execução e permanece anulável para suportar estados anteriores a `READY`; portanto, não haverá migration
Flyway, alteração de schema ou dado de bootstrap.

## Estratégia de testes

- Domínio: execução `READY` sem Technician não inicia e mantém `READY`; com Technician, inicia.
- Caso de uso: `StartExecutionUseCase` rejeita execução pronta sem atribuição e mantém o aggregate.
- HTTP MockMvc: endpoint retorna `409` e `INVALID_STATE_TRANSITION` para o novo cenário; o caminho feliz
  prepara a atribuição e continua retornando `200`/`IN_PROGRESS`.
- Contrato: atualizar teste OpenAPI, se necessário, e a collection Postman para atribuir antes de iniciar.
- Estrutura: executar `ModuleStructureTest` e os gates `make test` e `make verify`.

## Segurança

| Item | Avaliação |
|---|---|
| Validação e mass assignment | N/A: endpoint não recebe corpo; a validação é regra interna do aggregate. |
| Autenticação/autorização | N/A nesta alteração: permanece a limitação de AD-016. |
| Exposição de dados | Mitigado: retorno reutiliza `ErrorResponse` estável, sem detalhes internos. |
| Dados, logs e segredos | N/A: sem campos, logs ou dependências novos. |
| Persistência | Mitigado: sem migration e sem alteração de schema. |

## Decisões propostas para aprovação técnica

- [x] A guarda foi implementada em `ServiceExecution.start()`, após a validação de `READY` e antes da
      mudança para `IN_PROGRESS`.
- [x] A ausência de Technician reutiliza `IllegalStateException` e o contrato `409 INVALID_STATE_TRANSITION`.
- [x] Não houve migration, seed, dependência nova ou mudança de payload.
- [x] Postman, README, OpenAPI e testes de domínio/aplicação/HTTP foram atualizados na implementação.

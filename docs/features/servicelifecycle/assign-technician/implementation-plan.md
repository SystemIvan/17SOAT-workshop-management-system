# Plano de Implementação: Confirmar atribuição de Technician a ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `assign-technician` |
| Status | Stale |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-16 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-16) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-16) |

> Stale desde 2026-08-20: a especificação funcional foi devolvida a `Draft` pela feature
> `stock-item-reservation`. O plano histórico não deve ser usado para nova implementação até nova revisão do SDD.

## Objetivo da execução

Fechar as duas lacunas aprovadas na spec técnica sobre um fluxo de RF19 que já existe em produção:
validar a existência do Technician antes de gravar a atribuição, e mapear `IllegalStateException` no
`GlobalExceptionHandler` para que uma atribuição inválida (execução `COMPLETED`/`REJECTED`) retorne
`409` em vez de `500` não mapeado. Cobrir com testes de caso de uso, HTTP e persistência um fluxo que
hoje só tem teste de domínio.

A execução termina com `AssignTechnicianUseCase` validando existência do Technician, o novo handler
global de `IllegalStateException`, e a cobertura de teste descrita na spec técnica — sem alterar
comportamento de `StartExecutionUseCase`, sem checar disponibilidade/especialidade do Technician e sem
nenhuma migration nova.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas desta feature e `.claude/rules/epic-3-service-lifecycle.md`
  antes de alterar código.
- Executar um checkpoint por vez e atualizar seu status neste documento: `Pending`, `In Progress` ou
  `Completed`.
- Manter no máximo um checkpoint `In Progress`.
- Executar os testes indicados antes de concluir cada checkpoint.
- Não implementar verificação de disponibilidade/especialidade do Technician, nem exigir atribuição
  prévia para iniciar a execução — ambos permanecem fora de escopo (AD-006 pendente).
- Não alterar `StartExecutionUseCase`, `UpdateExecutionProgressUseCase` ou `CompleteExecutionUseCase`
  além do efeito colateral esperado e já aprovado do novo handler global de `IllegalStateException`
  (mudança de código de resposta, não de comportamento de negócio).
- Não importar pacotes internos de `registration` ou `stockprocurement`.
- Interromper e devolver as specs a `Draft` se surgir uma decisão funcional ou técnica materialmente
  diferente da aprovada.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | Validar existência do Technician em `AssignTechnicianUseCase` | Completed |
| 2 | Mapear `IllegalStateException` no `GlobalExceptionHandler` | Completed |
| 3 | Completar testes de aplicação, HTTP e persistência | Completed |
| 4 | Atualizar OpenAPI, Postman e critérios de aceite | Completed |
| 5 | Executar gates finais e concluir a feature | Completed |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar branch `feature/servicelifecycle-assign-technician` e `git status --short` sem mudanças
  inesperadas.
- Confirmar Java 21 e execução via `./mvnw` ou `Makefile`.
- Reler `AssignTechnicianUseCase`, `ServiceOrder.confirmTechnicianAssignment`,
  `ServiceExecution.confirmTechnicianAssignment`, `TechnicianRepository`, `GlobalExceptionHandler` e os
  testes existentes (`ServiceOrderTest`, `ServiceExecutionTest`) para confirmar que nada mudou desde a
  spec técnica.

### Verificação

- `git status --short` não apresenta alterações inesperadas.
- Os arquivos lidos batem com o que a spec técnica descreve.

### Evidência

- Branch confirmada: `feature/servicelifecycle-assign-technician`.
- `git status --short`: `M .gitignore` (pré-existente, não desta feature) e
  `?? docs/features/servicelifecycle/` (as três specs desta própria feature, ainda não commitadas) —
  nenhuma mudança inesperada.
- `java -version`: OpenJDK 21.0.10 — confirma Java 21. `mvnw`/`mvnw.cmd` presentes na raiz.
- `AssignTechnicianUseCase`, `ServiceOrder.confirmTechnicianAssignment` (linha 141-142),
  `ServiceExecution.confirmTechnicianAssignment` (linha 78-84), `TechnicianRepository` (sem
  `findById` ainda em uso por `serviceorder`) e `GlobalExceptionHandler` (sem handler de
  `IllegalStateException`) foram relidos nesta sessão e continuam exatamente como descritos na spec
  técnica — nenhuma divergência encontrada.

## Checkpoint 1 — Validar existência do Technician em `AssignTechnicianUseCase`

### Alterações

- Injetar `TechnicianRepository` em `AssignTechnicianUseCase`.
- Antes de chamar `serviceOrder.confirmTechnicianAssignment`, buscar o Technician por
  `TechnicianRepository.findById(request.technicianId())` e lançar `NoSuchElementException` se ausente.
- Não inspecionar `status()` nem `specialties()` do Technician retornado.
- Não alterar `AssignTechnicianRequest`, `ServiceOrder` nem `ServiceExecution`.

### Testes

- Teste de caso de uso: atribuição falha com `NoSuchElementException` quando `technicianId` não existe.
- Teste de caso de uso: atribuição bem-sucedida quando Technician existe (cobrindo o caminho feliz que
  hoje só é testado no domínio, não no caso de uso).

### Verificação

- Executar os testes do pacote `serviceorder.application.usecase`.
- Confirmar que `AssignTechnicianUseCase` não importa nada de `technician.domain.model` além do que já
  é público via `TechnicianRepository`/`Technician`.

### Evidência

- `AssignTechnicianUseCase` agora recebe `TechnicianRepository` por construtor e chama
  `technicianRepository.findById(technicianId)` antes de `serviceOrder.confirmTechnicianAssignment`,
  lançando `NoSuchElementException` quando ausente. Nenhuma inspeção de `status()`/`specialties()` foi
  adicionada. Nenhuma mudança em `AssignTechnicianRequest`, `ServiceOrder` ou `ServiceExecution`.
- Novo teste `AssignTechnicianUseCaseTest` (3 casos: atribuição bem-sucedida, Technician inexistente,
  ServiceOrder inexistente) usando repositories fake em memória, no mesmo estilo de
  `StockItemUseCaseTest`. `./mvnw test -Dtest=AssignTechnicianUseCaseTest`: **3 testes, 0 falhas**.
- Regressão: `ServiceOrderTest` (12), `ServiceExecutionTest` (3) e `TechnicianTest` (8) continuam
  passando sem alteração — a mudança não afeta o domínio.
- `AssignTechnicianUseCase` importa apenas `technician.domain.repository.TechnicianRepository`
  (interface pública), consistente com a spec técnica.

## Checkpoint 2 — Mapear `IllegalStateException` no `GlobalExceptionHandler`

### Alterações

- Adicionar `@ExceptionHandler(IllegalStateException.class)` em `GlobalExceptionHandler`, retornando
  `409 Conflict` com `ErrorResponse("INVALID_STATE_TRANSITION", ex.getMessage())`.
- Não alterar o comportamento de nenhum caso de uso existente; apenas garantir que o `IllegalStateException`
  já lançado (RF19-RF22) chega ao cliente como `409` em vez de erro não mapeado.

### Testes

- Teste do handler (ou teste HTTP end-to-end, ver checkpoint 3) confirmando `409` e o corpo
  `ErrorResponse` esperado para `IllegalStateException`.

### Verificação

- Executar os testes de `GlobalExceptionHandler`/testes HTTP afetados.
- Confirmar que nenhum teste existente que dependia do comportamento anterior (erro não mapeado) quebrou
  de forma inesperada — se algum teste assumia `500`, corrigir a expectativa para `409`, não o handler.

### Evidência

- **Primeira versão (revisada depois, ver nota abaixo):** `GlobalExceptionHandler` ganhou
  `@ExceptionHandler(IllegalStateException.class)` global, retornando `409`/`INVALID_STATE_TRANSITION`.
- **Regressão real encontrada e corrigida nesta primeira versão:**
  `StockItemControllerTest.createsSearchesUpdatesAndDeactivatesStockItem` passou a falhar
  (`$.code` esperado `STOCK_ITEM_INACTIVE`, recebido `INVALID_STATE_TRANSITION`) porque
  `StockItemInactiveException extends IllegalStateException` e o Spring resolve o primeiro advice bean
  aplicável com handler compatível, não o mais específico entre vários beans. Corrigido then com
  `@Order(Ordered.HIGHEST_PRECEDENCE)` em `StockItemExceptionHandler`.
- **Revisado após `/code-review` (2026-08-16):** o handler global de `IllegalStateException` foi
  apontado como achado real — captura qualquer `IllegalStateException` da aplicação inteira, inclusive
  técnicas/de infraestrutura não relacionadas, expondo a mensagem interna como `409` em vez de deixá-las
  cair no tratamento padrão de erro técnico (`AGENTS.md`: "Let unexpected technical failures propagate to
  the platform's standard error handling"). **Correção final:** o handler foi movido para
  `ServiceLifecycleExceptionHandler`, um `@RestControllerAdvice(basePackages = "...servicelifecycle")`
  restrito ao bounded context, cobrindo `ServiceOrderController` e `TechnicianController` (ambos lançam
  `IllegalStateException` de domínio). `GlobalExceptionHandler` e `StockItemExceptionHandler` voltaram
  exatamente ao estado original — o `@Order` deixou de ser necessário, pois não há mais handler global
  de `IllegalStateException` concorrendo. Diff final é menor que a primeira versão.
- Testes: `ServiceLifecycleExceptionHandlerTest` (1, unit test direto do handler) e
  `TechnicianControllerStatusConflictTest` (1, novo — confirma que o handler cobre `TechnicianController`,
  não só `ServiceOrderController`, validando a afirmação do Javadoc da classe).
- Suíte completa após a correção final: `./mvnw verify` → **62 testes, 0 falhas, BUILD SUCCESS**, incluindo
  `ModuleStructureTest` (2, confirma que a nova classe dentro de `servicelifecycle` não viola fronteira
  Modulith) e `StockItemControllerTest` (2, `STOCK_ITEM_INACTIVE` preservado sem `@Order`).

## Checkpoint 3 — Completar testes de aplicação, HTTP e persistência

### Alterações

Nenhuma alteração de produção; apenas testes descritos na spec técnica que ainda não existem:

- Testes MockMvc para `POST /api/service-orders/{id}/executions/{executionId}/assign-technician`
  cobrindo `200`, `404` (ServiceOrder, ServiceExecution e Technician inexistentes), `409`
  (execução `COMPLETED`/`REJECTED`) e `400` (`technicianId` ausente/inválido).
- Teste de persistência confirmando que `assigned_technician_id` é salvo e recarregado corretamente.

### Verificação

- Executar `make test`.
- Confirmar que os quatro cenários de erro do endpoint (`404` ×3 variações, `409`, `400`) estão cobertos.

### Evidência

- **HTTP** (`ServiceOrderControllerAssignTechnicianTest`, 8 testes): `200` com `assignedTechnicianId` no
  body; `404`/`NOT_FOUND` para ServiceOrder, ServiceExecution e Technician inexistentes (3 variações);
  `409`/`INVALID_STATE_TRANSITION` para execução `REJECTED` e para execução `COMPLETED`; `400`/`VALIDATION_ERROR`
  para `technicianId` ausente e para `technicianId` não-UUID. Como `authorizeExecutionFromEstimate` e
  `rejectExecutionFromEstimate` (Épico 2) ainda não têm endpoint HTTP, os cenários `REJECTED`/`COMPLETED`
  preparam o estado chamando o aggregate diretamente via `ServiceOrderRepository` dentro de uma
  `TransactionTemplate`, e só a chamada ao endpoint sob teste (`assign-technician`) passa pelo MockMvc —
  registrado explicitamente no Javadoc da classe de teste para não confundir leitura futura.
- **Achado durante a escrita do teste, corrigido no próprio teste (não é bug de produção):** chamar
  `ServiceOrderRepository.findById` fora de uma transação lança `LazyInitializationException` ao mapear
  `executions` (coleção `@ElementCollection`/`@ManyToOne` lazy). Corrigido envolvendo as chamadas diretas
  ao repository em `TransactionTemplate` no teste — não exigiu mudança em código de produção.
- **Persistência** (`ServiceOrderRepositoryImplTest`, novo pacote de teste para
  `serviceorder.infrastructure.persistence` — não existia nenhum teste de persistência no projeto até
  agora): confirma que `assigned_technician_id` é persistido e recarregado corretamente após
  `save`/`entityManager.clear()`/`findById` dentro da mesma transação, forçando um SELECT real em vez de
  reaproveitar o objeto em memória.
- `./mvnw test` (suíte completa): **61 testes, 0 falhas, BUILD SUCCESS** — 9 testes novos desde o
  Checkpoint 0 (3 aplicação + 1 handler + 8 HTTP + 1 persistência = 13; alguns já contados nos
  checkpoints anteriores).

## Checkpoint 4 — Atualizar OpenAPI, Postman e critérios de aceite

### Alterações

- Confirmar que as anotações Springdoc do endpoint `assign-technician` documentam os novos códigos de
  erro (`404`, `409`, `400`) além do `200` já existente.
- Atualizar `docs/api/postman/workshop-management-system.postman_collection.json` se o endpoint ainda não
  tiver exemplos de erro para este fluxo.
- Marcar os critérios de aceite de `functional-spec.md` com evidência real (não apenas `[x]` sem
  referência).

### Verificação

- Rodar o teste de contrato OpenAPI existente (`OpenApiContractTest`, se aplicável a este endpoint).
- Validar o JSON da coleção Postman.

### Evidência

- `ServiceOrderController.assignTechnician` ganhou `@ApiResponses` documentando `200`, `400`, `404` e
  `409`, únicos códigos de erro que este endpoint pode retornar (confirmado pela tabela de contratos de
  erro da `technical-spec.md`). Nenhum outro endpoint do projeto documenta `@ApiResponses` — este é o
  primeiro; mantive o padrão mínimo (código + descrição curta) para não introduzir um estilo divergente.
- `OpenApiContractTest` (verifica existência de todos os paths, não códigos de resposta) continua
  passando: **1 teste, 0 falhas**.
- Coleção Postman: `assign-technician` já existe (`docs/api/postman/workshop-management-system.postman_collection.json`,
  linha ~127) com o caminho feliz, no mesmo padrão de todos os outros endpoints da coleção (nenhum
  endpoint documenta exemplos de erro) — nenhuma mudança necessária.
- `functional-spec.md`: os 5 critérios de aceite foram marcados `[x]` com evidência (nome do teste) em
  vez de `[ ]` genérico. O item "Existência do Technician" na seção de regras não definidas foi
  atualizado de "Pendente" para "resolvido e implementado" (link para a decisão aprovada na
  `technical-spec.md`); disponibilidade/especialidade continuam explicitamente pendentes (AD-006).
- Suíte completa após as mudanças: `./mvnw test` → **61 testes, 0 falhas, BUILD SUCCESS**.

## Checkpoint 5 — Executar gates finais e concluir a feature

### Gates

- Executar `make verify` sem testes ignorados.
- Confirmar `ModuleStructureTest` verde.
- Revisar o diff final para excluir qualquer alteração fora do escopo (nada em `registration`,
  `stockprocurement`, `StartExecutionUseCase`, `UpdateExecutionProgressUseCase` ou
  `CompleteExecutionUseCase` além do efeito do novo handler).
- Preencher a revisão de segurança abaixo.
- Marcar este plano `Implemented` somente depois de todos os checkpoints concluídos.

### Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação de input e mass assignment | Resolved | `AssignTechnicianRequest` já é fechado (`@NotNull technicianId`); sem mudança. Coberto por `returnsValidationErrorWhenTechnicianIdIsMissing`/`...IsNotAUuid`. |
| Autenticação e autorização | N/A | Limitação já documentada na spec técnica; endpoint permanece público como os demais até AD-016 ser resolvida pelo time. |
| Exposição de dados operacionais | Resolved | `ErrorResponse(code, message)` do handler só expõe a mensagem de `IllegalStateException` já usada internamente (ex.: "Cannot assign a technician..."), sem stack trace, SQL ou nome de classe/pacote. |
| Segredos, credenciais e logs | Resolved | Nenhuma dependência nova; `technicianId` é o único dado manipulado (UUID opaco, não pessoal); nenhum log de body foi adicionado. |
| Erros e information disclosure | Resolved | `409 INVALID_STATE_TRANSITION` e `404 NOT_FOUND` usam apenas mensagens de domínio já existentes, sem stack trace, SQL ou classe interna — confirmado por `ServiceLifecycleExceptionHandlerTest` e pelos testes HTTP. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência Maven nova adicionada; `ApiResponses` já faz parte do Springdoc existente. |
| Categorização incorreta de falha técnica como conflito de negócio | Resolved | Achado do `/code-review`: um handler global de `IllegalStateException` capturaria também exceções técnicas não relacionadas (ex.: infraestrutura JPA) e exporia a mensagem interna como `409`, violando `AGENTS.md` ("Let unexpected technical failures propagate to the platform's standard error handling"). Corrigido escopando o handler a `@RestControllerAdvice(basePackages = "...servicelifecycle")` (`ServiceLifecycleExceptionHandler`) em vez de um handler no pacote raiz — `GlobalExceptionHandler` e `StockItemExceptionHandler` voltaram ao estado original. Verificado por `ServiceLifecycleExceptionHandlerTest`, `TechnicianControllerStatusConflictTest` e `StockItemControllerTest`. |

### Verificação

- `/code-review` (skill de revisão automática) rodou sobre o diff completo e apontou 1 achado real: ver
  linha "Categorização incorreta..." na tabela de segurança acima. Corrigido antes de prosseguir.
- `./mvnw verify` (equivalente a `make verify`) após a correção: `BUILD SUCCESS`, 62 testes, 0 falhas/erros.
- `ModuleStructureTest`: 2 testes, 0 falhas — nenhuma nova violação de fronteira Modulith, incluindo a
  nova classe `ServiceLifecycleExceptionHandler` dentro do pacote `servicelifecycle`.
- Diff final revisado (`git status --short`/`git diff --stat`): 3 arquivos de produção alterados
  (`AssignTechnicianUseCase`, `ServiceOrderController`, e o novo `ServiceLifecycleExceptionHandler`),
  5 testes novos, 3 specs novas. `GlobalExceptionHandler` e `StockItemExceptionHandler` voltaram ao
  estado original — a correção do achado do code-review eliminou a necessidade de tocá-los. Nada em
  `registration`, nada em `StartExecutionUseCase`/`UpdateExecutionProgressUseCase`/`CompleteExecutionUseCase`.
  `.gitignore` com a mesma modificação pré-existente desde antes desta sessão, não tocada aqui.
- Linhas >120 caracteres nos arquivos alterados: só as pré-existentes (imports antigos de
  `ServiceOrderController`); nenhuma linha nova introduzida ultrapassa o limite. Nenhum import wildcard.

### Evidência final

| Evidência | Resultado |
|---|---|
| `./mvnw test` | Passou: 62 testes, 0 falhas/erros |
| `./mvnw verify` (`make verify`) | `BUILD SUCCESS` |
| Cobertura JaCoCo (global) | 81% instruções (782/4.188 perdidas) — acima da meta do projeto |
| `ModuleStructureTest` | Passou: 2 testes |
| `OpenApiContractTest` | Passou: 1 teste |
| `/code-review` | 1 achado real, corrigido (handler de `IllegalStateException` escopado a `servicelifecycle`) |
| Revisão de segurança | Concluída, sem achado crítico ou alto em aberto |
| Diff fora do escopo | Nenhum |

## Rollback ou recuperação

Nenhuma migration é criada nesta feature; não há schema a reverter. Se `ServiceLifecycleExceptionHandler`
produzir uma regressão inesperada em outro fluxo de `servicelifecycle` (RF20-RF22, Technician) não coberto
pelos testes existentes, remover essa classe (e os testes que dependem do `409`, revertendo-os para o
comportamento anterior de erro não mapeado) é suficiente e isolado — não afeta `AssignTechnicianUseCase`
(Checkpoint 1) nem exige tocar em `GlobalExceptionHandler`/`StockItemExceptionHandler`, que permanecem no
estado original.

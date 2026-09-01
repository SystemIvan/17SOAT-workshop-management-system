# Especificação Técnica: Confirmar atribuição de Technician a ServiceExecution

| Campo | Valor |
|---|---|
| Feature | `assign-technician` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-20 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-20) |

## Revisão proposta por `stock-item-reservation`

Esta revisão preserva as regras atuais de atribuição e acrescenta somente o efeito consumidor de materiais
reservados. Ao atribuir ou reatribuir um Technician a uma Service Execution `READY` com
`stockReservationId`, `AssignTechnicianUseCase` publica um evento interno dentro da transação. Um listener
`AFTER_COMMIT` usa a extensão de `TechnicianNotificationPort` para comunicar a reserva ao Technician.

Não haverá notificação antes do commit, notificação duplicada por retry idempotente de reserva, consulta a
linhas/estado internos da Stock Reservation, módulo genérico de Notification, histórico, retry ou canal real.
Falhas do adapter serão registradas com IDs operacionais e não causarão rollback. O listener também não
altera `stockReservationId`, `READY` ou regras de reatribuição. Os testes devem cobrir atribuição posterior,
falha do adapter e execução somente após commit.

## Gate de aprovação

Nenhum `implementation-plan.md` pode ser criado e nenhuma implementação pode começar antes da aprovação
humana explícita desta especificação — incluindo a decisão proposta abaixo sobre validar a existência do
Technician, que ainda não está aprovada.

## Objetivo técnico e escopo

O comportamento de RF19 já está implementado (`AssignTechnicianUseCase`, `ServiceOrder.confirmTechnicianAssignment`,
`ServiceExecution.confirmTechnicianAssignment`, endpoint `POST /api/service-orders/{id}/executions/{executionId}/assign-technician`).
Esta especificação:

- confirma que o desenho atual (aggregate `ServiceOrder`, entidade `ServiceExecution`, Technician
  referenciado só por `UUID`) permanece válido e não propõe reestruturação;
- fecha uma lacuna real encontrada no `GlobalExceptionHandler` (`IllegalStateException` não é tratada,
  então uma tentativa de atribuição inválida hoje resultaria em erro `500` não mapeado, violando
  `AGENTS.md` — "Map business and validation errors through the global exception handler");
- propõe, para aprovação, adicionar validação de existência do Technician antes de gravar a atribuição;
- **não** propõe validar disponibilidade/especialidade do Technician nem exigir atribuição prévia para
  iniciar a execução — ambos permanecem fora de escopo conforme a `functional-spec.md`;
- adiciona a cobertura de teste que hoje não existe para este fluxo (uso de caso, HTTP, persistência).

Esta feature não implementará:

- qualquer verificação de `TechnicianStatus` (`AVAILABLE`/`BUSY`/`INACTIVE`) ou `Specialty` na
  atribuição — depende de AD-006 (Team Decision Required, whole-team);
- qualquer mudança em `StartExecutionUseCase`/`ServiceOrder.startExecution` para exigir
  `assignedTechnicianId` preenchido;
- histórico de reatribuições;
- autorização por papel (depende de AD-016, ainda Team Decision Required);
- mudanças em `Notification`.

## Contextos e fronteiras de módulo

`serviceorder` e `technician` são subpacotes do mesmo `@ApplicationModule` Spring Modulith
(`servicelifecycle`, ver `servicelifecycle/package-info.java`) — não são módulos Modulith separados.
Portanto, `AssignTechnicianUseCase` (em `serviceorder.application.usecase`) pode depender de
`TechnicianRepository` (em `technician.domain.repository`) sem violar fronteira de módulo nem tocar
AD-011 (contratos entre módulos Modulith), que trata de `registration` e `stockprocurement`, não de
subpacotes internos do mesmo módulo.

Nenhum pacote de `registration` ou `stockprocurement` é importado por esta feature. `ModuleStructureTest`
deve continuar verde sem exceções adicionais.

## Modelo de domínio

Nenhuma mudança nos aggregates. Para referência, o comportamento existente que esta feature preserva:

- `ServiceOrder.confirmTechnicianAssignment(UUID serviceExecutionId, UUID technicianId)` localiza a
  `ServiceExecution` pelo ID e delega a ela;
- `ServiceExecution.confirmTechnicianAssignment(UUID technicianId)` rejeita a atribuição com
  `IllegalStateException` quando o status é `COMPLETED` ou `REJECTED`; em qualquer outro status,
  sobrescreve `assignedTechnicianId`.

Nenhum destes métodos muda de assinatura ou de local nesta feature.

## Casos de uso de aplicação

### `AssignTechnicianUseCase` — mudança proposta

| Aspecto | Hoje | Proposto |
|---|---|---|
| Dependências | `ServiceOrderRepository` | `ServiceOrderRepository` + `TechnicianRepository` |
| Verificação de existência do Technician | Nenhuma | `TechnicianRepository.findById(technicianId)`, lança `NoSuchElementException` se ausente, **antes** de chamar `serviceOrder.confirmTechnicianAssignment` |
| Transação | `@Transactional` | inalterado |

`TechnicianRepository.findById` já existe (`technician/domain/repository/TechnicianRepository.java`,
linha 15) — nenhuma interface nova é criada. A verificação é apenas de existência; não inspeciona
`status()` nem `specialties()` do `Technician` retornado, para não decidir AD-006 unilateralmente.

Isto é uma decisão proposta, não implementada até aprovação — ver checklist ao final.

## Contratos HTTP

Endpoint já existe e não muda de path, verbo ou payload:

```
POST /api/service-orders/{id}/executions/{executionId}/assign-technician
Body: { "technicianId": "<uuid>" }
Sucesso: 200 OK com ServiceOrderResponse (inclui assignedTechnicianId em cada ServiceExecutionResponse)
```

Nenhuma mudança no `ServiceOrderResponse`/`ServiceExecutionResponse`: `assignedTechnicianId` já é
exposto (`ServiceExecutionResponse.java`, linha 16).

### Contratos de erro — mudança proposta

| Situação | Hoje | Proposto |
|---|---:|---|
| `ServiceOrder` ou `ServiceExecution` inexistente | `404` `NOT_FOUND` (já implementado, `ServiceOrderFinder`/`findExecution`) | inalterado |
| `technicianId` não corresponde a um Technician existente | `500` não mapeado (`IllegalStateException`/exceção de infraestrutura não tratada) | `404` `NOT_FOUND` — reutiliza o código genérico já usado por `ServiceOrder`/`ServiceExecution` ausentes, para manter consistência com o padrão existente em `servicelifecycle` (`GlobalExceptionHandler.handleNotFound`, que não distingue por entidade) |
| Atribuição a `ServiceExecution` em status `COMPLETED` ou `REJECTED` | `500` não mapeado (`IllegalStateException` sem handler) | `409 Conflict` com novo código `INVALID_STATE_TRANSITION` |

**Revisado após code review (2026-08-16):** em vez de adicionar `@ExceptionHandler(IllegalStateException.class)`
ao `GlobalExceptionHandler` (pacote raiz, aplicável a toda a aplicação), o mapeamento vive em um novo
`@RestControllerAdvice(basePackages = "...servicelifecycle")` — `ServiceLifecycleExceptionHandler` — restrito
ao bounded context. Um handler verdadeiramente global para `IllegalStateException` capturaria também
exceções técnicas não relacionadas (ex.: de infraestrutura JPA/Hibernate) e as reportaria como `409` com a
mensagem interna exposta, em vez de deixá-las cair no tratamento padrão de erro técnico — contrariando
`AGENTS.md` ("Let unexpected technical failures propagate to the platform's standard error handling").
Escopar por pacote resolve isso sem precisar criar uma hierarquia de exceções de domínio nova.

O novo handler cobre, como efeito colateral desejado (mesmo raciocínio de antes, com escopo mais preciso),
todos os `IllegalStateException` já lançados dentro de `servicelifecycle`: `ServiceOrder`/`ServiceExecution`
(diagnóstico duplicado, transições de estado inválidas em RF20-RF22) e `Technician`
(`markBusy`/`markAvailable` em técnico inativo) — hoje nenhum deles é tratado. Nenhum desses outros fluxos
tem seu comportamento de negócio alterado; apenas passam a retornar `409` em vez de `500` não mapeado.
`registration` e `stockprocurement` continuam fora do escopo deste handler; `StockItemInactiveException`
continua sendo tratada só por `StockItemExceptionHandler`, sem necessidade de `@Order` adicional, já que
não há mais concorrência com um handler global de `IllegalStateException`.

## Persistência e dados de bootstrap

Nenhuma mudança de schema. A coluna `assigned_technician_id` já existe em `service_executions`
(`V20260815000000__initial_schema.sql`) e já é lida/escrita por `ServiceExecutionJpaEntity`
(linha 50) e pelo mapper existente. Nenhuma migration nova é necessária.

Classificação de dados: nenhum dado novo é criado por esta feature; não se aplica seed nem fixture além
dos builders de teste já usados em `ServiceOrderTest`/`ServiceExecutionTest`.

## Segurança e operação

- Sem mudança de autorização: o endpoint continua público, como os demais endpoints atuais de
  `servicelifecycle` — mesma limitação conhecida registrada em `stock-domain-foundation/technical-spec.md`,
  ainda dependente de AD-016.
- Nenhum dado pessoal ou segredo é manipulado; `technicianId` é um `UUID` opaco.
- Mensagens de erro não expõem stack trace, SQL ou classes internas — o novo handler segue o mesmo
  `ErrorResponse` já usado pelos demais.
- Nenhuma dependência nova é adicionada.

## Estratégia de testes

Hoje só existem testes de domínio (`ServiceOrderTest.rf19_confirmingTechnicianAssignmentSetsTheAssignedTechnician`,
`ServiceExecutionTest`). Não há teste de caso de uso, HTTP ou persistência para RF19 — gap já registrado
em `Architecture-Decisions.md` ("Implementation Gaps"). Esta feature adiciona:

### Domínio (já existente, sem mudança necessária)

- Cobertura atual de `confirmTechnicianAssignment` (sucesso e rejeição em status terminal) permanece
  válida e não precisa ser reescrita.

### Aplicação (`AssignTechnicianUseCase`, novo)

Testes com repository fake/mock cobrindo:

- atribuição bem-sucedida quando `ServiceOrder`, `ServiceExecution` e `Technician` existem;
- `NoSuchElementException` quando `ServiceOrder` não existe (comportamento já coberto indiretamente por
  `ServiceOrderFinder`, testar explicitamente neste caso de uso);
- `NoSuchElementException` quando `technicianId` não corresponde a um Technician existente (novo);
- propagação de `IllegalStateException` quando a `ServiceExecution` está `COMPLETED`/`REJECTED`.

### HTTP (`ServiceOrderController`, novo)

Testes MockMvc cobrindo:

- `200 OK` com `assignedTechnicianId` no body na atribuição bem-sucedida;
- `404 NOT_FOUND` para `ServiceOrder`, `ServiceExecution` ou `technicianId` inexistentes;
- `409 INVALID_STATE_TRANSITION` para atribuição em execução `COMPLETED`/`REJECTED`;
- `400 VALIDATION_ERROR` quando `technicianId` está ausente ou não é um UUID válido.

### Persistência

- Confirmar que `assigned_technician_id` é persistido e recarregado corretamente após
  `save`/`findById` (pode reutilizar fixtures já existentes de `ServiceOrder` com uma `ServiceExecution`
  autorizada).

### Modulith

- `ModuleStructureTest` deve continuar verde; a nova dependência `AssignTechnicianUseCase → TechnicianRepository`
  é interna ao módulo `servicelifecycle` e não deve gerar violação.

## Decisões propostas para aprovação técnica

- [ ] `AssignTechnicianUseCase` passa a validar a existência do Technician via
      `TechnicianRepository.findById`, sem inspecionar `status()` ou `specialties()`.
- [ ] Technician inexistente retorna `404` com o código genérico `NOT_FOUND` (não um código específico
      por entidade).
- [ ] Novo `@ExceptionHandler(IllegalStateException.class)` global retorna `409 INVALID_STATE_TRANSITION`
      e passa a cobrir, sem mudança de comportamento de negócio, todos os `IllegalStateException`
      pré-existentes em `servicelifecycle` que hoje resultam em `500` não mapeado.
- [ ] Nenhuma verificação de disponibilidade/especialidade do Technician é adicionada nesta feature
      (permanece bloqueado por AD-006).
- [ ] Nenhuma mudança em `StartExecutionUseCase` é feita nesta feature.
- [ ] Nenhuma migration nova é necessária.

# Especificação Técnica: Listagem e detalhamento de ordens de serviço

| Campo | Valor |
|---|---|
| Feature | `list-service-orders` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-25 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-25 |
| Especificação funcional | `docs/features/servicelifecycle/list-service-orders/functional-spec.md` (`Approved` em 2026-08-25) |

## Objetivo técnico

O detalhamento por ID (`GET /api/service-orders/{id}` → `GetServiceOrderUseCase`) já existe e não muda.
Esta feature cobre exclusivamente a lacuna: não existe endpoint de listagem/coleção
(`GET /api/service-orders`). O gap já era conhecido localmente (`docs/Architecture.md` §"Gaps" — "Listar/
detalhar Service Orders ... Contratos e filtros não foram identificados") e a `functional-spec.md`
resolveu os filtros/contrato que faltavam.

## Contexto e desenho

Implementação inteira em `servicelifecycle.serviceorder`, reaproveitando o aggregate `ServiceOrder` e o
DTO `ServiceOrderResponse`/`ServiceOrderMapper` já existentes. Nenhuma importação de pacote interno de
outro módulo é necessária.

O padrão de busca com filtros já existe no projeto em `stockprocurement.stock`
(`StockItemRepository.search(StockItemSearchCriteria)`, implementado com
`JpaSpecificationExecutor`/`Specification` em `StockItemRepositoryImpl`, exposto por
`SearchStockItemsUseCase`). Esta feature replica o mesmo padrão em `serviceorder`, trocando apenas os
critérios (`status`, `customerId`, `technicianId`, `priority` em vez de `search`/`type`/`available`).

### Decisão de design: método de porta com default, não abstrato

`ServiceOrderRepository` tem hoje 12 implementações fake em `src/test` (uma por classe de teste de caso
de uso — `AssignTechnicianUseCaseTest`, `CompleteExecutionUseCaseTest`, `PerformDiagnosisUseCaseTest`
etc.), contra apenas 3 para `StockItemRepository`. Declarar `search(...)` como método abstrato quebraria
a compilação de todas as 12 classes de teste existentes só para adicionar um método que nenhuma delas
chama. Em vez disso, `search` recebe um **default method** que lança `UnsupportedOperationException`,
seguindo o precedente já existente na mesma interface (`findByIdForUpdate` já tem um default que delega
para `findById`). Só `ServiceOrderRepositoryImpl` (produção) e o novo fake de
`ListServiceOrdersUseCaseTest` precisam sobrescrever `search`; os outros 12 fakes continuam compilando
sem alteração.

## Estrutura proposta

- `serviceorder/domain/repository/ServiceOrderSearchCriteria.java` (novo) — record de filtros.
- `serviceorder/domain/repository/ServiceOrderRepository.java` — adicionar `search(...)` como default
  method (alteração de arquivo existente).
- `serviceorder/infrastructure/persistence/ServiceOrderJpaRepository.java` — implementar
  `JpaSpecificationExecutor<ServiceOrderJpaEntity>` (alteração de arquivo existente).
- `serviceorder/infrastructure/persistence/ServiceOrderRepositoryImpl.java` — implementar `search(...)`
  com `Specification` (alteração de arquivo existente).
- `serviceorder/application/usecase/ListServiceOrdersUseCase.java` (novo).
- `serviceorder/infrastructure/web/ServiceOrderController.java` — adicionar endpoint `GET`
  (alteração de arquivo existente).
- `docs/api/postman/workshop-management-system.postman_collection.json` — novo request "List service
  orders".
- `src/test/java/.../infrastructure/web/OpenApiContractTest.java` — nova asserção para
  `$.paths['/api/service-orders'].get`.

Nenhum arquivo de persistência muda de schema; nenhuma migration nova.

## Domínio

### `ServiceOrderSearchCriteria` (novo)

```java
package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;

import java.util.UUID;

public record ServiceOrderSearchCriteria(
        ServiceOrderStatus status, UUID customerId, UUID technicianId, Priority priority) {
}
```

Sem normalização (diferente de `StockItemSearchCriteria`, que faz trim/lowercase de `search`) — os
quatro campos são comparações de igualdade diretas, `null` significa "sem filtro".

### `ServiceOrderRepository` — novo método com default

```java
default List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
    throw new UnsupportedOperationException("search not supported by this ServiceOrderRepository");
}
```

## Caso de uso: `ListServiceOrdersUseCase`

```java
@Service
public class ListServiceOrdersUseCase {

    private final ServiceOrderRepository repository;

    public ListServiceOrdersUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> execute(ServiceOrderSearchCriteria criteria) {
        return repository.search(criteria).stream()
                .map(ServiceOrderMapper::toResponse)
                .toList();
    }
}
```

Nenhuma lógica de negócio própria: delega o filtro para o repositório (mesmo padrão de
`SearchStockItemsUseCase`) e reaproveita `ServiceOrderMapper.toResponse`, já usado por
`GetServiceOrderUseCase` e por todo endpoint de mutação.

## Repository — `ServiceOrderRepositoryImpl.search`

```java
@Override
public List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
    Specification<ServiceOrderJpaEntity> specification = (root, query, builder) -> {
        query.distinct(true);
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.status() != null) {
            predicates.add(builder.equal(root.get("statusSnapshot"), criteria.status()));
        }
        if (criteria.customerId() != null) {
            predicates.add(builder.equal(root.get("customerId"), criteria.customerId()));
        }
        if (criteria.priority() != null) {
            predicates.add(builder.equal(root.get("priority"), criteria.priority()));
        }
        if (criteria.technicianId() != null) {
            Predicate diagnosisAssignee = builder.equal(root.get("diagnosisAssigneeId"), criteria.technicianId());
            Join<ServiceOrderJpaEntity, ServiceExecutionJpaEntity> executions =
                    root.join("executions", JoinType.LEFT);
            Predicate executionAssignee = builder.equal(executions.get("assignedTechnicianId"), criteria.technicianId());
            predicates.add(builder.or(diagnosisAssignee, executionAssignee));
        }
        return builder.and(predicates.toArray(Predicate[]::new));
    };
    return jpaRepository.findAll(specification).stream().map(mapper::toDomain).toList();
}
```

Pontos relevantes:

- `query.distinct(true)` é sempre aplicado (não só quando `technicianId` é informado) — mais simples de
  ler e sem custo relevante no volume de dados de um MVP; evita duplicar `ServiceOrder`s quando o join
  com `executions` (`@OneToMany`) multiplica linhas.
- O `LEFT JOIN` em `executions` é necessário mesmo para o predicado (não `INNER JOIN`), porque uma
  `ServiceOrder` sem nenhuma execução ainda deve poder casar via `diagnosisAssigneeId` sozinho.
- Sem `Sort` explícito — a `functional-spec.md` não define ordenação (fora de escopo); a ordem retornada
  pelo banco não é uma garantia de contrato desta feature.

### `ServiceOrderJpaRepository` — adicionar interface

```java
public interface ServiceOrderJpaRepository extends JpaRepository<ServiceOrderJpaEntity, UUID>,
        JpaSpecificationExecutor<ServiceOrderJpaEntity> {
    // ... método findByIdForUpdate existente, sem mudança
}
```

## Interfaces e fluxo de dados

```
GET /api/service-orders?status={status}&customerId={uuid}&technicianId={uuid}&priority={priority}
```

Todos os query params são opcionais e combináveis com AND (aplicado pela conjunção de predicados acima).

```java
@GetMapping
@Operation(summary = "List service orders, optionally filtered by status, customer, technician or priority")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service orders listed"),
        @ApiResponse(responseCode = "400", description = "Invalid filter value")
})
public ResponseEntity<List<ServiceOrderResponse>> list(
        @RequestParam(required = false) ServiceOrderStatus status,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID technicianId,
        @RequestParam(required = false) Priority priority) {
    return ResponseEntity.ok(
            listServiceOrdersUseCase.execute(new ServiceOrderSearchCriteria(status, customerId, technicianId, priority)));
}
```

Resposta de sucesso: `200 OK` com `List<ServiceOrderResponse>` — array simples (`[]` quando vazio), cada
item no mesmo formato completo já retornado por `GET /{id}` (nenhum DTO novo).

`GET /{id}` continua exatamente como está — mapeado antes deste método na classe do controller (ordem de
declaração não afeta roteamento Spring, que já resolve `/{id}` vs. coleção sem ambiguidade por não haver
sobreposição de path).

## Tratamento de erros

- `400 Bad Request`, código `VALIDATION_ERROR` — `status`/`priority` fora do enum ou `customerId`/
  `technicianId` que não seja um UUID válido. Nenhum código novo é necessário: Spring já lança
  `MethodArgumentTypeMismatchException` ao converter o `@RequestParam` para o tipo declarado, e o
  `GlobalExceptionHandler` já mapeia essa exceção (junto com `MissingServletRequestParameterException`,
  `ConstraintViolationException` e `HttpMessageNotReadableException`) para `400`/`VALIDATION_ERROR` — o
  mesmo handler genérico usado por todo o projeto, nenhuma mudança em `GlobalExceptionHandler` ou em
  `ServiceLifecycleExceptionHandler`.
- Filtro válido sem resultados: `200 OK` com `[]` — não é um erro.

## Persistência e dados de bootstrap

Nenhuma migration nova: `search` só lê colunas já mapeadas (`statusSnapshot`, `customerId`, `priority`,
`diagnosisAssigneeId`, `service_executions.assigned_technician_id`). Nenhum dado é classificado como
seed — esta feature não introduz nenhuma tabela ou dado de referência novo.

## Segurança e operação

- Autorização: reaproveita a regra já existente em `SecurityConfig` para `/api/service-orders/**`
  (`hasAnyAuthority("MANAGER", "TECHNICIAN", "ADMIN")`, `SecurityConfig.java:68`) — o padrão Ant
  `/api/service-orders/**` já cobre `/api/service-orders` (a base, sem sufixo), então nenhuma regra nova
  é necessária.
- Nenhum dado sensível adicional exposto: o payload de cada item já é retornado hoje por
  `GET /api/service-orders/{id}`; esta feature só agrega múltiplos desses payloads em uma resposta.
- `technicianId`/`customerId` não são validados contra a existência de um Technician/Customer — mesmo
  padrão de referência apenas por UUID já usado no restante do módulo (ex.: `assignTechnician` não
  valida especialidade/disponibilidade, ver TD-002 em `.claude/rules/epic-3-service-lifecycle.md`); um
  filtro com um UUID que não corresponde a ninguém simplesmente não casa com nenhuma `ServiceOrder`.
- Superfície de abuso: nenhum novo vetor além do já existente em todo endpoint de leitura do módulo — é
  uma consulta somente-leitura sem paginação, então uma base muito grande de `ServiceOrder`s poderia
  gerar uma resposta grande; aceitável para o volume esperado de um MVP (mesma escolha já feita em
  `GET /api/vehicles`/`GET /api/customers`, que também não paginam).

## Estratégia de testes

### Domínio

Nenhum teste novo: a feature não adiciona nem altera invariante de `ServiceOrder`/`ServiceExecution`.

### Aplicação (novo — `ListServiceOrdersUseCaseTest`)

Teste com um fake `ServiceOrderRepository` (mesmo padrão dos 12 fakes já existentes no módulo,
sobrescrevendo `search` desta vez) cobrindo:

- `execute` delega o `ServiceOrderSearchCriteria` recebido para `repository.search` sem alterá-lo;
- `execute` mapeia cada `ServiceOrder` retornado pelo repositório para `ServiceOrderResponse` via
  `ServiceOrderMapper.toResponse` (mesma asserção de forma já usada em `GetServiceOrderUseCaseTest`);
- `execute` retorna lista vazia quando o repositório não encontra nada.

Este teste cobre orquestração, não a lógica de filtro do `Specification` (que só pode ser validada contra
um banco real — ver teste HTTP abaixo).

### Web (novo — `ServiceOrderControllerListTest`, `@SpringBootTest` com banco real)

Segue o mesmo padrão de `StockItemControllerTest` (que cobre `search`/`type`/`available` diretamente via
HTTP contra o banco de teste, sem teste de repositório dedicado) e reaproveita
`ServiceOrderHttpTestFixture`/`TestAuth` para popular clientes, veículos e ordens de serviço:

- sem filtros: `200` com todas as ordens de serviço criadas no teste;
- banco sem nenhuma ordem de serviço (ou filtro que não casa com nada): `200` com `[]`;
- `?status=<valor>`: retorna só as que têm aquele `statusSnapshot` (cenário com pelo menos duas SOs em
  status diferentes);
- `?customerId=<uuid>`: retorna só as daquele cliente (cenário com SOs de dois clientes diferentes);
- `?technicianId=<uuid>`: dois sub-cenários — casa via `diagnosisAssigneeId` e casa via
  `executions[].assignedTechnicianId` (usando `AssignTechnicianUseCase`/`AssignDiagnosisAssigneeUseCase`
  já existentes para preparar o estado, mesmo padrão de setup usado em
  `ServiceOrderControllerAssignTechnicianTest`);
- `?priority=<valor>`: retorna só as daquela prioridade;
- combinação de dois filtros (`status` + `priority`): aplica AND, não OR;
- `?status=NAO_EXISTE`: `400`/`VALIDATION_ERROR`;
- `?customerId=nao-e-uuid`: `400`/`VALIDATION_ERROR`;
- sem token: `401` (mesmo padrão de autenticação verificado nos demais testes HTTP do módulo);
- token de um papel fora de `MANAGER`/`TECHNICIAN`/`ADMIN` (ex.: `CUSTOMER`): `403`.
- regressão: `GET /api/service-orders/{id}` continua retornando `200`/`404` como hoje (reaproveita
  asserção equivalente à já existente em `GetServiceOrderUseCaseTest`/spec de `track-execution`, sem
  duplicar testes completos).

### Modulith

- `ModuleStructureTest` deve continuar verde; nenhuma dependência nova entre módulos é introduzida.

## Contratos e documentação

- OpenAPI: gerado automaticamente pelo springdoc a partir de `@Operation`/`@ApiResponses`/`@RequestParam`
  — nenhuma anotação manual adicional necessária além do bloco mostrado em "Interfaces e fluxo de dados".
- `OpenApiContractTest.documentEveryCurrentHttpOperation` — adicionar
  `.andExpect(jsonPath("$.paths['/api/service-orders'].get").exists())`.
- Postman: adicionar request "List service orders" na pasta "Service Orders", com exemplos de query
  string para cada filtro (`?status=...`, `?customerId={{customerId}}`, `?technicianId={{technicianId}}`,
  `?priority=...`), consistente com o padrão já usado em "Search available parts and supplies"
  (`stock-items?search=...&type=...`).

## Fora de escopo técnico

- Paginação, ordenação (`sort`) e busca textual livre — confirmado como fora de escopo na
  `functional-spec.md`.
- DTO resumido/enxuto para a listagem — mesmo DTO de detalhamento é reaproveitado.
- Qualquer mudança em `GET /api/service-orders/{id}` ou `GET /api/service-orders/{id}/status`.
- Validação de existência de `customerId`/`technicianId` contra `registration`/Technician.
- Qualquer nova regra de autorização — a existente já cobre o novo endpoint.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada (2026-08-25).
- [x] Technical Spec revisada e aprovada (2026-08-25).

Antes do PR:

- [ ] testes unitários passando;
- [ ] testes de integração aplicáveis passando (`ServiceOrderControllerListTest` contra o banco de teste);
- [ ] `make verify` passando;
- [ ] nenhuma migration nova necessária;
- [ ] OpenAPI atualizado (`OpenApiContractTest` verde);
- [ ] Postman atualizado;
- [ ] nenhuma fronteira do Spring Modulith violada (`ModuleStructureTest` verde).

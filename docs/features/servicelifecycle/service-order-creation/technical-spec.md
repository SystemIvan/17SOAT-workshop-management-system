# Especificação Técnica: Criação de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-creation` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Especificação funcional | `docs/features/servicelifecycle/service-order-creation/functional-spec.md` |

> **Nota:** este documento é retroativo. Descreve a arquitetura já implementada em produção, não uma
> proposta nova. Onde a implementação atual diverge do que seria a escolha ideal, isso é registrado
> explicitamente em vez de omitido.

> A reconciliação de 2026-08-22 foi revisada e aprovada por humano depois da aprovação da especificação funcional.
> Este documento cobre somente o impacto técnico no SDD histórico; a implementação permanece no plano da feature
> `service-order-initial-assessment`.

## Reconciliação RFC-002

A mudança material aprovada em `service-order-initial-assessment` amplia `CreateServiceOrderRequest`,
`ServiceOrder.create(...)`, persistência e `ServiceOrderResponse` com `initialAssessment`. O request passa a exigir
texto não vazio; responses de registros legados podem devolvê-lo como `null`. A especificação técnica daquela feature
é a fonte de verdade para contratos, migration, tratamento de falhas, dados e testes; esta referência evita duplicar
essas regras no SDD histórico.

## Objetivo técnico

Documentar a implementação existente de criação de `ServiceOrder` no módulo `servicelifecycle`, cobrindo
domínio, persistência, caso de uso e contrato HTTP, para fechar o gap de gate SDD identificado em
`EPIC2-REVIEW.md`.

## Contexto e fronteiras

A implementação pertence a `servicelifecycle.serviceorder`.

`ServiceOrder` é o Aggregate Root. `customerId` e `vehicleId` são referências por ID para
`registration.customer` e `registration.vehicle` — nenhum pacote interno desses módulos é importado por
`serviceorder`.

A criação é orquestrada na Application Layer por `CreateServiceOrderUseCase`.

## Estrutura existente

- `serviceorder/domain/model/ServiceOrder.java` (`create(...)`, `Priority`, `VehicleSnapshot`,
  `ServiceOrderStatus`)
- `serviceorder/domain/repository/ServiceOrderRepository.java`
- `serviceorder/application/dto/CreateServiceOrderRequest.java`, `VehicleSnapshotRequest.java`,
  `ServiceOrderResponse.java`, `ServiceOrderMapper.java`
- `serviceorder/application/usecase/CreateServiceOrderUseCase.java`
- `serviceorder/infrastructure/persistence/ServiceOrderJpaEntity.java`,
  `ServiceOrderPersistenceMapper.java`, `ServiceOrderRepositoryImpl.java`
- `serviceorder/infrastructure/web/ServiceOrderController.java` (`POST /api/service-orders`)

## Domínio

### `ServiceOrder.create(...)`

Dois overloads estáticos: um recebendo `priority` explícita e outro que aplica `Priority.NORMAL` como
padrão. `CreateServiceOrderUseCase` sempre resolve a prioridade antes de chamar o domínio (`request.priority()
!= null ? request.priority() : Priority.NORMAL`), então o overload sem `priority` hoje só é exercitado
por `ServiceOrder.create(customerId, vehicleId, vehicleSnapshot)` usado em testes de domínio e no
`FinalizeServiceOrderFlowApplicationModuleTest`.

Toda `ServiceOrder` criada:
- recebe um `id` novo (`UUID.randomUUID()`);
- começa com `statusSnapshot = ServiceOrderStatus.RECEIVED`;
- não possui `ServiceExecution` nem `openDiagnosisId`.

### `VehicleSnapshot`

`record` imutável (`licensePlate`, `brand`, `model`, `year`) copiado a partir do
`VehicleSnapshotRequest` no momento da criação (`ServiceOrderMapper.toVehicleSnapshot`). Não existe
nenhum método no domínio que permita alterar um `VehicleSnapshot` já atribuído a uma `ServiceOrder` —
o congelamento é garantido pela ausência de mutador, não por uma checagem em runtime.

### Prioridade

`Priority` é um enum simples (`LOW`, `NORMAL`, `HIGH`, `URGENT`), armazenado por valor. Existe um
método de domínio `ServiceOrder.definePriority(Priority)`, mas ele não tem nenhum caller em `src/main`
hoje (nenhum use case ou endpoint o invoca) — é scaffolding para RF10, fora do escopo desta feature.

## Caso de uso: `CreateServiceOrderUseCase`

Fluxo:

1. mapear `VehicleSnapshotRequest` → `VehicleSnapshot`;
2. resolver `priority` (`request.priority()` ou `Priority.NORMAL`);
3. `ServiceOrder.create(customerId, vehicleId, vehicleSnapshot, priority)`;
4. persistir via `ServiceOrderRepository`;
5. buscar todos os `Technician` não inativos (`TechnicianRepository.findAll()`, filtrando
   `status != INACTIVE`) e notificar cada um via `TechnicianNotificationPort` — falha ao notificar um
   technician não impede notificar os demais nem falha a criação da Service Order;
6. retornar `ServiceOrderResponse`.

O passo 5 (notificação de technicians) pertence funcionalmente à feature
`docs/features/servicelifecycle/notifications-technician-new-so/`, já especificada separadamente; está
descrito aqui apenas porque acontece dentro do mesmo método transacional do use case de criação, não
porque este documento define esse comportamento.

Não há validação de existência de `customerId`/`vehicleId` em `registration` — a Service Order aceita
qualquer UUID informado. Isso é consistente com a "Fonte de dados" documentada em
`docs/features/servicelifecycle/estimate-generation/technical-spec.md` (nenhuma leitura viva de outro
módulo durante a criação) e está registrado como decisão consciente, não como bug, na
`functional-spec.md` desta feature ("Fora de escopo").

## Persistência

`service_orders` já existe desde a migration de baseline
`src/main/resources/db/migration/V20260815000000__initial_schema.sql` — este documento não introduz
nem modifica nenhuma migration (a tabela já é anterior a este gate SDD retroativo e já está em uso por
outras features aprovadas, como `estimate-generation`).

Campos relevantes para esta feature: `id`, `customer_id`, `vehicle_id`, `vehicle_license_plate`,
`vehicle_brand`, `vehicle_model`, `vehicle_year`, `priority`, `status_snapshot`. O `VehicleSnapshot` é
persistido "achatado" nas quatro colunas `vehicle_*` (sem tabela própria), via
`ServiceOrderPersistenceMapper`.

`ServiceOrderJpaEntity` implementa `Persistable<UUID>` retornando sempre `isNew() == false` — o
comentário na classe explica que isso é necessário porque o domínio sempre atribui o `id` (UUID) antes
da persistência, então o Hibernate não pode inferir "é novo" a partir de `id == null`. Sem essa
interface, `save()` tentaria sempre um `UPDATE` em vez de `INSERT` para agregados novos.

## API

`POST /api/service-orders`

Request (`CreateServiceOrderRequest`):
```json
{
  "customerId": "<UUID>",
  "vehicleId": "<UUID>",
  "vehicleSnapshot": {
    "licensePlate": "<string>",
    "brand": "<string>",
    "model": "<string>",
    "year": "<int positivo>"
  },
  "priority": "<LOW|NORMAL|HIGH|URGENT, opcional>",
  "initialAssessment": "<texto não vazio, obrigatório para novas Service Orders>"
}
```

Validação via Bean Validation: `customerId`, `vehicleId` e `vehicleSnapshot` são `@NotNull`;
`vehicleSnapshot.licensePlate/brand/model` são `@NotBlank`; `vehicleSnapshot.year` é `@Positive`.
`priority` não tem `@NotNull` — ausência é tratada pelo use case, não pela validação HTTP. `initialAssessment` é
`@NotBlank`; sua definição completa está em `service-order-initial-assessment`.

Resposta de sucesso: `201 Created` com `ServiceOrderResponse` (inclui `id`, `customerId`, `vehicleId`,
`vehicleSnapshot`, `priority`, `initialAssessment`, `status`, `approvedEstimateIds`, `executions` — vazio na criação).
O campo de triagem é anulável somente na leitura de registros legados.

## Tratamento de erros

- Corpo inválido ou campos obrigatórios ausentes → `400 Bad Request`, código `VALIDATION_ERROR`
  (`GlobalExceptionHandler`, comportamento já compartilhado com os demais endpoints do módulo).
- Não existe cenário de conflito ou "not found" na criação em si — `customerId`/`vehicleId`
  inexistentes em `registration` não são rejeitados (ver "Fora de escopo" na `functional-spec.md`).

## Segurança e operação

- Não há mecanismo de autenticação/autorização no projeto hoje (nenhuma dependência Spring Security);
  este endpoint segue o mesmo padrão — sem controle de acesso — dos demais endpoints já existentes.
  Não é uma lacuna introduzida por esta feature.
- IDs são tratados como UUID; nenhuma concatenação manual de entrada em SQL.
- Nenhum dado pessoal do Customer é armazenado nesta feature além do `customerId` (referência opaca);
  `VehicleSnapshot` contém dados do veículo, não do Customer.

## Estratégia de testes

### Domínio
- `ServiceOrderTest.newServiceOrderStartsAsReceived` (já existente) cobre status inicial `RECEIVED`.

### Application
- `CreateServiceOrderUseCaseTest` (já existente) cobre o fluxo de notificação de technicians (sucesso,
  ausência de technicians ativos, falha parcial de notificação não bloqueando os demais).

### Web
- `ServiceOrderControllerCreateTest` (adicionado ao fechar o gap de teste HTTP identificado no
  `EPIC2-REVIEW.md`) cobre: `201` com payload completo (incluindo congelamento do `VehicleSnapshot` na
  resposta), prioridade padrão `NORMAL`, e `400`/`VALIDATION_ERROR` para `customerId` ausente,
  `vehicleSnapshot` ausente e campos do `VehicleSnapshot` em branco/inválidos.

## Fora de escopo técnico

- validação de existência cruzada de `customerId`/`vehicleId` em `registration`;
- qualquer alteração em `ServiceOrder.definePriority` ou exposição de endpoint para RF10;
- lógica de notificação de technicians (coberta por outra feature já especificada);
- diagnóstico, Estimate, execução ou finalização.

## Gates de validação

Antes da implementação:

- [x] Functional Spec aprovada.
- [x] Technical Spec revisada e aprovada.

Antes do PR:

- [x] testes unitários passando (já existentes/adicionados);
- [x] testes de integração aplicáveis passando (`ServiceOrderControllerCreateTest`);
- [ ] `make verify` executado após esta spec ser aprovada;
- [x] nenhuma migration nova necessária (schema já existente);
- [ ] OpenAPI revisado (endpoint já documentado via `@Operation`; confirmar que reflete este
  documento);
- [ ] Postman revisado;
- [x] nenhuma fronteira do Spring Modulith violada (sem import de pacote interno de outro módulo).

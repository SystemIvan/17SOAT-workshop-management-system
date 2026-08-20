# Plano de Implementação: Criação de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-creation` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Especificação técnica | `./technical-spec.md` |

> **Nota:** plano retroativo. O domínio, a persistência e o contrato HTTP já estavam implementados em
> produção antes deste gate SDD; os checkpoints abaixo registram o que já existia e o que foi
> adicionado especificamente por este esforço de documentação (testes HTTP e as três specs).

## Checkpoints

- [x] Arquitetura e contratos implementados sem violação de fronteiras — `ServiceOrder`,
  `CreateServiceOrderUseCase`, `ServiceOrderController` já existiam; nenhuma importação de pacote
  interno de `registration`/`stockprocurement` em `serviceorder`.
- [x] Persistência, migrations e classificação de seeds concluídas — tabela `service_orders` já parte
  da migration de baseline (`V20260815000000__initial_schema.sql`); nenhuma migration nova necessária
  por esta feature. Classificação: mandatory reference data não se aplica (é dado transacional, não
  seed); não há seed automático de `ServiceOrder` em produção.
- [x] Comportamento de domínio e aplicação implementado — `ServiceOrder.create`, congelamento do
  `VehicleSnapshot`, prioridade padrão `NORMAL`.
- [x] Testes automatizados e `make verify` aprovados — ver "Evidências de verificação".
- [x] Revisão de segurança concluída, com achados e mitigações registrados — ver seção abaixo.
- [x] OpenAPI, Postman e documentação do projeto atualizados — endpoint já documentado via
  `@Operation`/springdoc e já presente na collection Postman; nenhuma mudança de contrato nesta
  feature, então nenhuma atualização foi necessária.

## Revisão de segurança

- **Validação de entrada / mass assignment**: `CreateServiceOrderRequest` usa Bean Validation
  (`@NotNull`, `@NotBlank`, `@Positive`); DTO dedicado, sem exposição de entidade de domínio/JPA na
  API. OK.
- **Autenticação/autorização**: não há mecanismo de autenticação no projeto; este endpoint segue o
  mesmo padrão (sem controle de acesso) já presente nos demais endpoints. Risco pré-existente,
  registrado, não introduzido nem agravado por esta feature. Não bloqueia esta entrega — é uma lacuna
  de plataforma, fora do escopo de uma feature de bounded context.
- **Exposição de dados de Customer/Vehicle**: a resposta expõe `customerId`/`vehicleId` (IDs opacos) e
  o `VehicleSnapshot` (placa, marca, modelo, ano) — nenhum dado do Customer além do ID. OK.
- **Segredos/logs sensíveis**: nenhum segredo manipulado; log de notificação de technicians usa apenas
  IDs (ver `docs/features/servicelifecycle/notifications-technician-new-so/`, fora do escopo aqui).
- **SQL/persistência/migration**: nenhuma migration nova; persistência via Spring Data JPA, sem SQL
  manual. OK.
- **Erros e disclosure**: validação inválida retorna `VALIDATION_ERROR` via `GlobalExceptionHandler`,
  sem stack trace nem detalhe de SQL. OK.
- **Dependências novas**: nenhuma.
- **Abuso**: `customerId`/`vehicleId` arbitrários (não existentes em `registration`) são aceitos sem
  erro — permite criar Service Orders "órfãs". Registrado como decisão consciente na
  `functional-spec.md` ("Fora de escopo"), não como falha de segurança: não há mecanismo de
  autenticação hoje que torne isso um vetor de abuso diferente de qualquer outro endpoint do projeto.

Nenhum achado crítico/alto pendente.

## Evidências de verificação

- `./mvnw test -Dtest=ServiceOrderControllerCreateTest,CreateServiceOrderUseCaseTest` — 8 testes, 0
  falhas (5 novos em `ServiceOrderControllerCreateTest`, 3 já existentes em
  `CreateServiceOrderUseCaseTest`).
- `./mvnw verify` (equivalente a `make verify`) — build completo, 2026-08-20, sem falhas.
- Revisão manual do contrato Postman/OpenAPI: `POST /api/service-orders` já presente na collection
  (`docs/api/postman/workshop-management-system.postman_collection.json:197`) e documentado via
  `@Operation` em `ServiceOrderController`; nenhuma divergência encontrada em relação a este documento.

## Rollback ou recuperação

N/A — este gate SDD é documentação retroativa de código e schema já em produção; não há deploy nem
migration novos associados a este plano. Os dois testes adicionados
(`ServiceOrderControllerCreateTest`) podem ser revertidos isoladamente via `git revert` sem qualquer
impacto em dado persistido.

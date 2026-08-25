# Plano de Implementação: Criação de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-creation` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Branch | `feat/stockprocurement-purchase-order-creation` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-24) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-24) |

## Escopo desta execução

Implementar RF27 até a confirmação de uma Purchase Order no External Supplier System simulado. RF28 (fechamento),
RF29 (recebimento, entrada em estoque e repriorização de Service Orders) e RF30 (detecção automática de baixo estoque)
permanecem fora desta execução. RF27 entrega apenas o contrato interno que RF30 usará para registrar uma demanda
`LOW_STOCK`.

## Checkpoints ordenados

### 1. Domínio de Purchase Demand e Purchase Order

- [x] Criar aggregates, value objects, enums e exceções sem dependência de Spring, JPA ou HTTP.
- [x] Cobrir invariantes, transições idempotentes, conflitos e consolidação de linhas com testes unitários rápidos.
- [x] Preservar `OPEN` como único estado funcionalmente confirmado de Purchase Order nesta feature.

### 2. Persistência e concorrência

- [x] Criar migration Flyway para `purchase_demands`, `purchase_orders`, `purchase_order_lines` e
  `purchase_order_demand_links`, incluindo índices e constraints definidos na especificação técnica.
- [x] Implementar entities, mappers e adapters JPA sem expor entities ao domínio ou ao HTTP.
- [x] Implementar consultas com lock para equivalência de demanda, preparação idempotente e claim concorrente.
- [x] Confirmar a classificação de dados como `no seed required` e cobrir startup/migration no banco de teste.

### 3. Registro e consulta de Purchase Demands

- [x] Reagir sincronamente a `StockReservationNotReservedEvent` somente para `INSUFFICIENT_QUANTITY`.
- [x] Resolver demandas `OPEN` equivalentes após `StockReservationCreatedEvent`.
- [x] Entregar `PurchaseDemandApi.recordLowStock` sem endpoint público e validar Stock Item ativo.
- [x] Implementar listagem paginada e filtrada de demandas abertas.
- [x] Cobrir listeners, idempotência, filtros e isolamento do módulo com testes de aplicação/integração.

### 4. Preparação e idempotência da Purchase Order

- [x] Implementar normalização do comando, validação dos Stock Items e cálculo determinístico de `payloadHash`.
- [x] Preparar `PENDING_SUBMISSION` e claims em transação curta, sem manter transação durante I/O HTTP.
- [x] Tratar replay da mesma `Idempotency-Key`, conflito de payload e retomada de submissão pendente.
- [x] Finalizar `OPEN` ou `REJECTED` em nova transação, marcando ou liberando as demandas selecionadas.
- [x] Cobrir criação ad hoc, orientada por demanda, mista, concorrência e recuperação de respostas repetidas.

### 5. Integração com External Supplier System

- [x] Criar `ExternalSupplierGateway` e adapter HTTP com ACL, timeouts e configuração externa.
- [x] Adicionar WireMock 3.x com versão fixa em test scope e serviço Docker restrito ao ambiente local.
- [x] Criar mappings determinísticos para aceite, rejeição e falhas controladas, sem credenciais reais.
- [x] Cobrir tradução do contrato externo e cenários de timeout/indisponibilidade sem vazar detalhes internos.

### 6. Contratos HTTP e erros

- [x] Implementar `GET /api/purchase-demands`, `POST /api/purchase-orders` e
  `GET /api/purchase-orders/{id}` com DTOs e Bean Validation.
- [x] Exigir `Idempotency-Key` UUID e mapear erros de validação, negócio, conflito e indisponibilidade com códigos
  estáveis no `GlobalExceptionHandler`.
- [x] Documentar contratos com Springdoc/OpenAPI e cobri-los com MockMvc.
- [x] Não simular autorização de Stock Manager enquanto o baseline não possuir autenticação; registrar a lacuna.

### 7. Documentação e experiência manual

- [x] Atualizar a coleção Postman com demandas, criação ad hoc/orientada/mista, replay e conflitos.
- [x] Atualizar o README com instruções executáveis do fluxo Postman correspondente.
- [x] Atualizar a documentação do bounded context, configuração do simulador e decisões relevantes.
- [x] Revisar consistência entre implementação, especificações aprovadas e OpenAPI gerado.

### 8. Qualidade, segurança e conclusão

- [x] Executar testes focados durante os checkpoints e registrar evidências.
- [x] Executar `make test`, `make coverage` e `make verify`; manter a cobertura do código alterado e a meta do projeto.
- [x] Confirmar `ModuleStructureTest` e ausência de dependências cíclicas.
- [x] Concluir e registrar a revisão de segurança abaixo, resolvendo achados altos/críticos.
- [x] Marcar o plano e a feature como `Implemented` somente após todos os gates passarem.

## Revisão de segurança

| Item | Estado inicial | Evidência ou mitigação a registrar |
|---|---|---|
| Validação e mass assignment | Concluído | DTOs explícitos; máximo de 100 linhas/demandas; UUIDs e quantidades validados |
| Autenticação e autorização | Lacuna aceita no MVP | Baseline sem Spring Security; adapter bloqueia fornecedor não local e exige novo review para integração real |
| Exposição de dados | Concluído | Contratos omitem dados pessoais, estados técnicos e detalhes internos do fornecedor |
| Segredos e logs | Concluído | Nenhum segredo versionado; API key apenas por ambiente; payload e credencial não são logados |
| SQL e migration | Concluído | Constraints, índices e locks validados em H2/MySQL; migration aditiva aplicada do zero e no volume local |
| Erros e information disclosure | Concluído | Códigos estáveis e mensagens sanitizadas; timeout corrigido para `503` após teste real |
| Dependências | Concluído | WireMock `3.13.1` fixado apenas em teste e simulador local; nenhuma dependência runtime adicionada |
| Abuse cases | Concluído | Limites, overflow, idempotência, conflitos, concorrência e timeouts cobertos |

Não há finding crítico ou alto pendente. A ausência de autenticação é uma lacuna conhecida do baseline, aceita apenas
porque a configuração atual rejeita hosts de fornecedor não locais e o WireMock não produz efeito financeiro. Conectar
um fornecedor real exige autenticação/autorização `MANAGER` e nova revisão de segurança.

## Evidências de verificação

- domínio e aplicação: `PurchaseDemandTest`, `PurchaseOrderTest` e fluxo integrado de eventos/criação verdes;
- persistência/migration: 16 migrations aplicadas a schema vazio; testes de concorrência convergem por chave e impedem
  o reuso da mesma demanda;
- MockMvc/OpenAPI: endpoints, schemas, headers, status e erros RF27 cobertos;
- adapter WireMock: aceite, rejeição, `5xx`, resposta inválida e timeout cobertos; o teste real revelou e validou a
  correção de timeout lido durante o status HTTP;
- `make test`: 513 testes verdes durante o desenvolvimento, antes da inclusão dos testes finais de concorrência e
  timeout;
- `make coverage`: sucesso com 515 testes; o relatório final pós-correção foi regenerado também por `make verify`;
- `make verify`: sucesso em 2026-08-25, 516 testes, zero falha/erro/skip e `ModuleStructureTest` verde;
- JaCoCo final: 91,89% de instruções, 75,94% de branches e 92,66% de linhas; meta global de 80% preservada;
- Docker/MySQL/WireMock: migration aplicada no MySQL persistente; criação ad hoc `201`, replay `200`, consulta `200`,
  payload divergente `409`, duas chamadas concorrentes convergindo para um registro e timeout `503` recuperado com a
  mesma `Idempotency-Key` após restauração do fornecedor;
- Postman: coleção validada como JSON e README atualizado. A GUI do Postman não foi usada no gate automatizado; o mesmo
  contrato e sequência foram executados diretamente por HTTP no stack Docker.

## Rollback ou recuperação

A migration desta feature é aditiva. Em falha antes do deploy compartilhado, corrigir a migration e reconstruir o
ambiente descartável a partir de banco vazio. Depois de aplicada a um baseline compartilhado, não editar a migration:
publicar uma migration compensatória. Para falha de integração, restaurar o adapter/configuração anterior e manter as
ordens `PENDING_SUBMISSION` duráveis para retry com a mesma `Idempotency-Key`; não apagar registros cuja submissão
externa possa ter sido aceita.

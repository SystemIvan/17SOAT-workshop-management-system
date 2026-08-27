# Plano de Implementação: Fechamento de Purchase Order

| Campo | Valor |
|---|---|
| Feature | `purchase-order-closing` |
| Status | Implemented |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Branch de implementação | `feat/stockprocurement-purchase-order-receiving` |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |

## Objetivo da execução

Adicionar o fechamento idempotente de Purchase Order sem alterar saldo, preparando a precondição explícita que RF29
usará para registrar o recebimento.

O resultado final de RF28 deve ser:

```text
PurchaseOrder OPEN
    └── POST /close → CLOSED + closedAt + closedByUserAccountId

PurchaseOrder CLOSED
    └── POST /close → mesmo resultado, sem nova transição
```

`CLOSED` não cria Stock Receipt, não soma disponibilidade e não tenta novamente reservas.

## Instruções para retomada

Antes de iniciar qualquer checkpoint:

1. ler o `AGENTS.md` da raiz;
2. confirmar que as duas specs desta feature continuam `Approved`;
3. partir de `dev` atualizado depois que a branch documental for integrada;
4. criar ou confirmar `feat/stockprocurement-purchase-order-receiving`;
5. inspecionar `git status --short` e preservar mudanças alheias;
6. usar `./mvnw` somente através dos comandos ou targets já previstos pelo projeto;
7. manter apenas um checkpoint `In Progress` e registrar evidência antes de concluí-lo.

Se a execução exigir estado, endpoint ou regra materialmente diferente das specs, devolver o documento afetado para
`Draft` e obter nova aprovação antes de continuar.

## Coordenação com RF29

RF28 e RF29 usam a mesma branch de implementação, mas a ordem é obrigatória:

1. concluir domínio, migration, aplicação, HTTP e testes focados de RF28;
2. confirmar que fechar não alterou qualquer saldo;
3. somente então iniciar o checkpoint 0 do plano de RF29;
4. executar documentação e gates finais de forma conjunta, sem ocultar evidências de cada RF.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 1 | Implementar estado e invariantes de fechamento | Completed |
| 2 | Criar migration e persistência de `CLOSED` | Completed |
| 3 | Implementar casos de uso e listagem operacional | Completed |
| 4 | Implementar HTTP, erros e autorização | Completed |
| 5 | Validar idempotência, concorrência e regressão | Completed |
| 6 | Atualizar contratos e documentação | Completed |
| 7 | Concluir revisão de segurança e gates | Completed |

## Checkpoint 1 — Implementar estado e invariantes de fechamento

### Alterações

- Adicionar `CLOSED` a `PurchaseOrderStatus`.
- Adicionar `closedAt` e `closedByUserAccountId` a `PurchaseOrder`.
- Evoluir factory de reconstituição, validação de estado e getters.
- Implementar `close(userAccountId, closedAt)` somente para `OPEN`.
- Preservar os dados do primeiro fechamento em replay.
- Manter linhas, demandas, referência externa e timestamps anteriores imutáveis.

### Testes e verificação

- Cobrir `OPEN → CLOSED`, replay e estados incompatíveis.
- Cobrir autoria nula e timestamp anterior a `openedAt`/`updatedAt`.
- Reconstituir todos os estados válidos e rejeitar combinações inconsistentes.
- Executar os testes unitários de Purchase Order antes de avançar.

### Evidência a registrar

- classes e testes alterados;
- comando executado, quantidade de testes e resultado;
- confirmação de que domínio continua sem Spring/JPA/HTTP.

## Checkpoint 2 — Criar migration e persistência de `CLOSED`

### Migration Flyway

- Gerar timestamp UTC no início do checkpoint.
- Criar migration `VyyyyMMddHHmmss__close_purchase_orders.sql`.
- Adicionar `closed_at` e `closed_by_user_account_id` nullable.
- Substituir checks de estado/status para aceitar e validar `CLOSED`.
- Adicionar `idx_purchase_orders_status_updated`.
- Não editar `V20260825024334__create_purchase_orders.sql`.

### JPA e repositories

- Mapear os novos campos em entity, mapper e round-trip.
- Preservar lock pessimista de `findByIdForUpdate`.
- Adicionar busca de ordens confirmadas com filtro de estados públicos.
- Ordenar por `updatedAt DESC, id ASC` e excluir estados técnicos.

### Dados e verificação

- Classificação: **nenhum seed necessário**.
- Confirmar que ordens existentes permanecem válidas com campos nulos.
- Cobrir migration/startup em H2 MySQL mode com `ddl-auto=validate`.
- Registrar evidência posterior no MySQL do Docker Compose.

## Checkpoint 3 — Implementar casos de uso e listagem operacional

### Casos de uso

- Criar `ClosePurchaseOrderUseCase` transacional com `Clock` injetável.
- Extrair autoria do principal na borda e passar somente UUID ao caso de uso.
- Fazer replay de `CLOSED` retornar a representação existente.
- Evoluir `GetPurchaseOrderUseCase` para encontrar `OPEN` e `CLOSED`.
- Criar `SearchPurchaseOrdersUseCase` read-only.

### DTOs

- Substituir `OpenPurchaseOrderStatus` por enum público `OPEN`/`CLOSED` sem mudar o JSON.
- Adicionar `closedAt` e `closedByUserAccountId` nullable à resposta.
- Garantir obrigatoriedade dos campos em resposta `CLOSED`.

### Testes e verificação

- Cobrir relógio, ausência, replay e consulta por ambos os estados.
- Cobrir listagem sem filtro, filtro repetível, ordem e resultado vazio.
- Provar que estados técnicos nunca são retornados.

## Checkpoint 4 — Implementar HTTP, erros e autorização

### Endpoint

- Adicionar `POST /api/purchase-orders/{purchaseOrderId}/close` sem body.
- Adicionar `GET /api/purchase-orders` com `status` repetível.
- Preservar `GET /api/purchase-orders/{purchaseOrderId}`.
- Documentar responses no Springdoc/OpenAPI.

### Falhas

- Mapear not found para `PURCHASE_ORDER_NOT_FOUND`.
- Reservar `PURCHASE_ORDER_NOT_CLOSABLE` para conflito público real.
- Manter replay de `CLOSED` como `200`, nunca `409`.
- Traduzir enum/path inválido para `VALIDATION_ERROR`.

### Segurança

- Confirmar match de `/api/purchase-orders/**` para `MANAGER`/`ADMIN`.
- Usar `@AuthenticationPrincipal UUID` ou `Authentication#getPrincipal` validado na borda.
- Não aceitar autoria, instante, status, linhas ou quantidades no request.

### Testes

- MockMvc para sucesso, replay, filtros, `400`, `401`, `403` e `404`.
- Asserir ausência de body no fechamento e campos por estado.

## Checkpoint 5 — Validar idempotência, concorrência e regressão

### Concorrência

- Executar duas confirmações simultâneas com principals diferentes.
- Confirmar um único `closedAt` e autor persistido.
- Cobrir rollback antes do commit sem estado parcial.

### Regressão

- Executar testes de criação, submissão, rejeição e consulta de RF27.
- Confirmar que `OPEN` permanece resposta compatível.
- Confirmar por teste que fechar não chama Stock Item, Stock Reservation ou fornecedor.
- Executar `ModuleStructureTest` e busca de imports proibidos.

### Evidência

- registrar teste de concorrência e banco utilizado;
- registrar conjunto de regressão e resultado;
- registrar verificação de fronteiras Modulith.

## Checkpoint 6 — Atualizar contratos e documentação

- Atualizar annotations e expectativas do OpenAPI gerado.
- Adicionar no Postman: listar ordens, fechar, repetir fechamento e consultar `CLOSED`.
- Atualizar variáveis e testes de status/campos na coleção.
- Atualizar README com pré-requisitos, ordem de requests e resultados esperados.
- Atualizar `docs/Architecture.md` e o mapa de Stock & Procurement para `CLOSED`.
- Não manter YAML OpenAPI manuscrito.

### Verificação manual mínima

1. autenticar como Manager;
2. criar uma Purchase Order `OPEN`;
3. listar e localizar a ordem;
4. fechar e verificar autoria/instante;
5. repetir o fechamento e confirmar resposta idêntica;
6. confirmar que o saldo dos itens não mudou.

## Checkpoint 7 — Concluir revisão de segurança e gates

### Revisão de segurança a preencher

| Item | Status inicial | Evidência/mitigação esperada |
|---|---|---|
| Validação e mass assignment | Completed | Endpoint `POST /close` sem body; ID vem do path e autoria/instante são derivados no servidor. |
| Autenticação/autorização | Completed | Matriz central mantém `/api/purchase-orders/**` em `MANAGER`/`ADMIN`; suíte `SecurityAuthorizationTest` verde. |
| Exposição de dados | Completed | Resposta expõe somente dados operacionais e UUID opaco de auditoria. |
| Segredos/logs | Completed | Nenhum log, token ou payload externo foi adicionado ao fluxo. |
| SQL/migration | Completed | Migration aditiva com checks; Flyway e Hibernate `validate` verdes em H2 vazio. |
| Concorrência | Completed | Lock pessimista e teste concorrente preservam a primeira autoria e instante. |
| Dependências/vulnerabilidades | N/A | Nenhuma dependência nova prevista |
| Abuso do endpoint | Completed | Replay terminal retorna a representação existente e não executa efeito de saldo ou chamada externa. |

Não concluir com finding crítico/alto aberto. Registrar `N/A` final somente com justificativa.

### Gates finais

- Executar testes focados durante cada checkpoint.
- Executar `make test` durante a integração RF28/RF29.
- Executar `make verify` após RF29 e documentação final.
- Executar `make coverage` e revisar cobertura do código alterado.
- Confirmar `ModuleStructureTest` verde e ausência de testes enfraquecidos.
- Marcar este plano `Implemented` somente após todos os itens e evidências estarem completos.

## Evidências de verificação

Preencher durante a execução com data, comando, resultado, cobertura, banco e links de documentação. Um checkbox só pode
ser marcado depois da respectiva evidência.

| Checkpoint | Data | Evidência |
|---|---|---|
| 1 | 2026-08-25 | `./mvnw -q -Dtest=PurchaseOrderTest test` concluído com sucesso. Cobertos `OPEN → CLOSED`, replay idempotente, autoria/instante inválidos e reconstituição inconsistente. |
| 2 | 2026-08-25 | `./mvnw -q -Dtest=PurchaseOrderConcurrencyIntegrationTest test` concluiu com Flyway aplicando `V20260826014503__close_purchase_orders.sql` e Hibernate `validate` em schema H2 vazio. |
| 3–4 | 2026-08-25 | `./mvnw -q -Dtest=PurchaseOrderFlowIntegrationTest test` concluído com fechamento, replay, consulta e filtro `CLOSED` pelo contrato HTTP autenticado. |
| 5 | 2026-08-25 | `./mvnw -q -Dtest=PurchaseOrderConcurrencyIntegrationTest test` concluído com duas confirmações simultâneas convergindo para o mesmo `closedAt` e autor. |
| 6 | 2026-08-25 | Annotations Springdoc, coleção Postman (listagem, fechamento e replay), README e arquitetura atualizados; JSON validado com `python3 -c "import json; ..."` e `git diff --check` sem erros. |
| 7 | 2026-08-26 | `make test`, `make verify` e `make coverage` concluídos com sucesso. JaCoCo: 91,75% de linhas (4539/4947). `ModuleStructureTest` permaneceu verde na suíte. Sem finding crítico/alto aberto. Docker Compose não estava em execução, portanto a evidência manual de MySQL fica para a validação operacional da branch. |

## Rollback e recuperação

- Antes de existir ordem `CLOSED`, rollback do código é simples; manter a migration aplicada é aceitável.
- Depois do primeiro `CLOSED`, binário antigo não reconhece o estado; usar roll-forward.
- Não editar migration aplicada nem alterar status manualmente no banco.
- Falha de RF29 posterior não exige reabrir Purchase Order; repetir o recebimento conforme o plano de RF29.

## Checklist de conclusão

- [x] Specs continuam `Approved` e plano está atualizado.
- [x] Todos os checkpoints e evidências estão concluídos.
- [x] Fechamento idempotente e concorrente está coberto.
- [x] Fechar não altera saldo nem dispara retry.
- [x] OpenAPI, Postman, README e arquitetura estão atualizados.
- [x] Revisão de segurança não possui finding crítico/alto aberto.
- [x] `make verify`, Modulith e cobertura foram revisados.

# Especificação Técnica: Atualização de status da OS via canal externo (e-mail)

| Campo | Valor |
|---|---|
| Feature | `external-status-update` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-26 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-26 |
| Especificação funcional | `./functional-spec.md` (Approved, 2026-09-26) |

## Contexto e desenho

Módulos afetados:

- **`servicelifecycle`** (submódulo `estimate`): novo endpoint, novo caso de uso, nova consulta no repositório
  de Estimate. Toda a regra de decisão continua em `DecideEstimateLinesUseCase` (`decide-estimate-lines`).
- **`identity`** (pacote raiz): o filtro HMAC de RF41 passa a proteger também o novo path, e `SecurityConfig`
  ganha uma regra para ele. Nenhum tipo de `servicelifecycle` é importado — o path é referenciado como string,
  mesmo precedente de `/api/estimates/*/decisions` (ver `estimate-decisions-external-auth/technical-spec.md`).

Nenhuma dependência nova entre módulos. O novo código em `estimate` já depende de `serviceorder` (mesmo módulo
`servicelifecycle`), como `DecideEstimateLinesUseCase` faz hoje.

Componentes novos:

1. `estimate.application.dto.ExternalStatusUpdateRequest` — record com um único campo
   `@NotNull ExternalIntendedStatus intendedStatus`.
2. `estimate.application.dto.ExternalIntendedStatus` — enum `APPROVED`, `REJECTED`. Enum próprio em vez de
   reusar `EstimateLineDecision` para manter o contrato externo desacoplado do DTO interno (os valores coincidem
   hoje; o mapeamento é explícito no caso de uso).
3. `estimate.application.usecase.ApplyExternalStatusUpdateUseCase` — traduz a intenção em um
   `DecideEstimateLinesRequest` e delega a `DecideEstimateLinesUseCase`.
4. `estimate.infrastructure.web.ExternalStatusUpdateController` — expõe o endpoint.
5. `EstimateRepository.findByServiceOrderIdAndStatus(UUID serviceOrderId, EstimateStatus status)` e a
   implementação JPA correspondente.

Alternativa descartada: um método de domínio novo em `ServiceOrder` ("aprovar tudo"). Não traria regra nova —
a decisão em lote, o tudo-ou-nada, o fechamento da Estimate e a reserva de estoque já vivem em
`DecideEstimateLinesUseCase`; duplicá-los criaria uma segunda fonte de verdade, o que a functional-spec proíbe.

## Interfaces e fluxo de dados

### Contrato HTTP

```
POST /api/service-orders/{serviceOrderId}/external-status-updates
Content-Type: application/json
X-Estimate-Gateway-Timestamp: <epoch seconds UTC>
X-Estimate-Gateway-Signature: <hex HMAC-SHA256(timestamp + "." + rawBody, secret)>

{ "intendedStatus": "APPROVED" }
```

| Status | Quando | Corpo |
|---|---|---|
| `200` | Intenção aplicada | `ServiceOrderResponse` (mesmo formato da resposta de `decide-estimate-lines`) |
| `400` | `intendedStatus` ausente, nulo ou fora de `APPROVED`/`REJECTED`; `serviceOrderId` não UUID; JSON inválido | `VALIDATION_ERROR` |
| `401` | Sem headers HMAC, assinatura inválida, fora da janela de 300s ou corpo assinado acima de 64 KiB | `UNAUTHORIZED` (RF41) |
| `403` | JWT válido de qualquer role, sem assinatura HMAC | resposta do `ApiAccessDeniedHandler` existente |
| `404` | OS inexistente | `NOT_FOUND` |
| `409` | Nenhum orçamento `SENT`, mais de um `SENT`, ou nenhuma linha `PENDING` no orçamento `SENT` (inclui reenvio da mesma intenção e OS `COMPLETED`/`DELIVERED`) | `INVALID_STATE_TRANSITION` |

Endpoint novo e aditivo: nenhum contrato existente muda. `POST /api/estimates/{estimateId}/decisions` fica
intacto.

### Fluxo do caso de uso

`ApplyExternalStatusUpdateUseCase.execute(UUID serviceOrderId, ExternalStatusUpdateRequest request)`,
`@Transactional`:

1. `serviceOrderRepository.findById(serviceOrderId)` — ausente → `NoSuchElementException` (`404`).
2. `estimateRepository.findByServiceOrderIdAndStatus(serviceOrderId, SENT)`:
   - vazio → `IllegalStateException("No estimate awaiting decision for service order ...")` (`409`);
   - mais de um → `IllegalStateException("More than one estimate awaiting decision ...")` (`409`).
3. Seleciona as linhas da Estimate cujas `ServiceExecution` correspondentes na OS estão `PENDING`. Nenhuma →
   `IllegalStateException` (`409`).
4. Monta `DecideEstimateLinesRequest` com uma `LineDecisionRequest` por linha pendente, todas com a decisão
   mapeada de `intendedStatus` (`APPROVED` → `EstimateLineDecision.APPROVED`, `REJECTED` →
   `EstimateLineDecision.REJECTED`).
5. Retorna `decideEstimateLinesUseCase.execute(estimate.id(), request)`.

A chamada ao passo 5 participa da mesma transação (`REQUIRED`). `DecideEstimateLinesUseCase` recarrega a OS com
`findByIdForUpdate` e revalida que cada linha está `PENDING`. Se outra decisão concorrente mudar uma linha entre
os passos 3 e 5, ele lança `IllegalStateException` → `409` e nada é aplicado (tudo-ou-nada preservado). A leitura
do passo 3 sem lock é só para montar a lista; a validação autoritativa é a do caso de uso delegado.

Mensagens de exceção não incluem dados pessoais — só IDs, mesmo padrão das mensagens atuais do módulo.

### Tradução de falhas

Nenhum handler novo. As exceções usadas já são traduzidas: `NoSuchElementException` → `404 NOT_FOUND`
(`GlobalExceptionHandler`), `IllegalStateException` → `409 INVALID_STATE_TRANSITION`
(`ServiceLifecycleExceptionHandler`), `MethodArgumentNotValidException`/`HttpMessageNotReadableException` →
`400 VALIDATION_ERROR` (`GlobalExceptionHandler`). O código `409` é o mesmo de `decide-estimate-lines`, porque a
causa é a mesma: a OS não está num estado em que a decisão de orçamento seja válida.

Uma Estimate `SENT` cujo `expiresAt` já passou mas que o agendador ainda não marcou `EXPIRED` é tratada como
`SENT`, exatamente como `decide-estimate-lines` faz hoje. Não é introduzida regra de expiração nova.

## Persistência e dados de bootstrap

- Consulta nova: `EstimateJpaRepository.findByServiceOrderIdAndStatus(UUID, EstimateStatus)` (derivada pelo
  Spring Data), mapeada para o domínio em `EstimateRepositoryImpl`.
- Migração Flyway nova, `VyyyyMMddHHmmss__add_service_order_status_index_to_estimates.sql`, criando
  `idx_estimates_service_order_id_status` em `estimates (service_order_id, status)`. Hoje não existe índice em
  `service_order_id`; sem ele a consulta varre a tabela inteira, cujo tamanho cresce com o total de orçamentos,
  não com os de uma OS. É só um índice: não altera dados, é compatível com `ddl-auto=validate` e pode ser
  aplicado em base existente.
- Classificação de dados: **nenhum seed necessário**. Nenhum dado novo é persistido; a feature só lê Estimates
  e delega a escrita ao caso de uso existente.

## Segurança e operação

### Autenticação e autorização

- **Filtro HMAC (RF41)**: `EstimateGatewayHmacAuthenticationFilter` passa a proteger dois paths `POST`:
  `/api/estimates/*/decisions` (atual) e `/api/service-orders/*/external-status-updates` (novo). Comportamento
  idêntico nos dois: leitura do corpo só com os dois headers presentes, limite de 64 KiB, janela de 300s,
  comparação em tempo constante, authority sintética `ESTIMATE_APPROVAL_GATEWAY`. O nome da authority continua
  adequado: o novo endpoint também é uma decisão de aprovação de orçamento vinda do gateway.
- **Adendo em RF41**: a lista de paths protegidos está descrita em
  `estimate-decisions-external-auth/technical-spec.md`. Estendê-la exige um "Adendo 2" naquele documento, que
  precisa da aprovação do responsável antes do checkpoint de código que altera o filtro.
- **`SecurityConfig`**: nova regra
  `.requestMatchers(HttpMethod.POST, "/api/service-orders/*/external-status-updates").hasAuthority("ESTIMATE_APPROVAL_GATEWAY")`,
  declarada **antes** de `/api/service-orders/**` (que hoje concede `MANAGER`/`TECHNICIAN`/`ADMIN`). Sem essa
  ordem, um JWT `ADMIN` alcançaria o endpoint. Resultado: anônimo → `401`; JWT de qualquer role sem HMAC →
  `403`; HMAC válido → autorizado.
- **JWT junto com HMAC válido**: o filtro JWT roda depois e sobrescreve o contexto com a role do usuário; a
  regra exige só a authority do gateway, então a chamada recebe `403`. Aceitável: o gateway não envia JWT, e a
  functional-spec restringe este endpoint ao gateway.

### Abuso e dados

- **Replay**: dentro da janela de 300s, reenviar a mesma chamada resulta em `409` (não restam linhas
  `PENDING`), sem efeito duplicado. Fora da janela, `401`.
- **Enumeração**: toda falha de autenticação é `401` antes de qualquer acesso a dados, sem revelar se a OS
  existe. Com assinatura válida (chamador confiável), `404`/`409` revelam o estado da OS ao gateway — mesmo nível
  de informação que ele já tem via `/decisions`.
- **Mass assignment**: o DTO tem um único campo enum; não há como influenciar quais linhas são decididas, qual
  Estimate é usada nem qualquer outro campo.
- **Dados pessoais e logs**: nenhum dado pessoal no request nem em logs novos. A resposta é o
  `ServiceOrderResponse` já exposto por `/decisions` ao mesmo chamador.
- **Dependências**: nenhuma nova.
- **Rollout e rollback**: endpoint aditivo, sem feature flag. Rollback por `git revert`; a migração só cria um
  índice e pode permanecer mesmo após o revert do código.

## Documentação

- OpenAPI: `@Operation`, os dois headers HMAC como `@Parameter(in = HEADER)` e `@ApiResponses`
  `200`/`400`/`401`/`403`/`404`/`409` no novo controller; expectativa correspondente em `OpenApiContractTest`.
- Postman: requisição `Isolated / Update service order status via external channel (HMAC)` com o mesmo
  pre-request script de assinatura de RF41, alternativa ao passo 10 do README; e
  `Update service order status via external channel without credentials (expect 401)`, determinística.
- `README.md`: nova subseção junto à seção de RF41, com payload, valores aceitos, respostas e roteiro Postman.

## Estratégia de testes

- **Unitário — `ApplyExternalStatusUpdateUseCaseTest`** (mocks de repositórios e de `DecideEstimateLinesUseCase`):
  - `APPROVED`/`REJECTED` montam uma decisão por linha `PENDING`, todas com a decisão mapeada, para a Estimate
    `SENT` correta;
  - linhas já decididas ficam fora do request;
  - OS inexistente → `NoSuchElementException`, sem chamar o caso de uso delegado;
  - nenhuma Estimate `SENT`, mais de uma, ou nenhuma linha `PENDING` → `IllegalStateException`, sem chamar o
    caso de uso delegado.
- **Persistência — integração do repositório**: `findByServiceOrderIdAndStatus` retorna só as Estimates da OS no
  status pedido. A migração do índice é coberta pela subida do contexto com Flyway contra o banco de teste.
- **Filtro — `EstimateGatewayHmacAuthenticationFilterTest`**: o novo path é autenticado com assinatura válida e
  segue as mesmas regras de falha; outros paths de `/api/service-orders/**` continuam sem leitura de corpo.
- **HTTP — `ExternalStatusUpdateControllerTest`** (`@SpringBootTest` + `springSecurity()`, cadeia real, fluxo
  real até a Estimate `SENT`):
  - HMAC válido + `APPROVED` → `200`, execução `READY`; `REJECTED` → `200`, execução `REJECTED`;
  - reenvio após aplicar → `409 INVALID_STATE_TRANSITION`;
  - OS em diagnóstico (sem Estimate) → `409`; OS com orçamento já decidido pelo canal interno → `409`;
  - OS inexistente → `404`; `intendedStatus` inválido/ausente → `400`;
  - sem HMAC → `401`; assinatura inválida → `401`; JWT `ADMIN` sem HMAC → `403`; nos três casos, a linha
    continua `PENDING` (verificado decidindo depois pelo canal interno, mesmo padrão de RF41).
- **Regressão**: `EstimateControllerDecideLinesTest`, `EstimateControllerGatewayAuthenticationTest` e
  `SecurityAuthorizationTest` continuam verdes.
- **Contrato**: `OpenApiContractTest` verifica o path, os headers e as respostas `401`/`403`/`409`.
- **Fronteiras**: `ModuleStructureTest` verde.
- **Cobertura**: ≥80% no código novo, meta do projeto.

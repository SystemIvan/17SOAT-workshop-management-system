# Roteiro de teste manual: Atualização de status da OS via canal externo (RF40)

| Campo | Valor |
|---|---|
| Feature | `external-status-update` |
| Endpoint | `POST /api/service-orders/{serviceOrderId}/external-status-updates` |
| Autenticação | Só HMAC do gateway (RF41, `docs/adr/ADR-007-external-gateway-hmac-authentication.md`) |
| Ferramenta | Postman, com `docs/api/postman/workshop-management-system.postman_collection.json` |
| Ambiente | Aplicação em Docker local (`docker compose up -d --build`) |

Este roteiro exercita, contra a aplicação real, os critérios de aceite de `functional-spec.md`. Ele cobre o item
"Chamada real via Postman contra a aplicação em Docker" da Definition of Done de `implementation-plan.md`. A
referência de contrato continua sendo a seção "Atualização de status da OS via canal externo (HMAC)" do
`README.md`.

## Pré-requisitos

1. Aplicação no ar: `http://localhost:8080/swagger-ui.html` responde.
2. Collection importada no Postman, com as variáveis no escopo da collection.
3. Environment do Postman em **"No Environment"**. Um environment com `serviceOrderId` ou `estimateId` tem
   prioridade sobre as variáveis da collection e quebra o roteiro.
4. Variável `baseUrl` = `http://localhost:8080`.
5. Variável `estimateGatewaySecret` igual a `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` da aplicação. Sem `.env`
   sobrescrevendo o segredo, o padrão de `docker-compose.yml` já coincide com o valor da collection.

Requisições do RF40 na collection:

- `Isolated / Update service order status via external channel (HMAC)`: sem JWT; o pre-request script assina o
  corpo e preenche `X-Estimate-Gateway-Timestamp` e `X-Estimate-Gateway-Signature`.
- `Estimates / Update service order status via external channel without credentials (expect 401)`: sem JWT e
  sem assinatura.

Nos cenários em que a resposta esperada não é `200`, as asserções da aba *Test Results* da requisição HMAC
falham, porque esperam `200`. Isso é esperado: o que vale nesses cenários é o status HTTP e o `code` do corpo.

## Parte A — Levar a OS até ter um orçamento com linha `PENDING`

| # | Pasta / requisição | Esperado |
|---|---|---|
| A1 | `Auth / Login (bootstrap admin)` | `200`; `authToken` preenchido |
| A2 | `Registrations / Customer / Create customer` | `201`; `customerId` preenchido |
| A3 | `Registrations / Vehicle / Create vehicle` | `201`; `vehicleId` preenchido |
| A4 | `Registrations / Service Catalog / Create catalog service` | `201`; `catalogServiceId` preenchido |
| A5 | `Service Lifecycle / Technicians / Create technician` | `201`; `technicianId` preenchido |
| A6 | `Stock & Procurement / Create stock item` | `201`; `stockItemId` preenchido (`availableQuantity: 20`) |
| A7 | `Service Lifecycle / Service Orders / Create service order` | `201`; `serviceOrderId` preenchido |
| A8 | `Service Lifecycle / Service Orders / Assign diagnosis assignee` | `200` |
| A9 | `Service Lifecycle / Service Orders / Perform diagnosis` | `200`; `executionId` e `diagnosisId` preenchidos |

Se algum cadastro retornar `409` por duplicidade (placa, SKU, documento), troque o valor no body e reenvie.

### T1 — OS em diagnóstico, sem orçamento → `409`

Execute entre A9 e A10.

- Envie `Isolated / Update service order status via external channel (HMAC)`.
- **Esperado:** `409` com `"code": "INVALID_STATE_TRANSITION"`.

| # | Pasta / requisição | Esperado |
|---|---|---|
| A10 | `Estimates / Generate estimate` | `201`; `estimateId` preenchido |
| A11 | `Estimates / Get estimate` | `200`; linha com a execução `PENDING`; `total` `195.90 BRL` |

**Não envie `Decide estimate lines`.** O RF40 substitui esse passo.

## Parte B — Cenários negativos (nenhum altera estado)

### T2 — Sem credenciais → `401`

- Envie `Estimates / Update service order status via external channel without credentials (expect 401)`.
- **Esperado:** `401`, com o teste da requisição verde.

### T3 — JWT de `ADMIN` sem HMAC → `403`

1. Duplique `Isolated / Update service order status via external channel (HMAC)` (botão direito → *Duplicate*).
2. Na cópia:
   - aba **Authorization**: mude de *No Auth* para **Inherit auth from parent**, para usar `Bearer {{authToken}}`;
   - aba **Scripts → Pre-request**: apague o script, para que os headers HMAC não sejam enviados.
3. Envie.
4. **Esperado:** `403`. Depois apague a cópia.

### T4 — `intendedStatus` inválido → `400`

1. Na requisição HMAC, troque o body para `{"intendedStatus": "EXECUCAO"}` e envie.
   **Esperado:** `400` com `"code": "VALIDATION_ERROR"`.
2. Troque o body para `{}` e envie. **Esperado:** `400` com `"code": "VALIDATION_ERROR"`.
3. Volte o body para `{"intendedStatus": "APPROVED"}`, sem salvar a versão alterada.

### T5 — OS inexistente → `404`

1. Na URL da requisição HMAC, troque `{{serviceOrderId}}` por `00000000-0000-0000-0000-000000000000`. A
   assinatura cobre só o corpo, então continua válida.
2. Envie. **Esperado:** `404` com `"code": "NOT_FOUND"`.
3. Volte a URL para `{{serviceOrderId}}`.

### Conferência — nada mudou

- Envie `Estimates / Get estimate`.
- **Esperado:** a linha continua `PENDING`, o que confirma que T1 a T5 não alteraram estado.
- Envie `Service Lifecycle / Service Orders / Get service order status`.
- **Esperado:** `status` `AWAITING_APPROVAL` e `statusLabel` `AGUARDANDO_APROVACAO`, porque o orçamento enviado ainda
  tem linha pendente (correção `awaiting-approval-status`).

## Parte C — Caminho feliz (`APPROVED`)

### T6 — Aplicar a intenção `APPROVED` → `200`

- Envie `Isolated / Update service order status via external channel (HMAC)` com `{"intendedStatus": "APPROVED"}`.
- **Esperado:**
  - `200`, com os três testes verdes (status `200`, OS correta, nenhuma execução `PENDING`);
  - execução `READY` com `stockReservationId` preenchido, porque o Stock Item tem saldo suficiente.

### T7 — Status da OS

- Envie `Service Lifecycle / Service Orders / Get service order status`.
- **Esperado:** `status` `IN_PROGRESS` e `statusLabel` `EXECUCAO`.

### T8 — Reenvio da mesma intenção → `409`

- Envie de novo a mesma requisição HMAC.
- **Esperado:** `409` com `"code": "INVALID_STATE_TRANSITION"`. `Get service order` mostra a OS igual à de T6.

Opcionalmente, siga os passos 12 a 15 da "Sequência executável" do `README.md` (assign technician, start,
complete, finalize) para confirmar que a OS aprovada por este canal segue o fluxo normal.

## Parte D — Caminho `REJECTED` (nova OS)

1. Repita A7, A8, A9 e A10. Customer, Vehicle, Technician e Stock Item podem ser reaproveitados. As variáveis
   `serviceOrderId`, `executionId` e `estimateId` são sobrescritas.
2. **T9:** na requisição HMAC, troque o body para `{"intendedStatus": "REJECTED"}` e envie.
   **Esperado:** `200`, com a execução `REJECTED`.
3. **T10:** envie `Get service order status`.
   **Esperado:** `status` `COMPLETED` e `statusLabel` `FINALIZADA`, porque todas as execuções estão em estado
   terminal.
4. Volte o body para `{"intendedStatus": "APPROVED"}`.

## Parte E — Orçamento já decidido pelo canal interno (opcional)

1. Repita A7, A8, A9 e A10 para uma terceira OS.
2. Envie `Estimates / Decide estimate lines` (JWT) aprovando a linha. **Esperado:** `200`.
3. **T11:** envie a requisição HMAC. **Esperado:** `409` com `"code": "INVALID_STATE_TRANSITION"`.

## Registro de resultados

| Teste | Cenário | Esperado | Obtido |
|---|---|---|---|
| T1 | OS em diagnóstico, sem orçamento | `409 INVALID_STATE_TRANSITION` | |
| T2 | Sem credenciais | `401` | |
| T3 | JWT `ADMIN` sem HMAC | `403` | |
| T4 | `EXECUCAO` / `{}` | `400 VALIDATION_ERROR` / `400 VALIDATION_ERROR` | |
| T5 | OS inexistente | `404 NOT_FOUND` | |
| — | Linha continua `PENDING` após T1–T5 | sim | |
| T6 | `APPROVED` | `200`; execução `READY` | |
| T7 | Status da OS após `APPROVED` | `IN_PROGRESS` / `EXECUCAO` | |
| T8 | Reenvio da mesma intenção | `409 INVALID_STATE_TRANSITION` | |
| T9 | `REJECTED` | `200`; execução `REJECTED` | |
| T10 | Status da OS após `REJECTED` | `COMPLETED` / `FINALIZADA` | |
| T11 | Orçamento já decidido internamente (opcional) | `409 INVALID_STATE_TRANSITION` | |

Depois da execução, registre os resultados (data, quem executou e o status obtido em cada teste) na seção
"Evidências de verificação" de `implementation-plan.md` e marque o item correspondente da Definition of Done.

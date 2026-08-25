# Workshop Management System

Backend REST API for an automotive workshop, implemented as a Java 21/Spring Boot 4 modular monolith.

## Architecture

The Spring Modulith modules follow the project context map:

- Registrations
- Service Lifecycle
- Stock & Procurement

See [Project Structure](docs/PROJECT-STRUCTURE.md) and [AGENTS.md](AGENTS.md) before changing the application.

## Run locally

Requirements: Java 21 and Docker Compose.

```bash
make docker-up
```

The local Docker environment uses the `dev` profile and loads idempotent demonstration Customer and Stock Item records.
Copy `.env.example` to `.env` to override this behavior. Seeds are disabled in the default application profile.

Useful URLs:

- API base: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- External Supplier System simulator (WireMock): `http://localhost:8089`

## Development commands

```bash
make test
make coverage
make verify
make run-dev
```

Run `make help` for Docker and database commands. `make docker-reset` explicitly deletes the local database volume and is
needed once when adopting the initial Flyway baseline over a database previously created by Hibernate.

## Feature workflow

Feature specifications live under `docs/features/`. HTTP contract changes must update the generated OpenAPI expectations
and the collection at `docs/api/postman/workshop-management-system.postman_collection.json`.

## Teste manual do fluxo principal pelo Postman

Esta seção descreve o fluxo completo da oficina, do cadastro até a entrega do veículo. Ela usa como referência a
coleção [Workshop Management System](docs/api/postman/workshop-management-system.postman_collection.json): importe
esse arquivo no Postman e execute as requisições indicadas abaixo. A ordem das pastas na coleção é organizacional; a
ordem de execução é a desta seção.

### Pré-requisitos e variáveis

É necessário ter Java 21 e Docker Compose. Inicie a API e o MySQL com:

```bash
make docker-up
```

Espere a aplicação estar disponível em `http://localhost:8080/swagger-ui.html`, importe a coleção e mantenha as
variáveis no escopo da coleção. Não há autenticação configurada para essas requisições. `baseUrl` deve conter apenas a
origem, sem `/api`: para a execução local, use `http://localhost:8080`.

| Variável | Como preencher |
| --- | --- |
| `baseUrl` | `http://localhost:8080` localmente. |
| `customerId`, `vehicleId`, `technicianId`, `stockItemId`, `serviceOrderId`, `executionId`, `serviceExecutionId`, `diagnosisId` e `estimateId` | A coleção as atualiza automaticamente quando a respectiva requisição de criação/diagnóstico obtém sucesso. |
| `stockReservationId` | É preenchida pelo script de `Retry stock reservation` quando houver `reservationId`; se a reserva já ocorreu na decisão, copie `executions[0].stockReservationId` da resposta da decisão para consultar ou consumir a reserva. |
| `purchaseDemandId` | É preenchida por `List open purchase demands` com a primeira demanda retornada. |
| `purchaseOrderId` | É preenchida pelas criações de Purchase Order confirmadas pelo simulador. |
| `purchaseOrderIdempotencyKey` | Identifica o comando de criação. Preserve o mesmo UUID e body em retries; gere outro UUID para uma compra diferente. |
| `customerTaxId` | Informe o CPF/CNPJ sem formatação usado para o Customer; é utilizado somente por `Identify customer by CPF/CNPJ`. |
| `catalogServiceId` | Use o UUID de serviço apresentado na coleção. A coleção não possui uma requisição para cadastrar serviço de catálogo; este campo identifica o serviço informado no diagnóstico. |

Os exemplos da coleção usam CPF, placa, chassi e SKU fixos. Em uma base que já tenha sido usada, troque esses valores
por valores únicos antes de criar Customer, Vehicle e Stock Item, e ajuste `customerTaxId` de forma correspondente.
Os IDs retornados em respostas `201 Created` são os que devem ser usados no restante do teste.

### Sequência executável

1. Em `Registrations / Customer`, envie `Create customer`:

   ```http
   POST {{baseUrl}}/api/customers
   ```

   Use o body da coleção, com `name`, `document` e `contactInfo`. Espere `201 Created`; o script grava o `id` retornado
   em `customerId`.

2. Em `Registrations / Vehicle`, envie `Create vehicle with chassis` (ou `Create vehicle without chassis`). O body deve
   manter `"customerId": "{{customerId}}"`; os demais campos são `licensePlate`, `chassis` quando aplicável,
   `brand`, `model`, `year` e `color`.

   ```http
   POST {{baseUrl}}/api/vehicles
   ```

   Espere `201 Created` e confirme que `vehicleId` recebeu o `id` retornado.

3. Em `Service Lifecycle / Technicians`, envie `Create technician` com o exemplo da coleção:

   ```json
   {
     "name": "Joao Technician",
     "specialties": ["MECHANICAL", "DIAGNOSTICS"]
   }
   ```

   A resposta esperada é `201 Created` e preenche `technicianId`.

4. Se o diagnóstico usar peça ou insumo, crie-o agora em `Stock & Procurement / Create stock item`. O exemplo da
   coleção cria a peça que será referenciada no diagnóstico:

   ```json
   {
     "name": "Oil filter",
     "sku": "FILTER-001",
     "type": "PART",
     "availableQuantity": 20,
     "price": { "value": 45.90, "currency": "BRL" }
   }
   ```

   Envie `POST {{baseUrl}}/api/stock-items`, espere `201 Created` e use o `stockItemId` gravado pelo script. A
   quantidade disponível deve ser pelo menos a quantidade exigida no diagnóstico para exercitar a reserva bem-sucedida.

5. Em `Service Lifecycle / Service Orders`, envie `Create service order` em
   `POST {{baseUrl}}/api/service-orders`. Mantenha `customerId` e `vehicleId` nas variáveis da coleção e informe o
   retrato do veículo e a avaliação inicial, como no exemplo:

   ```json
   {
     "customerId": "{{customerId}}",
     "vehicleId": "{{vehicleId}}",
     "vehicleSnapshot": {
       "licensePlate": "ABC1D23",
       "brand": "Fiat",
       "model": "Argo",
       "year": 2024
     },
     "priority": "NORMAL",
     "initialAssessment": "Ruído ao frear relatado pelo cliente"
   }
   ```

   Espere `201 Created`. A resposta registra `serviceOrderId`; consulte já neste ponto `Get service order status`
   (`GET {{baseUrl}}/api/service-orders/{{serviceOrderId}}/status`) e espere `RECEIVED`.

6. Envie `Assign diagnosis assignee`:

   ```http
   PUT {{baseUrl}}/api/service-orders/{{serviceOrderId}}/diagnosis-assignee
   ```

   com `{"technicianId":"{{technicianId}}"}`. A resposta é `200 OK` e apresenta `diagnosisAssigneeId`. Esta é a
   atribuição planejada; ela não substitui o autor efetivo registrado no próximo passo.

7. Envie `Perform diagnosis`:

   ```http
   POST {{baseUrl}}/api/service-orders/{{serviceOrderId}}/diagnosis
   ```

   Para o caminho com peça, use o body da coleção, que inclui `diagnosedByTechnicianId`, um item de serviço com preço e
   `stockRequirements` com `stockItemId`, `type`, `quantity`, `nameSnapshot` e `priceSnapshot`:

   ```json
   {
     "diagnosedByTechnicianId": "{{technicianId}}",
     "items": [{
       "catalogServiceId": "{{catalogServiceId}}",
       "name": "Oil and filter change",
       "price": { "value": 150.00, "currency": "BRL" },
       "stockRequirements": [{
         "stockItemId": "{{stockItemId}}",
         "type": "PART",
         "quantity": 1,
         "nameSnapshot": "Oil filter",
         "priceSnapshot": { "value": 45.90, "currency": "BRL" }
       }]
     }]
   }
   ```

   Espere `200 OK`. O script define `executionId`, `serviceExecutionId` e `diagnosisId` a partir da primeira execução.
   Confirme que a execução mostra `diagnosedByTechnicianId` (autor efetivo) e consulte `/status`: o status esperado é
   `IN_DIAGNOSIS`.

8. Em `Estimates`, envie `Generate estimate`:

   ```http
   POST {{baseUrl}}/api/service-orders/{{serviceOrderId}}/estimates
   ```

   com `{"diagnosisId":"{{diagnosisId}}"}`. O script de pré-requisição usa o `diagnosisId` salvo pela coleção, mesmo
   que exista uma variável de ambiente com o mesmo nome. Espere `201 Created`, guarde o `estimateId` definido pelo
   script e use `Get estimate` (`GET {{baseUrl}}/api/estimates/{{estimateId}}`) para conferir `lines`, seus
   `serviceExecutionId`, preço do serviço e eventuais itens de estoque. Os requisitos ficam congelados ao gerar o
   orçamento.

9. Decida todas as linhas consultadas em `Decide estimate lines`:

   ```http
   POST {{baseUrl}}/api/estimates/{{estimateId}}/decisions
   ```

   Para aprovar a única linha do exemplo, use:

   ```json
   {
     "decisions": [
       { "serviceExecutionId": "{{executionId}}", "decision": "APPROVED" }
     ]
   }
   ```

   A resposta é `200 OK` com a Service Order. Para múltiplas linhas, inclua uma decisão para cada
   `lines[].serviceExecutionId` retornado por `Get estimate`; uma linha já decidida não pode ser decidida novamente.

10. Verifique se a execução aprovada está `READY`.

    - Se o diagnóstico não tiver peça (`"stockRequirements": []`), ela já estará pronta; siga para o passo 11.
    - Se tiver peça, a decisão do orçamento já tenta reservá-la. Com quantidade suficiente, a execução fica `READY` e
      recebe `stockReservationId`; siga para o passo 11. A reserva pode ser consultada nas requisições de Stock.
    - Se ela ficar `AWAITING_ITEMS`, falta material. Execute `Retry stock reservation`, sem body. `RESERVED` a deixa
      `READY`; `NOT_RESERVED` mostra o motivo em `issues` e o fluxo não pode prosseguir até haver quantidade
      disponível. RF27 agora permite criar a Purchase Order para a demanda, mas o recebimento e a reposição do saldo
      continuam fora desta feature (RF29).

11. Para cada execução aprovada que será realizada, envie `Assign technician`:

    ```http
    POST {{baseUrl}}/api/service-orders/{{serviceOrderId}}/executions/{{executionId}}/assign-technician
    ```

    com `{"technicianId":"{{technicianId}}"}`. Espere `200 OK` e confira `assignedTechnicianId`. Não atribua uma
    execução `REJECTED`.

12. Com a execução `READY`, envie, nesta ordem, `Start execution`, `Update execution progress` e `Complete execution`:

    ```http
    POST  {{baseUrl}}/api/service-orders/{{serviceOrderId}}/executions/{{executionId}}/start
    PATCH {{baseUrl}}/api/service-orders/{{serviceOrderId}}/executions/{{executionId}}/progress
    POST  {{baseUrl}}/api/service-orders/{{serviceOrderId}}/executions/{{executionId}}/complete
    ```

    O `PATCH` usa o body `{"note":"Oil drained and filter replaced"}`. Atualmente essa `note` só valida que a
    execução está `IN_PROGRESS`: ela não é persistida nem é devolvida pela API. Portanto, execute-o para testar o
    endpoint, mas não o use como histórico de trabalho. Espere `200 OK` em todas: os estados da execução devem ser,
    respectivamente, `IN_PROGRESS`, `IN_PROGRESS` e `COMPLETED`. Repita o ciclo para cada linha aprovada antes de
    finalizar a ordem.

13. Quando todas as execuções estiverem `COMPLETED` ou `REJECTED`, envie `Finalize service order`:

    ```http
    POST {{baseUrl}}/api/service-orders/{{serviceOrderId}}/finalize
    ```

    com `{"vehicleDelivered":true}`. Espere `200 OK` e `statusSnapshot: "DELIVERED"`. O valor `false`, ou tentar
    finalizar antes de a ordem estar concluída, retorna conflito (`409`).

14. Durante o roteiro, use tanto `Get service order` quanto `Get service order status`. O primeiro retorna o retrato
    completo, inclusive `executions` e `statusSnapshot`; o segundo retorna somente `{ "id", "status" }`. No contrato
    atual, `ServiceOrderResponse.status` está marcado como obsoleto; para a leitura completa, use
    `statusSnapshot` como o campo de acompanhamento.

### Fluxo executável de Purchase Order (RF27)

O `make docker-up` também inicia o WireMock na porta `8089`; a aplicação usa o endereço interno
`http://supplier-simulator:8080`. Não configure um fornecedor real neste MVP: os endpoints ainda não possuem
autenticação/autorização, portanto a restrição ao Stock Manager é funcional e não está tecnicamente aplicada.

Para exercitar a Purchase Order por reparo pendente:

1. Crie um Stock Item ativo com `availableQuantity: 1` e use seu `stockItemId` em um diagnóstico que solicite quantidade
   `3` ou maior. Gere e aprove o orçamento. A reserva insuficiente deixa a execução `AWAITING_ITEMS` e registra uma
   Purchase Demand `PENDING_REPAIR`; repetir a tentativa atualiza a mesma demanda em vez de duplicá-la.
2. Execute `Stock & Procurement / List open purchase demands`. Espere `200 OK`, confirme
   `suggestedQuantity = requestedQuantity - observedAvailableQuantity` e deixe o script preencher
   `purchaseDemandId`.
3. Gere um UUID novo em `purchaseOrderIdempotencyKey` e execute `Create Purchase Order from demand`. Ajuste a quantidade
   da linha para ser pelo menos a sugestão da demanda. Espere `201 Created`, `status: "OPEN"`, referência iniciada por
   `SUP-` e `Location: /api/purchase-orders/{id}`.
4. Execute `Retry same Purchase Order` sem mudar header nem body. Espere `200 OK`, o mesmo `purchaseOrderId` e a mesma
   referência externa. Mudar o body mantendo a chave retorna `409 PURCHASE_ORDER_IDEMPOTENCY_CONFLICT`.
5. Execute `Get Purchase Order` e confirme o snapshot de SKU, nome e tipo. Liste as demandas novamente: a demanda
   comprada não aparece porque passou a `ORDERED`.

Para criação livre do Stock Manager, crie qualquer Stock Item ativo e execute `Create ad hoc Purchase Order`. O request
usa `demandIds: []`, gera uma chave nova e deve retornar `201 Created`. Uma ordem mista mantém um ou mais `demandIds` e
inclui tanto as linhas que cobrem essas demandas quanto linhas ad hoc; itens repetidos são consolidados.

O simulador possui dois SKUs especiais para falhas controladas:

- `SUPPLIER-REJECT-001` retorna `422 SUPPLIER_ORDER_REJECTED`; demandas selecionadas voltam para `OPEN`.
- `SUPPLIER-TIMEOUT-001` demora sete segundos, acima do timeout padrão de cinco segundos, e retorna
  `503 EXTERNAL_SUPPLIER_UNAVAILABLE`. A ordem fica `PENDING_SUBMISSION` internamente; repita exatamente o mesmo POST e
  `Idempotency-Key` depois de restaurar o simulador/comportamento para reconciliar sem duplicar a compra.

RF27 termina quando a ordem está `OPEN`. Ela não recebe os itens, não aumenta `availableQuantity`, não fecha a ordem e
não tenta novamente as reservas por prioridade. Esses comportamentos permanecem em RF28/RF29.

### Bifurcações e acompanhamento de status

`statusSnapshot` é recalculado a partir das execuções e da entrega. Em uma ordem recém-criada ele é `RECEIVED`; após
o diagnóstico aberto, `IN_DIAGNOSIS`; uma execução aprovada sem requisito de estoque, ou com reserva confirmada, deixa
a ordem `IN_PROGRESS`; falta de peça deixa-a `AWAITING_ITEMS`; e todas as execuções terminais (`COMPLETED` ou
`REJECTED`) deixam-na `COMPLETED`. A entrega confirmada no passo final muda-a para `DELIVERED`.

Uma linha rejeitada recebe `REJECTED` e não deve ser atribuída, iniciada nem concluída. Se houver linhas aprovadas e
rejeitadas, execute apenas as aprovadas e complete todas elas; a rejeitada já conta como terminal. Se todas forem
rejeitadas, a ordem alcança `COMPLETED` e ainda pode ser finalizada com `vehicleDelivered: true`.

Embora `AWAITING_APPROVAL` exista entre os valores possíveis de status, a geração do orçamento preserva o diagnóstico
aberto no contrato atual; valide o status sempre pela resposta real de `Get service order status`, especialmente entre
geração e decisão do orçamento. Para erros de validação, referências inexistentes ou transições inválidas, espere os
status HTTP documentados no Swagger, em geral `400`, `404` ou `409`, e corrija a condição antes de continuar.

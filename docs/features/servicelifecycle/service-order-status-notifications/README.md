# Teste Manual de Notificações por E-mail com Mailpit (RF52)

Este guia descreve como validar as notificações por e-mail em ambiente local usando **Mailpit**, um simulador SMTP que oferece interface web para inspecionar e-mails.

## Pré-requisitos

- Docker e Docker Compose instalados
- GNU Make (opcional; você pode usar `docker compose` diretamente)
- Postman com a coleção `docs/api/postman/workshop-management-system.postman_collection.json` importada
- Navegador para acessar Mailpit

## Início rápido

### 1. Subir a aplicação com Mailpit

```bash
make docker-up
# ou, sem Make:
docker compose up -d --build
```

Aguarde até que a aplicação esteja pronta (verifique os logs):

```bash
docker compose logs -f app
# Procure por "Tomcat started on port(s): 8080"
```

### 2. Abrir Mailpit na interface web

Mailpit estará disponível em:

```


```

Você verá uma interface web vazia (nenhum e-mail recebido ainda). A partir daqui, todos os e-mails enviados pela aplicação aparecerão em tempo real nesta interface.

### 3. Importar Postman e configurar

1. Abra o Postman
2. Importe a coleção: `File → Import → docs/api/postman/workshop-management-system.postman_collection.json`
3. Configure as variáveis:
   - `baseUrl`: `http://localhost:8080`
   - Outras variáveis serão preenchidas automaticamente pela coleção

## Teste passo a passo — Fluxo completo (RF52)

### Passo 0: Login (obter token JWT)

**Requisição:** `Auth → Login (bootstrap admin)`

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{"username":"admin","password":"changeme123"}
```

**Resultado esperado:** `200 OK` — o script Postman grava o `token` em `authToken`.

**Mailpit:** Nenhum e-mail ainda.

---

### Passo 1: Criar cliente

**Requisição:** `Registrations / Customer → Create customer`

```http
POST http://localhost:8080/api/customers
```

**Resultado esperado:** `201 Created` — a coleção grava o `customerId` automaticamente.

**Mailpit:** Nenhum e-mail ainda (criar cliente não dispara notificação).

---

### Passo 2: Criar veículo

**Requisição:** `Registrations / Vehicle → Create vehicle`

**Resultado esperado:** `201 Created` — `vehicleId` gravado.

**Mailpit:** Nenhum e-mail ainda.

---

### Passo 3: Criar serviço no catálogo

**Requisição:** `Registrations / Service Catalog → Create catalog service`

```json
{
  "name": "Troca de óleo e filtro",
  "basePrice": { "value": 150.00, "currency": "BRL" }
}
```

**Resultado esperado:** `201 Created` — `catalogServiceId` gravado.

**Mailpit:** Nenhum e-mail ainda.

---

### Passo 4: Criar técnico

**Requisição:** `Service Lifecycle / Technicians → Create technician`

```json
{
  "name": "João Mecânico",
  "specialties": ["MECHANICAL", "DIAGNOSTICS"]
}
```

**Resultado esperado:** `201 Created` — `technicianId` gravado.

**Mailpit:** Nenhum e-mail ainda.

---

### Passo 5: Criar item de estoque (peça)

**Requisição:** `Stock & Procurement / Create stock item`

```json
{
  "name": "Filtro de óleo",
  "sku": "OIL-FILTER-001",
  "type": "PART",
  "availableQuantity": 20,
  "price": { "value": 45.90, "currency": "BRL" }
}
```

**Resultado esperado:** `201 Created` — `stockItemId` gravado.

**Mailpit:** Nenhum e-mail ainda.

---

### Passo 6: Criar Ordem de Serviço (OS)

**Requisição:** `Service Lifecycle / Service Orders → Create service order`

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

**Resultado esperado:** `201 Created` — `serviceOrderId` gravado. Status nominal: **"Recebida"**.

**✋ Mailpit:** Nenhum e-mail ainda. A criação não dispara notificação (regra: "criação não notifica").

---

### Passo 7: Atribuir diagnóstico

**Requisição:** `Service Lifecycle / Service Orders → Assign diagnosis assignee`

```http
PUT http://localhost:8080/api/service-orders/{{serviceOrderId}}/diagnosis-assignee

{"technicianId":"{{technicianId}}"}
```

**Resultado esperado:** `200 OK`.

**Mailpit:** Nenhum e-mail. (Status interno muda, mas nominal continua "Recebida".)

---

### Passo 8: Realizar diagnóstico

**Requisição:** `Service Lifecycle / Service Orders → Perform diagnosis`

```json
{
  "diagnosedByTechnicianId": "{{technicianId}}",
  "items": [{
    "catalogServiceId": "{{catalogServiceId}}",
    "name": "Troca de óleo e filtro",
    "price": { "value": 150.00, "currency": "BRL" },
    "stockRequirements": [{
      "stockItemId": "{{stockItemId}}",
      "type": "PART",
      "quantity": 1,
      "nameSnapshot": "Filtro de óleo",
      "priceSnapshot": { "value": 45.90, "currency": "BRL" }
    }]
  }]
}
```

**Resultado esperado:** `200 OK`. Status nominal: **"Diagnóstico"**.

**Mailpit:** Nenhum e-mail. (Status nominal continua igual.)

---

### Passo 9: **GERAR ORÇAMENTO** — 🎯 Primeiro e-mail!

**Requisição:** `Service Lifecycle / Estimates → Generate estimate`

```json
{"diagnosisId":"{{diagnosisId}}"}
```

**Resultado esperado:** `201 Created`. Status nominal: **"Aguardando Aprovação"** (mudança nominal!).

**🎉 Mailpit — Conferir 2 e-mails:**

1. **E-mail de orçamento** (RF51):
   - **De:** `no-reply@workshop.local`
   - **Assunto:** `Orçamento da OS <serviceOrderId> aguardando sua aprovação`
   - **Corpo:**
     ```
     ID da OS: <serviceOrderId>
     ID do orçamento: <estimateId>
     
     Troca de óleo e filtro: R$ 195,90
     
     Valor total: R$ 195,90
     Validade: <data no formato dd/mm/yyyy>
     ```

2. **E-mail de mudança de status** (RF52):
   - **De:** `no-reply@workshop.local`
   - **Assunto:** `OS <serviceOrderId>: status atualizado para Aguardando Aprovação`
   - **Corpo:**
     ```
     OS <serviceOrderId>
     Novo status: Aguardando Aprovação
     ```

**⚠️ Importante:** Os dois e-mails são **independentes**. O orçamento é de RF51, a mudança de status é RF52. Ambos são enviados neste passo.

---

### Passo 10: Decidir linhas do orçamento (Aprovar)

**Requisição:** `Service Lifecycle / Estimates → Decide estimate lines`

```json
{
  "decisions": [
    {
      "estimateLineId": "<lineId do orçamento>",
      "decision": "APPROVED"
    }
  ]
}
```

**Resultado esperado:** `200 OK`. Status nominal: **"Execução"** (mudança nominal!).

**🎉 Mailpit — Conferir 1 e-mail novo:**

- **E-mail de mudança de status para "Execução"**:
  - **Assunto:** `OS <serviceOrderId>: status atualizado para Execução`
  - **Corpo:**
    ```
    OS <serviceOrderId>
    Novo status: Execução
    ```

---

### Passo 11: Atribuir técnico à execução

**Requisição:** `Service Lifecycle / Service Orders → Assign technician`

```json
{"technicianId":"{{technicianId}}"}
```

**Resultado esperado:** `200 OK`.

**Mailpit:** Nenhum e-mail novo. (Status nominal não muda.)

---

### Passo 12: Iniciar execução

**Requisição:** `Service Lifecycle / Service Orders → Start execution`

**Resultado esperado:** `200 OK`.

**Mailpit:** Nenhum e-mail novo. (Status nominal continua "Execução".)

---

### Passo 13: Atualizar progresso

**Requisição:** `Service Lifecycle / Service Orders → Update execution progress`

```json
{"progress":50}
```

**Resultado esperado:** `200 OK`.

**Mailpit:** Nenhum e-mail novo. (Status nominal continua "Execução".)

---

### Passo 14: **CONCLUIR EXECUÇÃO** — 🎯 Novo e-mail!

**Requisição:** `Service Lifecycle / Service Orders → Complete execution`

**Resultado esperado:** `200 OK`. Status nominal: **"Finalizada"** (mudança nominal!).

**🎉 Mailpit — Conferir 1 e-mail novo:**

- **E-mail de mudança de status para "Finalizada"**:
  - **Assunto:** `OS <serviceOrderId>: status atualizado para Finalizada`
  - **Corpo:**
    ```
    OS <serviceOrderId>
    Novo status: Finalizada
    ```

---

### Passo 15: **FINALIZAR ORDEM** — 🎯 Último e-mail!

**Requisição:** `Service Lifecycle / Service Orders → Finalize service order`

```json
{"vehicleDelivered":true}
```

**Resultado esperado:** `200 OK`. Status nominal: **"Entregue"** (mudança nominal!).

**🎉 Mailpit — Conferir 1 e-mail novo:**

- **E-mail de mudança de status para "Entregue"**:
  - **Assunto:** `OS <serviceOrderId>: status atualizado para Entregue`
  - **Corpo:**
    ```
    OS <serviceOrderId>
    Novo status: Entregue
    ```

---

## Resumo de e-mails esperados

| Passo | Ação | Status nominal | E-mails esperados |
|---|---|---|---|
| 6 | Criar OS | Recebida | ❌ Nenhum (criação não notifica) |
| 8 | Diagnóstico | Diagnóstico | ❌ Nenhum (status nominal não muda) |
| **9** | **Gerar orçamento** | **Aguardando Aprovação** | ✅ **2 e-mails** (orçamento + status change) |
| 10 | Decidir orçamento | Execução | ✅ 1 e-mail (status change) |
| **14** | **Concluir execução** | **Finalizada** | ✅ 1 e-mail (status change) |
| **15** | **Finalizar ordem** | **Entregue** | ✅ 1 e-mail (status change) |

**Total esperado: 5 e-mails**

---

## Inspeção avançada no Mailpit

Clique em cada e-mail na interface do Mailpit para ver:

- **Headers:** De, Para, Assunto
- **Raw:** Conteúdo em texto puro
- **HTML:** Renderização (neste projeto, é texto puro)

### Verificar que o e-mail do cliente está correto

1. Abra a requisição `Create customer` no Postman
2. Note o e-mail gerado (geralmente `customer-<uuid>@example.com`)
3. No Mailpit, clique em um e-mail e veja o campo **To:** — deve coincidir

### Verificar que nenhum dado pessoal aparece em logs

Execute:

```bash
docker compose logs app | grep -i "notification\|email"
```

Você verá linhas como:

```
[INFO] ... ServiceOrderStatusChangedNotificationListener: Notifying status change for serviceOrderId=<uuid>, customerId=<uuid>, previousStatus=..., currentStatus=...
[INFO] ... SmtpCustomerNotificationAdapter: Email sent to c***@e***.com for orderId=<uuid>
```

✅ E-mail mascarado, sem dados pessoais em log.

---

## Teste alternativo: Usar canal `log` simulado (sem Mailpit)

Se quiser testar sem SMTP real, volte ao log simulado:

```bash
APP_NOTIFICATION_EMAIL_CHANNEL=log make docker-up
```

Nos logs da aplicação, você verá:

```
[INFO] ... SimulatedEmailCustomerNotificationAdapter: Service order status change notification | serviceOrderId=... | statusLabel=AGUARDANDO_APROVACAO | email=c***@e***.com
```

❌ Nenhum e-mail real é enviado, mas a lógica é coberta por testes de unidade.

---

## Solução de problemas

### Mailpit não carrega em `localhost:8025`

1. Confirme que o container está rodando:
   ```bash
   docker compose ps
   # Procure por "workshop-mailpit"
   ```

2. Se não aparecer, verifique os logs:
   ```bash
   docker compose logs mailpit
   ```

3. Reinicie:
   ```bash
   docker compose restart mailpit
   ```

### Nenhum e-mail aparece no Mailpit

1. **Verificar se a aplicação está enviando:**
   ```bash
   docker compose logs app | grep -i "email\|notification"
   ```

2. **Conferir a configuração:**
   ```bash
   docker compose exec app env | grep APP_NOTIFICATION
   # Deve mostrar: APP_NOTIFICATION_EMAIL_CHANNEL=smtp
   ```

3. **Verificar conectividade SMTP:**
   ```bash
   docker compose logs app | grep -i "mail\|smtp"
   # Procure por erros de conexão
   ```

### E-mail aparece, mas com conteúdo vazio

1. Verifique se o `Estimate` foi gerado corretamente (Passo 9)
2. Confira se o `Customer` tem e-mail cadastrado (Passo 1)
3. Veja os logs da aplicação para exceções

---

## Limpeza após teste

```bash
# Parar containers
docker compose down

# Remover volume MySQL (se quiser tabula rasa na próxima vez)
docker compose down -v

# Reiniciar limpo
make docker-up
```

---

## Referências

- **RF52:** Notificar o cliente por e-mail a cada mudança de status nominal da OS
- **RF51:** Gerar orçamento com serviços e valores
- **RF33:** Notificar o Customer quando a SO for finalizada (consolidado em RF52)
- **Mailpit:** https://mailpit.axllent.org/
- **Guia de demo no README:** ver seção "Notificações por e-mail (RF52)"

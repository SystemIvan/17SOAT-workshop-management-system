# Épico 3 — Guia de Testes Postman

Guia manual dos endpoints de execução e tracking (RF19–RF24). Não depende de seed de dev — todos os
dados são criados via chamadas reais à API, na ordem abaixo.

## Preparação

### Iniciar a aplicação

```bash
docker-compose up --build
```

Ou localmente com Maven:
```bash
./mvnw spring-boot:run
```

**Base URL:** `http://localhost:8080/api`

Não é necessário o perfil `dev` nem `app.seed.enabled=true` para este guia — os seeders de
`registration`/`stockprocurement` não criam `Technician` nem `ServiceOrder`.

## Fluxo de Teste Completo

### 1. Criar um Técnico
```
POST /technicians

Body:
{
  "name": "João Mecânico",
  "specialties": ["MECHANICAL"]
}
```
Resposta: `201 Created`. **Copie o `id`** → `{{technicianId}}`.

### 2. Criar uma Service Order
```
POST /service-orders

Body:
{
  "customerId": "{{qualquer-uuid}}",
  "vehicleId": "{{qualquer-uuid}}",
  "vehicleSnapshot": {
    "licensePlate": "ABC1D23",
    "brand": "Toyota",
    "model": "Corolla",
    "year": 2020
  },
  "priority": "NORMAL"
}
```
Resposta: `201 Created`, status `RECEIVED`. **Copie o `id`** → `{{serviceOrderId}}`.

`customerId`/`vehicleId` não precisam existir em `registration` para este fluxo — a Service Order só
guarda os IDs e o `vehicleSnapshot` congelado (RF09); não há validação cruzada de módulo aqui.

### 3. Registrar o Diagnóstico
```
POST /service-orders/{{serviceOrderId}}/diagnosis

Body:
{
  "items": [
    {
      "catalogServiceId": "{{qualquer-uuid}}",
      "name": "Troca de Óleo",
      "price": {"value": 150.00, "currency": "BRL"}
    }
  ]
}
```
Resposta: `200 OK` com `executions[0].status = PENDING`. **Copie `executions[0].id`** →
`{{executionId}}`.

### 4. Atribuir Técnico à Execução (Épico 3) ⭐
```
POST /service-orders/{{serviceOrderId}}/executions/{{executionId}}/assign-technician

Body:
{
  "technicianId": "{{technicianId}}"
}
```
Resposta: `200 OK`, `executions[0].assignedTechnicianId` preenchido.

### 5. Iniciar Execução (Épico 3) ⭐
```
POST /service-orders/{{serviceOrderId}}/executions/{{executionId}}/start
```
Sem body. Resposta: `200 OK`, execução em `IN_PROGRESS`.

> A execução precisa estar `READY` (autorizada) antes do `start`. Como este guia não passa pelo fluxo
> de Estimate/aprovação, use `assign-technician` seguido de `start` apenas se o ambiente já tratar a
> execução como pronta; caso o endpoint retorne `409`, veja "Troubleshooting" abaixo.

### 6. Atualizar Progresso (Épico 3) ⭐
```
PATCH /service-orders/{{serviceOrderId}}/executions/{{executionId}}/progress

Body:
{
  "note": "Óleo trocado, verificando filtro"
}
```
Resposta: `200 OK`.

### 7. Completar Execução (Épico 3) ⭐
```
POST /service-orders/{{serviceOrderId}}/executions/{{executionId}}/complete
```
Sem body. Resposta: `200 OK`, execução em `COMPLETED`.

## Troubleshooting

**`409 Conflict` ao chamar `start`?**
- A execução precisa estar `READY`, não `PENDING`. `assign-technician` sozinho não autoriza a
  execução — a autorização vem do fluxo de Estimate (`authorizeExecutionFromEstimate`), que hoje só
  tem endpoint HTTP dentro do fluxo de geração/decisão de Estimate, não neste guia. Se você só quer
  testar `assign-technician`, `start`/`progress`/`complete` isoladamente sem passar pela Estimate,
  peça a alguém do time para confirmar o estado esperado da execução antes desses passos — não é um
  bug deste guia, é o fluxo real ainda incompleto (ver `EPIC2-REVIEW.md`/`docs/Architecture-Decisions.md`
  AD-008).

**`404 Not Found`?**
- Confirme que `{{serviceOrderId}}`, `{{executionId}}` e `{{technicianId}}` são os IDs retornados nas
  respostas anteriores (UUIDs), não valores de exemplo copiados deste guia.

**`400 Bad Request` ao criar técnico ou service order?**
- Confira os valores aceitos de `specialties` em
  `src/main/java/.../servicelifecycle/technician/domain/model/Specialty.java`
  (`MECHANICAL`, `ELECTRICAL`, `BODYWORK`, `PAINTING`, `DIAGNOSTICS`).

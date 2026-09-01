# Especificação Técnica: Atribuir responsável planejado pelo diagnóstico

| Campo | Valor |
|---|---|
| Feature | `assign-diagnosis-assignee` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

A mudança permanece dentro do módulo `servicelifecycle`. `ServiceOrder` guarda o planejamento por ID e consulta o
aggregate `Technician` somente pela aplicação para validar existência. Não há acesso a pacotes internos de outro módulo.

O aggregate passa a conter `UUID diagnosisAssigneeId`, anulável até a primeira atribuição. São adicionados:

- `assignDiagnosisAssignee(UUID technicianId)`, que exige ID não nulo e ausência de Diagnosis aberto;
- `diagnosisAssigneeId()`, somente leitura;
- o campo correspondente em `reconstitute(...)`.

`performDiagnosis(...)` passa a exigir que a atribuição esteja preenchida antes de criar qualquer execução. O valor
permanece na SO após o Diagnosis e pode ser substituído para um ciclo posterior quando `openDiagnosisId` voltar a nulo.
Nenhum método copia o valor para `assignedTechnicianId` ou para a autoria efetiva.

## Interfaces e fluxo de dados

### Novo comando HTTP

```http
PUT /api/service-orders/{id}/diagnosis-assignee
Content-Type: application/json

{
  "technicianId": "33333333-3333-3333-3333-333333333333"
}
```

`PUT` expressa substituição idempotente do planejamento atual. O contrato usa
`AssignDiagnosisAssigneeRequest(@NotNull UUID technicianId)` e retorna o `ServiceOrderResponse` completo.

| Resultado | HTTP | Código estável |
|---|---:|---|
| Atribuição ou reatribuição concluída | 200 | — |
| ID ausente ou inválido | 400 | `VALIDATION_ERROR` |
| Service Order ou Technician inexistente | 404 | `NOT_FOUND` |
| Existe Diagnosis aberto | 409 | `INVALID_STATE_TRANSITION` |

`ServiceOrderController` recebe `AssignDiagnosisAssigneeUseCase` por construtor e documenta todos os resultados com
OpenAPI. A coleção Postman adiciona o comando antes de `Perform diagnosis`.

### Aplicação e concorrência

`AssignDiagnosisAssigneeUseCase`, transacional, executa nesta ordem:

1. valida a existência do Technician por `TechnicianRepository.findById`;
2. carrega a Service Order com lock de escrita;
3. chama `assignDiagnosisAssignee`;
4. salva o aggregate e mapeia a resposta.

`ServiceOrderRepository` ganha `findByIdForUpdate(UUID)`, implementado no adapter JPA com lock pessimista. O mesmo lock
deve ser usado por `PerformDiagnosisUseCase`, impedindo que atribuição e início do Diagnosis sejam confirmados em
paralelo sobre estados incompatíveis.

`ServiceOrderResponse` inclui `UUID diagnosisAssigneeId`, anulável antes da atribuição. `ServiceOrderMapper` e o mapper
de persistência propagam o campo. Nenhum evento ou notificação é publicado.

## Persistência e dados de bootstrap

Uma migration Flyway versionada adicionará:

```text
service_orders.diagnosis_assignee_id BINARY(16) NULL
```

A coluna é anulável porque a atribuição ocorre depois da criação e porque existem registros legados. Não haverá FK para
`technicians`: o vínculo entre aggregates continua sendo por ID e a existência é validada pelo caso de uso. Não haverá
índice enquanto não existir consulta por responsável.

Classificação de dados: **no seed required**. Não há backfill, referência obrigatória ou dado de demonstração. Após o
deploy, uma Service Order legada precisa receber atribuição antes de aceitar novo Diagnosis.

## Segurança e operação

- `technicianId` é validado por existência, mas disponibilidade e especialidade permanecem fora de escopo.
- Não há mass assignment; o request contém somente o ID permitido.
- O endpoint ainda não tem autenticação/autorização. A limitação deve ser registrada na revisão de segurança sem
  atribuir ao ID informado valor de identidade autenticada.
- IDs de Technician e Service Order são dados operacionais; não devem ser incluídos em logs de erro com outros dados
  pessoais.
- O lock abrange somente a SO alvo e reduz a janela de corrida sem introduzir lock entre módulos.
- Rollback da aplicação é compatível com a coluna adicional. Não remover a coluna durante rollback evita perda do
  planejamento já gravado.

## Estratégia de testes

- Domínio: primeira atribuição, reatribuição, rejeição com Diagnosis aberto, ID nulo, ausência de efeito em status e
  `assignedTechnicianId`, e guarda de Diagnosis sem responsável.
- Aplicação: sucesso, Technician inexistente, SO inexistente, conflito e preservação do estado em falha.
- Concorrência/persistência: confirmar o lock JPA e o round-trip de `diagnosisAssigneeId`.
- HTTP MockMvc: `200`, `400`, `404`, `409`, response atualizado e bloqueio de Diagnosis sem atribuição.
- Regressão: Diagnosis com atribuição válida continua criando o lote; atribuição de execução não é alterada.
- Contrato: OpenAPI verifica o novo `PUT`, schemas e respostas; Postman inclui o fluxo na ordem correta.
- Estrutura: executar `make test`, `make verify`, revisar cobertura e manter `ModuleStructureTest` verde.

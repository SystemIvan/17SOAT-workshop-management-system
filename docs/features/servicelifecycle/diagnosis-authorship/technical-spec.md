# Especificação Técnica: Registrar autoria efetiva do diagnóstico

| Campo | Valor |
|---|---|
| Feature | `diagnosis-authorship` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

A mudança pertence a `servicelifecycle` e amplia o lote já criado por `perform-diagnosis`. Não cria aggregate Diagnosis:
`diagnosisId` continua agrupando as Service Executions, e cada execução preserva a auditoria necessária.

`ServiceExecution` passa a conter os campos imutáveis:

| Campo | Tipo de domínio | Regra |
|---|---|---|
| `diagnosedByTechnicianId` | `UUID` | obrigatório para novas execuções e nunca inferido da atribuição planejada |
| `diagnosedAt` | `Instant` | UTC, precisão de microssegundos e igual para todo o lote |

`ServiceOrder.performDiagnosis(...)` recebe autor e instante junto dos itens. O método valida ambos antes de criar o
primeiro item e repassa os mesmos valores a cada `ServiceExecution.start(...)`. Reconstituição aceita nulos apenas para
execuções legadas; não existe método de alteração posterior.

## Interfaces e fluxo de dados

### HTTP

`POST /api/service-orders/{id}/diagnosis` mantém a rota e passa a exigir:

```json
{
  "diagnosedByTechnicianId": "33333333-3333-3333-3333-333333333333",
  "items": [
    {
      "catalogServiceId": "44444444-4444-4444-4444-444444444444",
      "name": "Troca de óleo",
      "price": { "value": 150.00, "currency": "BRL" }
    }
  ]
}
```

- `PerformDiagnosisRequest` recebe `@NotNull UUID diagnosedByTechnicianId`.
- `diagnosedAt` não é aceito no request.
- `ServiceExecutionResponse` adiciona `diagnosedByTechnicianId` e `diagnosedAt`.
- O instante é serializado em ISO-8601 UTC.
- A alteração do request é incompatível e foi autorizada na especificação funcional; o response é aditivo.

| Condição | HTTP | Código estável |
|---|---:|---|
| Diagnosis registrado | 200 | — |
| Autor ausente ou request inválido | 400 | `VALIDATION_ERROR` |
| SO ou Technician inexistente | 404 | `NOT_FOUND` |
| Sem responsável planejado ou já existe Diagnosis aberto | 409 | `INVALID_STATE_TRANSITION` |

### Aplicação e tempo

`PerformDiagnosisUseCase` passa a depender de `TechnicianRepository` e de um `Clock` controlável em teste. O construtor
de produção usa `Clock.systemUTC()` e uma sobrecarga package-private recebe o relógio fixo dos testes, seguindo o padrão
já usado em Stock Reservation.

O fluxo transacional é:

1. validar a existência de `diagnosedByTechnicianId`;
2. carregar a SO com `findByIdForUpdate`;
3. obter uma única vez `clock.instant().truncatedTo(MICROS)`;
4. criar todo o lote no aggregate;
5. salvar uma vez e mapear a resposta.

O autor pode divergir de `diagnosisAssigneeId`; nenhuma comparação bloqueante é adicionada. Uma falha antes do save
preserva a atomicidade do lote.

## Persistência e dados de bootstrap

Uma migration Flyway versionada adicionará a `service_executions`:

```text
diagnosed_by_technician_id BINARY(16) NULL
diagnosed_at                TIMESTAMP(6) NULL
```

As colunas ficam anuláveis somente porque não existe fonte confiável para reconstruir autoria e instante das execuções
anteriores. Não haverá backfill a partir de `diagnosisAssigneeId`, pois planejamento não comprova autoria. Novas
execuções sempre persistem ambos os campos.

Não haverá FK para `technicians`, consistente com as demais referências entre aggregates por UUID. Não há caso de
consulta por autor aprovado, portanto nenhum índice adicional é criado.

Classificação de dados: **no seed required**. A migration altera schema sem criar dados de negócio; testes usam relógio
fixo e fixtures próprias.

## Segurança e operação

- O ID recebido no request é uma declaração, não prova de identidade. A API não deve apresentar o dado como autoria
  autenticada enquanto autenticação e autorização permanecerem fora do escopo.
- A existência do Technician reduz referências inválidas, mas não impede falsificação por um chamador autorizado a
  acessar o endpoint. Essa limitação aprovada deve constar da revisão de segurança e da documentação da API.
- Autor e instante são dados operacionais e não devem ser incluídos em logs junto com a triagem ou dados do Customer.
- O chamador não controla `diagnosedAt`, evitando timestamps retroativos ou futuros no contrato.
- Não há mass assignment; somente o autor declarado e os itens previstos são mapeados.
- Rollback da aplicação ignora com segurança as colunas adicionais; elas não devem ser removidas para evitar perda de
  auditoria já capturada.

## Estratégia de testes

- Domínio: valores iguais no lote, autor/instante obrigatórios para novas execuções, divergência permitida em relação
  ao planejamento, imutabilidade e reconstituição legada.
- Aplicação: relógio fixo, Technician inexistente, SO inexistente, estado inválido e nenhuma persistência parcial.
- HTTP MockMvc: campo obrigatório, autor desconhecido, response com UUID e timestamp ISO-8601 e ausência de input para
  `diagnosedAt`.
- Persistência: round-trip com precisão de microssegundos e leitura de colunas nulas em execução legada.
- Contrato: OpenAPI marca o autor como obrigatório no request e os dois campos como anuláveis no response por legado;
  Postman envia `diagnosedByTechnicianId`.
- Regressão: `diagnosisId`, Stock Requirements, Estimate e atribuição de execução mantêm o comportamento atual.
- Qualidade: executar `make test`, `make verify`, revisar cobertura e manter `ModuleStructureTest` verde.

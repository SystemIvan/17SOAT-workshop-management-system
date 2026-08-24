# Especificação Técnica: Registrar triagem inicial da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-initial-assessment` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

A mudança pertence somente ao módulo `servicelifecycle`, dentro do aggregate `ServiceOrder`. Ela amplia o fluxo já
existente de `service-order-creation` e não cria dependência com `registration`, `stockprocurement` ou uma nova
capability.

O domínio continua livre de Spring e JPA:

- `ServiceOrder` passa a armazenar `String initialAssessment`;
- `ServiceOrder.create(...)` recebe a triagem e rejeita valor nulo ou em branco;
- `ServiceOrder.reconstitute(...)` aceita `null` exclusivamente para registros anteriores à migration;
- `initialAssessment()` expõe o valor sem permitir alteração;
- nenhum método de atualização é criado.

O domínio valida com `String.isBlank()`. O texto é armazenado como informado; não é interpretado, convertido em
Diagnosis nem usado para produzir Service Executions.

## Interfaces e fluxo de dados

### HTTP

`POST /api/service-orders` mantém o endpoint e passa a exigir o campo:

```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "vehicleId": "22222222-2222-2222-2222-222222222222",
  "vehicleSnapshot": {
    "licensePlate": "ABC1D23",
    "brand": "Fiat",
    "model": "Argo",
    "year": 2024
  },
  "priority": "NORMAL",
  "initialAssessment": "Ruído na dianteira relatado pelo cliente."
}
```

- `CreateServiceOrderRequest` recebe `@NotBlank String initialAssessment`.
- `ServiceOrderResponse` recebe `String initialAssessment`.
- `POST` continua retornando `201`; ausência ou valor em branco retorna `400 VALIDATION_ERROR`.
- `GET /api/service-orders/{id}` e todos os comandos que devolvem `ServiceOrderResponse` passam a incluir o campo.
- Para registros legados, a resposta permite `initialAssessment: null`; novas Service Orders nunca retornam nulo.

A mudança de request é incompatível e foi autorizada na especificação funcional. A mudança de response é aditiva.
OpenAPI deve documentar o campo como obrigatório no request e anulável no response devido ao legado.

### Aplicação

`CreateServiceOrderUseCase` repassa `request.initialAssessment()` ao factory do aggregate dentro da transação já
existente. `ServiceOrderMapper` inclui o valor em todos os responses. Não há novo caso de uso ou evento.

### Falhas

| Condição | HTTP | Código estável |
|---|---:|---|
| Campo ausente, nulo ou em branco | 400 | `VALIDATION_ERROR` |
| Valor inválido que alcance a validação de domínio | 400 | `VALIDATION_ERROR` |

O handler global não deve usar `INVALID_STOCK_ITEM` para uma falha de argumento de Service Order. O advice limitado a
`servicelifecycle` deve traduzir essa falha para `VALIDATION_ERROR` sem expor exceção interna.

## Persistência e dados de bootstrap

Uma migration Flyway versionada, com timestamp UTC de implementação, adicionará:

```text
service_orders.initial_assessment TEXT NULL
```

O campo permanece anulável no banco para representar com honestidade Service Orders legadas, cuja triagem nunca foi
capturada. Não haverá backfill sintético. A obrigatoriedade vale para novas criações e é garantida no boundary e no
factory do domínio. A projeção JPA usa `@Column(name = "initial_assessment")`.

`ServiceOrderPersistenceMapper` deve mapear o campo nos dois sentidos. O rollout aplica a migration antes de iniciar a
versão da aplicação; `ddl-auto=validate` permanece inalterado.

Classificação de dados: **no seed required**. A migration altera apenas schema e não cria referência, exemplo de
negócio, seed de desenvolvimento ou fixture.

## Segurança e operação

- A triagem pode conter dados pessoais ou operacionais; não deve aparecer em logs, mensagens de erro ou notificações.
- O backend trata o conteúdo como texto opaco em JSON. Interfaces consumidoras são responsáveis por escapar o valor
  antes de renderizá-lo como HTML.
- Não há mass assignment: somente o campo explícito do record é encaminhado ao aggregate.
- A feature não adiciona autenticação ou autorização; conserva a lacuna já existente no endpoint de criação.
- O payload continua sujeito aos limites de requisição da plataforma. Um limite funcional específico de caracteres
  não foi aprovado e não será criado apenas na camada técnica.
- Rollback de aplicação é compatível com a coluna adicional. A coluna não deve ser removida no rollback para evitar
  perda dos dados já registrados.

## Estratégia de testes

- Domínio: criação válida, rejeição de nulo/branco, imutabilidade e reconstituição de registro legado com nulo.
- Aplicação: `CreateServiceOrderUseCaseTest` verifica encaminhamento e retorno de `initialAssessment`.
- HTTP: `ServiceOrderControllerCreateTest` cobre `201`, campo ausente, nulo, vazio e em branco; os builders existentes
  devem incluir uma triagem válida.
- Consulta: `GetServiceOrderUseCaseTest` e teste MockMvc comprovam retorno do valor e compatibilidade com legado nulo.
- Persistência: `ServiceOrderRepositoryImplTest` comprova round-trip; startup com Flyway e
  `ddl-auto=validate` cobre a coluna.
- Contrato: `OpenApiContractTest` verifica request obrigatório e response anulável; a coleção Postman recebe o campo.
- Qualidade: executar `make test`, `make verify`, revisar cobertura e manter `ModuleStructureTest` verde.

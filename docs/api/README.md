# API

## Autenticação

Todos os endpoints administrativos exigem JWT (consulte `docs/adr/ADR-003-authentication-strategy.md`). As exceções
são os dois endpoints do gateway externo de aprovação do Customer, autenticados por assinatura HMAC
(`docs/adr/ADR-007-external-gateway-hmac-authentication.md`): `POST /api/estimates/{estimateId}/decisions`, que
também aceita JWT (RF41, ver
[Decisão de orçamento pelo gateway externo](manual-testing.md#decisão-de-orçamento-pelo-gateway-externo-hmac)), e
`POST /api/service-orders/{serviceOrderId}/external-status-updates`, que aceita só HMAC (RF40, ver
[Atualização de status da OS via canal externo](manual-testing.md#atualização-de-status-da-os-via-canal-externo-hmac)).
Defina `APP_SECURITY_JWT_SECRET` e `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` no `.env` para qualquer ambiente real;
os valores padrão de `.env.example` são exclusivos para desenvolvimento local. A conta obrigatória `admin`/`ADMIN`,
criada por migration Flyway, permite obter um token e criar outras contas, conforme o
[roteiro Postman](manual-testing.md).

## Recursos da API

| Recurso | Prefixo | Capacidades principais |
| --- | --- | --- |
| Autenticação | `/api/auth` | Login JWT e criação administrativa de contas. |
| Clientes | `/api/customers` | Cadastro, identificação, consulta, atualização e arquivamento. |
| Veículos | `/api/vehicles` | Cadastro, consulta, atualização, quilometragem e arquivamento. |
| Catálogo | `/api/catalog-services` | Cadastro, consulta, atualização de preço e arquivamento. |
| Técnicos | `/api/technicians` | Cadastro, consulta, especialidades e status. |
| Ordens de Serviço | `/api/service-orders` | Criação, abertura completa (`/intake`), diagnóstico, orçamento, execução, tracking e entrega. |
| Estoque | `/api/stock-items` | Estoque, saldo e política de baixo estoque. |
| Reservas | `/api/stock-reservations` | Consulta e consumo de reservas de materiais. |
| Compras | `/api/purchase-demands`, `/api/purchase-orders` | Demandas, compras e recebimentos. |

O contrato OpenAPI é a fonte de verdade para todos os endpoints. A coleção Postman versionada em
`docs/api/postman/workshop-management-system.postman_collection.json` contém exemplos e asserções dos fluxos.

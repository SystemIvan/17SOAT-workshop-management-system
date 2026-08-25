# Especificação Funcional: Autenticação e autorização JWT

| Campo | Valor |
|---|---|
| Feature | `jwt-authentication` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-24 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-24 |
| Referências | Tech Challenge Fase 1 (requisito obrigatório "Implementação de autenticação JWT para APIs administrativas"); `docs/Architecture-Decisions.md` (AD-016, `Resolved` em 2026-08-24); `docs/adr/ADR-003-authentication-strategy.md` (`Status: Proposed` no momento em que esta especificação foi escrita; promovida a `Accepted` no checkpoint 6 de `implementation-plan.md`); `GAPS-TECH-CHALLENGE.md` §4 |

## Problema e resultado esperado

O desafio exige explicitamente autenticação JWT para as APIs administrativas do sistema, e hoje **nenhum
endpoint do projeto valida quem está chamando** — qualquer cliente HTTP pode criar, alterar ou consultar
qualquer recurso de qualquer bounded context (`registration`, `servicelifecycle`, `stockprocurement`).

**AD-016** já resolveu a questão de *ownership*: um módulo interno de Identity/Auth é o dono de
credenciais e do mapeamento papel→ID de domínio; `Customer` e `Technician` continuam apenas referências
por UUID, sem carregar senha ou papel dentro do próprio aggregate. Esta especificação define o
*comportamento* desse módulo e como ele passa a proteger os endpoints existentes.

Resultado esperado: dado um usuário com credenciais válidas em um dos quatro papéis (`CUSTOMER`,
`TECHNICIAN`, `MANAGER`, `ADMIN`), o sistema emite um JWT contendo identidade e papel; endpoints
administrativos passam a exigir esse token e a rejeitar chamadas sem um papel autorizado.

## Atores e cenários

- **Customer** — autentica para consultar suas próprias Service Orders e decidir linhas de Estimate.
- **Technician** — autentica para operações de execução (diagnóstico, progresso, conclusão) atribuídas a
  ele.
- **Manager** — autentica para operações administrativas amplas (CRUD de cadastro, gestão de estoque,
  emissão de Purchase Order).
- **Admin** — autentica para operações de administração da própria plataforma (gestão de usuários/papéis).

Cenários ponta a ponta cobertos por esta feature:

1. Um usuário com credenciais válidas faz login e recebe um JWT válido por tempo limitado, contendo seu
   ID de domínio e papel.
2. Uma chamada a um endpoint administrativo sem token é rejeitada.
3. Uma chamada com token válido mas papel sem permissão para aquele endpoint é rejeitada.
4. Uma chamada com token válido e papel autorizado é processada normalmente, sem mudança de
   comportamento de negócio.
5. Um token expirado é rejeitado mesmo que tivesse sido válido anteriormente.

## Regras de negócio

- Existem exatamente quatro papéis nesta fase: `CUSTOMER`, `TECHNICIAN`, `MANAGER`, `ADMIN` (conforme
  `ADR-003`). Não há papéis compostos ou hierarquia de herança entre eles nesta fase.
- Uma credencial pertence a exatamente um papel e, quando aplicável (`CUSTOMER`/`TECHNICIAN`), referencia
  o ID do aggregate correspondente em `registration`/`servicelifecycle`. `MANAGER`/`ADMIN` não referenciam
  nenhum aggregate de domínio existente — são identidades da própria oficina.
- Senhas nunca são persistidas em texto plano; usam hashing (bcrypt, conforme `ADR-003`).
- O JWT expira em 1 hora a partir da emissão (conforme `ADR-003`, "tokens expiram após 1 hora"). Esta
  feature não implementa refresh token — é explicitamente opcional na ADR e fica fora de escopo aqui.
- Endpoints classificados como "administrativos" (ver `technical-spec.md` para a lista completa por
  módulo) exigem um JWT válido com papel autorizado; chamadas sem token retornam `401`, chamadas com
  papel não autorizado retornam `403`.
- Nenhum comportamento de negócio existente (regras de `ServiceOrder`, `Estimate`, `StockReservation`
  etc.) muda; esta feature é puramente uma camada de autenticação/autorização em frente aos casos de uso
  já implementados.
- `docs/adr/ADR-003-authentication-strategy.md` é promovida de `Status: Proposed` para `Status: Accepted`
  como parte do escopo desta feature, com o Approval Checklist da ADR marcado conforme cada item for
  atendido pela implementação (tokens expiram em 1h, senhas com bcrypt, testes de segurança planejados
  etc.).

## Fora de escopo

- **Autorização por posse de recurso** (ex.: "Customer só acessa as próprias OS", levantado como parte da
  RF23B em `GAPS-EPIC2-EPIC3.md`) — esta feature entrega autenticação e autorização por papel; checagem de
  propriedade linha a linha é uma feature separada, a ser aberta depois que este alicerce existir.
  Registrar esse gap remanescente no plano de implementação, não resolvê-lo aqui.
- Refresh tokens.
- SSO ou login via provedores externos (Google, GitHub etc.) — explicitamente rejeitado por `ADR-003`.
- Cadastro de credenciais para `Customer`/`Technician` novos como parte do fluxo de CRUD existente
  (`CreateCustomerUseCase` etc.) — como as credenciais são emitidas/associadas a um `Customer`/`Technician`
  existente é decisão de implementação a detalhar no `technical-spec.md`, mas o fluxo de auto-cadastro via
  API pública não é obrigatório pelo desafio e fica fora desta primeira versão.
- Rate limiting e logs de tentativas de login falhadas — citados como mitigação em `ADR-003`, mas não são
  requisito obrigatório do desafio; podem ficar como trabalho futuro.

## Critérios de aceite

- [ ] Um usuário com credenciais válidas consegue autenticar e recebe um JWT contendo seu ID de domínio
      (quando aplicável) e papel.
- [ ] Um JWT expira 1 hora após a emissão e chamadas com token expirado são rejeitadas com `401`.
- [ ] Uma chamada a um endpoint administrativo sem token retorna `401`.
- [ ] Uma chamada a um endpoint administrativo com token válido mas papel não autorizado retorna `403`.
- [ ] Uma chamada a um endpoint administrativo com token válido e papel autorizado é processada
      normalmente, sem regressão de comportamento de negócio.
- [ ] Senhas são armazenadas com hash (bcrypt), nunca em texto plano — verificável por teste de
      persistência/unit test do módulo de credenciais.
- [ ] `Customer` e `Technician` não ganham nenhum campo de senha/papel em seus próprios aggregates —
      verificável por revisão do modelo de domínio desses módulos, permanecendo inalterados por esta
      feature.
- [ ] Endpoints hoje sem qualquer proteção (ver levantamento em `GAPS-TECH-CHALLENGE.md` §4) passam a
      exigir autenticação conforme a classificação administrativa definida no `technical-spec.md`.
- [ ] `docs/adr/ADR-003-authentication-strategy.md` está `Status: Accepted`, com todos os itens do Approval
      Checklist marcados e cada um com evidência (teste, config ou trecho de código) do que os satisfaz.

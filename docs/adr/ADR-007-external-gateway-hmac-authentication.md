# ADR 007: Autenticação de sistemas externos por assinatura HMAC de webhook

**Status:** Accepted

**Date:** 2026-09-23

**Deciders:** Santiago Silvestre

**Affected By:** `identity` (cadeia de segurança HTTP), `servicelifecycle` (`POST /api/estimates/{estimateId}/decisions`),
canais externos futuros (RF37, RF40)

---

## Context

A `ADR-003` adotou Spring Security + JWT como estratégia de autenticação das APIs administrativas. Todo chamador é
um usuário interno com conta em `identity`, e o JWT carrega um papel mapeado para um domain-ID de `Customer` ou
`Technician` (AD-016).

O enunciado da Fase 2 exige um "endpoint para receber notificações externas de aprovação ou recusa" do orçamento
(RF41). O endpoint de negócio já existe, `POST /api/estimates/{estimateId}/decisions` (feature
`decide-estimate-lines`), mas só aceita JWT de usuário interno. O chamador agora é um sistema, o gateway de
aprovação do cliente, e não um ator de domínio: não é Customer nem Technician e não tem usuário ou senha.

É preciso decidir como autenticar esse tipo de chamador sistema-a-sistema. A decisão afeta mais do que o RF41:
RF40 (`external-status-update`) e possivelmente RF37 são canais externos do mesmo tipo, e usar mecanismos
diferentes para canais equivalentes aumentaria a superfície de segurança e a documentação sem ganho.

## Problem Statement

A decisão precisa:

- permitir que um sistema externo chame o endpoint sem conta de usuário interno;
- manter o fluxo JWT interno existente sem nenhuma mudança de comportamento;
- não alterar `DecideEstimateLinesUseCase` nem as regras de negócio da decisão de orçamento;
- não distorcer o modelo role→domain-ID ratificado em AD-016;
- manter o segredo/credencial configurado por ambiente, nunca versionado para ambientes reais;
- rejeitar chamadas sem credencial válida sem revelar a causa nem a existência dos recursos;
- servir de padrão para os próximos canais externos de entrada.

## Considered Options

### Option 1: JWT de serviço dedicado (client credentials)

Emitir um JWT para o sistema externo por um fluxo de credenciais de cliente, reaproveitando `JwtAuthenticationFilter`,
`ApiAuthenticationEntryPoint` e `ApiAccessDeniedHandler`.

Vantagens:

- um único formato de token para todos os chamadores;
- reaproveita a infraestrutura JWT já implementada e testada.

Desvantagens:

- exige um novo tipo de principal sem domain-ID, ou com um domain-ID sintético, estendendo o modelo role→domain-ID
  de AD-016 com um conceito que não pertence a ele;
- exige um fluxo de emissão de token para clientes e a gestão da credencial desse cliente;
- trata um sistema integrador como "usuário" do sistema.

### Option 2: Assinatura HMAC de webhook com segredo compartilhado ✅ SELECIONADO

O sistema externo assina a requisição com um segredo compartilhado, usando HMAC-SHA256 sobre um timestamp e o corpo
bruto. Um filtro de segurança dedicado valida a assinatura antes do controller e concede uma authority sintética,
restrita às rotas que aceitam o canal.

Vantagens:

- é o padrão mais comum para receber notificações de sistemas externos (webhooks);
- não usa nem estende o modelo de identidade: `identity.auth`, o enum `Role` e AD-016 ficam intocados;
- a assinatura cobre o corpo, garantindo integridade além da autenticidade;
- a mudança fica isolada em um filtro e em uma regra de autorização, sem tocar o módulo de negócio.

Desvantagens:

- não reaproveita a infraestrutura JWT, então passa a haver dois mecanismos de autenticação na aplicação;
- exige a gestão de um segredo compartilhado (distribuição e rotação) com cada integrador;
- sem controle de nonce, uma requisição capturada pode ser reenviada dentro da janela de tolerância do timestamp.

## Decision

Canais externos de entrada (sistema-a-sistema) serão autenticados por **assinatura HMAC de webhook com segredo
compartilhado**, e não por JWT. O JWT da `ADR-003` continua sendo o mecanismo dos usuários internos.

Fatores decisivos: um gateway externo não é um ator de domínio e não deve ganhar conta nem JWT, o que forçaria
AD-016 a acomodar um conceito fora do seu desenho; e o HMAC atende literalmente ao requisito de "receber notificação
de um sistema externo", isolando a mudança na borda de segurança.

Princípios que valem para qualquer canal que adotar este mecanismo:

- a assinatura cobre um timestamp e o corpo bruto, e é comparada em tempo constante;
- o timestamp é validado contra uma janela de tolerância, como mitigação de replay;
- a verificação acontece antes de qualquer acesso a dados de domínio;
- a leitura do corpo para verificação tem limite de tamanho e só ocorre quando a chamada traz a assinatura;
- uma verificação bem-sucedida concede uma `GrantedAuthority` sintética, específica do canal, que existe só no
  `SecurityContext` da requisição e não é um valor do enum `Role`;
- essa authority só é aceita nas rotas do próprio canal;
- toda falha do caminho HMAC responde o mesmo `401` genérico já usado para JWT ausente ou inválido;
- o segredo é configurado por ambiente, com um valor padrão aceitável só para desenvolvimento local, no mesmo modelo
  de `APP_SECURITY_JWT_SECRET`;
- em rotas que aceitam os dois caminhos, o fluxo JWT interno continua funcionando sem mudança.

Headers, formato exato da string assinada, valores padrão de janela e de tamanho, precedência entre JWT e HMAC e a
estratégia de testes pertencem à `technical-spec.md` de cada feature que consome esta decisão. A primeira é
`docs/features/servicelifecycle/estimate-decisions-external-auth/technical-spec.md`.

Um novo canal externo que precise de outro mecanismo deve registrar essa exceção em uma nova ADR.

## Consequências

### Positivas ✅

- Sistemas externos integram sem contas de usuário artificiais.
- O modelo de identidade (AD-016) e o fluxo JWT interno permanecem inalterados.
- A assinatura sobre o corpo garante que o payload não foi alterado em trânsito.
- RF40 e RF37 têm um mecanismo definido e não precisam decidir de novo.
- A mudança fica na borda HTTP, sem acoplar `identity` a tipos de `servicelifecycle`.

### Negativas ❌

- A aplicação passa a ter dois mecanismos de autenticação, e a documentação precisa deixar claro onde cada um vale.
- A rotação do segredo exige coordenação com o integrador.
- Replay dentro da janela de tolerância continua possível no MVP.
- Se o segredo não for configurado, a aplicação sobe com o valor de desenvolvimento publicado no repositório.

### Mitigação de Riscos

- A janela de tolerância do timestamp é curta e configurável; a comparação de limites evita overflow.
- Se o requisito de segurança subir, o próximo passo é um nonce/`jti` de uso único, registrado como evolução desta
  ADR.
- `.env.example` e `README.md` orientam trocar o segredo em qualquer ambiente real, como já fazem para o JWT.
- O limite de tamanho do corpo impede que um chamador anônimo force a leitura de corpos grandes em memória.
- O segredo, a assinatura recebida e o corpo bruto nunca são logados.

## Related ADRs

- **ADR-003:** Authentication Strategy — esta ADR complementa a ADR-003 para chamadores sistema-a-sistema, sem
  substituí-la; o JWT continua sendo o mecanismo dos usuários internos.
- **ADR-005:** Inter-Module Integration Contract — não relacionada: trata de integração entre módulos internos, não
  de chamadores externos.

## References

- `docs/features/servicelifecycle/estimate-decisions-external-auth/functional-spec.md` — RF41 e registro original da
  escolha entre as opções.
- `docs/features/servicelifecycle/estimate-decisions-external-auth/technical-spec.md` — contrato de headers, fluxo de
  verificação e Adendo 1 (limite de tamanho do corpo).
- `docs/features/servicelifecycle/estimate-decisions-external-auth/implementation-plan.md` — revisão de segurança e
  evidências.
- `docs/Architecture-Decisions.md` — AD-016 (Identity/Auth module, mapeamento role→domain-ID).

## Approval Checklist

- [x] Opção 2 (HMAC de webhook) escolhida em vez do JWT de serviço — confirmado por Santiago Silvestre em
      2026-09-23, no `functional-spec.md` de `estimate-decisions-external-auth`.
- [x] Risco de replay dentro da janela de tolerância aceito para o MVP — `technical-spec.md`, aprovado em 2026-09-23.
- [x] Limite de tamanho do corpo incorporado aos princípios — Adendo 1 do `technical-spec.md`, aprovado por Santiago
      Silvestre em 2026-09-26.
- [x] Esta ADR aprovada como padrão para canais externos de entrada — Santiago Silvestre, 2026-09-26.
- [ ] Aplicabilidade a RF37 confirmada quando o requisito for detalhado.

---

**Last Updated:** 2026-09-26

**Decision Maker:** Santiago Silvestre

**Status:** Accepted — decisão tomada em 2026-09-23 na feature `estimate-decisions-external-auth` e formalizada como
ADR em 2026-09-26

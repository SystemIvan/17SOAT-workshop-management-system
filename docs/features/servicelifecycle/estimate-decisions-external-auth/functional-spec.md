# Especificação Funcional: Autenticação externa de `POST /api/estimates/{estimateId}/decisions`

| Campo | Valor |
|---|---|
| Feature | `estimate-decisions-external-auth` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-23 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-23 |
| Referências | RF41; RF37 (decisão de desenho candidata a ser compartilhada — conteúdo ainda não detalhado, ver "Relação com RF37"); RF40 (`docs/features/servicelifecycle/external-status-update/functional-spec.md`, Draft, bloqueada por esta decisão); `docs/features/servicelifecycle/decide-estimate-lines/functional-spec.md` (endpoint e caso de uso protegidos por esta feature, comportamento de negócio inalterado); `docs/Architecture-Decisions.md` AD-016 (Identity/Auth module, mapeamento role→domain-ID); README.md (afirma hoje que todos os endpoints administrativos exigem JWT — texto impactado por esta feature) |

## Nota sobre a origem do requisito

RF41 é um requisito novo da Fase 2 do Tech Challenge, ainda **não registrado no board do Miro** (mesma
situação de RF38/RF39/RF40). Diferente do que a sessão havia assumido antes desta descrição completa, RF41
**não cria um endpoint novo** — ele confirma/ajusta o mecanismo de autenticação do endpoint **já
existente** `POST /api/estimates/{estimateId}/decisions` (feature `decide-estimate-lines`, já
`Approved`), para que um sistema externo (gateway de aprovação do cliente) consiga chamá-lo sem ter uma
conta de usuário interno com JWT admin. Isso também é o requisito do enunciado da Fase 2: "endpoint para
receber notificações externas de aprovação ou recusa".

Como consequência, RF40 (`external-status-update`) fica bloqueada por esta decisão: o contrato HTTP de
RF40 (se vier a existir como algo distinto deste endpoint) depende de qual mecanismo é escolhido aqui. Esta
branch (`feat/servicelifecycle-estimate-decisions-external-auth`) trata **só** de RF41; o rascunho de RF40
permanece isolado em `feat/servicelifecycle-external-status-update`, sem avançar até esta spec ser aprovada
e implementada.

## Problema e resultado esperado

`README.md` afirma que todos os endpoints administrativos exigem JWT. `POST
/api/estimates/{estimateId}/decisions` hoje só é alcançável por um ator interno autenticado (Customer via
atendente, Manager, Service Advisor — conforme `decide-estimate-lines`). Não existe nada que diferencie um
fluxo de chamada por um sistema externo (gateway de aprovação do cliente) do fluxo interno — um sistema
externo precisaria de uma conta de usuário interno com JWT admin para chamar o endpoint hoje, o que não
faz sentido para uma integração de sistema-a-sistema.

Resultado esperado: o mesmo endpoint aceita, além do fluxo JWT interno já existente e inalterado, uma
segunda forma de autenticação própria para o sistema externo (gateway de aprovação do cliente), sem exigir
que esse sistema tenha uma conta de usuário interno. Chamadas sem credencial/assinatura válida por nenhum
dos dois caminhos são rejeitadas com `401`/`403` e a decisão de orçamento não é aplicada. O comportamento
de negócio (`DecideEstimateLinesUseCase`) permanece inalterado — esta feature é só sobre a borda de
autenticação.

## Decisão a confirmar antes da aprovação

A US exige decidir explicitamente entre as duas opções abaixo antes de qualquer implementação:

- **(a) JWT com token de serviço dedicado (client credentials).** Emitir um token JWT para o sistema
  externo por um fluxo de credenciais de cliente (sem senha de usuário), reaproveitando a infraestrutura
  JWT já existente (`JwtAuthenticationFilter`, `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler`).
  Exigiria estender o modelo de identidade: hoje o Identity/Auth module mapeia role → domain-ID de
  `Customer`/`Technician` (AD-016); um "sistema externo" não é nem Customer nem Technician, então essa
  opção introduz um novo tipo de principal sem domain-ID (ou um domain-ID sintético), o que é uma extensão
  não trivial do modelo já ratificado em AD-016.
- **(b) Assinatura de webhook (HMAC do payload + segredo compartilhado), sem JWT.** O sistema externo
  assina o corpo da requisição com um segredo compartilhado; um filtro dedicado valida a assinatura antes
  do controller. Não usa nem estende a infraestrutura JWT/Identity existente; trata o gateway externo como
  uma integração de webhook, não como um "usuário" do sistema.

**Decisão: opção (b), HMAC de webhook.** Confirmada por Santiago Silvestre em 2026-09-23. Justificativa: um
gateway de aprovação externo não é um ator de domínio (não é Customer nem Technician) e não deveria ganhar
uma conta/JWT — isso força o Identity/Auth module (cujo desenho em AD-016 é explicitamente role→domain-ID)
a acomodar um conceito que não se encaixa nele. HMAC de webhook é o padrão mais comum para "receber
notificação de um sistema externo" (é literalmente o texto do enunciado da Fase 2) e mantém o JWT/Identity
module sem mudança, isolando toda a mudança em um filtro de segurança dedicado a este endpoint. O custo é
não reaproveitar a infraestrutura JWT já pronta, e precisar de gestão de segredo compartilhado (rotação,
armazenamento) — que já seria necessária de qualquer forma na opção (a) para o segredo/credencial do
client credentials.

## Relação com RF37

A US pede para registrar, se a mesma decisão de desenho (token de serviço vs. assinatura de webhook)
também resolver RF37, para evitar dois mecanismos diferentes para o mesmo tipo de canal externo. **RF37
ainda não foi detalhada nesta sessão** — esta spec não assume nenhum conteúdo dela. Quando RF37 for
descrita, revisar esta seção e, se aplicável, apontar para esta mesma decisão em vez de duplicá-la.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Sistema externo (gateway de aprovação do cliente) | Chama `POST /api/estimates/{estimateId}/decisions` com a credencial/assinatura do mecanismo escolhido; a chamada é aceita e processada pelo `DecideEstimateLinesUseCase` normalmente. |
| Sistema externo sem credencial/assinatura válida | Chama o endpoint sem a credencial exigida, ou com uma assinatura/token inválido; a API responde `401`/`403` e nenhuma decisão é aplicada. |
| Customer (via atendente), Manager, Service Advisor | Continuam chamando o mesmo endpoint com JWT de usuário interno, exatamente como hoje — este fluxo não muda. |

### Cenário principal — chamada externa autenticada corretamente

1. O gateway de aprovação do cliente monta a requisição para `POST
   /api/estimates/{estimateId}/decisions` incluindo a credencial/assinatura exigida pelo mecanismo
   escolhido (decisão acima).
2. A verificação passa.
3. `DecideEstimateLinesUseCase` é executado exatamente como para um chamador interno — mesmas regras de
   negócio, mesmos efeitos (`decide-estimate-lines`), sem nenhuma diferença de comportamento por a chamada
   ter vindo de um sistema externo.

### Cenário alternativo — chamada externa sem credencial/assinatura válida

1. O gateway (ou qualquer chamador externo) chama o endpoint sem a credencial exigida, ou com um valor
   inválido/adulterado.
2. A API responde `401 Unauthorized` ou `403 Forbidden` (conforme o mecanismo escolhido — token ausente
   vs. token/assinatura inválida têm semânticas distintas) e a decisão de orçamento não é aplicada.

### Cenário de regressão — fluxo interno inalterado

1. Um Manager/Service Advisor autenticado por JWT de usuário chama o mesmo endpoint como já faz hoje.
2. O comportamento é idêntico ao já especificado em `decide-estimate-lines`; esta feature não introduz
   nenhuma mudança perceptível para esse fluxo.

## Regras de negócio

1. `POST /api/estimates/{estimateId}/decisions` passa a aceitar dois caminhos de autenticação
   (JWT de usuário interno já existente, e o novo mecanismo para o sistema externo), sem que um exclua o
   outro.
2. Falha de autenticação/verificação em qualquer um dos dois caminhos resulta em `401`/`403`, sem invocar
   `DecideEstimateLinesUseCase`.
3. `DecideEstimateLinesUseCase` e as regras de negócio já documentadas em `decide-estimate-lines`
   permanecem inalteradas; esta feature não introduz, remove nem modifica nenhuma regra de decisão de
   orçamento.
4. O segredo/credencial do mecanismo escolhido é configurado por ambiente, nunca versionado em texto
   plano no repositório, seguindo a mesma disciplina de segredos já aplicada a outras credenciais do
   projeto.
5. `README.md`, o OpenAPI gerado e a coleção Postman devem refletir o novo caminho de autenticação deste
   endpoint (a afirmação atual do README de que "todos os endpoints administrativos exigem JWT" deixa de
   ser inteiramente precisa e precisa ser corrigida, já que este endpoint passa a aceitar um segundo
   mecanismo além de JWT).

## Fora de escopo

- qualquer mudança em `DecideEstimateLinesUseCase` ou nas regras de negócio de aprovação/rejeição de
  Estimate já especificadas em `decide-estimate-lines`;
- estender o modelo role→domain-ID do Identity/Auth module (AD-016) — a opção (a), que exigiria isso, foi
  descartada nesta decisão;
- o formato exato do payload/canal de RF40 (`external-status-update`) — RF40 continua sua própria spec,
  isolada em outra branch, e só avança depois que esta decisão estiver aprovada e implementada;
- detalhar ou resolver RF37 — apenas sinalizar a relação quando ela for descrita;
- registrar RF41/RF37 no board do Miro (task separada, mesma situação de RF38/RF39/RF40).

## Critérios de aceite

- [ ] Dado uma chamada ao endpoint com a assinatura HMAC correta (payload + segredo compartilhado), quando
      o endpoint é chamado, então `DecideEstimateLinesUseCase` é executado e o comportamento de negócio é
      idêntico ao já especificado em `decide-estimate-lines`.
- [ ] Dado uma chamada sem assinatura HMAC, ou com uma assinatura inválida/adulterada, quando o endpoint é
      chamado, então a API responde `401`/`403` e nenhuma decisão de orçamento é aplicada.
- [ ] Dado uma chamada de um usuário interno autenticado por JWT (fluxo já existente), quando o endpoint é
      chamado, então o comportamento é idêntico ao especificado em `decide-estimate-lines`, sem nenhuma
      regressão.
- [ ] `README.md` deixa de afirmar, sem ressalva, que todos os endpoints administrativos exigem JWT —
      passa a documentar explicitamente o caminho de autenticação externa deste endpoint.

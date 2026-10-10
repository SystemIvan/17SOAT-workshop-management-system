# ADR 008: Decisão do cliente sobre o orçamento por link assinado, sem login

**Status:** Proposed  
**Date:** 2026-10-10  
**Deciders:** Time (ratificação pendente); proposta de Santiago Silvestre  
**Affected By:** `identity` (cadeia de segurança HTTP), `servicelifecycle` (`estimate`: e-mail de orçamento e páginas
de decisão, RF53)

> **Pausada em 2026-10-10.** A RF53, única consumidora desta ADR, foi pausada porque não é exigida pelo enunciado
> da Fase 2 (ver `docs/features/servicelifecycle/estimate-approval-link/functional-spec.md`). A ADR continua
> `Proposed` e não precisa ser levada ao time enquanto a RF53 não for retomada. O número 008 fica reservado; uma nova
> ADR deve usar 009.

---

## Context

O sistema tem hoje dois mecanismos de autenticação:

- **JWT** (`ADR-003`) para usuários internos. Cada papel é mapeado para um domain-ID (AD-016).
- **HMAC de webhook** (`ADR-007`) para sistemas externos que chamam a API.

A RF53 (`docs/features/servicelifecycle/estimate-approval-link/`) cria um terceiro tipo de chamador: o **próprio
cliente**, pelo navegador, a partir do e-mail de orçamento da RF52. O cliente não tem conta, então não há como
emitir um JWT para ele. Ele também não consegue produzir uma assinatura HMAC de sistema. A RF52 descartou a resposta
por e-mail ("responda APROVO"), porque nenhum componente lê a caixa de resposta.

O `ADR-007` determina: "Um novo canal externo que precise de outro mecanismo deve registrar essa exceção em uma nova
ADR". Esta ADR cumpre essa determinação.

O AD-016 (Resolved, escopo do time inteiro) define o Customer como ator da aprovação comercial, identificado por
papel mapeado. Este canal permite que o Customer decida **sem identidade autenticada**. Isso estende a política de
autorização e por isso precisa de ratificação do time.

## Problem Statement

A decisão precisa:

- permitir que o dono da OS aprove ou recuse o orçamento a partir do e-mail, sem login (regra 7 da RF53);
- impedir que o link seja forjado, alterado, trocado de decisão ou apontado para outro orçamento;
- impedir que a abertura automática do link (antivírus, pré-visualização) provoque decisão;
- não alterar JWT (`ADR-003`), HMAC (`ADR-007`) nem o modelo role→domain-ID (AD-016);
- não expor dados pessoais nem revelar a existência de OS ou orçamento a quem não tem um link válido.

## Considered Options

### Option 1: Exigir login do cliente (JWT com papel `CUSTOMER`)

Prós: reaproveita `ADR-003` e AD-016 sem exceção.

Contras: o cliente não tem conta hoje, e criar contas, senhas e recuperação de senha é um portal do cliente, que a
RF53 deixa explicitamente fora de escopo. Além disso, a fricção derruba o propósito de decidir a partir do e-mail.

### Option 2: Link com token aleatório persistido

O token é aleatório, e só o hash dele é gravado em tabela, junto com orçamento, decisão e uso.

Prós: permite revogar um link manualmente e marcar uso único.

Contras: exige tabela, migration e repositório novos. As duas vantagens não são necessárias: revogação manual está
fora de escopo, e o uso único já decorre do status do orçamento ("primeira decisão vence").

### Option 3: Link com token assinado por HMAC, sem estado ✅ SELECIONADO

O token carrega o ID do orçamento e a decisão, assinados com HMAC-SHA256 por uma chave dedicada. Validade e "já
decidido" são lidos do orçamento a cada acesso. Abrir o link só mostra uma página de confirmação. A decisão exige um
`POST` explícito.

Prós: nenhuma persistência nova; a validade acompanha a do orçamento sem duplicar estado; o token não pode ser
forjado nem alterado; nenhum dos mecanismos existentes muda.

Contras: não há revogação individual. Rotacionar a chave invalida todos os links pendentes. Quem tiver o link pode
decidir enquanto o orçamento estiver aberto.

## Decision

O cliente decide sobre o orçamento por **link com token assinado por HMAC, sem estado e sem login**. Esse é um
terceiro mecanismo de autenticação, restrito às rotas de decisão do orçamento por link. Ele não substitui JWT nem o
HMAC de gateway e não cria conta, papel ou domain-ID. O token é uma **capacidade** (bearer capability): prova que
quem o apresenta recebeu o e-mail de orçamento do dono da OS, e não quem essa pessoa é.

Princípios que valem para este canal:

- o conteúdo assinado inclui o orçamento e a decisão, e a comparação é feita em tempo constante;
- a chave é dedicada a este canal. Ela é distinta das chaves de JWT e de HMAC de gateway, vem do ambiente e **não
  tem valor padrão fora do perfil `dev`**, porque as rotas são públicas e um segredo publicado permitiria forjar
  decisões;
- o token não guarda validade: o link vale enquanto o orçamento estiver aberto e dentro da validade;
- abrir o link nunca decide. Só uma confirmação explícita (`POST`) aplica a decisão;
- a decisão passa pelas mesmas regras de domínio dos outros canais, sem regra de decisão nova;
- as páginas não expõem dados pessoais, e toda falha de token responde a mesma página genérica, sem revelar se a OS
  ou o orçamento existem;
- o token e a URL completa nunca são logados.

Formato do token, rotas, headers HTTP, configuração e testes pertencem à `technical-spec.md` da RF53.

## Consequências

### Positivas ✅

- O cliente decide a partir do e-mail, sem portal nem conta.
- JWT, HMAC de gateway e AD-016 continuam sem mudança.
- Não há persistência nova; a validade do link acompanha a do orçamento.

### Negativas ❌

- Passam a existir três mecanismos de autenticação, e a documentação precisa deixar claro onde cada um vale.
- Quem tiver acesso ao e-mail do cliente, ou a um encaminhamento dele, pode decidir. É a premissa de qualquer
  aprovação por e-mail.
- Não há revogação individual de link nem segundo fator.
- Um scanner que submeta formulários automaticamente poderia confirmar a decisão. Não há sinal confiável para
  distingui-lo de um clique humano.
- Rotacionar a chave invalida todos os links pendentes. Nesse caso, o cliente decide pela oficina.

### Mitigação de Riscos

- A confirmação é um `POST` separado da abertura, o que neutraliza pré-visualizadores e antivírus que só fazem `GET`.
- A assinatura tem 256 bits, então força bruta é inviável.
- A aplicação não sobe com chave ausente ou curta fora do perfil `dev`.
- As respostas usam `Referrer-Policy: no-referrer`, `Cache-Control: no-store` e uma CSP restritiva, sem recursos
  externos, para que o token não vaze por cabeçalho ou cache.
- Se o requisito de segurança subir, o próximo passo é migrar para a Option 2 (token persistido, revogável e de uso
  único), registrado como evolução desta ADR.

## Related ADRs

- **ADR-003:** Authentication Strategy. O JWT continua sendo o mecanismo dos usuários internos.
- **ADR-007:** External Gateway HMAC Authentication. Esta ADR é a exceção que o ADR-007 exige para um novo canal
  externo com outro mecanismo.
- **ADR-004:** Notifications Boundary. O link é entregue pelo e-mail de orçamento da RF52. O canal de notificação
  continua ligado ao AD-014, que ainda está `Team Decision Required`.

## References

- `docs/features/servicelifecycle/estimate-approval-link/functional-spec.md` (RF53, Approved em 2026-10-10).
- `docs/features/servicelifecycle/estimate-approval-link/technical-spec.md` (formato do token, rotas e testes).
- `docs/Architecture-Decisions.md`: AD-016 (identidade e autorização), AD-014 (canal de notificação).

## Approval Checklist

- [ ] Time ratifica um terceiro mecanismo de autenticação (link assinado, sem login) para a decisão do cliente.
- [ ] Time ratifica que o Customer decide por capacidade (posse do link), como extensão do AD-016, sem conta nem
      papel.
- [ ] Riscos aceitos para o MVP: sem revogação individual, sem segundo fator e scanner que submete formulários.

---

**Last Updated:** 2026-10-10  
**Decision Maker:** Time (pendente)  
**Status:** Proposed. Bloqueia a aprovação da `technical-spec.md` da RF53 até a ratificação.

# Especificação Funcional: Atualização de status da OS via canal externo (e-mail)

| Campo | Valor |
|---|---|
| Feature | `external-status-update` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-26 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-09-26 |
| Referências | RF40 ("Endpoint de atualização de status da OS via canal externo (e-mail)"); RF41 (`docs/features/servicelifecycle/estimate-decisions-external-auth/` — autenticação HMAC do gateway externo, implementada); `docs/features/servicelifecycle/decide-estimate-lines/functional-spec.md` (RF15/RF16, regra de decisão reaproveitada); `docs/features/servicelifecycle/status-nominal-mapping/functional-spec.md` (RF39, nomes nominais de status); `docs/Architecture-Decisions.md` AD-010 (`statusSnapshot` recalculado por comando); `.claude/rules/epic-3-service-lifecycle.md` |

## Nota sobre a origem do requisito

RF40 é um requisito da Fase 2 do Tech Challenge ainda **não registrado no board do Miro** (mesma situação de
RF38/RF39/RF41). O texto de referência usado aqui foi fornecido pelo responsável em 2026-09-26:

- existe hoje só notificação de **saída** ao Customer (`SimulatedEmailCustomerNotificationAdapter`,
  `SimulatedEmailCustomerEstimateNotificationAdapter`); não há canal de **entrada** que receba uma
  atualização de status originada externamente (ex.: resposta de e-mail processada por ferramenta de
  terceiros);
- o payload externo identifica a OS e o novo status pretendido; a transição só ocorre se for válida no
  domínio, sem contornar invariantes do agregado;
- transição inválida (ex.: pular etapas, OS já `DELIVERED`) → `409` com código de erro estável, sem mudar o
  estado;
- o mecanismo de autenticação/verificação de origem deve ser decidido, provavelmente com a mesma decisão de
  desenho de RF41;
- a spec deve dizer explicitamente se o "e-mail" é literal ou simbólico; o texto de referência admite que
  uma simulação simples (endpoint HTTP com a intenção já extraída do e-mail) atende.

Esta versão substitui o rascunho de 2026-09-23, escrito antes de RF41 existir, que assumia um payload
literal de e-mail com conferência de remetente e tratava RF41 como autenticação de um endpoint novo. Na
prática, RF41 foi implementado como autenticação HMAC do endpoint já existente
`POST /api/estimates/{estimateId}/decisions`.

## Decisões desta spec

| # | Decisão | Resolução |
|---|---|---|
| 1 | O que é o "novo status pretendido" | O status da OS **não é setável**: `statusSnapshot` é recalculado a partir de comandos sobre as `ServiceExecution` (AD-010). O "status pretendido" é, portanto, a **intenção do Customer sobre o orçamento pendente da OS**, traduzida para o comando de domínio já existente de decisão de orçamento (`decide-estimate-lines`). É a única transição de status da OS que legitimamente se origina de uma resposta do Customer; iniciar, concluir ou entregar a execução são ações internas da oficina. |
| 2 | E-mail literal ou simbólico | **Simbólico.** O endpoint é HTTP simples e recebe a intenção **já extraída** do e-mail por uma ferramenta de automação de terceiros (simulada). Não há payload no formato de mensagem de e-mail, parsing de conteúdo, SMTP/MIME nem conferência do remetente. |
| 3 | Autenticação/verificação de origem | **A mesma de RF41**: assinatura HMAC-SHA256 com os headers `X-Estimate-Gateway-Timestamp`/`X-Estimate-Gateway-Signature`, segredo compartilhado, janela de 300s e limite de tamanho de corpo. O gateway externo não é um usuário interno e não usa JWT. Estender a proteção HMAC ao novo path exige um adendo em `estimate-decisions-external-auth/technical-spec.md` (hoje o filtro protege só `/api/estimates/*/decisions`), a ser tratado no technical-spec desta feature. |
| 4 | Granularidade da decisão | **A OS inteira**: a intenção aprova ou rejeita **todas** as linhas ainda `PENDING` do orçamento `SENT` da OS. Decisão linha a linha continua disponível só pelo endpoint existente de `decide-estimate-lines`. |

## Problema e resultado esperado

Hoje o Customer é avisado por e-mail (simulado) quando um orçamento é gerado, mas sua resposta não tem por
onde entrar no sistema: a decisão só é registrada por um canal interno autenticado por JWT
(`POST /api/estimates/{estimateId}/decisions`) ou, desde RF41, por um gateway que já conhece a Estimate e cada
`ServiceExecution`. Uma ferramenta que processa a resposta de e-mail do Customer tipicamente só sabe **qual OS**
e **o que o Customer quer** ("aprovo" / "não aprovo"), não os identificadores internos do orçamento.

Resultado esperado: existe um endpoint de entrada no nível da OS que recebe a intenção do Customer extraída
do e-mail e, se ela for válida para o estado atual da OS, aplica a decisão de orçamento pelo mesmo caminho de
domínio de `decide-estimate-lines`, fazendo o status da OS avançar conforme as regras já existentes. Se a
intenção não for aplicável, nada muda e a resposta é `409` com código estável.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Ferramenta externa de automação de e-mail (simulada) | Envia, autenticada por HMAC, a OS e a intenção `APPROVED`; as linhas pendentes do orçamento são autorizadas e a OS avança para execução. |
| Ferramenta externa de automação de e-mail | Envia a intenção `REJECTED`; as linhas pendentes são rejeitadas e a OS segue as regras já existentes para execuções rejeitadas. |
| Ferramenta externa de automação de e-mail | Envia uma intenção para uma OS sem orçamento pendente de decisão (ainda em diagnóstico, já decidida, expirada, finalizada ou entregue); a API responde `409` e nada muda. |
| Chamador sem assinatura válida | Chama o endpoint sem os headers HMAC, com assinatura incorreta ou fora da janela; a API responde `401` antes de qualquer regra de domínio. |
| Customer, Manager, Admin | Continuam usando `POST /api/estimates/{estimateId}/decisions` (JWT ou HMAC) sem nenhuma mudança; o novo endpoint é um canal adicional, não uma substituição. |

### Cenário principal — aprovação via e-mail

1. A OS tem um orçamento `SENT` com uma ou mais `ServiceExecution` `PENDING`.
2. O Customer responde ao e-mail aprovando; a ferramenta externa extrai a intenção e chama o endpoint com o
   identificador da OS e `intendedStatus = APPROVED`, assinando a chamada conforme RF41.
3. Todas as linhas `PENDING` desse orçamento são decididas como `APPROVED` pelo mesmo caso de uso de
   `decide-estimate-lines`, com os mesmos efeitos (autorização da execução, tentativa de reserva de estoque,
   orçamento `CLOSED` quando não restarem linhas pendentes, `statusSnapshot` recalculado).
4. A resposta é `200` com a OS atualizada, no mesmo formato da resposta de `decide-estimate-lines`.

### Cenário alternativo — rejeição via e-mail

Igual ao principal, com `intendedStatus = REJECTED`: todas as linhas pendentes vão para `REJECTED`. Se todas
as execuções da OS ficarem terminais, a OS passa a `COMPLETED` pelas regras já existentes.

### Cenário alternativo — intenção não aplicável

1. A OS não tem orçamento `SENT` com linha `PENDING` (ex.: ainda em diagnóstico, orçamento já decidido,
   `EXPIRED`, OS `COMPLETED` ou `DELIVERED`), ou tem mais de um orçamento `SENT` ao mesmo tempo.
2. A API responde `409` com código de erro estável e nenhuma linha é decidida.

### Cenário alternativo — OS inexistente

A API responde `404` com o código `NOT_FOUND` já usado pelas demais rotas de OS.

### Cenário alternativo — chamador não autenticado

A API responde `401` (mesmo `ErrorResponse` `UNAUTHORIZED` de RF41), sem invocar nenhum caso de uso.

## Regras de negócio

1. O endpoint não altera `statusSnapshot` diretamente nem cria transição de estado nova: ele só traduz a
   intenção em decisões de orçamento e delega ao caso de uso de `decide-estimate-lines`, que aplica
   `authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`. O status resultante é o que o agregado
   recalcular.
2. Valores aceitos para `intendedStatus`: somente `APPROVED` e `REJECTED` (a decisão do Customer sobre o
   orçamento). Qualquer outro valor é erro de validação (`400`), não uma tentativa de transição — o endpoint
   não aceita nomes de status da OS (internos ou nominais de RF39) porque não é um setter de status.
3. O orçamento alvo é o único orçamento `SENT` da OS. Sem orçamento `SENT`, sem linha `PENDING` nele, ou com
   mais de um orçamento `SENT` (ambiguidade que a intenção simbólica não resolve), a resposta é `409` com
   código estável e nada muda.
4. A decisão é tudo-ou-nada, como em `decide-estimate-lines`: ou todas as linhas pendentes são decididas, ou
   nenhuma.
5. Linhas já decididas antes da chamada (ex.: parte decidida pelo canal interno) não são alteradas; a
   intenção se aplica só às que continuam `PENDING`.
6. A autenticação é exclusivamente a assinatura HMAC de RF41. Um JWT de usuário interno **não** dá acesso a
   este endpoint: usuários internos continuam decidindo pelo endpoint de `decide-estimate-lines`.
7. Todas as falhas de autenticação respondem `401`, sem revelar se a OS existe (a verificação acontece antes
   de qualquer acesso a dados de domínio).

## Fora de escopo

- payload no formato de mensagem de e-mail, parsing de texto livre, SMTP/MIME ou integração com provedor de
  e-mail real — a intenção chega já extraída;
- conferência do remetente do e-mail com o e-mail do Customer cadastrado;
- qualquer transição da OS que não seja a decisão do orçamento (diagnóstico, atribuição de técnico, início,
  progresso, conclusão, entrega);
- decisão parcial (aprovar algumas linhas e rejeitar outras) por este canal — continua em
  `decide-estimate-lines`;
- alterar o contrato de `POST /api/estimates/{estimateId}/decisions`;
- idempotência além da já garantida pelo domínio (reenviar a mesma intenção depois de aplicada resulta em
  `409`, pois não restam linhas `PENDING`);
- registrar RF40 no board do Miro (task separada).

## Critérios de aceite

- [ ] Dada uma OS com orçamento `SENT` e linhas `PENDING`, quando o endpoint é chamado com assinatura HMAC
      válida e `intendedStatus = APPROVED`, então todas as linhas pendentes são autorizadas pelo caso de uso
      de `decide-estimate-lines` e a resposta é `200` com a OS atualizada.
- [ ] O mesmo cenário com `intendedStatus = REJECTED` rejeita todas as linhas pendentes.
- [ ] Dada uma OS sem orçamento `SENT` com linha `PENDING` (em diagnóstico, já decidida, orçamento `EXPIRED`,
      `COMPLETED` ou `DELIVERED`), quando o endpoint é chamado, então a resposta é `409` com código estável e
      nenhum estado muda.
- [ ] Dada uma OS com mais de um orçamento `SENT`, quando o endpoint é chamado, então a resposta é `409` e
      nada muda.
- [ ] Dada uma OS inexistente com assinatura válida, a resposta é `404` `NOT_FOUND`.
- [ ] Dado `intendedStatus` ausente ou com valor diferente de `APPROVED`/`REJECTED`, a resposta é `400`
      `VALIDATION_ERROR` e nada muda.
- [ ] Dada uma chamada sem assinatura, com assinatura inválida, fora da janela ou com corpo acima do limite,
      a resposta é `401` e nenhum caso de uso é invocado.
- [ ] Dada uma chamada apenas com JWT válido (qualquer role, inclusive `ADMIN`), sem assinatura HMAC, a
      resposta é `403` (autenticado, mas sem a authority do gateway) e nada muda.
- [ ] Reenviar a mesma intenção depois de aplicada resulta em `409`, sem efeito adicional.
- [ ] `POST /api/estimates/{estimateId}/decisions` continua com o mesmo comportamento (JWT e HMAC).

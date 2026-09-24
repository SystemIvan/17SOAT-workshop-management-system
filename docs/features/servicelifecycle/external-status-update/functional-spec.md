# Especificação Funcional: Decisão do Customer sobre orçamento via canal externo de e-mail

| Campo | Valor |
|---|---|
| Feature | `external-status-update` |
| Status | Draft |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-09-23 |
| Aprovado por | — |
| Aprovado em | — |
| Referências | RF40; RF41 (`docs/features/servicelifecycle/estimate-approval-email-authentication/functional-spec.md` — mecanismo de autenticação deste mesmo endpoint); enunciado do Tech Challenge Fase 2; `docs/features/servicelifecycle/decide-estimate-lines/functional-spec.md` (RF15/RF16, regra de domínio reaproveitada); `docs/features/servicelifecycle/notifications-estimate-generated/functional-spec.md` (canal de saída que este endpoint espelha na direção contrária); `.claude/rules/epic-3-service-lifecycle.md` |

## Nota sobre a origem do requisito

RF40 é um requisito novo da Fase 2 do Tech Challenge, ainda **não registrado no board do Miro** — na
mesma situação de RF38/RF39. `docs/Architecture.md` §2.3 ainda não cobre a faixa RF39–RF41. RF41, título
confirmado pelo responsável: "Mecanismo de autenticação do endpoint de aprovação de orçamento" — ou seja,
RF41 **não é** um segundo endpoint de canal externo independente, é a decisão de autenticação
especificamente para o endpoint desta spec. Por isso RF41 vira sua própria feature/spec (ver referências),
e este documento não decide autenticação — apenas depende do resultado dela.

## Decisões confirmadas nesta sessão

| # | Decisão | Resolução |
|---|---|---|
| 1 | O que o "novo status pretendido" representa | **(a)** Este endpoint é um canal alternativo (e-mail) para a **mesma decisão comercial do Customer** já coberta por `decide-estimate-lines` (RF15/RF16): aprovar ou rejeitar uma ou mais `ServiceExecution` de uma Estimate. Não é um setter genérico de `ServiceOrderStatus`. |
| 2 | Mecanismo de autenticação/verificação de origem | Fora desta spec — é o próprio RF41, tratado como pré-requisito (ver "Relação com RF41"). |
| 3 | E-mail literal ou simbólico | Literal na modelagem do payload (ver "Regras de negócio" e "Decisão: e-mail literal" abaixo), sem implementar um cliente SMTP/MIME real. |
| 4 | Relação com RF41 | Esclarecida: RF41 é a spec de autenticação deste mesmo endpoint, não um endpoint irmão. |

## Problema e resultado esperado

Hoje o Customer só recebe uma notificação de saída quando uma Estimate é gerada
(`SimulatedEmailCustomerEstimateNotificationAdapter`, feature `notifications-estimate-generated`). Para
decidir (aprovar/rejeitar) essa Estimate, ele depende de um canal interno já autenticado
(`POST /api/estimates/{estimateId}/decisions`, feature `decide-estimate-lines`) — não existe hoje um jeito
do Customer responder à notificação de e-mail e essa resposta virar, por si só, a decisão.

Resultado esperado: existe um endpoint HTTP que recebe um payload no formato de uma mensagem de e-mail
recebida (remetente, referência à Estimate/OS, corpo com a decisão), processado por uma automação externa
simulada, e que aplica a mesma regra de negócio e o mesmo caminho de domínio já usados por
`decide-estimate-lines` — nunca uma segunda fonte de verdade para a decisão do Customer.

## Decisão: e-mail literal

Diferente da proposta original (payload simbólico já pré-extraído), o payload deste endpoint **modela uma
mensagem de e-mail**: contém o remetente (endereço de e-mail) e o conteúdo de onde a decisão é extraída
(ex.: referência à Estimate/OS e à(s) `ServiceExecution`(s) decidida(s), e se cada uma foi aprovada ou
rejeitada). Continua sendo HTTP simples — não há integração SMTP/MIME real nem recebimento de e-mail de
fato; o payload apenas **representa** o e-mail já recebido por uma ferramenta de automação externa
(análoga a um provedor de inbound-email parsing). O formato exato de campos é definido no technical-spec.

O remetente informado é cruzado com o e-mail do Customer já registrado (`registration.Customer`/`Email`)
associado à Service Order/Estimate — isso é uma regra de negócio desta feature (garantir que a decisão
veio do Customer certo), não o mecanismo de autenticação do chamador (isso é RF41: quem tem permissão de
chamar o endpoint em si, independente de qual e-mail o payload alega representar).

## Relação com RF41

RF41 decide **como o chamador do endpoint é autenticado/verificado** (ex.: segredo compartilhado entre a
automação de e-mail e o backend). Esta feature (RF40) decide **o que o payload significa e qual regra de
negócio ele aciona**, e depende do resultado de RF41 para o technical-spec (o contrato HTTP inclui os
elementos exigidos pelo mecanismo de autenticação escolhido em RF41 — ex. um header de credencial). RF40
não deve ser implementado (checkpoint de código) antes de RF41 estar aprovado, já que o technical-spec
desta feature precisa incorporar o contrato de autenticação definido lá.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Automação externa de e-mail (simulada) | Envia um payload representando a resposta do Customer a uma Estimate, identificando a(s) `ServiceExecution`(s) e a decisão (aprovar/rejeitar); a mesma regra de domínio de `decide-estimate-lines` é aplicada. |
| Automação externa de e-mail | Envia um payload cuja decisão não é aplicável ao estado atual (ex.: `ServiceExecution` já decidida, ou não pertence à Estimate referenciada); a API responde `409 Conflict`/erro estável e nada muda. |
| Automação externa de e-mail | Envia um payload cujo remetente não corresponde ao e-mail do Customer registrado para aquela OS/Estimate; a API rejeita a chamada sem aplicar nenhuma decisão. |
| Chamador não autenticado | Chama o endpoint sem a credencial exigida por RF41; a API rejeita antes de tocar qualquer regra de domínio (comportamento definido em RF41, referenciado aqui). |
| Customer, Manager, Service Advisor | Continuam usando `POST /api/estimates/{estimateId}/decisions` (`decide-estimate-lines`) sem qualquer mudança de comportamento; este endpoint é um canal adicional para a mesma decisão, não uma substituição. |

### Cenário principal — decisão aplicada via e-mail

1. Uma Estimate tem uma ou mais `ServiceExecution` em `PENDING`.
2. A automação externa (autenticada conforme RF41) chama o endpoint com um payload representando o e-mail
   do Customer, identificando a Estimate/OS, o remetente, e a decisão (`APPROVED`/`REJECTED`) para uma ou
   mais `ServiceExecution`.
3. O remetente confere com o e-mail do Customer associado.
4. A mesma regra de domínio de `decide-estimate-lines` é aplicada (mesmo caso de uso/mesmos métodos de
   domínio `ServiceOrder.authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`), com os mesmos
   efeitos já documentados naquela feature (reserva de estoque quando aplicável, `statusSnapshot`
   recalculado etc.).

### Cenário alternativo — decisão inválida para o estado atual

1. O payload referencia uma `ServiceExecution` que não está `PENDING`, ou que não pertence à Estimate
   informada.
2. A API responde `409 Conflict` com o código de erro estável já usado em `decide-estimate-lines`
   (`INVALID_STATE_TRANSITION`/"não encontrado", conforme o caso), sem aplicar nenhuma decisão da chamada
   (mesma regra tudo-ou-nada de `decide-estimate-lines`).

### Cenário alternativo — remetente não corresponde ao Customer

1. O payload informa um remetente que não corresponde ao e-mail do Customer registrado para a OS/Estimate
   referenciada.
2. A API rejeita a chamada com um erro estável, sem aplicar nenhuma decisão.

### Cenário alternativo — chamador não autenticado

1. Uma chamada chega sem a credencial exigida por RF41.
2. A API rejeita a chamada conforme definido em RF41, sem invocar nenhum caso de uso de domínio.

## Regras de negócio

1. O endpoint **reaproveita integralmente** a regra de negócio e o caso de uso já existentes em
   `decide-estimate-lines` (RF15/RF16) — não cria uma segunda implementação da decisão de aprovação/
   rejeição de `ServiceExecution`.
2. O payload é modelado como uma mensagem de e-mail recebida (remetente + referência à decisão), não como
   um comando de domínio já pré-extraído; o mapeamento payload → chamada do caso de uso é responsabilidade
   desta feature (detalhado no technical-spec).
3. O e-mail remetente informado no payload deve corresponder ao e-mail do Customer associado à Service
   Order/Estimate referenciada; divergência é rejeitada sem aplicar decisão.
4. Todas as regras de negócio já documentadas em `decide-estimate-lines` (decisão em lote, tudo-ou-nada,
   `serviceExecutionId` repetido rejeitado, só decide `PENDING`, efeitos de `APPROVED`/`REJECTED`) se
   aplicam integralmente a este canal — nenhuma delas é relaxada ou duplicada.
5. A autenticação do chamador (quem pode invocar este endpoint) é definida por RF41, não por esta feature;
   este documento apenas consome esse contrato.
6. Este endpoint não introduz nenhuma transição de estado nova além das já cobertas por
   `authorizeExecutionFromEstimate`/`rejectExecutionFromEstimate`.

## Fora de escopo

- implementar um cliente de e-mail real, parsing de MIME/SMTP ou integração com um provedor de e-mail de
  fato — o payload apenas representa uma mensagem já processada por uma automação externa simulada;
- decidir o mecanismo de autenticação/verificação de origem do chamador — isso é RF41;
- qualquer transição de estado da Service Order/`ServiceExecution` fora da decisão de aprovação/rejeição
  de Estimate já coberta por `decide-estimate-lines` (ex.: iniciar execução, atribuir técnico, finalizar
  OS) — este endpoint não é um "setter" genérico de status;
- alterar o endpoint interno já existente `POST /api/estimates/{estimateId}/decisions` ou seu contrato;
- os 6 nomes nominais de status (RF39) — não fazem parte do payload nem da resposta deste endpoint;
- registrar RF40/RF41 no board do Miro (task separada, mesma situação de RF38/RF39).

## Critérios de aceite

- [ ] Dado um payload representando um e-mail do Customer com decisão de aprovação para uma
      `ServiceExecution` `PENDING` de uma Estimate existente, com remetente correspondente ao Customer da
      OS e credencial válida (RF41), quando o endpoint é chamado, então a mesma regra de domínio de
      `decide-estimate-lines` é aplicada e a `ServiceExecution` é autorizada com os mesmos efeitos já
      especificados naquela feature.
- [ ] O mesmo cenário, com decisão de rejeição, move a `ServiceExecution` para `REJECTED`.
- [ ] Dado um payload referenciando uma `ServiceExecution` que não está `PENDING` ou que não pertence à
      Estimate informada, quando o endpoint é chamado, então a API responde com erro estável (`409`/"não
      encontrado", conforme o caso) e nenhuma decisão é aplicada.
- [ ] Dado um payload cujo remetente não corresponde ao e-mail do Customer associado à OS/Estimate, quando
      o endpoint é chamado, então a chamada é rejeitada e nenhuma decisão é aplicada.
- [ ] Dado um payload sem a credencial exigida por RF41, quando o endpoint é chamado, então a chamada é
      rejeitada antes de qualquer efeito de domínio.
- [ ] Todas as regras de `decide-estimate-lines` (lote tudo-ou-nada, `serviceExecutionId` duplicado
      rejeitado, apenas `PENDING` pode ser decidida) continuam valendo integralmente por este canal.

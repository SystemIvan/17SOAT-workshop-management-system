# Especificação Funcional: Aprovação ou recusa do orçamento por link no e-mail

| Campo | Valor |
|---|---|
| Feature | `estimate-approval-link` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-10 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-10 |
| Referências | RF53 (`rf.md`); Enunciado da Fase 2 ("Aprovação de orçamento"); `../notifications-so-status-change/functional-spec.md` (RF52, envia o e-mail de orçamento que esta feature complementa; **dependência**); `../external-status-update/functional-spec.md` (RF40, regra de decisão do orçamento inteiro reaproveitada); `../decide-estimate-lines/functional-spec.md` (RF15/RF16); `../../../adr/ADR-007-external-gateway-hmac-authentication.md` |

## Situação: feature pausada (2026-10-10)

Pausada por decisão de Santiago Silvestre em 2026-10-10. Ao reler o enunciado da Fase 2, ficou claro que a
"Aprovação de Orçamento" pedida é um **endpoint que recebe notificações externas**, já atendido pela RF41 e pela RF40
(webhook HMAC, ADR-007). O enunciado não pede que o cliente decida por link nem páginas HTML. A demo usa o RF40 via
Postman como a "ferramenta externa". A prioridade passou para os entregáveis obrigatórios de infraestrutura
(Kubernetes, Terraform e deploy no CI/CD).

O status `Approved` desta spec continua válido e não foi revogado. `docs/features/README.md` não prevê um status de
pausa, por isso a pausa fica registrada nesta nota. Para retomar a feature:

1. o responsável confirma que esta spec continua válida;
2. o ADR-008 é ratificado pelo time;
3. a `technical-spec.md` é revisada e aprovada.

## Nota sobre a origem do requisito

Esta feature nasceu da discussão da RF52 em 2026-10-05. O e-mail de orçamento chegaria ao cliente, mas ele não
teria como decidir a partir dele: responder o e-mail não chega a lugar nenhum, porque nenhum componente lê a caixa
de resposta, e o endpoint do RF40 exige assinatura HMAC de sistema, que o navegador do cliente não consegue
produzir. O responsável escolheu links com página de confirmação, como feature própria, registrada como RF53
(`rf.md`). O texto da RF53 no Jira e no board foi cadastrado manualmente pelo responsável a partir do `rf.md`; se
divergir do que está aqui, a divergência deve ser resolvida e esta spec volta para `Draft`.

## Histórico de aprovação

- 2026-10-10 — versão inicial aprovada por Santiago Silvestre (RF53 própria; página de confirmação com serviços,
  valores e total).

## Problema e resultado esperado

O cliente recebe por e-mail o orçamento da sua OS, com serviços, valores, total e validade, mas não tem como
aprová-lo ou recusá-lo a partir dali. A decisão só entra hoje por um usuário interno (JWT) ou por um sistema
integrador (RF40/RF41, HMAC).

Resultado esperado: o e-mail de orçamento traz dois links, **Aprovar** e **Recusar**. Ao clicar, o cliente vê uma
página de confirmação com o resumo do orçamento. Ao confirmar, a decisão é aplicada pelas mesmas regras de domínio
já existentes, e o cliente vê uma página de resultado. A OS muda de status e o cliente recebe o e-mail de mudança
de status da RF52. O cliente não precisa de login.

## Atores e cenários

| Ator | Papel |
|---|---|
| Customer | Dono da OS. Recebe o e-mail de orçamento, abre o link e confirma a decisão. Não tem login. |
| Programa que abre links automaticamente (antivírus, pré-visualização do leitor de e-mail) | Pode abrir os links sem ação do cliente. Nunca pode provocar uma decisão. |
| Sistema (`servicelifecycle`) | Gera os links no e-mail de orçamento, mostra as páginas e aplica a decisão confirmada. |

### Cenário principal — aprovação

1. O cliente recebe o e-mail de orçamento com os links **Aprovar** e **Recusar**.
2. Clica em **Aprovar**. Abre uma página de confirmação com o número da OS, os serviços com valores, o total, a
   validade e a decisão escolhida ("Você está aprovando este orçamento"), com o botão **Confirmar aprovação**.
3. O cliente confirma. Todas as linhas ainda pendentes do orçamento são aprovadas, pela mesma regra do RF40.
4. Aparece a página de resultado: "Orçamento aprovado. Você receberá um e-mail quando o status da sua OS mudar."
5. A OS muda de status e o cliente recebe o e-mail de mudança de status (RF52).

### Cenário alternativo — recusa

Igual ao principal, com **Recusar** e **Confirmar recusa**. Todas as linhas pendentes são recusadas, e a OS segue
as regras já existentes para execuções recusadas.

### Cenário alternativo — link aberto sem confirmação

1. Um antivírus, ou o próprio cliente, abre o link.
2. A página de confirmação é exibida, mas ninguém confirma.
3. Nada muda: o orçamento continua pendente.

### Cenário alternativo — orçamento já decidido

1. O cliente clica num link de um orçamento que já foi decidido, por ele mesmo antes, pelo outro link, pela
   oficina ou pelo RF40.
2. Uma página informa que o orçamento já foi decidido e não oferece botão de confirmação. Nada muda.
3. Se dois cliques de confirmação concorrerem, a primeira decisão vale e a segunda mostra a mesma página.

### Cenário alternativo — link vencido

1. O cliente clica depois da validade do orçamento, ou o orçamento já está `EXPIRED`.
2. Uma página informa que o prazo de aprovação terminou e orienta a falar com a oficina. Nada muda.

### Cenário alternativo — link inválido

1. O link foi alterado, está incompleto ou não foi gerado pelo sistema.
2. Uma página genérica informa que o link é inválido, sem dizer se a OS ou o orçamento existem. Nada muda.

## Regras de negócio

1. **Os links fazem parte do e-mail de orçamento.** O e-mail de "orçamento gerado" da RF52 passa a trazer os dois
   links e uma frase explicando que a decisão vale para o orçamento inteiro. O restante do conteúdo e o gatilho da
   RF52 não mudam.
2. **Cada link vale para um único orçamento e uma única decisão.** O link de aprovação não serve para recusar, nem
   para outro orçamento. Ele não pode ser forjado nem alterado sem ficar inválido.
3. **Abrir o link nunca decide.** Só a confirmação explícita na página aplica a decisão. Isso protege contra
   programas que abrem links automaticamente.
4. **Mesma regra de decisão do RF40.** A confirmação decide todas as linhas ainda `PENDING` do orçamento, tudo ou
   nada, pelo mesmo caso de uso de domínio. Linhas já decididas não mudam. O status da OS é o que o aggregate
   recalcular. Esta feature não cria regra de decisão nova.
5. **A validade do link é a validade do orçamento.** Depois de `expiresAt`, ou com o orçamento fora de `SENT`, o
   link não decide mais nada.
6. **Primeira decisão vence.** Depois de decidido o orçamento, por qualquer canal, novos acessos mostram "já
   decidido" e nada muda.
7. **Sem login.** Quem tem o link pode decidir. Essa é a mesma premissa de qualquer aprovação por e-mail: o link
   chega só ao e-mail cadastrado do dono da OS.
8. **Exposição mínima de dados.** As páginas mostram só o que o próprio e-mail de orçamento já mostra: número da
   OS, serviços, valores, total e validade. Nunca mostram nome, e-mail, telefone, placa nem endereço do cliente.
   Páginas de erro não revelam se a OS ou o orçamento existem.
9. **Canais existentes intactos.** A decisão interna com JWT (`decide-estimate-lines`) e o endpoint do RF40/RF41
   (HMAC, sistema a sistema) continuam iguais. O link é um terceiro canal, voltado ao cliente.
10. **Sem dados pessoais em log.** Logs de acesso e de decisão por link trazem só IDs opacos e o resultado. O
    link completo nunca vai para log, porque ele é a credencial de decisão.
11. **Páginas em português, simples.** Uma página de confirmação e uma de resultado/erro, sem layout elaborado. A
    API continua sendo JSON em todos os outros endpoints.

## Decisões tomadas

1. **Número de RF** (Santiago Silvestre, 2026-10-10): a feature tem RF própria, **RF53**, separada da RF52 e da
   RF40, descrita em `rf.md` e cadastrada manualmente no Jira e no board pelo responsável.
2. **Detalhe da página de confirmação** (Santiago Silvestre, 2026-10-10): a página mostra os serviços com seus
   valores, além do número da OS, do total e da validade, para o cliente confirmar sabendo o que aprova ou recusa.
   A alternativa de mostrar só número da OS e total foi descartada.

## Fora de escopo

- login, portal do cliente ou área "meus orçamentos";
- decisão parcial (aprovar algumas linhas e recusar outras) pelo link;
- reenviar o e-mail ou gerar novo link para orçamento vencido;
- cancelar manualmente um link já enviado;
- processar respostas por e-mail;
- alterar o endpoint, o payload ou a autenticação do RF40/RF41, ou o endpoint interno de `decide-estimate-lines`;
- alterar o e-mail de mudança de status da RF52 ou o gatilho do e-mail de orçamento;
- páginas com layout elaborado, internacionalização ou acessibilidade além do HTML básico semântico;
- confirmação por segundo fator (código por SMS, etc.).

## Critérios de aceite

- [ ] O e-mail de orçamento contém os links Aprovar e Recusar e informa que a decisão vale para o orçamento
      inteiro.
- [ ] Abrir um link válido mostra a página de confirmação com número da OS, serviços com valores, total,
      validade e a decisão escolhida, e não altera o orçamento nem a OS.
- [ ] Confirmar a aprovação decide todas as linhas pendentes como aprovadas, mostra a página de resultado e a OS
      muda de status conforme as regras existentes.
- [ ] Confirmar a recusa decide todas as linhas pendentes como recusadas e mostra a página de resultado.
- [ ] Depois da decisão, o cliente recebe o e-mail de mudança de status da RF52, quando houver mudança nominal.
- [ ] Um link de um orçamento já decidido, por qualquer canal, mostra "já decidido", sem botão, e nada muda.
- [ ] Um link usado depois da validade do orçamento mostra "prazo encerrado" e nada muda.
- [ ] Um link alterado ou inventado mostra "link inválido", sem revelar se a OS ou o orçamento existem, e nada
      muda.
- [ ] O link de aprovação não pode ser transformado em link de recusa, nem apontado para outro orçamento, sem
      ficar inválido.
- [ ] Duas confirmações concorrentes resultam em uma única decisão aplicada.
- [ ] As páginas não mostram nome, e-mail, telefone, placa nem endereço do cliente.
- [ ] Nenhum log contém o link completo ou dados pessoais em claro.
- [ ] Os endpoints internos (JWT) e o endpoint do RF40 (HMAC) continuam funcionando sem alteração.

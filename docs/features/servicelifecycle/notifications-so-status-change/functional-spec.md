# Especificação Funcional: Notificar o cliente por e-mail a cada mudança de status nominal da OS

| Campo | Valor |
|---|---|
| Feature | `notifications-so-status-change` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-05 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-05 |
| Referências | RF52; RF39 (`../status-nominal-mapping/functional-spec.md`); RF33 (`../../notifications-so-finalized/functional-spec.md`, consolidado com esta história); RF40 (`../external-status-update/functional-spec.md`, sentido de entrada); RF41 (`../estimate-decisions-external-auth/functional-spec.md`); `../notifications-estimate-generated/functional-spec.md`; `../../../adr/ADR-004-notifications-boundary.md`; `../../../adr/ADR-007-external-gateway-hmac-authentication.md` |

## Histórico de aprovação

- 2026-10-05 — versão inicial aprovada por Santiago Silvestre (opção (a) de consolidação com RF33; criação não
  notifica).
- 2026-10-05 — **voltou para `Draft`** por mudança material de escopo pedida pelo responsável: o e-mail de
  "orçamento gerado" passa a ser enviado de verdade, com os valores do orçamento (ver "E-mail de orçamento"). A
  `technical-spec.md` fica obsoleta até a reaprovação desta spec.
- 2026-10-05 — a instrução "responda APROVO/RECUSO" foi descartada, porque nenhum componente lê a caixa de
  resposta e o cliente acharia que decidiu sem a decisão ser aplicada. A decisão do cliente pelo e-mail passa a ser
  feita por links "Aprovar"/"Recusar" com página de confirmação, numa **feature própria**:
  `../estimate-approval-link/`, que depende desta.
- 2026-10-05 — versão revisada (e-mail de orçamento real e informativo; links em feature própria) **reaprovada**
  por Santiago Silvestre.

## Nota sobre a origem do requisito

A fonte desta especificação é a descrição da história RF52 colada pelo responsável nesta sessão. O texto exato
do RF52 no board do Miro não foi consultado; se divergir do que está aqui, a divergência deve ser resolvida e
esta spec volta para `Draft`.

## Problema e resultado esperado

Hoje o cliente só é avisado automaticamente em dois momentos: quando o orçamento é gerado
(`notifications-estimate-generated`) e quando a OS é finalizada/entregue (`notifications-so-finalized`, RF33).
Nos dois casos o "e-mail" é simulado: o adapter só grava uma linha de log. Fora desses dois momentos, o cliente
só descobre que a OS andou se consultar o status ativamente (polling, AD-015).

O RF40 já cobre o sentido de entrada (resposta do cliente → decisão do orçamento). Esta história cobre o sentido
de saída: sempre que o status **nominal** da OS mudar (os 6 estados do RF39: Recebida, Diagnóstico, Aguardando
Aprovação, Execução, Finalizada, Entregue), o cliente dono da OS recebe um e-mail informando o novo status e a
identificação da OS.

Além disso, hoje o cliente nunca recebe de fato o orçamento: o e-mail de "orçamento gerado" é só uma linha de log,
sem valores. Esta história faz esse e-mail chegar ao cliente pelo mesmo canal real, com os serviços, os valores, o
total e a validade.

Fechar o ciclo, ou seja, o cliente aprovar ou recusar a partir do e-mail, é responsabilidade da feature
`estimate-approval-link`. Ela acrescenta a esse e-mail os links "Aprovar"/"Recusar" e a página de confirmação. O
ciclo completo, com as duas features entregues, fica: orçamento enviado por e-mail → cliente clica e confirma →
decisão aplicada → OS muda de status → cliente recebe o e-mail de mudança de status desta história.

## Atores e cenários

| Ator | Papel |
|---|---|
| Customer | Dono da OS; destinatário do e-mail. |
| Sistema (`servicelifecycle`) | Detecta a mudança de status nominal depois que ela é persistida e dispara a notificação como efeito colateral. |
| Manager / Service Advisor / Technician / gateway externo (RF40) | Executam os comandos que mudam o status da OS (diagnóstico, orçamento, decisão, execução, finalização). Não interagem com a notificação. |

### Cenário principal — e-mail de orçamento

1. O orçamento da OS é gerado e enviado; a OS passa para "Aguardando Aprovação".
2. O cliente recebe o e-mail de "orçamento gerado" com a identificação da OS e do orçamento, os serviços com seus
   valores, o valor total e a validade.
3. O cliente também recebe o e-mail de mudança de status para "Aguardando Aprovação" (regra 6).
4. Quando a OS mudar de status por causa da decisão do orçamento, por qualquer canal (interno, RF40 ou os links
   de `estimate-approval-link`), o cliente recebe o e-mail da transição correspondente.

### Cenário principal — mudança de status nominal

1. Uma OS de um cliente com e-mail cadastrado está em "Diagnóstico".
2. O orçamento é gerado e enviado; a OS passa para "Aguardando Aprovação".
3. Depois que a mudança é persistida, o cliente recebe um e-mail informando o novo status ("Aguardando
   Aprovação") e a identificação da OS.
4. O e-mail de "orçamento gerado" continua sendo enviado separadamente, com seu conteúdo próprio.

### Cenário alternativo — mudança interna sem mudança nominal

1. Uma OS está em `AWAITING_ITEMS` (nominal "Execução").
2. A reserva de estoque é concluída e ela passa para `IN_PROGRESS` (nominal "Execução").
3. Nenhum e-mail de mudança de status é enviado.

### Cenário alternativo — comando sem mudança de status

1. Um comando é executado sobre a OS (ex.: atualizar progresso, mudar prioridade) e o status nominal continua o
   mesmo.
2. Nenhum e-mail de mudança de status é enviado.

### Cenário alternativo — criação da OS

1. Uma OS é criada e assume o estado inicial "Recebida".
2. Nenhum e-mail é enviado (não há status anterior; ver "Decisão registrada — criação da OS").

### Cenário de falha — envio indisponível

1. O servidor de e-mail está indisponível.
2. A OS muda de status nominal.
3. O novo status fica persistido normalmente; a falha de envio é registrada em log sem dados pessoais e o fluxo
   continua.

### Cenário de falha — cliente sem e-mail ou não encontrado

1. O cliente da OS não é encontrado ou não tem e-mail utilizável.
2. A OS muda de status nominal.
3. Nenhum e-mail é enviado; um aviso é registrado em log apenas com IDs opacos.

## Regras de negócio

1. **Gatilho.** O e-mail é disparado quando o status nominal da OS (RF39, `ServiceOrderStatusLabel`) depois de um
   comando é diferente do status nominal antes desse comando. A comparação é sempre feita no nível nominal: uma
   mudança do status interno que não muda o nominal (ex.: `AWAITING_ITEMS` ↔ `IN_PROGRESS`, ambos "Execução") não
   dispara e-mail.
2. **Qualquer origem.** A regra vale para toda mudança de status nominal, independentemente de qual comando a
   causou (inclusive a decisão de orçamento recebida pelo gateway externo do RF40) e da direção da transição.
3. **Criação não notifica.** A criação da OS (estado inicial "Recebida") não é uma mudança de status e não gera
   e-mail.
4. **No máximo um e-mail por transição.** Cada mudança de status nominal persistida gera no máximo um e-mail de
   mudança de status. Se um único comando atravessar mais de um estado nominal intermediário, só a transição
   resultante (nominal anterior → nominal final persistido) é notificada.
5. **Consolidação com o aviso de OS finalizada (RF33).** O aviso dedicado de OS finalizada deixa de ser enviado
   como e-mail próprio: as transições para "Finalizada" e para "Entregue" geram, cada uma, apenas o e-mail de
   mudança de status (ver "Decisão registrada" abaixo).
6. **Orçamento gerado continua separado.** O e-mail de "orçamento gerado" (`notifications-estimate-generated`)
   não é absorvido pelo e-mail de mudança de status. O gatilho não muda (geração do orçamento), ele tem conteúdo
   próprio (ver "E-mail de orçamento") e continua sendo enviado além do e-mail de mudança de status para
   "Aguardando Aprovação".
7. **Conteúdo mínimo.** O e-mail identifica a OS e informa o novo status usando o nome nominal do RF39 (nunca o
   valor interno, como `AWAITING_ITEMS`).
8. **Depois do commit.** O e-mail só é enviado depois que a mudança de status foi persistida com sucesso. Se o
   comando falhar ou a transação for desfeita, nenhum e-mail sai.
9. **Falha não desfaz a transição.** Uma falha no envio nunca desfaz, bloqueia ou faz falhar o comando que mudou
   o status: a falha é registrada em log e o fluxo continua. Não há reenvio automático nesta história.
10. **Cliente sem e-mail ou não encontrado.** A notificação é ignorada e um aviso é registrado em log contendo
    apenas IDs opacos (ID da OS, ID do cliente). Observação: hoje `ContactInfo` exige e-mail no cadastro de
    Customer, então o caso prático é o cliente não encontrado; a regra continua valendo caso essa obrigatoriedade
    mude.
11. **Sem dados pessoais nos logs.** Logs de envio, falha ou aviso contêm apenas IDs opacos e, quando necessário,
    o e-mail mascarado (mesma máscara já usada pelos adapters atuais). Nome, e-mail em claro, telefone, placa ou
    endereço nunca aparecem em log.
12. **Sem estado persistido de notificação.** Esta história não cria histórico, fila ou status de entrega de
    notificação.

## E-mail de orçamento

Regras do e-mail de "orçamento gerado" (complementam as regras de negócio acima):

1. **Envio real.** O e-mail de orçamento sai pelo mesmo canal de e-mail dos avisos de mudança de status, com as
   mesmas garantias: só depois do commit da geração, falha de envio não desfaz a geração do orçamento, cliente não
   encontrado gera só um aviso em log com IDs, e nenhum dado pessoal sem máscara aparece em log.
2. **Conteúdo mínimo.** O e-mail traz:
   - a identificação da OS e do orçamento;
   - cada serviço do orçamento com seu valor (serviço + itens de estoque da linha);
   - o valor total do orçamento;
   - a validade do orçamento.
3. **Sem instrução de resposta por e-mail.** O e-mail não pede que o cliente responda com uma palavra-chave,
   porque nenhum componente processa respostas. Até `estimate-approval-link` ser entregue, o e-mail é apenas
   informativo, e a decisão continua pelos canais que já existem (interno com JWT ou RF40). O conteúdo do e-mail
   deve permitir que `estimate-approval-link` acrescente os links sem mudar as demais regras desta seção.
4. **Um e-mail por orçamento gerado.** Cada geração de orçamento produz exatamente um e-mail de orçamento.

## Decisão registrada — consolidação com o aviso de "OS finalizada" (RF33)

A história diz: "Ao passar para 'Finalizada', não pode sair o aviso novo junto com o aviso atual de OS
finalizada". O código atual, porém, dispara o aviso de RF33 em `FinalizeServiceOrderUseCase`, que leva a OS de
`COMPLETED` para `DELIVERED` — ou seja, na transição nominal para **"Entregue"**, não para "Finalizada"
(`COMPLETED` → "Finalizada" é atingido em `CompleteExecutionUseCase`, quando todas as execuções terminam).

Opções avaliadas:

- **(a)** O aviso de RF33 deixa de existir como e-mail próprio e é absorvido pelo e-mail genérico de mudança de
  status; "Finalizada" e "Entregue" geram, cada uma, um único e-mail de mudança de status.
- (b) Mover o conteúdo "veículo pronto para retirada" para a transição para "Finalizada" e enviar o e-mail
  genérico em "Entregue".
- (c) Manter o aviso de RF33 em "Entregue", consolidado com o e-mail de mudança de status dessa transição.

**Decisão: opção (a).** Confirmada por Santiago Silvestre em 2026-10-05. O aviso dedicado de RF33
(`notifications-so-finalized`) é substituído pelo e-mail de mudança de status; o RF33 passa a ser atendido pelas
transições para "Finalizada" e "Entregue" desta história, sem caso especial de conteúdo.

## Decisão registrada — criação da OS

A criação da OS leva a OS ao estado inicial "Recebida", mas não é uma mudança de status (não existe status
anterior). **Decisão: a criação não gera e-mail.** Confirmada por Santiago Silvestre em 2026-10-05.

## Fora de escopo

- receber e processar respostas do cliente por e-mail (ler caixa de entrada, interpretar texto);
- links "Aprovar"/"Recusar", página de confirmação e o endpoint público correspondente: feature
  `estimate-approval-link`;
- alterar o endpoint, o payload ou as regras do RF40/RF41;
- decisão parcial do orçamento por e-mail;
- SMS, push, WhatsApp ou qualquer outro canal além de e-mail;
- templates HTML elaborados, internacionalização do conteúdo e preferências de opt-out do cliente;
- histórico, auditoria, fila ou reenvio automático de notificações (nenhuma tabela nova);
- alterar o gatilho do e-mail de "orçamento gerado";
- notificar Technician ou qualquer ator que não seja o Customer dono da OS;
- alterar a lógica de precedência de `statusSnapshot` ou o mapeamento nominal do RF39;
- alterar qualquer contrato HTTP existente;
- escolha do canal técnico (SMTP real com Mailpit na demo vs. manter o log simulado), origem do evento e
  configuração de credenciais — decisões da `technical-spec.md`.

## Critérios de aceite

- [ ] Dado uma OS em "Diagnóstico" de um cliente com e-mail cadastrado, quando o orçamento é gerado e a OS passa
      para "Aguardando Aprovação", então o cliente recebe um e-mail informando o novo status nominal e a
      identificação da OS.
- [ ] Nesse mesmo cenário, o e-mail de "orçamento gerado" continua sendo enviado separadamente.
- [ ] Dado uma OS em `AWAITING_ITEMS`, quando ela passa para `IN_PROGRESS`, então nenhum e-mail de mudança de
      status é enviado.
- [ ] Um comando que não altera o status nominal da OS não gera e-mail de mudança de status.
- [ ] A criação de uma OS não gera e-mail.
- [ ] Quando a OS passa para "Finalizada", o cliente recebe exatamente um e-mail referente a essa transição.
- [ ] Quando a OS passa para "Entregue", o cliente recebe exatamente um e-mail referente a essa transição.
- [ ] O aviso dedicado de OS finalizada (RF33) não é mais enviado como e-mail separado em nenhuma transição.
- [ ] Toda transição nominal (Recebida → Diagnóstico, Diagnóstico → Aguardando Aprovação, Aguardando Aprovação →
      Execução, Execução → Finalizada, Finalizada → Entregue, e qualquer outra produzida pelos comandos
      existentes) gera exatamente um e-mail de mudança de status.
- [ ] Dado que o servidor de e-mail está indisponível, quando a OS muda de status, então o novo status fica
      persistido e a falha é registrada em log sem dados pessoais.
- [ ] Se o comando que mudaria o status falhar (regra de negócio violada ou rollback), nenhum e-mail é enviado.
- [ ] Dado um cliente sem e-mail cadastrado ou não encontrado, quando a OS muda de status, então nenhum e-mail é
      enviado e um aviso é registrado em log apenas com IDs.
- [ ] Nenhum log produzido por esta feature contém e-mail em claro, nome, telefone ou outro dado pessoal do
      cliente.
- [ ] Nenhuma tabela ou coluna nova é criada por esta feature.
- [ ] Dado um orçamento gerado para a OS de um cliente com e-mail cadastrado, então o cliente recebe exatamente um
      e-mail de orçamento com a identificação da OS e do orçamento, os serviços com valores, o total e a validade.
- [ ] O e-mail de orçamento não contém instrução para responder com palavra-chave.
- [ ] Depois do e-mail de orçamento, uma decisão `APPROVED` aplicada por qualquer canal existente leva a OS para
      "Execução" e o cliente recebe o e-mail de mudança de status correspondente.
- [ ] Falha no envio do e-mail de orçamento não desfaz a geração do orçamento e é registrada em log sem dados
      pessoais.

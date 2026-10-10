# RF53 — Aprovar ou recusar o orçamento por link no e-mail

| Campo | Valor |
|---|---|
| Número | RF53 (confirmado pelo responsável em 2026-10-10; cadastrado no Jira e no board) |
| Bounded context | `servicelifecycle` (Estimate / Service Order) |
| Responsável | Santiago Silvestre |
| Criado em | 2026-10-10 |
| Origem | Discussão da RF52 em 2026-10-05; enunciado da Fase 2 ("Aprovação de orçamento") |
| Dependências | RF52 (e-mail de orçamento e e-mail de mudança de status); RF40 (regra de decisão do orçamento inteiro); RF15/RF16 (decisão por linha) |
| Spec funcional | `functional-spec.md` (nesta pasta) |
| Situação | **Pausada em 2026-10-10.** A RF não é obrigatória na Fase 2: a aprovação por notificação externa já é atendida por RF40/RF41. Ver `functional-spec.md`. |

## História

**Como** cliente dono de uma Ordem de Serviço,
**quero** aprovar ou recusar o orçamento a partir de links no e-mail que o recebi,
**para** decidir sem precisar ligar para a oficina nem ter login no sistema.

## Contexto

O e-mail de orçamento da RF52 mostra serviços, valores, total e validade, mas o cliente não tem como decidir a
partir dele. Responder o e-mail não chega a lugar nenhum, porque nenhum componente lê a caixa de resposta. O
endpoint do RF40 exige assinatura HMAC de sistema, que o navegador do cliente não produz. Hoje a decisão só entra
por um usuário interno (JWT) ou por um sistema integrador (RF40/RF41).

## Descrição

O e-mail de orçamento passa a trazer dois links, **Aprovar** e **Recusar**. Ao clicar, o cliente vê uma página de
confirmação com o resumo do orçamento. Só ao confirmar a decisão é aplicada, pelas mesmas regras de domínio já
existentes, para o orçamento inteiro. A OS muda de status e o cliente recebe o e-mail de mudança de status da
RF52.

## Regras principais

1. Cada link vale para um único orçamento e uma única decisão, e não pode ser forjado nem alterado sem ficar
   inválido.
2. Abrir o link nunca decide. Só a confirmação explícita na página aplica a decisão, o que protege contra
   antivírus e pré-visualização do leitor de e-mail.
3. A decisão vale para todas as linhas ainda pendentes (tudo ou nada), pela mesma regra do RF40.
4. O link vale enquanto o orçamento estiver `SENT` e dentro da validade (`expiresAt`).
5. Vale a primeira decisão, por qualquer canal. Depois dela, o link mostra "já decidido".
6. As páginas não mostram dados pessoais do cliente (nome, e-mail, telefone, placa, endereço). As páginas de erro
   não revelam se a OS ou o orçamento existem.
7. Nenhum log contém o link completo nem dados pessoais.
8. Os canais existentes (JWT interno e RF40/RF41 por HMAC) continuam sem alteração.

## Critérios de aceite

- [ ] O e-mail de orçamento contém os links Aprovar e Recusar e informa que a decisão vale para o orçamento
      inteiro.
- [ ] Abrir um link válido mostra a confirmação com número da OS, serviços com valores, total e validade, e não
      altera o orçamento nem a OS.
- [ ] Confirmar a aprovação ou a recusa decide todas as linhas pendentes e mostra a página de resultado.
- [ ] Depois da decisão, o cliente recebe o e-mail de mudança de status da RF52, quando houver mudança nominal.
- [ ] Um link de orçamento já decidido, vencido ou inválido mostra a página correspondente, e nada muda.
- [ ] O link de aprovação não pode ser transformado em link de recusa, nem apontado para outro orçamento.
- [ ] Duas confirmações concorrentes resultam em uma única decisão.
- [ ] Os endpoints internos (JWT) e o do RF40 (HMAC) continuam funcionando sem alteração.

## Fora de escopo

Login ou portal do cliente; decisão parcial por linha pelo link; reenvio de e-mail ou novo link para orçamento
vencido; cancelamento manual de link; processamento de respostas por e-mail; segundo fator de confirmação.

# Especificação Funcional: Alterar Prioridade da Service Order

| Campo | Valor |
|---|---|
| Feature | `change-service-order-priority` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-20 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-20 |
| Referências | `docs/Architecture.md` §2.3 (RF09–RF18), §6.1 passo 2, `EPIC2-REVIEW.md` |

## Problema e resultado esperado

A prioridade de uma Service Order já pode ser definida na criação (RF09,
`docs/features/servicelifecycle/service-order-creation/`). Esta feature cobre o caso em que o Service
Advisor precisa **alterar** a prioridade depois que a Service Order já existe — por exemplo, quando o
Customer avisa urgência, ou o workshop reprioriza o atendimento.

Hoje já existe um método de domínio para isso (`ServiceOrder.definePriority(Priority)`), mas ele não é
acionável por ninguém: não há use case, controller nem endpoint. Esta feature expõe essa capacidade.

Ao final da alteração:

- a Service Order passa a refletir a nova prioridade;
- a alteração é visível na consulta da Service Order (`GET /api/service-orders/{id}`);
- nenhum outro dado da Service Order é afetado.

## Atores e cenários

- Um Service Advisor altera a prioridade de uma Service Order já existente, informando o novo valor.
- O sistema aceita a alteração e passa a refletir a nova prioridade em consultas subsequentes.
- O sistema rejeita a alteração se a Service Order não existir ou se o valor informado não for uma
  prioridade válida.

## Regras de negócio

### Valores aceitos

- A prioridade só pode ser um dos valores já existentes no domínio: `LOW`, `NORMAL`, `HIGH`, `URGENT`.
- Não há aprovação, permissão ou papel diferenciado exigido para alterar a prioridade (mesmo padrão de
  ausência de autenticação/autorização do restante do projeto hoje).

### Quando a alteração é permitida

Não há nenhuma regra documentada em `docs/Architecture.md`, `docs/Architecture-Decisions.md` ou no
board Miro (verificado nesta sessão — tabela de requisitos, "5. Detalhes Adicionais dos Aggregates" e
"4. Aggregates — Modelo Atualizado" não mencionam restrição alguma para RF10) restringindo em quais
status a prioridade pode ser alterada. Decisão confirmada por Santiago Silvestre em 2026-08-20:

- A prioridade pode ser alterada em qualquer status **exceto** `COMPLETED` e `DELIVERED` (Service
  Order já finalizada operacionalmente — alterar prioridade nesse ponto não tem efeito prático e pode
  confundir relatórios históricos). Tentar alterar nesses dois status é rejeitado como conflito.

### O que não muda

- A alteração de prioridade não recalcula `statusSnapshot`, não afeta `ServiceExecution`s existentes,
  Estimates ou o `VehicleSnapshot` congelado.
- A alteração não gera notificação a Customer ou Technician (fora do escopo; pode ser proposto como
  feature separada no futuro, seguindo o padrão de
  `docs/features/servicelifecycle/notifications-technician-new-so/`).

## Fora de escopo

- definir a prioridade na criação da Service Order (já coberto por RF09/`service-order-creation`);
- notificação de qualquer ator sobre a mudança de prioridade;
- histórico/auditoria de mudanças de prioridade;
- qualquer efeito automático da prioridade sobre ordenação de fila de trabalho, SLA ou atribuição de
  Technician (a prioridade hoje é só um atributo informativo na Service Order).

## Critérios de aceite

- [ ] A prioridade de uma Service Order existente pode ser alterada para qualquer valor válido de
  `Priority`.
- [ ] A prioridade alterada é refletida em `GET /api/service-orders/{id}`.
- [ ] Tentar alterar a prioridade de uma Service Order inexistente resulta em erro (não encontrado),
  sem criar nada.
- [ ] Informar um valor de prioridade inválido (fora do enum) resulta em erro de validação.
- [ ] Tentar alterar a prioridade de uma Service Order `COMPLETED` ou `DELIVERED` é rejeitado com um
  erro de conflito.

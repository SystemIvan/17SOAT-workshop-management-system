# Especificação Funcional: Recebimento e Reposição de Stock Items

| Campo | Valor |
|---|---|
| Feature | `stock-receiving-and-restocking` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Referências | RF29, Miro e SDDs de Stock & Procurement (links abaixo) |

Referências:

- [RF25–RF30 no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679722227775);
- [Domain Storytelling de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831);
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720);
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027);
- `docs/features/stockprocurement/purchase-order-closing/functional-spec.md`;
- `docs/features/stockprocurement/purchase-order-creation/`;
- `docs/features/stockprocurement/stock-item-reservation/`;
- `docs/features/servicelifecycle/change-service-order-priority/`;
- `docs/features/stockprocurement/README.md`;
- `docs/backlog.md`, especialmente BL-002 e BL-003.

## Estado desta descoberta

Esta especificação trata RF29 junto de RF28 como uma mesma frente de entrega, mas mantém o recebimento como operação de
negócio própria. RF28 termina com uma Purchase Order `CLOSED` e saldo inalterado; RF29 registra a entrada integral uma
única vez, aumenta a disponibilidade e reage às execuções que aguardam materiais.

As decisões aprovadas para o MVP são:

- receber somente a composição integral e imutável de uma Purchase Order `CLOSED`;
- não aceitar quantidades digitadas novamente no recebimento;
- registrar uma entrada rastreável, atômica e idempotente;
- aceitar a entrada histórica de Stock Item desativado sem reativá-lo;
- tentar novamente execuções `AWAITING_ITEMS` por prioridade, sem prometer o material à Service Order que originou a
  compra.

Entrega parcial, divergência, ajuste e devolução exigem regras próprias e permanecem fora deste draft.

## Problema e resultado esperado

Criar ou fechar uma Purchase Order não representa a entrada física de materiais. Sem RF29, a oficina continuaria com
o mesmo saldo indisponível, sem trilha do recebimento e dependeria de retries manuais isolados para cada Service
Execution.

O resultado esperado é que o Stock Manager registre o recebimento integral de uma Purchase Order `CLOSED`. O sistema
cria um Stock Receipt rastreável, aumenta atomicamente `availableQuantity` de todas as linhas e, depois da entrada
confirmada, tenta atender novamente as Service Executions em `AWAITING_ITEMS`, começando pelas prioridades mais
altas.

## Linguagem ubíqua

### Stock Receipt

Stock Receipt é o registro imutável de uma entrada física originada por uma Purchase Order. Possui identidade própria,
referência à ordem, responsável autenticado, instante e todas as linhas recebidas.

Existe no máximo um Stock Receipt para cada Purchase Order no MVP. A repetição da mesma operação retorna o registro
existente e não aumenta o saldo novamente.

### Stock Movement de entrada

Cada linha do Stock Receipt produz uma movimentação de entrada rastreável para o Stock Item. A movimentação identifica,
no mínimo, item, quantidade positiva, origem `PURCHASE_ORDER_RECEIPT`, Purchase Order, Stock Receipt, instante e
responsável.

A quantidade disponível não é alterada por CRUD do Stock Item. O saldo muda como efeito dessa movimentação de negócio.

### Reposição

Reposição é o aumento de `availableQuantity` causado pela entrada recebida. Ela não cria compromisso com uma Service
Order específica. Materiais recebidos entram no saldo comum e podem ser reservados para qualquer execução elegível,
conforme a prioridade operacional.

## Escopo funcional desta entrega

### Registrar o recebimento integral

O Stock Manager solicita o recebimento pelo ID local de uma Purchase Order `CLOSED`. O sistema deriva todas as linhas e
quantidades da ordem; não aceita itens ou quantidades enviados livremente pelo cliente.

Em uma única operação de negócio, o sistema:

1. valida que a ordem existe, está `CLOSED` e ainda não possui Stock Receipt;
2. cria o Stock Receipt com todas as linhas da ordem;
3. registra uma movimentação de entrada para cada linha;
4. aumenta `availableQuantity` de cada Stock Item pela quantidade correspondente;
5. confirma o recebimento somente se todas as linhas puderem ser registradas.

Falha em qualquer linha desfaz todo o recebimento e nenhum saldo é alterado. Quantidades inteiras permanecem a unidade
canônica do MVP; soma acima do limite suportado é rejeitada sem overflow ou saldo parcial.

### Receber item desativado

Um Stock Item pode ter sido desativado depois da criação da Purchase Order. Como a compra já foi confirmada e a entrega
precisa permanecer auditável, o recebimento integral ainda registra a entrada e aumenta seu saldo.

O item continua inativo: RF29 não o reativa e novas reservas continuam impedidas enquanto ele não puder ser usado. A
eventual reativação de Stock Item exige feature própria.

### Consultar o recebimento e as movimentações

O Stock Manager pode consultar o Stock Receipt pela Purchase Order e verificar:

- ID do recebimento e da Purchase Order;
- linhas e quantidades recebidas;
- instante e responsável;
- movimentações de entrada correspondentes.

A visão da Purchase Order permite distinguir uma ordem `CLOSED` ainda não recebida de outra que já possui Stock Receipt.
Um histórico geral de ajustes, saídas administrativas ou transferências não pertence a esta feature.

### Reavaliar Service Executions em espera

Depois que o Stock Receipt e todos os novos saldos forem confirmados, o sistema identifica Service Executions em
`AWAITING_ITEMS` cujos Stock Requirements incluem ao menos um dos itens recebidos e solicita novamente a reserva
integral de cada execução.

As tentativas obedecem às regras já aprovadas de Stock Reservation:

- cada Service Execution é uma fronteira independente de tudo-ou-nada;
- todos os seus requirements congelados são reavaliados, inclusive itens não presentes no recebimento atual;
- sucesso cria ou recupera a Stock Reservation e leva a execução a `READY`;
- insuficiência preserva `AWAITING_ITEMS` sem saldo parcial;
- uma falha em determinada execução não desfaz o recebimento nem impede tentativas posteriores elegíveis;
- notificações já previstas para reserva bem-sucedida mantêm seu comportamento e sua idempotência.

### Prioridade das novas tentativas

Quando várias execuções forem elegíveis, a ordem funcional é a prioridade atual da Service Order:

1. `URGENT`;
2. `HIGH`;
3. `NORMAL`;
4. `LOW`.

As execuções são processadas uma por vez nessa ordem, observando o saldo confirmado após cada tentativa. Assim, uma
reserva de maior prioridade pode consumir unidades e fazer uma execução posterior continuar em `AWAITING_ITEMS`.

Entre execuções com a mesma prioridade não existe preferência de negócio no MVP. A especificação técnica deverá adotar
um desempate estável para tornar testes e operação reproduzíveis, sem apresentar esse critério como SLA ou promessa de
antiguidade. Alterar a prioridade antes do retry altera legitimamente a ordem observada.

O vínculo histórico entre Purchase Demand e Purchase Order não concede preferência. Uma compra originada por uma
Service Execution pode atender outra de maior prioridade.

## Atores e cenários

- O Stock Manager fecha uma Purchase Order em RF28 e registra imediatamente seu recebimento integral.
- O recebimento aumenta os saldos de todas as linhas e preserva uma trilha por item.
- O comando é repetido depois de timeout e retorna o Stock Receipt existente sem nova entrada.
- Duas chamadas concorrentes tentam receber a mesma ordem e somente uma altera saldos.
- Um item foi desativado depois da compra; a entrada é registrada, mas o item continua inativo.
- Várias execuções aguardam o mesmo item; o sistema tenta primeiro as de maior prioridade.
- Uma execução de maior prioridade reserva o saldo; outra continua em `AWAITING_ITEMS` sem reserva parcial.
- Uma execução depende também de item não recebido e continua aguardando, sem desfazer a reposição.
- Uma Purchase Order ad hoc repõe o saldo e pode atender execuções sem vínculo com a ordem.

## Regras de negócio

### Elegibilidade e integridade

- somente Purchase Order `CLOSED` pode originar um Stock Receipt;
- cada Purchase Order origina no máximo um Stock Receipt;
- o recebimento sempre usa todas as linhas e quantidades imutáveis da ordem;
- Purchase Order sem fechamento, inexistente ou rejeitada não altera saldo;
- toda movimentação de entrada tem identidade e quantidade inteira positiva;
- saldos e movimentações de todas as linhas são confirmados atomicamente;
- `availableQuantity` nunca pode sofrer overflow nem ficar inconsistente com as movimentações confirmadas;
- receber item inativo não o reativa e não altera seus snapshots históricos.

### Idempotência e concorrência

- repetir o recebimento retorna o Stock Receipt já existente;
- repetição não cria outra movimentação, não substitui autoria ou instante e não soma saldo novamente;
- chamadas concorrentes convergem para um único Stock Receipt por Purchase Order;
- locks e ordem técnica não podem permitir perda de atualização diante de reserva concorrente;
- retries de Service Execution começam somente depois do commit do recebimento;
- falha nos retries não reverte nem duplica o recebimento confirmado.

### Autorização e exposição

- somente Stock Manager pode registrar ou consultar o recebimento operacional;
- enquanto Stock Manager for representado tecnicamente por `MANAGER`, `MANAGER` e `ADMIN` preservam o acesso HTTP;
- responsável e instante derivam do contexto confiável da aplicação;
- o cliente não envia saldo final, linhas, quantidades, identidade do responsável ou estado da ordem;
- os contratos não expõem Customer, Vehicle, Technician, preço da Estimate ou detalhes internos de persistência.

## Falhas esperadas

O fluxo deve distinguir pelo menos:

- Purchase Order inexistente;
- Purchase Order ainda não `CLOSED`;
- inconsistência entre ordem e Stock Item referenciado;
- overflow de quantidade;
- conflito concorrente não reconciliável como repetição idempotente;
- ausência de autenticação ou papel permitido;
- falha técnica que exige rollback integral do recebimento.

Uma execução ainda sem saldo suficiente é resultado esperado de retry, não falha do Stock Receipt. As respostas não
expõem SQL, stack trace, credenciais, dados pessoais ou tipos internos. Códigos HTTP e códigos de erro estáveis serão
definidos na especificação técnica.

## Relação com RF28 e RF30

- RF28 fornece a precondição `CLOSED`, mas não aumenta saldo;
- RF29 é dona do Stock Receipt, das movimentações de entrada, da reposição e da reação às execuções;
- RF30 observa o novo `availableQuantity` depois do recebimento para encerrar ou recalcular uma ocorrência de baixo
  estoque;
- a falha de RF30 não pode desfazer um recebimento confirmado;
- Purchase Order e Stock Receipt permanecem conceitos distintos mesmo que a interface operacional execute os dois
  passos em sequência.

## Fora de escopo

- fechar Purchase Order de RF28;
- configurar ou detectar nível baixo de RF30;
- recebimento parcial, múltiplas entregas ou backorder;
- divergência de item ou quantidade, avaria, substituição e devolução ao fornecedor;
- retirada administrativa, perda, ajuste, inventário físico ou transferência;
- reativar Stock Item desativado;
- lote, série, validade, localização, nota fiscal, custo, imposto, frete ou pagamento;
- reservar antecipadamente material futuro para a origem da Purchase Demand;
- alterar a regra de tudo-ou-nada ou liberar Stock Reservation;
- garantir preferência entre execuções de mesma prioridade;
- processamento assíncrono durável, retentativa agendada ou operação multiestoque.

## Critérios de aceite

- [ ] O Stock Manager registra o recebimento de uma Purchase Order `CLOSED` ainda não recebida.
- [ ] O sistema deriva todas as linhas e quantidades da ordem e não aceita composição arbitrária no comando.
- [ ] Um único Stock Receipt imutável e uma movimentação por linha preservam a rastreabilidade da entrada.
- [ ] Todas as quantidades são adicionadas atomicamente a `availableQuantity` ou nenhuma alteração é confirmada.
- [ ] Repetir ou concorrer pelo mesmo recebimento não duplica Receipt, movimentação ou saldo.
- [ ] Purchase Order inexistente, não fechada ou inconsistente não altera nenhum Stock Item.
- [ ] Um Stock Item desativado pode receber a entrada histórica, permanece inativo e não participa de nova reserva.
- [ ] A Purchase Order e o Stock Receipt permanecem consultáveis depois da reposição.
- [ ] Depois do recebimento, somente execuções `AWAITING_ITEMS` relacionadas a itens recebidos são reavaliadas.
- [ ] Cada retry usa todos os requirements congelados da execução e preserva a atomicidade já aprovada.
- [ ] Execuções são tentadas na ordem `URGENT`, `HIGH`, `NORMAL`, `LOW`.
- [ ] Entre prioridades iguais existe desempate técnico estável, sem preferência funcional prometida.
- [ ] Uma execução sem saldo suficiente continua em `AWAITING_ITEMS` sem desfazer o recebimento ou bloquear as demais.
- [ ] Material recebido pode atender qualquer execução elegível e não fica prometido à origem da compra.
- [ ] Falha ou repetição do retry não duplica reserva nem notificação.
- [ ] A reposição permite que RF30 reavalie baixo estoque sem acoplar RF29 à política de mínimo.
- [ ] Somente `MANAGER` e `ADMIN` acessam as operações HTTP enquanto representarem Stock Manager.
- [ ] Falhas não produzem saldo parcial nem expõem informações internas ou sensíveis.

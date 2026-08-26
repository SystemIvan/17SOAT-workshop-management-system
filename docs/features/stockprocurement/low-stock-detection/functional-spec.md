# Especificação Funcional: Identificação de Stock Items em Nível Baixo

| Campo | Valor |
|---|---|
| Feature | `low-stock-detection` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-25 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-25 |
| Referências | RF30, Miro e SDDs de Stock & Procurement (links abaixo) |

Referências:

- [RF25–RF30 no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679722227775);
- [Domain Storytelling de Stock no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725831);
- [Pivotal Events no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678817744720);
- [Modelo tático atualizado no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764680870224027);
- `docs/features/stockprocurement/stock-domain-foundation/`;
- `docs/features/stockprocurement/stock-item-reservation/`;
- `docs/features/stockprocurement/purchase-order-creation/`;
- `docs/features/stockprocurement/stock-receiving-and-restocking/functional-spec.md`;
- `docs/features/stockprocurement/README.md`;
- `docs/backlog.md`, especialmente BL-002.

## Estado desta descoberta

RF30 permanece uma feature independente de RF28/RF29 porque introduz uma política configurável de inventário e um ciclo
próprio de ocorrência de baixo estoque. Ela usa o contrato público já preparado por RF27 para publicar Purchase Demands
`LOW_STOCK`, mas não conhece a montagem ou o envio de Purchase Orders.

As decisões aprovadas para o MVP são:

- a política é opcional por Stock Item e possui `minimumQuantity` e `targetQuantity` distintos;
- baixo estoque significa `availableQuantity < minimumQuantity`;
- a quantidade sugerida é `targetQuantity - availableQuantity`;
- a condição é avaliada quando a política muda e depois de toda mudança confirmada de disponibilidade;
- uma mesma ocorrência não duplica demanda ou notificação;
- nenhuma Purchase Order é criada automaticamente.

Essas decisões foram aprovadas explicitamente em 2026-08-25.

## Problema e resultado esperado

Hoje a oficina só reage quando um reparo encontra saldo insuficiente. Um item pode cair a um nível operacionalmente
baixo sem bloquear uma execução naquele momento e permanecer assim até uma necessidade urgente aparecer.

O resultado esperado é que o Stock Manager configure um limite e um alvo para cada Stock Item que precisa de reposição
preventiva. Quando a disponibilidade ficar abaixo do limite, o sistema registra uma única ocorrência rastreável,
publica ou atualiza uma Purchase Demand `LOW_STOCK` com a quantidade necessária para alcançar o alvo e sinaliza o Stock
Manager, sem comprar automaticamente.

## Linguagem ubíqua

### Low Stock Policy

Low Stock Policy é a política opcional de reposição preventiva de um Stock Item ativo. Ela contém:

- `minimumQuantity`: limite inteiro não negativo abaixo do qual o item é considerado em nível baixo;
- `targetQuantity`: quantidade inteira positiva que a reposição sugerida pretende alcançar.

Para uma política habilitada, `targetQuantity` deve ser estritamente maior que `minimumQuantity`. Ausência de política
significa que o item não participa da detecção; não significa mínimo zero.

### Ocorrência de baixo estoque

Uma ocorrência começa quando um Stock Item ativo com política habilitada satisfaz
`availableQuantity < minimumQuantity`. Ela possui ID estável enquanto representar a mesma condição contínua.

A ocorrência deixa de estar aberta quando:

- `availableQuantity` volta a ser maior ou igual a `minimumQuantity`;
- a política é desabilitada;
- o Stock Item é desativado;
- uma reposição referente ao ciclo já comprado é recebida e exige uma nova avaliação.

Se, depois de um recebimento, o item continuar abaixo do mínimo, o ciclo anterior termina e uma nova ocorrência pode ser
aberta com novo ID e nova sugestão. Isso permite uma nova decisão de compra sem duplicar demandas enquanto a entrega
anterior ainda estava pendente.

### Quantidade sugerida

Durante uma ocorrência aberta:

`suggestedQuantity = targetQuantity - availableQuantity`

Exemplo: mínimo 5, alvo 12 e disponibilidade 4 produzem sugestão 8. A sugestão é sempre positiva porque o alvo é maior
que o mínimo e a condição exige disponibilidade abaixo do mínimo.

A sugestão orienta o Stock Manager, mas RF27 continua permitindo comprar acima dela e não cria ordem automaticamente.

## Escopo funcional desta entrega

### Configurar a política

O Stock Manager pode habilitar ou alterar a Low Stock Policy de um Stock Item ativo informando mínimo e alvo válidos.
Também pode desabilitá-la explicitamente.

A configuração:

- não altera `availableQuantity`;
- não muda SKU, tipo, nome, preço ou estado ativo;
- passa a aparecer nas consultas operacionais do Stock Item;
- avalia imediatamente a disponibilidade atual;
- mantém compatibilidade com itens existentes, que começam sem política até configuração explícita.

Se uma alteração mantiver a mesma ocorrência aberta, o sistema recalcula a sugestão e atualiza a mesma demanda aberta.
Se a nova política fizer o item deixar de estar baixo, a ocorrência é encerrada. Desabilitar a política impede novas
detecções, mas não apaga histórico nem cancela uma Purchase Order já criada.

### Detectar depois de mudança de disponibilidade

A condição é reavaliada depois de toda mudança confirmada de `availableQuantity`, inclusive:

- criação de Stock Item que já possua política configurada;
- reserva de Stock Items, que reduz disponibilidade;
- recebimento e reposição de RF29, que aumentam disponibilidade;
- futuras movimentações de inventário que adotem o mesmo contrato de reavaliação.

Consumir uma Stock Reservation não reduz novamente `availableQuantity` e, portanto, não abre outra ocorrência pelo
mesmo efeito. Uma operação que sofre rollback não gera ocorrência, demanda ou notificação confirmada.

O MVP não depende de varredura periódica: a avaliação é orientada pelas mudanças de política e disponibilidade. Uma
reconciliação administrativa ou agendada poderá ser especificada futuramente.

### Publicar a Purchase Demand `LOW_STOCK`

Ao abrir uma ocorrência, RF30 fornece a RF27:

- ID estável da ocorrência;
- `stockItemId` canônico;
- `availableQuantity` observada;
- `suggestedQuantity` calculada.

Enquanto a ocorrência permanecer aberta:

- avaliações repetidas com os mesmos dados não criam outra demanda;
- redução adicional de saldo atualiza observação e sugestão da mesma demanda `OPEN`;
- alteração de alvo atualiza a sugestão da mesma demanda `OPEN`;
- demanda já `ORDERED` não é reaberta nem duplicada enquanto o ciclo aguarda recebimento;
- a composição de qualquer Purchase Order existente permanece imutável.

Quando uma ocorrência termina antes de ser incluída em uma ordem, sua demanda `OPEN` deixa de ser selecionável sem ser
apagada. Demanda `ORDERED` permanece histórica e é reconciliada pelo recebimento correspondente.

### Expor a condição operacional

O Stock Manager pode consultar Stock Items e distinguir:

- item sem política;
- item com política e saldo normal;
- item com ocorrência aberta de baixo estoque;
- mínimo, alvo, disponibilidade atual e sugestão vigente.

Uma listagem pode ser filtrada pela condição de baixo estoque. O resultado é calculado a partir da política e da
disponibilidade confirmada e não altera saldo nem cria demanda durante uma simples leitura.

### Sinalizar o Stock Manager

Uma nova ocorrência produz uma única sinalização operacional ao Stock Manager com item, disponibilidade observada,
mínimo, alvo e sugestão. Reavaliações da mesma ocorrência não multiplicam sinalizações.

Falha de entrega da sinalização não desfaz a ocorrência nem a Purchase Demand. O mecanismo técnico seguirá a regra de
porta outbound pertencente a Stock & Procurement; não será criado bounded context genérico de Notifications.

## Atores e cenários

- O Stock Manager configura mínimo 5 e alvo 12 para um Stock Item com disponibilidade 10; nenhuma ocorrência é aberta.
- Uma reserva reduz a disponibilidade de 6 para 4; o sistema abre uma ocorrência e sugere comprar 8.
- Outra reserva reduz o saldo para 2; a mesma ocorrência e demanda são atualizadas para sugestão 10.
- Uma reavaliação repetida não duplica demanda nem sinalização.
- RF27 inclui a demanda em uma Purchase Order; enquanto a entrega não chega, o saldo baixo não cria nova demanda.
- RF29 repõe o item até 12; a ocorrência termina e nenhuma nova demanda é criada.
- RF29 repõe somente até 4 em um cenário legado ou divergente; o ciclo anterior termina e uma nova ocorrência é aberta.
- O Stock Manager reduz o mínimo para 2; uma ocorrência aberta com saldo 4 é encerrada.
- O Stock Manager desabilita a política ou desativa o item; novas detecções param sem apagar o histórico.
- Um item sem política pode chegar a zero sem produzir demanda `LOW_STOCK`.

## Regras de negócio

### Política e validação

- apenas Stock Item ativo aceita habilitação ou alteração da política;
- `minimumQuantity` é inteiro e maior ou igual a zero;
- `targetQuantity` é inteiro, positivo e maior que `minimumQuantity`;
- mínimo e alvo são informados em conjunto; valor parcial é inválido;
- desabilitação é explícita e preserva histórico;
- configuração não altera saldo nem cria Purchase Order;
- itens legados ficam sem política até decisão do Stock Manager.

### Detecção e ciclo

- somente item ativo com política habilitada participa da detecção;
- baixo estoque usa comparação estrita: `availableQuantity < minimumQuantity`;
- atingir exatamente o mínimo não é baixo estoque no MVP;
- existe no máximo uma ocorrência aberta por Stock Item;
- o ID da ocorrência permanece estável em reavaliações da mesma condição;
- cruzar de normal para baixo abre ocorrência; cruzar de baixo para normal encerra;
- receber o ciclo já comprado encerra sua ocorrência e permite nova avaliação com novo ID se o saldo continuar baixo;
- concorrência não pode abrir duas ocorrências ou demandas equivalentes;
- leitura pura não produz efeitos colaterais.

### Integração e independência

- RF30 publica necessidade, mas não monta, envia, fecha ou recebe Purchase Order;
- RF27 é dona da Purchase Demand e garante idempotência pelo ID da ocorrência;
- RF29 informa a mudança de disponibilidade sem conhecer mínimo, alvo ou demanda;
- Stock Reservation mantém sua atomicidade e não depende do sucesso de uma notificação;
- falha ao registrar a demanda não pode deixar uma ocorrência silenciosamente considerada processada; o mecanismo
  técnico de consistência será definido na especificação técnica;
- nenhuma demanda `LOW_STOCK` referencia Service Order ou Service Execution.

### Autorização e exposição

- somente Stock Manager pode configurar política e consultar a visão operacional completa;
- enquanto Stock Manager for representado tecnicamente por `MANAGER`, `MANAGER` e `ADMIN` preservam o acesso HTTP;
- mínimo e alvo são dados operacionais, não dados pessoais;
- o cliente não envia occurrence ID, sugestão calculada, saldo observado, autor ou instante como fonte confiável;
- respostas e sinalizações não incluem Customer, Vehicle, Estimate, Technician ou credenciais.

## Falhas esperadas

O fluxo deve distinguir pelo menos:

- Stock Item inexistente;
- tentativa de configurar item inativo;
- mínimo ou alvo ausente, negativo, não inteiro ou fora da relação obrigatória;
- concorrência na abertura ou atualização da ocorrência;
- falha de integração ao publicar a Purchase Demand;
- ausência de autenticação ou papel permitido.

Falha de sinalização é tratada como efeito outbound e não desfaz o estado de negócio confirmado. Falhas não expõem SQL,
stack trace, dados pessoais ou detalhes internos. Códigos HTTP e códigos de erro estáveis serão definidos na
especificação técnica.

## Fora de escopo

- criar ou enviar Purchase Order automaticamente;
- escolher fornecedor, cotar, calcular preço ou prever prazo de reposição;
- fechar ou receber Purchase Order de RF28/RF29;
- política global por tipo, categoria, fornecedor ou múltiplos estoques;
- quantidade fracionária, unidade de medida, lote, validade ou localização;
- previsão de consumo, sazonalidade, média móvel ou sugestão baseada em histórico;
- varredura agendada, reconciliação manual ou dashboard analítico;
- bloqueio de reserva por atingir o mínimo: saldo disponível continua reservável até zero;
- editar Purchase Demand ou sugestão manualmente;
- apagar histórico de ocorrências, demandas ou movimentações;
- criar bounded context genérico de Notifications.

## Critérios de aceite

- [ ] O Stock Manager habilita uma política válida com mínimo e alvo para um Stock Item ativo.
- [ ] Mínimo negativo, alvo não positivo, alvo menor ou igual ao mínimo ou configuração parcial são rejeitados.
- [ ] Itens existentes continuam válidos e sem detecção até receberem política explícita.
- [ ] `availableQuantity < minimumQuantity` abre uma ocorrência; igualdade com o mínimo não abre.
- [ ] A sugestão corresponde a `targetQuantity - availableQuantity` e é sempre positiva.
- [ ] Configurar uma política sobre saldo já baixo avalia e abre a ocorrência imediatamente.
- [ ] Reserva confirmada reavalia o item depois da redução de disponibilidade.
- [ ] Recebimento confirmado reavalia o item depois do aumento de disponibilidade.
- [ ] Reavaliações da mesma condição preservam o occurrence ID e não duplicam Purchase Demand.
- [ ] Redução adicional de saldo ou mudança de alvo atualiza a mesma demanda `OPEN`.
- [ ] Demanda `ORDERED` não é reaberta nem duplicada enquanto aguarda o recebimento do ciclo.
- [ ] Voltar a saldo normal, desabilitar a política ou desativar o item encerra a ocorrência aberta.
- [ ] Se o item continuar baixo depois do recebimento do ciclo anterior, nasce nova ocorrência com novo ID.
- [ ] Uma ocorrência que termina antes da compra torna sua demanda `OPEN` não selecionável sem apagar histórico.
- [ ] Uma nova ocorrência gera uma única sinalização ao Stock Manager; falha da sinalização não desfaz a demanda.
- [ ] A consulta distingue política ausente, saldo normal e ocorrência de baixo estoque sem efeito colateral.
- [ ] RF30 publica demanda `LOW_STOCK` sem referência a Service Order e nunca cria Purchase Order automaticamente.
- [ ] Concorrência não produz duas ocorrências ou demandas equivalentes para o mesmo item.
- [ ] Somente `MANAGER` e `ADMIN` acessam as operações HTTP enquanto representarem Stock Manager.
- [ ] Falhas não expõem informações internas, credenciais ou dados pessoais.

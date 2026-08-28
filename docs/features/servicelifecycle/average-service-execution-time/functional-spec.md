# Especificação Funcional: Monitorar tempo médio de execução dos serviços

| Campo | Valor |
|---|---|
| Feature | `average-service-execution-time` |
| Status | Approved |
| Responsável | Ivan Gomes |
| Atualizado em | 2026-08-28 |
| Aprovado por | Ivan Gomes |
| Aprovado em | 2026-08-28 |
| Referências | Requisito oficial; ADR-006; AD-019; Architecture; `start-execution`; `complete-execution` |

## Problema e resultado esperado

A gestão da oficina precisa acompanhar quanto tempo, em média, os serviços efetivamente executados levam para
serem concluídos. Hoje o sistema conhece as transições de início e conclusão de uma `ServiceExecution`, mas não
preserva os instantes dessas transições e não oferece uma consulta administrativa da métrica.

O resultado esperado é permitir que um ator administrativo autorizado consulte, para um período, a duração média
transcorrida das `ServiceExecution`s concluídas, tanto de forma global quanto agrupada por `catalogServiceId`. Cada
resultado apresenta a média em horas e a quantidade de execuções que compõem a amostra. Assim, a gestão interpreta
o indicador sem confundir ausência de dados com duração igual a zero.

Esta métrica representa o tempo transcorrido de execução do serviço na oficina. Ela não representa o tempo total da
`ServiceOrder` percebido pelo Customer nem afirma medir somente tempo de trabalho ativo.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Manager / Admin | Consulta a média global das execuções concluídas em um período |
| Manager / Admin | Consulta as médias por `catalogServiceId` para comparar tipos de serviço |

### Cenário principal — consulta global

1. Existem `ServiceExecution`s que iniciaram, foram concluídas e possuem os dois instantes temporais válidos.
2. O ator administrativo consulta a métrica para um período.
3. O sistema considera as execuções cujo `completedAt` pertence ao período solicitado.
4. O sistema retorna a duração média em horas e a quantidade de execuções consideradas.

### Cenário alternativo — consulta agrupada por serviço do catálogo

1. O ator administrativo consulta a métrica agrupada por `catalogServiceId` para um período.
2. O sistema separa as execuções elegíveis por `catalogServiceId`.
3. Para cada serviço com amostras elegíveis, o sistema retorna a média em horas e a quantidade de amostras.

### Cenário alternativo — período sem amostras

1. Nenhuma execução elegível possui `completedAt` dentro do período solicitado.
2. O sistema informa quantidade de amostras igual a zero e ausência de duração média.
3. O sistema não substitui a média ausente por zero.

## Regras de negócio

### Formação da amostra

- Cada `ServiceExecution` concluída representa uma amostra independente, inclusive quando se originar de um reparo
  adicional.
- `startedAt` registra o instante UTC da primeira e única transição válida de `READY` para `IN_PROGRESS`.
- `completedAt` registra o instante UTC da primeira e única transição válida de `IN_PROGRESS` para `COMPLETED`.
- Os instantes são fatos imutáveis: consultas e mudanças posteriores na `ServiceOrder` não os recalculam.
- Somente uma `ServiceExecution` no estado `COMPLETED`, com `startedAt` e `completedAt` válidos, participa da métrica.
- Execuções rejeitadas, ainda em andamento ou em qualquer estado anterior a `COMPLETED` não participam da métrica.
- Registros anteriores à introdução dos instantes não participam da métrica sem os dois fatos temporais
  confiáveis e não recebem valores estimados.

### Duração medida

- A duração de uma amostra é o tempo transcorrido entre `startedAt` e `completedAt`.
- Diagnóstico, espera pela decisão da Estimate e espera por materiais não entram no cálculo porque antecedem
  `startedAt`.
- O MVP não possui status ou evento de pausa e retomada. O cálculo usa somente `startedAt` e `completedAt`,
  registrados pelas mudanças de status, sem receber ou processar intervalos de pausa.
- A duração média é apresentada em horas e pode possuir parte fracionária. Por exemplo, 1 hora e 30 minutos
  correspondem a `1.5` horas na métrica.
- Segundos e milissegundos não são unidades de apresentação da métrica administrativa.
- A precisão e o arredondamento da representação em horas serão definidos na especificação técnica sem mudar a
  unidade funcional estabelecida neste documento.

### Período e agrupamento

- O período é aplicado sobre `completedAt`: o início é inclusivo e o fim é exclusivo.
- A consulta oferece o resultado global do período e resultados agrupados por `catalogServiceId`.
- Cada resultado apresenta a quantidade de amostras junto da duração média.
- Uma duração média igual a zero somente existe quando há ao menos uma amostra de duração zero. Quantidade de
  amostras igual a zero significa que a média está ausente.
- Alterações posteriores no nome, preço ou estado do serviço no catálogo não mudam o agrupamento histórico. O
  cálculo utiliza o `catalogServiceId` preservado na `ServiceExecution`.

### Acesso

- A métrica é uma consulta administrativa e não altera `ServiceOrder`, `ServiceExecution` ou Service Catalog.
- O acesso é restrito aos papéis administrativos `MANAGER` e `ADMIN`.
- Chamadas sem autenticação ou realizadas por papel não autorizado são rejeitadas conforme o contrato de segurança
  comum da API, sem exposição de dados operacionais.

## Fora de escopo

- Calcular o lead time completo da `ServiceOrder`, desde o recebimento até conclusão ou entrega.
- Modelar pausa e retomada, receber intervalos de pausa ou ajustar a duração com base neles.
- Inferir ou preencher retroativamente `startedAt` ou `completedAt` para registros históricos.
- Incluir execuções rejeitadas, incompletas ou sem os dois instantes confiáveis.
- Calcular mediana, percentis, metas, SLA, tendência, previsão ou comparação entre períodos.
- Filtrar ou agrupar por Technician, Customer, Vehicle, `ServiceOrder` ou qualquer dimensão diferente de
  `catalogServiceId`.
- Criar dashboard, exportação de relatório ou notificações baseadas na métrica.
- Criar um bounded context de Analytics, histórico append-only de transições ou tabela de médias pré-calculadas.
- Usar Micrometer, Prometheus ou logs como fonte de verdade da métrica administrativa.
- Definir formato HTTP, precisão, arredondamento, persistência, índices ou estratégia de agregação. Esses itens
  pertencem à especificação técnica após a aprovação desta especificação funcional.

## Critérios de aceite

- [x] Iniciar uma `ServiceExecution` pela transição válida para `IN_PROGRESS` registra seu `startedAt` em UTC uma
      única vez, sem considerar diagnóstico, aprovação ou espera por materiais.
- [x] Concluir uma `ServiceExecution` pela transição válida para `COMPLETED` registra seu `completedAt` em UTC uma
      única vez, sem alterar o `startedAt` previamente registrado.
- [x] A consulta global considera somente execuções `COMPLETED` com os dois instantes válidos e retorna a quantidade
      de amostras e a duração média apresentada em horas.
- [x] A consulta agrupada retorna, por `catalogServiceId`, a quantidade de amostras e a duração média apresentada em
      horas para o mesmo período consultado.
- [x] Uma duração fracionária é apresentada em horas, sem expor segundos ou milissegundos como unidade da métrica.
- [x] Uma execução no início do período é incluída, e uma execução no fim do período é excluída, conforme seu
      `completedAt`.
- [x] Execuções rejeitadas, não concluídas, sem `startedAt` ou sem `completedAt` não alteram a média nem a
      quantidade de amostras.
- [x] O cálculo utiliza somente os instantes das mudanças para `IN_PROGRESS` e `COMPLETED`, sem exigir ou processar
      dados de pausa.
- [x] Um período sem amostras retorna quantidade igual a zero e média ausente, distinguindo esse resultado de uma
      média igual a zero calculada a partir de uma ou mais amostras.
- [x] Registros históricos sem timestamps confiáveis permanecem fora da métrica e não recebem backfill inferido.
- [x] Apenas `MANAGER` e `ADMIN` autenticados podem consultar a métrica; chamadas não autenticadas ou sem o papel
      necessário são rejeitadas sem divulgar os resultados.

## Registro de aprovação

Ivan Gomes ratificou a ADR-006 e aprovou esta especificação funcional em 2026-08-28. As permissões para `MANAGER` e
`ADMIN` e a disponibilidade dos resultados global e agrupado pelo mesmo período também foram confirmadas. A
especificação técnica decidirá se os resultados serão entregues juntos ou por modos de consulta distintos.

# ADR 006: Semântica e estratégia do tempo médio de execução dos serviços

**Status:** Accepted

**Date:** 2026-08-25

**Deciders:** Ivan Gomes

**Affected By:** `servicelifecycle` (`ServiceExecution`) e consulta administrativa de desempenho

---

## Context

O enunciado oficial do Tech Challenge exige que a oficina possa monitorar o tempo médio de execução dos serviços. O
modelo atual não registra os instantes de início e conclusão de uma `ServiceExecution`, não define a semântica da média
e não oferece uma consulta administrativa para essa informação.

O fluxo atual distingue o trabalho executado na oficina das etapas anteriores: uma `ServiceExecution` pode aguardar
aprovação e materiais antes de transicionar de `READY` para `IN_PROGRESS`, e só depois transiciona para `COMPLETED`.
Por isso, medir a execução individual e medir o ciclo completo da `ServiceOrder` produzem indicadores de negócio
diferentes.

O sistema é um monólito modular e não possui um bounded context de Analytics. A necessidade atual não justifica criar
um novo módulo, uma plataforma de eventos ou uma stack de observabilidade apenas para satisfazer essa métrica. A solução
do MVP deve permanecer dentro de `servicelifecycle`, preservar os fatos temporais necessários e permitir evolução do
mecanismo de leitura sem alterar a definição da métrica.

Esta ADR formaliza a opção recomendada no registro AD-019 de `docs/Architecture-Decisions.md`. A opção foi selecionada
para proposta em 2026-08-25 e ratificada por Ivan Gomes em 2026-08-28.

## Problem Statement

A decisão precisa definir:

- qual entidade representa uma amostra da métrica;
- quais instantes delimitam a duração;
- quais estados e tempos de espera entram no cálculo;
- como período e agrupamento são determinados;
- qual mecanismo fornece a informação no MVP;
- como preservar uma evolução futura sem antecipar infraestrutura desnecessária.

Para ser interpretável, qualquer média também precisa informar a quantidade de amostras. Dados históricos sem
instantes confiáveis não podem ser reconstruídos por inferência.

## Considered Options

### Semântica da métrica

#### Option A: Tempo transcorrido por `ServiceExecution` concluída ✅ SELECIONADO

Cada `ServiceExecution` concluída representa uma amostra. Sua duração é o intervalo entre o instante em que transiciona
de `READY` para `IN_PROGRESS` (`startedAt`) e o instante em que transiciona de `IN_PROGRESS` para `COMPLETED`
(`completedAt`).

Vantagens:

- corresponde literalmente ao tempo de execução de um serviço;
- não mistura execução com diagnóstico, aprovação ou espera por materiais;
- permite comparar serviços do mesmo catálogo;
- usa transições que já existem no agregado.

Desvantagens:

- não representa o tempo total percebido pelo Customer;
- o MVP não modela pausa ou retomada e não recebe intervalos de pausa no cálculo;
- execuções anteriores à introdução dos timestamps não compõem a amostra.

#### Option B: Lead time completo da `ServiceOrder`

A duração seria calculada desde o recebimento da `ServiceOrder` até sua conclusão ou entrega.

Vantagens:

- representa melhor o tempo total percebido pelo Customer;
- inclui gargalos de aprovação, materiais e operação.

Desvantagens:

- mistura causas e responsabilidades diferentes em uma única média;
- não permite atribuir o resultado diretamente a um tipo de serviço;
- responde a uma pergunta distinta do requisito de tempo de execução.

#### Option C: Entregar as duas métricas no MVP

Seriam expostos tanto o tempo por `ServiceExecution` quanto o lead time da `ServiceOrder`.

Vantagens:

- oferece visão operacional e visão da experiência do Customer.

Desvantagens:

- amplia timestamps, contratos, documentação e testes sem necessidade para o requisito atual;
- aumenta o risco de apresentar indicadores diferentes sob nomes ambíguos.

### Estratégia de entrega

#### Option 1: Fatos temporais persistidos e agregação sob demanda ✅ SELECIONADO

Os instantes `startedAt` e `completedAt` são fatos do domínio de `ServiceExecution`. Uma capacidade de leitura própria
de `servicelifecycle` calcula a média no banco de dados e a expõe por consulta administrativa, sem carregar os agregados
completos para realizar o cálculo em memória.

Vantagens:

- menor complexidade compatível com o MVP;
- fatos de negócio permanecem persistidos e auditáveis;
- evita estado derivado duplicado;
- uma porta de leitura permite substituir o adapter por uma projeção futura sem mudar a definição da métrica.

Desvantagens:

- o custo da agregação ocorre durante a consulta;
- consultas de grande volume poderão exigir índices ou uma projeção especializada no futuro.

#### Option 2: Histórico append-only de transições e projeção analítica

Cada transição relevante seria registrada como evento imutável, e uma projeção construiria as métricas.

Vantagens:

- oferece histórico completo, reprocessamento e base para pausas e novas métricas;
- desacopla o modelo analítico do modelo transacional.

Desvantagens:

- introduz decisões adicionais sobre consistência, idempotência, retenção e reconstrução;
- aumenta desproporcionalmente o escopo do MVP.

#### Option 3: Tabela de médias pré-calculadas

Contadores e durações acumuladas seriam atualizados quando uma execução fosse concluída.

Vantagens:

- leitura rápida e previsível.

Desvantagens:

- duplica estado derivado e exige tratamento de concorrência, correção e reprocessamento;
- o volume esperado no MVP não justifica essa complexidade.

#### Option 4: Métrica efêmera em Micrometer/Prometheus

A aplicação registraria a duração em um `Timer` e consultaria a média pela plataforma de observabilidade.

Vantagens:

- integração natural com dashboards e alertas operacionais;
- útil para acompanhar o comportamento da aplicação em produção.

Desvantagens:

- não constitui histórico de negócio autoritativo;
- reinício e retenção da plataforma podem alterar a janela observada;
- filtros de domínio e cardinalidade de labels limitam seu uso como relatório administrativo;
- exigiria infraestrutura de observabilidade ainda não consolidada no projeto.

## Decision

Será adotada a **Option A** para a semântica e a **Option 1** para a estratégia de entrega.

O tempo médio de execução representará a média do tempo transcorrido entre `startedAt` e `completedAt` de cada
`ServiceExecution` concluída. Para o MVP:

- `startedAt` é registrado na transição válida para `IN_PROGRESS`;
- `completedAt` é registrado na transição válida para `COMPLETED`;
- ambos são fatos imutáveis e usam instante UTC;
- somente execuções `COMPLETED` com os dois instantes válidos entram no cálculo;
- execuções rejeitadas, ainda em andamento ou em qualquer estado anterior são excluídas;
- diagnóstico, aprovação e espera por materiais são excluídos porque antecedem `startedAt`;
- não existe contabilização específica de pausas: o cálculo utiliza somente os instantes das transições para
  `IN_PROGRESS` e `COMPLETED`;
- cada execução, inclusive uma execução originada por reparo adicional, representa uma amostra independente;
- o período da consulta é aplicado sobre `completedAt`, com início inclusivo e fim exclusivo;
- a consulta oferece o resultado global e o agrupamento por `catalogServiceId`;
- todo resultado informa a quantidade de amostras junto da média;
- a duração média é apresentada em horas, inclusive quando possuir parte fracionária; segundos e milissegundos não
  são unidades de apresentação dessa métrica;
- a ausência de amostras deve ser distinguida de uma duração média igual a zero;
- registros anteriores à introdução dos timestamps permanecem sem medição e não recebem backfill inferido.

Os fatos temporais pertencem ao agregado `ServiceOrder`/`ServiceExecution`; a agregação pertence ao lado de leitura do
módulo `servicelifecycle`. Não será criado um novo bounded context de Analytics no MVP. O contrato HTTP, a precisão e
o arredondamento da representação em horas, os limites do período, a persistência, os índices e a estratégia de
testes serão definidos na `technical-spec.md` da feature, após aprovação da respectiva `functional-spec.md`.

Micrometer poderá receber a mesma duração como telemetria complementar no futuro, mas não será a fonte de verdade da
métrica administrativa. O lead time completo da `ServiceOrder` poderá ser introduzido posteriormente como indicador
separado e com nome não ambíguo.

## Consequências

### Positivas ✅

- O requisito passa a ter uma definição objetiva, verificável e diretamente relacionada ao trabalho executado.
- Espera por aprovação ou peças não distorce a comparação entre tipos de serviço.
- Os fatos temporais podem sustentar consultas e métricas futuras sem reconstrução por logs.
- A porta de leitura mantém o cálculo substituível por projeção ou pré-agregação se o volume crescer.
- A solução permanece dentro do bounded context responsável e não cria infraestrutura prematura.

### Negativas ❌

- A métrica não expressa a experiência completa do Customer.
- Pausas e retomadas não podem produzir ajustes na duração porque o MVP não modela essas transições.
- O histórico anterior à migration não poderá compor a média com confiabilidade.
- A agregação sob demanda pode exigir otimização conforme o volume e a janela consultada crescerem.

### Mitigação de Riscos

- Nomear e documentar a métrica como duração transcorrida de execução, sem usar “tempo ativo”.
- Expor sempre a quantidade de amostras e o período efetivamente consultado.
- Não substituir valores ausentes por zero nem estimar timestamps históricos.
- Avaliar o plano da consulta e índices na especificação técnica e nos testes de persistência.
- Caso pausa e retomada sejam introduzidas, revisar esta ADR e definir segmentos ativos antes de alterar a semântica.
- Caso o volume torne a consulta inadequada, manter a porta e substituir apenas o adapter por uma projeção ou
  pré-agregação reprocessável.

## Related ADRs

- **ADR-002:** Real-Time Updates Strategy — a estratégia de polling para tracking não define nem armazena esta métrica
  administrativa.
- **ADR-005:** Inter-Module Integration Contract — a consulta permanece interna a `servicelifecycle`; nenhuma chamada
  REST entre módulos é necessária.
- **AD-019 em `docs/Architecture-Decisions.md`:** registro original, agora resolvido, da semântica do tempo médio.

## References

- `docs/Architecture.md` — requisito oficial e lacuna de cálculo/exposição do tempo médio.
- `docs/Architecture-Decisions.md` — AD-019, “Define average service-execution time semantics”.
- `docs/features/servicelifecycle/complete-execution/functional-spec.md` — timestamps de execução anteriormente
  registrados como fora de escopo da feature RF22.

## Approval Checklist

- [x] Opção A selecionada para elaboração da proposta em 2026-08-25.
- [x] A amostra é uma `ServiceExecution` concluída e não uma `ServiceOrder` completa.
- [x] O MVP utiliza somente as mudanças de status e não contabiliza pausas separadamente, confirmado por Ivan Gomes
      em 2026-08-28.
- [x] O período usa `completedAt`, o agrupamento usa `catalogServiceId` e o resultado informa a quantidade de amostras.
- [x] Horas, inclusive fracionárias, são a unidade de apresentação da duração média.
- [x] AD-019 foi marcado `Resolved` após a ratificação.
- [x] `functional-spec.md` da feature foi criada e aprovada antes da especificação técnica e da implementação.

---

**Last Updated:** 2026-08-28

**Decision Maker:** Ivan Gomes

**Status:** Accepted — Option A e agregação sob demanda ratificadas em 2026-08-28

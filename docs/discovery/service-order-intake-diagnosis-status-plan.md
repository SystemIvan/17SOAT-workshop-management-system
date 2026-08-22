# Plano de consolidação: entrada, diagnóstico e status da Service Order

**Status:** Draft — requer validação humana
**Natureza:** discovery; não autoriza implementação nem altera features aprovadas

## Objetivo

Separar e alinhar quatro conceitos que hoje estão parcialmente ausentes ou misturados:

1. triagem inicial feita pelo Service Advisor;
2. atribuição planejada de quem fará o diagnóstico;
3. autoria efetiva do diagnóstico;
4. visão resumida do andamento da Service Order.

## Escopo e segregação

Este plano cobre somente entrada da SO, atribuição e autoria do diagnóstico e projeção de status. Permanecem
independentes:

- `start-execution`, que continua sendo um comando explícito por endpoint;
- geração e persistência da Estimate e decisão de suas linhas;
- Stock Reservation, acompanhada no
  [plano da feature](../features/stockprocurement/stock-item-reservation/implementation-plan.md).

Esses fluxos não devem ser alterados como efeito colateral desta discovery.

## Fluxo proposto

1. O Service Advisor cria a Service Order e registra `initialAssessment`, com as informações iniciais do cliente e do
   veículo.
2. O Service Advisor define `diagnosisAssigneeId`, indicando o Technician planejado para fazer o diagnóstico.
3. O Technician realiza o diagnóstico. Nesse momento são criadas as Service Executions e cada uma recebe o mesmo
   `diagnosisId`, `diagnosedByTechnicianId` e `diagnosedAt`.
4. Cada Service Execution pode receber seu próprio `assignedTechnicianId`, inclusive diferente do autor do diagnóstico.
5. O detalhamento operacional fica nas Service Executions; a Service Order mantém apenas uma projeção resumida em
   `statusSnapshot`.

## Responsabilidade dos dados

| Dado | Onde fica | Significado |
|---|---|---|
| `initialAssessment` | Service Order | Triagem não técnica registrada pelo Service Advisor na criação |
| `diagnosisAssigneeId` | Service Order | Technician planejado; pode mudar antes do diagnóstico |
| `diagnosisId` | Service Execution | Agrupa as execuções criadas pelo mesmo diagnóstico |
| `diagnosedByTechnicianId` | Service Execution | Technician que efetivamente realizou o diagnóstico |
| `diagnosedAt` | Service Execution | Momento em que o diagnóstico foi registrado |
| `assignedTechnicianId` | Service Execution | Technician responsável por executar aquele serviço específico |

`diagnosisAssigneeId` não substitui `diagnosedByTechnicianId`: o primeiro representa planejamento e o segundo,
auditoria do que realmente ocorreu.

## Regras mínimas

- `initialAssessment` é um texto livre e não contém serviços, materiais, preços ou conclusão técnica. Evita-se o nome
  `preDiagnosis` no código para não confundir triagem com `Diagnosis`.
- A atribuição do diagnóstico acontece antes da criação das Service Executions e, por isso, pertence à Service Order.
- A autoria do diagnóstico é registrada quando as Service Executions são criadas e não deve ser inferida apenas da
  atribuição planejada.
- A atribuição para execução é independente: diferentes Technicians podem executar os serviços diagnosticados.

## Consistência de `statusSnapshot`

`statusSnapshot` deve ser tratado como uma projeção genérica, e não como substituto do status de cada Service
Execution. Para estados simultâneos, prevalece a fase ativa mais avançada:

1. `DELIVERED`;
2. `COMPLETED`, quando todo o trabalho aplicável estiver encerrado;
3. `IN_PROGRESS`, quando existir execução `READY` ou `IN_PROGRESS`;
4. `AWAITING_ITEMS`, quando não houver execução pronta/em andamento e alguma estiver aguardando itens;
5. `AWAITING_APPROVAL`, quando houver linhas enviadas ainda sem decisão;
6. `IN_DIAGNOSIS`, enquanto o diagnóstico estiver aberto;
7. `RECEIVED`, quando nenhuma das condições anteriores se aplicar.

A resposta unificada da Service Order deve expor `statusSnapshot` e as Service Executions. Assim, por exemplo, uma SO
pode estar genericamente `IN_PROGRESS` enquanto uma execução específica permanece `AWAITING_ITEMS`.

## Decisões pendentes

- confirmar o nome `initialAssessment` e se o campo será obrigatório;
- decidir se `diagnosisAssigneeId` será obrigatório antes de realizar o diagnóstico;
- definir se `diagnosedByTechnicianId` virá da identidade autenticada ou, provisoriamente, do request;
- definir o estado final da SO quando todas as execuções forem rejeitadas;
- confirmar o mapeamento de execução `READY` para o snapshot genérico `IN_PROGRESS`.

## Próximos passos, após aprovação

1. Atualizar o Miro de forma aditiva, preservando o histórico do desenho atual.
2. Abrir specs funcionais independentes para:
   - triagem inicial da Service Order;
   - atribuição do responsável pelo diagnóstico;
   - autoria do diagnóstico;
   - correção da projeção de status da Service Order.
3. Seguir os gates SDD de cada feature antes de alterar domínio, persistência ou contratos HTTP.
4. Atualizar OpenAPI, Postman, migrations e testes somente nas features aprovadas.

Não se recomenda incluir este detalhamento em `docs/Architecture-Decisions.md`: ainda são decisões funcionais em
discovery, e a separação por tema evita misturá-las às decisões arquiteturais já consolidadas.

## Referências no Miro

- [Service Order](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764678560725739)
- [Ubiquitous Language](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684049703)
- [Requirements and Refinement](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679721508363)

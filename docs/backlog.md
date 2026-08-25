# Backlog de evolução

Este documento registra ideias e oportunidades deliberadamente deixadas fora do MVP. Um item do backlog não representa
compromisso de implementação nem substitui o processo de SDD: antes de ser desenvolvido, deverá passar por discovery e
por suas próprias aprovações funcional e técnica.

## BL-001 — Revisão e versionamento de Estimate pendente

- **Contexto:** Service Lifecycle, com dados consultados de Stock & Procurement.
- **Status:** ideia futura.
- **Origem:** `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`.
- **Problema:** uma Estimate mantém congelados os snapshots de nome, tipo e preço dos Stock Items desde sua criação. O
  MVP não permite atualizar uma oferta pendente quando preço ou escopo mudam.
- **Evolução a avaliar:** permitir revisão explícita, preservar o histórico das versões apresentadas e garantir que o
  Customer aprove exatamente a versão vigente.
- **Pontos para discovery:** eventos que permitem revisão, efeito sobre aprovações parciais, expiração da versão
  anterior, auditoria, comunicação ao Customer e comportamento dos Stock Requirements entre versões.

## BL-002 — Operações de inventário de Stock Item

- **Contexto:** Stock & Procurement.
- **Status:** feature futura.
- **Origem:** `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`.
- **Problema:** o catálogo mantém somente a quantidade disponível inicial e não altera saldos após a criação.
- **Evolução a avaliar:** recebimento, retirada administrativa, justificativa, ajustes, nível mínimo, rastreabilidade e
  identificação de baixo estoque.
- **Pontos para discovery:** tipos de movimentação, correções, auditoria mínima, responsáveis, concorrência e unidade de
  medida.

## BL-003 — Ciclo de Stock Reservation

- **Contexto:** Stock & Procurement, consumido por Service Lifecycle.
- **Status:** feature futura.
- **Origem:** `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`.
- **Problema:** uma Service Execution aprovada precisa separar itens sem depender de um contador reservado sem origem.
- **Evolução a avaliar:** Stock Reservation com referência à Service Execution, linhas de item/quantidade, reserva
  atômica, idempotência, liberação e consumo.
- **Pontos para discovery:** identidade da reserva, fronteira transacional, locks, indisponibilidade após aprovação,
  repetição de comandos e vínculo com Procurement.

## BL-004 — Integração de Stock Requirement e Estimate

- **Contexto:** Service Lifecycle consumindo Stock & Procurement.
- **Status:** feature futura.
- **Origem:** `docs/features/stockprocurement/stock-domain-foundation/functional-spec.md`.
- **Problema:** o scaffolding atual permite que o cliente envie nome, tipo e preço de Stock Item no diagnóstico.
- **Evolução a avaliar:** Stock Requirement recebe somente `stockItemId` e quantidade; a Estimate consulta os dados
  canônicos e congela seu snapshot comercial na criação.
- **Pontos para discovery:** porta consumidora, tratamento de item inativo, disponibilidade informativa, contrato HTTP e
  gatilho de Stock Reservation após aprovação.

## BL-005 — Observabilidade e política de logs da aplicação

- **Contexto:** plataforma, com aplicação pelos bounded contexts.
- **Status:** ideia futura.
- **Origem:** revisão operacional durante a execução de `stock-domain-foundation`.
- **Problema:** a aplicação usa somente os logs padrão do Spring Boot; não existe política para logs de negócio,
  correlação, níveis por ambiente, formato estruturado, retenção ou proteção sistemática contra dados sensíveis.
- **Evolução a avaliar:** definir eventos operacionais relevantes, formato e destino dos logs, correlação de requisições,
  níveis por ambiente, métricas/health checks e regras para impedir registro de dados pessoais, segredos e bodies HTTP.
- **Pontos para discovery:** requisitos de suporte e auditoria, observabilidade de Flyway e integrações, custo/retenção,
  LGPD, alertas, tracing distribuído e ownership operacional.

## BL-006 — Histórico persistente de progresso de Service Execution

- **Contexto:** Service Lifecycle.
- **Status:** ideia futura.
- **Origem:** revisão do fluxo manual principal pelo Postman.
- **Problema:** `PATCH /api/service-orders/{id}/executions/{executionId}/progress` aceita uma `note`, mas o MVP apenas
  valida que a execução está `IN_PROGRESS`; a nota não é persistida nem retornada nas consultas.
- **Evolução a avaliar:** registrar entradas de progresso com texto, instante e autoria, e expor o histórico no detalhe
  da Service Order sem alterar indevidamente o estado da execução.
- **Pontos para discovery:** fronteira do aggregate, modelo de auditoria, identificação do autor, ordenação temporal,
  limites de conteúdo, retenção/LGPD, contrato HTTP e estratégia de migração.

## BL-007 — Listagem operacional de Service Orders

- **Contexto:** Service Lifecycle.
- **Status:** ideia futura.
- **Origem:** revisão do fluxo manual principal pelo Postman.
- **Problema:** a API permite consultar uma Service Order por ID e seu status agregado, mas não oferece uma visão para
  triagem operacional de ordens abertas.
- **Evolução a avaliar:** criar uma listagem paginada de resumos de Service Orders, com filtros a definir para
  `statusSnapshot`, prioridade, Customer, Vehicle e Technician, preservando o detalhe completo para a consulta por ID.
- **Pontos para discovery:** critérios de ordenação, paginação, projeção de leitura sem carregar todas as execuções,
  índices e desempenho, campos mínimos para a operação, autorização/exposição de dados de Customer e contrato HTTP.

## BL-008 — Totais calculados de Estimate

- **Contexto:** Service Lifecycle.
- **Status:** ideia futura.
- **Origem:** revisão do fluxo manual principal pelo Postman.
- **Problema:** a Estimate retorna os preços de serviço e dos itens de estoque por linha, mas não expõe subtotal por
  linha nem total consolidado para consumo por cliente ou interface operacional.
- **Evolução a avaliar:** expor `lineTotal` e `total`, calculados no servidor a partir dos preços congelados e das
  quantidades dos itens, sem aceitar valores totais no request.
- **Pontos para discovery:** regra para moedas distintas, arredondamento, cálculo de itens por quantidade, contrato de
  resposta, compatibilidade e se os valores derivados exigem persistência para auditoria/versionamento.

## BL-009 — Prazo de conclusão e monitoramento de serviço em atraso

- **Contexto:** Service Lifecycle.
- **Status:** ideia futura.
- **Origem:** necessidade de negócio de proteger o Customer contra a retenção indefinida do Vehicle na oficina.
- **Problema:** o fluxo atual não define um prazo para a conclusão do serviço nem oferece visibilidade operacional quando
  esse prazo é ultrapassado, o que pode deixar o Customer sem previsibilidade e com o Vehicle retido por tempo
  indeterminado.
- **Evolução a avaliar:** definir e controlar um prazo de conclusão para o serviço. Quando a oficina não concluir o
  trabalho no período acordado, um evento deve registrar o atraso e alimentar métricas, alertas e acompanhamento
  operacional, sem cancelar o serviço automaticamente. Também deve existir um endpoint para solicitar o cancelamento em
  casos excepcionais. O prazo acordado deve constar na Estimate de forma clara para ciência e aprovação do Customer.
- **Pontos para discovery:** início da contagem, unidade e cálculo do prazo, pausas justificadas, alteração do prazo com
  nova anuência do Customer, indicadores de atraso, metas operacionais, alertas e escalonamento antes e depois do prazo,
  publicação e consumo idempotente do evento, prevenção de eventos e métricas duplicados, autorização e idempotência do
  endpoint, casos em que o cancelamento manual é permitido, consequências do cancelamento, liberação do Vehicle,
  auditoria, comunicação ao Customer e impacto nos estados de Estimate, Service Order e Service Execution.

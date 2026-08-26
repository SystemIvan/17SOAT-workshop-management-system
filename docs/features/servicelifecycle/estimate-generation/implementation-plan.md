# Plano de Implementação: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Implemented |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-25 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-25) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-25) |
| Decisão arquitetural | AD-013 — política de expiração de Estimate |

## Objetivo

Revalidar os requisitos congelados durante a geração da Estimate, reconciliar Purchase Demands e guardar em cada linha
a fotografia de disponibilidade independente do snapshot comercial, sem reservar estoque.

Implementar também a política ratificada de expiração da Estimate, utilizando a disponibilidade observada durante a
geração para determinar `expiresAt`:

- todos os Stock Items disponíveis: 24 horas;
- qualquer Stock Item indisponível ou com quantidade insuficiente: 48 horas.

A política de cálculo do prazo permanece separada do mecanismo automático de expiração. O scheduler utiliza somente o
`expiresAt` persistido e não conhece nem recalcula as durações de 24h ou 48h.

## Checkpoints ordenados

### 1. Domínio e avaliação compartilhada

- [x] Criar `EstimateStockAvailability` e invariantes, agregando uma entrada por Stock Item em cada `EstimateLine`.
- [x] Reutilizar exclusivamente `RepairStockAssessmentApi`; não importar internals de Stock & Procurement.
- [x] Cobrir cópia imutável, resultado incompleto/extra/duplicado e execução sem requisito.

### 2. Orquestração e persistência

- [x] Alterar `GenerateEstimateUseCase` para congelar, consolidar, avaliar uma vez, atualizar a Service Execution e só
  então criar/persistir a Estimate e publicar `EstimateGenerated`.
- [x] Criar migration aditiva para `estimate_line_stock_availability` e adaptar JPA/mappers, sem backfill e sem seed.
- [x] Garantir rollback de congelamento, snapshots, Estimate e Purchase Demands para qualquer erro do lote.

### 3. Política de expiração — AD-013

- [x] Criar `EstimateExpirationPolicy` como serviço de domínio dedicado ao cálculo de `expiresAt`.
- [x] Definir prazo de 24 horas quando todos os Stock Items estiverem disponíveis.
- [x] Definir prazo de 48 horas quando qualquer Stock Item estiver indisponível ou possuir quantidade insuficiente.
- [x] Utilizar o snapshot de disponibilidade já produzido durante a geração, sem nova consulta ao estoque.
- [x] Utilizar o instante fornecido pelo `Clock` como referência para o cálculo.
- [x] Integrar `EstimateExpirationPolicy` ao `GenerateEstimateUseCase`.
- [x] Persistir o `expiresAt` calculado na Estimate.
- [x] Publicar em `EstimateGenerated` o mesmo `expiresAt` persistido.
- [x] Cobrir os cenários de 24h e 48h com testes automatizados.

### 4. Expiração automática

- [x] Permitir a transição de domínio `SENT -> EXPIRED`.
- [x] Disponibilizar busca de Estimates `SENT` cujo `expiresAt` tenha sido atingido.
- [x] Implementar `ExpireEstimatesUseCase` para executar e persistir a expiração.
- [x] Implementar `EstimateExpirationScheduler` para disparar periodicamente o caso de uso.
- [x] Habilitar scheduling na aplicação.
- [x] Manter o scheduler independente da política de 24h/48h.
- [x] Utilizar exclusivamente o `expiresAt` persistido para decidir se uma Estimate venceu.
- [x] Cobrir caso de uso e scheduler com testes automatizados.

### 5. HTTP e documentação

- [x] Expor `stockAvailability` como array não nulo em criação e consulta de Estimate, mantendo `stockItems` comercial.
- [x] Manter `expiresAt` como parte da representação da Estimate.
- [x] Não criar endpoint específico para manipular horário ou forçar expiração.
- [x] Atualizar a especificação funcional com a política ratificada da AD-013.
- [x] Atualizar a especificação técnica com a política e o mecanismo de expiração.
- [x] Atualizar este plano de implementação.
- [x] Atualizar Springdoc, MockMvc, coleção Postman e README para a revalidação da Estimate.

### 6. Segurança e qualidade

- [x] Revisar dados calculados pelo servidor, erros estáveis, ausência de dados pessoais e a lacuna de autenticação do
  baseline.
- [x] Manter `expiresAt` calculado exclusivamente pelo servidor.
- [x] Utilizar `Clock` nos testes em vez de endpoint para manipulação de horário.
- [x] Executar testes unitários de `EstimateExpirationPolicy`.
- [x] Executar testes de `GenerateEstimateUseCase`.
- [x] Executar suíte relacionada a Estimate.
- [ ] Executar `mvnw verify` completo após a atualização final.
- [ ] Executar `git diff --check` antes do commit.

## Critérios de conclusão

- [x] Estimate preserva sua fotografia e não altera saldo/reserva.
- [x] Leitura suficiente não resolve Purchase Demand; reserva criada continua sendo a transição de resolução.
- [x] `expiresAt` é calculado durante a geração da Estimate.
- [x] Estimate com todos os itens disponíveis recebe prazo de 24 horas.
- [x] Estimate com qualquer item indisponível recebe prazo de 48 horas.
- [x] O mecanismo automático de expiração não recalcula a política de duração.
- [x] Estimate `SENT` vencida pode transicionar para `EXPIRED`.
- [x] O scheduler utiliza o `expiresAt` persistido como fonte de verdade.
- [x] Não existe endpoint adicional para simular passagem de tempo.
- [x] Testes específicos da política de expiração passaram com 2 testes, 0 falhas e 0 erros.
- [x] Testes de `GenerateEstimateUseCase` passaram com 6 testes, 0 falhas e 0 erros.
- [x] Suíte relacionada a Estimate passou com 55 testes, 0 falhas e 0 erros.
- [ ] Build completo final validado após atualização da documentação.
- [ ] Diff final validado antes do commit.

## Evidências e segurança

A implementação mantém o cálculo de `expiresAt` no servidor e não permite que o Customer determine ou altere o prazo.

A política utiliza o snapshot de disponibilidade produzido durante a geração:

```text
todos disponíveis
    -> createdAt + 24h

qualquer indisponibilidade
    -> createdAt + 48h
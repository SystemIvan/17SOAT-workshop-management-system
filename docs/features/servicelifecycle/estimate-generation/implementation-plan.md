# Plano de Implementação: Geração de Estimate

| Campo | Valor |
|---|---|
| Feature | `estimate-generation` |
| Status | Approved |
| Responsável | Matheus Campagnone |
| Atualizado em | 2026-08-16 |
| Functional Spec | `./functional-spec.md` |
| Technical Spec | `./technical-spec.md` |

## Objetivo

Implementar uma fatia vertical de geração de Estimate a partir de um Diagnosis já realizado, incluindo domínio, persistência, caso de uso, API, contrato `EstimateGenerated` e testes.

A entrega não inclui aprovação/rejeição da Estimate, reserva de Stock, execução de serviços ou Notification.

## Checkpoint 1 — Domínio

Criar:

- `Estimate`
- `EstimateLine`
- snapshot dos Stock Requirements da linha
- `EstimateGenerated`
- `EstimateRepository`

Validar:

- Estimate exige pelo menos uma linha;
- IDs obrigatórios;
- snapshots imutáveis;
- uma linha referencia sua `ServiceExecution`;
- o evento possui contrato estável para consumo externo.

Testes:

- criação válida;
- Estimate sem linhas rejeitada;
- snapshots preservados;
- coleções expostas de forma imutável.

## Checkpoint 2 — Caso de uso

Criar `GenerateEstimateUseCase`.

Fluxo:

1. carregar ServiceOrder;
2. validar Diagnosis;
3. selecionar ServiceExecutions do Diagnosis;
4. impedir geração sem execuções;
5. impedir Estimate duplicada para o Diagnosis;
6. criar snapshots;
7. persistir Estimate;
8. produzir `EstimateGenerated`.

Não limpar `openDiagnosisId` durante a geração.

Testes:

- fluxo válido;
- ServiceOrder inexistente;
- Diagnosis inválido;
- Diagnosis sem execuções;
- Estimate duplicada;
- persistência realizada;
- evento produzido.

## Checkpoint 3 — Persistência

Criar migration Flyway nova para:

- `estimates`;
- `estimate_lines`;
- snapshots de Stock Items por linha.

Criar adapter de persistência seguindo o padrão já usado no projeto.

Garantir unicidade de `diagnosis_id`.

Validar round-trip entre domínio e JPA.

## Checkpoint 4 — API

Criar endpoint:

`POST /api/service-orders/{serviceOrderId}/diagnoses/{diagnosisId}/estimate`

Resultado esperado:

- `201 Created`;
- retorno da Estimate criada;
- identificação de ServiceOrder, Diagnosis, Customer e linhas;
- erros coerentes para not found, conflito e dados inválidos.

Atualizar:

- OpenAPI;
- collection Postman.

## Checkpoint 5 — Contrato para Notifications

Garantir que `EstimateGenerated` exponha:

- `eventId`;
- `occurredAt`;
- `estimateId`;
- `serviceOrderId`;
- `diagnosisId`;
- `customerId`;
- `expiresAt`.

Não implementar adapter ou listener de Notification nesta feature.

O contrato deve poder ser instanciado e testado independentemente para permitir que o Épico 5 trabalhe com mock.

## Checkpoint 6 — Validação final

Executar:

- testes do domínio;
- testes da Application Layer;
- testes de persistência;
- testes Web;
- testes de arquitetura;
- `make verify`.

Revisar:

- migration;
- OpenAPI;
- Postman;
- ausência de mudanças fora da feature;
- ausência de violação entre módulos.

## Definition of Done

- [ ] Estimate implementada como Aggregate Root.
- [ ] EstimateLine implementada como snapshot comercial.
- [ ] EstimateRepository implementado.
- [ ] GenerateEstimateUseCase implementado.
- [ ] Persistência e migration implementadas.
- [ ] Endpoint REST implementado.
- [ ] `EstimateGenerated` implementado e testado.
- [ ] OpenAPI atualizado.
- [ ] Postman atualizado.
- [ ] Testes relevantes passando.
- [ ] `make verify` passando.
- [ ] PR pronto para review.

# Especificação Funcional: Finalizar e entregar a Service Order

| Campo | Valor |
|---|---|
| Feature | `finalize-service-order` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-19 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-08-19 |
| Referências | RF24 (Miro — "Levantamento de Requisitos e Refinamento Técnico"); `docs/features/servicelifecycle/complete-execution/functional-spec.md` (RF22); `docs/features/servicelifecycle/track-execution/functional-spec.md` (RF23); `docs/Architecture-Decisions.md` (AD-006, AD-010, AD-015, AD-016); `.claude/rules/epic-3-service-lifecycle.md`; código atual: `FinalizeServiceOrderUseCase`, `ServiceOrder.finalize`, `ServiceOrderTest.rf24_finalizeRequiresCompletedStatusAndVehicleDelivered` |

## Problema e resultado esperado

Depois que todas as `ServiceExecution` não-rejeitadas de uma `ServiceOrder` estão `completed` (RF22),
a oficina precisa registrar a entrega do veículo ao Customer, fechando o ciclo de vida da
`ServiceOrder`. Sem essa etapa, a Service Order fica presa em `COMPLETED` mesmo depois de o veículo
já ter sido retirado, e o tracking (RF23) nunca reflete o estado terminal `DELIVERED`.

Resultado esperado: dado o ID de uma `ServiceOrder` cujo `statusSnapshot` é `COMPLETED`, e a
confirmação de que o veículo foi entregue, o sistema marca a Service Order como `DELIVERED` e
retorna a Service Order atualizada.

**Nota sobre o estado atual do código:** este comportamento já está implementado
(`FinalizeServiceOrderUseCase`, endpoint `POST /api/service-orders/{id}/finalize`,
`ServiceOrder.finalize(boolean vehicleDelivered)`), mas foi escrito antes do gate de SDD adotado pelo
projeto — sem spec dedicada e com cobertura de teste parcial: `ServiceOrderTest` já cobre a regra de
domínio (`rf24_finalizeRequiresCompletedStatusAndVehicleDelivered`), mas não existe
`FinalizeServiceOrderUseCaseTest` nem teste HTTP para o endpoint, e o endpoint não tem
`@ApiResponses` (diferente de `assignTechnician`/`startExecution`/`updateExecutionProgress`/
`completeExecution`). Esta spec documenta o comportamento esperado de RF24 para validá-lo
formalmente contra o requisito, identificar lacunas e servir de base para a cobertura de teste que
falta — não parte do zero.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Service Advisor / Manager | Confirma a entrega do veículo e finaliza a `ServiceOrder` depois que o Customer retira o veículo |
| Customer | Indiretamente: pode ver (via tracking, RF23) que a `ServiceOrder` passou a `DELIVERED` |

### Cenário principal

1. A `ServiceOrder` existe e seu `statusSnapshot` é `COMPLETED` (todas as execuções não-rejeitadas
   concluídas, RF22).
2. O ator informa o `serviceOrderId` e confirma `vehicleDelivered = true`.
3. O sistema recalcula o `statusSnapshot` da `ServiceOrder` para `DELIVERED` e retorna a Service Order
   atualizada.

### Cenários de erro

1. A `ServiceOrder` ainda não está `COMPLETED` (ex.: ainda há execução `pending`/`in_progress`) e o
   ator tenta finalizar: o sistema rejeita a operação.
2. A `ServiceOrder` está `COMPLETED`, mas o ator informa `vehicleDelivered = false`: o sistema rejeita
   a operação — a finalização exige a confirmação explícita da entrega, não apenas a conclusão do
   trabalho.
3. O `serviceOrderId` não existe: erro `not-found` estável.

## Regras de negócio

- Uma `ServiceOrder` só pode ser finalizada quando seu `statusSnapshot` atual é exatamente
  `COMPLETED` **e** `vehicleDelivered` é `true` na mesma chamada. **Comportamento atual do código**,
  preservado por esta spec: `ServiceOrder.finalize` rejeita a operação se qualquer uma das duas
  condições não for satisfeita (`statusSnapshot != COMPLETED || !vehicleDelivered`).
- Finalizar recalcula o `statusSnapshot` da `ServiceOrder` via `recomputeStatusSnapshot(true)`
  (AD-010, Option B: recomputado em comando, não em leitura — comportamento preservado, decisão de
  time ainda pendente). Com `vehicleDelivered = true` e todas as execuções concluídas, a precedência
  de `recomputeStatusSnapshot` resolve o `statusSnapshot` para `DELIVERED`.
- A operação não é idempotente: finalizar uma `ServiceOrder` que já não está em `COMPLETED` (por
  exemplo, uma já `DELIVERED`) falha, pois a regra compara `statusSnapshot != COMPLETED`.

### Regras que a Ubiquitous Language e o código atual NÃO definem (não inventar)

- **Quem pode finalizar a Service Order:** mesma lacuna de AD-016 (identidade e autorização) já
  registrada em RF20/RF21/RF22/RF23 — nenhuma fonte restringe o ator a um Service Advisor/Manager
  autenticado.
- **Como `vehicleDelivered` é confirmado (assinatura, checklist físico, etc.):** não modelado; o
  contrato atual recebe apenas um booleano (`FinalizeServiceOrderRequest.vehicleDelivered`).
- **Cobrança/pagamento associado à entrega:** fora do bounded context `servicelifecycle`; não
  mencionado em `Architecture.md` como parte de RF24.
- **Notificar o Customer quando a `ServiceOrder` fica `DELIVERED`:** AD-015 foi resolvida em 2026-08-23
  a favor de polling puro (sem cache/push); um mecanismo de notificação em tempo real exigiria uma nova
  decisão do time. Esta spec não implementa notificação alguma.

## Fora de escopo

- Autorização de quem pode finalizar — depende de AD-016.
- Qualquer mecanismo de confirmação de entrega além do booleano `vehicleDelivered` já existente
  (assinatura digital, checklist, foto, etc.) — não modelado no domínio atual.
- Notificações em tempo real de entrega — AD-015 resolvida a favor de polling puro (2026-08-23); push
  exigiria nova decisão do time.
- Qualquer mudança na regra de `finalize` ou na precedência de `recomputeStatusSnapshot` —
  comportamento já implementado e já coberto por teste de domínio; esta feature apenas fecha a lacuna
  de cobertura no nível de use case/HTTP e a lacuna de documentação Swagger.
- Reabrir uma `ServiceOrder` já `DELIVERED` — não modelado; fora do escopo deste RF.

## Critérios de aceite

- [x] Finalizar uma `ServiceOrder` cujo `statusSnapshot` é `COMPLETED`, com `vehicleDelivered = true`,
      muda seu `statusSnapshot` para `DELIVERED` e é refletido na resposta. Evidência:
      `FinalizeServiceOrderUseCaseTest.finalizesACompletedServiceOrderAndMovesItToDelivered` e
      `ServiceOrderControllerFinalizeTest.finalizesACompletedServiceOrderAndReturns200`.
- [x] Tentar finalizar uma `ServiceOrder` cujo `statusSnapshot` não é `COMPLETED` falha com erro de
      negócio explícito, mapeado para `409` (mesmo padrão de `completeExecution`). Evidência:
      `FinalizeServiceOrderUseCaseTest.rejectsFinalizingWhenServiceOrderIsNotCompleted` e
      `ServiceOrderControllerFinalizeTest.returnsConflictWhenServiceOrderIsNotCompleted`.
- [x] Tentar finalizar uma `ServiceOrder` que é `COMPLETED` mas com `vehicleDelivered = false` falha
      com o mesmo erro de negócio `409`. Evidência:
      `FinalizeServiceOrderUseCaseTest.rejectsFinalizingWhenVehicleWasNotDelivered` e
      `ServiceOrderControllerFinalizeTest.returnsConflictWhenVehicleWasNotDelivered`.
- [x] Finalizar uma `ServiceOrder` inexistente retorna erro `not-found` estável (`404 NOT_FOUND`).
      Evidência: `FinalizeServiceOrderUseCaseTest.rejectsFinalizingWhenServiceOrderDoesNotExist` e
      `ServiceOrderControllerFinalizeTest.returnsNotFoundWhenServiceOrderDoesNotExist`.
- [x] O endpoint `POST /api/service-orders/{id}/finalize` passa a documentar seus códigos de resposta
      via `@ApiResponses`, no mesmo padrão de `assignTechnician`/`startExecution`/
      `updateExecutionProgress`/`completeExecution`.

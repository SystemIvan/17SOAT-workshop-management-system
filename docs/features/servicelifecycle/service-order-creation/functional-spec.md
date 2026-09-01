# Especificação Funcional: Criação de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-creation` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | `docs/Architecture.md` §2.3 (RF09–RF18), `EPIC2-REVIEW.md`, feature `service-order-initial-assessment` |

> **Nota:** este documento é retroativo. A feature já está implementada em produção
> (`ServiceOrder.create`, `CreateServiceOrderUseCase`, `POST /api/service-orders`) e foi identificada
> sem passar pelo gate SDD do `AGENTS.md` durante a revisão do Épico 2. O texto abaixo descreve o
> comportamento como ele existe hoje no código, não uma proposta nova.

## Delta material aprovado em feature dependente — pendente de nova aprovação desta spec

A feature `service-order-initial-assessment` passa a exigir `initialAssessment` não vazio em
`POST /api/service-orders` e o expõe na resposta detalhada, podendo ser nulo somente para Service Orders legadas.
O campo registra a triagem não técnica de entrada e não cria Diagnosis ou Service Executions. A mudança de request é
incompatível e foi aprovada naquela feature; esta referência não duplica suas regras.

Classificação: **material** — altera o request de criação, a validação e a resposta da feature. A revisão foi aprovada
por humano em 2026-08-22. A especificação técnica revisada foi aprovada na sequência; o plano histórico permanece
`Stale` porque não cobre a implementação deste delta, que pertence ao plano da feature dependente.

## Problema e resultado esperado

O workshop precisa abrir uma Service Order no momento em que um Customer chega com um Vehicle para
atendimento, antes de qualquer diagnóstico. A Service Order é o aggregate que vai concentrar todo o
ciclo de vida do atendimento (diagnóstico, Estimate, execução, entrega).

Ao final da criação:

- existe uma Service Order identificável, com status inicial `RECEIVED`;
- a Service Order referencia o Customer e o Vehicle por ID;
- os dados do Vehicle apresentados no momento da criação ficam congelados em um `VehicleSnapshot`,
  imune a alterações futuras no cadastro do Vehicle;
- a Service Order tem uma prioridade definida (padrão `NORMAL` quando não informada).
- a nova criação exige uma `initialAssessment` não vazia, conforme
  `docs/features/servicelifecycle/service-order-initial-assessment/functional-spec.md`.

## Atores e cenários

- Um atendente registra uma nova Service Order para um Customer e Vehicle já conhecidos pelo sistema, incluindo a
  triagem inicial exigida.
- O sistema cria a Service Order com status `RECEIVED`, sem nenhuma Service Execution ainda (essas só
  existem após o diagnóstico, fora do escopo desta feature).

## Regras de negócio

### Vínculo com Customer e Vehicle

- `customerId` e `vehicleId` são obrigatórios e são armazenados como referências por ID — a Service
  Order não copia nem depende de dados vivos de `registration.customer`/`registration.vehicle`.
- Não há validação cruzada de módulo verificando se o Customer/Vehicle existem em `registration` no
  momento da criação; a Service Order confia no ID informado. (Ver "Fora de escopo".)

### Congelamento do VehicleSnapshot

- No momento da criação, `licensePlate`, `brand`, `model` e `year` do Vehicle são copiados para um
  `VehicleSnapshot` imutável, armazenado na própria Service Order.
- Alterações posteriores no cadastro do Vehicle em `registration` não retroagem sobre Service Orders
  já criadas — o snapshot é a fonte de verdade para exibição histórica.
- Todos os quatro campos do `VehicleSnapshot` são obrigatórios; `year` deve ser um valor positivo.

### Prioridade

- A Service Order aceita uma prioridade (`LOW`, `NORMAL`, `HIGH`, `URGENT`) no momento da criação.
- Quando nenhuma prioridade é informada, o valor padrão é `NORMAL`.
- Alterar a prioridade após a criação é uma capacidade separada (RF10), fora do escopo deste
  documento.

### Triagem inicial

- `initialAssessment` é obrigatório para novas Service Orders; ausência, `null`, texto vazio ou somente espaços é
  rejeitado sem persistência parcial.
- A semântica completa, compatibilidade com registros legados e exclusões pertencem à feature
  `service-order-initial-assessment`; esta spec apenas incorpora o delta no fluxo de criação.

### Status inicial

- Toda Service Order criada começa com status `RECEIVED`.
- Nenhuma Service Execution existe na criação — elas só surgem a partir de um Diagnosis (feature
  separada, também sem SDD retroativo ainda; ver `EPIC2-REVIEW.md`).

## Fora de escopo

- validação de existência do Customer/Vehicle em `registration` no momento da criação;
- diagnóstico e criação de Service Executions;
- alteração de prioridade após a criação (RF10);
- notificação de technicians sobre a nova Service Order (coberta por
  `docs/features/servicelifecycle/notifications-technician-new-so/`);
- qualquer fluxo de Estimate, execução ou finalização.

## Critérios de aceite

- [x] Uma Service Order pode ser criada informando `customerId`, `vehicleId` e um `VehicleSnapshot`
  válido.
- [x] A Service Order criada referencia Customer e Vehicle por ID, não por objeto vivo.
- [x] O `VehicleSnapshot` da Service Order não é afetado por alterações posteriores no Vehicle.
- [x] Quando `priority` não é informado, a Service Order é criada com `NORMAL`.
- [x] Quando `priority` é informado, a Service Order é criada com o valor informado.
- [x] A Service Order criada começa com status `RECEIVED` e sem Service Executions.
- [x] A criação sem `initialAssessment` válido é rejeitada com erro de validação; o delta foi implementado e
      verificado em `service-order-initial-assessment`.
- [x] Campos obrigatórios ausentes ou inválidos (`customerId`, `vehicleId`, campos do
  `VehicleSnapshot`) resultam em erro de validação, não em criação parcial.

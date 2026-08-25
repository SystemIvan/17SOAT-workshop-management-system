# TD 002: Atribuição de Technician não valida especialidade nem disponibilidade

**Status:** Open  
**Date:** 2026-08-23  
**Reported by:** Santiago Silvestre  
**Affected areas:** `servicelifecycle` (`serviceorder`, `technician`)  
**Related decisions:** AD-006 (`docs/Architecture-Decisions.md`, Resolved em 2026-08-23)

---

## Contexto

`AD-006` foi ratificada pelo time em 2026-08-23: Technician continua um aggregate rico, com `specialties`
(`Set<Specialty>`) e `status`/disponibilidade (`AVAILABLE`/`BUSY`/`INACTIVE`), como já implementado. A
decisão explícita do time foi seguir, por ora, apenas com a validação de **existência** do Technician na
atribuição — deixando a validação de especialidade/disponibilidade registrada como dívida técnica, e não
como escopo bloqueado por decisão pendente.

## A dívida

`Technician.hasSpecialty(Specialty)` já existe no domínio (`technician/domain/model/Technician.java`), e o
aggregate já carrega `status()` (disponibilidade). Nenhum dos dois é consultado no momento da atribuição:
`AssignTechnicianUseCase` e `AssignDiagnosisAssigneeUseCase` apenas confirmam que o `technicianId`
informado existe (`TechnicianRepository.findById(...).orElseThrow(...)`), sem checar se o Technician tem a
especialidade exigida pelo serviço nem se seu `status` é `AVAILABLE`. Isso permite, por exemplo, atribuir
um Technician `BUSY` ou sem a especialidade correta a uma `ServiceExecution`.

## Evidência

- `technician/domain/model/Technician.java` — `hasSpecialty(Specialty)` implementado, sem nenhum chamador
  em todo o código (confirmado por busca em `serviceorder`).
- `serviceorder/application/usecase/AssignTechnicianUseCase.java:49-50` — `technicianRepository.findById(...)`
  seguido diretamente de `serviceOrder.confirmTechnicianAssignment(...)`, sem checagem de `status()` ou
  `hasSpecialty(...)`.
- `serviceorder/application/usecase/AssignDiagnosisAssigneeUseCase.java:29` — mesmo padrão de checagem
  apenas de existência.
- `serviceorder/application/usecase/CreateServiceOrderUseCase.java:46-49` — a única leitura de `status()`
  hoje no fluxo de `serviceorder` é para filtrar quem recebe notificação de nova Service Order
  (`technician.status() != TechnicianStatus.INACTIVE`), não para gatear atribuição.
- `ServiceExecution` (`serviceorder/domain/model/ServiceExecution.java:14-21`) não carrega nenhum campo de
  especialidade/categoria exigida — a validação de especialidade depende de uma fonte de dado que ainda
  não existe no modelo (`ServiceCatalog` é hoje só um placeholder documentado, sem aggregate implementado
  — ver `docs/Architecture.md`, "ServiceCatalog: nome e Money como preço-base").

## Impacto se não for pago

- Um Technician pode ser atribuído a uma execução fora de sua especialidade, sem nenhum aviso do sistema —
  o controle depende inteiramente do processo manual da oficina.
- Um Technician `BUSY`/indisponível pode ser atribuído a uma nova execução sem nenhum sinal, permitindo
  sobrecarga não intencional.
- `hasSpecialty(...)` permanece código morto (implementado, nunca chamado), o que confunde qualquer
  leitura futura do domínio sobre se essa regra já está em vigor.

## Opções de encaminhamento

### Opção A: Implementar a validação agora

- Exigiria antes modelar de onde vem a especialidade exigida por um serviço (`ServiceCatalog` ou
  equivalente), hoje um placeholder sem aggregate implementado — o `AGENTS.md` proíbe inventar essa
  entidade sem uma especificação aprovada. Portanto esta opção não está disponível como ajuste pontual;
  exigiria abrir uma feature nova sob o gate de SDD do `AGENTS.md`, cobrindo tanto o campo de especialidade
  no serviço quanto a regra de disponibilidade na atribuição.

### Opção B: Manter como dívida documentada (recomendada pelo time)

- Não implementar agora. Manter `AssignTechnicianUseCase`/`AssignDiagnosisAssigneeUseCase` validando
  apenas existência, como o time já decidiu ao ratificar AD-006.
- **Prós:** não bloqueia o MVP; a decisão já foi tomada explicitamente pelo time, não é uma omissão
  silenciosa.
- **Contras:** a lacuna persiste até ser retomada.

## Recomendação

Opção B — decisão já tomada pelo time ao ratificar AD-006 em 2026-08-23. Este documento existe para que a
lacuna fique explícita e rastreável, não para propor mudança de rumo. Quando/se o time quiser retomar,
depende de duas coisas: (1) uma fonte de dado para a especialidade exigida por um serviço (provavelmente
via uma feature de `ServiceCatalog`), e (2) uma regra explícita de disponibilidade (bloquear atribuição a
Technician `BUSY`/`INACTIVE`, ou apenas avisar) — ambas devem seguir o gate de SDD normal do `AGENTS.md`.

## Custo de não decidir agora

O trabalho pode continuar sem retomar esta dívida — nenhum caso de uso está bloqueado; a atribuição por
existência já é o comportamento aceito pelo time. Nenhuma suposição temporária adicional é necessária além
de não expandir mais esse fluxo assumindo que a validação de especialidade/disponibilidade já existe.

---

**Last Updated:** 2026-08-23  
**Status:** Open

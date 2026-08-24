# TD 001: Technician modelado dentro de Service Lifecycle em vez de Registration

**Status:** Open  
**Date:** 2026-08-23  
**Reported by:** Santiago Silvestre  
**Affected areas:** `servicelifecycle` (sub-pacotes `technician` e `serviceorder`), `registration`  
**Related decisions:** AD-006 (`docs/Architecture-Decisions.md`, "Team Decision Required")

---

## Contexto

`Technician` foi implementado como agregado rico (`nome`, `especialidades`, `status`/disponibilidade,
CRUD completo via `/api/technicians`) dentro do pacote `servicelifecycle.technician`
(`src/main/java/.../servicelifecycle/technician/`). A escolha original fez sentido operacionalmente: o
Épico 3 (execução/tracking) foi o primeiro a precisar de Technician (atribuição de execuções), então o
agregado nasceu fisicamente ao lado de quem o consumia primeiro.

`AD-006` já registra que essa colocação está em aberto — mas o eixo discutido lá é outro: se Technician
deve continuar como agregado rico (Option A, o que o código já implementa) ou ser reduzido a um ator de
identidade autenticada referenciado só por ID (Option B), conforme uma leitura mais recente do Miro. Esta
dívida técnica assume que o time mantém a Option A de AD-006 (Technician como agregado com nome,
especialidades e status — não há indicação de que isso vá mudar) e levanta um eixo diferente e
independente: **dado que Technician é um agregado rico, ele está no bounded context certo?**

## A dívida

`registration` já é o bounded context dos agregados de cadastro/master data do domínio: `Customer`,
`Vehicle`, e o placeholder documentado para `ServiceCatalog`. `Technician` tem exatamente esse perfil —
dados cadastrais de um recurso da oficina (nome, especialidades, disponibilidade), consultado por
identidade estável a partir de outros fluxos — mas vive em `servicelifecycle`, o bounded context de
execução/workflow (Service Order, Estimate, Diagnosis, Service Execution).

Como consequência dessa colocação, o acoplamento entre `serviceorder` (o lado de workflow de
`servicelifecycle`) e `technician` hoje é direto, não mediado por porta/ACL: quatro use cases de
`serviceorder` importam e injetam `TechnicianRepository`/`Technician`/`TechnicianStatus` do domínio de
`technician` diretamente. Isso é permitido hoje porque `technician` e `serviceorder` são dois pacotes
dentro do **mesmo** módulo Spring Modulith (`servicelifecycle`), então `ModuleStructureTest` não o
sinaliza. Mas é exatamente o tipo de acoplamento que o próprio `AGENTS.md` proíbe entre módulos
diferentes ("Do not import another module's internal packages. Communicate through public APIs, stable
IDs, domain events or a consumer-owned port and adapter") — a única razão para esse acoplamento não violar
a regra hoje é a fronteira de módulo estar desenhada do lado "errado".

## Evidência

- `src/main/java/.../servicelifecycle/technician/package-info.java`: `Technician capabilities used by the
  Service Lifecycle bounded context.` — a própria documentação do pacote já descreve Technician como algo
  "usado por" Service Lifecycle, não como parte do seu domínio de workflow.
- Acoplamento direto (repositório/domínio, não porta) de `serviceorder` em `technician`:
  - `serviceorder/application/usecase/CreateServiceOrderUseCase.java:11-13` — importa `Technician`,
    `TechnicianStatus` e `TechnicianRepository`.
  - `serviceorder/application/usecase/AssignTechnicianUseCase.java:11` — importa `TechnicianRepository`.
  - `serviceorder/application/usecase/AssignDiagnosisAssigneeUseCase.java:8` — importa
    `TechnicianRepository`.
  - `serviceorder/application/usecase/PerformDiagnosisUseCase.java:9` — importa `TechnicianRepository`.
- `registration/package-info.java` e `servicelifecycle/package-info.java` confirmam que só existem três
  módulos Spring Modulith reais (`registration`, `servicelifecycle`, `stockprocurement`) — `technician` e
  `serviceorder` são sub-pacotes internos do mesmo módulo, então o compilador/`ModuleStructureTest` não
  enxerga esse acoplamento como cruzamento de fronteira, embora conceitualmente seja um.
- `AGENTS.md` — "Bounded contexts" já descreve `registration` como "Customer, Vehicle and Service
  Catalog" (dados de cadastro) e `servicelifecycle` como "Service Order, Estimate and supporting
  Technician capabilities" — a própria redação ("*supporting* Technician capabilities") trata Technician
  como um apêndice de Service Lifecycle, não como um cidadão de primeira classe de nenhum dos dois
  contextos.

## Impacto se não for pago

- O acoplamento direto entre `serviceorder` e `technician` tende a crescer (mais use cases de execução
  precisando de dados de Technician) enquanto continua invisível para `ModuleStructureTest` — o custo de
  eventualmente formalizar a fronteira sobe com cada novo ponto de acoplamento.
- Se o time futuramente quiser reaproveitar Technician fora do fluxo de execução (ex.: autenticação,
  agenda/disponibilidade consultada por outro contexto, relatórios administrativos independentes de
  Service Order), a modelagem atual força esse consumidor a depender de `servicelifecycle` inteiro para
  algo que é, em essência, dado de cadastro.
- Mantém uma inconsistência entre a intenção documentada (`registration` = cadastro) e a implementação
  real, o que confunde qualquer leitura futura da arquitetura (humana ou de agente) que tente usar
  `AGENTS.md`/`PROJECT-STRUCTURE.md` como fonte de verdade.

## Opções de encaminhamento

### Opção A: Mover Technician para `registration` como seu próprio módulo/agregado

- Criar `registration.technician` como sub-pacote (mesmo padrão de `customer`/`vehicle`), movendo
  domínio, aplicação, persistência e o controller de `/api/technicians`.
- Os quatro use cases de `serviceorder` passam a consumir Technician através de uma porta consumidora
  (mesmo padrão já usado para `stockprocurement` em `serviceorder`, ex. `StockReservationApi`), em vez de
  importar `TechnicianRepository` diretamente.
- **Esforço:** médio — não muda schema de banco (mesma tabela, só pacote Java), mas toca 4 use cases, os
  testes desses use cases, `ModuleStructureTest`, e a documentação (`AGENTS.md`, `PROJECT-STRUCTURE.md`,
  `docs/Architecture.md`, AD-006).
- **Prós:** alinha a fronteira física com a intenção documentada; formaliza o acoplamento como porta,
  tornando-o visível para `ModuleStructureTest`; abre caminho para Technician ser consumido por outros
  contextos (auth, relatórios) sem depender de `servicelifecycle`.
- **Contras:** é uma mudança de fronteira de módulo, não uma refatoração local; exige revisão de todos os
  pontos de consumo e novos testes de fronteira; se o time depois resolver AD-006 pela Option B
  (Technician como ator só-ID), parte deste trabalho perde valor.

### Opção B: Manter Technician em `servicelifecycle`, mas formalizar o acoplamento como porta interna

- Sem mover pacotes, introduzir uma interface (`TechnicianLookupPort` ou similar) implementada por um
  adapter que envolve `TechnicianRepository`, e fazer os 4 use cases de `serviceorder` dependerem da porta
  em vez do repositório de domínio diretamente.
- **Esforço:** baixo — não muda módulo Spring Modulith nem documentação de bounded context, só introduz
  uma camada de indireção dentro do mesmo módulo.
- **Prós:** reduz o acoplamento imediato sem o risco de uma mudança de fronteira maior; reversível a baixo
  custo.
- **Contras:** não resolve a inconsistência de fundo (Technician continua fisicamente em
  `servicelifecycle` apesar de ser dado de cadastro); a porta fica "por convenção", sem
  `ModuleStructureTest` capaz de garanti-la, já que ainda é o mesmo módulo Spring Modulith.

### Opção C: Não pagar agora — aguardar resolução de AD-006

- Adiar qualquer mudança de fronteira até o time decidir se Technician continua agregado rico (Option A
  de AD-006) ou vira ator só-ID (Option B de AD-006); mover pacote antes disso arrisca refazer o trabalho.
- **Esforço:** zero agora.
- **Prós:** evita retrabalho se AD-006 for resolvida pela Option B, caso em que "para qual bounded
  context mover Technician" deixa de ser a pergunta certa.
- **Contras:** mantém a dívida acumulando enquanto AD-006 não é discutida; o time já tem `AD-006` como
  `Team Decision Required` sem data prevista de resolução.

## Recomendação

Esta é uma mudança de fronteira de bounded context — mesmo tipo de decisão que já está registrada como
"Whole-team decision" em AD-006. Não deve ser decidida ou implementada unilateralmente por um único
contribuidor. Recomendo:

1. Levar esta dívida à mesma discussão de time que resolverá AD-006, já que as duas perguntas
   ("Technician é agregado ou ator?" e "se é agregado, onde ele mora?") são relacionadas mas distintas, e
   a resposta à primeira muda o valor da segunda.
2. Se o time confirmar Technician como agregado rico (mantendo a Option A de AD-006), a Opção A desta
   dívida (mover para `registration`) é a que melhor alinha código e documentação — não é urgente para o
   MVP, mas deveria entrar como uma feature normal sob o gate de SDD do `AGENTS.md` antes que mais use
   cases se acoplem diretamente a `technician`.
3. Se o time preferir não mover o pacote agora, a Opção B (formalizar como porta interna) é uma mitigação
   de baixo custo que pelo menos impede o acoplamento direto de crescer sem se tornar uma decisão maior de
   fronteira.

## Custo de não decidir agora

O trabalho pode continuar sem resolver esta dívida — nenhum caso de uso está bloqueado. A suposição
temporária a respeitar: não adicionar novos consumidores de `TechnicianRepository`/`Technician` fora de
`serviceorder` sem passar por uma porta (mesmo que informal), para não espalhar ainda mais o acoplamento
direto antes de uma decisão de time.

---

**Last Updated:** 2026-08-23  
**Status:** Open

# ADR 005: Inter-Module Integration Contract — Java Ports vs REST Interno

**Status:** Accepted  
**Date:** 2026-08-25  
**Deciders:** Time de Desenvolvimento   
**Affected By:** Todos os módulos (`registration`, `servicelifecycle`, `stockprocurement`, `identity`)

---

## Context

O sistema é um monólito modular (Spring Modulith) com quatro bounded contexts:
`registration`, `servicelifecycle`, `stockprocurement` e `identity`. `AGENTS.md` proíbe um módulo de
importar pacotes internos de outro — a comunicação deve passar por APIs públicas, IDs estáveis, eventos
de domínio ou um port/adapter de propriedade do consumidor. O que ainda não está formalmente decidido é
**qual mecanismo concreto** implementa isso para consultas síncronas entre módulos.

Existe evidência conflitante sobre esse mecanismo:

- O Context Map do board Miro descreve `Registrations` expondo um OHS (Open Host Service) via **REST**.
- O RFC aceito internamente descreve um port de propriedade do consumidor, implementado por um adapter
  Java direto no mesmo processo.
- `PROJECT-STRUCTURE.md` fala genericamente em "port + adapter se necessário", sem decidir o transporte.

Essa é a decisão registrada como **AD-011** em `docs/Architecture-Decisions.md` ("Choose in-process module
integration contracts"). O código já adotava de fato um padrão consistente, descrito abaixo, antes de esta
ADR formalizar a ratificação pelo time (ver Decision); a atualização do status de AD-011 de
`Team Decision Required` para `Resolved` é registrada como item pendente no Approval Checklist desta ADR.

## Problem Statement

### Requisitos

- Módulos não podem importar pacotes internos uns dos outros (regra já vigente em `AGENTS.md`).
- Consultas síncronas entre módulos (ex.: `servicelifecycle` verificando elegibilidade de um serviço do
  catálogo de `registration`) precisam de um contrato estável e testável.
- Reações a mudanças de estado entre módulos (ex.: notificar após geração de um orçamento) já usam eventos
  de domínio Spring e não são objeto de disputa nesta ADR.

### Restrições do projeto

- MVP em um único processo/deploy; não há extração de serviços prevista no curto prazo.
- Equipe pequena; complexidade operacional extra (rede, serialização, contratos versionados) não tem
  retorno dentro do mesmo processo.
- Esta decisão é válida enquanto o sistema for um monólito modular. Uma eventual migração para
  microsserviços é um cenário futuro e não decidido — não é escopo desta ADR planejar essa migração, mas o
  contrato escolhido aqui precisa deixar claro o que muda se ela ocorrer (ver Consequências).

### Evidência já implementada no código

O padrão abaixo já existe e está em uso em produção de código, sem ter sido formalmente ratificado:

- `registration.servicecatalog` expõe uma interface pública em `application/api`
  (`CatalogServiceAvailabilityApi`).
- `servicelifecycle.serviceorder` declara portas próprias em `application/port`
  (`CatalogServiceEligibilityPort`, `VehicleEligibilityPort`) e as implementa em
  `infrastructure/registration` (`RegistrationCatalogServiceEligibilityAdapter`,
  `RegistrationVehicleEligibilityAdapter`), injetando e chamando a API pública do módulo dono como bean
  Spring — sem HTTP, sem serialização, na mesma transação.
- Reações assíncronas usam `ApplicationEventPublisher`/`@EventListener` (ex.:
  `EstimateGeneratedNotificationListener`).

## Considered Options

### Option 1: Portas Java in-process + eventos de domínio para reações ✅ SELECIONADO

O módulo consumidor declara uma interface própria (`application/port`) e um adapter na sua camada de
infraestrutura chama, dentro do mesmo processo, uma interface pública exposta pelo módulo dono
(`application/api`). Reações assíncronas usam eventos de domínio Spring.

#### Vantagens ✅

- Encaixa no modelo de monólito modular; nenhuma chamada de rede dentro do mesmo processo.
- O consumidor controla a própria porta (inversão de dependência), podendo trocar o adapter sem afetar o
  módulo dono.
- Já é o padrão implementado e testado em `servicelifecycle` → `registration`.
- Sem overhead de serialização/desserialização nem falhas de rede simuladas dentro de um único processo.

#### Desvantagens ❌

- Exige disciplina para manter interfaces públicas (`application/api`) e portas (`application/port`)
  claramente nomeadas e minimamente expostas — sem isso, o limite lógico entre módulos enfraquece mesmo
  sem import direto de pacote interno.
- Não se parece com uma futura extração de serviço; migrar para HTTP/mensageria no futuro exige reescrever
  os adapters (mas não os casos de uso, que dependem apenas da porta).

### Option 2: REST interno entre módulos

Cada módulo expõe endpoints HTTP próprios e outros módulos os consomem via cliente HTTP, mesmo estando no
mesmo processo/deploy.

#### Vantagens ✅

- Se parece mais com uma futura extração de serviço (cada módulo já fala HTTP).

#### Desvantagens ❌

- Overhead de rede e serialização desnecessário dentro do mesmo processo.
- Introduz semântica de falha de rede (timeout, retry, circuit breaker) onde uma chamada de método já
  seria suficiente e transacional.
- Nenhum uso hoje no código — adotar essa opção exigiria reescrever os dois adapters já existentes.
- Contradiz o RFC já aceito internamente, que descreve porta consumidora + adapter Java direto.

## Decision

O time ratificou a **Option 1**: módulos consumidores declaram portas próprias (`application/port`) e as
implementam com adapters que chamam, in-process, as interfaces públicas (`application/api`) expostas
pelos módulos donos; reações assíncronas continuam usando eventos de domínio Spring
(`ApplicationEventPublisher`/`@EventListener`). Esse é o padrão já implementado em
`servicelifecycle.serviceorder` → `registration.servicecatalog` e em `registration` → `Vehicle`, e deve ser
seguido por qualquer integração síncrona futura entre módulos (ex.: `servicelifecycle` ↔
`stockprocurement`).

Nenhum endpoint REST interno entre módulos deve ser criado para esse propósito enquanto o sistema
permanecer um monólito modular.

Esta decisão é escopada ao monólito modular atual. Ela não decide o mecanismo de integração para um
cenário futuro de extração de módulos em microsserviços — se e quando essa extração for decidida, o
Context Map do board Miro (que já descreve `Registrations` expondo um OHS via REST) passa a ser a
referência correta, e a porta consumidora troca seu adapter in-process por um cliente HTTP contra esse
OHS, sem alterar o caso de uso ou a porta em si. Essa troca de mecanismo, se necessária, deve ser registrada
em uma ADR própria no momento em que a extração for de fato decidida, não antecipada aqui.

## Consequências

### Positivas ✅

- Formaliza um padrão já em uso, eliminando a ambiguidade que hoje permite que uma próxima integração
  escolha REST interno por engano.
- Mantém o MVP simples: sem overhead de rede, sem contratos HTTP adicionais para manter dentro do mesmo
  deploy.
- Preserva a inversão de dependência: o consumidor controla sua porta e pode trocar o adapter sem afetar o
  módulo dono.

### Negativas ❌

- O Context Map do Miro descreve `Registrations` expondo REST/OHS; para o estado atual (monólito modular)
  isso precisa ser marcado como o mecanismo de um cenário futuro de extração de serviço, não como o
  contrato vigente hoje — do contrário o board continua lido como se REST interno já fosse a decisão
  corrente.
- Uma futura extração de um módulo para um serviço separado exigirá reescrever os adapters de
  infraestrutura (não os casos de uso/portas, que já dependem apenas da abstração), adotando então o OHS
  via REST já descrito no Context Map.

### Mitigação de Riscos

- Atualizar o Context Map do board Miro para deixar explícito que o OHS/REST de `Registrations` descreve o
  mecanismo de uma eventual extração para microsserviços, e não o contrato vigente entre módulos do
  monólito — que é a porta consumidora + API pública in-process + eventos, ratificada nesta ADR.
- Exigir que toda nova interface pública em `application/api` seja mínima e intencional — não expor
  métodos além do que o consumidor realmente precisa. Isso também reduz o custo de uma eventual troca para
  OHS, já que a superfície pública a migrar já é enxuta.
- Ao planejar uma eventual extração de serviço, tratar a troca do adapter (porta → cliente HTTP contra o
  OHS, conforme o Context Map) como um trabalho isolado, já que a porta no consumidor não muda, e abrir uma
  ADR específica para essa migração no momento em que ela for decidida.

---

## Related ADRs

- Nenhuma ADR relacionada publicada até o momento. Referenciar aqui caso uma ADR futura decida extrair um
  módulo para um serviço separado (a troca de adapter descrita na Mitigação de Riscos).

## References

- `docs/Architecture-Decisions.md` — seção AD-011 ("Choose in-process module integration contracts"),
  registro original desta decisão, incluindo a evidência conflitante do Context Map.
- `AGENTS.md` — regra de fronteira entre módulos ("Do not import another module's internal packages...").
- `src/main/java/.../registration/servicecatalog/application/api/CatalogServiceAvailabilityApi.java`
- `src/main/java/.../servicelifecycle/serviceorder/application/port/CatalogServiceEligibilityPort.java`
- `src/main/java/.../servicelifecycle/serviceorder/infrastructure/registration/RegistrationCatalogServiceEligibilityAdapter.java`

## Approval Checklist

- [x] Time ratifica a Option 1 (portas Java in-process + eventos) como o contrato oficial para toda
      integração síncrona entre módulos **enquanto o sistema for um monólito modular**. Uma eventual
      extração para microsserviços poderá adotar OHS (REST), conforme o Context Map do Miro, mas isso fica
      para uma ADR futura no momento em que a extração for de fato decidida.
- [ ] Context Map do board Miro atualizado para marcar o OHS/REST de `Registrations` como o mecanismo de
      uma eventual extração futura para microsserviços, não como o contrato vigente entre módulos.
- [x] AD-011 em `docs/Architecture-Decisions.md` atualizada de `Team Decision Required` para `Resolved`,
      apontando para esta ADR.

---

**Last Updated:** 2026-08-25
**Decision Maker:** Time de Desenvolvimento
**Status:** Accepted — Option 1 (portas Java in-process + eventos) ratificada para o monólito modular atual;
ver AD-011 em `docs/Architecture-Decisions.md` para o registro da decisão e a ADR futura de extração para
microsserviços caso essa migração seja decidida.

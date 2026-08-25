# ADR 005: Inter-Module Integration Contract — Java Ports vs REST Interno

**Status:** Proposed  
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
integration contracts"), com status `Team Decision Required`. Enquanto não é ratificada, o código já
adotou de fato um padrão consistente, descrito abaixo, sem que isso tenha sido formalmente aprovado pelo
time.

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

Será adotada a **Option 1**: módulos consumidores declaram portas próprias (`application/port`) e as
implementam com adapters que chamam, in-process, as interfaces públicas (`application/api`) expostas
pelos módulos donos; reações assíncronas continuam usando eventos de domínio Spring
(`ApplicationEventPublisher`/`@EventListener`). Esse é o padrão já implementado em
`servicelifecycle.serviceorder` → `registration.servicecatalog` e em `registration` → `Vehicle`, e deve ser
seguido por qualquer integração síncrona futura entre módulos (ex.: `servicelifecycle` ↔
`stockprocurement`).

Nenhum endpoint REST interno entre módulos deve ser criado para esse propósito.

## Consequências

### Positivas ✅

- Formaliza um padrão já em uso, eliminando a ambiguidade que hoje permite que uma próxima integração
  escolha REST interno por engano.
- Mantém o MVP simples: sem overhead de rede, sem contratos HTTP adicionais para manter dentro do mesmo
  deploy.
- Preserva a inversão de dependência: o consumidor controla sua porta e pode trocar o adapter sem afetar o
  módulo dono.

### Negativas ❌

- O Context Map do Miro, que hoje descreve `Registrations` expondo REST/OHS, passa a contradizer a decisão
  ratificada e precisa ser corrigido.
- Uma futura extração de um módulo para um serviço separado exigirá reescrever os adapters de
  infraestrutura (não os casos de uso/portas, que já dependem apenas da abstração).

### Mitigação de Riscos

- Atualizar o Context Map do board Miro para descrever o mecanismo real (porta consumidora + API pública
  in-process + eventos), evitando que o board continue divergente do código.
- Exigir que toda nova interface pública em `application/api` seja mínima e intencional — não expor
  métodos além do que o consumidor realmente precisa.
- Ao planejar uma eventual extração de serviço, tratar a troca do adapter (porta → cliente HTTP/mensageria)
  como um trabalho isolado, já que a porta no consumidor não muda.

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

- [ ] Time ratifica a Option 1 (portas Java in-process + eventos) como o contrato oficial para toda
      integração síncrona entre módulos.
- [ ] Context Map do board Miro atualizado para não descrever mais REST/OHS entre `registration` e
      `servicelifecycle`.
- [ ] AD-011 em `docs/Architecture-Decisions.md` atualizada de `Team Decision Required` para `Resolved`,
      apontando para esta ADR.

---

**Last Updated:** 2026-08-25
**Decision Maker:** Time de Desenvolvimento (pendente de ratificação)
**Status:** Proposed (aguardando ratificação do time; ver AD-011 em `docs/Architecture-Decisions.md`)

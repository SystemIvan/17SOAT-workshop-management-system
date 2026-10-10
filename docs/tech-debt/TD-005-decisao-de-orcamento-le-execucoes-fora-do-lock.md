# TD 005: Decisão de orçamento lê as execuções fora do lock da OS

**Status:** Open  
**Date:** 2026-10-10  
**Reported by:** Santiago Silvestre (achado R3 do `architecture-reviewer` na technical-spec da RF53)  
**Affected areas:** `servicelifecycle` (`estimate`, `serviceorder`, camada de persistência)  
**Related decisions:** AD-010 (`statusSnapshot` recalculado em comando); `decide-estimate-lines`; RF40
(`external-status-update`); ADR-008 (proposto, RF53)

---

## Contexto

`DecideEstimateLinesUseCase` (canal interno JWT e RF41) e `ApplyExternalStatusUpdateUseCase` (RF40) protegem a decisão
concorrente com `ServiceOrderRepository.findByIdForUpdate`. A intenção é que duas decisões simultâneas sobre o mesmo
orçamento resultem em uma aplicada e outra rejeitada com `409`.

## A dívida

O lock pessimista pega só a linha de `service_orders`. As execuções são carregadas depois, pela coleção
`@OneToMany` de `ServiceOrderJpaEntity`, numa leitura comum sem lock. Antes do lock, os dois casos de uso já fizeram
uma leitura comum na mesma transação: o orçamento, em `DecideEstimateLinesUseCase`, e a OS, em
`ApplyExternalStatusUpdateUseCase`.

No MySQL com `REPEATABLE READ` (padrão do InnoDB), o snapshot da transação é fixado na primeira leitura comum. Se a
segunda transação fixou o snapshot antes do commit da primeira, ela obtém o lock depois do commit, mas lê as
execuções do snapshot antigo e as vê ainda `PENDING`. A consequência provável é a segunda decisão também ser
aplicada sobre o estado antigo e sobrescrever a primeira, ou falhar com erro de persistência. Isso não foi
reproduzido: é uma análise do código e da semântica do InnoDB.

O H2 usado nos testes trabalha em `READ COMMITTED` e não reproduz esse comportamento. Por isso os testes atuais não
detectam o problema.

## Evidência

- `serviceorder/infrastructure/persistence/ServiceOrderJpaRepository.java`: `findByIdForUpdate` tem
  `@Lock(PESSIMISTIC_WRITE)` sobre `select serviceOrder from ServiceOrderJpaEntity ...`, sem fetch das execuções.
- `serviceorder/infrastructure/persistence/ServiceOrderJpaEntity.java`: `@OneToMany(mappedBy = "serviceOrder", ...)`,
  com carregamento lazy padrão.
- `estimate/application/usecase/DecideEstimateLinesUseCase.java`: `estimateRepository.findById(...)` vem antes de
  `findByIdForUpdate(...)`.
- `estimate/application/usecase/ApplyExternalStatusUpdateUseCase.java`: `serviceOrderRepository.findById(...)` vem
  antes da delegação, que só então chama `findByIdForUpdate(...)`.
- `src/test/resources/application.properties`: H2 (`MODE=MySQL`), sem MySQL real nos testes. O projeto não usa
  Testcontainers.

## Impacto se não for pago

Duplo clique ou decisões simultâneas por canais diferentes podem gerar decisão dupla ou inconsistente: linhas
decididas duas vezes, reserva de estoque repetida, orçamento fechado com estado divergente. O risco é baixo no uso
interno atual, mas aumenta com a RF53, em que o cliente clica em links de e-mail.

## Opções de encaminhamento

### Opção A: travar a OS antes de qualquer leitura comum

Reordenar os casos de uso para que `findByIdForUpdate` seja a primeira consulta da transação. Assim o snapshot é
fixado depois do lock. Esforço pequeno, mas a regra é implícita e frágil: qualquer leitura nova antes do lock
reintroduz o problema.

### Opção B: carregar as execuções na própria consulta travada

`findByIdForUpdate` passa a buscar as execuções com `join fetch` sob o mesmo lock, ou as execuções são travadas
também. A leitura travada sempre vê a versão mais recente. Esforço pequeno a médio, e a solução é mais robusta.

### Opção C: versionamento otimista

`@Version` na OS ou nas execuções, com conflito traduzido para `409`. Esforço médio. Exige migration e muda o
comportamento de erro.

## Recomendação

Opção B, com um teste de concorrência contra MySQL real. Esse teste exige decidir se Testcontainers entra como
dependência de teste. A RF53 já adota a ordem da Opção A no próprio caso de uso, sem alterar os casos de uso
existentes, para não ampliar o escopo daquela feature.

## Custo de não decidir agora

O sistema continua funcionando em uso sequencial. Enquanto a dívida existir, não assumir que `findByIdForUpdate`
garante leitura atualizada das execuções. Código novo que dependa disso deve travar a OS como primeira consulta da
transação (Opção A), como faz a RF53.

---

**Last Updated:** 2026-10-10  
**Status:** Open

# ADR 004: Notifications Are an Outbound Capability

**Status:** Accepted  
**Date:** 2026-08-11  
**Deciders:** Time de Desenvolvimento (5 pessoas)  
**Affected By:** servicelifecycle (estimate, serviceorder), futuros consumidores de notificação em stockprocurement

---

## Context

O fluxo de domínio contém políticas que notificam clientes, técnicos e responsáveis por estoque. O
modelo atual não dá às notificações um ciclo de vida independente, vocabulário próprio ou estado
persistido.

## Decision

Notifications não é um bounded context no MVP atual. Um módulo que precisa de entrega define um port de
saída (outbound) em sua camada de aplicação, e um adapter de infraestrutura implementa o canal escolhido.
Nenhuma abstração compartilhada de notificação não utilizada será criada por antecipação.

Notifications deve ser reconsiderado como bounded context quando passar a possuir regras de negócio ou
estado próprios, como templates, preferências de usuário, seleção de canal, tentativas de entrega,
retries ou histórico de entregas.

## Consequências

### Positivas ✅

- Código de domínio/aplicação não depende diretamente de provedores de e-mail, SMS ou mensageria.
- Nenhuma abstração compartilhada é criada antes de haver um caso de uso real que a justifique.

### Negativas ❌

- Falhas de entrega são tratadas conforme o caso de uso consumidor, até que exista um modelo de
  notificação dedicado — sem retries ou histórico centralizados.
- Um futuro contexto de notificação exige uma nova ADR e uma atualização explícita do context map.

---

## Related ADRs

- **ADR-002:** Real-Time Updates Strategy — referencia esta ADR ao justificar por que a estratégia de
  tracking não depende de um contexto de notificações dedicado.
- **ADR-003:** Authentication Strategy — referencia esta ADR pela mesma razão de escopo do MVP.

---

**Last Updated:** 2026-08-11  
**Decision Maker:** Time de Desenvolvimento  
**Status:** Accepted

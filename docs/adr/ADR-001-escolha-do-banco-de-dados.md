# ADR 001: Escolha do Banco de Dados (MySQL)

**Status:** Accepted  
**Date:** 2026-08-03  
**Deciders:** Time de Desenvolvimento (5 pessoas)  
**Affected By:** Toda a camada de persistência (registration, servicelifecycle, stockprocurement)

---

## Context

O domínio do sistema é fortemente relacional: uma Ordem de Serviço (OS) está associada a um cliente
(identificado por CPF/CNPJ), a um veículo (placa, marca, modelo, ano), a um conjunto de serviços
solicitados e a peças/insumos com controle de estoque. Essas entidades possuem relacionamentos bem
definidos (1:N e N:N) e o fluxo de negócio depende de consistência forte entre elas — por exemplo, o
orçamento é calculado a partir dos serviços e peças vinculados à OS, e o controle de estoque de peças
precisa refletir com exatidão as baixas realizadas em cada atendimento.

Além disso, o sistema precisa garantir:

- Integridade referencial entre cliente, veículo, OS, serviços e peças;
- Transações atômicas em operações críticas (ex.: criação de OS com baixa de estoque);
- Persistência de histórico de clientes e veículos ao longo do tempo;

O projeto consiste no back-end (MVP) de um Sistema Integrado de Atendimento e Execução de Serviços para
uma oficina mecânica de médio porte, desenvolvido em Java com Spring Boot, seguindo uma arquitetura
monolítica em camadas orientada por Domain-Driven Design (DDD).

- Execução simples em ambiente local via Docker/docker-compose, conforme exigido pelos requisitos
  técnicos do desafio.

Era necessário escolher um banco de dados que suportasse esses requisitos com maturidade, boa integração
com o ecossistema Java/Spring, e que fosse viável para uma equipe com prazo curto de entrega (MVP).

## Decision

Será utilizado o **MySQL** como sistema de gerenciamento de banco de dados (SGBD) do projeto.

A escolha se baseia principalmente em dois fatores:

1. **Natureza relacional e transacional do domínio.** O modelo de dados da oficina (cliente, veículo,
   OS, serviços, peças/estoque) exige integridade referencial e suporte a transações ACID para garantir
   consistência entre orçamento, execução de serviços e baixa de estoque. O MySQL, com o mecanismo de
   armazenamento InnoDB, atende esses requisitos de forma nativa, oferecendo chaves estrangeiras,
   constraints e transações atômicas.
2. **Familiaridade da equipe.** A equipe já possui experiência prática com MySQL, o que reduz o risco
   técnico e a curva de aprendizado durante o desenvolvimento do MVP, permitindo focar o esforço na
   modelagem de domínio (DDD) e na qualidade do código dentro do prazo estabelecido para a fase.

De forma complementar, o MySQL também se integra bem ao restante da stack definida para o projeto:

- Suporte maduro ao Spring Data JPA / Hibernate para mapeamento objeto-relacional;
- Imagem oficial disponível no Docker Hub, facilitando a orquestração via `docker-compose.yml` exigida
  nos requisitos técnicos;
- Ferramentas consolidadas de migração de schema (ex.: Flyway, Liquibase) compatíveis com o ecossistema
  Spring Boot;
- Licenciamento open source (GPL), sem custo de uso para o contexto do MVP.

## Consequências

### Positivas ✅

- Menor risco de atraso no cronograma, já que a equipe não precisa aprender uma nova tecnologia de
  persistência sob prazo apertado;
- Garantia de integridade e consistência transacional nos fluxos críticos (criação de OS, controle de
  estoque, aprovação de orçamento);
- Boa documentação e grande comunidade, facilitando troubleshooting durante o desenvolvimento;
- Compatibilidade direta com os requisitos técnicos de containerização (Dockerfile e docker-compose).

### Negativas ❌

- Nenhuma alternativa (ex.: PostgreSQL, SQL Server, bancos NoSQL) foi avaliada formalmente nesta fase; a
  decisão prioriza velocidade de entrega e familiaridade da equipe em detrimento de uma análise
  comparativa mais ampla;
- Caso o sistema evolua para cenários que exijam recursos avançados não tão fortes no MySQL (ex.: tipos
  de dados geoespaciais mais robustos, JSON avançado, particionamento em larga escala), uma reavaliação
  do SGBD pode ser necessária em ADRs futuras;
- Escalabilidade horizontal do MySQL em cenários de altíssimo volume é mais limitada do que em soluções
  especializadas, o que não é um problema para o escopo atual de MVP, mas deve ser reconsiderado se o
  sistema crescer significativamente.

---

## Related ADRs

- N/A — nenhuma outra ADR depende diretamente desta escolha até o momento.

---

**Last Updated:** 2026-08-03  
**Decision Maker:** Time de Desenvolvimento  
**Status:** Accepted

# ADR 008: Estratégia de ambientes — Kubernetes local com kind primeiro, AWS Academy (EKS + RDS) depois

**Status:** Accepted

**Date:** 2026-10-14

**Deciders:** Santiago Silvestre

**Affected By:** plataforma (containerização, `/k8s`, `/infra`, pipeline CI/CD); nenhum bounded context muda de
comportamento

---

## Context

A Fase 2 do Tech Challenge exige que a aplicação da Fase 1 rode em Kubernetes, com o cluster e o banco de dados
provisionados por Terraform, escalabilidade automática via HPA e uma pipeline CI/CD que faça build, testes, build da
imagem, deploy do banco e aplicação dos manifestos no cluster. O enunciado aceita cluster "local ou cloud".

Hoje a aplicação só roda via `docker-compose` (MySQL, Mailpit e WireMock). Não existem manifestos Kubernetes,
scripts Terraform nem etapa de deploy na pipeline (`.github/workflows/ci.yml` executa apenas `./mvnw clean verify`).

Restrições que tornam a decisão necessária agora:

- **Prazo:** entrega completa em 2026-10-27, com o grupo comparando as implementações individuais em 2026-10-26.
- **Implementação individual:** cada integrante implementa a solução completa na própria branch; a pipeline desta
  implementação roda em um fork pessoal do repositório, porque environments e secrets do repositório compartilhado
  exigem permissões que o autor não tem.
- **Nuvem disponível:** a única conta AWS disponível é um AWS Academy Learner Lab, com crédito limitado, credenciais
  temporárias que expiram a cada sessão e permissões de IAM restritas (uso da `LabRole` fornecida em vez de roles
  próprias). Os limites exatos (região, tipos de instância, duração da sessão) serão confirmados antes de qualquer
  provisionamento na AWS.
- **Custo:** um cluster EKS com node group, RDS e NAT Gateway consome crédito por hora enquanto existir.
- **Aprendizado:** o autor está aprendendo infraestrutura; um ciclo de erro e correção rápido e gratuito reduz o
  risco de consumir o crédito do Learner Lab com erros de configuração.

## Problem Statement

A decisão precisa:

- cumprir todos os requisitos de infraestrutura do enunciado (Deployment, Service, ConfigMap, Secret, HPA, Terraform
  para cluster e banco, CI/CD com deploy) em pelo menos um ambiente completo e demonstrável em vídeo;
- permitir uma pipeline que de fato faça deploy, apesar de um runner do GitHub Actions não alcançar um cluster
  rodando na máquina do desenvolvedor;
- manter a aplicação sem conhecimento do ambiente: banco, SMTP e segredos entram por variáveis de ambiente;
- não desperdiçar crédito do Learner Lab e garantir uma entrega mesmo que a AWS falhe;
- não interferir nas imagens, secrets e branches dos outros integrantes.

## Considered Options

### Option 1: Apenas Kubernetes local

Cluster local provisionado por Terraform, banco MySQL dentro do cluster e pipeline que cria um cluster efêmero
dentro do próprio job do GitHub Actions.

Vantagens:

- custo zero e ciclo de feedback rápido;
- atende ao enunciado, que aceita cluster local;
- nenhuma dependência de credenciais externas.

Desvantagens:

- não exercita conceitos de nuvem (rede, IAM, serviço gerenciado de banco);
- o deploy na pipeline acontece em um cluster descartável, não no ambiente que roda a demonstração.

### Option 2: Apenas AWS Academy (EKS + RDS)

Todo o desenvolvimento e a validação feitos diretamente na AWS.

Vantagens:

- ambiente mais próximo de produção real;
- RDS demonstra "Terraform para banco" com um serviço gerenciado.

Desvantagens:

- cada tentativa custa crédito e minutos de provisionamento (EKS e RDS levam dezenas de minutos para subir);
- credenciais temporárias interrompem o trabalho a cada sessão;
- um bloqueio do Learner Lab (permissão, cota, crédito) deixa a entrega sem nenhum ambiente funcional.

### Option 3: Local com kind primeiro, AWS Academy (EKS + RDS) depois ✅ SELECIONADO

Construir e validar tudo localmente com kind; só depois, reaproveitando a mesma imagem e os mesmos manifestos base,
provisionar o ambiente AWS.

Vantagens:

- o ambiente local completo é uma entrega garantida, independente da AWS;
- os erros de manifesto, probes e HPA são corrigidos de graça, antes de gastar crédito;
- a migração para a AWS troca apenas o que é específico do ambiente (cluster, banco, acesso externo), porque a
  aplicação já é configurada por variáveis de ambiente;
- exercita os conceitos de nuvem pedidos no aprendizado sem colocar a entrega em risco.

Desvantagens:

- mantém dois diretórios Terraform e dois conjuntos de ajustes de manifesto;
- o banco difere entre ambientes (MySQL no cluster versus RDS), então o comportamento de conexão precisa ser
  validado nos dois.

### Ferramenta de cluster local: kind, minikube ou k3d

As três rodam Kubernetes localmente. O kind foi escolhido porque roda os nodes como containers Docker (o mesmo Docker
já usado pelo `docker-compose`), tem provider Terraform e é a ferramenta mais comum para criar clusters efêmeros
dentro de jobs do GitHub Actions. Assim, o mesmo tipo de cluster serve para o desenvolvimento local e para a
pipeline. minikube e k3d são viáveis, mas não trariam ganho para estes requisitos.

## Decision

Será adotada a **Option 3**: a infraestrutura é construída primeiro em um cluster Kubernetes local criado com
**kind** e provisionado por Terraform, incluindo o MySQL também provisionado por Terraform, para que "Terraform para
banco" já seja cumprido no ambiente local. A pipeline faz o deploy em um cluster kind efêmero criado dentro do job.

Depois que o ambiente local estiver completo e demonstrado, o mesmo conjunto (imagem identificada pelo SHA do commit
e manifestos base) é levado ao **AWS Academy Learner Lab** com **EKS** (usando a `LabRole`) e **RDS MySQL**, com
state remoto do Terraform em S3. Apenas os ajustes por ambiente diferem entre local e AWS.

Os fatores decisivos são a entrega garantida pelo ambiente local e a economia de crédito, já que os erros acontecem
onde custam zero.

## Consequências

### Positivas ✅

- Existe um ambiente completo e demonstrável antes de qualquer gasto na AWS.
- Imagem e manifestos base são os mesmos nos dois ambientes; a aplicação não conhece o ambiente em que roda.
- O deploy da pipeline é real e verificável (smoke test) mesmo sem um cluster permanente.

### Negativas ❌

- Há duas configurações de infraestrutura para manter (local e AWS).
- O cluster usado pela pipeline local é descartável: ele prova que o deploy funciona, mas não é o ambiente da
  demonstração local.
- As credenciais temporárias do Learner Lab precisam ser atualizadas manualmente nos secrets do environment a cada
  sessão.
- Enquanto existirem, os recursos AWS consomem crédito; esquecer um `destroy` gasta o orçamento do lab.

### Mitigação de Riscos

- **Data-limite para a AWS:** se até 2026-10-24 o EKS não estiver servindo a API com o RDS, a entrega segue apenas
  com o ambiente local, que já terá vídeo gravado em 2026-10-22.
- Antes de cada `apply` na AWS é apresentada uma estimativa de consumo de crédito por hora; o procedimento de
  `destroy` ao fim de cada uso e o de renovação de credenciais por sessão ficam documentados no README.
- As restrições reais do Learner Lab são confirmadas antes de escrever o Terraform da AWS.
- Imagens publicadas com tag pelo SHA do commit e secrets em environment próprio evitam conflito com os outros
  integrantes.

## Related ADRs

- **ADR-001:** Escolha do Banco de Dados (MySQL). Esta decisão mantém o MySQL nos dois ambientes (no cluster
  localmente; RDS MySQL na AWS).
- **ADR-007:** Autenticação de sistemas externos por HMAC. O segredo HMAC passa a ser entregue à aplicação como
  Secret do Kubernetes, sem mudança na decisão.

## References

- Enunciado do Tech Challenge — Fase 2 (requisitos de Infraestrutura e CI/CD).
- kind: https://kind.sigs.k8s.io/
- Amazon EKS: https://docs.aws.amazon.com/eks/
- Amazon RDS for MySQL: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_MySQL.html

## Approval Checklist

- [x] Estratégia "local primeiro, AWS depois" aprovada por Santiago Silvestre em 2026-10-14.
- [x] Restrições do Learner Lab confirmadas antes do provisionamento na AWS (Santiago Silvestre, 2026-10-24, no
      CloudShell: `us-east-1`, `LabRole`, `t3.medium`, NAT, S3, EKS 1.34–1.37, RDS MySQL 8.4 `db.t3.micro`; sessão de
      4 h, US$ 50 de crédito). Evidências no CP8 do plano da infra.

---

**Last Updated:** 2026-10-14
**Decision Maker:** Santiago Silvestre
**Status:** Accepted — aprovada por Santiago Silvestre em 2026-10-14

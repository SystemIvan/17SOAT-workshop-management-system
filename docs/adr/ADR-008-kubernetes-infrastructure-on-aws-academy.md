# ADR 008: Infraestrutura Kubernetes na AWS Academy com Terraform e GitHub Actions

**Status:** Accepted

**Date:** 2026-10-26

**Deciders:** Leandro Nascimento

**Affected By:** plataforma (`k8s/`, `infra/`, `.github/workflows/`); nenhum bounded context

---

## Context

O enunciado da Fase 2 exige manifestos Kubernetes com HPA, scripts Terraform que provisionem o cluster (local ou na
nuvem) e o banco, e uma pipeline de CI/CD que faça build, testes, imagem Docker, deploy do banco e aplicação dos
manifestos no cluster. A feature `container-readiness` já deixou a aplicação pronta para orquestração: probes na porta
de gestão `8081`, imagem não-root com UID 10001 e encerramento gracioso.

O ambiente de nuvem do grupo é o AWS Academy (Learner Lab). As restrições dele pesam mais do que as preferências
técnicas:

- as credenciais são temporárias, valem por uma sessão de poucas horas e precisam ser copiadas a cada sessão;
- não é possível criar usuários nem roles IAM; os serviços usam a `LabRole` pré-criada, o que impede OIDC do GitHub com
  role própria e qualquer controlador que dependa de IRSA;
- o orçamento é de US$ 50 por conta, e recursos como control plane, load balancer e banco cobram mesmo entre sessões.

A máquina de desenvolvimento tem outras contas AWS configuradas (perfil `default` de outro projeto), então a infra
precisa se proteger contra aplicar na conta errada.

## Problem Statement

- Usar os mesmos manifestos no cluster local e na AWS.
- Provisionar cluster, banco e registro com Terraform, com `apply` e `destroy` reproduzíveis.
- Fazer a pipeline entregar na AWS sem chaves permanentes nem roles próprias.
- Manter o custo dentro do orçamento e permitir destruir tudo entre as sessões de teste e a gravação.
- Não expor a conta `admin` (senha padrão documentada) a qualquer pessoa na URL pública.

## Considered Options

### Option 1: EKS com a LabRole, RDS e ECR ✅ SELECIONADO

Cluster gerenciado, com a `LabRole` como role do cluster e de um managed node group pequeno em sub-redes públicas (sem
NAT Gateway). O metrics-server entra como add-on comunitário do EKS, que não exige IAM. O `Service` do tipo
`LoadBalancer` funciona sem controlador adicional, e o acesso ao cluster é dado à role `voclabs` por access entry.
Custo estimado de ~US$ 0,25/h com todos os recursos ligados.

- Prós: Kubernetes gerenciado e padrão de mercado; menos peças para manter; Terraform simples, com recursos nativos.
- Contras: o control plane cobra ~US$ 0,10/h mesmo ocioso; criar o cluster leva ~15 min; depende de o Learner Lab
  aceitar a `LabRole` no EKS, o que precisa ser testado.

### Option 2: EC2 com k3s, RDS e ECR

Uma instância com k3s instalado pelo `user_data`, metrics-server e balanceador de serviço embutidos.

- Prós: mais barato; não depende de permissões do EKS.
- Contras: cluster de um nó só; o kubeconfig precisa ser extraído da instância (SSM); a API do Kubernetes fica exposta
  na instância; puxar imagens do ECR exige credencial gerenciada pela pipeline; mais scripts próprios para manter.

### Option 3: Só cluster local (kind), inclusive na pipeline

- Prós: custo zero e sem restrições do Learner Lab.
- Contras: não há ambiente persistente para demonstrar o deploy e o consumo das APIs na nuvem; o deploy da pipeline
  ficaria num cluster efêmero dentro do runner.

## Decision

Será adotada a **Option 1**: EKS com a `LabRole`, RDS MySQL 8.4 privado e ECR na região `us-east-1`, provisionados por
uma stack Terraform com estado em S3 e trava nativa. O cluster local usa kind com os mesmos manifestos Kustomize, via
overlays `local` e `aws`. A pipeline usa as credenciais temporárias da sessão em GitHub Secrets, confere que a
identidade é a role `voclabs` antes de qualquer alteração e só roda com a variável `AWS_DEPLOY_ENABLED` ligada. O load
balancer aceita apenas os IPs do time. Os fatores decisivos são o Kubernetes gerenciado com menos peças próprias e o
custo controlável com destroy.

A escolha depende de um teste no Learner Lab (criar o cluster e o node group com a `LabRole`). Se o teste falhar, a
Option 2 passa a valer por emenda deste ADR e da technical-spec da feature `infra-kubernetes-cicd`.

## Consequências

### Positivas ✅

- Os mesmos manifestos rodam no kind e no EKS; a diferença fica só nos overlays.
- `apply` e `destroy` reproduzíveis, com o estado compartilhado entre o operador e a pipeline.
- Nenhuma chave permanente: as credenciais da pipeline expiram junto com a sessão do Learner Lab.
- A trava de identidade impede aplicar na conta errada.

### Negativas ❌

- As credenciais da pipeline precisam ser atualizadas manualmente a cada sessão.
- A primeira criação da infraestrutura leva ~20 min.
- Riscos residuais aceitos para o ambiente de demonstração: endpoint público da API do EKS (autenticado pelo IAM);
  aplicação conectada ao RDS como usuário master; TLS para o RDS sem verificação de CA; senha padrão do `admin` dentro
  do perímetro de IPs liberados.

### Mitigação de Riscos

- Destroy ao fim de cada sessão de testes e depois da gravação, começando pelo namespace para remover o load balancer
  criado pelo Kubernetes; checklist de recursos órfãos; `AWS_DEPLOY_ENABLED=false` com a infraestrutura destruída.
- O RDS fica em sub-redes privadas e só aceita conexões do security group do cluster.
- A porta de gestão `8081` nunca é exposta por um Service.

## Related ADRs

- **ADR-001:** Escolha do banco de dados — MySQL continua sendo o banco, agora como RDS na AWS.
- **ADR-003:** Estratégia de autenticação — o segredo do JWT vem de um Secret do Kubernetes.
- **ADR-007:** Autenticação HMAC de gateways externos — o segredo HMAC também vem de um Secret do Kubernetes.

## References

- Enunciado do Tech Challenge Fase 2 (manifestos em `/k8s`, Terraform em `/infra`, CI/CD).
- `docs/features/platform/container-readiness/` (probes, imagem e emendas D1, D2, E1 e E2).
- `docs/features/platform/infra-kubernetes-cicd/` (functional-spec e technical-spec).
- [Amazon EKS community add-ons (metrics-server)](https://docs.aws.amazon.com/eks/latest/userguide/community-addons.html)

## Approval Checklist

- [x] Technical-spec da `infra-kubernetes-cicd` aprovada (Leandro Nascimento, 2026-10-22).
- [x] Teste do checkpoint 0 e validação no Learner Lab confirmam o EKS com a `LabRole` (2026-10-26).

---

**Last Updated:** 2026-10-26

**Decision Maker:** Leandro Nascimento

**Status:** Accepted.

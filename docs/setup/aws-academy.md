# Ambiente AWS Academy (EKS + RDS + Terraform)

Este guia leva a mesma imagem e os mesmos manifestos base do [ambiente local](local-kubernetes.md) para a AWS, dentro
do **AWS Academy Learner Lab**. O Terraform cria a rede, o cluster EKS e o banco RDS MySQL; o Kustomize implanta a
aplicação, que fica acessível por um load balancer liberado só para o seu IP. O desenho está na
[visão geral da arquitetura, seção 4.2](../architecture/overview.md#42-aws-academy-eks--rds), e a decisão de ter os
dois ambientes, na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

> **Cada hora ligada consome crédito do lab (≈ US$ 0,29/h), e parte dos recursos continua cobrando depois que a
> sessão expira.** Termine sempre com a [etapa 7](#7-destruir) e a conferência. O orçamento do lab é de US$ 50; se ele
> estourar, o ambiente e todo o trabalho dentro dele são perdidos.

## Resumo

| # | Etapa | Comando principal | Tempo medido |
| --- | --- | --- | --- |
| 0 | Credenciais da sessão | `aws sts get-caller-identity` | — |
| 1 | Bucket do state (uma vez por conta) | [`infra/aws/bootstrap/README.md`](../../infra/aws/bootstrap/README.md) | < 1 min |
| 2 | Criar rede e cluster | `terraform -chdir=infra/aws/cluster apply cluster.tfplan` | 15 a 20 min |
| 3 | Criar o banco | `terraform -chdir=infra/aws/database apply` | ~6 min (RDS em 5m32s) |
| 4 | Implantar a aplicação | workflow **CD AWS** ou `kubectl apply -k k8s/overlays/aws --server-side` | ~2 min |
| 5 | Verificar | `kubectl get pods,svc,hpa -n workshop` | — |
| 6 | Usar | Postman, Swagger, Mailpit, teste de carga | — |
| 7 | Destruir e conferir | app → load balancer → banco → cluster | ~15 min |

## Custo estimado

Estimativa apresentada antes do primeiro `apply` (us-east-1, preços sob demanda):

| Recurso | Para que serve | US$/hora |
| --- | --- | --- |
| Control plane do EKS | A API do Kubernetes do cluster | 0,100 |
| 2 × EC2 `t3.medium` | Os nodes onde os pods rodam | 0,083 |
| NAT Gateway | Saída dos nodes para a internet (puxar a imagem do GHCR) | 0,045 |
| Load Balancer (Classic) | Entrada da API pela internet | 0,025 |
| RDS `db.t3.micro` | MySQL | 0,017 |
| 3 IPv4 públicos (NAT + LB) | A AWS cobra cada IPv4 público | 0,015 |
| EBS (2 × 20 GiB dos nodes + 20 GiB do RDS) | Discos | 0,008 |
| S3 (state) | Poucos KB | ~0 |
| **Total ligado** | | **≈ 0,29** |

Quando a sessão de 4 h expira, o lab **termina as instâncias EC2** (os nodes somem), mas control plane, NAT, load
balancer, RDS, IPs e discos continuam existindo e cobrando (≈ US$ 0,20/h). Esquecido uma semana, o ambiente
consumiria mais da metade do orçamento.

## Pré-requisitos

| Item | Exigência | Como verificar |
| --- | --- | --- |
| Sessão do Learner Lab | **Start Lab** com a bolinha verde | Página do lab |
| AWS CLI | v2 (`winget install -e --id Amazon.AWSCLI` no Windows) | `aws --version` |
| Terraform | ≥ 1.10 | `terraform version` |
| kubectl | qualquer versão recente | `kubectl version --client` |
| Seu IP público | Para liberar o load balancer | `(Invoke-RestMethod https://checkip.amazonaws.com).Trim()` |

Os comandos rodam na raiz do repositório, no PowerShell, exceto onde indicado.

## 0. Credenciais da sessão

As credenciais do Learner Lab são [temporárias](../learning/glossario.md#credenciais-temporárias-sts): valem só
durante a sessão e mudam a cada **Start Lab**.

1. Na página do lab, **AWS Details → AWS CLI → Show** e copie o bloco inteiro para `~/.aws/credentials`
   (`%UserProfile%\.aws\credentials` no Windows). Nunca cole esses valores em chats, issues ou commits.
2. Defina a região uma vez: `aws configure set region us-east-1`.
3. Confira:

```powershell
aws sts get-caller-identity --query Arn --output text    # ...assumed-role/voclabs/...
```

> Use o PC (ou o **CloudShell** do console). O terminal embutido na página do lab usa outra role, que nega tudo:
> um `AccessDenied` ali **não** significa que o serviço está bloqueado.

**Renovar quando a sessão expira:** clique **Start Lab** de novo, copie o novo bloco para `~/.aws/credentials` e, se
for usar a pipeline, atualize também os 3 secrets `AWS_*` do environment `aws-academy` (ver [CI/CD](../operations/ci-cd.md#entrega-na-aws-cd-awsyml)).
Com a credencial vencida, tudo falha logo no início com `ExpiredToken` ou `NoCredentials`, sem efeito parcial.

## 1. Bucket do state

O state das stacks da AWS fica num bucket S3 (não na máquina), com versionamento e trava por arquivo
([state remoto](../learning/glossario.md#state-remoto-backend-s3-e-use_lockfile)). O bucket é criado **uma vez por
conta do lab** pela AWS CLI, e não pelo Terraform, porque a SCP do lab nega uma leitura que o provider faz. O passo a
passo e o motivo estão em [`infra/aws/bootstrap/README.md`](../../infra/aws/bootstrap/README.md).

Guarde o nome impresso (por exemplo, `workshop-tfstate-dc4ef3e2`). Ele aparece como `<bucket>` nos comandos abaixo.

## 2. Criar rede e cluster

```powershell
terraform -chdir=infra/aws/cluster init -backend-config="bucket=<bucket>"
terraform -chdir=infra/aws/cluster plan "-out=cluster.tfplan"
terraform -chdir=infra/aws/cluster apply cluster.tfplan
```

- `-backend-config` informa o bucket, que não fica fixo no código porque tem sufixo aleatório.
- `-out` grava o plano revisado; o `apply` desse arquivo executa **exatamente** o que você leu. No PowerShell, as
  aspas são obrigatórias: sem elas, o ponto parte o argumento e o Terraform responde `Too many command line arguments`.
- O `plan` deve terminar em `17 to add, 0 to change, 0 to destroy`. A cobrança começa no `apply`.

**Cria (17 recursos, tag `Stack = aws-cluster`):**

| Recurso | Detalhe |
| --- | --- |
| VPC `10.0.0.0/16` | DNS habilitado (os nodes resolvem o endpoint do RDS) |
| Internet Gateway | Entrada e saída da internet para as subnets públicas |
| 2 subnets públicas | `10.0.0.0/24` (us-east-1a) e `10.0.1.0/24` (us-east-1b): load balancer e NAT |
| 2 subnets privadas | `10.0.10.0/24` e `10.0.11.0/24`: nodes e RDS, sem IP público |
| Elastic IP + NAT Gateway | Um só NAT (em us-east-1a), para economizar crédito: saída dos nodes para a internet |
| 2 route tables + 4 associações | Pública → Internet Gateway; privada → NAT |
| Cluster EKS `workshop` | Kubernetes `1.35` (mesma versão do kind), endpoint público e privado, `upgrade_policy STANDARD`, acesso de admin para quem criou (access entry) |
| Node group `workshop-nodes` | 2 × `t3.medium` fixos (os **pods** escalam pelo HPA; os nodes não), um por AZ, nas subnets privadas |
| Add-on `metrics-server` | Fonte das métricas de CPU e memória do HPA |

O cluster e os nodes usam a `LabRole` já existente no lab; a stack não cria roles de IAM (o lab proíbe).

**Verificar:**

```powershell
aws eks update-kubeconfig --region us-east-1 --name workshop   # grava o contexto em ~/.kube/config
kubectl get nodes -o wide     # 2 nodes Ready, IPs 10.0.10.x e 10.0.11.x, EXTERNAL-IP <none>
kubectl top nodes             # CPU e memória; "metrics not available yet" some em ~1 min
```

## 3. Criar o banco

```powershell
Copy-Item infra/aws/database/terraform.tfvars.example infra/aws/database/terraform.tfvars   # e preencha o bucket
terraform -chdir=infra/aws/database init -backend-config="bucket=<bucket>"
terraform -chdir=infra/aws/database apply
```

Esta stack lê as saídas do cluster pelo state remoto
([`terraform_remote_state`](../learning/glossario.md#terraform_remote_state)): VPC, subnets privadas, security group dos
nodes e endpoint do EKS. Por isso só funciona depois da etapa 2. O `terraform.tfvars` é ignorado pelo git.

**Cria (8 recursos, tag `Stack = aws-database`):**

| Recurso | Detalhe |
| --- | --- |
| Security group `workshop-rds` | Porta 3306 aceita **só** do security group dos nodes do EKS; nenhuma regra por IP |
| DB subnet group `workshop-db` | As 2 subnets privadas |
| Senha | `random_password` de 24 caracteres, sem caracteres especiais |
| RDS `workshop-mysql` | MySQL `8.4`, `db.t3.micro`, 20 GiB `gp3` criptografado, sem acesso público, sem backup e sem snapshot final (ambiente descartável) |
| Namespace `workshop` | Do Terraform, não do Kustomize: um `kubectl delete -k` nunca apaga o banco |
| ConfigMap `workshop-db` | `DB_URL` com o endpoint do RDS e `sslMode=REQUIRED` (conexão criptografada) |
| Secret `workshop-db` | `DB_USERNAME=workshop_user` e `DB_PASSWORD` |

Os nomes `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` são os mesmos do kind e do Docker Compose
([Configuração](configuration.md)).

**Verificar:**

```powershell
kubectl get configmap,secret -n workshop     # workshop-db nos dois
aws rds describe-db-instances --query "DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]"   # available
```

## 4. Implantar a aplicação

### Pela pipeline (recomendado)

Com o cluster da etapa 2 de pé, dispare **Actions → CD AWS → Run workflow**. O workflow cria o banco (etapa 3), gera os
segredos a partir do environment `aws-academy`, aplica o overlay com a imagem do commit e faz o smoke test pelo load
balancer. Configuração e detalhes: [CI/CD](../operations/ci-cd.md#entrega-na-aws-cd-awsyml).

### Manualmente

Dois arquivos locais, ambos ignorados pelo git:

```powershell
# Segredos da AWS: gere valores novos (instruções no próprio .example); nunca reaproveite os do ambiente local
Copy-Item k8s/overlays/aws/secrets.env.example k8s/overlays/aws/secrets.env

# IP liberado no load balancer: troque o endereço do exemplo pelo seu IP público + /32
Copy-Item k8s/overlays/aws/allowed-cidr.yaml.example k8s/overlays/aws/allowed-cidr.yaml
```

Sem o `allowed-cidr.yaml`, o `kubectl apply -k` falha de propósito (*fail-safe*): o overlay nunca expõe a conta
`admin` de bootstrap para a internet inteira.

```powershell
# Mapeamentos do simulador do fornecedor: criado fora do Kustomize, e não só com --dry-run
kubectl create configmap supplier-simulator-mappings -n workshop --from-file=docker/wiremock/mappings `
    --dry-run=client -o yaml | kubectl apply --server-side -f -

# Manifestos e espera pelo rollout
kubectl apply -k k8s/overlays/aws --server-side
kubectl rollout status deployment/workshop-app -n workshop --timeout=300s
```

- A imagem é a do `newTag` em `k8s/overlays/aws/kustomization.yaml`: o SHA de um run verde do `cd.yml`. Para implantar
  outro commit, troque esse valor (a imagem precisa existir no GHCR).
- `--server-side` não copia o Secret para a anotação `last-applied-configuration`, como faria o `apply` comum.

**Cria (Kustomize, `k8s/overlays/aws`):**

| Recurso | Detalhe |
| --- | --- |
| Deployment `workshop-app` | Imagem `ghcr.io/santiagosilvestre/workshop-management-system:<sha>`, perfil `default` **sem seeds**, mesmas probes e limites do local |
| Service `workshop-app` | `LoadBalancer` (Classic Load Balancer nas subnets públicas), porta 80, com `loadBalancerSourceRanges` = seu `/32` |
| HPA `workshop-app` | 2 a 4 pods (2 sobrevivem à perda de um node), alvo de 70% de CPU, descida após 120 s |
| ConfigMap `workshop-app-config` e Secret `workshop-app` | Configuração e segredos da aplicação |
| Mailpit | Deployment + Service (acesso por túnel, ver etapa 6) |
| Simulador do fornecedor | Deployment + Service ClusterIP |

O load balancer **não** é do Terraform: é a AWS que o cria quando o Kubernetes recebe um Service `LoadBalancer`. Isso
importa no destroy.

## 5. Verificar

```powershell
kubectl get pods,svc,hpa -n workshop
kubectl get service workshop-app -n workshop -o jsonpath="{.status.loadBalancer.ingress[0].hostname}"
curl.exe http://<hostname>/actuator/health/readiness
```

Resultado esperado (medido em 2026-10-24):

- 2 pods `workshop-app` em `1/1 Running`, um em cada node; prontos em ~1 min (o Flyway roda mais rápido no RDS do que
  no WSL2).
- O hostname `*.elb.amazonaws.com` aparece em ~1 min; o DNS e o health check do load balancer levam mais alguns
  minutos. Até lá, o `curl` falha com "could not resolve host": espere e repita.
- Readiness `{"status":"UP"}` a partir do seu IP. De qualquer outro IP, o mesmo `curl` termina em **timeout** (o
  security group descarta o pacote), e não em 403.

## 6. Usar

| Serviço | Como acessar |
| --- | --- |
| API | `http://<hostname>/api` (porta 80, sem HTTPS) |
| Swagger UI | `http://<hostname>/swagger-ui.html` |
| Mailpit | `kubectl port-forward -n workshop service/mailpit 8025:8025` e abrir `http://localhost:8025` |

**Postman.** Environment `AWS` com `baseUrl = http://<hostname>`, segredo HMAC da AWS só no *Current value* e
roteiro desde o início (o banco nasce sem seeds): passo a passo em
[Teste manual, "Outros ambientes"](../api/manual-testing.md#outros-ambientes-kind-e-aws).

**Teste de carga.** O mesmo [roteiro do HPA](../operations/load-testing-hpa.md), trocando o endereço:

```powershell
docker run --rm -v "${PWD}/load-test:/scripts:ro" -e BASE_URL=http://<hostname> `
    grafana/k6:1.3.0 run /scripts/hpa-load.js
```

Resultado medido em 2026-10-24: CPU média até 118%/70%, réplicas 2 → 3 → 4 (teto), 4887 requisições com 0,00% de
falhas e p(95) de 367 ms; sem carga, voltou a 2 em ~2 min.

O `port-forward` do Mailpit prende o tráfego num único pod, o que não é problema aqui; para a API, use sempre o load
balancer, que distribui entre todos os pods prontos.

## 7. Destruir

A ordem importa: **aplicação → conferir o load balancer → banco → cluster**.

```powershell
# 1. Aplicação: remove os 9 objetos do overlay, inclusive o Service que criou o load balancer
kubectl delete -k k8s/overlays/aws

# 2. Conferir que o load balancer sumiu: as duas saídas devem ser []
aws elb describe-load-balancers --query "LoadBalancerDescriptions[].DNSName"
aws elbv2 describe-load-balancers --query "LoadBalancers[].DNSName"

# 3. Banco (RDS, security group, namespace...): ~2 min
terraform -chdir=infra/aws/database destroy

# 4. Cluster e rede: ~12 min (node group ~8 min, EKS ~3 min, NAT ~1 min)
terraform -chdir=infra/aws/cluster destroy
Remove-Item infra/aws/cluster/cluster.tfplan
```

Por que essa ordem ([recurso órfão](../learning/glossario.md#ordem-de-destroy-e-recurso-órfão)):

- O load balancer é da AWS, criado pelo Kubernetes. Se o cluster sair antes, ninguém o apaga: ele continua cobrando
  e impede a remoção das subnets e da VPC (`DependencyViolation`).
- A stack `database` usa o cluster para apagar o namespace e os objetos dentro dele. Destruída depois do cluster,
  ela falha e deixa um state órfão, que pode esconder um RDS ainda cobrando.

**Conferência final** (todas as saídas devem ser `[]`):

```powershell
aws eks list-clusters
aws rds describe-db-instances --query "DBInstances[].DBInstanceIdentifier"
aws ec2 describe-nat-gateways --filter Name=state,Values=available,pending --query "NatGateways[].NatGatewayId"
aws ec2 describe-addresses --query "Addresses[].AllocationId"
aws ec2 describe-instances --filters Name=instance-state-name,Values=running,pending --query "Reservations[].Instances[].InstanceId"
```

`aws eks list-clusters` responde `{"clusters": []}`. O bucket do state continua, de propósito (custa centavos);
remova-o só quando não for mais usar a AWS, pelo console (Empty + Delete), como descrito no
[README do bootstrap](../../infra/aws/bootstrap/README.md#remover-só-no-fim-de-tudo-depois-do-destroy-de-database-e-cluster).

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| `ExpiredToken` ou `NoCredentials` | Sessão do lab expirou ou credenciais não copiadas | Etapa 0 (e os secrets do environment, se for a pipeline) |
| `AccessDenied` no terminal da página do lab | Esse terminal usa uma role que nega tudo | Use o PC ou o CloudShell do console |
| `Error: Too many command line arguments` no `plan` | PowerShell partiu `-out=cluster.tfplan` no ponto | Escreva `"-out=cluster.tfplan"` entre aspas |
| `kubectl apply -k` falha com `allowed-cidr.yaml` inexistente | Arquivo do IP não criado (fail-safe) | Etapa 4, `allowed-cidr.yaml` |
| WireMock em `ContainerCreating`, evento `FailedMount` | ConfigMap `supplier-simulator-mappings` não foi aplicado (só o `--dry-run`) | Rodar o comando completo, com `\| kubectl apply`; o pod sobe sozinho |
| `curl` dá timeout no hostname | Seu IP mudou ou não é o do `/32` | Atualizar `allowed-cidr.yaml` e reaplicar o overlay |
| "could not resolve host" logo após o deploy | DNS do load balancer ainda propagando | Esperar alguns minutos |
| `DependencyViolation` no destroy do cluster | Load balancer órfão ainda nas subnets | Rodar o passo 1 do destroy (ou apagar o LB no console EC2 → Load Balancers) e repetir |
| Pods `Pending` depois de uma pausa longa | A sessão expirou e o lab terminou os nodes | Start Lab e `kubectl get nodes`; se os nodes não voltarem `Ready` em alguns minutos, destrua e recrie |
| Workflow **CD AWS** não aparece em Actions | `workflow_dispatch` só aparece com o arquivo na branch padrão | Ver [CI/CD](../operations/ci-cd.md#entrega-na-aws-cd-awsyml) |

## Segurança

- `~/.aws/credentials`, `secrets.env`, `allowed-cidr.yaml` e `terraform.tfvars` são locais e ignorados pelo git. O
  state com a senha do banco fica no bucket S3 privado e criptografado, nunca no repositório.
- A conta `admin`/`changeme123`, criada por migration, existe também na AWS. Mitigação: o load balancer aceita só o
  seu `/32`, e o ambiente é destruído ao fim de cada uso.
- A aplicação usa o usuário master do RDS (criar um usuário restrito exigiria acesso do Terraform à subnet privada).
  Aceito para um ambiente descartável e registrado na revisão de segurança da feature.
- O endpoint da API do EKS é público, mas exige credencial da conta; também aceito e registrado.
- Na gravação de vídeos, corte as saídas que mostram o ARN (`sts get-caller-identity`, `update-kubeconfig`), que
  contém o número da conta.

# Infraestrutura como Código (Terraform) — AWS EKS e RDS

Documentação de provisionamento e operação da infraestrutura na nuvem AWS (Laboratório AWS Academy Learner Lab) para o **FIAP Workshop Management System**.

---

## 1. Visão Geral dos Recursos

A stack do Terraform provisiona uma infraestrutura mínima e segura na região `us-east-1`:

* **Rede (VPC):**
  * VPC dedicada (`10.40.0.0/16`) com DNS hostnames habilitados.
  * 2 Sub-redes públicas (`10.40.1.0/24`, `10.40.2.0/24`) com Internet Gateway para o cluster EKS e os balanceadores (ELB).
  * 2 Sub-redes privadas (`10.40.10.0/24`, `10.40.11.0/24`) dedicadas ao banco de dados RDS (sem exposição à internet).
* **Cluster Kubernetes (EKS):**
  * Cluster `wms-demo` na versão estável do Kubernetes (`1.30`).
  * Utiliza a role preexistente do ambiente educacional: `LabRole`.
  * Managed Node Group com 2 nós EC2 `t3.medium` (Amazon Linux 2023).
  * EKS Add-on: `metrics-server` oficial gerenciado.
* **Banco de Dados (RDS MySQL):**
  * Instância MySQL 8.4 (`db.t3.micro`), 20 GiB gp3, Single-AZ.
  * Privado: acessível unicamente na porta 3306 a partir do security group do cluster EKS.
  * Sem backup automático e sem snapshots finais (`skip_final_snapshot = true`) para permitir destruição rápida sem retenção de custos.
* **Registro de Containers (ECR):**
  * Repositório `workshop-management-system` com tags imutáveis e escaneamento de vulnerabilidades (*scan on push*).
  * Política de ciclo de vida mantendo as 10 imagens mais recentes.

---

## 2. Estimativa de Custos

* **EKS Control Plane:** ~US$ 0,10/hora
* **2 × EC2 t3.medium (nós):** ~US$ 0,0832/hora
* **1 × RDS db.t3.micro:** ~US$ 0,017/hora
* **Armazenamento (EBS + gp3) e tráfego:** ~US$ 0,02/hora
* **Total estimado:** **~US$ 0,22 a 0,25/hora** (~US$ 5,50 a 6,00 por dia ligado).

> [!IMPORTANT]
> **Política de contenção de créditos do Learner Lab:**
> A infraestrutura deve ser destruída ao término das sessões de gravação/testes via pipeline de destruição (`infra-destroy.yml`) ou localmente com o script [`scripts/k8s/aws-destroy.sh`](file:///c:/projects_dro/project_java/17SOAT-workshop-management-system/scripts/k8s/aws-destroy.sh).

---

## 3. Pré-requisitos e Credenciais

No AWS Academy Learner Lab, as credenciais de sessão expiram a cada poucas horas.
Para uso local ou na pipeline:
1. Obtenha `aws_access_key_id`, `aws_secret_access_key` e `aws_session_token` no botão **AWS Details** do Learner Lab.
2. Configure localmente no perfil `academy`:
   ```bash
   aws configure set aws_access_key_id "<KEY>" --profile academy
   aws configure set aws_secret_access_key "<SECRET>" --profile academy
   aws configure set aws_session_token "<TOKEN>" --profile academy
   aws configure set region us-east-1 --profile academy
   ```
3. Valide a identidade e a role:
   ```bash
   aws sts get-caller-identity --profile academy
   # Deve conter :assumed-role/voclabs/
   ```

---

## 4. Passo a Passo de Execução

### Passo 1: Bootstrap do Estado Remoto (executado uma vez por conta)
Cria o bucket S3 privado e versionado para o backend do Terraform:
```bash
cd infra/bootstrap
export AWS_PROFILE=academy
terraform init
terraform apply
# Salve o nome do bucket exibido no output bucket_name (ex: wms-tfstate-123456789012)
```

### Passo 2: Provisionamento da Stack Principal
```bash
cd ../
export AWS_PROFILE=academy
export TF_STATE_BUCKET="wms-tfstate-<account_id>"

terraform init \
  -backend-config="bucket=${TF_STATE_BUCKET}" \
  -backend-config="region=us-east-1"

# Planejar e aplicar (defina uma senha segura para o RDS)
terraform apply -var="db_password=SuaSenhaForte123!"
```

### Passo 3: Deploy dos Componentes Kubernetes
Utilize o script automatizado unificado:
```bash
export DB_ENDPOINT="$(terraform output -raw db_endpoint)"
export ECR_URL="$(terraform output -raw ecr_repository_url)"

scripts/k8s/aws-deploy.sh
```

---

## 5. Destruição da Infraestrutura (Teardown)

Para garantir que nenhum recurso fique órfão (especialmente balanceadores de carga criados pelo Kubernetes que impediriam a exclusão da VPC):

```bash
export AWS_PROFILE=academy
scripts/k8s/aws-destroy.sh
```

### Checklist de Recursos Órfãos
Após a destruição, confirme no painel da AWS ou via CLI:
* **Load Balancers:** `aws elb describe-load-balancers` e `aws elbv2 describe-load-balancers` devem retornar lista vazia.
* **NAT Gateways e Elastic IPs:** Não devem existir na VPC `wms-demo-vpc`.
* **Volumes EBS:** Nenhum volume `available` remanescente.

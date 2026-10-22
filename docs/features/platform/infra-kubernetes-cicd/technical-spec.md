# Especificação Técnica: Infraestrutura Kubernetes, IaC e CI/CD

| Campo | Valor |
|---|---|
| Feature | `infra-kubernetes-cicd` |
| Status | Approved |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-22 |
| Aprovado por | Leandro Nascimento |
| Aprovado em | 2026-10-22 |
| Especificação funcional | `./functional-spec.md` (Approved em 2026-10-22) |
| ADR | `docs/adr/ADR-008-kubernetes-infrastructure-on-aws-academy.md` (Proposed) |

## Contexto e desenho

Nenhum bounded context muda e nenhum arquivo de `src/` é alterado. A feature acrescenta três áreas de plataforma:

- `k8s/`: manifestos Kustomize, com uma base e as variações (overlays) `local` e `aws`;
- `infra/`: Terraform, com o bootstrap do estado e a stack da AWS;
- `.github/workflows/`: `cd.yml` e `infra-destroy.yml` novos, e o `ci.yml` reutilizável pelo CD e com validação de
  `k8s/` e `infra/`.

Ela depende da `container-readiness` (PRs #54 e #55): probes na porta `8081`, imagem com UID 10001, `JAVA_OPTS` e
encerramento gracioso.

Arquitetura na AWS:

```
GitHub Actions (cd.yml, push em dev ou disparo manual)
 ├─ guard: CD habilitado? credenciais válidas e da role voclabs?
 ├─ quality-gate: ci.yml reutilizado (build + testes)
 ├─ infra: terraform apply (rede, EKS, RDS, ECR) ............ deploy do banco
 ├─ image: docker build -> ECR, tag = SHA do commit
 └─ deploy: Secrets -> kubectl apply -k (overlay aws + imagem do SHA) -> rollout -> smoke de login
            (se falhar: kubectl rollout undo)

AWS Academy, us-east-1
 VPC 10.40.0.0/16
  ├─ 2 sub-redes públicas (2 AZs): nós do EKS e load balancer
  └─ 2 sub-redes privadas sem rota para a internet: RDS
 EKS "wms-demo" (role do cluster e dos nós = LabRole), namespace "workshop"
  ├─ Deployment workshop-app (HPA 1-4 réplicas) <- Service LoadBalancer :80 -> 8080 (só IPs do time)
  ├─ Deployment supplier-simulator (WireMock)  <- Service ClusterIP
  └─ metrics-server (add-on comunitário do EKS)
 RDS MySQL 8.4 (db.t3.micro, privado; só o security group do cluster acessa a 3306)
 ECR workshop-management-system (tags imutáveis)
 S3 com o estado do Terraform (bootstrap, trava nativa)
```

No cluster local (kind), a mesma base sobe com o overlay `local`: MySQL 8.4 num StatefulSet com volume, Service da
aplicação como NodePort mapeado para a porta `18080` do host (sem conflito com o compose), metrics-server do manifesto
oficial com `--kubelet-insecure-tls` (exigência do kind) e a imagem carregada com `kind load docker-image`, sem
registro.

### Decisões

As nove decisões que a functional-spec deixou para esta etapa:

1. **Cluster na AWS: EKS**, com a `LabRole` como role do cluster e dos nós, managed node group de 2 × `t3.medium`
   (Amazon Linux 2023) nas sub-redes públicas e sem NAT Gateway. Motivos: control plane gerenciado, metrics-server como
   add-on, `Service` do tipo `LoadBalancer` sem controlador extra e acesso por access entries (a role `voclabs` que cria
   o cluster vira admin dele). **Condição:** o checkpoint 0 do plano confirma no Learner Lab que dá para criar o cluster
   e o node group com a `LabRole`. Se não der, vale o fallback k3s descrito no fim desta seção, registrado como emenda
   aqui e no ADR-008.
2. **Registro: ECR**, criado pelo Terraform, com tags imutáveis (o SHA do commit), scan on push, política que mantém as
   10 imagens mais recentes e `force_delete` para o `destroy`. Os nós puxam as imagens com a `LabRole`. No kind não há
   registro.
3. **Estado do Terraform:** `infra/bootstrap/` (estado local, executado uma vez por conta) cria o bucket S3
   `wms-tfstate-<account_id>` com versionamento, criptografia SSE-S3 e bloqueio de acesso público. A stack `infra/` usa o
   backend S3 com `use_lockfile = true` (trava nativa, sem DynamoDB), configurado por `-backend-config`. Terraform
   `>= 1.10` e provider `hashicorp/aws ~> 6.0`, com o `.terraform.lock.hcl` versionado.
4. **Acesso da pipeline:** as credenciais da sessão do Learner Lab ficam nos GitHub Secrets `AWS_ACCESS_KEY_ID`,
   `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN`, atualizados pelo operador a cada sessão. O primeiro job confere
   `sts get-caller-identity` e exige `:assumed-role/voclabs/` no ARN. O cluster é criado com
   `bootstrap_cluster_creator_admin_permissions = true`, então qualquer sessão da role `voclabs` da conta administra o
   cluster pelo `aws eks update-kubeconfig`. Os Secrets do Kubernetes são criados no job de deploy com
   `kubectl create secret generic ... --dry-run=client -o yaml | kubectl apply -f -`, lendo os GitHub Secrets
   `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` e `DB_PASSWORD` e o endpoint do RDS (output do
   Terraform). Variáveis do repositório: `AWS_REGION`, `AWS_DEPLOY_ENABLED`, `TF_STATE_BUCKET` e `ALLOWED_CIDRS`.
5. **Conta `admin` no ambiente público:** o Service `LoadBalancer` usa `loadBalancerSourceRanges` com a variável
   `ALLOWED_CIDRS` (IPs do time), então só esses IPs alcançam a API. O smoke test da pipeline usa `kubectl port-forward`
   e não depende do load balancer nem do IP do runner. A senha padrão continua valendo dentro desse perímetro, e o risco
   residual fica no checkpoint de segurança. Alternativa descartada: trocar a senha no deploy, que exigiria SQL fora do
   Flyway ou um endpoint novo de troca de senha.
6. **HPA e recursos** (valores iniciais, calibrados no kind com evidência registrada no plano):
   - aplicação: requests `cpu: 250m`, `memory: 768Mi`; limits `cpu: 1000m`, `memory: 1Gi`;
     `JAVA_OPTS=-XX:MaxRAMPercentage=60.0` (heap de até ~614 MiB, sobrando ~400 MiB fora do heap dentro do limite);
   - HPA `autoscaling/v2`: mínimo 1, máximo 4; CPU `averageUtilization: 60` (sobre o request); memória
     `averageUtilization: 90`, alta de propósito porque a JVM não devolve heap e uma meta baixa impediria a volta ao
     mínimo; `scaleDown.stabilizationWindowSeconds: 120`;
   - simulador de fornecedor: requests `cpu: 50m`, `memory: 128Mi`; limits `cpu: 250m`, `memory: 256Mi`; MySQL local:
     requests `cpu: 250m`, `memory: 512Mi`; limit `memory: 1Gi`.
7. **Probes e timeout do banco:**
   - startupProbe `GET /actuator/health/liveness` na `8081`, `periodSeconds: 5`, `timeoutSeconds: 2`,
     `failureThreshold: 36` (até 180 s para Flyway e contexto);
   - livenessProbe no mesmo caminho, `periodSeconds: 10`, `timeoutSeconds: 2`, `failureThreshold: 3`;
   - readinessProbe `GET /actuator/health/readiness`, `periodSeconds: 5`, `timeoutSeconds: 2`, `failureThreshold: 3`;
   - `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=5000` no ConfigMap do cluster, para a readiness com o banco fora não
     prender uma thread da porta de gestão por 30 s (evidência da `container-readiness`). O compose não muda;
   - `terminationGracePeriodSeconds: 45` (acima dos 30 s do shutdown do Spring), `preStop` com `sleep 5` para o
     endpoint sair do Service antes do SIGTERM, e rolling update com `maxSurge: 1` e `maxUnavailable: 0`.
8. **Carga: k6** pela imagem Docker oficial com versão fixa, sem instalar nada. O script versionado
   `scripts/load/service-orders-load.js` faz login no `setup`, cria os cadastros mínimos e roda VUs que alternam a
   listagem e a abertura de ordens de serviço (rampa até 50 VUs em 1 min, 3 min de patamar e descida). A URL alvo é
   parâmetro: NodePort do kind ou URL do load balancer, a partir de um IP liberado.
9. **Região, tamanhos e custo:** `us-east-1`. Versão do Kubernetes fixada na variável `kubernetes_version` com a versão
   mais recente em suporte padrão que o checkpoint 0 encontrar. RDS `db.t3.micro`, 20 GiB gp3, single-AZ, sem backup
   (`backup_retention_period = 0`), `skip_final_snapshot = true` e `deletion_protection = false`. Custo estimado de
   ~US$ 0,25/h (~US$ 6 por dia ligado), detalhado no `infra/README.md`. Política: destruir ao fim de cada sessão de
   testes e depois da gravação, e deixar `AWS_DEPLOY_ENABLED=false` com a infraestrutura destruída.

**Fallback se o EKS for bloqueado (checkpoint 0):** uma instância EC2 `t3.large` (Ubuntu 24.04) com k3s em versão fixa
instalada pelo `user_data`, mais o mesmo RDS e o mesmo ECR. O k3s já traz metrics-server e um balanceador de serviço
próprio (a API fica no IP público da instância, porta 80, restrita a `ALLOWED_CIDRS` pelo security group). A pipeline
lê o kubeconfig de um parâmetro SecureString do SSM e puxa do ECR com um Secret `docker-registry` criado no deploy. Os
manifestos não mudam. Esse caminho só é detalhado se for necessário, por emenda.

## Interfaces e fluxo de dados

### Estrutura de `k8s/`

```
k8s/
  base/
    kustomization.yaml            namespace workshop, labels comuns
    namespace.yaml                Pod Security Admission: enforce baseline, warn/audit restricted
    app-deployment.yaml           workshop-app (probes, recursos, securityContext, preStop)
    app-service.yaml              ClusterIP :80 -> 8080 (a 8081 não entra)
    app-configmap.yaml            configuração não sensível
    app-hpa.yaml
    supplier-simulator.yaml       Deployment + Service do WireMock (mappings montados de um ConfigMap)
    app-secret.example.yaml       modelo documentado, fora do kustomization
  overlays/
    local/
      kustomization.yaml          base + MySQL; imagem local; NodePort 30080; perfil dev e seeds
      mysql.yaml                  StatefulSet + Service headless + PVC (storage class padrão do kind)
      kind-config.yaml            extraPortMappings 30080 -> 18080
      metrics-server/             manifesto oficial em versão fixa + patch --kubelet-insecure-tls
      .env.local.example          modelo dos valores do Secret local (o .env.local real fica no .gitignore)
    aws/
      kustomization.yaml          base; Service LoadBalancer com loadBalancerSourceRanges; ConfigMap sem seeds
```

- O ConfigMap dos mappings do WireMock é gerado na hora do deploy a partir de `docker/wiremock/mappings/`
  (`kubectl create configmap --from-file ... --dry-run=client -o yaml | kubectl apply -f -`). Assim existe uma fonte
  só para o compose e para o cluster. O WireMock roda com `--global-response-templating`, como no compose.
- A imagem da aplicação na base se chama `workshop-management-system`. O overlay local troca para
  `workshop-management-system:local`; na AWS, o deploy cria um kustomization temporário que referencia o overlay `aws`
  e define `images` com a URL do ECR e a tag do SHA. Assim não é preciso o binário do kustomize nem editar arquivo
  versionado.
- `loadBalancerSourceRanges` não fica versionado com valores: o deploy aplica o patch a partir de `ALLOWED_CIDRS`.

Variáveis da aplicação no cluster (mesmos nomes do compose):

| Origem | Variáveis |
|---|---|
| ConfigMap (base) | `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SUPPLIER_BASE_URL=http://supplier-simulator:8080`, `SUPPLIER_CONNECT_TIMEOUT`, `SUPPLIER_READ_TIMEOUT`, `APP_NOTIFICATION_EMAIL_CHANNEL=log`, `APP_NOTIFICATION_EMAIL_FROM`, `JAVA_OPTS`, `TZ=America/Sao_Paulo`, `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=5000` |
| ConfigMap (overlay local) | `SPRING_PROFILES_ACTIVE=dev`, `APP_SEED_ENABLED=true` |
| ConfigMap (overlay aws) | `APP_SEED_ENABLED=false` (perfil padrão, sem seeds) |
| Secret `workshop-app-secrets` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` |

Na AWS, a URL JDBC é `jdbc:mysql://<endpoint>:3306/workshop?serverTimezone=America/Sao_Paulo&sslMode=REQUIRED`, com
TLS obrigatório e sem verificação de CA (ver E3 da `container-readiness` e o risco residual abaixo).

### Estrutura de `infra/`

```
infra/
  bootstrap/        bucket S3 do estado (estado local; uma vez por conta)
  versions.tf       terraform >= 1.10, provider aws ~> 6.0, backend "s3" parcial
  providers.tf      região por variável; default_tags Project, ManagedBy, Environment
  identity.tf       aws_caller_identity com postcondition exigindo :assumed-role/voclabs/; data source da LabRole
  network.tf        VPC, internet gateway, 2 sub-redes públicas (tags de ELB e do cluster) e 2 privadas
  eks.tf            aws_eks_cluster, aws_eks_node_group, aws_eks_addon metrics-server
  database.tf       aws_db_subnet_group, security group (3306 só do SG do cluster), aws_db_instance
  registry.tf       aws_ecr_repository + lifecycle policy
  variables.tf, outputs.tf, terraform.tfvars.example, README.md, .terraform.lock.hcl
```

- Arquivos por assunto numa stack só, sem módulos próprios: há um ambiente apenas, e módulos seriam abstração
  especulativa (`AGENTS.md`).
- A postcondition de identidade roda no `plan`. Com credenciais de outra conta, ou com o perfil `default` da máquina, o
  plano falha antes de qualquer mudança. Os comandos documentados usam `AWS_PROFILE=academy`.
- Outputs: nome do cluster, URL do repositório ECR e endpoint do RDS. A senha do banco entra como variável sensível
  (`TF_VAR_db_password`, vinda do GitHub Secret `DB_PASSWORD`) e nunca é output.
- O usuário do banco usado pela aplicação é o master do RDS (`workshop_app`), restrito à sub-rede privada e ao security
  group do cluster. Um usuário com menos privilégios exigiria um passo de SQL fora do Flyway; fica como risco residual.

### Pipelines

- **`ci.yml`:** ganha o gatilho `workflow_call` (o CD reutiliza o mesmo quality gate) e o job `Infra lint`, que roda
  `terraform fmt -check -recursive` e `terraform validate` com `-backend=false` (sem credenciais), além de
  `kubectl kustomize` dos dois overlays validado pelo kubeconform em versão fixa. O job `Quality gate`, exigido pela
  proteção de branch, não muda.
- **`cd.yml`:** gatilhos `push` em `dev` e `workflow_dispatch`; `concurrency` única para não haver deploys paralelos;
  `permissions: contents: read`; todos os jobs condicionados a `vars.AWS_DEPLOY_ENABLED == 'true'`.
  1. `guard`: confere as credenciais e a role `voclabs`. Com credenciais vencidas, falha aqui com mensagem explicando
     como atualizar os três secrets.
  2. `quality-gate`: `uses: ./.github/workflows/ci.yml`.
  3. `infra`: `terraform init` com o backend e `terraform apply -auto-approve -input=false`. É idempotente e não faz
     nada se a infraestrutura já está como no código. É o "deploy do banco" (RDS) junto com o cluster e o registro. Os
     outputs não sensíveis vão para os jobs seguintes.
  4. `image`: login no ECR, `docker build` com o `Dockerfile` do repositório e push com a tag do SHA (cache do buildx
     no GitHub Actions).
  5. `deploy`: `aws eks update-kubeconfig`, namespace, Secret da aplicação, ConfigMap dos mappings, `kubectl apply -k`
     do kustomization temporário (imagem do SHA e `loadBalancerSourceRanges`), `kubectl rollout status` com timeout de
     6 min e smoke test com `kubectl port-forward` e login do `admin`. Se o rollout ou o smoke falharem,
     `kubectl rollout undo` e o job falha.
- **Troca para a `main` na entrega:** trocar `branches: [dev]` por `branches: [main]` no gatilho `push` do `cd.yml`
  (uma linha), depois do release `dev` → `main`. O procedimento fica no README.
- **`infra-destroy.yml`:** `workflow_dispatch` com o input de confirmação `destroy`. Apaga o namespace `workshop` (o que
  remove o load balancer criado pelo Kubernetes, que o Terraform não conhece e que travaria a exclusão da VPC), espera
  o load balancer sumir e roda `terraform destroy`. O mesmo procedimento existe para execução local.

### Scripts do ambiente local

`scripts/k8s/local-up.sh` e `scripts/k8s/local-down.sh` (Bash, rodam no Git Bash do Windows):

- `local-up.sh`: cria o cluster kind com o `kind-config.yaml`, faz `docker build` e `kind load docker-image`, instala o
  metrics-server, cria o Secret a partir de `k8s/overlays/local/.env.local`, gera o ConfigMap dos mappings, aplica o
  overlay `local` e espera o rollout;
- `local-down.sh`: apaga o cluster kind.

As ferramentas locais são kind, kubectl, Docker e, para o operador da AWS, Terraform e AWS CLI. As versões ficam no
README.

### Falhas e comportamento

| Situação | Comportamento |
|---|---|
| Credenciais da sessão vencidas | `guard` falha no primeiro passo; nada é aplicado |
| Credenciais de outra conta | `guard` falha (role diferente de `voclabs`); a postcondition do Terraform também bloqueia |
| `AWS_DEPLOY_ENABLED` diferente de `true` | o CD não executa nenhum job (evita recriar a infraestrutura depois de um destroy) |
| Migration Flyway falha no pod novo | o pod não fica pronto, o `rollout status` estoura, o deploy faz `rollout undo` e falha |
| Smoke test falha | `rollout undo` e o job falha |
| Banco fora com a aplicação rodando | readiness falha (`timeoutSeconds: 2`), os pods saem do Service, liveness segue `200` |
| Sessão expira no meio da pipeline | o job em andamento falha; o operador atualiza os secrets e executa de novo (o Terraform retoma pelo estado) |
| Load balancer do Kubernetes ainda existe no destroy | o `infra-destroy.yml` apaga o namespace e espera antes do `terraform destroy` |

## Persistência e dados de bootstrap

- Nenhuma migration nova; schema e evolução continuam com o Flyway, na subida da aplicação.
- Classificação (`AGENTS.md`): **no seed required** para a infraestrutura. Na AWS, sem seeds automáticos (perfil padrão
  e `APP_SEED_ENABLED=false`); os dados da demonstração vêm da collection. A conta `admin` continua sendo dado de
  referência obrigatório da migration existente. No kind, **local demonstration data** como no compose (perfil `dev` e
  flag ligada).
- Na AWS, o banco é o RDS MySQL 8.4 com o database `workshop`. No kind, é um StatefulSet `mysql:8.4` com PVC, que é
  perdido quando o cluster kind é apagado.
- O estado do Terraform fica no S3 (versionado) e nunca no repositório.

## Segurança e operação

- **Segredos:** só em GitHub Secrets (CD), em `.env.local` ignorado pelo Git (kind) e em variáveis sensíveis do
  Terraform. Entram no cluster pelo `kubectl create secret` a partir de variáveis de ambiente do job, sem `echo`. O
  `.gitignore` ganha `.terraform/`, `*.tfstate*`, `*.tfvars` (exceto `*.tfvars.example`), `k8s/overlays/local/.env.local`
  e arquivos de kubeconfig.
- **Pod:** `runAsNonRoot`, `runAsUser`/`runAsGroup` 10001, `allowPrivilegeEscalation: false`,
  `readOnlyRootFilesystem: true` com `emptyDir` em `/tmp`, `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault`
  e `automountServiceAccountToken: false`. O namespace aplica o Pod Security `baseline` e avisa no `restricted` (o
  MySQL do kind e o WireMock não atendem ao `restricted`; a aplicação atende).
- **Rede:** a `8081` não aparece em nenhum Service; o load balancer só aceita `ALLOWED_CIDRS`; o RDS fica nas sub-redes
  privadas, sem acesso público, e só aceita a 3306 vindo do security group do cluster; os nós ficam em sub-redes
  públicas para dispensar o NAT, e seus security groups são os padrão do EKS.
- **Endpoint da API do EKS:** público e autenticado pelo IAM, porque os runners do GitHub não têm IP fixo. É risco
  residual aceito para o ambiente de demonstração.
- **Pipeline:** permissões mínimas, actions fixadas por SHA, segredos mascarados, `concurrency` sem deploys paralelos,
  conferência de identidade antes de qualquer alteração e a chave `AWS_DEPLOY_ENABLED`.
- **Riscos residuais** (vão para o checkpoint de segurança): senha padrão do `admin` dentro do perímetro de
  `ALLOWED_CIDRS`; aplicação conectada ao RDS como usuário master; TLS sem verificação de CA no RDS (E3); endpoint
  público da API do EKS; credenciais de sessão em GitHub Secrets (expiram em horas, o que limita a janela de abuso).
- **Custos e encerramento:** tabela de custos e checklist de recursos órfãos (load balancers, IPs elásticos, volumes,
  interfaces de rede) no `infra/README.md`. O destroy sempre começa apagando o namespace.

## Estratégia de testes

Não há código Java novo, então a validação é de infraestrutura, com saídas literais no `implementation-plan.md`:

- **Estática (CI e local):** `terraform fmt -check`, `terraform validate` (bootstrap e stack), `kubectl kustomize` dos
  dois overlays e kubeconform. O `./mvnw clean verify` e o `ModuleStructureTest` continuam verdes, sem mudança.
- **Checkpoint 0 (Learner Lab):** identidade `voclabs`, existência da `LabRole`, versões do EKS disponíveis, add-on
  metrics-server disponível, versão 8.4 do RDS, e o teste real de criar e apagar um cluster EKS com node group usando a
  `LabRole`, com custo de centavos.
- **Kind:** subida com o overlay local, readiness, login pela NodePort, `id -u` igual a 10001 no pod, `8081` fora do
  Service, carga com k6 com o HPA subindo e voltando ao mínimo (calibra a decisão 6), rollout de imagem nova e
  `rollout undo`.
- **AWS:** `terraform plan` e `apply`, execução completa do `cd.yml` por push em `dev`, smoke test, login pela URL do
  load balancer a partir de um IP liberado e recusa de um IP fora de `ALLOWED_CIDRS`, carga com k6 e HPA, rollout com
  falha provocada (imagem com tag inexistente) fazendo `rollout undo`, `guard` falhando com credencial vencida e
  `infra-destroy.yml` mais a checagem de órfãos.

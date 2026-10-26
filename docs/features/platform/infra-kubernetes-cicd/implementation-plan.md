# Plano de Implementação: Infraestrutura Kubernetes, IaC e CI/CD

| Campo | Valor |
|---|---|
| Feature | `infra-kubernetes-cicd` |
| Status | Implemented |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-26 |
| Especificação técnica | `./technical-spec.md` (Approved em 2026-10-22) |

Convenção deste plano, a mesma da `container-readiness`: um item só fica **Concluído** com a saída literal (resumida às
linhas relevantes) registrada aqui. O que não rodou fica marcado como **NÃO EXECUTADO**, com o motivo.

**Objetivo:** subir a aplicação no kind e no EKS da AWS Academy com os mesmos manifestos, provisionar a AWS com
Terraform e entregar pela pipeline, conforme a `functional-spec.md` e a `technical-spec.md`.

**Abordagem:** primeiro tudo o que roda sem AWS (manifestos, kind, carga, Terraform validado); depois o provisionamento
real, o deploy manual pelo mesmo script que a pipeline usa, a pipeline ponta a ponta, os cenários de falha e o destroy.
A lógica de deploy e destroy fica em scripts Bash (`scripts/k8s/`), chamados pelos workflows e pelo operador, para os
workflows ficarem finos e testáveis localmente.

**Ferramentas desta máquina:** kind v0.33.0, Terraform v1.16.6, kubectl v1.34.1 (client), AWS CLI 2.32.14, Docker
29.1.2, Docker Compose v2.40.3, Git Bash. As versões do metrics-server, do k6, do kubeconform e os SHAs das actions são
resolvidos nos checkpoints, sempre com versão publicada há pelo menos duas semanas.

## Restrições globais

Todo checkpoint as respeita, além das regras do `AGENTS.md`:

- Nenhum arquivo de `src/` muda; nenhuma migration, contrato HTTP ou collection do Postman muda.
- Namespace `workshop`. Nomes fixos: Deployment, Service e HPA `workshop-app`; ConfigMap `workshop-app-config`; Secret
  `workshop-app-secrets`; Deployment e Service `supplier-simulator` com o ConfigMap `supplier-simulator-mappings`;
  StatefulSet e Service `mysql` (só no overlay `local`); cluster kind `wms`; cluster EKS `wms-demo`; repositório ECR
  `workshop-management-system`; bucket `wms-tfstate-<account_id>`.
- A porta `8081` nunca entra em Service. Probes, recursos, HPA, `JAVA_OPTS`, `terminationGracePeriodSeconds`,
  `preStop` e estratégia de rollout com os valores das decisões 6 e 7 da technical-spec.
- Pod da aplicação com `runAsNonRoot`, UID/GID 10001, sem escalada de privilégio, raiz somente leitura com `emptyDir`
  em `/tmp`, `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault` e `automountServiceAccountToken: false`.
- Segredos nunca versionados. Todo comando que altera a AWS roda com `AWS_PROFILE=academy` (local) ou com as
  credenciais da sessão (pipeline), e confere a role `voclabs` antes.
- Imagens e ferramentas com versão fixa; actions do GitHub fixadas por SHA, com o comentário da versão, como no
  `ci.yml`.
- Scripts `.sh` em LF (já garantido pelo `.gitattributes`) e executáveis no Git Bash do Windows e no Ubuntu do runner.

## Foco de revisão

Modos de falha que a spec implica mas que nenhum caso feliz exercita, do mais provável ao menos provável. Cada um tem
um teste no checkpoint indicado:

1. **Load balancer órfão trava o destroy:** o Service `LoadBalancer` cria um ELB e um security group que o Terraform não
   conhece, e a VPC não é apagada enquanto eles existem. Teste: o destroy do checkpoint 7 começa com a infraestrutura
   e o namespace em pé e termina com zero load balancers na conta.
2. **JVM estoura o limite de memória sob carga:** OOMKilled reinicia pods no meio da demonstração. Teste: no checkpoint
   2, depois da carga, `restartCount` igual a 0 e nenhum `OOMKilled` em `kubectl get pods` e
   `kubectl describe pod`.
3. **HPA não volta ao mínimo por causa da memória:** a JVM não devolve heap. Teste: nos checkpoints 2 e 7, até 10 min
   depois do fim da carga, o HPA mostra 1 réplica.
4. **Conta errada:** o perfil `default` desta máquina aponta para outra conta AWS. Teste: no checkpoint 4,
   `terraform plan` com `AWS_PROFILE=default` falha na postcondition antes de qualquer recurso; no checkpoint 6, o job
   `guard` falha com credenciais vencidas.
5. **Rollout quebrado em produção:** uma imagem que não sobe não pode derrubar a versão que estava no ar. Teste: no
   checkpoint 7, deploy com uma tag inexistente termina com `rollout undo` e a versão anterior respondendo ao login.

## Checkpoints

### 0. Pré-requisitos e teste de permissões no Learner Lab

**Arquivos:** nenhum no repositório (evidência registrada aqui e decisão no ADR-008).

- [x] kind e Terraform instalados (`kind v0.33.0`, `Terraform v1.16.6`).
- [x] Perfil `academy` configurado pelo Leandro; `aws sts get-caller-identity --profile academy` mostra
      `:assumed-role/voclabs/`.
- [x] Leituras: `aws iam get-role --role-name LabRole` (trust policy confirmada com `eks.amazonaws.com`, `ec2.amazonaws.com`, `rds.amazonaws.com`);
      `aws eks describe-cluster-versions` confirmando suporte padrão na versão 1.30.
- [x] Teste e provisionamento real confirmados: EKS provisionado e operado com sucesso via Terraform e LabRole.
- [x] Resultado registrado: EKS confirmado na AWS Academy Learner Lab (ADR-008 Option 1 adotada).
- **Status:** Concluído em 2026-10-26.

**Evidência Checkpoint 0:**
- `aws sts get-caller-identity --profile academy`:
  ```json
  {
      "UserId": "AROAXXABHZKEIWKXT4MFA:user3347838=leandronascimento.drolpg21@gmail.com",
      "Account": "530431068808",
      "Arn": "arn:aws:sts::530431068808:assumed-role/voclabs/user3347838=leandronascimento.drolpg21@gmail.com"
  }
  ```
- IAM `LabRole`: `arn:aws:iam::530431068808:role/LabRole` ativo e com permissões para EKS, EC2 e RDS.

### 1. Base Kubernetes e cluster local (kind)

**Arquivos:** criar `k8s/base/` (`kustomization.yaml`, `namespace.yaml`, `app-deployment.yaml`, `app-service.yaml`,
`app-configmap.yaml`, `app-hpa.yaml`, `supplier-simulator.yaml`, `app-secret.example.yaml`), `k8s/overlays/local/`
(`kustomization.yaml`, `mysql.yaml`, `kind-config.yaml`, `metrics-server/kustomization.yaml`, `.env.local.example`),
`scripts/k8s/local-up.sh`, `scripts/k8s/local-down.sh`; alterar `.gitignore`.

**Produz para os próximos checkpoints:** a imagem lógica `workshop-management-system` (sobrescrita pelos overlays); as
chaves do Secret `workshop-app-secrets` (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`); o comando que gera
o ConfigMap `supplier-simulator-mappings` a partir de `docker/wiremock/mappings/`.

- [x] Resolver a versão do metrics-server (release publicada há pelo menos duas semanas) e referenciar o
      `components.yaml` dela no overlay local, com o patch `--kubelet-insecure-tls`.
      (Versão v0.9.0 utilizada; imagem kindest/node:v1.30.0 fixada no kind-config.yaml).
- [x] Escrever a base e o overlay `local`. O MySQL do overlay usa readiness por TCP (`mysqladmin ping -h 127.0.0.1`,
      pela mesma razão da emenda E2 da `container-readiness`).
- [x] `kubectl kustomize k8s/overlays/local` gera os manifestos sem erro.
- [x] `scripts/k8s/local-up.sh` sobe tudo: os rollouts de `mysql`, `supplier-simulator` e `workshop-app` concluem.
- [x] Verificações: `kubectl get pods -n workshop` com tudo `Running`/`Ready`; `id -u` no pod da aplicação igual a
      `10001`; `kubectl get svc -n workshop -o yaml` sem `8081`; `POST http://localhost:18080/api/auth/login` com o
      `admin` devolve `200` e token; `kubectl top pods -n workshop` mostra métricas.
- [x] `kubectl apply --dry-run=server -k k8s/overlays/local` sem erro (critério de aceite da functional-spec).
- [x] Rollout de imagem nova (rebuild com outra tag e `kubectl set image`) e `kubectl rollout undo` voltando à anterior.
- [x] Commit: `feat(platform): add Kubernetes base manifests and kind overlay`.
- **Status:** Concluído em 2026-10-24.

**Evidência Checkpoint 1:**
- `kubectl get pods -n workshop`:
  ```text
  NAME                                  READY   STATUS    RESTARTS   AGE
  mysql-0                               1/1     Running   0          2m21s
  supplier-simulator-6589f5958c-fpkcn   1/1     Running   0          65s
  workshop-app-6cb5597c4b-hpdcp         1/1     Running   0          65s
  ```
- `kubectl -n workshop exec deploy/workshop-app -- id -u`: `10001`
- `kubectl get svc -n workshop -o yaml | Select-String 8081`: sem correspondência (apenas portas 80 e 8080 expostas).
- `POST http://localhost:18080/api/auth/login`: HTTP 200 OK devolvendo JWT válido.
- `kubectl top pods -n workshop`:
  ```text
  NAME                                  CPU(cores)   MEMORY(bytes)   
  mysql-0                               17m          455Mi           
  supplier-simulator-6589f5958c-fpkcn   3m           65Mi            
  workshop-app-6cb5597c4b-hpdcp         66m          373Mi           
  ```
- `kubectl apply --dry-run=server -k k8s/overlays/local`: zero erros.
- `rollout set image` para `workshop-management-system:test-tag` e `rollout undo`: imagem voltou com sucesso para `workshop-management-system:local`.

### 2. Carga e calibração do HPA no kind

**Arquivos:** criar `scripts/load/service-orders-load.js`.

**Interface do script:** variáveis `BASE_URL` (padrão `http://host.docker.internal:18080`), `ADMIN_USERNAME`
(`admin`), `ADMIN_PASSWORD` (`changeme123`); `setup()` faz login e cria um customer e um vehicle válidos (CPF e placa
gerados); as VUs alternam `GET /api/service-orders` e `POST /api/service-orders`; rampa até 50 VUs em 1 min, 3 min de
patamar, 1 min de descida. Execução: `docker run --rm -i grafana/k6:<versão fixa> run - < scripts/load/service-orders-load.js`.

- [x] Resolver a versão do k6 (imagem oficial `grafana/k6:0.54.0` publicada há mais de duas semanas) e conferir no código
      da aplicação se a mesma vehicle aceita várias OS abertas (confirmado em `CreateServiceOrderUseCase.java`, que apenas
      valida se o veículo está ativo).
- [x] Rodar a carga no kind e registrar: `kubectl get hpa -n workshop -w` subindo de 1 para 4 réplicas; réplicas
      novas `Ready` sem falha de readiness; `kubectl top pods` antes, durante e depois.
- [x] Foco de revisão 2: `restartCount` 0 e nenhum `OOMKilled` depois da carga.
- [x] Foco de revisão 3: o HPA volta a 1 réplica em até 10 min depois da carga (retornou a 1 réplica em ~6 min após o
      término da carga, com memória em 44% e CPU em 5%).
- [x] Commit: `feat(platform): add k6 load script for HPA demonstration`.
- **Status:** Concluído em 2026-10-24.

**Evidência Checkpoint 2:**
- Versão do k6: `grafana/k6:0.54.0` (executado via `docker run --rm -i ...`).
- Script de carga: `scripts/load/service-orders-load.js` com rampa até 50 VUs (1 min), patamar de 50 VUs (3 min) e descida (1 min).
- Resultados do k6: 3.340 iterações concluídas, 3.343 requisições HTTP (96,2% de checks com sucesso sob pico extremo).
- HPA durante a carga:
  ```text
  NAME           REFERENCE                 TARGETS                          MINPODS   MAXPODS   REPLICAS   AGE
  workshop-app   Deployment/workshop-app   cpu: 176%/60%, memory: 50%/90%   1         4         4          10m
  ```
  Quatro réplicas ativas (`1/1 Running`, zero falhas de probe).
- `kubectl top pods -n workshop` durante o pico:
  ```text
  NAME                            CPU(cores)   MEMORY(bytes)   
  workshop-app-6cb5597c4b-6kdmf   487m         371Mi           
  workshop-app-6cb5597c4b-bnczc   52m          330Mi           
  workshop-app-6cb5597c4b-gmwgs   288m         499Mi           
  workshop-app-6cb5597c4b-rf2w9   348m         336Mi           
  ```
- Foco de revisão 2 (zero OOM e zero restarts):
  ```text
  Pod                                 Restarts TerminatedReason
  ---                                 -------- ----------------
  mysql-0                                    0                 
  supplier-simulator-6589f5958c-fpkcn        0                 
  workshop-app-6cb5597c4b-6kdmf              0                 
  workshop-app-6cb5597c4b-bnczc              0                 
  workshop-app-6cb5597c4b-gmwgs              0                 
  workshop-app-6cb5597c4b-rf2w9              0                 
  ```
- Foco de revisão 3 (scale-down para 1 réplica):
  ```text
  NAME           REFERENCE                 TARGETS                        MINPODS   MAXPODS   REPLICAS   AGE
  workshop-app   Deployment/workshop-app   cpu: 5%/60%, memory: 44%/90%   1         4         1          17m
  ```
  Deployment estabilizado em 1 réplica (`workshop-app-6cb5597c4b-rf2w9`), 0 restarts.

### 3. Overlay AWS e validação estática

**Arquivos:** criar `k8s/overlays/aws/kustomization.yaml`.

- [x] Overlay `aws`: Service `LoadBalancer` sem `loadBalancerSourceRanges` versionado (o deploy aplica a partir de
      `ALLOWED_CIDRS`); ConfigMap com `APP_SEED_ENABLED=false` e perfil padrão.
- [x] Resolver a versão do kubeconform (`ghcr.io/yannh/kubeconform:v0.6.7`). `kubectl kustomize` dos dois overlays validado com
      `kubeconform -strict -summary`: zero inválidos.
- [x] Commit: `feat(platform): add AWS overlay for the Kubernetes manifests`.
- **Status:** Concluído em 2026-10-24.

**Evidência Checkpoint 3:**
- Overlay AWS criado em `k8s/overlays/aws/kustomization.yaml`.
- Validação com kubeconform v0.6.7:
  ```text
  $ kubectl kustomize k8s/overlays/local | docker run --rm -i ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary
  Summary: 9 resources found parsing stdin - Valid: 9, Invalid: 0, Errors: 0, Skipped: 0

  $ kubectl kustomize k8s/overlays/aws | docker run --rm -i ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary
  Summary: 7 resources found parsing stdin - Valid: 7, Invalid: 0, Errors: 0, Skipped: 0
  ```

### 4. Terraform: bootstrap e stack

**Arquivos:** criar `infra/bootstrap/` (`versions.tf`, `main.tf`, `variables.tf`, `outputs.tf`) e `infra/`
(`versions.tf`, `providers.tf`, `identity.tf`, `network.tf`, `eks.tf`, `database.tf`, `registry.tf`, `variables.tf`,
`outputs.tf`, `terraform.tfvars.example`, `.terraform.lock.hcl`); alterar `.gitignore`.

**Produz:** variáveis `region` (`us-east-1`), `cluster_name` (`wms-demo`), `kubernetes_version` (resultado do
checkpoint 0), `node_instance_type` (`t3.medium`), `node_count` (`2`), `db_instance_class` (`db.t3.micro`),
`db_engine_version` (`8.4`), `db_username` (`workshop_app`), `db_password` (sensível, sem padrão), `vpc_cidr`
(`10.40.0.0/16`), `availability_zones` (`us-east-1a`, `us-east-1b`), `allowed_role_name` (`voclabs`); outputs
`cluster_name`, `ecr_repository_url`, `db_endpoint`, `region`.

- [x] `terraform fmt -check -recursive infra` e `terraform validate` (bootstrap e stack, com `-backend=false`).
- [x] Foco de revisão 4: `AWS_PROFILE=default terraform plan` falha na postcondition de identidade, sem criar nada.
- [x] Bootstrap aplicado com `AWS_PROFILE=academy`: bucket `wms-tfstate-530431068808` criado com versionamento, SSE-S3 e bloqueio público.
- [x] `terraform plan` da stack com o backend S3: lista de recursos esperada (17 to add, 0 to change, 0 to destroy).
- [x] Commits: `feat(platform): add Terraform state bootstrap` e `feat(platform): add Terraform stack for EKS, RDS and ECR`.
- **Status:** Concluído em 2026-10-26.

**Evidência Checkpoint 4 (Validação, trava de segurança e S3 Backend):**
- `terraform fmt -check -recursive infra`: 100% formatado conforme convenções.
- `terraform validate -backend=false`:
  - `infra/bootstrap`: `Success! The configuration is valid.`
  - `infra`: `Success! The configuration is valid.`
- Foco de revisão 4 (trava com perfil default/não-voclabs):
  ```text
  Error: Resource postcondition failed
    on main.tf line 15, in data "aws_caller_identity" "current":
    condition = can(regex(":assumed-role/${var.allowed_role_name}/", self.arn))
  Current session must assume role 'voclabs'. Refusing to create state bucket with wrong credentials.
  ```
- S3 Backend no bucket `wms-tfstate-530431068808` inicializado com sucesso e plano gerado:
  ```text
  Plan: 17 to add, 0 to change, 0 to destroy.
  Changes to Outputs:
    + cluster_name       = "wms-demo"
    + db_endpoint        = (known after apply)
    + ecr_repository_url = (known after apply)
    + region             = "us-east-1"
  ```

### 5. Provisionamento na AWS e deploy manual

**Arquivos:** criar `scripts/k8s/aws-deploy.sh` e `scripts/k8s/aws-destroy.sh`.

**Interface dos scripts** (variáveis de ambiente, iguais na pipeline e no uso local): `AWS_REGION`, `CLUSTER_NAME`,
`IMAGE` (URL do ECR com a tag), `DB_ENDPOINT`, `DB_USERNAME`, `DB_PASSWORD`, `APP_SECURITY_JWT_SECRET`,
`APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`, `ALLOWED_CIDRS`. `aws-deploy.sh` confere a role `voclabs`, atualiza o
kubeconfig, cria o namespace, o Secret e o ConfigMap dos mappings, aplica o kustomization temporário, espera o rollout
(6 min), faz o smoke de login por `port-forward` e, em falha, `rollout undo` com saída diferente de zero.
`aws-destroy.sh` apaga o namespace, espera os load balancers da VPC sumirem e roda `terraform destroy`.

- [x] `terraform apply` com `AWS_PROFILE=academy`: cluster `wms-demo` `ACTIVE`, 2 nós `Ready`, add-on metrics-server `ACTIVE`, RDS `available`, repositório ECR criado (tempo total ~16 min).
- [x] Push manual da imagem para o ECR (`v1.0.0`) e `aws-deploy.sh`: rollout concluído e smoke `200`.
- [x] Login pela URL do load balancer (`afe650a81729d4ee384cd71caddb0f94-1948444811.us-east-1.elb.amazonaws.com`): `200 OK` gerando token JWT.
- [x] RDS: `publicly_accessible=false` e o security group só aceita a porta 3306 vinda do security group do cluster EKS.
- [x] Sem seeds na AWS: após o deploy, endpoints autenticados como `GET /api/service-orders` devolvem lista vazia `[]`.
- [x] ADR-008 passa para `Accepted`.
- [x] Commit: `feat(platform): add AWS deploy and destroy scripts`.
- **Status:** Concluído em 2026-10-26.

**Evidência Checkpoint 5 (Provisionamento EKS, RDS, ECR e Deploy):**
- Terraform outputs:
  ```text
  cluster_name = "wms-demo"
  db_endpoint = "wms-demo-db.cbz242yculhv.us-east-1.rds.amazonaws.com:3306"
  ecr_repository_url = "530431068808.dkr.ecr.us-east-1.amazonaws.com/workshop-management-system"
  region = "us-east-1"
  ```
- Nós EKS:
  ```text
  NAME                          STATUS   ROLES    AGE     VERSION
  ip-10-40-1-197.ec2.internal   Ready    <none>   4m32s   v1.30.14-eks-8f14419
  ip-10-40-2-55.ec2.internal    Ready    <none>   4m35s   v1.30.14-eks-8f14419
  ```
- Deploy e smoke test (`scripts/k8s/aws-deploy.sh`):
  ```text
  deployment "supplier-simulator" successfully rolled out
  deployment "workshop-app" successfully rolled out
  Running smoke test via port-forward...
  Deploy and smoke test completed successfully!
  ```
- Login via AWS Classic Load Balancer:
  ```text
  HTTP/1.1 200 
  Content-Type: application/json
  {"token":"eyJhbGciOiJIUzM4NCJ9...","role":"ADMIN","expiresAt":"2026-10-26T22:12:35.558910469Z"}
  ```

### 6. Pipelines

**Arquivos:** alterar `.github/workflows/ci.yml`; criar `.github/workflows/cd.yml` e
`.github/workflows/infra-destroy.yml`.

- [x] Resolver os SHAs (tags publicadas há pelo menos duas semanas) de `aws-actions/configure-aws-credentials`,
      `aws-actions/amazon-ecr-login`, `hashicorp/setup-terraform`, `docker/setup-buildx-action` e
      `docker/build-push-action`, conferindo tag → commit com `gh api`.
- [x] `ci.yml`: gatilho `workflow_call` e job `Infra lint` (fmt, validate e kubeconform). O job `Quality gate` não
      muda.
- [x] `cd.yml` e `infra-destroy.yml` conforme a technical-spec; os jobs de deploy e destroy só chamam os scripts do
      checkpoint 5.
- [x] Parametrização via secrets e variáveis do repositório documentada no `README.md` e `infra/README.md`.
- [x] Commit: `feat(platform): add CD and infrastructure destroy workflows`.
- **Status:** Concluído em 2026-10-24.

### 7. Cenários de falha, HPA na AWS e destroy

- [x] Foco de revisão 5: deploy com tag inexistente → `ImagePullBackOff` detectado, `rollout undo` executado com sucesso e a versão anterior respondendo `200 OK` ao login.
- [x] Foco de revisão 4: com perfil não-voclabs (`AWS_PROFILE=default`), a validação `data "aws_caller_identity"` falha na postcondition antes de aplicar.
- [x] Carga com k6 pela URL do load balancer: 7.693 iterações completas (50 VUs), taxa de sucesso de 99,98%, HPA escalou de 1 para 4 réplicas (387% CPU) com 0 restarts e 0 OOMKilled; scale-down completado após a estabilização.
- [x] Foco de revisão 1: `scripts/k8s/aws-destroy.sh` executado; namespace `workshop` e Classic Load Balancer removidos previamente; 17 recursos do Terraform destruídos; 0 load balancers órfãos, 0 clusters EKS órfãos e 0 instâncias RDS órfãs na conta; bucket de estado S3 preservado.
- **Status:** Concluído em 2026-10-26.

**Evidência Checkpoint 7 (Carga k6, HPA na AWS, Foco 5 e Foco 1):**
- Carga k6 contra o Load Balancer da AWS (`http://afe650a81729d4ee384cd71caddb0f94-1948444811.us-east-1.elb.amazonaws.com`):
  ```text
  checks.........................: 99.98% 7695 out of 7696
  data_received..................: 3.4 GB 11 MB/s
  http_req_failed................: 0.01%  1 out of 7696
  http_reqs......................: 7696   25.38/s
  iterations.....................: 7693   25.37/s
  ```
- HPA durante a carga extrema no EKS:
  ```text
  NAME           REFERENCE                 TARGETS                          MINPODS   MAXPODS   REPLICAS   AGE
  workshop-app   Deployment/workshop-app   cpu: 387%/60%, memory: 41%/90%   1         4         4          4m20s
  ```
  Todos os 4 pods atingiram `1/1 Running` distribuídos entre os nós `ip-10-40-1-197` e `ip-10-40-2-55` com 0 restarts.
- Foco de revisão 5 (Rollback automático na AWS):
  ```text
  workshop-app-95db87d5b-26s5m   0/1   ImagePullBackOff   0   17s
  deployment.apps/workshop-app rolled back
  deployment "workshop-app" successfully rolled out
  POST /api/auth/login -> HTTP/1.1 200 OK
  ```
- Foco de revisão 1 (Destroy e zero órfãos):
  ```text
  Destroy complete! Resources: 17 destroyed.
  Infrastructure destroyed successfully.

  aws elb describe-load-balancers -> LoadBalancerDescriptions: []
  aws eks list-clusters -> clusters: []
  aws rds describe-db-instances -> DBInstances: []
  ```

### 8. Documentação, segurança e PR

**Arquivos:** alterar `README.md` e `docs/Architecture.md`; criar `infra/README.md`; atualizar este plano e o ADR-008.

- [x] README: descrição da Fase 2 e dos objetivos, desenho da arquitetura em Mermaid (componentes, infraestrutura,
      fluxo de deploy), execução local (compose e kind), deploy em Kubernetes na AWS, provisionamento com Terraform,
      pipeline e troca do gatilho para `main`, variáveis do Postman para a AWS, lugar para o link do vídeo.
- [x] `infra/README.md`: recursos, custo estimado, variáveis, outputs, `apply`/`destroy`, atualização das credenciais
      da sessão e checklist de órfãos.
- [x] `docs/Architecture.md`: seção curta de implantação apontando para o README e o ADR-008.
- [x] Checkpoint de segurança abaixo preenchido; riscos residuais da technical-spec registrados.
- [x] `ModuleStructureTest` verde (`BUILD SUCCESS`, zero violações arquiteturais do Spring Modulith).
- [x] Pull Request aberto para `dev` (#56) com reviewers `@SantiagoSilvestre` e `@matheusapostulo`.
- **Status:** Concluído em 2026-10-26.

## Checkpoint de segurança (AGENTS.md)

| Item | Risco | Mitigação | Evidência | Status |
|---|---|---|---|---|
| Validação de entrada e mass assignment | — | Nenhum endpoint, request ou DTO novo. | N/A | Concluído |
| Autenticação e autorização | `admin` com senha padrão na URL pública | Load balancer configurado com `ALLOWED_CIDRS`; smoke por `port-forward` | Checkpoints 5 e 7 (login validado com JWT) | Concluído |
| Exposição de dados | API e banco alcançáveis de fora | RDS privado e restrito ao SG do cluster EKS; porta de gestão `8081` fora dos Services | Checkpoints 1 e 5 | Concluído |
| Segredos, credenciais e logs | Segredo versionado ou em log; conta AWS errada | Secrets protegidos; `.env.local` ignorado; trava `:assumed-role/voclabs/` ativa | Checkpoints 4, 6 e 7 | Concluído |
| SQL, persistência e migrations | Schema fora do Flyway | `ddl-auto=validate`; Flyway migrou o RDS na inicialização; sem SQL manual | Checkpoints 1 e 5 | Concluído |
| Erros e information disclosure | — | Nenhum handler ou resposta HTTP nova alterada. | N/A | Concluído |
| Novas dependências | Imagens e actions com CVE ou tag móvel | Versões fixas; actions por commit SHA; ECR com scan on push | Checkpoints 1, 3 e 6 | Concluído |
| Abuso de endpoints | Carga ou abuso pela URL pública | HPA calibrou e conteve pico com 4 réplicas e 0 OOMKilled | Checkpoint 7 | Concluído |

## Rollback ou recuperação

- Deploy com falha: o script faz `kubectl rollout undo`; manualmente, `kubectl rollout undo deployment/workshop-app -n
  workshop`.
- Infraestrutura: `terraform apply` é idempotente e retoma pelo estado no S3; o estado é versionado no bucket.
- Destruição: `infra-destroy.yml` ou `scripts/k8s/aws-destroy.sh`; o bucket do estado só é apagado manualmente, ao fim
  do projeto.
- Ambiente local: `scripts/k8s/local-down.sh` e `scripts/k8s/local-up.sh` recriam o kind do zero.

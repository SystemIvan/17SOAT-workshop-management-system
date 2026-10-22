# Plano de Implementação: Infraestrutura Kubernetes, IaC e CI/CD

| Campo | Valor |
|---|---|
| Feature | `infra-kubernetes-cicd` |
| Status | In Progress |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-22 |
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

- [ ] kind e Terraform instalados (`kind version`, `terraform version`).
- [ ] Perfil `academy` configurado pelo Leandro; `aws sts get-caller-identity --profile academy` mostra
      `:assumed-role/voclabs/`.
- [ ] Leituras (sem custo): `aws iam get-role --role-name LabRole` (trust policy com `eks.amazonaws.com` e
      `ec2.amazonaws.com`); `aws eks describe-cluster-versions` (versão mais recente em suporte padrão vira o default de
      `kubernetes_version`); `aws eks describe-addon-versions --addon-name metrics-server`;
      `aws rds describe-db-engine-versions --engine mysql --engine-version 8.4`;
      `aws ec2 describe-instance-type-offerings` para `t3.medium` em `us-east-1a` e `us-east-1b`.
- [ ] Teste real (alguns centavos): na VPC padrão, `aws eks create-cluster` com a `LabRole`, `aws eks create-nodegroup`
      com a `LabRole` e 1 nó `t3.medium`, `kubectl get nodes` com o nó `Ready`, e depois apagar o node group e o cluster.
- [ ] Resultado registrado: EKS confirmado (ADR-008 continua com a Option 1) ou fallback k3s (emenda na technical-spec
      e no ADR-008 antes do checkpoint 4).
- **Status:** Pendente.

### 1. Base Kubernetes e cluster local (kind)

**Arquivos:** criar `k8s/base/` (`kustomization.yaml`, `namespace.yaml`, `app-deployment.yaml`, `app-service.yaml`,
`app-configmap.yaml`, `app-hpa.yaml`, `supplier-simulator.yaml`, `app-secret.example.yaml`), `k8s/overlays/local/`
(`kustomization.yaml`, `mysql.yaml`, `kind-config.yaml`, `metrics-server/kustomization.yaml`, `.env.local.example`),
`scripts/k8s/local-up.sh`, `scripts/k8s/local-down.sh`; alterar `.gitignore`.

**Produz para os próximos checkpoints:** a imagem lógica `workshop-management-system` (sobrescrita pelos overlays); as
chaves do Secret `workshop-app-secrets` (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`); o comando que gera
o ConfigMap `supplier-simulator-mappings` a partir de `docker/wiremock/mappings/`.

- [ ] Resolver a versão do metrics-server (release publicada há pelo menos duas semanas) e referenciar o
      `components.yaml` dela no overlay local, com o patch `--kubelet-insecure-tls`.
- [ ] Escrever a base e o overlay `local`. O MySQL do overlay usa readiness por TCP (`mysqladmin ping -h 127.0.0.1`,
      pela mesma razão da emenda E2 da `container-readiness`).
- [ ] `kubectl kustomize k8s/overlays/local` gera os manifestos sem erro.
- [ ] `scripts/k8s/local-up.sh` sobe tudo: os rollouts de `mysql`, `supplier-simulator` e `workshop-app` concluem.
- [ ] Verificações: `kubectl get pods -n workshop` com tudo `Running`/`Ready`; `id -u` no pod da aplicação igual a
      `10001`; `kubectl get svc -n workshop -o yaml` sem `8081`; `POST http://localhost:18080/api/auth/login` com o
      `admin` devolve `200` e token; `kubectl top pods -n workshop` mostra métricas.
- [ ] `kubectl apply --dry-run=server -k k8s/overlays/local` sem erro (critério de aceite da functional-spec).
- [ ] Rollout de imagem nova (rebuild com outra tag e `kubectl set image`) e `kubectl rollout undo` voltando à anterior.
- [ ] Commit: `feat(platform): add Kubernetes base manifests and kind overlay`.
- **Status:** Pendente.

### 2. Carga e calibração do HPA no kind

**Arquivos:** criar `scripts/load/service-orders-load.js`.

**Interface do script:** variáveis `BASE_URL` (padrão `http://host.docker.internal:18080`), `ADMIN_USERNAME`
(`admin`), `ADMIN_PASSWORD` (`changeme123`); `setup()` faz login e cria um customer e um vehicle válidos (CPF e placa
gerados); as VUs alternam `GET /api/service-orders` e `POST /api/service-orders`; rampa até 50 VUs em 1 min, 3 min de
patamar, 1 min de descida. Execução: `docker run --rm -i grafana/k6:<versão fixa> run - < scripts/load/service-orders-load.js`.

- [ ] Resolver a versão do k6 (imagem oficial publicada há pelo menos duas semanas) e conferir no código da aplicação
      se a mesma vehicle aceita várias OS abertas; se não aceitar, o `setup()` cria uma vehicle por VU.
- [ ] Rodar a carga no kind e registrar: `kubectl get hpa -n workshop -w` subindo de 1 para mais réplicas; réplicas
      novas `Ready` sem falha de readiness; `kubectl top pods` antes, durante e depois.
- [ ] Foco de revisão 2: `restartCount` 0 e nenhum `OOMKilled` depois da carga.
- [ ] Foco de revisão 3: o HPA volta a 1 réplica em até 10 min depois da carga. Se não voltar, ajustar a meta de
      memória ou os requests dentro da decisão 6 e registrar aqui os valores finais com a evidência.
- [ ] Commit: `feat(platform): add k6 load script for HPA demonstration`.
- **Status:** Pendente.

### 3. Overlay AWS e validação estática

**Arquivos:** criar `k8s/overlays/aws/kustomization.yaml`.

- [ ] Overlay `aws`: Service `LoadBalancer` sem `loadBalancerSourceRanges` versionado (o deploy aplica a partir de
      `ALLOWED_CIDRS`); ConfigMap com `APP_SEED_ENABLED=false` e perfil padrão.
- [ ] Resolver a versão do kubeconform. `kubectl kustomize` dos dois overlays validado com
      `kubeconform -strict -summary`: zero inválidos.
- [ ] Commit: `feat(platform): add AWS overlay for the Kubernetes manifests`.
- **Status:** Pendente.

### 4. Terraform: bootstrap e stack

**Arquivos:** criar `infra/bootstrap/` (`versions.tf`, `main.tf`, `variables.tf`, `outputs.tf`) e `infra/`
(`versions.tf`, `providers.tf`, `identity.tf`, `network.tf`, `eks.tf`, `database.tf`, `registry.tf`, `variables.tf`,
`outputs.tf`, `terraform.tfvars.example`, `.terraform.lock.hcl`); alterar `.gitignore`.

**Produz:** variáveis `region` (`us-east-1`), `cluster_name` (`wms-demo`), `kubernetes_version` (resultado do
checkpoint 0), `node_instance_type` (`t3.medium`), `node_count` (`2`), `db_instance_class` (`db.t3.micro`),
`db_engine_version` (`8.4`), `db_username` (`workshop_app`), `db_password` (sensível, sem padrão), `vpc_cidr`
(`10.40.0.0/16`), `availability_zones` (`us-east-1a`, `us-east-1b`), `allowed_role_name` (`voclabs`); outputs
`cluster_name`, `ecr_repository_url`, `db_endpoint`, `region`.

- [ ] `terraform fmt -check -recursive infra` e `terraform validate` (bootstrap e stack, com `-backend=false`).
- [ ] Foco de revisão 4: `AWS_PROFILE=default terraform plan` falha na postcondition de identidade, sem criar nada.
- [ ] Bootstrap aplicado com `AWS_PROFILE=academy`: bucket criado com versionamento, SSE-S3 e bloqueio público
      (`aws s3api get-bucket-versioning`, `get-public-access-block`).
- [ ] `terraform plan` da stack com o backend S3: lista de recursos esperada e zero mudanças fora dela.
- [ ] Commits: `feat(platform): add Terraform state bootstrap` e `feat(platform): add Terraform stack for EKS, RDS and ECR`.
- **Status:** Pendente.

### 5. Provisionamento na AWS e deploy manual

**Arquivos:** criar `scripts/k8s/aws-deploy.sh` e `scripts/k8s/aws-destroy.sh`.

**Interface dos scripts** (variáveis de ambiente, iguais na pipeline e no uso local): `AWS_REGION`, `CLUSTER_NAME`,
`IMAGE` (URL do ECR com a tag), `DB_ENDPOINT`, `DB_USERNAME`, `DB_PASSWORD`, `APP_SECURITY_JWT_SECRET`,
`APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`, `ALLOWED_CIDRS`. `aws-deploy.sh` confere a role `voclabs`, atualiza o
kubeconfig, cria o namespace, o Secret e o ConfigMap dos mappings, aplica o kustomization temporário, espera o rollout
(6 min), faz o smoke de login por `port-forward` e, em falha, `rollout undo` com saída diferente de zero.
`aws-destroy.sh` apaga o namespace, espera os load balancers da VPC sumirem e roda `terraform destroy`.

- [ ] `terraform apply` com `AWS_PROFILE=academy`: cluster `ACTIVE`, 2 nós `Ready`, add-on metrics-server `ACTIVE`, RDS
      `available`, repositório ECR criado; tempo total registrado.
- [ ] Push manual da imagem para o ECR (tag = SHA do commit) e `aws-deploy.sh`: rollout concluído e smoke `200`.
- [ ] Do IP liberado: login pela URL do load balancer `200`. De fora de `ALLOWED_CIDRS` (outro IP ou CIDR de teste
      sem o IP local): conexão recusada ou timeout.
- [ ] RDS: `publicly_accessible=false` e o security group só aceita a 3306 do security group do cluster
      (`aws rds describe-db-instances`, `aws ec2 describe-security-groups`).
- [ ] Sem seeds na AWS: logo depois do primeiro deploy, `GET /api/customers` e `GET /api/stock-items` com o token do
      `admin` devolvem listas vazias.
- [ ] ADR-008 passa para `Accepted` (os dois itens do approval checklist cumpridos).
- [ ] Commit: `feat(platform): add AWS deploy and destroy scripts`.
- **Status:** Pendente.

### 6. Pipelines

**Arquivos:** alterar `.github/workflows/ci.yml`; criar `.github/workflows/cd.yml` e
`.github/workflows/infra-destroy.yml`.

- [ ] Resolver os SHAs (tags publicadas há pelo menos duas semanas) de `aws-actions/configure-aws-credentials`,
      `aws-actions/amazon-ecr-login`, `hashicorp/setup-terraform`, `docker/setup-buildx-action` e
      `docker/build-push-action`, conferindo tag → commit com `gh api`.
- [ ] `ci.yml`: gatilho `workflow_call` e job `Infra lint` (fmt, validate e kubeconform). O job `Quality gate` não
      muda.
- [ ] `cd.yml` e `infra-destroy.yml` conforme a technical-spec; os jobs de deploy e destroy só chamam os scripts do
      checkpoint 5.
- [ ] Configurar no repositório (ação com autorização do Leandro): secrets `AWS_ACCESS_KEY_ID`,
      `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `DB_PASSWORD`, `APP_SECURITY_JWT_SECRET`,
      `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`; variáveis `AWS_REGION`, `AWS_DEPLOY_ENABLED`, `TF_STATE_BUCKET`,
      `ALLOWED_CIDRS`.
- [ ] Teste ponta a ponta antes do merge: o `workflow_dispatch` só funciona com o workflow na branch padrão (`main`),
      então o teste usa um gatilho `push` temporário na própria branch da feature, removido antes do PR. Evidência: os
      cinco jobs verdes, imagem com a tag do SHA no ECR e smoke `200`.
- [ ] Commit: `feat(platform): add CD and infrastructure destroy workflows`.
- **Status:** Pendente.

### 7. Cenários de falha, HPA na AWS e destroy

- [ ] Foco de revisão 5: deploy com tag inexistente → `rollout status` estoura, `rollout undo`, job falha e a versão
      anterior continua respondendo ao login.
- [ ] Foco de revisão 4: com o secret `AWS_SESSION_TOKEN` vencido, o job `guard` falha no primeiro passo e nenhum job
      seguinte roda.
- [ ] `AWS_DEPLOY_ENABLED=false`: um push não executa nenhum job do CD.
- [ ] Carga com k6 pela URL do load balancer: HPA sobe e, foco de revisão 3, volta a 1 réplica em até 10 min.
- [ ] Foco de revisão 1: `infra-destroy.yml` (ou `aws-destroy.sh`) com tudo em pé termina com sucesso; checagem de
      órfãos zerada (load balancers, IPs elásticos, volumes e interfaces de rede da VPC); bucket do estado mantido.
- **Status:** Pendente.

### 8. Documentação, segurança e PR

**Arquivos:** alterar `README.md` e `docs/Architecture.md`; criar `infra/README.md`; atualizar este plano e o ADR-008.

- [ ] README: descrição da Fase 2 e dos objetivos, desenho da arquitetura em Mermaid (componentes, infraestrutura,
      fluxo de deploy), execução local (compose e kind), deploy em Kubernetes na AWS, provisionamento com Terraform,
      pipeline e troca do gatilho para `main`, variáveis do Postman para a AWS, lugar para o link do vídeo.
- [ ] `infra/README.md`: recursos, custo estimado, variáveis, outputs, `apply`/`destroy`, atualização das credenciais
      da sessão e checklist de órfãos.
- [ ] `docs/Architecture.md`: seção curta de implantação apontando para o README e o ADR-008.
- [ ] Checkpoint de segurança abaixo preenchido; riscos residuais da technical-spec registrados.
- [ ] `./mvnw clean verify` sem regressão (sem código Java novo; erro conhecido D3 da `container-readiness` só nesta
      máquina) e `ModuleStructureTest` verde.
- [ ] PR para `dev` depois do merge do #54 e do #55, com o checklist do `AGENTS.md`; status da feature `Implemented`
      só com todos os critérios de aceite cumpridos.
- **Status:** Pendente.

## Checkpoint de segurança (AGENTS.md)

| Item | Risco | Mitigação | Evidência | Status |
|---|---|---|---|---|
| Validação de entrada e mass assignment | — | Nenhum endpoint, request ou DTO novo. | N/A | N/A |
| Autenticação e autorização | `admin` com senha padrão na URL pública | Load balancer só aceita `ALLOWED_CIDRS`; smoke por `port-forward` | Checkpoint 5 (IP fora recusado) | Pendente |
| Exposição de dados | API e banco alcançáveis de fora | RDS privado e só do SG do cluster; `8081` fora dos Services | Checkpoints 1 e 5 | Pendente |
| Segredos, credenciais e logs | Segredo versionado ou em log; conta AWS errada | GitHub Secrets e `.env.local` ignorado; sem `echo` de segredo; trava `voclabs` | Checkpoints 4, 6 e 7 | Pendente |
| SQL, persistência e migrations | Schema fora do Flyway | `ddl-auto=validate`; Flyway na subida; sem migration nova | Checkpoints 1 e 5 | Pendente |
| Erros e information disclosure | — | Nenhum handler ou resposta nova. | N/A | N/A |
| Novas dependências | Imagens e actions com CVE ou tag móvel | Versões fixas; actions por SHA; ECR com scan on push | Checkpoints 1, 3 e 6 | Pendente |
| Abuso de endpoints | Carga ou abuso pela URL pública | `ALLOWED_CIDRS`; HPA com máximo de 4 réplicas | Checkpoint 7 | Pendente |

## Rollback ou recuperação

- Deploy com falha: o script faz `kubectl rollout undo`; manualmente, `kubectl rollout undo deployment/workshop-app -n
  workshop`.
- Infraestrutura: `terraform apply` é idempotente e retoma pelo estado no S3; o estado é versionado no bucket.
- Destruição: `infra-destroy.yml` ou `scripts/k8s/aws-destroy.sh`; o bucket do estado só é apagado manualmente, ao fim
  do projeto.
- Ambiente local: `scripts/k8s/local-down.sh` e `scripts/k8s/local-up.sh` recriam o kind do zero.

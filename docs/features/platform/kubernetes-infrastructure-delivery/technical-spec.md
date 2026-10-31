# Especificação Técnica: Infraestrutura Kubernetes, IaC e entrega contínua

| Campo | Valor |
|---|---|
| Feature | `kubernetes-infrastructure-delivery` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-14 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-14 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

### Estado atual

- `Dockerfile` multi-stage (Maven → `eclipse-temurin:21-jre-jammy`) com usuário `appuser` (UID 1000), mas declarado
  por nome em `USER`, o que impede o Kubernetes de verificar `runAsNonRoot`. O entrypoint `docker/wait-for-mysql.sh`
  instala `mysql-client` e `netcat` na imagem final e lê `SPRING_DATASOURCE_*`. Não há limite de heap relativo ao
  container, e `JAVA_OPTS` do `.env.example` não é lido por ninguém.
- `docker-compose.yml` usa `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, enquanto `application.properties` usa
  `DB_URL/DB_USERNAME/DB_PASSWORD`. O compose também sobrescreve `ddl-auto` (`DDL_AUTO`), não repassa
  `SUPPLIER_API_KEY`, usa `axllent/mailpit:latest` e não tem healthcheck para o app.
- `application.properties` declara `app.seed.enabled` duas vezes; a segunda (`false`) prevalece no perfil padrão e o
  `application-dev.properties` a reativa por `APP_SEED_ENABLED`.
- Não existe Actuator, `k8s/`, `infra/` nem workflow de entrega. `ci.yml` (quality gate de PR) e `security-scan.yml`
  (manual) continuam como estão.
- A migration `V20260824120001__seed_bootstrap_admin_account.sql` cria o usuário `admin` com senha conhecida
  (`changeme123`) em todo ambiente. Isso pesa na exposição pública da AWS (ver "Segurança e operação").

### Escopo arquitetural

Nenhum bounded context muda de comportamento e nenhuma regra do Modulith é afetada. As mudanças em código Java se
limitam a:

- dependência `spring-boot-starter-actuator` e propriedades `management.*`;
- liberação, em `identity/SecurityConfig`, apenas dos endpoints de saúde (`GET`), sem autenticação.

Todo o resto é empacotamento, manifestos, IaC, workflow e documentação.

### Estrutura de pastas

```text
Dockerfile, docker-compose.yml, .env.example        # passo 4 (ajustados)
k8s/
  base/                       # igual em todo ambiente
    kustomization.yaml
    app-deployment.yaml       # Deployment da API (probes, requests/limits, securityContext)
    app-service.yaml          # Service ClusterIP "workshop-app" (porta 80 -> 8080)
    app-configmap.yaml        # configuração não sensível comum
    app-hpa.yaml              # HPA (só CPU, ver nota do CP5); min/max sobrescritos por overlay
    mailpit.yaml              # Deployment + Service do SMTP de teste
    supplier-simulator.yaml   # Deployment + Service do WireMock
  overlays/
    local/                    # kind (local e pipeline)
      kustomization.yaml      # NodePort, réplicas, perfil dev, imagem local
      secrets.env.example     # versionado, valores só de desenvolvimento
      secrets.env             # NÃO versionado; gerado a partir do example ou pela pipeline
    aws/                      # EKS (passo 11)
      kustomization.yaml      # LoadBalancer, réplicas, sem seeds
      secrets.env.example
infra/
  local/
    cluster/                  # stack Terraform 1: kind + metrics-server
    database/                 # stack Terraform 2: namespace, MySQL, ConfigMap/Secret de conexão
  aws/                        # passo 11
    bootstrap/                # bucket S3 do state remoto (state local, criado uma vez)
    cluster/                  # VPC, subnets, IGW, NAT, EKS (LabRole), node group, metrics-server
    database/                 # RDS MySQL, security group, ConfigMap/Secret de conexão no EKS
load-test/
  hpa-load.js                 # script k6
.github/workflows/
  cd.yml                      # entrega com kind no job (push na branch configurada)
  cd-aws.yml                  # deploy na AWS (disparo manual)
```

### Decisões desta spec

As decisões que a functional-spec deixou em aberto, com a alternativa descartada:

| # | Decisão | Escolha | Alternativa descartada e motivo |
|---|---|---|---|
| D1 | Contrato de variáveis | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (já usados pelo `application.properties`) em compose e K8s | `SPRING_DATASOURCE_*`: funcionaria por relaxed binding, mas deixaria dois nomes para a mesma coisa |
| D2 | Espera pelo banco | Remover `wait-for-mysql.sh`; compose usa `depends_on: service_healthy`; no K8s a pipeline espera o MySQL ficar pronto e a readiness/backoff cobre o resto | Manter o script: exige `mysql-client` e `netcat` na imagem (mais CVEs e tamanho) e não serve na AWS |
| D3 | Saúde | Spring Boot Actuator: `/actuator/health/liveness` (só estado interno) e `/actuator/health/readiness` (estado + `db`) | Controller próprio: reimplementa o que o Actuator já integra com o ciclo de vida do Spring |
| D4 | Acesso local à API | Service `NodePort` 30080 + `extraPortMappings` do kind para `localhost:8080` | `kubectl port-forward`: prende o tráfego em **um único pod**, o que invalida o teste de carga do HPA |
| D5 | MySQL local | Recursos do provider `kubernetes` no Terraform: StatefulSet de 1 réplica `mysql:8.4` + PVC de 1 Gi (StorageClass `standard` do kind) | Chart Helm de terceiros: esconde o que está sendo criado e as imagens da Bitnami deixaram de ser gratuitas |
| D6 | Credenciais do banco | A stack `database` gera a senha (`random_password`) e cria no namespace `workshop` o ConfigMap `workshop-db` (`DB_URL`) e o Secret `workshop-db` (`DB_USERNAME`, `DB_PASSWORD`) | Senha digitada em `secrets.env`: pode divergir da senha real do banco |
| D7 | Duas stacks por ambiente | `cluster` e `database` separadas; a pipeline aplica `database` em um step próprio ("Deploy do banco") | Uma stack só: o "deploy do banco" ficaria escondido no mesmo `apply` do cluster |
| D8 | Imagem no cluster | Pipeline e AWS puxam `ghcr.io/<owner>/workshop-management-system:<sha>`; no PC, `kind load` de `workshop-management-system:local` | Puxar do GHCR também no PC: exigiria push a cada teste local |
| D9 | Visibilidade do pacote GHCR | Público (a imagem não contém segredos) | Privado: exigiria `imagePullSecret` com PAT no kind e no EKS |
| D10 | Seeds de demonstração | Overlay `local` (inclui pipeline): `dev` + `APP_SEED_ENABLED=true`. Overlay `aws`: perfil padrão, sem seeds | Seeds na AWS: AWS é tratada como "produção" da demonstração; os dados são criados pela collection |
| D11 | Teste de carga | k6 rodando pelo container `grafana/k6` (sem instalar nada no Windows) | `hey`: sem cenário/limiares; JMeter: pesado e baseado em XML |
| D12 | Pipeline AWS | Workflow separado `cd-aws.yml`, só `workflow_dispatch` | Disparar no push: falharia em toda sessão em que o Learner Lab está desligado |
| D13 | Mapeamentos do WireMock | Continuam em `docker/wiremock/mappings`; o ConfigMap é criado a partir dessa pasta pelo comando de deploy | Mover para `k8s/`: mudaria caminhos documentados por outra feature (`purchase-order-creation`) |

## Interfaces e fluxo de dados

### Contrato de variáveis de ambiente (D1)

Mesmos nomes no compose, no Kubernetes e na pipeline. "Origem K8s" indica de onde o pod recebe o valor.

| Variável | Sensível | Origem K8s | Local (kind) | AWS |
|---|---|---|---|---|
| `DB_URL` | Não | ConfigMap `workshop-db` (Terraform) | `jdbc:mysql://mysql.workshop.svc.cluster.local:3306/workshop` | endpoint do RDS |
| `DB_USERNAME`, `DB_PASSWORD` | Sim | Secret `workshop-db` (Terraform) | gerados | gerados |
| `SPRING_PROFILES_ACTIVE` | Não | ConfigMap do overlay | `dev` | `default` |
| `APP_SEED_ENABLED` | Não | ConfigMap do overlay | `true` | `false` |
| `SUPPLIER_BASE_URL` | Não | ConfigMap base | `http://supplier-simulator:8080` | idem |
| `SUPPLIER_CONNECT_TIMEOUT`, `SUPPLIER_READ_TIMEOUT` | Não | ConfigMap base | `2s`, `5s` | idem |
| `SUPPLIER_API_KEY` | Sim | Secret `workshop-app` | vazio permitido | vazio permitido |
| `APP_SECURITY_JWT_SECRET` | Sim | Secret `workshop-app` | valor de dev | environment `aws-academy` |
| `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` | Sim | Secret `workshop-app` | valor de dev | environment `aws-academy` |
| `APP_NOTIFICATION_EMAIL_CHANNEL`, `APP_NOTIFICATION_EMAIL_FROM` | Não | ConfigMap base | `smtp`, `no-reply@workshop.local` | idem |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | Não | ConfigMap base | `mailpit`, `1025` | idem |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Sim | Secret `workshop-app` | vazios | vazios |
| `JAVA_TOOL_OPTIONS` | Não | `ENV` da imagem | `-XX:MaxRAMPercentage=75.0` | idem |
| `TZ` | Não | ConfigMap base | `America/Sao_Paulo` | idem |

Saem do contrato: `SPRING_DATASOURCE_*`, `DDL_AUTO`/`SPRING_JPA_HIBERNATE_DDL_AUTO` (o `AGENTS.md` fixa `validate`),
`SHOW_SQL` e `JAVA_OPTS`. O Secret `workshop-app` é gerado pelo `secretGenerator` do Kustomize a partir de
`k8s/overlays/<env>/secrets.env` (não versionado). O nome gerado recebe um sufixo de hash, de modo que trocar um
segredo dispara um rolling update.

### Endpoints de saúde (D3)

Endpoints novos, fora de `/api`, sem autenticação, somente `GET`. Não aparecem no OpenAPI (`springdoc` não publica o
Actuator por padrão), mas são documentados no README e na collection Postman (pasta "Health").

| Endpoint | Inclui | Sucesso | Falha |
|---|---|---|---|
| `GET /actuator/health/liveness` | `livenessState` | `200 {"status":"UP"}` | `503 {"status":"DOWN"}` |
| `GET /actuator/health/readiness` | `readinessState`, `db` | `200 {"status":"UP"}` | `503 {"status":"OUT_OF_SERVICE"}` ou `DOWN` |
| `GET /actuator/health` | agregado | `200 {"status":"UP"}` | `503` |

Propriedades:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never
management.endpoint.health.show-components=never
management.endpoint.health.group.readiness.include=readinessState,db
# SMTP de teste fora do ar não deve tirar a API do Service: o e-mail é efeito colateral (ADR-004).
management.health.mail.enabled=false
```

`SecurityConfig` ganha, antes das regras de `/api/**`, `GET /actuator/health`, `/actuator/health/liveness` e
`/actuator/health/readiness` com `permitAll()`. Qualquer outro `/actuator/**` continua sem exposição (404) ou exige
autenticação. A nomenclatura exata das propriedades no Spring Boot 4.1 é confirmada pelos testes do passo 5.

### Imagem (passo 4)

- Build multi-stage mantido. A imagem final não instala pacotes extras e não tem `wait-for-mysql.sh`.
- `USER 1000` (numérico), para que `runAsNonRoot` seja verificável pelo Kubernetes.
- `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"`: o heap segue o limite de memória do container.
- `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`, sem shell, para que o `SIGTERM` chegue à JVM (desligamento gracioso).
- Sem `HEALTHCHECK` no Dockerfile: o Kubernetes ignora essa instrução. O healthcheck do app fica no compose e chama
  `/actuator/health/readiness` com uma ferramenta já presente na imagem base (`curl`, se existir; senão o
  `/dev/tcp` do bash), sem instalar pacotes.
- Compose: `axllent/mailpit` com tag fixa (versão definida no passo 4), `SUPPLIER_API_KEY` repassada, variáveis de D1.

### Manifestos Kubernetes

**Deployment `workshop-app`** (base):

- `strategy: RollingUpdate` com `maxSurge: 1` e `maxUnavailable: 0`, e `progressDeadlineSeconds: 300`. Uma versão que
  nunca fica pronta não derruba a anterior, e `kubectl rollout status` falha após 5 minutos.
- `envFrom`: ConfigMap base, ConfigMap do overlay, ConfigMap `workshop-db`, Secret `workshop-db`, Secret
  `workshop-app`.
- Probes (todas na porta 8080):

  | Probe | Caminho | Parâmetros | Por quê |
  |---|---|---|---|
  | `startupProbe` | `/actuator/health/liveness` | `periodSeconds: 5`, `failureThreshold: 36` (até 3 min) | Flyway e o boot do Spring são lentos no kind; sem ela, a liveness mataria o pod durante o boot |
  | `livenessProbe` | `/actuator/health/liveness` | `periodSeconds: 10`, `failureThreshold: 3` | Reinicia só um processo travado; não depende do banco |
  | `readinessProbe` | `/actuator/health/readiness` | `periodSeconds: 5`, `failureThreshold: 3` | Banco fora: o pod sai do Service sem reiniciar |

- `securityContext`: `runAsNonRoot: true`, `runAsUser: 1000`, `allowPrivilegeEscalation: false`,
  `readOnlyRootFilesystem: true` (com `emptyDir` em `/tmp` para o Tomcat), `capabilities.drop: [ALL]`,
  `seccompProfile: RuntimeDefault`.
- `terminationGracePeriodSeconds: 30`.

**Recursos e escala** (requests = reserva; limits = teto):

| Item | Local (kind e pipeline) | AWS (provisório, confirmado no passo 11) |
|---|---|---|
| App: requests CPU / memória | `250m` / `768Mi` | `250m` / `768Mi` |
| App: limits CPU / memória | `1000m` / `1Gi` | `1000m` / `1Gi` |
| HPA min / max | 1 / 3 | 2 / 4 |
| HPA alvo CPU / memória | 70% / ~~85%~~ removida (nota do CP5) | 70% / ~~85%~~ removida (nota do CP5) |
| HPA scale-down | janela de estabilização de 60 s | 120 s |
| MySQL (local) requests / limits | `250m`, `512Mi` / `1000m`, `1Gi` | RDS (fora do cluster) |
| Mailpit, WireMock requests / limits | `50m`, `64Mi` / `200m`, `128Mi` e `100m`, `256Mi` / `500m`, `384Mi` | idem |

Justificativa: com `MaxRAMPercentage=75` e limite de 1 Gi, o heap máximo fica em ~768 MiB. O consumo ocioso esperado
da JVM (~400–500 MiB) fica abaixo de 85% de 768 Mi (~650 MiB), então a memória não impede a volta ao mínimo. **Risco
conhecido:** a JVM devolve pouca memória ao sistema depois de uma carga, e o HPA usa a maior recomendação entre CPU e
memória. Se a memória segurar a escala, o passo 8 ajusta o alvo de memória ou a request, registrando a evidência no
plano. Soma local no pior caso: ~5,5 GiB, por isso o requisito de ≥ 6 GiB para o Docker Desktop/WSL2.

> **Nota (2026-10-18, CP5):** o risco acima se confirmou no teste de carga (2 pods ociosos a 48% de memória →
> `ceil(2 × 48 / 85) = 2`, sem voltar a 1). A métrica de memória foi **removida** do HPA (só CPU a 70%) nos dois
> ambientes; a memória continua limitada pelo `limit` de 1 Gi e pelo `MaxRAMPercentage`. Evidências e desvio no plano.

**Service `workshop-app`:** `ClusterIP` na base (porta 80 → 8080). O overlay `local` troca para `NodePort` 30080; o
overlay `aws`, para `LoadBalancer` com `loadBalancerSourceRanges` (ver "Segurança e operação"). O Service do Mailpit
também ganha `NodePort` 30025 no local, para ver os e-mails em `localhost:8025`.

**Kustomize:** `k8s/base` + `k8s/overlays/{local,aws}`, com `namespace: workshop` no overlay. O objeto `Namespace` **não**
fica no Kustomize, porque é criado pela stack Terraform `database`, que precisa dele antes do app. A tag da imagem é
definida no overlay (`images:`) e sobrescrita pela pipeline com `kustomize edit set image` antes do `apply`.

### Terraform

Comum: `required_version >= 1.10`, provedores com versão fixada e `.terraform.lock.hcl` **versionado** (garante as
mesmas versões de provider em todo `init`). As versões exatas são fixadas no plano após consulta ao registry.

**`infra/local/cluster`** (state local):

- provider `tehcyx/kind`: cluster `workshop` com 1 node (control-plane), `extraPortMappings` 30080→8080 e 30025→8025,
  e kubeconfig gravado no contexto `kind-workshop`;
- provider `helm`: chart oficial `metrics-server` (kubernetes-sigs) com `--kubelet-insecure-tls`. O kubelet do kind
  usa certificado autoassinado, que o metrics-server rejeitaria; a flag é aceitável só em cluster local descartável;
- outputs: nome do cluster e contexto do kubeconfig.

**`infra/local/database`** (state local, aplicada depois de `cluster`):

- provider `kubernetes` (contexto `kind-workshop`) e `random`;
- `kubernetes_namespace_v1` `workshop`;
- Secret `mysql-root` e Secret/ConfigMap `workshop-db` (D6);
- `kubernetes_stateful_set_v1` `mysql` (`mysql:8.4`, 1 réplica, `volumeClaimTemplates` de 1 Gi, readiness por
  `mysqladmin ping`) e `kubernetes_service_v1` `mysql` (ClusterIP, porta 3306);
- `wait_for_rollout`: o `apply` só termina com o MySQL pronto, e é isso que a pipeline chama de "Deploy do banco".

Os dados sobrevivem a `kubectl delete pod mysql-0`, porque o PVC continua existindo. São apagados no `destroy` da
stack (comportamento esperado de ambiente descartável).

**`infra/aws/*`** (passo 11; desenho provisório, sujeito às restrições do Learner Lab):

- `bootstrap`: bucket S3 com versionamento, criptografia e bloqueio de acesso público; state local deste stack;
- `cluster` (backend S3 com `use_lockfile = true`, sem DynamoDB): VPC `10.0.0.0/16` em 2 AZs, 2 subnets públicas
  (load balancer e NAT) e 2 privadas (nodes e RDS), IGW, **1** NAT Gateway (custo), EKS e node group com
  `data.aws_iam_role.lab_role`, 2 nodes `t3.medium`, add-on `metrics-server` do EKS;
- `database` (backend S3): RDS MySQL 8.4 `db.t3.micro`, single-AZ, 20 GiB `gp3`, `publicly_accessible = false`,
  security group liberando 3306 só para o security group dos nodes, `skip_final_snapshot = true`,
  `deletion_protection = false`, namespace e ConfigMap/Secret `workshop-db` no EKS (provider `kubernetes` autenticado
  por `aws eks get-token`). Lê as saídas de `cluster` por `terraform_remote_state`.

Se a confirmação do Learner Lab invalidar esse desenho (por exemplo, EKS ou NAT indisponíveis), esta spec volta a
`Draft` antes do passo 11.

### Pipelines

**`cd.yml`: entrega com kind no job** (environment `kind-ci` do fork).

```yaml
on:
  push:
    branches:
      - feat/terraform-san   # ÚNICO ponto com o nome da branch (documentado no README)
```

| Job | `needs` | Steps principais | Permissões |
|---|---|---|---|
| `build-test` | — | `./mvnw clean verify` (build + testes + gate JaCoCo) | `contents: read` |
| `image` | `build-test` | `docker build`; login no GHCR com `GITHUB_TOKEN`; push `ghcr.io/<owner minúsculo>/workshop-management-system:${{ github.sha }}` | `contents: read`, `packages: write` |
| `deploy-kind` | `image` | `terraform fmt -check`/`validate`; **Provisionar cluster** (`infra/local/cluster` apply); **Deploy do banco** (`infra/local/database` apply); gerar `secrets.env` a partir dos secrets do environment; criar o ConfigMap do WireMock; `kustomize edit set image`; `kubectl apply -k k8s/overlays/local`; **Smoke test** | `contents: read` |

- Registry e nome da imagem em `env:` no topo do workflow (`REGISTRY`, `IMAGE_NAME`), sem repetição.
- Actions de terceiros fixadas por SHA, como no `ci.yml`; `persist-credentials: false` no checkout.
- `concurrency` por ref com `cancel-in-progress: true` (cada execução tem o próprio cluster).
- Timeouts: `build-test` 30 min, `image` 15 min, `deploy-kind` 25 min. Meta: execução completa em até 40 minutos.
- **Smoke test** (falha em qualquer item reprova o job):
  1. `kubectl rollout status deployment/workshop-app -n workshop --timeout=300s`;
  2. `GET http://localhost:8080/actuator/health/readiness` → `200` e `"UP"`;
  3. `POST /api/auth/login` com o admin de bootstrap → `200` com token (mascarado com `::add-mask::`);
  4. `GET /api/service-orders` com o token → `200`.

  Em caso de falha: `kubectl get pods`, `describe` e as últimas 200 linhas do log do app, como diagnóstico.
- `ci.yml` e `security-scan.yml` não mudam.

**`cd-aws.yml`: deploy na AWS** (environment `aws-academy`, `workflow_dispatch` com input `image_sha` cujo padrão é o
SHA do commit). Pré-condição: o desenvolvedor já aplicou `infra/aws/cluster` naquela sessão. Steps: credenciais da
sessão → `aws sts get-caller-identity` (falha rápida e explícita com credencial expirada) → **Deploy do banco**
(`infra/aws/database` apply) → `aws eks update-kubeconfig` → `secrets.env` → `kubectl apply -k k8s/overlays/aws` →
smoke test pelo hostname do load balancer. O detalhamento fica para o passo 11.

**Secrets e variáveis por environment** (nunca no nível do repositório):

| Environment | Secrets | Variables |
|---|---|---|
| `kind-ci` | `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` | — |
| `aws-academy` | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` | `AWS_REGION`, `TF_STATE_BUCKET`, `AWS_ALLOWED_CIDR` |

### Teste de carga (D11)

`load-test/hpa-load.js` (k6), executado com
`docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6:<versão fixa> run - < load-test/hpa-load.js`.
No `setup()`, faz login uma vez; depois, rampa de usuários virtuais por ~5 minutos chamando `GET /api/service-orders`
autenticado e, numa fração das iterações, `POST /api/auth/login` (o BCrypt gera consumo de CPU previsível). Os limiares
são `http_req_failed < 1%` e `p(95) < 2s`. A observação é feita com `kubectl get hpa -n workshop -w` e
`kubectl top pods -n workshop`.

### Comandos de conveniência (Makefile)

`infra-local-up`, `infra-local-down`, `k8s-local-deploy` (build da imagem `:local` + `kind load` + ConfigMap do WireMock
+ `apply -k`), `k8s-local-delete` e `load-test`. São atalhos documentados. Durante o aprendizado, o Santiago executa
os comandos `terraform`/`kubectl` diretamente.

### Tradução de falhas

| Falha | Comportamento |
|---|---|
| Banco indisponível | readiness `503`; o pod sai do Service; a liveness segue `200`; não há reinício |
| Banco indisponível no boot | Flyway falha, o processo encerra e o Kubernetes reinicia com backoff até o banco voltar |
| Versão nova não fica pronta | rollout travado com os pods antigos atendendo; `rollout status` falha em 300 s; correção com `kubectl rollout undo` |
| Imagem inexistente | `ImagePullBackOff`; mesmo efeito do item acima |
| Credencial AWS expirada | `aws sts get-caller-identity` falha antes de qualquer `apply` |
| Lock do state ocupado | `terraform apply` falha sem alterar recursos; liberar só após confirmar que não há outro `apply` em curso |

## Persistência e dados de bootstrap

- **Schema:** nenhuma migration nova. Flyway continua o único mecanismo e `ddl-auto=validate` permanece; o compose
  deixa de poder sobrescrever esse valor.
- **Classificação de seed desta feature:** **nenhum seed requerido.** A infraestrutura não cria dados de negócio.
- Dados existentes, sem alteração: o admin de bootstrap (referência obrigatória, via migration) e os seeders de
  demonstração (`@Profile("dev")` + `app.seed.enabled=true`), habilitados só no overlay `local` (D10).
- Correção pontual: remover a primeira linha duplicada `app.seed.enabled=${APP_SEED_ENABLED:true}` de
  `application.properties`. O comportamento efetivo não muda (a segunda declaração, `false`, já prevalece).
- Banco local: o PVC preserva os dados entre reinícios do pod. O `destroy` apaga tudo. Banco AWS: sem backup
  (fora de escopo), destruído ao fim de cada uso.

## Segurança e operação

| Tema | Tratamento |
|---|---|
| Segredos no repositório | Nenhum. `secrets.env`, `*.tfvars`, `*.tfstate*`, `.terraform/` e kubeconfig entram no `.gitignore` (passo 6) antes do primeiro Terraform. Só `secrets.env.example` com valores rotulados "desenvolvimento local" é versionado |
| Segredos no state | O state contém a senha do banco. Local: arquivo fora do git. AWS: S3 com criptografia, versionamento e bloqueio público |
| Segredos na pipeline | Só de environments do fork; `secrets.env` escrito com `umask 077` no runner efêmero; token do smoke test mascarado; nada de `set -x` nos steps com segredo |
| Admin de bootstrap com senha conhecida | **Achado alto** quando exposto publicamente. Mitigação: no EKS, `loadBalancerSourceRanges` restrito a `AWS_ALLOWED_CIDR` (o IP do desenvolvedor), ambiente destruído após cada uso. No kind, a exposição é só `localhost`. A troca da senha do admin fica fora de escopo (feature de `identity`) |
| Endpoints de saúde | Sem autenticação, mas sem detalhes nem componentes; só `health` exposto no Actuator |
| Container | Não-root numérico, sem escalonamento de privilégio, filesystem somente leitura, capabilities removidas |
| Rede AWS | Nodes e RDS em subnets privadas; RDS só aceita conexões vindas dos nodes; nenhum `0.0.0.0/0` de entrada exceto o load balancer, que é limitado por `loadBalancerSourceRanges` |
| IAM AWS | Só a `LabRole`; nenhuma role ou policy criada |
| `--kubelet-insecure-tls` | Aceito apenas no kind; na AWS é usado o add-on do EKS, sem a flag |
| Dependências novas | `spring-boot-starter-actuator` (gerenciado pelo BOM do Boot), imagens com tag fixa e actions fixadas por SHA |
| Custo AWS | Estimativa preliminar ≈ US$ 0,27/h (EKS US$ 0,10, 2×`t3.medium` ≈ 0,083, NAT ≈ 0,045, `db.t3.micro` ≈ 0,017, CLB ≈ 0,025, EBS). Recalculada e apresentada antes do primeiro `apply` |
| Rollout e recuperação | Rolling update sem indisponibilidade; `kubectl rollout undo`; recriar ambiente local com `destroy` + `apply` |

## Estratégia de testes

| Camada | Teste | Critério coberto |
|---|---|---|
| HTTP (MockMvc, `@SpringBootTest`) | liveness e readiness `200 {"status":"UP"}` sem token; o corpo não contém `components`/`details`; `/actuator/env` e `/actuator/beans` não acessíveis | Saúde: sem autenticação e sem detalhes |
| HTTP | Com o indicador `db` substituído por um que responde `DOWN`: readiness `503` e liveness `200` | "Viva" independe do banco; "pronta" depende |
| Segurança | Endpoints de `/api/**` continuam exigindo token (regressão do `SecurityConfig`) | Contratos HTTP inalterados |
| Fronteiras | `ModuleStructureTest` verde | Modulith |
| Quality gate | `make verify` e JaCoCo ≥ 80% | AGENTS.md |
| Container (manual, passo 4) | `docker compose up` → healthcheck `healthy`; `docker run ... id -u` = 1000; `docker history`/`inspect` sem segredos | Containers |
| IaC (pipeline) | `terraform fmt -check` e `terraform validate` nas stacks; `kubectl kustomize` dos dois overlays | Higiene da IaC |
| Deploy (pipeline) | Smoke test descrito acima | Pipeline e smoke test |
| Resiliência (manual, passo 8) | `kubectl delete pod` → nova réplica; `kubectl delete pod mysql-0` → dados preservados; `kubectl set env deployment/workshop-app DB_URL=jdbc:mysql://inexistente:3306/x` → pods antigos continuam atendendo, depois `rollout undo` | Resiliência e atualização sem queda |
| Escala (manual, passo 8) | k6 + `kubectl get hpa -w`: sobe acima do mínimo, respeita o máximo e volta ao mínimo | Escalabilidade |
| Pipeline negativa (manual, passo 9) | Commit temporário com teste quebrado → `image` não roda; push em outra branch → `cd.yml` não dispara | Ordem e gatilho |

As evidências (saídas de comandos, links das execuções) ficam no `implementation-plan.md`.

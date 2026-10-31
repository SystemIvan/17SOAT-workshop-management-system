# Plano de Implementação: Infraestrutura Kubernetes, IaC e entrega contínua

| Campo | Valor |
|---|---|
| Feature | `kubernetes-infrastructure-delivery` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-27 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-14 |
| Especificação técnica | `./technical-spec.md` (Approved, Santiago Silvestre, 2026-10-14) |

## Premissas

- Branch `feat/terraform-san`, renomeada em 2026-10-27 para `feat/platform-kubernetes-infrastructure-delivery`
  (convenção `{type}/{bounded-context}-{feature}` do `AGENTS.md`). As menções antigas são registro histórico. Cada
  checkpoint termina com uma mensagem de commit **sugerida**; o commit só acontece com pedido explícito.
- O Santiago executa os comandos principais (`docker build/compose`, `terraform`, `kubectl apply`, k6, observação do
  HPA) e cola as saídas; as evidências são registradas aqui.
- A abertura de OS completa (passo 3) é uma feature própria em `docs/features/servicelifecycle/` e não faz parte deste
  plano. Ela só precisa estar pronta antes do vídeo (CP7).
- Ao fim de cada checkpoint, o `docs/learning/glossario.md` é atualizado com os conceitos novos.

## Pré-requisitos da máquina local (verificar no CP4)

| Item | Exigência | Verificação |
|---|---|---|
| Docker Desktop (WSL2) | rodando, com ≥ 6 GiB de memória e ≥ 4 CPUs | `docker info --format '{{.MemTotal}} {{.NCPU}}'` |
| Terraform | ≥ 1.10 | `terraform version` |
| kubectl | já instalado (Docker Desktop) | `kubectl version --client` |
| kind / helm / k6 | não precisam ser instalados: o provider `tehcyx/kind` e o provider `helm` embutem as bibliotecas, e o k6 roda por container | — |

## Checkpoints

### CP1 — Container (passo 4, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] `Dockerfile`: remover `wait-for-mysql.sh` e a instalação de pacotes; `USER 1000`;
      `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"`; `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.
- [x] Apagar `docker/wait-for-mysql.sh`.
- [x] `docker-compose.yml`: contrato D1 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); remover `DDL_AUTO`/`SHOW_SQL`;
      repassar `SUPPLIER_API_KEY`; Mailpit com tag fixa (`v1.31.4`). O healthcheck do app em
      `/actuator/health/readiness` foi movido para o CP2, quando o endpoint existir (`curl` já existe na imagem base).
- [x] `.env.example`: remover `JAVA_OPTS`, `DDL_AUTO` e `SHOW_SQL`; documentar `SUPPLIER_API_KEY`.
- [x] `application.properties`: linha duplicada `app.seed.enabled` — **N/A**: na leitura de 2026-10-17 o arquivo tem
      só `app.seed.enabled=false`, e o `application-dev.properties` reativa por `APP_SEED_ENABLED` (comportamento
      esperado). Nada a remover.
- [x] Atualizar as referências do README (e do `DOCKER.md`) ao compose e ao `wait-for-mysql.sh`.
- [x] Extra (achado da abertura de OS, sessão 4): `.gitattributes` com `*.sh text eol=lf`.

Verificação: `docker compose up -d --build` → API responde em `localhost:8080`; `docker compose exec app id -u` = `1000`;
`docker image inspect` sem segredos em `Env`; `make verify` verde.

Commit sugerido: `refactor(platform): align container image and compose with env var contract`

### CP2 — Saúde (passo 5, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] `pom.xml`: `spring-boot-starter-actuator`.
- [x] Propriedades `management.*` da technical-spec. **Desvio de implementação:** ficam em
      `src/main/resources/actuator.properties`, importado por `spring.config.import` tanto pelo `application.properties`
      principal quanto pelo de testes. O de testes tem o mesmo nome e sombreia o principal; sem o import, os testes
      validariam uma cópia e não detectariam, por exemplo, a remoção de `show-details=never`. As propriedades e os
      valores são os da technical-spec.
- [x] `SecurityConfig`: `permitAll()` só para `GET` nos três caminhos de health.
- [x] Testes MockMvc (`HealthEndpointTest`, `HealthEndpointDatabaseDownTest`): os três caminhos `200 UP` sem token e
      sem `components`/`details`; `POST` → `401`; `/actuator/env` e `/actuator/beans` → `401` sem token e `404` com
      token de `ADMIN` (não expostos); com o indicador `dbHealthIndicator` em `DOWN`, readiness `503` e liveness `200`.
      Mutação manual (readiness sem `db` + `show-components=always`) → 3 testes vermelhos, depois restaurado.
- [x] Healthcheck do app no compose (`curl -fsS` na readiness; `start_period: 180s`).
- [x] Postman: pasta "Health" (`Liveness probe`, `Readiness probe`), primeiros passos do `make e2e`; README com a
      seção "Verificação de saúde" (e remoção das mensagens do antigo `wait-for-mysql.sh`, que ficaram do CP1).

Verificação: `make verify` verde (incluindo `ModuleStructureTest` e JaCoCo ≥ 80%); `docker compose ps` mostra o app
`healthy`; `docker compose stop mysql` → readiness `503` e liveness `200`; `docker compose start mysql` → readiness
volta para `200`.

Commit sugerido: `feat(platform): expose liveness and readiness health probes`

### CP3 — `.gitignore` (passo 6, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] Ignorar `**/.terraform/`, `*.tfstate`, `*.tfstate.*`, `*.tfvars` (exceto `*.tfvars.example`), `crash.log`,
      `kubeconfig*` e `k8s/overlays/*/secrets.env`. Extras: `*.tfplan` (plano salvo contém os mesmos valores
      sensíveis do state) e `crash.*.log`.
- [x] **Não** ignorar `.terraform.lock.hcl`.
- **Nota para o CP4:** o provider `tehcyx/kind` grava, por padrão, o kubeconfig como `<nome-do-cluster>-config` na
  pasta da stack, nome que **não** casa com `kubeconfig*` (verificado com o arquivo fictício `local-kind-config`).
  No CP4, definir `kubeconfig_path` explicitamente com um nome que comece por `kubeconfig`.

Verificação: criar arquivos fictícios (`infra/local/cluster/terraform.tfstate`, `k8s/overlays/local/secrets.env`) e
confirmar com `git status --ignored` / `git check-ignore -v` que ficam ignorados; depois apagá-los.

Commit sugerido: `chore(platform): ignore terraform state, tfvars, kubeconfig and k8s secrets`

### CP4 — `infra/local` (passo 7, previsto 19/10)

**Status: Completed (2026-10-17, adiantado).**

- [x] Verificar os pré-requisitos da máquina e registrar a saída. O Docker tinha 2,84 GiB/2 CPUs, limitado pelo
      `~/.wslconfig`; ajustado para `memory=7GB`, `processors=5`, `swap=2GB` e `autoMemoryReclaim=gradual` (máquina
      com 16 GB/12 CPUs lógicas).
- [x] Consultar o Terraform Registry e fixar as versões (2026-10-17): `tehcyx/kind ~> 0.11.0`,
      `hashicorp/helm ~> 3.3.0`, `hashicorp/kubernetes ~> 3.3.0`, `hashicorp/random ~> 3.9.0` e chart
      `metrics-server` `3.14.0` (app `0.9.0`). Node `kindest/node:v1.35.0` fixado por digest (imagem padrão do kind
      v0.31.0, embutido no provider).
- [x] `infra/local/cluster`: cluster `workshop` (1 node, `extraPortMappings` 30080→8080 e 30025→8025) e
      metrics-server com `--kubelet-insecure-tls`; outputs. **Desvio da nota do CP3:** o kubeconfig é mesclado em
      `~/.kube/config` (fora do repositório) em vez de um arquivo `kubeconfig*` na stack, de modo que nenhuma
      credencial nasce dentro do projeto; o `destroy` remove o contexto (comportamento lido no código do provider).
- [x] `infra/local/database`: namespace `workshop`, `random_password`, Secret `mysql-root`, ConfigMap/Secret
      `workshop-db`, StatefulSet `mysql` com PVC de 1 Gi e Service `mysql`. Readiness por `mysqladmin ping` em
      `127.0.0.1` e, de propósito, sem liveness (a primeira inicialização leva ~95 s).
- [x] `.terraform.lock.hcl` das duas stacks versionados, com checksums de `windows_amd64` e `linux_amd64`
      (`terraform providers lock`), para a pipeline (CP6) conferir os mesmos binários.
- [x] Makefile: `infra-local-up` e `infra-local-down`.

Verificação (Santiago executa): `terraform init/plan/apply` nas duas stacks, em ordem; `kubectl get nodes` → `Ready`;
`kubectl top nodes` responde (metrics-server ok); `kubectl get pods,pvc -n workshop` → `mysql-0` `Running` e PVC
`Bound`; `terraform destroy` (database, depois cluster) remove tudo e `docker ps` não mostra mais o node do kind.

Commit sugerido: `feat(platform): provision local kind cluster, metrics-server and mysql with terraform`

### CP5 — Manifestos e HPA (passo 8, previsto 20/10)

**Status: Completed (2026-10-18): 10 de 10 critérios; deploy em 2026-10-17, teste de carga/HPA em 2026-10-18.**

- [x] `k8s/base`: Deployment, Service, ConfigMap, HPA, Mailpit e WireMock, conforme a technical-spec.
- [x] `k8s/overlays/local`: NodePort 30080/30025, HPA 1–3, perfil `dev` com seeds, imagem
      `workshop-management-system:local`, `secretGenerator` e `secrets.env.example`.
- [x] Makefile: `k8s-local-deploy` (build `:local`, `kind load`, ConfigMap do WireMock a partir de
      `docker/wiremock/mappings`, `kubectl apply -k`), `k8s-local-delete` e `load-test`.
- [x] `load-test/hpa-load.js` (k6) com limiares `http_req_failed < 1%` e `p(95) < 2s`.

Desvios registrados:

- **ConfigMap do app:** em vez de `app-configmap.yaml` + um segundo ConfigMap no overlay, a base usa
  `configMapGenerator` (`workshop-app-config`, a partir de `app-config.env`) e o overlay faz `behavior: merge` com
  `SPRING_PROFILES_ACTIVE=dev`/`APP_SEED_ENABLED=true`. Motivo: um único ConfigMap com hash no nome (mudança de
  configuração dispara rolling update) e a base continua válida sozinha para o overlay `aws`.
- **`kind load`:** o CLI do kind não é pré-requisito, então o deploy faz o que o `kind load docker-image` faz por
  dentro: `docker save --platform linux/<arch>` → `docker cp` para o node → `ctr --namespace=k8s.io images import`.
  O `--platform` evita a falha de import com o containerd image store do Docker Desktop (atestações sem conteúdo).
  O destino é `/var/tmp`, não `/tmp`: no node do kind, `/tmp` é `tmpfs`, e o `docker cp` gravou por baixo da montagem
  (`ctr: open /tmp/...: no such file or directory`, observado em 2026-10-17).
- **k6:** o script é montado por volume (`-v .../load-test:/scripts:ro`) em vez de `run - < arquivo`, porque o
  PowerShell 5.1 não aceita o operador `<`; `--add-host=host.docker.internal:host-gateway` para funcionar também no
  Linux (pipeline). Imagem `grafana/k6:1.3.0`.
- **HPA 1–3 e janela de 60 s ficam na base** (valores locais); o overlay `aws` sobrescreve.
- **`startupProbe` 3 → 5 min (`failureThreshold` 36 → 60), calibração aprovada pelo Santiago (2026-10-17):** o
  primeiro boot em banco vazio levou **165,6 s** (Flyway aplicou 25 migrations em 2m18s), a 14 s do limite de 180 s
  da technical-spec; com o banco já migrado, o pod recriado ficou pronto em 27 s. Matar o container no meio de uma
  migration deixaria DDL do MySQL (não transacional) pela metade. 5 min cabem no `progressDeadlineSeconds: 300`. É
  um valor calibrado com evidência, não estrutural: a lentidão do MySQL local (~5,5 s por migration) está em
  investigação.
- **MySQL local com durabilidade relaxada (aprovado pelo Santiago, 2026-10-18):** `args` `--skip-log-bin`,
  `--innodb-flush-log-at-trx-commit=2` e `--innodb-doublewrite=OFF` só na stack `infra/local/database` (banco
  descartável; não se aplica ao RDS). Flyway 2m18s → **1m55s**, boot 165,6 s → **139,6 s** (−17%). A causa principal
  não é o commit: o `execution_time` do `flyway_schema_history` mostrou DML em 2–5 ms e DDL lento, e um teste num banco
  descartável mediu `INSERT`/`UPDATE` 0,00 s, `ADD COLUMN` (instantâneo) 0,27 s, `CREATE TABLE` 1,23 s e
  `ADD CONSTRAINT CHECK` (reconstrói a tabela) 1,50 s. O custo é criar e sincronizar arquivos `.ibd` no disco do kind
  sobre WSL2, que nenhuma flag desliga. A startupProbe de 5 min continua (folga suficiente). Sem dívida técnica.
- **Observação para o time (fora do escopo):** consolidar (*squash*) as 25 migrations num schema só com `CREATE`s
  eliminaria os `ALTER`s do primeiro boot (estimativa: ~25–30 s de Flyway), mas muda checksums, exige recriar todos os
  bancos e afeta as branches de todos; o `AGENTS.md` só permite isso antes do baseline operacional e com spec aprovada.
- **HPA só por CPU (aprovado pelo Santiago, 2026-10-18):** no 1º teste de carga a escala desceu 3 → 2 e travou: 2 pods
  ociosos a 48% de memória → `ceil(2 × 48 / 85) = 2` (`describe hpa`: `ScalingActive ... from memory resource
  utilization`), porque a JVM não devolve o heap. A métrica de memória foi removida do `app-hpa.yaml` (base, vale para
  local e AWS); a memória segue limitada pelo `limit` de 1 Gi e pelo `MaxRAMPercentage`. Nota na technical-spec, sem
  voltar a Draft: o risco já estava previsto e o comportamento pedido (escalar e voltar ao mínimo) não muda.
- **Imagem `:local` reconstruída:** o Deployment não muda (mesma tag), então o deploy faz `rollout restart` quando ele
  já existe.

Verificação (Santiago executa e registra):

| Critério | Comando / ação | Esperado |
|---|---|---|
| Deploy pronto | `kubectl rollout status deployment/workshop-app -n workshop` | `successfully rolled out` |
| Migrations | log do app | Flyway aplicou as migrations; sem erro de `validate` |
| Postman | collection contra `http://localhost:8080` | fluxo do README passa |
| ConfigMap/Secret | `kubectl describe deployment workshop-app -n workshop` | `envFrom` com ConfigMaps e Secrets; valores sensíveis não exibidos |
| Recriação de pod | `kubectl delete pod <app>` | pod novo criado automaticamente |
| Dados persistem | criar cliente, `kubectl delete pod mysql-0`, consultar de novo | cliente continua existindo |
| Atualização sem queda | `kubectl set env deployment/workshop-app DB_URL=jdbc:mysql://inexistente:3306/x` | pod novo nunca fica pronto e o antigo continua respondendo; depois `kubectl rollout undo` |
| Escala sobe | k6 + `kubectl get hpa -n workshop -w` | réplicas > 1 e ≤ 3 |
| Escala desce | aguardar sem carga | réplicas voltam a 1 (se a memória segurar, ajustar alvo/request e registrar aqui) |
| Mesmo endereço | k6 apontado só para `localhost:8080` | sem troca de URL durante a escala |

Commit sugerido: `feat(platform): add kustomize manifests, hpa and k6 load test for local cluster`

### CP6 — Pipeline com kind (passo 9, previsto 21/10)

**Status: Completed (2026-10-20, adiantado): 5 de 5 critérios.**

- [x] Criar o fork pessoal (`SantiagoSilvestre/17SOAT-workshop-management-system`); environment `kind-ci` com
      `APP_SECURITY_JWT_SECRET` e `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` (48 bytes aleatórios do
      `RandomNumberGenerator`; `Keys.hmacShaKeyFor` exige ≥ 32 bytes).
- [x] `.github/workflows/cd.yml` com jobs `build-test` → `image` → `deploy-kind`, gatilho só em `feat/terraform-san`
      (um único ponto), `REGISTRY`/`IMAGE_NAME` em `env:`, actions fixadas por SHA e smoke test.
- [x] Tornar o pacote GHCR público depois do primeiro push (D9): já nasceu público (ver desvios).
- [x] README: onde fica o nome da branch no workflow e como trocá-lo (seção "Pipeline de entrega").

Desvios registrados:

- **Dono da imagem calculado:** `${GITHUB_REPOSITORY_OWNER,,}` (minúsculas, exigência do GHCR) em vez de um nome fixo;
  o job `image` exporta a referência completa como `outputs.image` e o `deploy-kind` a consome. No repositório do grupo,
  o mesmo arquivo publica em `ghcr.io/systemivan/...` sem edição.
- **Login no GHCR por `docker login --password-stdin`** com o `GITHUB_TOKEN`, sem `docker/login-action`/
  `build-push-action`: uma action de terceiros a menos para fixar por SHA. Terraform pela `hashicorp/setup-terraform`
  v4.0.1 (`dfe3c3f8...`), versão `1.16.6` igual à local, `terraform_wrapper: false`.
- **Pacote GHCR público sem ação manual:** o pull anônimo do manifest `:d902d1b...` respondeu `200` logo após o
  primeiro run. O pacote publicado pelo `GITHUB_TOKEN` fica ligado ao repositório de origem e herdou a visibilidade
  pública do fork.
- **Retry na readiness do smoke test (`46cd5b2`):** o run `39363382338` falhou com
  `curl: (56) Recv failure: Connection reset by peer` 13 ms depois de `successfully rolled out` (pod `1/1`, 0
  restarts). O pod fica Ready antes de o kube-proxy programar o endpoint na regra do NodePort; o `curl` passou a usar
  `--retry 12 --retry-delay 5 --retry-all-errors --max-time 5` (até ~1 min). Um app que nunca fica pronto continua
  reprovando o job.
- **Commit de teste revertido:** `8a9f474` (teste que falha de propósito) desfeito por `git revert` (`1cc7409`), sem
  reescrever o histórico já publicado no fork.

Verificação: execução verde no fork, com link registrado e duração total ≤ 40 min; commit temporário com teste
quebrado → `image` e `deploy-kind` não rodam (depois reverter); push em outra branch → `cd.yml` não dispara; logs sem
segredos (procurar pelos valores); `ci.yml` intacto (`git diff` vazio no arquivo).

Commit sugerido: `feat(platform): add delivery pipeline with ephemeral kind cluster and smoke test`

### CP7 — README local e vídeo de garantia (passo 10, previsto 22/10)

**Status: Completed (2026-10-27): documentação reorganizada (2026-10-20 a 2026-10-25) e guia local verificado do zero
pelo Santiago em 2026-10-27.**

**Escopo ampliado (aprovado por Santiago Silvestre em 2026-10-20):** além de documentar o ambiente local, o README de
776 linhas é reorganizado em documentos com uma responsabilidade cada (Diátaxis: tutorial, how-to, referência,
explicação). O README vira a capa (≤ 150 linhas) e o detalhe vai para `docs/`. `docs/Architecture.md`,
`docs/Architecture-Decisions.md`, `docs/adr/` e `docs/features/` não mudam de lugar, porque o `CLAUDE.md`, as regras
por épico e o agente `architecture-reviewer` apontam para eles. Referências ao `README.md` em specs antigas são
registro histórico e não são reescritas. É uma mudança só de documentação (plano conciso do `AGENTS.md`), dividida em
commits focados:

- [x] 1. Mover as seções do README para `docs/setup/`, `docs/operations/`, `docs/api/` e `CONTRIBUTING.md` sem
      reescrever o texto (só títulos e alvos de links mudam); incorporar o `DOCKER.md` e remover o `HELP.md`
      (boilerplate do Spring Initializr); apontar para os novos caminhos as referências vivas (glossário, comentário do
      `cd.yml`, descrição da collection, `manual-test-guide.md` do RF40). Verificado por comparação linha a linha:
      nenhuma linha de conteúdo perdida.
- [x] 2. `docs/architecture/overview.md`: visão C4 (contexto, containers, módulos, implantação local e pipeline) com 5
      diagramas mermaid, escrita a partir do código atual (dependências entre módulos, listeners, HPA, namespaces). A
      seção "Arquitetura" do README foi absorvida e virou link. Verificado: os 5 diagramas renderizam no
      `mermaid-cli` 11.4.2 e o link checker não acusou links quebrados. Divergências encontradas e **não
      corrigidas** (documentos compartilhados, fora do escopo): a seção "Current Repository State" do `docs/Architecture.md` ainda descreve os
      módulos `customer/technician/parts`; o `docs/PROJECT-STRUCTURE.md` não cita o módulo `identity`; o README dizia
      "MySQL 8.0", mas o compose e o kind usam `mysql:8.4`.
- [x] 3. `docs/setup/local-kubernetes.md` (Terraform → deploy no kind → verificação → destroy, com equivalentes sem
      `make`, recursos criados por stack) e `docs/operations/load-testing-hpa.md`; traduzir e deduplicar o conteúdo
      vindo do `DOCKER.md` (a frase "Mailpit is not deployed to Kubernetes" está desatualizada).
      `configuration.md` virou referência completa, gerada a partir do `application.properties`, com a origem de
      cada valor por ambiente. Corrigido nessa revisão: o `DOCKER.md` dizia que o canal de e-mail padrão era `smtp`,
      mas o padrão da aplicação é `log` (quem liga o `smtp` é o compose e o ConfigMap). Fatos conferidos nos
      manifestos e no Terraform (`imagePullPolicy`, tipos de Service, PVC `data-mysql-0`, `random_password` de 24) e
      resultados do HPA tirados das evidências do CP5. Link checker sem links quebrados. Pendente para a verificação
      do CP7: seguir o `local-kubernetes.md` do zero.
- [x] 4. README como capa (objetivos da fase, diagrama, quickstart, mapa "quero... → leia") e `docs/README.md`
      (índice por público).
      README com 102 linhas (de 776): objetivos da fase 2 tirados da functional-spec, diagrama ponta a ponta
      (código → pipeline → GHCR → kind/AWS → banco), início rápido de 2 comandos, tabela "Quero... → Leia", stack,
      estrutura do repositório e status da fase. `docs/README.md` indexa tudo por público (avaliar, rodar, consumir,
      contribuir) e registra as regras de manutenção. O aviso sobre o `AGENTS.md` foi para o `CONTRIBUTING.md`.
      Sem badges de propósito: a URL de um badge fixa o repositório (fork ou grupo), e a branch entregue ainda não foi
      escolhida.
- [x] 5. `AGENTS.md` (linhas que exigem o teste manual "no README") e demais links apontando para os novos caminhos.
      `AGENTS.md`: a regra de HTTP e 2 itens do checklist de conclusão apontam para `docs/api/manual-testing.md` e
      para os guias de `docs/` (**mudança de regra do projeto: avisar o grupo**). Também atualizados: comentário do
      alvo `e2e` no `Makefile` e 3 descrições da collection Postman ("passo N do README"; a numeração da Sequência
      executável não mudou). Mantidas por serem registro histórico: ADR-008 (linha 143) e o README da feature
      `service-order-status-notifications`. Link checker: 13 documentos, 0 links quebrados.
- [x] Postman/README: fluxo de teste manual atualizado (regra do `AGENTS.md`). A pasta `Health` e o intake completo já
      estavam no `docs/api/manual-testing.md` (CP2 e feature `service-order-full-intake`). Em 2026-10-25 entrou a seção
      "Outros ambientes (kind e AWS)": `baseUrl` por ambiente, environment `AWS` no Postman, segredo HMAC da AWS só no
      *Current value*, banco sem seeds e Mailpit por túnel. Conferido que Compose, overlay local e coleção usam o mesmo
      segredo HMAC de desenvolvimento e que os IDs padrão da coleção são marcadores (nenhum seeder os usa). O guia da
      AWS passou a apontar para essa seção (fonte única). A coleção não mudou. Glossário: runbook e drift.
- [x] ~~Gravar o vídeo local: deploy, pipeline, consumo das APIs e HPA escalando.~~ **Dispensado por Santiago
      Silvestre em 2026-10-25:** era um vídeo "de garantia" caso a AWS falhasse. As cenas gravadas na AWS em
      2026-10-24 cobrem os quatro itens que o enunciado exige no vídeo (deploy, CI/CD com `cd.yml` e CD AWS, consumo
      das APIs e HPA 2 → 4 com k6), e o enunciado não exige ambiente local no vídeo.
- [x] Verificação: seguir o `docs/setup/local-kubernetes.md` do zero (Santiago, 2026-10-27, PowerShell 5.1 sem `make`,
      partindo de state vazio). Duas rodadas: a 1ª cobriu as etapas 1–4 e 6 e mostrou que faltava a etapa 5; a 2ª foi
      feita com o guia já corrigido e cobriu todas as etapas, inclusive Postman, Mailpit, Swagger e carga (evidências na
      tabela). Passos implícitos e pontos de confusão encontrados, todos corrigidos no guia: (1) `make` não existe no
      PowerShell (aviso nos pré-requisitos, apontando para "Sem make"); (2) no Windows PowerShell 5.1, `curl` é alias de
      `Invoke-WebRequest`, que pede confirmação e não mostra o JSON (`curl.exe` nos pré-requisitos e na etapa 4; também
      em `load-testing-hpa.md`); (3) o HPA mostra `<unknown>` por 1–2 min após o deploy (explicado na etapa 4);
      (4) com a aplicação implantada, o `destroy` do banco leva ~1 min a mais porque o namespace espera os pods
      terminarem (tempo e explicação na etapa 6); (5) pods removidos pelo scale-down aparecem como `Error` (SIGTERM → saída diferente de
      zero; explicado em `load-testing-hpa.md`). Também ficou explícito rodar um bloco por vez e não colar a saída de
      volta no terminal (as linhas `PS E:\...>` viram chamadas de `Get-Process`). Glossário: alias de comando.

Verificação: README ≤ 150 linhas e sem conteúdo duplicado; link checker com 0 links quebrados (inclusive âncoras);
mermaid renderizado no GitHub; seguir só o `docs/setup/local-kubernetes.md` do zero em um cluster novo
(`infra-local-down` → `up` → deploy → Postman → carga) sem passo implícito.

Commits sugeridos: `docs(platform): move readme sections into dedicated docs without rewording`, seguido dos commits 2
a 5 acima, e por fim `docs(platform): document local kubernetes environment and delivery pipeline`.

### CP8 — AWS Academy (passo 11, previsto 23–24/10; data-limite 24/10, 23h)

**Status: Completed (2026-10-25): ambiente provisionado, usado e destruído em 2026-10-24; documentação em 2026-10-25.
A busca nos logs do run `40149256673` achou dados pessoais (IP e e-mail) no log público; corrigido e confirmado em
2026-10-25 (run `40244048603`). O risco residual do admin de bootstrap foi aceito por Santiago Silvestre em 2026-10-25,
só para o AWS Academy.**

- [x] **Antes de qualquer código:** confirmar região, `LabRole`, tipos de instância, disponibilidade de EKS/NAT e
      duração da sessão; marcar o item pendente da ADR-008. Se o desenho mudar, a technical-spec volta a `Draft`.
      Confirmado em 2026-10-24: o desenho da technical-spec se mantém (evidências abaixo).
- [x] `infra/aws/bootstrap`, `infra/aws/cluster` e `infra/aws/database`, conforme a technical-spec. Código escrito e
      validado em 2026-10-24; `apply` e `destroy` das duas stacks no mesmo dia (`f386891`). Detalhes: EKS `1.35` (mesma versão do kind) com
      `upgrade_policy STANDARD` (evita o suporte estendido, 6× mais caro); acesso ao cluster por access entry do
      criador (`authentication_mode = "API"`); backend S3 com o bucket por `-backend-config` (nome com sufixo
      aleatório); JDBC com `sslMode=REQUIRED` no RDS; `backup_retention_period = 0`.
      **Desvio de implementação (2026-10-24):** a stack Terraform `bootstrap` foi substituída por um passo de AWS CLI
      documentado em `infra/aws/bootstrap/README.md`. No primeiro `apply`, o bucket foi criado, mas a leitura
      posterior do `aws_s3_bucket` falhou com `AccessDenied` em `s3:GetBucketObjectLockConfiguration` (*explicit deny*
      na SCP da organização do Learner Lab), e o recurso ficou `tainted`. Todo refresh repete essa leitura, então não
      há contorno no código. As outras leituras do bucket foram testadas uma a uma e são permitidas. O backend S3 não
      faz essa leitura. O desenho da spec se mantém (bucket com versionamento, criptografia `AES256` e bloqueio
      público; backend S3 com `use_lockfile`). O bucket já criado (`workshop-tfstate-dc4ef3e2`) foi reaproveitado
      e o state local da stack foi descartado. A remoção do bucket passa a ser feita pelo console (Empty + Delete).
- [x] `k8s/overlays/aws`: LoadBalancer com `loadBalancerSourceRanges`, HPA 2–4, sem seeds. Escrito e aplicado no EKS
      em 2026-10-24 (`ba3f8d7`): imagem `ghcr.io/santiagosilvestre/workshop-management-system:<sha>`, cujo pull anônimo
      do SHA `37bd708` retorna `200`; perfil `default` e seeds desligados herdados da base, sem merge; HPA 2–4 com
      descida em 120 s. O IP liberado fica em `allowed-cidr.yaml`, ignorado pelo git, com um `.example` versionado;
      sem o arquivo, o `kustomize build` falha (*fail-safe*: nunca abre o admin de bootstrap para `0.0.0.0/0`).
      `kubectl kustomize` renderizado numa cópia: sem o arquivo → erro; com o arquivo → 9 objetos, `type:
      LoadBalancer`, `/32`.
      **Primeiro deploy manual (2026-10-24):**
      - `cluster`: `apply` → `17 added`. Nodes `v1.35.9` `Ready` em `10.0.10.x`/`10.0.11.x`, sem IP externo.
        `kubectl top nodes` ok (metrics-server do EKS).
      - `database`: `apply` → `8 added`; o RDS levou 5m32s. Ordem implícita observada: o Secret foi criado antes do
        RDS e o ConfigMap `workshop-db` depois do endpoint.
      - Overlay: `kubectl apply -k k8s/overlays/aws --server-side`, que não grava a anotação
        `last-applied-configuration` e resolve o achado Baixo do CP5 para a AWS. 2 pods `1/1`, um por node/AZ,
        prontos em ~1 min no RDS (contra ~2m20s no WSL2). LB `*.elb.amazonaws.com` em ~1 min; readiness `UP` pela
        internet a partir do IP liberado.
      - Postman: 27/27 etapas do `make e2e`, 74 asserções, 0 falhas, 124 s.
      - Teste negativo do `loadBalancerSourceRanges`: o mesmo `curl` feito do CloudShell (IP da AWS, fora do `/32`)
        → `curl: (28) Connection timed out after 10002 milliseconds`, 2×. É *timeout*, e não um HTTP 403: o Security
        Group descarta o pacote antes de qualquer resposta.
      - Incidente de operação: o ConfigMap `supplier-simulator-mappings` não foi criado (só rodou o `--dry-run`) →
        WireMock em `ContainerCreating` com `FailedMount`. Recriado o ConfigMap, o pod subiu sozinho, sem restart.
        O `cd-aws.yml` deve automatizar esse passo.
      - HPA com k6 (`load-test/hpa-load.js`, 5 min, até 20 VUs, pelo LB): CPU média chegou a 118%/70% → réplicas
        2 → 3 → 4 (teto); 4887 requisições, `http_req_failed` 0,00%, `p(95)` 366,97 ms (limiares `< 1%` e
        `< 2000 ms` ✓). Pods novos começaram perto do limit (983m–998m, aquecimento da JVM) e caíram para ~180m em
        ~30 s. Sem carga, voltou 4 → 3 → 2 em ~2 min (janela de 120 s do overlay).
      - Gravação do fluxo `intake` → e-mail do orçamento no Mailpit (via `kubectl port-forward`) → RF40
        `external-status-updates` sem assinatura (`401`) e assinado com o segredo HMAC da AWS (`200`) → e-mail
        "status atualizado para Execução".
      - Destroy: `kubectl delete -k k8s/overlays/aws` → 9 objetos removidos; `aws elb`/`elbv2
        describe-load-balancers` → `[]` antes do Terraform (o LB é criado pelo Kubernetes, não pelo Terraform, e
        ficaria órfão). `database` → `8 destroyed` (RDS em 1m52s); `cluster` → `17 destroyed` (node group 8m17s,
        EKS 2m56s, NAT 52s), sem `DependencyViolation`.
- [x] Environment `aws-academy` e `.github/workflows/cd-aws.yml` (`workflow_dispatch`), em 2026-10-24 (`0e01b18`,
      correção `bf3c199`). O workflow faz o deploy do banco (`infra/aws/database`), gera o `secrets.env` a partir do
      environment, aplica o overlay com a imagem do SHA e faz o smoke test **pelo load balancer**. O IP do runner é
      liberado só durante o smoke test e revogado num step `always()`, conferido sem imprimir o IP. O cluster fica
      fora do workflow, como na technical-spec (D12): leva 15–20 min e cobra por hora, então quem o liga é a pessoa,
      não um disparo da pipeline. A branch
      padrão do fork passou a ser `feat/terraform-san`, porque o GitHub só lista `workflow_dispatch` da branch padrão.
      Runs:
      - `40144883328` e `40145730363` → falha em `Validate inputs` (variable `AWS_ALLOWED_CIDR` mal formatada): o
        *fail-safe* do `/32` funcionou.
      - `40145796420` → falha em `Check AWS credentials` (`NoCredentials`, exit 253), sem tocar em nenhum recurso:
        critério "credencial expirada → falha em `sts`".
      - `40149256673` (`bf3c199`) → **verde em 7m11s**: banco 5m19s, aplicação 8 s, smoke test pelo LB 85 s, revoke
        2 s, `Diagnostics` *skipped* (a correção de `bf3c199` funcionou).
      **Desvio de implementação (2026-10-25):** a technical-spec lista `AWS_ALLOWED_CIDR` como *variable* do
      environment; ele passou a ser *secret*. Motivo: o GitHub imprime os valores de `env:` no topo de cada step e só
      mascara *secrets*, então o IP do desenvolvedor apareceu nos logs públicos dos 4 runs. No mesmo ajuste, o
      `aws sts get-caller-identity` passou a descartar a saída (o ARN da sessão do lab traz o número da conta e o
      e-mail do aluno). O comportamento do workflow não muda; detalhes na revisão de segurança.
- [x] Apresentar e registrar a estimativa de crédito por hora **antes** do primeiro `apply`: ≈ **US$ 0,29/h** ligado
      (control plane EKS 0,100; 2 × `t3.medium` 0,083; NAT 0,045; CLB 0,025; RDS 0,017; 3 IPv4 0,015; EBS 0,008) e
      ≈ US$ 0,20/h com a sessão expirada (os nodes somem). Apresentada em 2026-10-24, antes do `apply` do cluster;
      tabela em `docs/setup/aws-academy.md`.
- [x] README: renovação de credenciais por sessão, ordem de `apply`/`destroy` e lista de recursos. Em 2026-10-25
      (`ba42e95`): guia `docs/setup/aws-academy.md`, diagrama da infra AWS e das pipelines no `overview.md` (§4.2 e
      §4.3), seção do `cd-aws.yml` no `ci-cd.md`, README e índice sem "planejado". Link checker sem links quebrados;
      diagramas renderizados no `mermaid-cli` 11.4.2.

Verificação: `apply` das três stacks; workflow AWS verde; API responde pelo hostname do load balancer apenas a partir
do IP liberado; HPA escala com k6; credencial expirada → falha em `aws sts get-caller-identity`; `destroy`
(database → cluster) e conferência no console de que não sobrou EKS, NAT, ELB, RDS nem EIP.

Resultado da verificação (2026-10-24): `apply` de `cluster` e `database` ✅ (o `bootstrap` virou passo de AWS CLI,
ver desvio acima); workflow AWS verde ✅ (`40149256673`); API pelo LB a partir do IP liberado e timeout a partir do
CloudShell ✅; HPA 2 → 4 → 2 com k6 ✅; credencial inválida → falha em `sts` ✅ (`40145796420`); destroy e conferência
com `[]` em EKS, RDS, NAT, EIP, EC2 e ELB ✅.

Commit sugerido: `feat(platform): provision aws eks and rds with terraform and add aws deploy workflow`

### CP9 — Entrega final (passo 12, previsto 26/10, freeze às 12h)

- [x] README final e vídeo final: vídeo publicado no YouTube
      ([`-1JPvTIRDf8`](https://www.youtube.com/watch?v=-1JPvTIRDf8)) e linkado no README, na tabela "Quero... → Leia" e
      em "Status da fase 2" (2026-10-27).
- [x] Revisão de segurança: "Dependências" resolvido e "Abuso de endpoints" aceito (2026-10-26).
- [x] `./mvnw clean verify` final: 936 testes, 0 falhas, 0 ignorados, 94,5% de instruções (2026-10-26). Depois disso
      só mudou documentação.
- [ ] PDF de entrega (link do repositório, desenho da arquitetura e link do vídeo). É um artefato do grupo, fora do
      repositório, e depende da branch escolhida; **não** é gate do checklist de conclusão do `AGENTS.md`, por isso
      fica como pendência do grupo, fora deste plano (decisão do Santiago, 2026-10-27).
- [x] Marcar a feature como `Implemented` só se todos os gates passarem: CP1–CP8 `Completed`, `verify` verde,
      Modulith intacto (nenhuma mudança estrutural de módulo), revisão de segurança sem achado crítico/alto em aberto
      (o Alto da AWS está mitigado, com risco residual aceito), OpenAPI/Postman e guias atualizados. Marcada por
      Santiago Silvestre em 2026-10-27.

**Status: Completed (2026-10-27).**

## Revisão de segurança

A preencher durante a implementação. Linha de base (achados da technical-spec):

| Item | Achado | Severidade | Mitigação | Status |
|---|---|---|---|---|
| Validação de entrada / mass assignment | Nenhum endpoint de negócio muda | N/A | — | N/A |
| Autenticação e autorização | Health sem token, só `GET` em três caminhos | Baixa | sem detalhes/componentes; `POST` → 401; `/actuator/env` e `/beans` → 401 sem token e 404 com ADMIN (`HealthEndpointTest`; `curl` manual → 401) | Resolvido (CP2) |
| Exposição de dados | Admin de bootstrap `admin/changeme123` exposto por endpoint público na AWS | **Alta** | `loadBalancerSourceRanges` com o IP do desenvolvedor e `destroy` após cada uso. CP8: o IP vem de um arquivo ignorado pelo git e, sem ele, o `kustomize build` falha (nunca `0.0.0.0/0`); o `cd-aws.yml` recusa qualquer valor que não seja um único `/32`, libera o IP do runner só durante o smoke test e o revoga num step `always()`. Provas: `curl` do CloudShell (fora do `/32`) → timeout 2×; runs `40144883328`/`40145730363` barrados em `Validate inputs`; destroy conferido. Risco residual: a senha conhecida continua valendo enquanto o ambiente existe, para quem estiver no IP liberado. Pontos considerados no aceite: (1) o `/32` cobre todos os aparelhos atrás do roteador e, com CGNAT da operadora, outros clientes que dividem o mesmo IP público; (2) sem HTTPS (fora de escopo), senha e JWT trafegam em texto puro; (3) o ADMIN lê e altera todos os dados e cria usuários (`POST /api/auth/users`), um acesso que sobrevive à troca da senha; (4) a janela depende de executar o destroy. Limitam o impacto: banco da AWS sem seeds e só com dados fictícios, admin da aplicação sem acesso à conta AWS, custo limitado pelos nodes fixos (HPA no máximo 4 pods). Alternativas descartadas por prazo: `UPDATE` manual do hash no RDS a cada deploy; senha por variável de ambiente ou troca obrigatória no primeiro login (exige reabrir a spec). Registrado também no TD-003, Grupo 4 | Mitigado (CP8). **Risco residual aceito por Santiago Silvestre em 2026-10-25**, válido **só** para o AWS Academy (ambiente descartável, sem dados reais e destruído após cada uso); não vale como precedente para qualquer ambiente com dados reais |
| Segredos e logs | Secrets por environment; `secrets.env` não versionado; token mascarado | Média | `.gitignore` (CP3), `umask 077`, `::add-mask::`, revisão dos logs; CP6: secrets só no job `deploy-kind` (`environment: kind-ci`), entregues por `env:`, sem `set -x`; busca nos logs do run `39360660986` sem nenhum valor exposto. CP8: environment `aws-academy` com credenciais do lab e segredos da app como *secrets* e só o necessário como *variables*; `secrets.env` com `umask 077`; token do smoke test mascarado; o IP do desenvolvedor é comparado, nunca impresso; ARN com o número da conta cortado na edição do vídeo | Resolvido para `kind-ci` (CP6) e para os segredos da AWS (CP8): busca no zip de logs do run `40149256673` com 38 valores como `***`, nenhuma chave `ASIA`/`AKIA`, nenhum token `eyJ`, valores sensíveis do Terraform como `(sensitive value)` |
| Dados pessoais em log público | A mesma busca encontrou, em texto puro, o IP do desenvolvedor (`AWS_ALLOWED_CIDR` era *variable*, impressa no cabeçalho `env:` de cada step) e o ARN da sessão com o número da conta e o e-mail do aluno (saída do `sts get-caller-identity`). O fork é público: qualquer pessoa logada no GitHub lê os logs | Média | `AWS_ALLOWED_CIDR` virou *secret* do environment (Santiago, 2026-10-25) e o workflow lê `secrets.AWS_ALLOWED_CIDR`; o `sts get-caller-identity` descarta a saída; logs dos 4 runs do CD AWS apagados no GitHub (Santiago, 2026-10-25; a conclusão de cada run continua visível); `ci-cd.md` corrigido (afirmava que o valor precisava ser *variable*). Continuam no log, aceitos: nome do bucket, endpoint do RDS e hostname do LB (recursos já destruídos) e o `changeme123` no texto do script (já público na migration) | Resolvido (CP8): run de confirmação [`40244048603`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/40244048603) (`77efc43`) com `AWS_ALLOWED_CIDR: ***` no cabeçalho `env:`; o descarte do ARN num run bem-sucedido é verificado pelo código (`> /dev/null`), sem run verde após a correção porque exigiria uma nova sessão do lab |
| Persistência e migrations | Nenhuma migration nova; state com senha do banco | Média | state local fora do git (CP3); AWS: backend S3 com `encrypt = true` e `use_lockfile`, bucket privado, criptografado e com versionamento (`infra/aws/bootstrap/README.md`); senhas geradas por `random_password`, nunca no `plan` | Resolvido: local (CP3) e AWS (CP8, conferido em 2026-10-25: `AES256`, bloqueio público com os 4 valores `true`, versionamento `Enabled`) |
| Respostas de erro | Health não expõe detalhes | Baixa | `show-details`/`show-components=never` em `actuator.properties`, importado também pelos testes; banco fora → só `{"status":"DOWN"}` | Resolvido (CP2) |
| Dependências | Actuator (BOM do Boot), imagens com tag fixa, actions por SHA | Baixa | Actions dos três workflows (`cd.yml`, `cd-aws.yml`, `security-scan.yml`) fixadas por SHA; node do kind por digest; imagem do app com a tag do SHA do commit; Mailpit `v1.31.4` e WireMock `3.13.1` com tag fixa; providers do Terraform com lock file e checksums; chart do metrics-server `3.14.0`; Actuator com a versão do BOM do Boot, coberto pelo OWASP Dependency-Check (`security-scan.yml`), que já forçou a correção do Tomcat (`pom.xml`). Aceito: a base do `Dockerfile` (`maven:3.9.6-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) e o `mysql:8.4` usam tag de linha para receber patches de segurança, ao custo de um build não 100% reprodutível | Resolvido (CP9, 2026-10-26) |
| Abuso de endpoints | Health pode ser chamado em massa | Baixa | Os três caminhos de health só aceitam `GET` e devolvem apenas o status agregado (`show-details`/`show-components=never`); a readiness faz uma checagem leve de conexão com o banco, sem cache. Alcance limitado: na AWS, o LB só aceita o `/32` do desenvolvedor (e o IP do runner durante o smoke test); no local, só `localhost`. Rate limiting fora do escopo, o mesmo risco residual registrado na abertura de OS (`service-order-full-intake`) | Aceito (CP9, 2026-10-26) |
| Container | Não-root numérico, FS somente leitura, sem capabilities | — | `securityContext` do app: `runAsNonRoot`/`runAsUser: 1000`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem` (só `/tmp` em `emptyDir` de 256 Mi), `drop: [ALL]`, `seccomp RuntimeDefault`, `automountServiceAccountToken: false`; o app subiu sem `Read-only file system`. Mailpit/WireMock (imagens de terceiros): `allowPrivilegeEscalation: false` + seccomp, sem token | Resolvido (CP5) |
| Secret no `last-applied-configuration` | O `kubectl apply` grava o objeto inteiro numa anotação, inclusive o `data` (base64) do Secret `workshop-app` | Baixa | Quem pode ler a anotação já pode ler o Secret (mesma permissão `get secrets`); valores locais são só de desenvolvimento. Na AWS, `kubectl apply --server-side` (manual e no `cd-aws.yml`), que não grava essa anotação | Aceito (local); resolvido na AWS (CP8) |
| `--kubelet-insecure-tls` | TLS do kubelet não verificado | Baixa | só no kind descartável (`infra/local/cluster/main.tf`); EKS usa add-on | Resolvido (CP4) |
| Credenciais do kind no plano | O provider `tehcyx/kind` não marca `client_key`/`kubeconfig` como sensíveis: `plan`/`destroy` imprimem a chave privada de admin do cluster (observado no CP4) | Baixa | API server só em `127.0.0.1`, cluster descartável (a chave morre no `destroy`); kubeconfig fora do repositório; não colar saídas de `destroy` em canais públicos; no CP6, o cluster vive só no runner efêmero, mas os logs do job ficam visíveis no fork: revisar se o `plan`/`destroy` do kind deve rodar com saída filtrada. CP6: a pipeline só faz `apply` sobre state vazio (atributos `(known after apply)`) e nunca `destroy` (o runner é descartado); busca nos logs do run `39360660986` sem `PRIVATE KEY`/`client-key-data`, então nenhum filtro é necessário | Aceito (local); resolvido na pipeline (CP6) |
| App como usuário master do RDS | Na AWS, o app conecta com o usuário master (`workshop_user`), que tem privilégios administrativos no MySQL; no local ele não é root | Média | RDS sem acesso público, porta 3306 só a partir do security group dos nodes, senha gerada (`random_password`) e entregue por Secret, ambiente destruído após cada uso. Criar um usuário de menor privilégio exigiria conexão do Terraform às subnets privadas (fora do escopo do lab) | Aceito (CP8) |
| Endpoint público da API do EKS | O control plane aceita conexões de qualquer IP (`0.0.0.0/0`), necessário para o runner do GitHub | Baixa | Toda chamada exige credencial IAM válida + access entry; credenciais do lab expiram em 4 h | Aceito (CP8) |
| Senhas do MySQL local | Geradas por `random_password` (24 caracteres), entregues por Secret; nunca aparecem no plano, só no state | Baixa | state fora do git (CP3); `describe` mostra só a origem (`secret_key_ref`); MySQL só `ClusterIP`, sem porta no Windows | Resolvido (CP4) |

## Evidências de verificação

Registrar por checkpoint: comando, trecho relevante da saída, data e (para a pipeline) link da execução.

| Checkpoint | Evidência | Data |
|---|---|---|
| CP1 | `./mvnw clean verify`: exit 0, 926 testes, 0 falhas, 0 ignorados | 2026-10-17 |
| CP1 | `docker compose up -d --build` (Santiago): app `Up`, MySQL/Mailpit/WireMock `healthy`, Mailpit `v1.31.4` | 2026-10-17 |
| CP1 | `docker compose exec app id -u` → `1000`; `echo $JAVA_TOOL_OPTIONS` → `-XX:MaxRAMPercentage=75.0` | 2026-10-17 |
| CP1 | `docker image inspect ... .Config.Env` → só `PATH`, `JAVA_HOME`, `LANG*`, `JAVA_VERSION`, `JAVA_TOOL_OPTIONS` (sem segredos) | 2026-10-17 |
| CP1 | `GET /swagger-ui.html` → `302` (redirect do springdoc para `/swagger-ui/index.html`): API no ar em `localhost:8080` | 2026-10-17 |
| CP2 | `./mvnw clean verify`: exit 0, 936 testes (+10), 0 falhas, 0 ignorados; JaCoCo 94,5% instruções, 77,8% branches | 2026-10-17 |
| CP2 | `docker compose up -d --build app` (Santiago) → `docker compose ps`: `workshop-app ... (healthy)` | 2026-10-17 |
| CP2 | `curl` sem token: liveness `200 {"status":"UP"}`, readiness `200 {"status":"UP"}`, `/actuator/env` `401 UNAUTHORIZED` | 2026-10-17 |
| CP2 | `docker compose stop mysql` → readiness `503 {"status":"DOWN"}`, liveness `200 {"status":"UP"}`, app `Up 8 minutes (unhealthy)` (não reiniciou) | 2026-10-17 |
| CP2 | `docker compose start mysql` → readiness volta a `200 {"status":"UP"}` | 2026-10-17 |
| CP3 | Arquivos fictícios + `git check-ignore -v`/`git status`: ignorados `terraform.tfstate`, `.tfstate.backup`, `.terraform/`, `kubeconfig`, `terraform.tfvars`, `aws.tfplan`, `crash.log`, `k8s/overlays/{local,aws}/secrets.env`; versionáveis `.terraform.lock.hcl`, `terraform.tfvars.example`, `secrets.env.example`. Fictícios apagados | 2026-10-17 |
| CP4 | Pré-requisitos (Santiago): `terraform version` → `v1.16.6`; `kubectl version --client` → `v1.36.1` (Kustomize `v5.8.1`); `docker info` → `7273299968 5` (6,77 GiB, 5 CPUs) após o ajuste do `.wslconfig` | 2026-10-17 |
| CP4 | `terraform fmt -check -recursive infra` → exit 0 | 2026-10-17 |
| CP4 | `cluster`: `plan` → `2 to add`; `apply` → `2 added`; `kubectl get nodes` → `workshop-control-plane Ready v1.35.0`; metrics-server `1/1 Running`, 0 restarts; `kubectl top nodes` → `124m`, `573Mi`; `docker ps` → `0.0.0.0:8080->30080`, `0.0.0.0:8025->30025`, `127.0.0.1:59720->6443` | 2026-10-17 |
| CP4 | `database`: `plan` → `8 to add` (Secrets e senhas como `sensitive value`); `apply` → `8 added`; `mysql-0 1/1 Running`, 0 restarts; PVC `data-mysql-0 Bound 1Gi standard` em `workshop`; PV com `RECLAIM POLICY Delete`; `top pods` → `12m`, `424Mi` | 2026-10-17 |
| CP4 | Readiness falhou 18× durante a primeira inicialização (~95 s, servidor temporário com `port: 0`) sem reiniciar o pod; `SHOW DATABASES` como `workshop_user` com a senha do Secret `workshop-db` → `workshop` (D6 comprovada) | 2026-10-17 |
| CP4 | Persistência: `CREATE TABLE prova` + `INSERT 42` → `kubectl delete pod mysql-0` → pod recriado `1/1` em 14 s → `SELECT` → `42` | 2026-10-17 |
| CP5 | `kubectl kustomize k8s/overlays/local` → 8 objetos; ConfigMap `workshop-app-config-gbdf27b9fm` e Secret `workshop-app-dt9kffbhb5` com hash; NodePorts 30080/30025 | 2026-10-17 |
| CP5 | Imagem no node (Santiago): `docker save --platform linux/amd64` → `docker cp` para `/var/tmp` → `ctr -n k8s.io images import` → `crictl images` lista `docker.io/library/workshop-management-system local 0002eb3fea1ce 165MB` (mesmo config digest do build). Tentativa em `/tmp` falhou: `tmpfs` no node | 2026-10-17 |
| CP5 | `kubectl apply -k k8s/overlays/local` → 9 objetos `created`; `rollout status` → `successfully rolled out`; 4 pods `1/1`, 0 restarts | 2026-10-17 |
| CP5 | Log: Flyway `Successfully applied 25 migrations ... now at version v20260926043630 (execution time 02:18.305s)`; `Started ... in 165.559 seconds` (banco vazio) | 2026-10-17 |
| CP5 | `curl localhost:8080/actuator/health/readiness` → `{"status":"UP"}`; `describe deployment` → `envFrom` com `workshop-app-config-*`/`workshop-db` (ConfigMaps) e `workshop-db`/`workshop-app-*` (Secrets), sem valores | 2026-10-17 |
| CP5 | Recriação: `kubectl delete pod -l app.kubernetes.io/name=workshop-app` → pod novo do mesmo ReplicaSet `1/1` em 27 s (banco já migrado) | 2026-10-17 |
| CP5 | Persistência: `POST /api/customers` (CPF 529.982.247-25) → id `13008937-...`; `kubectl delete pod mysql-0` → `mysql-0` `1/1` em 20 s; app `0/1` (readiness) e de volta a `1/1` sem restart; `GET /api/customers/{id}` → cliente presente. CPF 111.444.777-35 → 409 (já criado pelo seed `dev`, D10 comprovada) | 2026-10-17 |
| CP5 | Rolling update (probe 36 → 60): `apply -k` → ReplicaSet novo `5d589d5c8c`; 2 pods durante ~30 s; o antigo só terminou depois que o novo ficou `1/1` (`maxSurge: 1`, `maxUnavailable: 0`) | 2026-10-17 |
| CP5 | Versão ruim: `set env DB_URL=jdbc:mysql://inexistente:3306/x` → pod novo `Error`/`CrashLoopBackOff` (3 restarts em 96 s); antigo `1/1`; readiness `UP`; `get rs` → `5d589d5c8c 1/1/1`, `76bfc6cd55 1/1/0`; `rollout undo` → `76bfc6cd55` a 0; `kubectl diff -k` → sem diferenças | 2026-10-17 |
| CP5 | Postman: 27 passos do `make e2e` (Newman, um processo por pasta) contra `localhost:8080` → 27/27. Corrigido antes: o pre-request de `Open full intake with services` gravava `intakeCustomerTaxId`/`intakeLicensePlate` só como variável de collection, perdida entre processos do Newman (`Run Open full intake with services first`); agora grava também no environment | 2026-10-17 |
| CP5 | Ambiente recriado com o MySQL relaxado: `SELECT @@log_bin, @@innodb_flush_log_at_trx_commit, @@innodb_doublewrite` → `0 2 OFF`; Flyway `Successfully applied 25 migrations ... (execution time 01:55.249s)`; `Started ... in 139.643 seconds` (banco vazio) | 2026-10-18 |
| CP5 | `flyway_schema_history.execution_time`: DML 2 ms/5 ms; `initial schema` 20,2 s, `add customer address` 15,7 s (8 `ALTER`). Banco `bench` descartável (`mysql -vvv`): `INSERT`/`UPDATE` 0,00 s, `ADD COLUMN` 0,27 s, `CREATE TABLE` 1,23 s, `ADD CONSTRAINT CHECK` 1,50 s; `bench` apagado | 2026-10-18 |
| CP5 | Carga 1 (HPA CPU + memória), k6 `grafana/k6:1.3.0` só em `localhost:8080`: CPU 154% → 2 pods, 183% → 3 pods; pod novo nasce a ~1000m (boot da JVM) sem cascata; 6714 requisições, **0 falhas**, p95 84 ms. Fim da carga: 3 → 2 em ~1 min e **travou em 2** (memória 48%/85%, `ScalingActive ... from memory`) | 2026-10-18 |
| CP5 | Após remover a memória (`kubectl diff -k` → só o bloco `memory` sai; `apply` → `configured`): 2 → 1 ocioso em ~1 min. Carga 2: CPU 104% → 2, 3 pods no pico (máx. 124%); 6727 requisições, **0 falhas**, p95 77 ms, máx. 836 ms; fim da carga → 3 → 2 → **1** em ~1 min. Critérios "escala sobe", "escala desce" e "mesmo endereço" ✅ | 2026-10-18 |
| CP6 | Run verde [`39360660986`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39360660986) (`d902d1b`): `build-test` 1m53s, `image` 1m40s, `deploy-kind` 2m35s, **total 6m17s** (meta ≤ 40 min). Banco vazio no runner: step `Smoke test` (rollout incluído) em 36 s; no run `39363382338`, `Started ... in 17.791 seconds`, contra ~140 s no kind sobre WSL2 (confirma o gargalo de disco do CP5); `Smoke test passed for ghcr.io/santiagosilvestre/workshop-management-system:d902d1b...` | 2026-10-20 |
| CP6 | Pull anônimo `GET ghcr.io/v2/santiagosilvestre/workshop-management-system/manifests/d902d1b...` → `200` (pacote público, D9) | 2026-10-20 |
| CP6 | Logs do run `39360660986` (zip, `Select-String`): nenhum `PRIVATE KEY`/`client-key-data`/`eyJ`; `client_key`/`client_certificate` só como `(known after apply)`; secrets do `kind-ci`, `GITHUB_TOKEN` e cabeçalho `Authorization` como `***` | 2026-10-20 |
| CP6 | Push em outra branch (`git push fork feat/terraform-san:test/cd-trigger-probe`, depois apagada) → nenhuma execução nova (Santiago) | 2026-10-20 |
| CP6 | Teste quebrado: run [`39363151926`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39363151926) (`8a9f474`) → `Build and test` falhou em `Run quality gate`; `Build and push image` e `Deploy to kind and smoke test` **skipped** | 2026-10-20 |
| CP6 | Revert `1cc7409`: run [`39363382338`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39363382338) falhou no smoke test (corrida com o kube-proxy, ver desvios); após o retry, run verde [`39364405763`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39364405763) (`46cd5b2`), **total 5m29s** | 2026-10-20 |
| CP6 | `git diff --quiet 3d0cdb3 HEAD -- .github/workflows/ci.yml` → sem diferenças (`ci.yml` intacto) | 2026-10-20 |
| CP8 | Learner Lab (Santiago, CloudShell do console): sessão de 4 h, crédito US$ 50; `sts get-caller-identity` → `assumed-role/voclabs/user...` (conta do lab, não pessoal); `iam get-role LabRole` → `arn:aws:iam::<conta>:role/LabRole`; região `us-east-1`. Primeira tentativa no terminal da página do lab (role `runserver`) negou tudo: terminal errado, não restrição do lab | 2026-10-24 |
| CP8 | `--dry-run`: `run-instances t3.medium` e `create-nat-gateway` → `DryRunOperation` (permitidos); `s3 ls` → OK; `eks list-clusters` → `[]`; add-on `metrics-server` compatível com EKS `1.34`–`1.37`; RDS MySQL `8.4.5`–`8.4.11` e `db.t3.micro` disponíveis (criação do RDS só se prova no `apply`: não há dry-run) | 2026-10-24 |
| CP8 | `terraform fmt -check -recursive infra` → exit 0; `init -backend=false` + `validate` em `infra/aws/{bootstrap,cluster,database}` → `Success! The configuration is valid.`; lock files com `windows_amd64` + `linux_amd64` (`aws 6.69.1`, `kubernetes 3.3.0`, `random 3.9.1`) | 2026-10-24 |
| CP8 | HPA no EKS com k6: 2 → 4 réplicas a 118%/70%, 4887 req, 0,00% de falhas, p95 366,97 ms; descida para 2 em ~2 min | 2026-10-24 |
| CP8 | Destroy: app (`kubectl delete -k`, LB `[]` em `elb`/`elbv2`) → `database` `8 destroyed` → `cluster` `17 destroyed`. Conferência por AWS CLI: `eks list-clusters`, `rds describe-db-instances`, `ec2 describe-nat-gateways` (pending/available), `ec2 describe-addresses`, `ec2 describe-instances` (pending/running) e `elb describe-load-balancers` → `[]` em todos; contexto do EKS removido do kubeconfig e planos `*.tfplan` apagados. Mantido de propósito: bucket do state `workshop-tfstate-dc4ef3e2` | 2026-10-24 |
| CP8 | CD AWS: [`40145796420`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/40145796420) falhou em `Check AWS credentials` (`NoCredentials`, exit 253); [`40149256673`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/40149256673) (`bf3c199`) verde em 7m11s, smoke test pelo LB em 85 s, revoke do IP do runner em 2 s | 2026-10-24 |
| CP8 | Bucket do state `workshop-tfstate-dc4ef3e2` (Santiago, AWS CLI): `get-bucket-encryption` → `SSEAlgorithm: AES256` (e `SSE-C` bloqueado); `get-public-access-block` → `BlockPublicAcls`, `IgnorePublicAcls`, `BlockPublicPolicy` e `RestrictPublicBuckets` = `true`; `get-bucket-versioning` → `Enabled` | 2026-10-25 |
| CP8 | Logs do run `40149256673` (zip baixado pelo Santiago, busca com `grep`): segredos como `***` (38), nenhum `ASIA`/`AKIA`/`eyJ`, Terraform com `(sensitive value)`; **achado**: IP do `AWS_ALLOWED_CIDR` no cabeçalho `env:` de cada step e e-mail + conta no ARN do `sts get-caller-identity`. Fork confirmado público pela API. Logs dos 4 runs apagados e `AWS_ALLOWED_CIDR` migrado para *secret* pelo Santiago | 2026-10-25 |
| CP8 | Run de confirmação [`40244048603`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/40244048603) (`77efc43`, lab desligado): passou em `Validate inputs` (*secret* `AWS_ALLOWED_CIDR` com formato válido) e falhou em `Check AWS credentials` com `ExpiredToken` (exit 254), sem tocar em recursos; cabeçalho `env:` com `AWS_ALLOWED_CIDR: ***` e os 3 `AWS_*` como `***` | 2026-10-25 |
| CP8 | Documentação (`ba42e95`): `markdown-link-check@3.13.7` em README, `docs/README.md`, `aws-academy.md`, `ci-cd.md`, `overview.md` e glossário → 0 links quebrados; diagramas novos renderizados no `mermaid-cli` 11.4.2; README com 110 linhas | 2026-10-25 |
| CP4 | `destroy` `database` → `8 destroyed` (namespace por último, 13 s); `destroy` `cluster` → `2 destroyed`; `docker ps` vazio; `kubectl config get-contexts` sem `kind-workshop` | 2026-10-17 |
| CP7 | Runbook local, 1ª rodada (Santiago): `apply` cluster e database a partir de state vazio; o contexto passou de um EKS já destruído para `kind-workshop` sozinho; node `Ready`, `mysql 1/1`, PVC `Bound`; build da imagem em 61 s; `docker cp` 165 MB + `ctr import`; `apply -k` → 9 `created`; `rollout status` → `successfully rolled out`; 4 pods `1/1`, 0 restarts; readiness `200` (15 bytes = `{"status":"UP"}`); HPA `cpu: <unknown>/70%`; `destroy` `database` → `8 destroyed` (namespace em 1m3s, com a aplicação dentro) e `cluster` → `2 destroyed` | 2026-10-27 |
| CP7 | Runbook local, 2ª rodada (guia corrigido): etapas 1–4 ok; Mailpit e Swagger conferidos pelo Santiago; HPA `cpu: 1%/70%`; Newman (um processo por pasta, ordem do `make e2e`) contra `localhost:8080` → **27/27 pastas, 69 asserções, 0 falhas** | 2026-10-27 |
| CP7 | Carga (Santiago, `load-testing-hpa.md`): k6 6708 requisições em 5 min, **0,00% de falhas**, p95 76,73 ms, máx. 20 VUs. HPA: 106% → 2 réplicas (15 s depois) → 167% → 3 réplicas (mais 15 s); 76–98% com 3 pods; fim da carga → 0% → 2 → 1 em ~1 min. Pods novos `0/1` → `1/1` em 26 s. Eventos: `SuccessfulRescale ... above target` e `All metrics below target`. Pods removidos: `Terminating` → `Error` (encerramento por SIGTERM, não é falha) | 2026-10-27 |
| CP7 | `destroy` `database` e `cluster` (Santiago); conferido: os dois `terraform.tfstate` com 0 recursos e nenhum container `workshop` no Docker | 2026-10-27 |

## Rollback ou recuperação

- **Deploy ruim no cluster:** `kubectl rollout undo deployment/workshop-app -n workshop`. O rolling update com
  `maxUnavailable: 0` mantém a versão anterior atendendo enquanto a nova não fica pronta.
- **Ambiente local corrompido:** `terraform destroy` em `database` e depois em `cluster`, seguido de novo `apply`. Os
  dados locais são descartáveis.
- **Pipeline com falha:** nenhuma etapa posterior roda; o cluster do job é descartado com o runner. Corrigir e fazer
  novo push.
- **State AWS travado:** confirmar que não há `apply` em curso antes de `terraform force-unlock`.
- **Esquecer recursos na AWS:** `destroy` das stacks `database` e `cluster`; conferir no console EKS, EC2 (NAT, ELB,
  EIP) e RDS.
- **Migrations:** nenhuma migration nova nesta feature. A recuperação de schema segue o processo normal do Flyway.

# Ambiente Kubernetes local (kind + Terraform)

Este guia sobe no seu computador o mesmo desenho usado na pipeline: um cluster Kubernetes
([kind](https://kind.sigs.k8s.io)), o MySQL e o metrics-server provisionados por Terraform, e a aplicação implantada
por Kustomize com probes, rolling update e escala automática (HPA). O desenho está na
[visão geral da arquitetura, seção 4.1](../architecture/overview.md#41-kubernetes-local-kind).

Para só desenvolver e chamar a API, o [Docker Compose](local-docker-compose.md) é mais rápido.
Se escolheu Kubernetes, comece pela conferência de dependências na seção 0.1. Este caminho sobe o ambiente completo;
o Compose só precisa ser parado caso já esteja em execução.

## Resumo

| # | Etapa | Comando | Tempo (1ª vez) |
| --- | --- | --- | --- |
| 0 | Conferir dependências, portas e recursos | Seguir a seção "0. Preparar o ambiente" abaixo | — |
| 1–2 | Criar o cluster, metrics-server e banco | `make infra-local-up` (faz as duas etapas) | ~8 a 10 min |
| 3 | Implantar a aplicação | `make k8s-local-deploy` | ~2 min de build + até 3 min de boot |
| 4 | Verificar | `kubectl get pods,svc,hpa -n workshop` | — |
| 5 | Usar | Postman, Swagger, Mailpit, [teste de carga](../operations/load-testing-hpa.md) | — |
| 6 | Destruir banco e depois cluster | `make infra-local-down` (apaga os dados) | ~20 s sem a aplicação; ~1 min 30 s com ela |

## Escolha um caminho de execução

**Com Make (recomendado):** conclua primeiro a seção 0 e execute apenas:

```bash
make infra-local-up       # executa init e apply do cluster e depois do banco
make k8s-local-deploy     # constrói a imagem e implanta a aplicação
```

**Depois que esses comandos terminarem, vá direto para a seção 4 (Verificar). Não execute os comandos Terraform
das seções 1 e 2 nem repita a implantação da seção 3.** Essas seções detalham os recursos e mostram as alternativas
manuais. O Make chama o Terraform instalado na máquina e usa `-auto-approve`, dispensando a confirmação de cada plano.

**Sem Make (alternativa manual):** conclua a seção 0 e execute as seções 1, 2 e 3, nessa ordem. Nesse caminho,
os comandos Terraform substituem `make infra-local-up`; permitem revisar o plano e confirmar cada `apply`.

**Os dois caminhos são alternativas. Escolha um; não execute ambos.**

## Pré-requisitos

| Item | Exigência | Como verificar |
| --- | --- | --- |
| Docker Engine ou Docker Desktop (WSL2 no Windows) | rodando, com ≥ 6 GiB de memória e ≥ 4 CPUs | `docker info --format '{{.MemTotal}} {{.NCPU}}'` |
| Docker Compose plugin | para os comandos de preparação do Compose | `docker compose version` |
| Terraform | ≥ 1.10 | `terraform version` |
| kubectl | preferir 1.35.x para o cluster Kubernetes 1.35 deste projeto | `kubectl version --client` |
| GNU Make e curl | para os atalhos e verificações deste guia | `make --version` e `curl --version` |
| Portas livres no host | 8080 e 8025 (pare o Docker Compose antes: `docker compose down`) | — |

kind, Helm e k6 **não** precisam ser instalados: os providers do Terraform embutem o kind e o Helm, e o k6 roda em
container. No Windows, a memória do WSL2 é definida em `%UserProfile%\.wslconfig` (`memory=7GB` funcionou numa máquina
de 16 GB).

Todos os comandos abaixo rodam na raiz do repositório e funcionam no PowerShell e no Bash, exceto onde indicado.
No Windows PowerShell 5.1, `make` não existe (use as seções "Sem make") e `curl` é um apelido de `Invoke-WebRequest`,
que pede confirmação e não mostra o JSON: digite `curl.exe` para chamar o curl de verdade (vem com o Windows 10 e 11).
Rode um bloco de cada vez e cole no terminal só os comandos, nunca a saída anterior.

## 0. Preparar o ambiente

### 0.1. Conferir e instalar dependências

Este é o primeiro passo, antes de liberar portas, parar containers ou executar Terraform.
No Bash do Linux/WSL, identifique ferramentas ausentes:

```bash
for tool in docker terraform kubectl make curl; do
    if command -v "$tool" >/dev/null 2>&1; then
        printf 'OK: %s\n' "$tool"
    else
        printf 'AUSENTE: %s (instale antes de continuar)\n' "$tool"
    fi
done
```

Depois de instalar o que faltar, confira versões e acesso ao daemon:

```bash
docker version
docker compose version
terraform version
kubectl version --client
make --version
curl --version
```

Só avance quando as ferramentas responderem, Terraform for ≥ 1.10 e `docker version` mostrar também o servidor.
Se o cliente Docker existir mas o servidor não responder, inicie o daemon ou ajuste o acesso; reinstalar o cliente
não resolve isso. Para WSL com Docker Desktop, inicie o Desktop e habilite a integração com sua distribuição.

#### Instalação no Ubuntu / Ubuntu no WSL (Bash)

Execute apenas os blocos necessários. Os comandos com `apt` são específicos do Ubuntu; outras distribuições e macOS
devem seguir as instruções oficiais das ferramentas. As instalações são feitas explicitamente pelo usuário.

**Ferramentas básicas:**

```bash
sudo apt-get update
sudo apt-get install -y make curl ca-certificates gnupg
```

**Terraform**, pelo [repositório oficial da HashiCorp](https://developer.hashicorp.com/terraform/install):

```bash
curl -fsSL https://apt.releases.hashicorp.com/gpg -o /tmp/workshop-hashicorp.asc
gpg --dearmor --yes -o /tmp/workshop-hashicorp.gpg /tmp/workshop-hashicorp.asc
sudo install -m 0644 /tmp/workshop-hashicorp.gpg /usr/share/keyrings/hashicorp-archive-keyring.gpg
. /etc/os-release
printf 'deb [arch=%s signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] %s %s main\n' \
    "$(dpkg --print-architecture)" "https://apt.releases.hashicorp.com" "$VERSION_CODENAME" \
    | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt-get update
sudo apt-get install -y terraform
terraform version
```

**kubectl**, conforme a [instalação oficial no Linux](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/).
O bloco usa a versão 1.35 mais recente, compatível com o cluster do projeto, e valida o checksum antes de instalar:

```bash
(
    set -eu
    case "$(uname -m)" in
        x86_64) kubectl_arch=amd64 ;;
        aarch64|arm64) kubectl_arch=arm64 ;;
        *) printf 'Arquitetura não contemplada neste exemplo\n' >&2; exit 1 ;;
    esac
    kubectl_version=$(curl -fsSL https://dl.k8s.io/release/stable-1.35.txt)
    kubectl_dir=$(mktemp -d)
    cd "$kubectl_dir"
    kubectl_url="https://dl.k8s.io/release/$kubectl_version/bin/linux/$kubectl_arch/kubectl"
    curl -fsSLo kubectl "$kubectl_url"
    curl -fsSLo kubectl.sha256 "$kubectl_url.sha256"
    printf '%s  kubectl\n' "$(cat kubectl.sha256)" | sha256sum --check
    sudo install -m 0755 kubectl /usr/local/bin/kubectl
)
kubectl version --client
```

**Docker e Compose:** no Ubuntu com Engine nativo, configure primeiro o repositório `apt` conforme a
[instalação oficial do Docker](https://docs.docker.com/engine/install/ubuntu/#install-using-the-repository).
Depois de configurar o repositório, instale:

```bash
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl start docker
docker version
docker compose version
```

Esse bloco pressupõe um Ubuntu com systemd. Para configurar acesso ao Docker sem `sudo`, siga o
[guia oficial de pós-instalação](https://docs.docker.com/engine/install/linux-postinstall/).
Se usa Docker Desktop com integração WSL, utilize o Engine fornecido pelo Desktop em vez de instalar outro
daemon na mesma distribuição. Após a instalação, repita a conferência de dependências antes de continuar.

### 0.2. Conferir recursos e liberar portas

Antes de subir o cluster, confira quais containers já estão usando as portas e a memória da máquina:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker stats --no-stream
```

Confira também os recursos do ambiente que executa o daemon Docker:

```bash
docker info --format 'Memory={{.MemTotal}} CPUs={{.NCPU}} DockerRootDir={{.DockerRootDir}}'
docker system df
```

No Linux/WSL, quando o daemon roda nessa mesma distribuição, confira disco, memória disponível, swap e `inotify`:

```bash
df -h /
free -h
sysctl fs.inotify.max_user_instances fs.inotify.max_user_watches
```

Se `DockerRootDir` estiver em outro filesystem, execute `df -h` também para o caminho retornado.
Com Docker Desktop ou daemon remoto, o espaço da distribuição/terminal pode diferir do disco usado pelo Docker;
confira os recursos do daemon e, no WSL2, também o espaço livre na unidade Windows que armazena o disco virtual.

| Recurso | Como avaliar antes da subida |
| --- | --- |
| Disco | Conferir o espaço disponível no filesystem do Docker; `docker system df` mostra o consumo, não o espaço livre. Não apagar volumes como rotina de preparação |
| Memória | Conferir `available` em `free -h`, além da memória total; outros clusters podem consumir a capacidade prevista para este ambiente |
| Swap | Uso elevado junto com pouca memória disponível indica pressão de recursos; interromper cargas escolhidas ou aumentar a memória do WSL/Docker |
| `inotify` | Vários clusters compartilham limites do host; um limite de 128 instâncias pode ser insuficiente. Seguir o diagnóstico de boot abaixo |

Disco livre, memória disponível e limites de `inotify` são verificações diferentes. A mensagem de espera pelo boot
do Kind não comprova falta de disco nem de memória; é necessário examinar os logs do nó para confirmar a causa.

Se estiver migrando do Docker Compose deste projeto para Kubernetes, execute na raiz do repositório:

```bash
make docker-down          # sem make: docker compose down
```

Esse comando remove os containers e a rede do Compose deste projeto, preservando o volume do MySQL.
O banco do Kubernetes é independente: os dados do Compose não são transferidos para ele.

Se outros projetos ou clusters estiverem consumindo os recursos necessários, escolha quais pode interromper e
pare apenas esses containers pelo nome. Por exemplo, substitua `nome-do-container` pelo nome listado acima:

```bash
docker stop nome-do-container
```

Parar um nó Kubernetes interrompe os serviços executados nele. O container é preservado e pode ser iniciado
novamente com `docker start nome-do-container`. A decisão de interromper outro ambiente pertence ao usuário;
não há limpeza global automática, nem é necessário apagar imagens ou volumes para liberar memória.

Depois de liberar as portas 8080 e 8025 e reservar memória para o novo cluster, siga o caminho escolhido:
com Make, execute os dois comandos da seção "Escolha um caminho de execução" e avance para a seção 4;
sem Make, comece pela seção 1.

`infra-local-up` provisiona a infraestrutura sem executar uma limpeza prévia. A preparação é explícita e separada
para evitar interromper outros projetos ou apagar dados ao subir o ambiente.
`make infra-local-down` é destinado a destruir a infraestrutura Kubernetes deste projeto, incluindo o MySQL e
seus dados, conforme a seção 6; não é um pré-requisito para subir ou reaplicar a infraestrutura.
`make docker-reset` também apaga dados, do banco do Compose, e não deve ser usado apenas para liberar portas.

## 1. Criar o cluster

**Alternativa manual — somente se não usou `make infra-local-up`.** Com Make, a criação já está incluída.

```bash
terraform -chdir=infra/local/cluster init
terraform -chdir=infra/local/cluster plan
terraform -chdir=infra/local/cluster apply
```

`-chdir` executa o Terraform dentro da pasta da stack sem mudar o diretório atual. O `init` baixa os providers (só
na primeira vez ou quando as versões mudam); o `plan` mostra o que será criado; o `apply` mostra o plano de novo e
pede `yes`.

**Cria:**

| Recurso | Detalhe |
| --- | --- |
| Cluster kind `workshop` | 1 nó (`workshop-control-plane`), imagem `kindest/node:v1.35.0` fixada por digest |
| Mapeamento de portas | `localhost:8080` → NodePort 30080 (API) e `localhost:8025` → NodePort 30025 (Mailpit) |
| kubeconfig | Contexto `kind-workshop` gravado em `~/.kube/config` e definido como o atual |
| metrics-server | Chart Helm `3.14.0` no namespace `kube-system`, com `--kubelet-insecure-tls` (o kubelet do kind usa certificado autoassinado) |

**Verificar:**

```bash
kubectl get nodes          # workshop-control-plane   Ready
kubectl top nodes          # mostra CPU e memória; "metrics not available yet" some em ~1 min
```

> O `plan` e o `destroy` desta stack imprimem a chave de administrador do cluster, porque o provider `tehcyx/kind` não
> a marca como sensível. É aceitável num cluster local e descartável; não cole essa saída em lugares públicos.

## 2. Criar o banco

**Alternativa manual — somente se não usou `make infra-local-up`.** Com Make, o banco já foi criado após o cluster.

```bash
terraform -chdir=infra/local/database init
terraform -chdir=infra/local/database apply
```

Esta stack depende do cluster da etapa 1 (usa o contexto `kind-workshop`), por isso vem depois.

**Cria:**

| Recurso | Detalhe |
| --- | --- |
| Namespace `workshop` | Onde ficam o banco e a aplicação. É do Terraform, não do Kustomize: um `kubectl delete -k` nunca apaga o banco |
| Senhas | `random_password` para root e para o usuário da aplicação (24 caracteres) |
| Secret `mysql-root` | Senha de root do MySQL |
| ConfigMap `workshop-db` | `DB_URL` apontando para `mysql.workshop.svc.cluster.local:3306/workshop` |
| Secret `workshop-db` | `DB_USERNAME=workshop_user` e `DB_PASSWORD` |
| Service `mysql` | ClusterIP na porta 3306 (acessível só dentro do cluster) |
| StatefulSet `mysql` | `mysql:8.4`, readiness por `mysqladmin ping`, PVC `data` de 1 Gi |

O MySQL local roda com durabilidade relaxada (`--skip-log-bin`, `--innodb-flush-log-at-trx-commit=2`,
`--innodb-doublewrite=OFF`) para o primeiro boot da aplicação ficar mais rápido. Isso vale só para este ambiente
descartável; um banco real (como o RDS) mantém os padrões.

**Verificar:**

```bash
kubectl get statefulset,pvc -n workshop    # mysql 1/1; PVC data-mysql-0 Bound
```

O `apply` só termina quando o MySQL está pronto. Na primeira vez, ele baixa a imagem (~240 MB) e inicializa o banco.

## 3. Implantar a aplicação

Se já executou `make k8s-local-deploy` no caminho recomendado, avance para a seção 4.

### Com make (Bash, Git Bash, Linux, macOS)

```bash
make k8s-local-deploy
```

### Sem make (PowerShell)

Rode os grupos na ordem, um de cada vez, conferindo a saída de cada um antes de seguir.

```powershell
# Segredos locais: o secrets.env é ignorado pelo git; o modelo traz valores só para desenvolvimento
if (-not (Test-Path k8s/overlays/local/secrets.env)) {
    Copy-Item k8s/overlays/local/secrets.env.example k8s/overlays/local/secrets.env
}

# Imagem: build local e carga no containerd do nó do kind (é o que "kind load docker-image" faz por dentro)
docker build -t workshop-management-system:local .
$arch = docker version --format '{{.Server.Arch}}'
New-Item -ItemType Directory -Force target | Out-Null
docker save --platform "linux/$arch" -o target/workshop-app-local.tar workshop-management-system:local
docker cp target/workshop-app-local.tar workshop-control-plane:/var/tmp/workshop-app-local.tar
docker exec workshop-control-plane ctr --namespace=k8s.io images import /var/tmp/workshop-app-local.tar
docker exec workshop-control-plane rm /var/tmp/workshop-app-local.tar
Remove-Item target/workshop-app-local.tar

# Mapeamentos do simulador do fornecedor (mesmos arquivos do Docker Compose)
kubectl create configmap supplier-simulator-mappings -n workshop --from-file=docker/wiremock/mappings `
    --dry-run=client -o yaml | kubectl apply -f -

# Manifestos e espera pelo rollout
kubectl apply -k k8s/overlays/local
kubectl rollout status deployment/workshop-app -n workshop --timeout=300s
```

Por que cada detalhe:

- **`--platform` no `docker save`:** sem ele, o arquivo inclui manifestos de atestação sem conteúdo, e o `ctr import`
  falha.
- **`/var/tmp` e não `/tmp`:** dentro do nó do kind, `/tmp` é um `tmpfs` montado por cima do disco; o arquivo copiado
  ficaria invisível para o `ctr`.
- **`--dry-run=client -o yaml | kubectl apply -f -`:** gera o ConfigMap e o aplica, o que funciona tanto na criação
  quanto numa atualização (um `create` puro falharia na segunda vez).

**Ao reimplantar depois de mudar o código**, a tag `:local` não muda, então o Kubernetes não vê diferença no
Deployment. Depois do `kubectl apply -k`, force a troca dos pods (o `make k8s-local-deploy` já faz isso):

```bash
kubectl rollout restart deployment/workshop-app -n workshop
kubectl rollout status deployment/workshop-app -n workshop --timeout=300s
```

**Cria (Kustomize, `k8s/overlays/local`):**

| Recurso | Detalhe |
| --- | --- |
| Deployment `workshop-app` | Imagem `:local`, perfil `dev` com seeds, probes de startup/liveness/readiness, request 250m/768Mi e limit 1000m/1Gi |
| Service `workshop-app` | NodePort 30080 → porta 8080 dos pods prontos |
| HPA `workshop-app` | 1 a 3 pods, alvo de 70% de CPU |
| ConfigMap `workshop-app-config` e Secret `workshop-app` | Configuração e segredos (ver [Configuração](configuration.md)) |
| Mailpit | Deployment + Service NodePort 30025 |
| Simulador do fornecedor | Deployment + Service ClusterIP, com os mapeamentos do ConfigMap acima |

## 4. Verificar

```bash
kubectl get pods,svc,hpa -n workshop
curl http://localhost:8080/actuator/health/readiness    # no PowerShell: curl.exe
```

Resultado esperado:

- Pods `workshop-app-...`, `mailpit-...`, `supplier-simulator-...` e `mysql-0` em `1/1 Running`, com 0 restarts.
- Readiness: `{"status":"UP"}`.
- HPA com `TARGETS` em percentual de CPU (por exemplo, `cpu: 2%/70%`) e `REPLICAS 1`. Logo após o deploy aparece
  `cpu: <unknown>/70%` por 1 a 2 minutos, enquanto o metrics-server coleta a primeira amostra; repita o comando.

Com o banco vazio, o primeiro boot leva 2 a 3 minutos (o Flyway aplica todas as migrations). Nesse intervalo, o pod
fica `0/1 Running` sem reiniciar: a `startupProbe` tolera até 5 minutos. Com o banco já migrado, o boot leva ~30 s.

## 5. Usar

| Serviço | URL |
| --- | --- |
| API | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Mailpit | `http://localhost:8025` |

Depois que a readiness responder `{"status":"UP"}`, abra o Swagger, faça login em `POST /api/auth/login`
com a conta de bootstrap `admin` / `changeme123` e use o token no botão **Authorize** para testar os endpoints.
Os e-mails enviados aparecem no Mailpit.

> A conta `admin` é criada por migration em todos os ambientes. Troque a senha fora de um ambiente de demonstração.

Os endereços são os mesmos do Docker Compose, então a collection Postman funciona sem nenhuma mudança: siga o
[teste manual](../api/manual-testing.md) ou rode `make e2e`.

O acesso passa por `localhost:8080` → NodePort → Service, que distribui as requisições entre **todos** os pods prontos.
Por isso não se usa `kubectl port-forward` aqui: ele prende o tráfego num único pod e esconde a escala.

### Próximos passos com o ambiente no ar

- **Testar o autoscaling sob carga:** siga o [guia de teste de carga e HPA](../operations/load-testing-hpa.md).
  Ele mostra como gerar tráfego com `make load-test`, acompanhar a criação e remoção de réplicas e avaliar os resultados.
- **Explorar os demais guias do projeto:** volte à [documentação no README principal](../../README.md#documentação)
  para acessar arquitetura, APIs, CI/CD e implantação na AWS.

Mantenha o ambiente em execução enquanto realiza os testes. Quando terminar, siga a seção 6 para destruí-lo.

## 6. Destruir

Ao terminar, libere memória e disco. A ordem importa: **primeiro o banco, depois o cluster**.

**Com Make:** o comando abaixo executa as duas destruições na ordem correta e apaga os dados do banco:

```bash
make infra-local-down
```

**Sem Make:** execute os comandos abaixo como alternativa. Não execute os dois caminhos.

```bash
terraform -chdir=infra/local/database destroy
terraform -chdir=infra/local/cluster destroy
```

Com a aplicação ainda implantada, o `destroy` do banco fica cerca de 1 minuto em
`kubernetes_namespace_v1.workshop: Still destroying...`: apagar o namespace apaga também tudo o que está dentro dele
(pods da aplicação, Mailpit, simulador), e o Kubernetes espera esses pods terminarem. É normal.

A stack `database` precisa do cluster para conseguir apagar seus recursos. Se o cluster for destruído antes, o
`destroy` do banco falha sem apagar nada e o state fica órfão (veja [Problemas comuns](#problemas-comuns)).

Opções mais leves:

| Objetivo | Comando | Mantém |
| --- | --- | --- |
| Remover só a aplicação | `kubectl delete -k k8s/overlays/local` | Namespace, MySQL e dados |
| Liberar a memória do Windows | Fechar o Docker Desktop e rodar `wsl --shutdown` | Nada em execução; o cluster volta ao reabrir o Docker |

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| `apply` do cluster falha com porta em uso | Docker Compose (ou outro processo) usando 8080 ou 8025 | Identificar o responsável; se for o Compose deste projeto, executar `make docker-down` e repetir (seção 0) |
| `could not find a log line that matches "Reached target .*Multi-User System.*\|detected cgroup v1"` | O Kind não observou a conclusão do boot; uma possibilidade é esgotamento de `inotify` com vários clusters ativos | Seguir o diagnóstico abaixo; a mensagem sozinha não determina a causa |
| `kubectl` responde de outro cluster | Contexto atual não é o do kind | `kubectl config use-context kind-workshop` |
| Pod do app `0/1 Running` por minutos | Primeiro boot com banco vazio (Flyway) | Aguardar até 5 min; acompanhar com `kubectl logs -f deployment/workshop-app -n workshop` |
| Pod do app em `CrashLoopBackOff` | O processo Java terminou no boot (ex.: banco inacessível) | `kubectl logs deployment/workshop-app -n workshop --previous`; corrigir; se foi um deploy ruim, `kubectl rollout undo deployment/workshop-app -n workshop` |
| Pod em `ErrImagePull` ou `ImagePullBackOff` | A imagem `:local` não foi carregada no nó, então o Kubernetes tenta baixá-la do Docker Hub, onde ela não existe | Repetir a carga da imagem (etapa 3); conferir com `docker exec workshop-control-plane crictl images` |
| HPA com `<unknown>/70%` | metrics-server ainda coletando (~1 min após subir o pod) | Aguardar; se persistir, `kubectl top pods -n workshop` |
| `destroy` do banco falha com `connection refused` | O cluster foi destruído antes | O state ficou órfão: apague `infra/local/database/terraform.tfstate*` (ambiente local e descartável) |
| Tudo lento, pods `Pending` | Pouca memória para o WSL2 | Aumentar `memory` no `.wslconfig`, `wsl --shutdown` e reabrir o Docker |

### Falha na espera pelo boot do Kind

No Linux/WSL que executa o Docker, confira os limites de monitoramento de arquivos:

```bash
sysctl fs.inotify.max_user_instances fs.inotify.max_user_watches
```

Com vários clusters ativos, o limite de 128 instâncias pode ser insuficiente. Se os valores estiverem abaixo de
512 instâncias e 524288 watches, aumente apenas os que estiverem abaixo desses valores, conforme a
[orientação do Kind](https://kind.sigs.k8s.io/docs/user/known-issues/#pod-errors-due-to-too-many-open-files):

```bash
sudo sysctl -w fs.inotify.max_user_instances=512
sudo sysctl -w fs.inotify.max_user_watches=524288
```

Esses ajustes são temporários e afetam o host; não são executados pelo Makefile. Repita `make infra-local-up`.
Se a falha persistir, em outro terminal capture `docker logs -f workshop-control-plane` assim que o container
aparecer durante a tentativa. O Kind pode removê-lo ao falhar, tornando os logs indisponíveis depois.
Procure mensagens como `Too many open files` antes de atribuir a falha ao limite de `inotify`.

## Segurança

- O `secrets.env` e os `terraform.tfstate` (que contêm as senhas geradas) são ignorados pelo git. Nunca os versione.
- Os valores de `secrets.env.example` servem só para este cluster local.
- A conta `admin`/`changeme123`, criada por migration, existe em todos os ambientes. Aqui ela só é acessível por
  `localhost`.

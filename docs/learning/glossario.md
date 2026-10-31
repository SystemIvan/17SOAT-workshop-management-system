# Glossário de infraestrutura

Glossário de aprendizado da Fase 2. Cada termo tem uma explicação curta, uma analogia com a oficina e um link para
onde ele aparece no projeto. Os termos são aprofundados no passo em que forem usados pela primeira vez; entradas
marcadas com _(introdução)_ ainda serão expandidas.

## Arquitetura e processo

### ADR (Architecture Decision Record)

Documento curto que registra uma decisão de arquitetura: contexto, opções avaliadas, decisão e trade-offs aceitos.
Nasce `Proposed` e só um humano o promove a `Accepted`. É a "ata da reunião" que explica por que a oficina comprou
este elevador e não outro. Usado em [`docs/adr/`](../adr/), por exemplo na
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### Trade-off

O que se perde ao escolher uma opção. Toda decisão tem um, e a ADR obriga a registrá-lo na seção "Negativas".

### SDD e especificação funcional

SDD (Spec-Driven Development) é escrever e aprovar o comportamento antes do código. A especificação funcional diz
**o quê** e **por quê**, em linguagem de negócio; o **como** fica na technical-spec. Analogia: a ordem de serviço
descreve o defeito e o resultado esperado antes de o mecânico escolher as ferramentas. Ver
[functional-spec da infra](../features/platform/kubernetes-infrastructure-delivery/functional-spec.md).

### Critério de aceite

Frase verificável de forma independente que diz quando um comportamento está pronto ("sem carga, as cópias voltam ao
mínimo"). Se não dá para testar, não é critério de aceite. Analogia: o checklist de entrega do veículo.

### Mitigação e risco residual

**Mitigar** é reduzir a chance ou o impacto de um risco sem eliminá-lo; o que sobra é o **risco residual**, que alguém
com autoridade precisa **aceitar** por escrito (o agente nunca aceita no lugar da pessoa). Analogia: o carro com
freio gasto sai com aviso na OS e a assinatura do cliente, porque a peça ainda não chegou. Exemplo: o admin de
bootstrap na AWS, restrito a um `/32` e destruído após o uso, na
[revisão de segurança da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md#revisão-de-segurança).

### Modelo C4 e diagrama de implantação

O C4 desenha um sistema em níveis de zoom: contexto (quem usa), containers (quais processos rodam), componentes (como
um processo se divide) e código. O **diagrama de implantação** complementa esses níveis e mostra **onde** cada
processo roda: rede, máquinas, serviços gerenciados e por onde o tráfego passa. Analogia: a planta da oficina mostra
as seções; a planta de instalação mostra em qual elevador cada carro fica e por qual portão ele entra. Usado no
[overview, seção 4](../architecture/overview.md#4-implantação), inclusive na infraestrutura da AWS (4.2).

### Diátaxis (tutorial, how-to, referência, explicação)

Forma de dividir a documentação pelo que o leitor quer fazer: **tutorial** (aprender seguindo passos, como o
[guia da AWS](../setup/aws-academy.md)), **how-to** (resolver uma tarefa, como o
[teste de carga](../operations/load-testing-hpa.md)), **referência** (consultar fatos, como o
[CI/CD](../operations/ci-cd.md)) e **explicação** (entender o porquê, como o overview). Analogia: o curso de formação
do mecânico, a ficha de procedimento, o catálogo de peças e a aula teórica são documentos diferentes. A coluna "Tipo"
do [índice](../README.md) segue essa divisão.

### Runbook

Roteiro operacional que alguém segue passo a passo, sem precisar entender tudo antes: comando, saída esperada e o que
fazer se der diferente. Só vale se foi **executado do zero** por outra pessoa, porque quem escreveu pula passos
implícitos sem perceber. Analogia: o procedimento de troca de óleo pendurado no box, que o mecânico novo segue sem
perguntar. No projeto: [Ambiente Kubernetes local](../setup/local-kubernetes.md) e
[Ambiente AWS Academy](../setup/aws-academy.md), com as seções "Verificar" e "Problemas comuns".

### Fonte única da verdade

Cada fato vive em **um** documento; os outros apontam para ele em vez de copiar. Cópias envelhecem em ritmos
diferentes (foi assim que o `Architecture.md` ficou desatualizado). Analogia: o preço da peça fica só no cadastro do
estoque; o orçamento consulta, não copia à mão. Por isso o README só aponta para o
[guia da AWS](../setup/aws-academy.md), que é quem lista os recursos criados.

## Containers

### Container x VM

Uma VM simula um computador inteiro, com sistema operacional próprio; um container é só um processo isolado que
compartilha o kernel (o núcleo do sistema operacional) da máquina. Por isso o container sobe em segundos e ocupa
megabytes, não gigabytes. Analogia: a VM é uma oficina inteira alugada; o container é um box dentro da mesma oficina.

### Imagem, Dockerfile e camadas

A imagem é o "molde" somente leitura de onde os containers nascem; o [Dockerfile](../../Dockerfile) é a receita
dela. Cada instrução (`COPY`, `RUN`) gera uma camada, e o Docker reaproveita (cacheia) as camadas que não mudaram: por
isso o `pom.xml` é copiado antes do `src`. Analogia: o kit de peças padrão montado uma vez e reutilizado em cada box.

### Multi-stage build

Dockerfile com mais de um `FROM`: o primeiro estágio (Maven + JDK) compila; o último (só JRE) recebe apenas o `.jar`
por `COPY --from`. A imagem final não leva compilador nem código-fonte: fica menor e com menos vulnerabilidades. Ver o
[Dockerfile](../../Dockerfile).

### Usuário não-root e UID numérico

O processo roda como `USER 1000`, e não como root (administrador): se alguém explorar uma falha da aplicação, não
ganha poder sobre o container. O UID numérico permite ao Kubernetes verificar `runAsNonRoot`; com um nome, ele não
sabe se o usuário é root. Analogia: o estagiário tem crachá de box, não a chave do cofre.

### `ENTRYPOINT` na forma exec, PID 1 e `SIGTERM`

Na forma exec (`["java", "-jar", ...]`) a JVM é o PID 1, o primeiro processo do container, e recebe o `SIGTERM` (o
aviso "termine o que está fazendo") quando o Docker ou o Kubernetes param o container. Um script na frente só é seguro
se terminar com `exec "$@"` (que troca o bash pela JVM); sem o `exec`, o sinal para no bash e a JVM é morta à força
(`SIGKILL`) depois do prazo. Ver o [Dockerfile](../../Dockerfile).

### `JAVA_TOOL_OPTIONS` e `MaxRAMPercentage`

`JAVA_TOOL_OPTIONS` é lida pela própria JVM em qualquer forma de inicialização (o antigo `JAVA_OPTS` só funcionava
com um script que o repassasse, e era ignorado). `-XX:MaxRAMPercentage=75.0` limita o heap a 75% da memória do
container, deixando o resto para threads e metaspace: com limite de 1 Gi, o heap fica em ~768 Mi.

### Variáveis de ambiente e contrato `DB_*`

Valores entregues ao processo de fora do código (`DB_URL`, `DB_PASSWORD`): a mesma imagem roda no compose, no kind e
na AWS, mudando só o ambiente. O projeto usa um único nome por configuração (D1 da
[technical-spec](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md)). Analogia: a mesma
ferramenta, calibrada de outro jeito em cada oficina.

### `depends_on` com `service_healthy`

No [docker-compose.yml](../../docker-compose.yml), o app só é iniciado quando o healthcheck do MySQL passa. Isso
substituiu o `wait-for-mysql.sh`, que exigia `mysql-client` e `netcat` na imagem; no Kubernetes, quem segura o
tráfego até o banco responder é a readiness.

### Tag de imagem

Etiqueta que identifica uma versão de uma imagem (`app:3d0cdb3`). Sem tag, o Docker usa `latest`, um nome
reaproveitado que cada push sobrescreve. A tag pelo SHA do commit é única e rastreável: é o número de série da peça.
Decidida na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### CRLF x LF e shebang

Fim de linha: Linux usa `\n` (LF), Windows usa `\r\n` (CRLF); com `core.autocrlf=true` o git entrega CRLF na pasta.
O shebang (`#!/bin/bash`, 1ª linha do script) diz ao Linux quem executa o arquivo; com CRLF ele vira `bash\r`, que não
existe, e o container falha com "no such file or directory". Correção: `*.sh text eol=lf` no `.gitattributes`.
Analogia: um caractere invisível no nome da ferramenta do manual. Ver o achado do
[CP7](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### `docker compose up --build`

Sem `--build`, o Compose reaproveita a imagem que já existe, mesmo com o código alterado; com `--build`, ele
recompila a imagem antes de subir. Sintoma de imagem velha: endpoint novo ausente no Swagger. Ver
[docker-compose.yml](../../docker-compose.yml).

### WSL2 e `.wslconfig`

No Windows, o Docker Desktop roda os containers dentro de uma VM Linux leve (WSL2). O `~/.wslconfig` define o teto de
memória e de CPUs dessa VM; são tetos, não reservas. Mudanças só valem após `wsl --shutdown`. Analogia: o tamanho do
galpão alugado: não cabem mais boxes do que o galpão comporta. Ajustado para 7 GB/5 CPUs no
[CP4 da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md).

### Digest de imagem

Impressão digital (`@sha256:...`) do conteúdo exato de uma imagem. A tag (`v1.35.0`) pode ser republicada; o digest,
não. Fixar por digest garante a mesma imagem no PC e na pipeline. Usado no node do kind em
[variables.tf](../../infra/local/cluster/variables.tf).

## Kubernetes

### Pod _(introdução)_

Menor unidade que o Kubernetes executa; aqui, uma cópia da aplicação rodando. Pods são descartáveis: morrem, são
recriados e mudam de endereço. Analogia: um box de atendimento (ou uma sala de aula).

### Service _(introdução)_

Endereço fixo que recebe as requisições e as encaminha para qualquer pod disponível. Existe porque os pods mudam;
o cliente (Postman) chama sempre o Service e não precisa saber qual pod responde. Analogia: a secretaria da escola,
que manda o aluno para qualquer sala com aquela aula.

### kind

"Kubernetes IN Docker": cria um cluster Kubernetes em que cada node é um container Docker
(`workshop-control-plane` no `docker ps`). Usado no ambiente local e dentro do job da pipeline. Alternativas:
minikube e k3d. Escolhido na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md); criado pela
stack [infra/local/cluster](../../infra/local/cluster/main.tf), sem instalar o kind (o provider embute a biblioteca).

### Namespace

Divisão lógica do cluster que agrupa recursos com nomes próprios (`workshop`, `kube-system`). Apagar o namespace
apaga tudo dentro dele, inclusive PVCs. Por isso tem um único dono: a stack
[database](../../infra/local/database/main.tf). Analogia: uma ala da oficina com seus boxes.

### Label e selector

Etiquetas chave=valor nos objetos (`app=mysql`). O selector de um Service ou StatefulSet escolhe "todos os pods com
esta etiqueta"; o `kubectl get pods -l app=mysql` usa o mesmo mecanismo. Analogia: a etiqueta colorida na chave do
carro que diz em qual box ele está.

### StorageClass e reclaim policy

A StorageClass cria o disco (PV) automaticamente quando surge um PVC; no kind, a `standard` usa uma pasta dentro do
node. A reclaim policy diz o que acontece com o disco quando o PVC some: `Delete` apaga, `Retain` guarda. Analogia:
o almoxarifado que entrega o armário sob pedido e o esvazia (ou não) na devolução.

### Helm e chart

O Helm é o gerenciador de pacotes do Kubernetes; um chart é um pacote de YAMLs parametrizáveis por `values`. O
metrics-server é instalado pelo chart oficial via provider `helm`, sem instalar o Helm. Analogia: um kit de peças já
montado, em vez de parafusos avulsos. Ver [infra/local/cluster](../../infra/local/cluster/main.tf).

### kubeconfig

Arquivo com o endereço do cluster e a **credencial** de acesso a ele; é o que o `kubectl` lê para saber com quem
falar. Quem tem o arquivo administra o cluster, por isso `kubeconfig*` está no [.gitignore](../../.gitignore).
Analogia: a chave da oficina com o endereço gravado no chaveiro.

### minikube

Outra ferramenta de Kubernetes local, focada na experiência de desenvolvimento: traz addons prontos
(`minikube addons enable metrics-server`) e atalhos de acesso (`minikube service`, `minikube tunnel`). O kind é mais
cru (instala-se tudo manualmente), mas é mais leve, sobe mais rápido e é o padrão em pipelines de CI. Analogia:
minikube é a oficina-escola com ferramentas já penduradas; kind é o galpão vazio que monta e desmonta rápido.

### Liveness x readiness

Duas perguntas diferentes que o Kubernetes faz à aplicação. **Liveness**: "você está vivo?"; se não, o pod é
reiniciado. **Readiness**: "você consegue atender agora?"; se não, sai do Service até melhorar. A liveness não deve
depender do banco, senão uma queda do banco reinicia todos os pods à toa. Analogia: o mecânico está no turno
(liveness) x tem as ferramentas para pegar um carro agora (readiness). Ver
[functional-spec da infra](../features/platform/kubernetes-infrastructure-delivery/functional-spec.md).
No projeto, a liveness olha só o estado interno (`livenessState`) e a readiness olha `readinessState` + `db`
([actuator.properties](../../src/main/resources/actuator.properties)). Banco fora → readiness `503` e liveness `200`,
provado em [HealthEndpointDatabaseDownTest](../../src/test/java/br/com/fiap/workshop_management_system/HealthEndpointDatabaseDownTest.java).

### Health check (healthcheck do compose)

Comando que o orquestrador roda periodicamente para saber se o container está saudável. No
[docker-compose.yml](../../docker-compose.yml), o app chama a readiness com `curl -f` (falha em 4xx/5xx), e o
`start_period` dá tempo ao boot antes de contar falhas. O Kubernetes ignora o `HEALTHCHECK` do Dockerfile e usa os
próprios probes; por isso ele não está na imagem.

### Rolling update _(introdução)_

Forma padrão de o Deployment trocar de versão: sobe os pods novos e só desliga os antigos quando os novos ficam
prontos. Se a versão nova nunca fica pronta, a antiga continua atendendo.

### Deployment e ReplicaSet

O Deployment é o plano permanente ("N cópias desta imagem com esta configuração"); ele cria um ReplicaSet, que só
mantém N pods vivos e repõe quem morre. Cada mudança no template do pod gera um ReplicaSet novo, e o rolling update
passa as réplicas do antigo para o novo (`maxSurge`/`maxUnavailable`). Com HPA, o Deployment **não** declara
`replicas`. Analogia: chefe de oficina (plano) e encarregado de turma (repõe mecânicos). Ver
[app-deployment.yaml](../../k8s/base/app-deployment.yaml).

### `rollout undo`, revisões e `last-applied-configuration`

`kubectl rollout undo` reativa o ReplicaSet anterior (a revisão volta com número novo no `rollout history`). O aviso
sobre `last-applied-configuration` lembra que o cluster pode divergir do Git: o `apply` compara com essa anotação. A
correção definitiva vai para o arquivo; `kubectl diff -k` mostra se ainda há divergência.

### `CrashLoopBackOff` e código de saída

O container encerra sozinho (ex.: Flyway sem achar o host do banco), o kubelet o reinicia com espera crescente
(10 s, 20 s, 40 s...) e mostra `CrashLoopBackOff` entre as tentativas. É diferente da liveness, que mata um processo
vivo. A JVM encerrada por `SIGTERM` sai com código 143 (128 + 15), por isso um pod em desligamento normal aparece como
`Error` por um instante.

### `securityContext`

Regras de segurança do processo: `runAsNonRoot`/`runAsUser`, `allowPrivilegeEscalation: false`,
`readOnlyRootFilesystem`, `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault`. Analogia: crachá de
funcionário, não de dono. Imagens de terceiros que rodam como root não aceitam `runAsNonRoot`. Ver
[app-deployment.yaml](../../k8s/base/app-deployment.yaml).

### `emptyDir`

Pasta temporária gravável que nasce e morre com o pod. É o que permite `readOnlyRootFilesystem`: só `/tmp` (Tomcat,
JVM) fica gravável.

### Generator do Kustomize (hash no nome)

`configMapGenerator`/`secretGenerator` criam o objeto com um sufixo que é o hash do conteúdo
(`workshop-app-config-gbdf27b9fm`). Mudar um valor muda o nome referenciado pelo Deployment e dispara um rolling
update; sem isso, os pods ficariam com o valor antigo. `behavior: merge` deixa o overlay acrescentar chaves ao
generator da base.

### containerd, `ctr` e carregar imagem no kind

O node do kind tem o próprio containerd (o motor que roda os containers do Kubernetes) e não enxerga as imagens do
Docker Desktop. Carregar = `docker save` → `docker cp` → `ctr --namespace=k8s.io images import`; `crictl images`
mostra o que o kubelet vê. Dentro do node, `/tmp` é `tmpfs` (memória): o `docker cp` grava por baixo da montagem e o
arquivo some, por isso usamos `/var/tmp`.

### Calibração de probe x correção estrutural

A `startupProbe` subiu de 3 para 5 min porque o 1º boot em banco vazio (25 migrations) levou 165 s, e o boot com
banco migrado leva 27 s. Calibrar com evidência resolve o prazo; a correção estrutural seria separar as migrations
do boot (Job de migração), porque a probe passou a ser, sem querer, o prazo das migrations.

### startupProbe

Terceira verificação de saúde, válida só durante o boot. Enquanto ela não passa, a liveness não é feita; assim um boot
lento (Flyway, JVM) não é confundido com processo travado. Analogia: não cobrar produtividade do mecânico enquanto ele
troca de roupa. Ver [technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### Requests e limits

`request` é quanto de CPU/memória o pod tem reservado (usado para escolher o node e como base do HPA); `limit` é o
teto. Quem passa do limite de memória é morto (`OOMKilled`); quem passa do de CPU é desacelerado. Analogia: vaga
reservada no pátio × tamanho máximo do box.

### HPA (Horizontal Pod Autoscaler)

Ajusta o número de réplicas de um Deployment entre um mínimo e um máximo, comparando o consumo médio com um alvo
percentual da request (ex.: 70% de CPU). Analogia: o gerente chama mais mecânicos quando a fila cresce. Ver
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### metrics-server

Componente que coleta CPU e memória dos pods a partir do kubelet de cada node; sem ele o HPA fica cego. No kind roda
com `--kubelet-insecure-tls`, porque o certificado do kubelet é autoassinado. Analogia: o medidor de fila do gerente.

### Fórmula do HPA e tolerância

A cada 15 s: `réplicas desejadas = ceil(réplicas atuais × uso ÷ alvo)`, com o uso em % da request; com várias
métricas vale a maior recomendação, e razões entre 0,9 e 1,1 são ignoradas (tolerância). Ex. do CP5: 1 pod a 148% de
CPU → `ceil(1 × 148 ÷ 70) = 3`, limitado pelo `maxReplicas`. Ver [`app-hpa.yaml`](../../k8s/base/app-hpa.yaml).

### Janela de estabilização

Para descer, o HPA usa a **maior** recomendação dos últimos N segundos (60 s no local), evitando dispensar um pod num
vale curto de carga; para subir, a janela padrão é 0 (age na hora). Analogia: só dispensar o mecânico extra após um
minuto de fila vazia. Medido no CP5: 3 → 2 → 1 cerca de 1 min depois do fim da carga.

### Período de inicialização de CPU

O HPA ignora a CPU de pods que ainda não ficaram prontos (e trata com cuidado os primeiros minutos de vida), porque o
boot da JVM consome muito (≈1000m no CP5) e geraria escala em cascata. Analogia: não medir a produtividade do novato
enquanto ele veste o macacão. Parente da [startupProbe](#startupprobe).

### HPA por memória com JVM

A JVM não devolve o heap depois de um pico, então a memória média não cai e trava a escala: no CP5, 2 pods ociosos a
48% davam `ceil(2 × 48 ÷ 85) = 2` para sempre. Por isso o HPA do projeto usa **só CPU**; a memória é limitada pelo
`limit` + `MaxRAMPercentage`. Ver a nota do CP5 na
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### Kustomize (base e overlay)

Ferramenta embutida no `kubectl` (`apply -k`) que monta os YAMLs a partir de uma `base` comum e de `overlays` com só
as diferenças de cada ambiente. Analogia: ficha padrão de revisão + anexo por modelo de carro.

### ConfigMap e Secret

Objetos que entregam configuração ao pod, normalmente como variáveis de ambiente. ConfigMap para valores comuns;
Secret para sensíveis (acesso mais restrito, fora do `describe`). Analogia: quadro de avisos × cofre.

### StatefulSet e PV/PVC

StatefulSet é o controlador para quem guarda dados: nome estável (`mysql-0`) e disco próprio. O PVC
(PersistentVolumeClaim) é o pedido de disco; o PV é o disco entregue. O disco sobrevive à morte do pod. Analogia: o
arquivo de fichas fica no lugar mesmo que o arquivista troque. Usado no MySQL local.

### NodePort, extraPortMappings e port-forward

NodePort abre uma porta fixa (30000–32767) no node e encaminha ao Service. No kind o node é um container, então o
`extraPortMappings` liga essa porta ao `localhost` do PC. `kubectl port-forward` também dá acesso, mas prende o
tráfego em um único pod, o que invalida um teste de HPA. Analogia: janela de atendimento que dá para a rua.

## Terraform

### IaC (Infraestrutura como Código)

Descrever em arquivos o que deve existir e deixar uma ferramenta fazer a realidade bater com eles. O mesmo código
recria o ambiente do zero, no PC e na pipeline. Analogia: a ficha técnica do modelo, que qualquer mecânico segue e
chega ao mesmo carro. Ver [infra/local](../../infra/local).

### Provider

Plugin que ensina o Terraform a falar com uma plataforma (`tehcyx/kind`, `hashicorp/helm`, `hashicorp/kubernetes`,
`hashicorp/random`; na AWS, `hashicorp/aws`). Versões fixadas em `versions.tf` com `~> X.Y.0` (só correções).
Analogia: o fornecedor homologado pelo qual o almoxarifado pede peças.

### Resource e dependência implícita

`resource "tipo" "nome" { }` descreve uma coisa que deve existir. Quando um resource usa o atributo de outro
(`random_password.app.result` no Secret), o Terraform deduz a ordem de criação e a inverte no `destroy`; a listagem do
`plan` é alfabética e não mostra essa ordem. Analogia: itens da ficha técnica que dependem de outros.

### Variable e output

Variable é a entrada da stack (`cluster_name = "workshop"`); output é a saída, mostrada no fim do `apply`
(`kubeconfig_context`). O output de uma stack alimenta a variable de outra. Analogia: a OS que chega e o laudo que
sai. Ver [outputs.tf](../../infra/local/cluster/outputs.tf).

### `init` / `plan` / `apply` / `destroy`

`init` baixa os providers e cria/confere o lock file; `plan` mostra o que será criado (`+`), alterado (`~`) ou
destruído (`-`) sem tocar em nada; `apply` executa após `yes`; `destroy` remove tudo o que está no state. Analogia:
diagnóstico, orçamento, aprovação e execução da OS; nunca se aprova sem ler o orçamento.

### Valor sensível (`sensitive`)

Valor que o Terraform esconde no `plan` (`(sensitive value)`), como senhas do `random_password`. Continua em texto
puro no state. Depende de o provider marcar o atributo: o `tehcyx/kind` não marca a chave do cluster, que aparece
no `destroy` (achado registrado no
[plano da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md)).

### Stack, state e lock file

Stack é uma pasta com `init/plan/apply` próprios (aqui, `cluster` e `database` por ambiente). O state é o inventário
do que o Terraform criou e pode conter segredos: nunca é versionado. O `.terraform.lock.hcl` fixa as versões dos
providers e **é** versionado. Analogia: inventário do almoxarifado × lista de fornecedores homologados. Ver
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### `.terraform/`, `.tfvars` e plano salvo

`.terraform/` é a pasta onde o `terraform init` baixa os providers (binários grandes, recriados a partir do lock file).
`*.tfvars` guarda os valores das variáveis de cada desenvolvedor (região, IP, senhas); versiona-se só um
`*.tfvars.example` com valores fictícios. Um plano salvo (`terraform plan -out=*.tfplan`) carrega os mesmos valores
sensíveis do state. Os três ficam fora do git pelo [.gitignore](../../.gitignore).

### `.gitignore` e negação (`!`)

Lista de padrões de arquivos que o git não oferece para commit. `!padrão` reabre uma exceção (`!*.tfvars.example`).
Só protege arquivos **ainda não versionados**: se um segredo já foi commitado, ignorar depois não o apaga do
histórico (é preciso trocar o segredo). Por isso o CP3 da infra vem **antes** de qualquer Terraform. Conferência:
`git check-ignore -v <arquivo>` mostra qual linha casou; `git status` é a prova final.

### Drift

Diferença entre o que o código/state descreve e o que existe de verdade, causada por mudança fora do fluxo (clique no
console, `kubectl edit`, outro dono apagando o recurso). O `terraform plan` e o `kubectl diff -k` revelam o drift:
saída vazia significa "real = código". Analogia: a ficha do carro diz pneu novo, mas alguém trocou por um usado sem
registrar. O projeto evita drift com **dono único** de cada recurso: se o Kustomize declarasse o namespace, um
`kubectl delete -k` apagaria o banco e deixaria o state do Terraform mentindo
([overview, §4.4](../architecture/overview.md#44-ambientes)).

## Aplicação

### Actuator

Módulo do Spring Boot que expõe endpoints operacionais prontos, como `/actuator/health/liveness` e `/readiness`, e já
sabe checar o banco. Analogia: o painel de luzes do carro. Aqui só `health` é exposto, sem token e sem detalhes
(`show-details=never`): mostrar qual componente falhou ou a versão do banco ajudaria um atacante. A regra de acesso
fica no [SecurityConfig](../../src/main/java/br/com/fiap/workshop_management_system/identity/SecurityConfig.java).

### Arquivo de propriedades sombreado nos testes

`src/test/resources/application.properties` tem o mesmo nome do arquivo principal e o **substitui** nos testes (vence
o primeiro achado no classpath). A configuração que precisa ser testada de verdade fica num arquivo próprio, importado
pelos dois (`spring.config.import=classpath:actuator.properties`); senão o teste validaria uma cópia.

### Teste de mutação (manual)

Quebrar o código ou a configuração de propósito e conferir que algum teste fica vermelho. No CP2 da infra, tirar o
`db` da readiness e mostrar os componentes derrubou 3 testes: é a prova de que eles não são um falso verde.

### Clean Architecture / Hexagonal: porta e adapter

O código fica em camadas: `domain` (regras puras), `application` (casos de uso) e `infrastructure` (HTTP, banco,
outros módulos). O caso de uso não chama o mundo de fora direto: ele declara uma **porta** (interface dele, ex.
`CustomerIntakePort`), e um **adapter** na infraestrutura a implementa, traduzindo tipos e erros de outro módulo.
Analogia: a oficina pede "uma peça com estas medidas" (porta); o comprador sabe qual fornecedor e qual catálogo usar
(adapter). Trocar de fornecedor não muda a oficina. Ver `serviceorder/infrastructure/registration` e o
[CP3](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Dependência cíclica entre pacotes

Quando o pacote A usa o B e o B usa o A, nenhum dos dois pode mudar ou ser testado sem o outro: viram um bloco só.
A saída é um terceiro pacote que depende dos dois (aqui, `servicelifecycle/intake` usa `serviceorder` e
`estimate`). Analogia: em vez de o mecânico e o orçamentista dependerem um do outro, o atendente coordena os dois.
Ver o [CP4](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Códigos HTTP: 4xx x 5xx (400, 401, 403, 404, 409, 500)

4xx = erro de quem chama; 5xx = erro de quem responde. 400: pedido mal formado. 401: sem identificação (falta o
token). 403: identificado, mas sem permissão (ex.: `TECHNICIAN` na abertura completa). 404: o recurso não existe.
409: o pedido é válido, mas conflita com o estado atual (cliente arquivado, placa de outro dono). 500: falha interna.
Analogia: 401 é chegar sem crachá; 403 é ter crachá de mecânico e tentar entrar no caixa. Ver o
[CP5](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Endpoint de orquestração aditivo

Endpoint novo que chama, em sequência, casos de uso que já existem (cadastro, abertura de OS, diagnóstico, orçamento),
sem mudar os endpoints antigos. "Aditivo" = só acrescenta, e por isso não quebra quem usa o contrato atual. Analogia:
um atendente que leva a ficha completa aos vários guichês da oficina. Ver
[abertura completa de OS](../features/servicelifecycle/service-order-full-intake/functional-spec.md).

### Transação e atomicidade

Transação é um bloco de gravações no banco tratado como unidade: ou tudo é confirmado (_commit_), ou tudo é desfeito
(_rollback_). Atomicidade é essa garantia de "tudo ou nada". Analogia: se a OS é recusada no último guichê, a ficha do
cliente cadastrada no primeiro também é rasgada. Ver a regra "Atomicidade" da
[abertura completa de OS](../features/servicelifecycle/service-order-full-intake/functional-spec.md).

### Durabilidade, `fsync`, redo log, binlog e doublewrite

Durabilidade (o "D" de ACID) é o commit sobreviver a uma queda de energia; o `fsync` força o dado da memória para o
disco e é lento. O MySQL faz `fsync` do **redo log** (diário interno) a cada commit, mantém o **binlog** (diário para
replicação) e grava páginas duas vezes (**doublewrite**). No MySQL local desligamos os três
(`--skip-log-bin`, `--innodb-flush-log-at-trx-commit=2`, `--innodb-doublewrite=OFF`, em
[`infra/local/database/main.tf`](../../infra/local/database/main.tf)): banco descartável; nunca no RDS. Analogia: o
mecânico ir ao cofre a cada anotação x uma vez por segundo.

### Custo de DDL e squash de migrations

No MySQL 8 cada tabela é um arquivo `.ibd`; `CREATE TABLE` e `ALTER` que reconstrói a tabela criam arquivo e fazem
`fsync` obrigatório (~1,2–1,5 s no kind sobre WSL2), enquanto `INSERT`/`UPDATE` levaram 0,00 s. *Squash* (baseline) é
consolidar o histórico de migrations num schema final só com `CREATE`s; muda checksums do Flyway e exige recriar
bancos, por isso é decisão do time. Ver o CP5 do
[plano da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md).

### Propagação de transação (`REQUIRED` x `MANDATORY`)

Regra do Spring sobre o que um método `@Transactional` faz quando é chamado dentro de outra transação. `REQUIRED` (o
padrão) entra na transação que já existe ou abre uma nova. `MANDATORY` exige que já exista uma, e falha se não
existir. Analogia: `MANDATORY` é o guichê que só atende quem chega com a ficha aberta no balcão. Ver
[technical-spec da abertura completa](../features/servicelifecycle/service-order-full-intake/technical-spec.md).

### Spring Modulith e Named Interface

O Modulith verifica, por teste (`ModuleStructureTest`), que um módulo só usa de outro o que foi publicado. A Named
Interface é esse "balcão público" do módulo, por exemplo o pacote `application/api` de `registration`. Analogia: o
almoxarifado atende pela janela, e ninguém entra na prateleira dele. Ver a
[ADR-005](../adr/ADR-005-inter-module-integration-contract.md).

### Deadlock e ordem de lock

Deadlock é quando duas transações esperam uma pela outra para sempre: A trancou a peça 1 e quer a 2, enquanto B
trancou a 2 e quer a 1. O banco detecta e derruba uma delas. A prevenção clássica é sempre trancar na **mesma ordem**
(aqui, IDs ordenados). Analogia: dois mecânicos pegando chave e macaco; se ambos pegam primeiro a chave, ninguém trava.
Ver `QuoteStockItemsUseCase` no
[CP2](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Teste unitário, teste de integração e mock

Teste unitário testa uma classe isolada, trocando as dependências por **mocks** (dublês do Mockito que respondem o que
o teste mandar), e roda em milissegundos. Teste de integração sobe o Spring e um banco de verdade (aqui, H2 em
memória) para provar que as peças funcionam juntas, por exemplo um rollback. Analogia: testar o freio na bancada x
testar o carro na pista. Ver os testes do
[CP1](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Teste de aceitação (ponta a ponta) e rastreabilidade

Teste que exercita o fluxo inteiro como o usuário faria (HTTP → caso de uso → banco), um por critério de aceite da
spec. Rastreabilidade é a tabela "critério → teste que o prova": mostra que nada da spec ficou sem verificação.
Analogia: o test-drive depois do conserto, com a ficha da OS na mão, item por item. Ver a tabela do
[CP6](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Condição de corrida e lock pessimista

Condição de corrida: duas requisições simultâneas veem "não existe" ao mesmo tempo e as duas tentam criar o mesmo
registro. Lock pessimista (`SELECT ... FOR UPDATE`) tranca a linha lida até o fim da transação. A constraint `UNIQUE`
do banco é a última barreira. Analogia: dois atendentes cadastrando o mesmo cliente; o sistema aceita um e o outro
recebe "já existe, tente de novo". Ver
[technical-spec da abertura completa](../features/servicelifecycle/service-order-full-intake/technical-spec.md).

### Validação em cascata (`@Valid`) e elemento nulo

`@Valid` numa lista manda o Bean Validation validar **cada objeto** dela, mas um elemento `null` não tem o que
validar e passa. Para rejeitá-lo, a anotação vai no tipo do elemento: `List<@NotNull IntakeServiceRequest>`.
Sem isso, `"services": [null]` virava `NullPointerException` e `500`. Analogia: conferir cada peça da caixa, mas não
perceber um espaço vazio onde deveria haver uma peça. Ver o achado do
[CP8](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Cobertura de código (JaCoCo)

Percentual do código executado pelos testes: instruções (bytecode), linhas e branches (cada caminho de um `if`).
O JaCoCo mede e o `./mvnw verify` falha abaixo de 80%. Cobertura alta não prova que o teste confere algo; ela
mostra o que **nunca** foi exercitado (aqui, o endereço do intake). Analogia: a lista de itens do check-up que o
mecânico nem chegou a olhar. Relatório em `target/site/jacoco/index.html`.

### OpenAPI e teste de contrato

OpenAPI é a descrição da API em JSON (`/v3/api-docs`), gerada pelo Springdoc a partir das anotações do controller; o
Swagger UI a desenha. O teste de contrato lê esse JSON e falha se um endpoint, um código de resposta ou um campo sumir
ou aparecer sem querer, como um `price` no body da abertura completa. Analogia: o catálogo de serviços impresso no
balcão, conferido contra o sistema antes de cada mudança. Ver `OpenApiContractTest` e o
[CP7](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Postman, collection e Newman

Postman é um cliente HTTP; a collection é o arquivo versionado com as requests, variáveis (`{{baseUrl}}`,
`{{authToken}}`) e scripts (`pre-request` prepara dados, `test` confere a resposta e guarda IDs). Newman executa a
mesma collection na linha de comando (`make e2e`), o que a transforma em smoke test. Analogia: o roteiro de
test-drive escrito, que qualquer mecânico repete igual. Ver
[a collection](../api/postman/workshop-management-system.postman_collection.json).

### Environment do Postman e precedência de variáveis

Um environment é um conjunto nomeado de variáveis ("Local", "AWS") que se troca num seletor. Quando o mesmo nome
existe nos dois lugares, o valor do **environment vence** o da collection. Assim, a mesma collection aponta para
`localhost:8080` ou para o Load Balancer da AWS sem editar nenhuma request. Analogia: o mesmo roteiro de test-drive,
feito em outra filial. Na AWS o banco começa vazio (sem seed), então os IDs fixos da collection não existem lá e
precisam ser criados pelas requests `Create ...`.

## CI/CD

### CI x CD

CI (integração contínua) compila e testa cada mudança: "o código está saudável?". CD (entrega contínua) empacota,
instala num ambiente e confere que funciona: "dá para rodar de verdade?". Analogia: inspecionar a peça na bancada x
montá-la no carro e dar a partida. Aqui: `ci.yml` (do grupo) e `cd.yml` (passo 9).

### Workflow, job, step e action

Workflow é o arquivo em `.github/workflows/`; job é uma etapa que roda numa VM própria; step é cada tarefa do job,
em sequência (`run:` = comando, `uses:` = action pronta de terceiros). Analogia: OS → etapas → tarefas. Jobs não
compartilham disco, por isso a imagem passa do `image` para o `deploy-kind` pelo GHCR.

### `needs` e `outputs` entre jobs

`needs` ordena os jobs e pula os seguintes quando um falha (provado no CP6: teste quebrado → `image` e `deploy-kind`
skipped). `outputs` passa um valor pequeno de um job para o outro; aqui, a referência da imagem calculada no `image`.

### Gatilho por branch (`on.push.branches`)

Define quais pushes disparam o workflow. No `cd.yml` é o único lugar com o nome da branch de entrega; um push em
outra branch não dispara nada (ver [CI/CD](../operations/ci-cd.md#pipeline-de-entrega-cd)).

### `GITHUB_TOKEN` e `permissions`

Credencial temporária criada pelo GitHub para cada job, válida só enquanto ele roda. O bloco `permissions` limita o
que ela pode fazer (menor privilégio): só o job `image` tem `packages: write` para publicar no GHCR.

### Action fixada por SHA

`uses: dono/action@<sha do commit>` em vez de `@v4`: a tag pode ser movida para outro código (ataque de *supply
chain*), o commit não. Analogia: pedir a peça pelo número de série, não "a mais nova da marca".

### SCA (Software Composition Analysis) e CVE

Uma **CVE** é o registro público de uma vulnerabilidade conhecida, com número próprio (`CVE-AAAA-NNNN`). A **SCA**
compara as bibliotecas do projeto com o banco de CVEs (NVD) e avisa quais estão afetadas. Analogia: a oficina
conferir no estoque se alguma peça é do lote de um recall. Aqui: OWASP Dependency-Check no
[`security-scan.yml`](../../.github/workflows/security-scan.yml); a correção vai no `pom.xml` (ex.: `tomcat.version`).

### Rate limiting

Limitar quantas requisições o mesmo cliente faz por intervalo (ex.: 100/min); passou disso, `429 Too Many Requests`.
Analogia: a recepção não deixa um único cliente ocupar a fila inteira. O projeto **não tem** rate limiting (risco
residual aceito; ver a revisão de segurança do
[plano da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md)).

### Máscara de secret (`***`)

O GitHub procura o **texto exato** de cada secret no log e o troca por `***`; `::add-mask::` estende isso a valores
criados no job (o token do smoke test). Não é criptografia: imprimir o secret em base64 ou invertido vaza. A barreira
real é quem pode fazer push no repositório.

### *Variable* x *secret* no log

As duas chegam ao job do mesmo jeito e podem ser validadas igual; a diferença é o **log**. O GitHub imprime os
valores de `env:` no topo de cada step: *secrets* aparecem como `***`, *variables* aparecem como são. Regra: *variable*
só para o que poderia estar no README (região, nome do bucket); IP, e-mail e o que identifica uma pessoa vai em
*secret*. Analogia: na ficha da OS que fica no balcão, o que é sigiloso vai tarjado. Lição real: o IP do
`AWS_ALLOWED_CIDR` vazou no log público do fork enquanto era *variable* (ver
[`cd-aws.yml`](../../.github/workflows/cd-aws.yml)).

### Fork

Cópia do repositório na conta pessoal, com histórico e configurações próprios. Usado porque environments e secrets
exigem ser admin; a imagem vai para `ghcr.io/<usuario>/...` sem afetar os colegas.

### `concurrency`

Agrupa execuções (aqui, por workflow + branch); com `cancel-in-progress`, um push novo cancela a execução anterior.
Cada execução tem o próprio cluster, então não se perde nada. No
[`cd-aws.yml`](../../.github/workflows/cd-aws.yml) é o contrário (`cancel-in-progress: false`): cancelar um
`terraform apply` no meio deixa o lock do state preso e recursos pela metade, então o segundo run espera na fila.

### `workflow_dispatch` e `inputs`

Gatilho de disparo manual (botão **Run workflow** ou `gh workflow run`), com campos de formulário (`inputs`). O GitHub
só lista o workflow se o arquivo estiver na **branch padrão** do repositório; por isso o fork usa a branch de entrega
como padrão. Usado no [`cd-aws.yml`](../../.github/workflows/cd-aws.yml), porque o lab fica desligado quase sempre.
Analogia: serviço por agendamento, em vez da revisão automática de todo carro que entra.

### `if: always()` e passo de limpeza

Um step normalmente só roda se os anteriores passaram; com `always()`, roda mesmo depois de uma falha. O `cd-aws.yml`
libera o IP do runner no load balancer só durante o smoke test e o remove num passo `always()`. Analogia: devolver a
chave do carro ao balcão no fim do serviço, tenha dado certo ou não.

### Teste flaky e condição de corrida no NodePort

Teste flaky passa ou falha conforme o tempo, sem mudança de código. No CP6, o pod ficou Ready e o `curl` chegou 13 ms
antes de o kube-proxy programar o endpoint no NodePort (`connection reset`). Correção: repetir com limite
(`curl --retry`), não `sleep` fixo nem "rodar de novo até passar".

### GHCR

GitHub Container Registry (`ghcr.io`): registry de imagens ligado à conta do GitHub, onde a pipeline publica a imagem
com tag pelo SHA. Analogia: estoque central de peças.

### Environment do GitHub

Agrupamento de secrets e variáveis que só os jobs que declaram `environment: <nome>` enxergam (aqui, `kind-ci` e
`aws-academy` no fork). Analogia: um cofre por filial.

### Pipeline de infraestrutura x pipeline de aplicação

A infraestrutura (rede, cluster) muda raramente; a aplicação, várias vezes por dia. Em produção costumam ser
pipelines separadas: a de infra faz `terraform plan` no PR e `apply` só após aprovação; a de app só faz deploy.
Ninguém roda `apply` de produção do próprio PC. Aqui: o [`cd.yml`](../../.github/workflows/cd.yml) cria o kind
efêmero a cada run; o [`cd-aws.yml`](../../.github/workflows/cd-aws.yml) aplica o banco e a app, e o cluster EKS é
criado à mão (credenciais do lab de 4 h e custo por hora).

### OIDC (federação GitHub → AWS)

Em vez de guardar access keys como secret, o job apresenta à AWS um token assinado pelo GitHub ("sou o repo X,
branch Y") e recebe credenciais temporárias de uma role que só aceita esse repo/branch. Analogia: o crachá de
visitante emitido na portaria, que vale só para aquele dia e aquela sala. Não usado no projeto: o Learner Lab só
oferece a LabRole e credenciais de sessão (ver [SCP](#scp-service-control-policy)).

### k6

Ferramenta de teste de carga com cenários em JavaScript e limiares de aprovação; roda pelo container `grafana/k6`.
Usada para provocar a escala do HPA.

### Smoke test _(introdução)_

Teste rápido e raso depois do deploy, que só confirma que o sistema "liga": a aplicação ficou pronta e uma chamada
básica à API responde. O nome vem da eletrônica: ligar o aparelho e ver se sai fumaça. Analogia: dar a partida no
carro depois do conserto, antes do test-drive completo.

### Runner do GitHub Actions _(introdução)_

Máquina virtual temporária, no datacenter do GitHub, que executa um job e é apagada no fim. Ela não enxerga a sua
rede de casa; por isso a pipeline cria o próprio cluster kind dentro dela. Ver
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

## AWS

### AWS Academy Learner Lab _(introdução)_

Conta AWS de estudo com crédito limitado, credenciais temporárias que expiram a cada sessão e permissões restritas.
Analogia: laboratório da faculdade, com equipamento real, mas com horário e regras. Ver
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### IAM Role / LabRole _(introdução)_

Uma role do IAM é um conjunto de permissões ("o que este serviço pode fazer"). Não é caminho de rede: nenhuma
requisição passa por ela. No Learner Lab não se pode criar roles; usa-se a `LabRole` fornecida. Analogia: o crachá
do mecânico.

### Internet Gateway (IGW) _(introdução)_

Porta da VPC para a internet, por onde as requisições entram e as respostas voltam. Analogia: o portão principal
da oficina. Não existe no ambiente local.

### NAT Gateway _(introdução)_

Saída para a internet de quem está em uma subnet privada e precisa **iniciar** uma conexão (ex.: o node do EKS
puxando a imagem do registry). Não recebe conexões de fora e não é o caminho de volta das respostas. Analogia: porta
dos fundos só de saída. Tem custo por hora.

### EKS _(introdução)_

Kubernetes gerenciado pela AWS: ela opera o control plane (o "cérebro" do cluster). Analogia: terceirizar a gerência
do pátio. Planejado na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### RDS _(introdução)_

Banco de dados gerenciado pela AWS (aqui, MySQL), com backup e manutenção feitos por ela. Analogia: arquivo de fichas
mantido por uma empresa especializada. Planejado na
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### Região e zona de disponibilidade (AZ)

Região é um conjunto de datacenters em um lugar do mundo (o Learner Lab usa `us-east-1`, Virgínia); cada AZ é um
prédio separado dentro dela, com energia e rede próprias. Recursos espalhados por 2 AZs sobrevivem à queda de uma.
Analogia: a rede de oficinas numa cidade (região) com filiais em bairros diferentes (AZs).

### Credenciais temporárias (STS)

O STS emite um "crachá de visitante" (access key, secret e **session token**) válido por poucas horas, ao assumir uma
role. O Learner Lab só dá esse tipo, por isso os valores mudam a cada Start Lab e precisam ser atualizados no
environment do GitHub. `aws sts get-caller-identity` mostra qual crachá está em uso (`assumed-role/voclabs/...` no lab).

### CloudShell

Terminal Linux dentro do console da AWS, com o `aws` CLI já logado com as credenciais da sessão: testa permissões sem
copiar chaves para o PC. Não confundir com o terminal da página do Learner Lab (role `runserver`, quase sem
permissões). Analogia: o computador do balcão, já logado no sistema da oficina.

### `--dry-run` (AWS CLI)

Pergunta "eu poderia fazer isso?" sem executar nem cobrar: `DryRunOperation` = permitido, `UnauthorizedOperation` =
bloqueado pela política. Usado na confirmação do Learner Lab (CP8 do
[plano da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md)). Analogia: pedir o
orçamento sem autorizar o serviço.

### VPC

Rede privada na AWS, com uma faixa de endereços própria (`10.0.0.0/16`); nada entra ou sai sem um caminho criado.
Analogia: o terreno murado da oficina. No kind, o papel era da rede do Docker. Ver
[`infra/aws/cluster/main.tf`](../../infra/aws/cluster/main.tf).

### Subnet pública x privada e route table

Subnet é um pedaço da VPC em uma AZ; a route table diz para onde vai o tráfego. Pública: rota para a internet pelo
IGW (load balancer, NAT). Privada: saída só pelo NAT (nodes, RDS). Analogia: recepção que dá para a rua x pátio e
almoxarifado. EKS e RDS exigem subnets em 2 AZs.

### Security Group

Firewall preso a cada recurso: "quem pode entrar, por qual porta". O do RDS libera 3306 só para o security group dos
nodes do EKS, não para um IP. Analogia: o porteiro do almoxarifado que só deixa entrar quem tem crachá de mecânico.
Ver [`infra/aws/database/main.tf`](../../infra/aws/database/main.tf).

### Node group gerenciado

Grupo de EC2 que a AWS cria e mantém como nodes do EKS (aqui, 2 `t3.medium` com a LabRole). As máquinas somem quando
a sessão do lab acaba; o control plane continua. Analogia: a equipe de mecânicos da terceirizada que gerencia o pátio.

### State remoto (backend S3) e `use_lockfile`

O state vai para um bucket S3, para que o PC e a pipeline usem o mesmo e para que ele não se perca (state perdido =
recursos órfãos cobrando). `use_lockfile` grava um `.tflock` durante o `apply` e recusa um segundo `apply`
simultâneo. O bucket é criado pelo AWS CLI ([`infra/aws/bootstrap/README.md`](../../infra/aws/bootstrap/README.md)),
porque a SCP do lab impede o Terraform de gerenciá-lo (ovo e galinha resolvido fora do Terraform). Analogia: o livro de OS
sai da gaveta do mecânico e vai para o arquivo central, com "em uso por..." na capa.

### `terraform_remote_state`

Lê as saídas de outra stack direto do state remoto: a `database` pega VPC, subnets e o security group dos nodes da
`cluster`, sem copiar IDs à mão. Também força a ordem `cluster` → `database`.

### EKS access entry

Define quem pode usar o `kubectl` no cluster. Com `bootstrap_cluster_creator_admin_permissions`, quem criou o
cluster (a role `voclabs` do lab) vira admin; a pipeline, com credenciais da mesma role, herda o acesso.

### SCP (Service Control Policy)

Política da *organização* dona da conta (aqui, a AWS Academy), aplicada acima de qualquer permissão da role. Um
`explicit deny` numa SCP vence qualquer `allow` da LabRole e não tem contorno no nosso código. Foi o que barrou o
`aws_s3_bucket` (leitura do Object Lock negada). Analogia: o crachá do mecânico (role) permite, mas o regulamento do
shopping onde fica a oficina (SCP) proíbe. Ver [`infra/aws/bootstrap/README.md`](../../infra/aws/bootstrap/README.md).

### `tainted` (Terraform)

Recurso marcado no state como "criado, mas não confiável", geralmente porque o `apply` falhou no meio. No próximo
`apply`, o Terraform o destrói e recria. Analogia: peça montada, mas reprovada na inspeção; volta para a bancada.

### Service `LoadBalancer`

Tipo de Service que pede à nuvem um balanceador de verdade nas subnets públicas, com um hostname
`*.elb.amazonaws.com`, que repassa o tráfego aos pods prontos nas subnets privadas. Substitui o `NodePort` do kind.
Analogia: a recepção na rua que encaminha cada cliente a um mecânico livre no pátio fechado. Ver
[`k8s/overlays/aws/kustomization.yaml`](../../k8s/overlays/aws/kustomization.yaml).

### `loadBalancerSourceRanges` e *fail-safe*

É a lista de IPs que o Load Balancer aceita (vira regra de Security Group); `/32` corresponde a exatamente um
endereço. Protege o admin de bootstrap com senha conhecida. O IP fica num arquivo ignorado pelo git, e sem esse arquivo
o deploy **falha** em vez de abrir para o mundo. Isso é *fail-safe*: na dúvida, falha fechado. Analogia: sem a lista
de clientes autorizados, a recepção não abre a porta.

### Ordem de destroy e recurso órfão

O Terraform só apaga o que ele mesmo criou e anotou no state. O Load Balancer da API foi criado pelo **Kubernetes**
(Service `LoadBalancer`), não pelo Terraform, e vive dentro da VPC. Se o cluster for destruído primeiro, ninguém mais
apaga esse LB: ele vira um **recurso órfão**, continua cobrando e trava a remoção da VPC e das subnets. Por isso a
ordem é inversa à da criação: app (`kubectl delete`, que faz o Kubernetes apagar o LB) → `database` → `cluster`.
Analogia: antes de devolver o galpão alugado, desmonte a recepção que você montou por conta própria; o dono só leva o
que está no contrato. Ver [`infra/aws/bootstrap/README.md`](../../infra/aws/bootstrap/README.md).

### Alias de comando (`curl` no PowerShell)

Alias é um apelido: um nome curto que o shell troca por outro comando antes de executar. No Windows PowerShell 5.1,
`curl` é alias de `Invoke-WebRequest`, um comando diferente que analisa a página como um navegador (por isso pede
confirmação e devolve uma tabela em vez do JSON). `curl.exe` pula o alias e chama o curl de verdade. O mesmo vale para
`PS`, alias de `Get-Process`: colar a saída do terminal (linhas `PS E:\...>`) de volta no PowerShell faz ele tentar
executar cada linha. Analogia: pedir "a chave 10" e receber uma chave de outro jogo que tem o mesmo apelido na
oficina. Ver [`local-kubernetes.md`](../setup/local-kubernetes.md#pré-requisitos).

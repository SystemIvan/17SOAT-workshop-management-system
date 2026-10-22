# Especificação Funcional: Infraestrutura Kubernetes, IaC e CI/CD

| Campo | Valor |
|---|---|
| Feature | `infra-kubernetes-cicd` |
| Status | Approved |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-22 |
| Aprovado por | Leandro Nascimento |
| Aprovado em | 2026-10-22 |
| Referências | Enunciado do Tech Challenge Fase 2 (manifestos em `/k8s`, Terraform em `/infra`, pipeline de CI/CD, README e vídeo); `docs/features/platform/container-readiness/` (probes na porta `8081`, imagem não-root, `JAVA_OPTS`, emendas D1, D2, E1 e E2); `docs/features/infra-kubernetes-cicd/tasks.md` da branch `feature/infra-kubernetes-cicd` (Matheus Apostulo, 2026-10-17: contexto da conta AWS Academy); `.github/workflows/ci.yml`; `docker-compose.yml`; `.env.example`; `DOCKER.md`; `V20260824120001__seed_bootstrap_admin_account.sql` |

## Problema e resultado esperado

A Fase 2 exige que a aplicação rode num cluster Kubernetes com escalabilidade automática, que o cluster e o banco
sejam provisionados com Terraform e que uma pipeline de CI/CD construa, teste e implante tudo. Hoje o repositório tem:

- `Dockerfile` e `docker-compose.yml` para execução local, revisados na feature `container-readiness` (PRs #54 e #55);
- a pipeline de CI (`ci.yml`), que compila e roda os testes, e um workflow manual de scan de segurança;
- nenhum manifesto Kubernetes, nenhum script Terraform e nenhuma etapa de entrega (imagem publicada, deploy do banco,
  deploy no cluster).

O ambiente de nuvem disponível é o AWS Academy (Learner Lab), que tem restrições que mudam o desenho: as credenciais são
temporárias e expiram com a sessão; não é possível criar usuários nem roles IAM (os serviços usam a `LabRole`
pré-criada); cada conta tem orçamento de US$ 50; e recursos como cluster, load balancer e banco continuam cobrando entre
sessões.

**Resultado esperado:**

- os mesmos manifestos, em `/k8s`, sobem a aplicação num cluster local (kind) e num cluster na AWS, com o HPA
  escalando réplicas por CPU e memória;
- scripts Terraform, em `/infra`, provisionam o cluster e o banco na AWS Academy e destroem tudo depois, com
  documentação de recursos, custos e comandos;
- a pipeline (GitHub Actions) faz build, testes, imagem Docker, deploy do banco, aplicação dos manifestos e verificação
  do rollout. Durante os testes ela dispara na `dev`; a troca para a `main` na entrega fica documentada;
- o README passa a ter o desenho da arquitetura e as instruções de execução local, deploy em Kubernetes e provisionamento
  com Terraform.

Nenhuma regra de negócio, contrato de API, migration ou módulo de domínio muda.

## Atores e cenários

- **Desenvolvedor**: valida a aplicação num cluster local antes de gastar na AWS.
- **Operador da infraestrutura** (integrante com sessão ativa no Learner Lab): provisiona e destrói a infraestrutura com
  Terraform e mantém as credenciais da pipeline.
- **Pipeline de CI/CD (GitHub Actions)**: constrói, testa, publica a imagem e implanta.
- **Cluster Kubernetes** (kubelet, HPA, Service): executa a aplicação, verifica a saúde e escala as réplicas.
- **Consumidor da API** (Postman, avaliadores da FIAP): acessa a API pela URL pública do ambiente na AWS.
- **Chamador externo não autorizado**: alcança a URL pública do ambiente na AWS.

Cenários ponta a ponta:

1. **Cluster local:** o desenvolvedor cria um cluster kind e aplica a variação local dos manifestos. MySQL, simulador de
   fornecedor e aplicação sobem no cluster; a aplicação fica pronta (readiness) e atende a collection do Postman; o HPA
   mostra as métricas das réplicas.
2. **Provisionamento na AWS:** com a sessão do Learner Lab ativa, o operador executa o Terraform, que cria o cluster
   Kubernetes, o banco MySQL gerenciado, o registro de imagens e a rede necessária. A documentação lista cada recurso, o
   custo estimado e os comandos para aplicar e destruir.
3. **Integração contínua:** a cada pull request e push em `dev`/`main`, a pipeline compila e roda os testes, como hoje.
4. **Entrega contínua na `dev`:** um push em `dev`, ou um disparo manual, gera a imagem Docker identificada pelo SHA do
   commit, publica a imagem no registro, garante o banco, aplica os manifestos no cluster, espera o rollout e executa um
   smoke test na URL pública. Se o rollout falhar, a pipeline falha e o cluster volta à versão anterior.
5. **Troca para a `main` na entrega:** a documentação descreve o passo para o CD passar a disparar na `main` e a
   sequência de release `dev` → `main`.
6. **Escalabilidade automática:** um script de carga gera muitas requisições e ordens de serviço contra o ambiente; o
   HPA aumenta as réplicas quando o consumo passa do alvo e as reduz quando a carga termina.
7. **Encerramento:** depois dos testes ou da gravação, o operador destrói tudo com o Terraform e confere que não sobrou
   recurso cobrando (load balancer, NAT, volumes, IPs).
8. **Credencial vencida:** a sessão do Learner Lab expira; a próxima execução do CD falha logo no primeiro passo, com
   mensagem clara e sem deploy pela metade. O operador atualiza as credenciais e executa de novo.
9. **Acesso indevido:** um chamador externo tenta usar a conta `admin` com a senha padrão na URL pública; o ambiente não
   permite que isso dê acesso administrativo (R7).

## Regras

### R1 — Mesmos manifestos para local e AWS

- `/k8s` tem uma base comum e uma variação por ambiente (local e AWS). As diferenças ficam só nas variações: banco no
  cluster (local) ou gerenciado (AWS), forma de exposição do Service, origem da imagem e número de réplicas.
- A base tem, no mínimo: Deployment e Service da aplicação, ConfigMap, Secret (só o modelo, sem valores), HPA e o
  simulador de fornecedor (WireMock com os mappings de `docker/wiremock/`).
- Os probes seguem a `container-readiness`: liveness e readiness na porta de gestão `8081`, que nunca é exposta por um
  Service, e um startupProbe para a inicialização lenta (Flyway).
- Todos os containers têm requests e limits de CPU e memória; `JAVA_OPTS` fica coerente com o limite de memória.
- A aplicação roda como não-root (UID 10001), sem privilégios e com as restrições de segurança do pod que a imagem
  permite (por exemplo, raiz somente leitura com `/tmp` gravável, conforme a evidência da `container-readiness`).

### R2 — Configuração e segredos

- Toda configuração vem de ConfigMap e Secret, com as mesmas variáveis do compose: datasource, JWT, HMAC do gateway,
  fornecedor, notificação por e-mail e `JAVA_OPTS`.
- Nenhum segredo é versionado. Os Secrets do cluster são criados a partir de GitHub Secrets (CD) ou de variáveis locais
  (kind). Arquivos `*.tfvars` com valores, estado do Terraform e kubeconfig ficam fora do repositório.
- Os valores padrão de desenvolvimento do `application.properties` (JWT, HMAC) são sempre sobrescritos no cluster.
- Os logs da pipeline não exibem segredos.

### R3 — Banco de dados

- Na AWS, o banco é um MySQL gerenciado provisionado pelo Terraform, na mesma versão usada no compose (8.4), acessível
  só a partir do cluster.
- O schema é criado e evoluído **somente** pelo Flyway na subida da aplicação (`ddl-auto=validate` mantido). O "deploy
  do banco" da pipeline é o banco provisionado mais as migrations aplicadas e verificadas antes de o pod receber tráfego
  (readiness).
- O ambiente na AWS é de **demonstração** e roda **sem seeds automáticos**: os dados de exemplo são criados pela
  collection do Postman durante a demonstração, seguindo a regra de seeds do `AGENTS.md`. A conta `admin` inicial
  continua vindo da migration obrigatória.
- No cluster local, o MySQL roda no próprio cluster com volume persistente; os seeds seguem a mesma regra do compose
  (perfil `dev` mais a flag `app.seed.enabled`).

### R4 — Infraestrutura como código (Terraform)

- `/infra` provisiona, no mínimo, o cluster Kubernetes e seus nós, o banco MySQL gerenciado, o registro de imagens e a
  rede e os grupos de segurança necessários.
- Respeita as restrições do AWS Academy: nenhum usuário ou role IAM é criado; os serviços usam a `LabRole` e o perfil de
  instância já existentes; a região é única e definida na technical-spec.
- O estado do Terraform é remoto e com trava, para a pipeline e o operador compartilharem o mesmo estado, e é criado por
  um passo de bootstrap documentado.
- `terraform fmt`, `validate` e `plan` ficam limpos. `apply` e `destroy` são documentados, com a lista de recursos
  criados e o custo estimado.
- **Trava de conta:** todo comando que altera a AWS (Terraform, scripts, pipeline) usa credenciais explícitas do Learner
  Lab e confere a identidade antes de agir. Nunca usa o perfil `default` da máquina, que pode apontar para outra conta
  AWS.

### R5 — Pipeline de CI/CD

- **CI (existente):** o quality gate continua obrigatório em pull requests e pushes, sem regressão.
- **Imagem:** construída a partir do `Dockerfile` do repositório, com tag igual ao SHA do commit, e publicada no
  registro do ambiente.
- **Deploy:** a pipeline autentica no cluster, aplica a variação AWS dos manifestos com a imagem do commit, aguarda o
  rollout (que depende da readiness) e executa um smoke test de login. Em falha, reverte para a versão anterior e marca a
  execução como falha.
- **Gatilho:** push em `dev` e disparo manual durante os testes. A troca para `main` na entrega é documentada (R8).
- **Credenciais:** como o Learner Lab não permite OIDC com role própria, a pipeline usa as credenciais temporárias da
  sessão guardadas em GitHub Secrets, atualizadas pelo operador a cada sessão. Com credenciais vencidas, a pipeline
  falha no primeiro passo com mensagem clara (cenário 8).
- O token do GitHub tem permissões mínimas, as actions ficam fixadas por SHA (padrão do repositório) e nenhum segredo
  aparece em log.

### R6 — Escalabilidade automática (HPA)

- O HPA escala a aplicação entre um mínimo e um máximo de réplicas pelo consumo de CPU e de memória, com o
  metrics-server disponível nos dois ambientes. Os valores ficam na technical-spec.
- Um script de carga versionado e reproduzível gera volume suficiente para demonstrar o aumento e a redução de réplicas.
  O procedimento e um resultado de referência ficam documentados.
- Escalar não pode derrubar a aplicação: réplicas novas só recebem tráfego depois da readiness, e réplicas removidas
  encerram de forma graciosa.

### R7 — Exposição e acesso

- Na AWS, a API fica acessível por uma URL pública (HTTP) do load balancer, só na porta de negócio; a porta de gestão
  `8081` não é exposta.
- A conta `admin` inicial tem uma senha padrão documentada no README. No ambiente público, ela não pode dar acesso
  administrativo a qualquer pessoa: ou o acesso ao load balancer fica restrito (por exemplo, aos IPs do time), ou a senha
  é trocada no deploy a partir de um segredo. A escolha fica na technical-spec, e o risco residual entra no checkpoint de
  segurança.
- No cluster local, a API fica acessível na máquina do desenvolvedor sem conflitar com as portas do compose.
- A collection do Postman funciona contra os dois ambientes trocando só variáveis de ambiente (URL base e, se a senha
  for trocada, a credencial do `admin`).

### R8 — Documentação

- `README.md`: descrição da solução e dos objetivos da Fase 2; desenho da arquitetura (componentes da aplicação,
  infraestrutura provisionada e fluxo de deploy); instruções de execução local (compose e kind), deploy em Kubernetes e
  provisionamento com Terraform; link da collection; lugar para o link do vídeo.
- `infra/README.md`: recursos criados, custos, variáveis, outputs, `apply`/`destroy` e como atualizar as credenciais da
  sessão do Learner Lab.
- Procedimento para trocar o gatilho do CD de `dev` para `main` (R5).
- ADR-008 com a estratégia de infraestrutura: cluster, banco, registro de imagens, credenciais e custos.

## Fora de escopo

- Regras de negócio, APIs, migrations, módulos de domínio, seeds e a collection do Postman (além das variáveis de
  ambiente).
- Envio real de e-mail na AWS: no cluster, o canal de notificação é o `log` e o Mailpit não é implantado; a evolução
  para SMTP real fica documentada.
- HTTPS/TLS no load balancer, domínio próprio, DNS, Ingress e ALB.
- Alta disponibilidade do banco (multi-AZ), backups e recuperação de desastre.
- Observabilidade (Prometheus, Grafana, logs centralizados, tracing).
- Scan de vulnerabilidades da imagem (Trivy), registrado como sugestão.
- GitOps (Argo CD, Flux) e ambientes adicionais (staging e produção separados).
- Gravação do vídeo e montagem do PDF de entrega: esta feature deixa o ambiente e a documentação prontos para eles.
- O release `dev` → `main` em si, que é decisão do time; aqui só se documenta o procedimento.

## Decisões para a technical-spec

1. Tipo de cluster na AWS: EKS (gerenciado; cerca de US$ 0,10/h só pelo control plane; exige a `LabRole`) ou EC2 com
   k3s (mais barato e já com metrics-server, mas com acesso da pipeline ao cluster mais trabalhoso). A decisão vem depois
   de testar as permissões no Learner Lab.
2. Registro de imagens: ECR (dentro da AWS, mesmas credenciais) ou GHCR.
3. Backend do estado do Terraform (S3 com trava nativa) e o passo de bootstrap.
4. Como a pipeline acessa o cluster e como os Secrets são criados a partir dos GitHub Secrets.
5. Mitigação da conta `admin` no ambiente público (R7).
6. Valores do HPA (mínimo, máximo e alvos de CPU e memória), requests, limits e `JAVA_OPTS`.
7. `timeoutSeconds` dos probes e `connectionTimeout` do Hikari: com o banco fora, a readiness leva ~31 s para responder
   (evidência da `container-readiness`, checkpoint 4).
8. Ferramenta de carga (k6 ou hey) e de onde ela roda.
9. Região, tamanhos de instância, custo estimado e política de destruir após o uso.

## Critérios de aceite

Kubernetes:

- [ ] `/k8s` tem a base e as variações local e AWS; os manifestos dos dois ambientes são gerados sem erro e passam em
      `kubectl apply --dry-run=server` no cluster local.
- [ ] No cluster local (kind), aplicação, MySQL e simulador sobem com os mesmos manifestos; a aplicação fica pronta e o
      login da collection funciona.
- [ ] Os probes usam a porta `8081`, que não aparece em nenhum Service.
- [ ] Todos os containers têm requests e limits; a aplicação roda com UID 10001 e sem privilégios.
- [ ] Nenhum Secret com valor real está versionado.
- [ ] Sob o script de carga, o HPA aumenta as réplicas e depois volta ao mínimo, sem falha de readiness nas réplicas
      novas, no cluster local e na AWS.

Terraform:

- [ ] `/infra` provisiona cluster, banco MySQL 8.4 gerenciado, registro de imagens e rede na AWS Academy sem criar
      usuários nem roles IAM.
- [ ] `terraform fmt -check`, `validate` e `plan` ficam limpos; `apply` cria e `destroy` remove todos os recursos,
      conferido pela CLI.
- [ ] Toda execução confere a identidade do Learner Lab antes de alterar recursos; com credenciais de outra conta, nada é
      aplicado.
- [ ] A documentação lista recursos, custo estimado, variáveis, outputs e os comandos de `apply` e `destroy`.

CI/CD:

- [ ] O quality gate atual continua rodando em pull requests e pushes, sem regressão.
- [ ] Um push em `dev`, ou um disparo manual, publica a imagem com a tag do SHA, aplica os manifestos na AWS, aguarda o
      rollout e passa no smoke test de login.
- [ ] Um rollout com falha faz a pipeline falhar e o cluster voltar à versão anterior.
- [ ] Com credenciais vencidas, a pipeline falha no primeiro passo com mensagem clara.
- [ ] Nenhum segredo aparece nos logs; o token tem permissões mínimas; as actions ficam fixadas por SHA.
- [ ] O procedimento para trocar o gatilho para `main` está documentado.

Banco e acesso:

- [ ] O banco na AWS é criado pelo Terraform e só o cluster o alcança; a aplicação aplica as migrations na subida e só
      fica pronta depois disso.
- [ ] O ambiente na AWS sobe sem seeds; os dados da demonstração vêm da collection.
- [ ] Um chamador fora do time não consegue acesso administrativo com a senha padrão do `admin` na URL pública.

Documentação:

- [ ] README com descrição da fase, desenho da arquitetura, instruções (compose, kind, Kubernetes na AWS, Terraform),
      link da collection e lugar para o link do vídeo.
- [ ] `infra/README.md` e ADR-008 registrados.
- [ ] Checkpoint de segurança registrado no `implementation-plan.md`.

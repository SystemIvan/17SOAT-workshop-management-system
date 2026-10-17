# Infraestrutura, Kubernetes e CI/CD

## Objetivo desta etapa

Usar o PDF do Tech Challenge Fase 2 como fonte dos requisitos e inspecionar o projeto existente para organizar as tarefas da entrega. A Clean Architecture já está bem encaminhada; o foco agora é IaC com Terraform, Kubernetes, GitHub Actions e AWS.

Neste primeiro momento, queremos apenas uma visão do trabalho: revisar e ajustar esta lista com base no PDF e no repositório. Não implementar a infraestrutura nem criar recursos AWS ainda. Depois criaremos as specs e seguiremos com SDD. Trabalhar por etapas, explicando as decisões de forma objetiva.

Branch sugerida: `feat/infra-kubernetes-cicd`.
Arquivo no projeto: `docs/feature/infra-kubernetes-cicd/tasks.md`.

## Contexto do ambiente e orçamento

- Desenvolvimento no Windows com WSL2, Ubuntu 26.04 LTS, zsh, x86_64; home `/home/matheus`.
- WSL com cerca de 5,7 GiB de RAM e 12 CPUs disponíveis.
- Git, Docker e Docker Compose funcionando. Já existem containers de outros trabalhos: preservá-los.
- kubectl v1.37.1 e kind v0.33.0 instalados. A criação/saúde do cluster local ainda não foi confirmada.
- AWS CLI v2 instalada e autenticada. Terraform instalado segundo confirmação do usuário; conferir versão e compatibilidade com o projeto.
- Go e Node também estão instalados; identificar a stack real pelo repositório.
- Conta AWS Academy com orçamento informado de US$ 50; consultar saldo atual e região antes de planejar recursos.
- A role disponível é `voclabs`. A tentativa de criar usuário IAM foi negada por falta de `iam:CreateUser`; não há chave permanente criada.
- Validar permissões dos serviços necessários. Não presumir que é possível criar roles ou configurar OIDC.
- Preferir validar localmente com kind antes de gastar na AWS. EC2 + K3s é uma opção a avaliar; EKS não é decisão fechada. O PDF permite cluster local ou cloud.
- Estimar custos totais e planejar limpeza dos recursos. Não presumir que encerrar a sessão Academy elimina cobranças.

## Credenciais AWS no WSL

Iniciar o Learner Lab e obter as credenciais em **AWS Details → AWS CLI** (os nomes podem variar). Guardar localmente em `~/.aws/credentials`, fora do Git:

```ini
[academy]
aws_access_key_id = <access key do laboratório>
aws_secret_access_key = <secret key do laboratório>
aws_session_token = <session token do laboratório>
```

Configurar e validar:

```bash
chmod 600 ~/.aws/credentials
aws configure set region <regiao-do-laboratorio> --profile academy
aws configure set output json --profile academy
aws configure set cli_pager "" --profile academy
export AWS_PROFILE=academy
aws sts get-caller-identity
```

A região ainda precisa ser confirmada; não assumir `us-east-1`. O perfil fica salvo, mas as credenciais expiram: atualizar os três valores quando necessário. O `export` vale para a sessão atual. A consulta STS já funcionou com `voclabs`, mas não comprova permissões de provisionamento. Nunca incluir credenciais em documentos, commits ou logs.

Para GitHub Actions, a alternativa discutida é usar Secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN`, atualizados manualmente quando expirarem. Nenhum Secret/workflow foi configurado nesta conversa. A autenticação e a conectividade do deploy ainda devem ser definidas: o runner do GitHub não acessa automaticamente o kind no WSL.

## Visão inicial das tarefas

Lista preliminar: validar contra o PDF e o código antes de detalhar specs. Itens abaixo não significam que o projeto ainda não possua essas implementações.

### 1. Diagnóstico e planejamento — fazer agora

- [ ] Ler o PDF, README, AGENTS.md e estrutura atual do repositório.
- [ ] Identificar stack, banco, migrations, testes, Docker/Compose e automações existentes.
- [ ] Mapear o que já atende ao challenge e as lacunas, incluindo APIs e testes exigidos.
- [ ] Confirmar prazo, região, saldo e restrições do Academy.
- [ ] Comparar opções de infraestrutura e banco, custos e acesso da pipeline ao cluster.
- [ ] Revisar esta lista e propor a ordem de execução. Manter implementação e specs para a próxima etapa.

### 2. Base local e Kubernetes — execução futura

- [ ] Validar aplicação, banco e testes localmente; ajustar Dockerfile/Compose se necessário.
- [ ] Validar cluster kind e compatibilidade das ferramentas.
- [ ] Preparar `/k8s`: Deployments, Services, ConfigMaps e gestão de Secrets.
- [ ] Configurar probes, requests/limits e persistência conforme a aplicação.
- [ ] Configurar HPA e métricas; demonstrar escalabilidade com carga.

### 3. Infraestrutura como código — execução futura

- [ ] Definir a arquitetura mínima, estratégia de banco e orçamento.
- [ ] Preparar `/infra` para cluster/infraestrutura e banco, conforme os requisitos.
- [ ] Definir state, locking, variáveis, outputs e tratamento de dados sensíveis.
- [ ] Validar provisionamento, acesso e remoção controlada dos recursos do projeto.

### 4. GitHub Actions — execução futura

- [ ] Criar CI com build e testes automatizados.
- [ ] Fazer build e publicar imagem rastreável por commit no registro escolhido (ECR se adotado).
- [ ] Configurar autenticação AWS e acesso seguro ao Kubernetes.
- [ ] Automatizar deploy do banco, migrations quando aplicáveis e aplicação dos manifestos.
- [ ] Verificar rollout, funcionamento e procedimento de rollback.

### 5. Entrega — execução futura

- [ ] Validar todos os requisitos do PDF, incluindo APIs, testes e HPA funcionando.
- [ ] Atualizar README, desenho da arquitetura, instruções e collection das APIs.
- [ ] Preparar demonstração de pipeline, deploy, APIs e escalabilidade em até 15 minutos.
- [ ] Preparar os links, compartilhamento e PDF final exigidos pelo challenge.
- [ ] Registrar como remover recursos sem perder dados necessários à entrega.

## Próxima fase: specs e SDD

Após revisar o escopo acima, dividir o trabalho em specs pequenas, com decisões, critérios de aceite e validação. Não gerar essas specs nesta primeira etapa.

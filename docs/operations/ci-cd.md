# CI/CD

## Integração contínua

O workflow `CI` executa o check único `Quality gate` em Pull Requests e pushes para `dev` e `main`, usando Temurin 21 e
o Maven Wrapper. No resumo de cada execução, a seção **Artifacts** disponibiliza por 30 dias:

- `test-reports-*`, com os relatórios Surefire/Failsafe produzidos;
- `jacoco-report-*`, com os relatórios HTML, XML e CSV do JaCoCo.

Um novo commit cancela a execução obsoleta da mesma proposta. A proteção das branches deve exigir o contexto exato
`Quality gate`; renomear o job requer coordenar antes a regra correspondente no GitHub.

### Scan de segurança

O workflow `Security scan` é separado do `CI` de propósito: ele não participa do check obrigatório `Quality gate`, para
que os scans não deixem toda Pull Request mais lenta nem a bloqueiem por uma CVE publicada em biblioteca de terceiros.
Ele é disparado manualmente (**Actions → Security scan → Run workflow**) e tem dois jobs:

- `Dependency-Check (SCA)`, que analisa as dependências do `pom.xml`. Requer o secret `NVD_API_KEY`;
- `OWASP ZAP (DAST)`, que sobe a aplicação via Docker Compose e a escaneia a partir do contrato OpenAPI, autenticado
  como `ADMIN`.

Os artifacts ficam disponíveis por 30 dias:

- `dependency-check-report-*`, com o relatório HTML/JSON do OWASP Dependency-Check;
- `zap-report-*`, com o relatório HTML/JSON do OWASP ZAP.

**O output bruto dessas ferramentas nunca é commitado**. Isso evita registrar no histórico do Git um mapa detalhado de
vulnerabilidades por versão, inclusive depois de uma correção. A análise narrativa fica em
`docs/security/vulnerability-report.md` e referencia qual execução de CI gerou cada evidência.

Para rodar localmente:

```bash
export NVD_API_KEY=<chave da NVD>
make sca            # analise de dependencias

make docker-up      # sobe a aplicacao
make dast           # escaneia a API em execucao; use DAST_BASE_URL=... para outro host
```

Se a porta 3306 já estiver ocupada, use `DB_PORT=3307 make docker-up`. No Git Bash do Windows o alvo `dast` já define
`MSYS_NO_PATHCONV=1`, sem o qual o volume do ZAP não é montado.

## Pipeline de entrega (CD)

O workflow `CD` (`.github/workflows/cd.yml`) entrega a aplicação num cluster kind criado dentro do próprio runner e
descartado no fim. Ele roda em pushes na branch de entrega, num fork onde o desenvolvedor é dono (environments e
secrets exigem admin). O `ci.yml` não muda. A entrega na AWS é um workflow separado e manual
([`cd-aws.yml`](#entrega-na-aws-cd-awsyml)).

| Job | Depende de | O que faz |
|---|---|---|
| `Build and test` | — | `./mvnw clean verify` (testes e gate JaCoCo) |
| `Build and push image` | `Build and test` | `docker build` e push de `ghcr.io/<dono em minúsculas>/workshop-management-system:<sha do commit>` com o `GITHUB_TOKEN` |
| `Deploy to kind and smoke test` | `Build and push image` | Terraform `infra/local/cluster` e `infra/local/database`, `secrets.env` a partir do environment, `kubectl apply -k k8s/overlays/local` com a imagem do SHA e smoke test |

O smoke test espera o rollout, exige readiness `UP`, faz login com o admin de bootstrap e chama
`GET /api/service-orders` com o token (deve dar `200`). Se algo falhar, o step `Diagnostics` mostra pods, `describe` e
as últimas 200 linhas do log do app. Uma falha em `Build and test` faz os outros dois jobs serem pulados.

**Nome da branch de entrega.** Ele aparece **só** em `on.push.branches` no topo do `cd.yml` (hoje
`feat/platform-kubernetes-infrastructure-delivery`; até 2026-10-27, `feat/terraform-san`, nome que aparece nos runs
antigos e no vídeo). Para trocar, edite essa linha. Registry, nome da imagem e dono vêm de `env:` e de
`github.repository_owner`, então o mesmo arquivo funciona no fork e no repositório do grupo.

**Configuração do fork (uma vez):**

1. Fork do repositório na conta pessoal e, na aba **Actions**, habilitar os workflows.
2. **Settings → Environments → New environment** `kind-ci`, com os secrets `APP_SECURITY_JWT_SECRET` e
   `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`. Use valores aleatórios novos de pelo menos 32 bytes (o JWT recusa
   chaves menores), por exemplo no PowerShell:
   `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`.
3. `git remote add fork https://github.com/<usuario>/17SOAT-workshop-management-system.git` e
   `git push -u fork <branch de entrega>`.
4. Conferir se o pacote `workshop-management-system` em **Packages** está público (D9). Publicado pelo
   `GITHUB_TOKEN`, ele herda a visibilidade do repositório. O kind do runner puxa a imagem sem login.

Uma execução completa leva ~6 min (meta: até 40 min).

## Entrega na AWS (`cd-aws.yml`)

O workflow `CD AWS` (`.github/workflows/cd-aws.yml`) implanta no EKS do AWS Academy a imagem que o `cd.yml` já
publicou. Ele é **só manual** (`workflow_dispatch`): o lab fica desligado quase sempre, e um gatilho por push falharia
em toda execução sem sessão ativa. Antes de disparar, o cluster precisa existir na sessão atual do lab
(`infra/aws/cluster`, etapa 2 do [guia da AWS](../setup/aws-academy.md#2-criar-rede-e-cluster)). O cluster fica fora da
pipeline de propósito: ele leva 15 a 20 min para subir e cobra por hora, então quem decide quando ligá-lo é a pessoa,
não um push.

**Disparo:** **Actions → CD AWS → Run workflow**. O campo opcional `image_sha` aceita o SHA completo de um commit cuja
imagem já está no GHCR; vazio, usa o commit do próprio run. O GitHub só lista um workflow `workflow_dispatch` se o
arquivo estiver na **branch padrão** do repositório; por isso a branch padrão do fork é a branch de entrega.

| Step | O que faz |
|---|---|
| `Validate inputs` | Exige `image_sha` com 40 caracteres hexadecimais e `AWS_ALLOWED_CIDR` com um único IPv4 `/32` (*fail-safe*: nunca `0.0.0.0/0`) |
| `Check AWS credentials` | `aws sts get-caller-identity`, com a saída descartada (no lab, o ARN traz o número da conta e o e-mail do aluno): com a sessão expirada, falha aqui, antes de tocar em qualquer recurso |
| `Check preconditions` | O cluster `workshop` existe e a imagem `:sha` existe no GHCR |
| `Deploy database` | `terraform apply` de `infra/aws/database`: RDS, namespace e ConfigMap/Secret `workshop-db` (deploy do banco) |
| `Configure kubectl` | `aws eks update-kubeconfig` |
| `Write application secrets` | Gera `k8s/overlays/aws/secrets.env` a partir dos secrets do environment (`umask 077`) |
| `Deploy application` | Libera também o IP do runner no load balancer, cria o ConfigMap do simulador, fixa a imagem `:sha` e aplica o overlay com `--server-side` (aplicação dos manifestos) |
| `Smoke test` | Rollout, hostname do load balancer, readiness `UP`, login e `GET /api/service-orders` = `200`, tudo pela internet |
| `Diagnostics` | Só em falha, e só se o kubectl foi configurado: pods, `describe` e log do app |
| `Revoke runner access` | Roda mesmo após falha (`always()`): deixa só `AWS_ALLOWED_CIDR` no load balancer e confere sem imprimir o IP |

`concurrency` com `cancel-in-progress: false`: um segundo disparo espera o primeiro terminar, porque cancelar um
`terraform apply` no meio deixa o lock do state preso e recursos pela metade.

**Environment `aws-academy` no fork (Settings → Environments):**

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Credenciais da sessão atual do lab (**AWS Details**); atualizar a cada Start Lab |
| Secret | `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` | Os mesmos do `k8s/overlays/aws/secrets.env` local, para o Postman continuar assinando o webhook com o mesmo segredo |
| Variable | `AWS_REGION` | `us-east-1` |
| Variable | `TF_STATE_BUCKET` | Nome do bucket do state ([bootstrap](../../infra/aws/bootstrap/README.md)) |
| Secret | `AWS_ALLOWED_CIDR` | Seu IP público + `/32`. É *secret*, e não *variable*: o GitHub imprime os valores de `env:` no topo de cada step e só mascara os *secrets*. O workflow valida o formato do mesmo jeito |

Regra para novos valores: *variable* só para o que poderia estar no README (região, nome do bucket); IP, e-mail e
qualquer dado que identifique uma pessoa vão em *secret*. O fork é público, e os logs de um run podem ser lidos por
qualquer pessoa logada no GitHub.

O IP do runner fica liberado só durante o smoke test: o runner é uma máquina do GitHub, fora do seu `/32`, e sem essa
liberação o smoke test pelo load balancer daria timeout.

Execuções registradas (2026-10-24, no fork):

| Run | Resultado | O que prova |
|---|---|---|
| `40144883328`, `40145730363` | Falha em `Validate inputs` | O *fail-safe* do `AWS_ALLOWED_CIDR` (valor mal formatado) |
| `40145796420` | Falha em `Check AWS credentials` (`NoCredentials`) | Credencial inválida falha logo no início, sem efeito parcial |
| `40149256673` | Verde em 7m11s | Deploy completo: banco em 5m19s, aplicação em 8 s, smoke test pelo load balancer em 85 s |

O destroy não faz parte do workflow: ele é sempre manual e conferido ([guia da AWS, etapa 7](../setup/aws-academy.md#7-destruir)).

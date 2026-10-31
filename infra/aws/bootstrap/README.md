# Bucket do state remoto (AWS Academy)

Este bucket guarda o state das stacks `infra/aws/cluster` e `infra/aws/database` (backend S3 com `use_lockfile`).

## Por que não é Terraform

A SCP (Service Control Policy) da organização do Learner Lab nega `s3:GetBucketObjectLockConfiguration`. O recurso
`aws_s3_bucket` do provider `aws` faz essa leitura em todo `apply`, `plan` e refresh, então a stack falha logo depois
de criar o bucket e o deixa `tainted`. Esse erro não tem contorno no código. O backend S3 não faz essa leitura (ele só
lê e grava objetos), por isso o state remoto continua funcionando.

Desvio registrado no CP8 do `implementation-plan.md` da feature `kubernetes-infrastructure-delivery`.

## Criar (uma vez por conta do lab)

Use o PowerShell, com as credenciais da sessão atual em `~/.aws/credentials`. O sufixo aleatório é obrigatório,
porque o nome de um bucket é único no mundo inteiro.

```powershell
$bucket = "workshop-tfstate-" + -join ((48..57) + (97..102) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
aws s3api create-bucket --bucket $bucket --region us-east-1
aws s3api put-bucket-versioning --bucket $bucket --versioning-configuration Status=Enabled
$bucket
```

Desde 2023, todo bucket novo já nasce com criptografia `AES256` e com o bloqueio de acesso público ativo. Confira os
três itens:

```powershell
aws s3api get-bucket-versioning --bucket $bucket      # "Status": "Enabled"
aws s3api get-bucket-encryption --bucket $bucket      # "SSEAlgorithm": "AES256"
aws s3api get-public-access-block --bucket $bucket    # quatro valores true
```

O nome impresso entra no `terraform init -backend-config="bucket=<nome>"` das outras stacks e na variável
`state_bucket` da `database`.

Bucket em uso na conta atual do lab: `workshop-tfstate-dc4ef3e2` (criado em 2026-10-24).

## Remover (só no fim de tudo, depois do destroy de `database` e `cluster`)

Com o versionamento ativo, cada `apply` deixou uma versão antiga do state. Para apagar, use o console: S3 → bucket →
**Empty** (apaga todas as versões) → **Delete**. O `aws s3 rb --force` não remove as versões e falha.

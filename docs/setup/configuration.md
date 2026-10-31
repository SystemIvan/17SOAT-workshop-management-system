# Configuração

Referência das variáveis de ambiente lidas pela aplicação. A mesma imagem roda em todos os ambientes; só muda de onde
cada valor vem. A fonte da verdade são `src/main/resources/application.properties` e `application-dev.properties`: se
esta tabela divergir deles, vale o código.

## Onde os valores são definidos

| Ambiente | Valores não sensíveis | Segredos |
| --- | --- | --- |
| Docker Compose | `docker-compose.yml`, sobrescrito pelo `.env` (modelo em `.env.example`) | `.env` |
| Kubernetes (kind) | ConfigMap `workshop-app-config` (`k8s/base/app-config.env` + overlay) e ConfigMap `workshop-db` (Terraform) | Secret `workshop-app` (de `k8s/overlays/local/secrets.env`) e Secret `workshop-db` (Terraform) |
| Pipeline (`cd.yml`) | Os mesmos do kind | Secrets do environment `kind-ci` no GitHub |

O Deployment carrega os quatro objetos do Kubernetes com `envFrom`, então cada chave vira uma variável de ambiente do
container.

## Variáveis

### Banco de dados

| Variável | Padrão da aplicação | Kubernetes | Observação |
| --- | --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/workshop_management_system` | ConfigMap `workshop-db` (Terraform) | URL JDBC completa |
| `DB_USERNAME` | `root` | Secret `workshop-db` (Terraform) | |
| `DB_PASSWORD` | `root` | Secret `workshop-db` (Terraform, `random_password`) | Gerada a cada `apply` em state vazio |

O schema é sempre validado (`ddl-auto=validate`, fixo) depois que o Flyway aplica as migrations; a aplicação nunca
cria nem altera tabelas sozinha.

### Perfil e dados de demonstração

| Variável | Padrão da aplicação | Compose / kind | Observação |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `default` | `dev` | |
| `APP_SEED_ENABLED` | `false` (no perfil `dev`: `true`) | `true` | Seeds só rodam com perfil `dev` **e** esta variável ligada; nunca em produção |

### Segurança

| Variável | Padrão da aplicação | Observação |
| --- | --- | --- |
| `APP_SECURITY_JWT_SECRET` | valor fixo de desenvolvimento | **Trocar em qualquer ambiente real.** Mínimo de 32 bytes |
| `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` | valor fixo de desenvolvimento | **Trocar em qualquer ambiente real.** Deve ser igual à variável `estimateGatewaySecret` da collection Postman |
| `APP_SECURITY_ESTIMATE_GATEWAY_TIMESTAMP_TOLERANCE_SECONDS` | `300` | Janela aceita para o timestamp da assinatura HMAC |
| `APP_SECURITY_ESTIMATE_GATEWAY_MAX_BODY_BYTES` | `65536` | Tamanho máximo do corpo assinado |

Para gerar um segredo aleatório no PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

### Fornecedor externo

| Variável | Padrão da aplicação | Compose / kind | Observação |
| --- | --- | --- | --- |
| `SUPPLIER_BASE_URL` | `http://localhost:8089` | `http://supplier-simulator:8080` | O simulador WireMock usa `docker/wiremock/mappings` |
| `SUPPLIER_API_KEY` | vazio | vazio | Segredo; o simulador local não confere a chave |
| `SUPPLIER_CONNECT_TIMEOUT` | `2s` | `2s` | |
| `SUPPLIER_READ_TIMEOUT` | `5s` | `5s` | |

### E-mail (RF52)

| Variável | Padrão da aplicação | Compose / kind | Observação |
| --- | --- | --- | --- |
| `APP_NOTIFICATION_EMAIL_CHANNEL` | `log` | `smtp` | `log` só registra a mensagem no log; `smtp` envia de verdade |
| `APP_NOTIFICATION_EMAIL_FROM` | `no-reply@workshop.local` | igual | Remetente |
| `SPRING_MAIL_HOST` | sem padrão | `mailpit` | Sem host configurado, nenhum envio SMTP é criado |
| `SPRING_MAIL_PORT` | sem padrão | `1025` | |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | sem padrão | vazios | Segredos; o Mailpit não exige autenticação |

As variáveis `SPRING_MAIL_*` não têm padrão de propósito: nenhuma credencial fica versionada. Em um provedor SMTP
real, host e porta vão para o ConfigMap, e usuário e senha vão para o Secret. Para desligar o envio no Compose:

```bash
APP_NOTIFICATION_EMAIL_CHANNEL=log docker compose up -d --build
```

### Outras

| Variável | Onde | Observação |
| --- | --- | --- |
| `TZ` | Compose e kind: `America/Sao_Paulo` | Fuso do container |
| `JAVA_TOOL_OPTIONS` | Imagem: `-XX:MaxRAMPercentage=75.0` | Heap proporcional ao limite de memória do container |
| `SERVER_PORT` | Compose: `8080` | Mesmo valor padrão do Spring |

# Execução local com Docker Compose

O caminho mais rápido para rodar a API no dia a dia: um comando sobe a aplicação, o MySQL, o Mailpit (SMTP local) e o
simulador do fornecedor (WireMock). Para reproduzir o ambiente Kubernetes (probes, HPA, rolling update), use o
[ambiente Kubernetes local](local-kubernetes.md).

## Pré-requisitos

| Item | Observação |
| --- | --- |
| Docker com Docker Compose | Docker Desktop no Windows/macOS |
| GNU Make | Opcional: cada alvo do `Makefile` tem o comando equivalente indicado abaixo |
| Java 21 | Só para rodar a aplicação fora dos containers (`make run-dev`) |

## Subir o ambiente

```bash
make docker-up              # sem make: docker compose up -d --build
```

O `make docker-up` cria o `.env` a partir do `.env.example` quando ele não existe; sem make, o Compose usa os mesmos
valores padrão. Edite o `.env` para mudar portas, perfil, seeds ou segredos.

O `--build` reconstrói a imagem da aplicação. Sem ele, o Compose reaproveita a imagem antiga e uma mudança de código
não aparece.

**Tempo de inicialização:** o MySQL leva até 90 s para ficar saudável, e a aplicação só inicia depois disso
(`depends_on` com `condition: service_healthy`). Com o banco vazio, o Flyway aplica todas as migrations e a aplicação
fica pronta em cerca de 3 minutos. Para acompanhar:

```bash
make docker-logs            # sem make: docker compose logs -f app
```

A aplicação está pronta quando `docker compose ps` mostra `workshop-app` como `(healthy)`. O healthcheck do Compose
chama o mesmo endpoint de readiness usado pelo Kubernetes (ver [Verificação de saúde](../operations/health-checks.md)).

## Endereços

| Serviço | URL |
| --- | --- |
| API | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` |
| Mailpit (e-mails enviados) | `http://localhost:8025` |
| Simulador do fornecedor (WireMock) | `http://localhost:8089` |
| MySQL | `localhost:3306` (dados no volume `mysql_data`) |

As portas mudam pelo `.env` (`APP_PORT`, `DB_PORT`, `MAILPIT_UI_PORT`). Se a 3306 já estiver ocupada, use
`DB_PORT=3307 make docker-up`. O Compose e o cluster kind usam as mesmas portas 8080 e 8025 no host, então rode um
de cada vez.

Para trocar para Kubernetes, execute `make docker-down` e siga a
[preparação do ambiente Kubernetes](local-kubernetes.md#0-preparar-o-ambiente) antes de `make infra-local-up`.
O volume do banco do Compose é preservado, mas seus dados não são transferidos para o banco do Kubernetes.
Outros projetos em execução devem ser avaliados e interrompidos explicitamente pelo usuário quando necessário.

## Dados de demonstração

O Compose usa o perfil `dev` com `APP_SEED_ENABLED=true`: na subida, a aplicação cria Customers e Stock Items de
exemplo de forma idempotente. Para subir sem eles, defina `APP_SEED_ENABLED=false` no `.env`. Fora do perfil `dev`, os
seeds ficam sempre desligados.

A conta `admin`/`ADMIN` não é seed: ela é criada por migration Flyway e existe em todos os ambientes (ver
[API: autenticação](../api/README.md#autenticação)).

## Comandos do dia a dia

| Objetivo | Com make | Sem make |
| --- | --- | --- |
| Estado dos containers | `make docker-ps` | `docker compose ps` |
| Logs da aplicação | `make docker-logs` | `docker compose logs -f app` |
| Console do MySQL | `make db-shell` | `docker compose exec mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'` |
| Parar (mantém os dados) | `make docker-down` | `docker compose down` |
| Apagar o banco e recomeçar | `make docker-reset` e `make docker-up` | `docker compose down -v` e `docker compose up -d --build` |
| Lista de alvos | `make help` | — |

O `docker-reset` apaga o volume do MySQL. Nenhum alvo de build, teste ou subida faz isso sozinho. Ele é necessário uma
vez ao adotar o baseline do Flyway sobre um banco criado antes pelo Hibernate (`ddl-auto=update`).

## Próximos passos

- Variáveis de ambiente aceitas pela aplicação: [Configuração](configuration.md).
- Roteiro completo de chamadas pelo Postman: [Teste manual](../api/manual-testing.md).

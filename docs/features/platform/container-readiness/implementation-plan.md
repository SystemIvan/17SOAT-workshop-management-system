# Plano de Implementação: Prontidão da aplicação para containers e Kubernetes

| Campo | Valor |
|---|---|
| Feature | `container-readiness` |
| Status | Draft |
| Atualizado em | 2026-10-07 |
| Revisor de identity/SecurityConfig | Santiago Silvestre |

Convenção deste plano: um item só é **Concluído** com a saída literal (resumida às linhas relevantes) registrada
aqui. O que não rodou está marcado como **NÃO EXECUTADO**, com o motivo. Ambiente da evidência: Windows 11,
Temurin `21.0.12.1+1` (via `JAVA_HOME`), Maven Wrapper 3.9.16, Docker 29.1.2, Docker Compose v2.40.3. GNU Make **não
está instalado** nesta máquina: os alvos `make` foram executados pelo comando `./mvnw` equivalente do `Makefile`.

## Checkpoints

### 1. Dependência Actuator, configurações de saúde e encerramento

- **Tarefas:** `spring-boot-starter-actuator` no `pom.xml` (versão do BOM); propriedades de health/probes, porta de
  gestão `8081` e `server.shutdown=graceful` com `spring.lifecycle.timeout-per-shutdown-phase=30s`.
- **Achado:** `src/test/resources/application.properties` sombreia o arquivo de `main` no classpath de testes; o bloco
  de health foi duplicado nele (sem a porta), com comentário nos dois arquivos exigindo que sejam mantidos iguais.
- **Colisão com a 8081:** nenhum outro teste usa `RANDOM_PORT`/`DEFINED_PORT`; nos testes a porta de gestão não é
  definida (mesma porta) e `ContainerProbesIntegrationTest` usa `management.server.port=0`.
- **Status:** Concluído (evidência no checkpoint 2).

### 2. Segurança, isolamento de probes e testes

- **Tarefas:** `SecurityFilterChain` dedicada (`@Order(1)`, `securityMatcher("/actuator/**")`) que libera só `GET` nos
  três caminhos e nega o resto (`denyAll`), sem filtro de autenticação; `ActuatorSecurityTest` (MockMvc) e
  `ContainerProbesIntegrationTest` (`RANDOM_PORT`, `management.server.port=0`, `@LocalManagementPort`).
- **Decisões D1/D2** (emendas nas specs, aprovadas por Leandro Nascimento em 2026-10-07): o agregado `/actuator/health` traz
  `groups`; qualquer caminho fora dos probes recebe `401`, inclusive com JWT `ADMIN` válido.
- **Banco DOWN:** indicador real `org.springframework.boot.jdbc.health.DataSourceHealthIndicator` (jar
  `spring-boot-jdbc-4.1.0`) registrado como `dbHealthContributor` sobre um `DataSource` que falha; a
  auto-configuração recua por `@ConditionalOnMissingBean(name = {"dbHealthIndicator","dbHealthContributor"})`.
- **Evidência** (`./mvnw test -Dtest='ContainerProbesIntegrationTest*,ActuatorSecurityTest,SecurityAuthorizationTest'`):

  ```
  [INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in ...identity.ActuatorSecurityTest
  [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in ...identity.ContainerProbesIntegrationTest$DatabaseDown
  [INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in ...SecurityAuthorizationTest
  [INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```

  (O Surefire atribui os 7 testes da classe com `@Nested` a `$DatabaseDown`.) Casos provados por HTTP real: liveness e
  readiness `200` `{"status":"UP"}`; agregado `200` `{"groups":["liveness","readiness"],"status":"UP"}`; banco DOWN →
  readiness `503` `{"status":"DOWN"}`, agregado `503`, liveness `200`; porta de negócio → `401` nos três caminhos;
  `/actuator`, `/actuator/env`, `/actuator/beans`, `/actuator/health/db` → `401`; `POST`/`PUT`/`DELETE` nos probes →
  `401`; `Authorization: Bearer lixo` no liveness → `200`; JWT `ADMIN` válido em `/actuator/env` → `401`.
- **`./mvnw clean verify`:** falha por um único teste, que **também falha na `dev`** nesta máquina (ver D3):

  ```
  # branch da feature
  [ERROR]   ExternalSupplierHttpAdapterTest.translatesAcceptanceAndSendsOnlyTheSupplierContract:64 » ExternalSupplierUnavailable External supplier is unavailable
  [ERROR] Tests run: 774, Failures: 0, Errors: 1, Skipped: 0
  [INFO] BUILD FAILURE
  # dev (a00fec5), worktree separado
  [ERROR]   ExternalSupplierHttpAdapterTest.translatesAcceptanceAndSendsOnlyTheSupplierContract:64 » ExternalSupplierUnavailable External supplier is unavailable
  [ERROR] Tests run: 763, Failures: 0, Errors: 1, Skipped: 0
  [INFO] BUILD FAILURE
  ```

- **Cobertura** (`./mvnw verify -Dmaven.test.failure.ignore=true`, usado só para o gate rodar apesar do erro acima):

  ```
  [INFO] --- jacoco:0.8.13:check (check) @ workshop-management-system ---
  [INFO] All coverage checks have been met.
  LINE global: covered=5203 missed=339 ratio=0.9388 · SecurityConfig: missed 0, covered 51
  ```

- **`ModuleStructureTest`:** incluído nos 774 testes acima, sem falha.
- **OpenAPI:** `curl -s http://localhost:8080/v3/api-docs | grep -c actuator` → `0` (stack do compose). Postman
  inalterado.
- **`make sca`:** NÃO EXECUTADO — sem `NVD_API_KEY`, o Dependency-Check ficou parado na primeira página do NVD
  (`Retrying request /rest/json/cves/2.0?resultsPerPage=2000&startIndex=0 : 12th time`) e foi interrompido (D5).
  O responsável executa depois, com a chave.
- **Status:** Concluído com ressalvas (`make verify` vermelho por D3; `make sca` NÃO EXECUTADO por D5).

### 3. Dockerfile multi-stage e imagem

- **Imagens base** (`docker buildx imagetools inspect`, índice multi-arch com `linux/amd64` e `linux/arm64/v8`):

  ```
  Name:      docker.io/library/eclipse-temurin:21.0.12.1_1-jdk-noble
  Digest:    sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87
  Name:      docker.io/library/eclipse-temurin:21.0.12.1_1-jre-noble
  Digest:    sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c
  ```

- **`docker build -t app-readiness-test .`** → `exit=0`:

  ```
  #13 [builder 5/7] RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline   DONE 95.2s
  #15 [builder 7/7] RUN ./mvnw -B -q package -DskipTests && cp target/workshop-management-system-*.jar application.jar && java -Djarmode=tools -jar application.jar extract --layers --destination extracted   DONE 60.4s
  #21 naming to docker.io/library/app-readiness-test:latest
  ```

- **`docker history --no-trunc`** (camadas próprias): `ENTRYPOINT ["sh" "-c" "exec java $JAVA_OPTS -jar
  application.jar"]`, `EXPOSE [8080/tcp 8081/tcp]`, `USER 10001:10001`, `ENV JAVA_OPTS=-XX:MaxRAMPercentage=75.0`,
  `COPY --chmod=0755 docker/healthcheck.sh`, 4 `COPY` das camadas extraídas (`dependencies` 71.8MB, `spring-boot-loader`,
  `snapshot-dependencies`, `application` 840kB), `RUN groupadd --gid 10001 app && useradd --uid 10001 ...`.
- **`docker inspect`:** `User="10001:10001"`; `ExposedPorts={"8080/tcp":{},"8081/tcp":{}}`;
  `Env=[PATH, JAVA_HOME, LANG, LANGUAGE, LC_ALL, JAVA_VERSION=jdk-21.0.12.1+1, JAVA_OPTS=-XX:MaxRAMPercentage=75.0]` —
  sem segredos em `ENV`, `ARG`, `LABEL` ou camadas.
- **Usuário:** `docker run --rm --entrypoint id app-readiness-test -u` → `10001`; `-g` → `10001`;
  `application.jar` com dono `0 0`, modo `-rw-r--r--` → somente leitura para o usuário da aplicação.
- **`JAVA_OPTS`** (comando do ENTRYPOINT com `-XX:+PrintFlagsFinal -version`, `-m 1g`):

  ```
  ## nao definida (padrao da imagem)       MaxHeapSize = 805306368  MaxRAMPercentage = 75.000000 {command line}
  ## JAVA_OPTS=-XX:MaxRAMPercentage=50.0  MaxHeapSize = 536870912  MaxRAMPercentage = 50.000000 {command line}
  ## definida vazia JAVA_OPTS=             MaxHeapSize = 268435456  MaxRAMPercentage = 25.000000 {default}
  ```

  No compose, `${JAVA_OPTS:--XX:MaxRAMPercentage=75.0}` aplica o padrão também quando a variável está vazia.
- **E3 — CAs customizadas:** o `ENTRYPOINT` do projeto substitui o `/__cacert_entrypoint.sh` da imagem Temurin, então
  CAs customizadas (`USE_SYSTEM_CA_CERTS`/`/certificates`) não são importadas. Relevante se exigirmos TLS verificado no
  RDS; registrado para o ADR-008 da frente de infra.
- **E4 — LABEL `org.opencontainers.image.version`:** herdado da base (`24.04`). Não sobrescrito: exigiria definir um
  valor de versão e ligá-lo ao `pom.xml` por `ARG`, o que não é trivial.
- **Linha longa:** o `FROM` do estágio JDK tem 125 caracteres (referência imagem+digest, não quebrável).
- **Status:** Concluído.

### 4. Healthcheck, Compose e variáveis

- **`docker/healthcheck.sh`:** Bash puro via `/dev/tcp`, `printf` com `\r\n`, `timeout 3` cobrindo conexão e leitura,
  aceita só `^HTTP/1\.[01] 200`; `bash -n` ok; modo `100755` no índice; LF garantido por `.gitattributes`
  (`*.sh text eol=lf`, `Dockerfile text eol=lf` — D4):

  ```
  i/lf    w/lf    attr/text eol=lf      	Dockerfile
  i/lf    w/lf    attr/text eol=lf      	docker/healthcheck.sh
  ```

- **`docker compose up -d --build`** (com `DB_PORT=3307` no ambiente do comando: a `3306` do host está ocupada por um
  MySQL local; a primeira tentativa falhou com `bind: Only one usage of each socket address`):

  ```
  workshop-app                  ... Up About a minute (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
  workshop-mysql                ... Up 2 minutes (healthy)        0.0.0.0:3307->3306/tcp
  workshop-supplier-simulator   ... Up 6 minutes (healthy)        0.0.0.0:8089->8080/tcp
  ```

- **Porta 8081 não publicada:** do host, `curl http://localhost:8081/actuator/health/readiness` → `000`, `exit=7`.
  Porta de negócio: `GET /actuator/health/readiness` → `HTTP 401`.
- **Probes dentro do container (8081):** agregado `200` `{"groups":["liveness","readiness"],"status":"UP"}`;
  liveness `200` `{"status":"UP"}`; readiness `200` `{"status":"UP"}`; `/actuator/env` `401`;
  `/app/healthcheck.sh` → `exit=0`. PID 1: `java -Xms512m -Xmx1024m -XX:+UseG1GC -jar application.jar` (valor do `.env`
  local, não versionado).
- **Banco derrubado (`docker compose stop mysql`):**

  ```
  app health=unhealthy
  /actuator/health/liveness -> HTTP/1.1 200  {"status":"UP"}
  /actuator/health/readiness -> HTTP/1.1 503  {"status":"DOWN"}
  healthcheck.sh exit=1
  RestartCount=0
  ```

  Com o banco de volta, `app health=healthy` com `RestartCount=0` e o mesmo `StartedAt` (sem reinício).
- **SIGTERM (E1):** 17 paradas observadas: **143 x 16, 0 x 1**. A parada com `0` foi a primeira
  (`docker compose stop app`, `ExitCode=0 OOMKilled=false`), com shutdown gracioso completo no log
  (`Commencing graceful shutdown` → `Graceful shutdown complete` → `HikariPool-1 - Shutdown completed.` em ~3 s); não
  se reproduziu. Controles com 143: JVM pura (`Thread.sleep`), JVM pura com `-t -i`, app via `docker run` sem TTY,
  `docker stop` e `docker compose stop` no compose, dois ciclos de queda/volta do banco e container recriado. Rodada
  E1 de 10 ciclos (`up → healthy → docker stop → ExitCode`):

  ```
  ciclo 1: antes=healthy ExitCode=143
  ciclo 2: antes=healthy ExitCode=143
  ciclo 3: antes=healthy ExitCode=143
  ciclo 4: antes=healthy ExitCode=143
  ciclo 5: antes=healthy ExitCode=143
  ciclo 6: antes=healthy ExitCode=143
  ciclo 7: antes=healthy ExitCode=143
  ciclo 8: antes=healthy ExitCode=143
  ciclo 9: antes=healthy ExitCode=143
  ciclo 10: antes=healthy ExitCode=143
  ```

  Nenhum `137` (SIGKILL) nem OOM. Critério emendado (E1) nas specs: `143` esperado; `0` aceito como parada limpa
  quando o log mostra o shutdown gracioso completo.
- **`stop_grace_period: 35s` no serviço `app` (F1):** maior que `timeout-per-shutdown-phase=30s`, para o Docker não
  mandar `SIGKILL` no meio do shutdown gracioso. `docker compose config app` → `stop_grace_period: 35s`; parada medida:

  ```
  antes: healthy
  StopTimeout=35
  duração do stop: 10.3s        (comando `docker compose stop app` inteiro)
  ExitCode=143 OOMKilled=false
  Commencing graceful shutdown 09:19:00.853 -03:00 → FinishedAt 12:19:01.168Z (JVM encerrou em ~0,3 s)
  ```

- **Filesystem somente leitura (E2), insumo para K8S-2:** com `--read-only --tmpfs /tmp` contra o MySQL do compose, a
  aplicação sobe e os probes respondem; sem `tmpfs` ela não sobe porque a Tomcat cria seus diretórios em `/tmp`:

  ```
  # docker run --read-only --tmpfs /tmp
  Status=running ExitCode=0 ReadonlyRootfs=true Tmpfs={"/tmp":""}
  Started WorkshopManagementSystemApplication in 48.106 seconds
  /actuator/health/liveness -> HTTP/1.1 200  {"status":"UP"}
  /actuator/health/readiness -> HTTP/1.1 200  {"status":"UP"}
  touch: cannot touch '/app/x': Read-only file system
  ls /tmp: hsperfdata_app tomcat.8080.* tomcat.8081.* tomcat-docbase.8080.* tomcat-docbase.8081.*
  # docker run --read-only (sem tmpfs)
  sem tmpfs: Status=exited ExitCode=1
  Caused by: org.springframework.boot.web.server.WebServerException: Unable to create tempDir. java.io.tmpdir is set to /tmp
  Caused by: java.nio.file.FileSystemException: /tmp/tomcat.8080.2993336203226096314: Read-only file system
  ```

  Insumo para K8S-2: `readOnlyRootFilesystem: true` exige um volume `emptyDir` montado em `/tmp`.
- **Revalidação após o merge da `dev` (2026-10-22, emenda E2):** o PR #53 (RF52) trouxe Mailpit, `mysql:8.4` e o
  `docker/wait-for-mysql.sh` como `ENTRYPOINT`, com `netcat-traditional` e `mysql-client` na imagem. Pela emenda E2
  (decisão de Leandro Nascimento em 2026-10-22, aguardando a aprovação do Santiago Silvestre no PR #55), o script sai e
  o healthcheck do `mysql` passa a usar TCP. Ambiente: Docker 29.1.2, Compose v2.40.3, projeto compose isolado
  (`-p wmsval`, nomes de container e portas próprios, `APP_PORT=8095`) para não tocar no volume local. Causa raiz,
  medida com o serviço `mysql` do compose em volume novo e ping a cada ~0,3 s (horários UTC):

  ```
  17:26:51.717  /usr/sbin/mysqld: ready for connections ... port: 0      (servidor temporário, só socket)
  17:26:51.805  mysqladmin ping -h localhost  -> OK pela primeira vez
  17:27:08.353  [Entrypoint]: Temporary server stopped
  17:27:22.843  /usr/sbin/mysqld: ready for connections ... port: 3306   (servidor definitivo)
  17:27:24.929  mysqladmin ping -h 127.0.0.1  -> OK pela primeira vez
  ```

  Primeira subida com o healthcheck por TCP (volume vazio, `docker compose up -d --build`):

  ```
  build exit=0 em 177s
  17:32:08.98 mysqld: ready for connections ... port: 3306
  17:32:13 wmsval-mysql health_status: healthy
  17:32:13 wmsval-app start
  17:33:18 wmsval-app health_status: healthy
  Started WorkshopManagementSystemApplication in 59.375 seconds
  StartedAt=2026-10-22T17:32:13.421Z RestartCount=0 User=10001:10001 Entrypoint=["sh","-c","exec java $JAVA_OPTS -jar application.jar"]
  falhas de conexao com o banco no log da app: 0
  ```

  Probes, imagem, banco parado, SIGTERM e segunda subida:

  ```
  /actuator/health -> HTTP/1.1 200   {"groups":["liveness","readiness"],"status":"UP"}
  /actuator/health/liveness -> HTTP/1.1 200   {"status":"UP"}
  /actuator/health/readiness -> HTTP/1.1 200   {"status":"UP"}
  /actuator/env -> HTTP/1.1 401   {"code":"UNAUTHORIZED","message":"Authentication is required"}
  host -> 8081: curl exit=7 (portas publicadas da app: só 8080/tcp -> 8095)
  imagem: nc ausente, mysql ausente, mysqladmin ausente, /app/wait-for-mysql.sh inexistente, uid=10001 gid=10001
  banco parado: app unhealthy em 10s, liveness 200 {"status":"UP"}, healthcheck.sh exit=1
  banco de volta: mysql healthy em 4s, app healthy em 2s, RestartCount=0, mesmo StartedAt
  SIGTERM: ExitCode=143 OOMKilled=false, "Commencing graceful shutdown" -> "Graceful shutdown complete"
  segunda subida (volume existente): mysql 17:35:31 -> healthy 17:35:41; app 17:35:42 -> healthy 17:36:36; RestartCount=0
  ```

  Tempo de resposta da readiness com o banco parado (tempos via `docker exec`, que soma ~1-3 s no Windows):

  ```
  banco no ar:   /actuator/health/readiness -> HTTP/1.1 200 {"status":"UP"}    em 2679 ms
  banco parado:  /actuator/health/readiness -> HTTP/1.1 503 {"status":"DOWN"}  em 31871 ms e 31346 ms
                 /actuator/health/liveness  -> HTTP/1.1 200 {"status":"UP"}    em 1423 ms
  log: Caused by: java.net.UnknownHostException: mysql (HikariPool.createPoolEntry)
  ```

  Com o banco fora, a readiness só responde `503` depois do `connectionTimeout` padrão do Hikari (30 s). No compose isso
  não muda o resultado: o `healthcheck.sh` tem `timeout 3` e o serviço fica `unhealthy`. **Insumo para K8S-2:** o
  `timeoutSeconds` do readinessProbe estoura antes e conta como falha, o que tira o pod do Service como esperado; avaliar
  reduzir `spring.datasource.hikari.connection-timeout` para não prender uma thread da porta de gestão por 30 s a cada
  probe.

- **Status:** Concluído (ver ressalva de E1).

### 5. Documentação

- **README — `make run-dev` e porta 8081 (F3):** comando equivalente do alvo
  (`SPRING_PROFILES_ACTIVE=dev APP_SEED_ENABLED=true ./mvnw spring-boot:run`) contra o MySQL do compose
  (`DB_URL=jdbc:mysql://localhost:3307/workshop...`). A `8080` do host está ocupada por um `httpd` local (a primeira
  tentativa falhou com `Port 8080 was already in use`), então a porta de negócio foi movida com `SERVER_PORT=8090`; a
  porta de gestão ficou no padrão:

  ```
  Tomcat started on port 8090 (http) with context path '/'
  Tomcat started on port 8081 (http) with context path '/'
  curl -s http://localhost:8081/actuator/health/liveness -> {"status":"UP"} HTTP 200
  curl -s http://localhost:8081/actuator/health/readiness -> {"status":"UP"} HTTP 200
  curl -s http://localhost:8081/actuator/health -> {"groups":["liveness","readiness"],"status":"UP"} HTTP 200
  curl -s http://localhost:8081/actuator/env -> {"code":"UNAUTHORIZED","message":"Authentication is required"} HTTP 401
  curl -s http://localhost:8090/actuator/health/readiness -> {"code":"UNAUTHORIZED","message":"Authentication is required"} HTTP 401
  ```

- `README.md` e `DOCKER.md`: somente parágrafos acrescentados (porta de gestão, probes, `JAVA_OPTS`, `DB_PORT`);
  nenhuma frase existente foi reescrita.
- E2 (2026-10-22): o parágrafo "Tempo de inicialização" do `README.md`, vindo do #53, foi reescrito porque descrevia o
  `wait-for-mysql.sh` (mesma estrutura, tempos medidos na revalidação do checkpoint 4); o `DOCKER.md` ganhou um
  parágrafo sobre o healthcheck do `mysql` por TCP.
- `docs/Architecture.md`: seção da feature reescrita para afirmar só o que configuração e testes sustentam; a baseline
  histórica de 10 de agosto não foi alterada.
- `.env.example`: `JAVA_OPTS=-XX:MaxRAMPercentage=75.0`.
- Specs: emendas D1, D2 e E1 aprovadas por Leandro Nascimento em 2026-10-07; E2 decidida por Leandro Nascimento em
  2026-10-22, aguardando a aprovação do Santiago Silvestre no PR #55; campo Status inalterado.
- **Status:** Concluído (aguardando revisão humana).

### 6. Validação final, PRs e checkpoint de segurança

- Divisão em dois PRs: `feat/platform-actuator-probes` (aplicação) e `feat/platform-container-image` (imagem/compose).
- **Status:** Pendente — só depois dos dois PRs abertos, revisados e com `make sca` executado pelo responsável.

## D3 — `ExternalSupplierHttpAdapterTest` (fora do escopo, não alterado)

O teste falha só nesta máquina, na suíte completa (`SocketTimeoutException: Read timed out` com `readTimeout` de
200 ms); isolado passou 3/3 e falha igualmente na `dev` local (saídas no checkpoint 2). No CI a `dev` está verde:

```
completed	success	Merge pull request #52 from SystemIvan/fix/servicelifecycle-awaiting-…	CI	dev	push	37550442682	1m38s	2026-10-07T00:09:12Z
completed	success	Merge pull request #51 from SystemIvan/feat/servicelifecycle-external…	CI	dev	push	37397541123	1m24s	2026-10-06T01:07:20Z
completed	success	Merge pull request #50 from SystemIvan/feat/servicelifecycle-estimate…	CI	dev	push	37124613568	1m17s	2026-10-03T12:58:27Z
completed	success	Merge pull request #49 from SystemIvan/feat/servicelifecycle-status-n…	CI	dev	push	35938730035	1m25s	2026-09-24T00:30:33Z
completed	success	Merge pull request #48 from SystemIvan/feat/servicelifecycle-order-li…	CI	dev	push	35674771851	1m48s	2026-09-22T01:10:15Z
```

## D5 — SCA

`make sca` NÃO EXECUTADO: sem `NVD_API_KEY` e sem GNU Make nesta máquina. O processo equivalente
(`./mvnw org.owasp:dependency-check-maven:check -DskipTestScope=false`) foi interrompido sem resultado. Fica para o
responsável, com a chave, antes do merge do PR 1 (nova dependência `spring-boot-starter-actuator`).

## Checkpoint de Segurança (AGENTS.md)

| Item | Risco | Mitigação | Evidência | Status |
|---|---|---|---|---|
| Validação de entrada e mass assignment | — | Nenhum endpoint de negócio, request ou DTO novo. | N/A | N/A |
| Autenticação e autorização | Bypass da segurança ou exposição de gestão | Cadeia dedicada só para `/actuator/**`, `GET` nos 3 probes, `denyAll` no resto; cadeia de negócio inalterada | `ContainerProbesIntegrationTest` (401 em `/actuator`, `/env`, `/beans`, `/health/db`, métodos de escrita e JWT `ADMIN`); `SecurityAuthorizationTest` 15/15 | Concluído |
| Exposição de dados de cliente/veículo/operação | — | Probes não leem dados de domínio. | N/A | N/A |
| Segredos, credenciais e logs | Segredo na imagem | Nada de segredo em `ENV`/`ARG`/`LABEL`; `.env` fora do contexto (`.dockerignore`) | `docker inspect` e `docker history` (checkpoint 3) | Concluído |
| SQL, persistência e migrations | — | Sem migration, entidade ou tabela. | N/A | N/A |
| Erros e information disclosure | Detalhes internos no health | `show-details=never`, `show-components=never`; só status (agregado com nomes de grupos, D1) | Corpos literais no checkpoint 2 e no compose (checkpoint 4) | Concluído |
| Novas dependências | CVE no Actuator | Versão gerenciada pelo BOM do Spring Boot 4.1.0 | `make sca` NÃO EXECUTADO (D5) | Pendente |
| Abuso de endpoints novos | Probes sem token usados para carga | Porta `8081` não publicada (compose) e fora do Service (K8S-2); probes baratos e sem dados | `curl` do host na 8081 → `exit=7` | Concluído |

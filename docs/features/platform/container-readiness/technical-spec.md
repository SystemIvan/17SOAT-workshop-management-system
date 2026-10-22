# Especificação Técnica: Prontidão da aplicação para containers e Kubernetes

| Campo | Valor |
|---|---|
| Feature | `container-readiness` |
| Status | Approved |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-06 |
| Aprovado por | Leandro Nascimento |
| Aprovado em | 2026-10-06 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

A feature prepara a aplicação para rodar no Kubernetes com escalabilidade e resiliência, ativando endpoints do Actuator para probes de saúde e empacotando tudo numa imagem de containers confiável e de permissões mínimas.
Nenhum módulo de domínio será modificado. As mudanças focarão na configuração transversal HTTP (`identity` e segurança), nas propriedades de *graceful shutdown* e em arquivos de DevOps (`Dockerfile`, `docker-compose.yml`, documentação).

## Interfaces e fluxo de dados

Será ativado o Spring Boot Actuator no `application.properties` para garantir o controle da exposição da API de saúde na porta separada:
- `management.server.port=8081`
- `management.endpoints.web.exposure.include=health`
- `management.endpoint.health.probes.enabled=true`
- `management.endpoint.health.group.liveness.include=livenessState`
- `management.endpoint.health.group.readiness.include=readinessState,db`
- `management.endpoint.health.show-details=never`
- `management.endpoint.health.show-components=never`
- `server.shutdown=graceful`
- `spring.lifecycle.timeout-per-shutdown-phase=30s` (Garante tempo pro Spring Boot fechar as conexões)

A semântica das integrações não-críticas (como o mock do WireMock) ficará de fora dos probes `readinessState` para evitar downtime generalizado indevido.

- **Comportamento e Falhas:**
  - Banco indisponível: `readiness` falha com `503` `DOWN`. `liveness` continua `200` `UP`.
  - Migration Flyway falha: Spring não inicializa, o container não fica pronto e termina (restart loop no k8s).
  - Partida lenta vs probes: O pod responde 503 no `readiness` até terminar de carregar os beans e as conexões ao banco.
  - Porta `8081` ocupada: Inicialização do Actuator aborta e quebra a subida da aplicação com status 1.
  - `docker stop` ou `SIGTERM`: Processo em background vai negar novas requests e terá `30s` (via timeout) para concluir o que já processava, saindo com exit code `143` (não 137/SIGKILL) e log de "Graceful shutdown complete" dentro do prazo. Readiness desativado no shutdown.
  - WireMock fora: Nenhum dos probes sofre alteração.

## Persistência e dados de bootstrap

- Sem migration.
- No seed required.

## Segurança e operação

- **Segurança (Módulo `identity`) e Filtro JWT**:
  O maior RISCO nesta etapa é a `SecurityFilterChain` não reagir adequadamente aos múltiplos contextos de porta abertos pela aplicação no Actuator (porta 8080 de negócio vs 8081 do Actuator). É necessário liberar explicitamente com método GET os três probes (health, liveness e readiness).
  - A regra principal exigirá bloquear via `401` para anônimos e `403` para todos os perfilados os acessos não mapeados em `/actuator/**`.
  - **Estratégia de resolução:** Caso o `requestMatchers("/actuator/health/**")` injetado na cadeia primária falhe por conta da diferença de porta da servlet do gerenciamento, será utilizado o plano B: criação de uma `SecurityFilterChain` dedicada com `securityMatcher("/actuator/**")` e prioridade explícita `@Order(1)`, onde se exclui o `JwtAuthenticationFilter` da execução, assegurando o bypass direto pros 3 probes definidos e negando todos os outros endpoints de Actuator.

- **Dockerfile (Build Stage & Imagem Final):**
  - **Build Stage**: Usa-se a tag completa do JDK 21 atual com digest multi-arch obtido na implementação via `docker buildx imagetools inspect` (ex: `eclipse-temurin:21-jdk-noble@sha256:...`). O Maven integrado não será usado. Será rodado `./mvnw` para usar o wrapper e o cache de camadas do próprio Maven em `.m2`. Após o build, o JAR (`target/workshop-management-system-*.jar`) será copiado para um nome fixo (`application.jar`).
  - **Extração de Camadas**: O comando `java -Djarmode=tools -jar application.jar extract --layers --destination extracted` explodirá o pacote preservando a separação de camadas oficial da R5, sem necessidade de `--launcher`.
  - **Runtime Final**: Será usada a base de longo suporte JRE correspondente com tag completa + digest. Do estágio de build, faremos comandos `COPY` separados para o mesmo diretório de trabalho: `extracted/dependencies/`, `extracted/spring-boot-loader/`, `extracted/snapshot-dependencies/` e `extracted/application/`. A imagem expõe portas `EXPOSE 8080` e `EXPOSE 8081`.
  - A imagem executará como não-root possuindo ID/GID `10001:10001` (evitando assim o default ID 1000 da canonical).
  - A validade desta receita exata será atestada no `docker build` da fase de implementação.

- **ENTRYPOINT Exec com `JAVA_OPTS` e PID 1**:
  - Para receber o sinal de `SIGTERM` e executar o `graceful shutdown` perfeitamente, o Java precisa rodar no PID 1.
  - Dessa forma será utilizado `ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]`. A chamada de `exec java` substitui o shell e repassa o processo como dono do PID 1 e realiza expansão correta de `$JAVA_OPTS`. O Spring Boot ≥ 3.3 entende e atua com as camadas nativamente pelo JAR descompactado, removendo a necessidade do `JarLauncher` direto no ENTRYPOINT sem usar `-cp`.

- **Integração no Localhost e Configuração Compose**:
  - Será passado a variável `JAVA_OPTS` via `.env` ao container app para configurar a memória (indicado em `.env.example`).
  - A porta de gerenciamento `8081` não será exposta ao hospedeiro local.
  - O Healthcheck nativo no `.yml` de composição irá rodar um arquivo de script versionado `docker/healthcheck.sh` copiado para a imagem, efetuando o respectivo GET na URL via bash puro. O script utilizará `printf` com CRLF (`\r\n`), casando a primeira linha estrita `^HTTP/1\.[01] 200` e definindo um timeout curto, assegurando que termine com exit != 0 em caso de lentidão ou falha de conexão (ex.: `timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/8081 && printf "GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && cat <&3 | grep -q "^HTTP/1\.[01] 200"' || exit 1`). Isso prova estado HTTP limpo de forma nativa e enxuta.

- **Impactos Estruturais em Markdown**: O `README.md`, `DOCKER.md`, `.env.example` e a documentação `docs/Architecture.md` serão atualizados explicando as portas, flags de JVM, uso dos probes e a gestão do Docker Compose.

## Emenda 2026-10-06 (aprovada por Leandro Nascimento em 2026-10-07)

Decisões tomadas durante a implementação (revisão da etapa 2), registradas aqui sem alterar o status da spec:

- **D1 — corpo do agregado:** `/actuator/health` responde `{"groups":["liveness","readiness"],"status":"UP"}`.
  No Spring Boot 4.1, `HealthEndpointSupport` inclui sempre os nomes dos grupos no endpoint primário,
  independentemente de `show-components`/`show-details` (verificado no bytecode de `spring-boot-health-4.1.0`).
  Os nomes são fixos e não sensíveis. `/liveness` e `/readiness` retornam exatamente `{"status":"UP"}`/`{"status":"DOWN"}`.
- **D2 — sem autenticação na cadeia do Actuator:** a `SecurityFilterChain` dedicada não registra o
  `JwtAuthenticationFilter`; todo chamador é anônimo. Qualquer caminho fora dos três probes (`GET`) recebe `401`,
  inclusive com JWT `ADMIN` válido, na porta de gestão. Na porta de negócio, os caminhos de health também recebem `401`
  (nenhum endpoint do Actuator está mapeado nela).
- **Configuração nos testes:** `src/test/resources/application.properties` sombreia o arquivo de `main` no classpath
  de testes; o bloco de health foi duplicado nele (sem a porta) e deve ser mantido idêntico ao de produção.
  `ContainerProbesIntegrationTest` sobe a porta de gestão separada (`management.server.port=0`) e simula banco
  indisponível com o indicador real `org.springframework.boot.jdbc.health.DataSourceHealthIndicator`, registrado como
  `dbHealthContributor` sobre um `DataSource` que falha.

## Emenda 2026-10-07 (aprovada por Leandro Nascimento em 2026-10-07)

- **E1 — exit code no SIGTERM:** `143` esperado; `0` observado em casos isolados com shutdown gracioso completo nos
  logs; ambos são parada limpa; `137` (SIGKILL) ou OOM é falha. Evidência (16 x 143, 1 x 0 em 17 paradas) no
  `implementation-plan.md`, checkpoint 4. Isto ajusta o item "`docker stop` ou `SIGTERM`" de "Comportamento e
  Falhas" e a validação manual 5.

## Estratégia de testes

- **Cobertura Base:** Confirmar que `make verify` continua gerando verde em todas as classes, que a fronteira estanque via `ModuleStructureTest` é obedecida e a cobertura global é `>= 80%`. *Os testes existentes da matriz de autorização do SecurityConfig continuam passando inalterados.*
- **Testes Mocks (Semântica de Acessos):** Criação/Atualização do arquivo `ActuatorSecurityTest` validando a liberação global (`200 OK` via MockMvc) de GET em `/actuator/health/readiness`, `/liveness` e `/health`. O comportamento do teste simulará o banco de dados indisponível (`DOWN`) através de um substituto usando `@TestConfiguration` ou `@MockitoBean` sobrepondo o `HealthIndicator` de DB para DOWN. O WireMock caindo também será simulado provando que liveness/readiness não alteram seus status originais.
- **Teste OBRIGATÓRIO de Porta HTTP Aleatória:** No ambiente configurado para Mock, o Actuator responde pelo contexto principal integrado. Por isso, a separação real da porta será comprovada unicamente na classe `ContainerProbesIntegrationTest` (que será instanciada em ambiente real de runtime `SpringBootTest.WebEnvironment.RANDOM_PORT`, atrelando `management.server.port=0`, e efetuando requests mapeando a porta em `@LocalManagementPort`). Provando por consequência que o `JwtAuthenticationFilter` não colide nem vaza credenciais ou regras com os 3 probes específicos da porta paralela.

- **Validações Manuais Documentadas para o Checkpoint:**
  1. Compilar sem falhas: `docker build` (verificando as layers, a conversão para `application.jar` e a busca por tags sha256).
  2. Teste do Actuator rodando compose `docker compose up` atestando estado "healthy" pelo request GET via `/dev/tcp`.
  3. Checagem do PID rootless rodando o comando `docker run --rm --entrypoint id <imagem> -u` retornando `10001`.
  4. Checagem de ausência de credenciais sigilosas com `docker history <imagem>` ou `docker inspect <imagem>`.
  5. Teste de `SIGTERM` rodando `docker stop <imagem>` atestando exit code `143` (não 137) e log legível de 'Graceful shutdown complete'.

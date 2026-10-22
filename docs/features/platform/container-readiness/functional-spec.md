# Especificação Funcional: Prontidão da aplicação para containers e Kubernetes

| Campo | Valor |
|---|---|
| Feature | `container-readiness` |
| Status | Approved |
| Responsável | Leandro Nascimento |
| Atualizado em | 2026-10-06 |
| Aprovado por | Leandro Nascimento |
| Aprovado em | 2026-10-06 |
| Revisor de identity/SecurityConfig | Santiago Silvestre |
| Referências | Tech Challenge Fase 2 (deploy em Kubernetes com probes e escalabilidade automática); tarefa K8S-1 do plano da Fase 2 do time; `identity/SecurityConfig.java`; `Dockerfile`; `docker-compose.yml`; `DOCKER.md`; `docs/Architecture.md` §\"Execução local\" (afirma que não há Actuator); `docs/features/platform/owasp-vulnerability-assessment/technical-spec.md` (Frente 2 — prontidão medida por `/v3/api-docs` por falta de Actuator); `docs/adr/ADR-003-authentication-strategy.md` |

## Problema e resultado esperado

A Fase 2 exige que a aplicação rode em Kubernetes (EKS na AWS e cluster local) com escalabilidade automática. Para
isso, o orquestrador precisa saber, a cada instante, se cada réplica **está viva** (senão deve ser reiniciada) e se
**está pronta para receber tráfego** (senão deve sair do balanceamento). Hoje a aplicação não oferece nenhum desses
sinais:

- não há Spring Boot Actuator nem qualquer endpoint de saúde;
- a cadeia de segurança termina em `anyRequest().authenticated()`, então qualquer endpoint de saúde que viesse a
  existir exigiria JWT — e o kubelet não envia token, logo os probes falhariam e os pods nunca ficariam prontos;
- o `docker-compose.yml` não sabe quando o serviço `app` está pronto (só o MySQL tem healthcheck); por isso o scan
  ZAP usa `/v3/api-docs` como gate improvisado de prontidão.

A imagem Docker também não está adequada a containers orquestrados:

- o estágio de build usa `maven:3.9.6-eclipse-temurin-21`, mas o build executa `./mvnw`, que baixa o Maven declarado
  no wrapper (3.9.16) — a imagem Maven é redundante e diverge da versão usada localmente e na CI;
- as tags de imagem são móveis (`21-jre-jammy`), o que torna o build não reprodutível;
- o `ENTRYPOINT` em forma exec ignora `JAVA_OPTS` (documentado no `.env.example`), e o compose nem repassa essa
  variável; a JVM não recebe nenhum ajuste orientado a limites de memória do container;
- o usuário não-root é declarado por nome (`USER appuser`), o que impede o Kubernetes de verificar `runAsNonRoot`.

**Resultado esperado:** a aplicação expõe três sinais de saúde mínimos, acessíveis sem autenticação apenas para o que
os probes precisam, sem vazar detalhes internos; a imagem é reprodutível, não-root verificável, respeita os limites de
memória do container e encerra de forma graciosa; o `docker compose up` continua funcionando e passa a saber quando a
aplicação está pronta. Nenhuma regra de negócio, módulo de domínio, migration ou contrato de API de negócio muda.

## Atores e cenários

- **Orquestrador (kubelet do Kubernetes)** — consulta periodicamente os probes de cada pod, sem credenciais.
- **Docker Compose (ambiente local)** — usa o healthcheck do serviço `app` para reportar `healthy`.
- **Time de infraestrutura / pipeline de CD** — depende dos probes para `rollout status`, HPA e smoke test.
- **Chamador anônimo externo** (inclusive potencial atacante) — alcança a aplicação pelo LoadBalancer.
- **Usuário autenticado da API** (qualquer papel de `ADR-003`) — não deve ganhar acesso a nada de gestão por ter JWT.

Cenários ponta a ponta:

1. A aplicação inicia, o Flyway aplica as migrations e o contexto sobe: liveness responde \"vivo\"; readiness só passa
   a responder \"pronto\" depois que a aplicação terminou de iniciar e o banco está acessível.
2. O banco fica indisponível com a aplicação rodando: readiness passa a \"não pronto\" (o pod sai do balanceamento),
   mas liveness continua \"vivo\" (o pod **não** é reiniciado). Quando o banco volta, readiness volta a \"pronto\" sem
   reinício.
3. O simulador de fornecedor (WireMock) fica indisponível: liveness e readiness permanecem inalterados — a
   indisponibilidade de uma integração não crítica não tira a aplicação do ar.
4. O pod recebe `SIGTERM` (rolling update, scale-down do HPA): o processo Java recebe o sinal diretamente, readiness
   passa a \"não pronto\", requisições em andamento terminam e o processo encerra dentro do prazo padrão.
5. Um chamador anônimo tenta acessar `/actuator/env`, `/actuator/heapdump`, `/actuator` ou qualquer outro caminho de
   gestão: é negado.
6. Um usuário autenticado, mesmo `ADMIN`, tenta acessar caminhos de gestão além dos três probes: é negado.
7. O desenvolvedor executa `docker compose up`: MySQL, simulador e aplicação sobem como hoje e o serviço `app` passa
   a aparecer como `healthy` quando estiver pronto.
8. O operador define `JAVA_OPTS` (compose ou ConfigMap do K8s): as opções são de fato aplicadas à JVM.

## Regras

### R1 — Exposição mínima de endpoints de gestão

- Somente três caminhos de gestão existem para chamadores externos, apenas com `GET`:
  `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`.
- Nenhum outro endpoint do Actuator é exposto via HTTP (`env`, `beans`, `configprops`, `mappings`, `metrics`,
  `heapdump`, `threaddump`, `loggers`, `info`, `shutdown`, índice `/actuator` etc.).
- As respostas de saúde contêm apenas o status agregado (`{\"status\":\"UP\"}` ou `{\"status\":\"DOWN\"}`), **sem**
  componentes, detalhes, versões, nomes de banco, URLs, espaço em disco ou mensagens de erro, para qualquer chamador.
  Subcaminhos de componentes (ex.: `/actuator/health/db`) não são acessíveis.
- Status HTTP: `200` quando `UP`; `503` quando `DOWN`/`OUT_OF_SERVICE` (comportamento que o kubelet interpreta como
  falha do probe).
- Os probes ficam habilitados explicitamente (não apenas pela autodetecção de Kubernetes do Spring Boot), para que o
  mesmo comportamento valha no compose, nos testes e no cluster.
- **Emenda 2026-10-06 (D1) — aprovada por Leandro Nascimento em 2026-10-07:** `/actuator/health/liveness` e
  `/actuator/health/readiness` retornam exatamente `{\"status\":\"UP\"}` (ou `DOWN`). O agregado `/actuator/health`
  retorna também os nomes dos grupos, ex.: `{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}`. O Spring Boot
  4.1 sempre inclui esse campo no endpoint de saúde primário e nenhuma propriedade o remove; os nomes são fixos e não
  sensíveis, então são aceitos. Componentes, detalhes e demais informações continuam ocultos.

### R2 — Segurança (módulo `identity`)

- A matriz de autorização ganha uma regra explícita e mínima liberando **somente** os três caminhos de R1, com `GET`,
  declarada antes de `anyRequest()`, conforme exige o comentário de `SecurityConfig`.
- Qualquer outro caminho sob `/actuator/**` é negado explicitamente para todos (anônimo → `401`; autenticado com
  qualquer papel → `403`), em vez de depender apenas de o endpoint não estar exposto. Isso protege contra exposição
  acidental futura caso alguém amplie a lista de endpoints expostos.
- Nenhuma outra regra existente da matriz muda; nenhuma rota de negócio fica pública.
- As respostas de erro de autenticação/autorização continuam vindo dos handlers já existentes
  (`ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler`), sem stack trace ou detalhes internos.
- **Emenda 2026-10-06 (D2) — aprovada por Leandro Nascimento em 2026-10-07:** a cadeia de segurança do Actuator
  (`securityMatcher(\"/actuator/**\")`, `@Order(1)`) não registra nenhum filtro de autenticação, de forma intencional:
  todo chamador é anônimo nessa cadeia. Por isso, qualquer requisição fora dos três probes (`GET`) recebe `401`,
  inclusive com JWT `ADMIN` válido; não há caso `403` no Actuator. Isso substitui a expectativa \"autenticado com
  qualquer papel → `403`\" acima e o critério de aceite correspondente. Um header `Authorization` inválido não afeta
  os probes.

### R3 — Semântica de liveness e readiness (decisão proposta)

- **Liveness** reflete apenas o estado interno da aplicação (o processo está vivo e não entrou em estado
  irrecuperável). **Não** depende do banco nem de integrações externas. Justificativa: se liveness dependesse do banco,
  uma queda do RDS faria o kubelet reiniciar todas as réplicas ao mesmo tempo (reinício em cascata), sem resolver a
  causa e agravando a recuperação (todas as réplicas refazendo boot, Flyway e pool de conexões simultaneamente).
- **Readiness** reflete \"posso atender requisições agora\": o estado de prontidão da aplicação **e** a conectividade
  com o banco. Justificativa: praticamente todos os endpoints dependem do MySQL; um pod sem banco só devolveria erros.
  Migrations Flyway são cobertas de forma implícita: elas rodam durante a inicialização, e a aplicação só passa a
  \"pronta\" depois que a inicialização termina; se uma migration falhar, a aplicação não sobe (o container termina e
  o problema aparece como falha de rollout, não como pod \"pronto\" com schema inválido).
- Integrações não críticas (simulador de fornecedor/WireMock) **não** entram em liveness nem readiness; suas falhas
  continuam tratadas pelos fluxos de negócio que as usam.
- `/actuator/health` (agregado) reflete o estado geral (inclui banco), servindo para diagnóstico e smoke test; os
  probes do Kubernetes devem usar os caminhos específicos de liveness e readiness.
- Trade-off aceito: com o banco fora, **todas** as réplicas ficam \"não prontas\" e o Service fica sem endpoints
  (clientes recebem erro de conexão/503 do balanceador em vez de 500 da aplicação). Como a API não tem modo degradado
  sem banco, isso é preferível a manter réplicas \"prontas\" que só retornam erro.

### R4 — Porta de gestão (decisão proposta: porta separada `8081`)

Comparação avaliada:

| Critério | Porta separada `8081` (recomendada) | Mesma porta `8080` |
|---|---|---|
| Exposição pública | Health **não** é alcançável pelo Service/LoadBalancer (só pela rede do pod) | Health exposto na internet via LoadBalancer |
| Superfície para scans (ZAP) | Nenhum caminho de Actuator na porta pública | Caminhos de health visíveis publicamente |
| Probes sob carga (HPA) | Conector/pool de threads próprios: probes não disputam threads com a carga de negócio, reduzindo reinícios falsos durante o teste de carga | Probes competem com a carga; risco de timeout de liveness e reinício indevido sob estresse |
| Simplicidade | Duas portas para documentar (container, compose, manifestos) | Uma porta só |
| Testes | Regras de segurança testáveis via MockMvc; a separação real da porta exige um teste HTTP com porta aleatória | Tudo testável via MockMvc |
| Smoke test do CD | Não pode usar `/actuator/health` pelo LoadBalancer; usa `rollout status` (que já depende do readiness) + um endpoint de negócio (login) | Pode usar `/actuator/health` pelo LoadBalancer |

Recomendação: **porta de gestão `8081`, nunca publicada no Service/LoadBalancer**, com a aplicação de negócio
permanecendo em `8080`. Os ganhos de exposição e de estabilidade dos probes sob carga (relevante para a demonstração do
HPA) superam o custo de documentar uma segunda porta. A cadeia de segurança continua sendo aplicada também na porta de
gestão; a regra R2 vale nas duas portas (defesa em profundidade, não apenas \"isolamento por rede\").

Consequências registradas para as tarefas seguintes (fora do escopo desta feature): os manifestos (K8S-2) apontam os
probes para `8081` e não a incluem no Service; o smoke test do CD (CICD-3) usa `kubectl rollout status` + login em vez de
`/actuator/health` via LoadBalancer.

### R5 — Imagem Docker

- **Build:** usa imagem JDK 21 Temurin **sem Maven embutido** e compila com `./mvnw`, garantindo a mesma versão de Maven
  do wrapper (3.9.16) usada localmente e na CI. Testes não rodam no build da imagem (já rodam no quality gate da CI).
- **Versões fixas:** todas as imagens base usam tag de versão completa (ex.: versão exata do JDK/JRE e da distribuição)
  e, preferencialmente, digest `@sha256`, com comentário justificando a escolha. Nenhuma tag móvel (`latest`, `21-jre`).
- **Camadas e cache:** dependências (que mudam pouco) ficam em camadas separadas do código da aplicação, tanto no build
  (download de dependências antes de copiar `src/`) quanto na imagem final (camadas do Spring Boot), para builds e
  pushes ao ECR mais rápidos.
- **Não-root verificável:** a imagem final roda com UID/GID numéricos fixos e não privilegiados (ex.: `1000:1000`), o
  que permite `runAsNonRoot` no Kubernetes. O JAR pertence a root e é somente leitura para o usuário da aplicação.
- **JVM para container:** `JAVA_OPTS` passa a ser aplicada de fato. O padrão da imagem é orientado a containers
  (ex.: `-XX:MaxRAMPercentage=75.0`, deixando a JVM dimensionar o heap pelo limite de memória do pod em vez de `-Xmx`
  fixo); o operador pode sobrescrever sem reconstruir a imagem.
- **Sinais e encerramento:** o processo Java é o PID 1 do container (ou recebe os sinais diretamente via `exec`), de
  modo que `SIGTERM` dispare o encerramento gracioso do Spring Boot (cenário 4).
- **Portas:** a imagem documenta `8080` (API) e `8081` (gestão) via `EXPOSE`.
- **`HEALTHCHECK` no Dockerfile: não usar (decisão proposta).** O Kubernetes ignora a instrução `HEALTHCHECK` e usa os
  próprios probes; incluí-la exigiria embutir uma ferramenta HTTP na imagem só para isso e duplicaria a definição de
  saúde. A verificação local fica no `docker-compose.yml` (R6), onde é específica do ambiente.
- **Sem segredos:** nenhum segredo em `ENV`, `ARG`, `LABEL` ou camada da imagem; `.env` continua fora do contexto de
  build (`.dockerignore`).

### R6 — Docker Compose (ambiente local)

- O serviço `app` ganha healthcheck baseado no readiness da porta de gestão, executado dentro do container, **sem
  instalar pacotes adicionais na imagem apenas para isso** (usar o que a imagem base já oferece; se nada servir, usar
  um mecanismo nativo do shell/JVM, a definir na technical-spec).
- O tempo de tolerância inicial do healthcheck acomoda a inicialização com Flyway e seed de demonstração.
- `JAVA_OPTS` é repassada ao serviço `app` a partir do `.env`, e o `.env.example` passa a sugerir opções orientadas a
  container, coerentes com R5.
- A porta de gestão `8081` **não** é publicada no host por padrão (ver pergunta em aberto 4).
- Nada mais muda no fluxo local: mesmos serviços, mesmas portas publicadas (`8080`, `3306`, `8089`), mesmo
  `depends_on` do MySQL, mesmas variáveis de seed/perfil. `make docker-up` e `docker compose up` continuam funcionando.

### R7 — Contratos HTTP e documentação

- Os endpoints de saúde **não fazem parte do contrato de API de negócio**: o OpenAPI gerado pelo Springdoc continua sem
  endpoints do Actuator (comportamento padrão mantido) e a coleção Postman **não muda**. Justificativa: são endpoints
  operacionais, consumidos pelo orquestrador e pelo compose, não pelos clientes da oficina; a coleção Postman documenta
  fluxos de negócio. A documentação dos probes fica no `README.md` e no `DOCKER.md`.
- `README.md` (execução local) e `DOCKER.md` passam a descrever: portas `8080`/`8081`, como verificar os três endpoints
  de saúde (rodando via `./mvnw` e via compose), o significado de liveness × readiness, e o uso de `JAVA_OPTS`.
- `docs/Architecture.md` deixa de afirmar que o Actuator não existe e registra a porta de gestão.
- Documentos históricos que registram a ausência do Actuator na época (relatório de vulnerabilidades e specs da feature
  OWASP) não são reescritos.

### R8 — Compatibilidade

- Nenhum endpoint, request, response, validação ou status code de negócio muda.
- Nenhum módulo de domínio (`registration`, `servicelifecycle`, `stockprocurement`), migration ou seed muda.
- A única alteração em módulo é a configuração de segurança em `identity` (regra de R2); as fronteiras Modulith
  permanecem válidas (`ModuleStructureTest` verde).

## Fora de escopo

- Manifestos Kubernetes (`k8s/`): Deployment, probes, `startupProbe`, requests/limits, Service, HPA (tarefa K8S-2).
  Recomendação registrada para K8S-2: usar `startupProbe` apontando para liveness, para não precisar de
  `initialDelaySeconds` longo por causa do Flyway.
- Terraform (`infra/`) e workflows do GitHub Actions (`.github/`), inclusive trocar o gate de prontidão do scan ZAP de
  `/v3/api-docs` para o readiness — fica como sugestão de follow-up.
- Métricas (Prometheus/Micrometer), endpoint `info`, tracing e logs estruturados.
- Ajuste fino do encerramento gracioso além do que será explícito: `server.shutdown` não está definido em `application.properties`; será definido explicitamente.
- Scan de vulnerabilidades da imagem (Trivy) — tarefa CICD-6.
- Compatibilidade com `readOnlyRootFilesystem` (exigiria volume para `/tmp`; avaliar em K8S-2).
- Valores padrão de segredos de desenvolvimento já presentes em `application.properties` (`APP_SECURITY_JWT_SECRET`,
  segredo HMAC): permanecem como estão; nos ambientes Kubernetes eles devem ser sempre sobrescritos por Secret
  (registrar como pré-requisito de K8S-2/K8S-3, não alterar aqui).
- Coleção Postman, regras de negócio, módulos de domínio, migrations e seeds.
- ADR: as decisões desta feature ficam registradas nas specs; não se propõe ADR novo (ver pergunta em aberto 7).

## Critérios de aceite

Saúde e segurança:

- [ ] `GET /actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness` na porta de gestão retornam
      `200` com corpo contendo apenas `status` = `UP`, **sem** token, com a aplicação e o banco saudáveis.
- [ ] Nenhuma resposta de saúde contém `components`, `details`, nomes de banco, URLs, versões ou mensagens de erro.
- [ ] Com o banco indisponível (simulado em teste), readiness e `/actuator/health` retornam `503` com `status` =
      `DOWN`, enquanto liveness continua `200`/`UP`.
- [ ] Com o simulador de fornecedor indisponível, liveness e readiness permanecem `200`/`UP`.
- [ ] Anônimo em `/actuator`, `/actuator/env`, `/actuator/beans`, `/actuator/heapdump`, `/actuator/health/db` (e
      demais caminhos de gestão fora dos três probes) recebe `401`; usuário autenticado (incluindo `ADMIN`) recebe
      `403`; nenhum desses retorna conteúdo do Actuator.
- [ ] Métodos diferentes de `GET` nos três caminhos de saúde não são liberados.
- [ ] Na porta pública `8080` nenhum caminho de Actuator retorna informação de saúde.
- [ ] Todas as regras existentes da matriz de autorização continuam passando nos testes atuais (sem regressão).
- [ ] O OpenAPI gerado (`/v3/api-docs`) não contém endpoints do Actuator.

Imagem e ambiente local:

- [ ] O Dockerfile não usa imagem Maven; o build usa `./mvnw` sobre JDK Temurin 21 com versão fixa.
- [ ] Todas as imagens base do Dockerfile têm versão completa fixa (e digest, se aprovado), com justificativa em
      comentário.
- [ ] A imagem final roda com UID/GID numéricos não-root; `docker run --rm <imagem> id -u` não retorna `0`.
- [ ] `JAVA_OPTS` definida no ambiente é aplicada à JVM (verificável, ex.: `-XX:+PrintFlagsFinal` ou
      `jcmd`/log mostrando o `MaxRAMPercentage` configurado), e há um padrão orientado a container quando não definida.
- [ ] `docker stop` (SIGTERM) encerra a aplicação de forma graciosa dentro do prazo, sem `SIGKILL`.
      **Emenda 2026-10-07 (E1) — aprovada por Leandro Nascimento em 2026-10-07:** exit code `143` esperado; `0` observado em
      casos isolados com shutdown gracioso completo nos logs; ambos são parada limpa; `137` (SIGKILL) ou OOM é falha.
- [ ] A imagem não contém segredos (`docker history`/`docker inspect` sem valores sensíveis).
- [ ] `docker build` conclui com sucesso e `docker compose up` sobe os três serviços; `app` chega a `healthy`.
- [ ] O healthcheck do `app` não exigiu instalar pacotes adicionais na imagem.

Qualidade e documentação:

- [ ] Testes MockMvc cobrem as regras de segurança e a semântica de liveness/readiness; um teste de integração HTTP
      cobre a porta de gestão separada (se R4 for aprovada).
- [ ] `make verify` verde, `ModuleStructureTest` verde e cobertura global de linhas ≥ 80%.
- [ ] `README.md`, `DOCKER.md`, `.env.example` e `docs/Architecture.md` atualizados conforme R6/R7.
- [ ] Coleção Postman inalterada, com a justificativa registrada nesta spec (R7).
- [ ] Checkpoint de segurança registrado no `implementation-plan.md`.

## Decisões finais

1. **Porta de gestão:** `8081` separada, não publicada no Service.
2. **Readiness inclui o banco:** Confirmado (readiness = estado da aplicação + banco; liveness sem banco; WireMock fora de ambos).
3. **Estratégia de testes:** MockMvc para segurança/semântica + teste HTTP com porta aleatória OBRIGATÓRIO para provar a separação de portas e a cadeia de segurança no contexto de gestão.
4. **Porta de gestão no compose:** Não publicada no host no compose.
5. **Pinagem das imagens:** Imagens com tag completa + digest do manifest multi-arch (amd64 e arm64).
6. **Distribuição base:** Base Noble. A imagem Ubuntu 24.04 já tem o usuário `ubuntu` com UID 1000, logo será usado UID/GID numérico alto (10001).
7. **ADR:** Sem ADR novo; o ADR-008 (infra) referenciará esta feature.
8. **Responsável e revisão:** Aprovador: Leandro Nascimento · Responsável: Leandro Nascimento · Revisor de identity/SecurityConfig: Santiago Silvestre.

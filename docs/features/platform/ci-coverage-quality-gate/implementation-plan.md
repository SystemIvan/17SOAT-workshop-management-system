# Plano de Implementação: Quality gate de testes e cobertura no CI

| Campo | Valor |
|---|---|
| Feature | `ci-coverage-quality-gate` |
| Status | In Progress |
| Responsável | Time de Desenvolvimento |
| Atualizado em | 2026-08-26 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-26) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-26) |

## Objetivo e ordem de execução

Implantar um único quality gate reproduzível localmente e no GitHub Actions. O Maven decidirá sucesso ou falha de
testes, fronteiras Modulith, existência do relatório e cobertura global de linhas de pelo menos 80%. O workflow apenas
preparará Java 21, executará o Maven Wrapper e publicará evidências.

Os checkpoints devem ser executados na ordem abaixo. Somente um checkpoint ficará `In progress` por vez; o status deste
plano e as evidências serão atualizados após cada conclusão. Mudança material nas especificações aprovadas interrompe a
execução e retorna o documento afetado a `Draft`, conforme `AGENTS.md`.

## Checkpoints

### Checkpoint 1 — Confirmar linha de base e dependências de build

Status: `Completed`.

Arquivos inspecionados, sem alteração de comportamento neste checkpoint:

- `pom.xml`;
- `Makefile`;
- `.github/workflows/ci.yml`;
- `mvnw`, `.mvn/wrapper/maven-wrapper.properties` e `.mvn/wrapper/maven-wrapper.jar`;
- `target/site/jacoco/jacoco.csv`, somente se produzido por uma execução limpa deste checkpoint.

Atividades:

- confirmar Java 21 e execução pelo Maven Wrapper;
- executar `./mvnw clean verify` antes de adicionar o limite e registrar testes, falhas, skips e duração;
- calcular a cobertura global de linhas do relatório recém-gerado e confirmar se a linha de base atende a 80%;
- confirmar que `ModuleStructureTest` faz parte da suíte executada;
- validar que o Wrapper está versionado e preserva permissão de execução no Git;
- confirmar as releases e os SHAs completos das actions selecionadas para o workflow;
- confirmar a versão, licença e origem do `maven-enforcer-plugin` 3.6.3.

Critério de conclusão:

- linha de base reproduzível registrada e igual ou superior a 80%; nenhuma incompatibilidade crítica de dependência ou
  runner aberta.

Bloqueio explícito:

- se a cobertura limpa estiver abaixo de 80%, interromper a feature sem reduzir o limite, criar exclusões ou remover
  testes. O déficit deverá receber escopo e aprovação próprios.

### Checkpoint 2 — Tornar o Maven a fonte única do quality gate

Status: `Completed`.

Arquivos previstos:

- `pom.xml`;
- `Makefile`;
- `mvnw.cmd`, para compatibilidade do Wrapper versionado com PowerShell 5.1 no Windows.

Atividades:

- manter `jacoco-maven-plugin` 0.8.13 e as execuções existentes de `prepare-agent` e `report`;
- garantir que `report` permaneça em `verify` e seja declarado antes da validação;
- adicionar `jacoco:check` em `verify`, com `BUNDLE`, `LINE`, `COVEREDRATIO`, mínimo `0.80` e
  `haltOnFailure=true`;
- não adicionar `includes`, `excludes`, limite de branches ou propriedade que permita reduzir o percentual por linha de
  comando;
- adicionar `maven-enforcer-plugin` 3.6.3 depois do JaCoCo, em `verify`, com `requireFilesExist` para
  `${project.reporting.outputDirectory}/jacoco/jacoco.xml`;
- alterar `make verify` e `make coverage` para delegarem ao Wrapper com `clean verify` e manter `make test` inalterado;
- selecionar `mvnw.cmd` no Makefile para Windows e `./mvnw` nos demais sistemas operacionais;
- tornar nula-segura a resolução do diretório `.m2` no `mvnw.cmd`, preservando diretórios comuns e links simbólicos.

Verificações focadas:

- executar `./mvnw clean verify` e confirmar a ordem `report`, `check` e Enforcer;
- confirmar geração de `target/site/jacoco/index.html`, `jacoco.xml` e `jacoco.csv`;
- executar `make test`, `make coverage` e `make verify`;
- executar `./mvnw clean verify -Djacoco.skip=true` e confirmar falha por ausência de `jacoco.xml`;
- elevar temporariamente o mínimo acima da cobertura observada, executar o gate e confirmar falha pelo JaCoCo;
- desfazer a alteração temporária, revisar o diff e repetir o gate com a configuração aprovada.

Critério de conclusão:

- o mesmo build limpo passa com cobertura válida, falha abaixo de 80% e falha sem relatório, sem parsing externo e sem
  enfraquecimento da suíte.

### Checkpoint 3 — Ativar o workflow de CI

Status: `Completed`.

Arquivo previsto:

- `.github/workflows/ci.yml`.

Atividades:

- substituir integralmente o conteúdo comentado pelo workflow `CI`;
- configurar `pull_request` e `push` para `dev` e `main`;
- declarar `permissions: contents: read` e não referenciar secrets;
- configurar `concurrency` por workflow e Pull Request, com fallback para a referência Git e
  `cancel-in-progress: true`;
- criar somente o job `quality-gate`, com nome visível `Quality gate`, `ubuntu-24.04` e timeout de 30 minutos;
- fixar `actions/checkout@v6`, `actions/setup-java@v5` e `actions/upload-artifact@v7` pelos SHAs completos validados,
  mantendo comentários com as releases correspondentes;
- configurar Temurin 21 e cache Maven a partir do `pom.xml`;
- executar somente `./mvnw clean verify` como decisão do gate;
- publicar, com `if: always()`, artifacts separados de Surefire/Failsafe e JaCoCo, por 30 dias;
- usar `if-no-files-found: warn` para testes e `error` para cobertura;
- não introduzir Sonar, Maven global, MySQL, Docker, Newman, deploy, package publishing ou `pull_request_target`.

Verificações focadas:

- validar a sintaxe e a indentação do YAML;
- revisar que o workflow possui somente permissões de leitura de conteúdo;
- confirmar que o nome `Quality gate` ocorre em um único job e permanece estável;
- confirmar que não há cálculo ou parsing de percentual no YAML;
- confirmar que os paths de artifact se limitam aos relatórios autorizados.

Critério de conclusão:

- workflow estático consistente com a especificação e pronto para validação remota, sem ampliar permissões ou depender
  de infraestrutura externa.

### Checkpoint 4 — Atualizar documentação de uso e arquitetura

Status: `Completed`.

Arquivos previstos:

- `README.md`;
- `docs/Architecture.md`;
- `docs/Architecture-Decisions.md`.

Atividades:

- documentar `make verify` e `./mvnw clean verify` como formas equivalentes de executar o gate local;
- registrar cobertura global mínima de 80% de linhas e o relatório local em `target/site/jacoco/index.html`;
- explicar onde consultar `Quality gate` e baixar os artifacts no GitHub Actions;
- documentar que teste, Modulith, relatório ausente e cobertura insuficiente reprovam o gate;
- remover afirmações vigentes de que JaCoCo e CI estão ausentes, preservando o contexto histórico quando aplicável;
- retirar a ativação de JaCoCo/CI da lista de lacunas depois que o comportamento correspondente estiver validado.

Itens deliberadamente não alterados:

- OpenAPI e testes de contrato;
- coleção Postman e instruções manuais do fluxo HTTP;
- ADRs, contratos entre módulos e documentação de banco.

Critério de conclusão:

- documentação reproduz o comando e a decisão reais do build e não mantém afirmações contraditórias sobre CI ou
  JaCoCo.

### Checkpoint 5 — Executar gates locais e revisão de segurança

Status: `Completed`.

Atividades de qualidade:

- executar `make test`;
- executar `make coverage`;
- executar `make verify`;
- confirmar `ModuleStructureTest` verde;
- registrar total de testes, falhas, erros, skips, duração e cobertura global de linhas;
- confirmar que nenhum teste foi desabilitado, ignorado ou enfraquecido;
- revisar o diff completo contra a branch base e confirmar ausência de alterações fora do escopo.

Checklist de segurança:

- [x] Inputs/mass assignment — `N/A`, sem endpoint, request ou DTO alterado.
- [x] Autenticação/autorização da API — `N/A`, sem mudança de runtime ou JWT.
- [x] Código de PR — evento `pull_request`, runner efêmero e ausência de `pull_request_target`.
- [x] Permissões — somente `contents: read`; demais permissões não concedidas.
- [x] Secrets — nenhuma referência a `secrets.*`, `.env`, banco, registry ou deploy.
- [x] Dados sensíveis — artifacts restritos a relatórios, sem dados pessoais reais, dumps ou workspace amplo.
- [x] Supply chain — actions fixadas por SHA; plugin Maven com versão, origem e licença revisadas.
- [x] Persistência/SQL — `N/A`, sem banco, migration ou schema no escopo.
- [x] Erros/logs — nenhuma credencial, dado pessoal ou detalhe de ambiente incluído nas evidências.
- [x] Abuso de recursos — timeout e cancelamento de execução obsoleta configurados.
- [x] Achados críticos/altos — nenhum aberto antes de avançar ao rollout remoto.

Resultado da revisão em 2026-08-26: nenhum achado crítico ou alto. O workflow não recebe secrets, mantém somente
`contents: read`, desabilita a persistência de credenciais do checkout e limita os artifacts aos diretórios de
relatórios. O risco de supply chain foi mitigado com versões explícitas do plugin Maven e SHAs completos das actions.

Critério de conclusão:

- gates locais verdes, cobertura igual ou superior a 80%, fronteiras Modulith válidas e revisão de segurança registrada
  sem achado crítico ou alto pendente.

### Checkpoint 6 — Validar CI e aplicar proteção de branches

Status: `Pending`.

Pré-requisitos:

- checkpoints 1 a 5 concluídos;
- alterações publicadas em Pull Request da branch `chore/platform-ci-coverage-quality-gate` para `dev`;
- permissão administrativa disponível para consultar e alterar a proteção/ruleset;
- autorização do responsável pelo repositório para a alteração operacional externa.

Atividades remotas:

- confirmar execução do check `Quality gate` com Temurin 21 e Maven Wrapper no commit mais recente;
- inspecionar artifacts de testes e JaCoCo, incluindo conteúdo, nomes e retenção;
- atualizar o Pull Request com um novo commit e confirmar cancelamento da execução obsoleta;
- executar cenários negativos controlados em commit/PR de validação, sem integrá-los, para teste falho, cobertura abaixo
  do limite e relatório ausente;
- confirmar que cada cenário negativo deixa o check vermelho e preserva as evidências produzidas;
- confirmar execução do workflow em push para as branches configuradas quando o fluxo permitir esse teste;
- exigir `Quality gate`, com origem GitHub Actions, nas regras de `dev` e `main`;
- exigir atualização com a branch base e preservar regras de review, restrições e políticas de bypass existentes;
- confirmar que um PR com o check pendente ou falho não pode ser integrado.

Critério de conclusão:

- check remoto validado nos caminhos positivo e negativos, artifacts consultáveis e proteção efetivamente exigindo
  `Quality gate` em `dev` e `main`.

Dependência operacional:

- o required check só pode ser selecionado depois de uma execução recente com esse nome. Se permissões ou plano do
  GitHub impedirem a configuração, registrar a evidência e interromper a conclusão da feature; não declarar a proteção
  como aplicada sem verificação.

### Checkpoint 7 — Encerrar a feature

Status: `Pending`.

Atividades:

- revisar todos os critérios de aceite da especificação funcional;
- preencher a seção de evidências com comandos, resultados locais, URLs/IDs de runs e configuração de proteção;
- registrar resultado final da revisão de segurança e mitigações;
- confirmar novamente que OpenAPI, Postman, Flyway e seeds são `N/A` para esta feature;
- atualizar todos os checkpoints e este plano para `Implemented` somente após os gates locais e remotos passarem;
- manter especificações aprovadas e coerentes com o comportamento entregue.

Critério de conclusão:

- todos os critérios de aceite comprovados, `make verify` verde, segurança sem achado crítico/alto e required check
  ativo nas branches aprovadas.

## Checklist consolidado

- [x] Linha de base limpa confirmada com cobertura global de linhas igual ou superior a 80%.
- [x] Maven reprova cobertura abaixo de 80% sem parsing textual externo.
- [x] Maven reprova ausência do relatório JaCoCo em execução limpa.
- [x] `make verify` e `make coverage` delegam ao gate `./mvnw clean verify`.
- [ ] Workflow `CI` executa o único check `Quality gate` com Java 21 e Maven Wrapper.
- [ ] Concorrência cancela execução obsoleta sem misturar Pull Requests distintos.
- [ ] Artifacts de testes e cobertura são publicados por 30 dias, inclusive após falhas aplicáveis.
- [x] Permissões, secrets, actions e conteúdo dos artifacts passaram pela revisão de segurança local.
- [x] `make test`, `make coverage`, `make verify` e `ModuleStructureTest` estão verdes.
- [x] README e documentação de arquitetura refletem o estado implementado.
- [ ] `Quality gate` é obrigatório em `dev` e `main` e foi validado em Pull Request.
- [ ] Evidências finais e critérios de aceite estão registrados.

## Evidências de verificação

| Data | Checkpoint | Comando/verificação | Resultado | Evidência |
|---|---|---|---|---|
| 2026-08-26 | 1 | `./mvnw clean verify` antes do gate | Passou | 601 testes, 0 falhas/erros/skips; 4.033 de 4.391 linhas cobertas (91,85%) |
| 2026-08-26 | 1 | Wrapper e actions oficiais | Passou | Maven 3.9.16, Java 21.0.12; releases e SHAs completos conferidos |
| 2026-08-26 | 2 | `./mvnw clean verify -Djacoco.skip=true` | Falhou como esperado | Enforcer acusou ausência de `target/site/jacoco/jacoco.xml` |
| 2026-08-26 | 2 | limite temporário `1.00` + `./mvnw clean verify` | Falhou como esperado | JaCoCo registrou razão `0.91`, abaixo de `1.00`; mínimo restaurado para `0.80` |
| 2026-08-26 | 2 | `mvnw.cmd --version` no PowerShell 5.1 | Passou | Wrapper compatível com diretório `.m2` comum; Maven 3.9.16 sobre Java 21.0.12 |
| 2026-08-26 | 3 | revisão estática de `.github/workflows/ci.yml` | Passou | Um job `Quality gate`, permissões de leitura, sem secrets e actions fixadas por SHA |
| 2026-08-26 | 5 | `make test` | Passou em 38,528 s | 601 testes, 0 falhas, 0 erros e 0 skips |
| 2026-08-26 | 5 | `make coverage` | Passou em 50,025 s | `jacoco:report`, `jacoco:check` e `require-jacoco-report` aprovados |
| 2026-08-26 | 5 | `make verify` | Passou em 48,936 s | 601 testes; `ModuleStructureTest` com 2 testes verdes; gate completo aprovado |
| 2026-08-26 | 5 | relatórios finais | Passou | 91,85% de linhas; HTML, XML e CSV presentes em `target/site/jacoco/` |
| 2026-08-26 | 5 | `git diff --check` e revisão do diff | Passou | Sem erro de whitespace, teste alterado/desabilitado ou arquivo fora do escopo |
| 2026-08-26 | 5 | revisão de segurança | Passou | Nenhum achado crítico/alto; itens `N/A` justificados no checklist |
| 2026-08-26 | 6 | `gh auth status` e `gh pr view` | Pendente | Autenticação válida; a branch ainda não possui upstream nem Pull Request |

Evidências mínimas esperadas:

- versão de Java e Maven Wrapper;
- resumo de testes e `ModuleStructureTest`;
- percentual global de linhas e paths dos relatórios;
- falha controlada por cobertura insuficiente;
- falha controlada por relatório ausente;
- mapeamento de cada action para release e SHA completo;
- run ID/URL do check positivo e dos cenários negativos;
- nomes e conteúdo dos artifacts;
- captura ou resposta da API confirmando required check em `dev` e `main`;
- revisão de segurança final.

## Rollback ou recuperação

Não há migration, dado persistente ou deploy para reverter.

- Falha antes de ativar a proteção: corrigir o `pom.xml` ou workflow na mesma branch e repetir os gates.
- Action revogada ou incompatível: substituir somente o SHA por uma release estável revisada e revalidar o workflow.
- Workflow indisponível: reexecutar ou corrigir o job; manter o gate Maven ativo.
- Required check configurado incorretamente: corrigir o contexto/origem na regra, preservando as demais proteções.
- Cobertura legítima abaixo de 80%: interromper o rollout e cobrir o déficit em escopo aprovado; não reduzir o limite.
- Remoção emergencial do required check: somente por decisão explícita da pessoa mantenedora, com motivo e restauração
  registrados. O plano não autoriza remoção automática nem bypass.

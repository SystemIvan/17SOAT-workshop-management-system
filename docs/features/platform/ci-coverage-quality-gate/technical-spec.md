# Especificação Técnica: Quality gate de testes e cobertura no CI

| Campo | Valor |
|---|---|
| Feature | `ci-coverage-quality-gate` |
| Status | Approved |
| Responsável | Time de Desenvolvimento |
| Atualizado em | 2026-08-26 |
| Aprovado por | Ivan |
| Aprovado em | 2026-08-26 |
| Especificação funcional | `./functional-spec.md` |

## Contexto e desenho

### Estado atual

O `pom.xml` já usa Java 21 e `jacoco-maven-plugin` 0.8.13. O agente JaCoCo é anexado aos testes e a execução
`report`, vinculada à fase `verify`, produz o relatório em `target/site/jacoco`. Não existe, porém, uma execução do
goal `check`; assim, o relatório é informativo e uma cobertura abaixo de 80% não reprova o build.

O `Makefile` delega `coverage` e `verify` ao Maven Wrapper, mas não remove resultados anteriores. O arquivo
`.github/workflows/ci.yml` contém somente uma configuração comentada, baseada em Java 17, Maven global, Sonar e parsing
textual de percentual. Portanto, não há workflow ativo nem check que possa ser exigido pela proteção de branch.

### Escopo arquitetural

Esta é uma mudança transversal de plataforma. Ela não altera os bounded contexts `registration`, `servicelifecycle`,
`stockprocurement` ou `identity`, não cria dependências entre módulos e não modifica código de domínio, aplicação ou
infraestrutura da API. `ModuleStructureTest` continua dentro da suíte Maven e será executado pelo mesmo gate.

Não é necessário um novo ADR: a feature implementa a meta de cobertura e os gates já definidos em `AGENTS.md` e na
documentação de arquitetura. Uma mudança futura de métrica, percentual, escopo medido ou fornecedor externo deverá ter
especificação própria.

### Componentes e responsabilidades

| Componente | Responsabilidade |
|---|---|
| `pom.xml` | Definir a métrica, o limite e a falha do build; gerar e exigir o relatório JaCoCo. |
| `Makefile` | Expor o gate local sem duplicar a política de cobertura. |
| `.github/workflows/ci.yml` | Preparar Java 21, executar o Maven Wrapper e publicar as evidências. |
| GitHub branch protection/ruleset | Exigir o check estável para integração em `dev` e `main`. |
| `README.md` | Explicar execução local, consulta do check e download dos artifacts. |
| `docs/Architecture.md` | Remover a afirmação obsoleta de que JaCoCo e CI não estão configurados. |
| `docs/Architecture-Decisions.md` | Retirar o quality gate da lista de lacunas após sua implantação. |

O fluxo de decisão será:

```text
pull_request/push em dev ou main
        |
        v
GitHub Actions / Quality gate (Java 21)
        |
        v
./mvnw clean verify
        |
        +-- compilação ou teste falha --------------------------> check falha
        +-- ModuleStructureTest falha --------------------------> check falha
        +-- relatório JaCoCo não é gerado ----------------------> check falha
        +-- cobertura global de linhas < 0.80 ------------------> check falha
        `-- todos os critérios passam --------------------------> check passa
                                  |
                                  v
                    reports publicados como artifacts
```

### Política Maven e JaCoCo

O `pom.xml` continuará sendo a fonte única da decisão de cobertura. A configuração existente do JaCoCo será ampliada
sem exclusões de classes ou pacotes e sem cálculo paralelo no workflow:

- manter `prepare-agent` para instrumentar a suíte;
- manter `report` em `verify`, antes da validação, para produzir HTML, XML e CSV mesmo quando o limite for reprovado;
- adicionar uma execução `check`, também em `verify`, com `haltOnFailure=true`;
- aplicar uma regra ao elemento `BUNDLE`, contador `LINE`, valor `COVEREDRATIO` e mínimo literal `0.80`;
- não aplicar limite de branches nesta versão e não adicionar `includes` ou `excludes`;
- preservar `jacoco-maven-plugin` 0.8.13, evitando misturar a ativação do gate com uma atualização de dependência.

O goal `jacoco:check` pode ser ignorado quando o arquivo de dados de execução não existe. Para que essa condição nunca
produza um falso sucesso, o build adicionará `maven-enforcer-plugin` 3.6.3 depois do JaCoCo, com uma execução em
`verify` e a regra `requireFilesExist` para `${project.reporting.outputDirectory}/jacoco/jacoco.xml`. A ordem dos
plugins no `pom.xml` será JaCoCo, Enforcer e Spring Boot; dentro do JaCoCo, `report` precederá `check`.

`maven-enforcer-plugin` é uma dependência de build, não da aplicação. Nenhuma biblioteca será acrescentada ao classpath
de produção ou teste. A regra verificará o XML porque ele é um arquivo estável e próprio para automação; a existência do
HTML será coberta pela publicação e pela verificação das evidências do workflow.

Para impedir que um relatório antigo satisfaça a regra de existência, o comando canônico passará a ser
`./mvnw clean verify`. Os targets `make verify` e `make coverage` delegarão a esse comando. O workflow executará o mesmo
comando diretamente; flags que alterem `jacoco.skip`, testes ou regras de cobertura não serão usadas.

### Workflow do GitHub Actions

`.github/workflows/ci.yml` será substituído por um workflow ativo com estas decisões:

| Item | Definição |
|---|---|
| Nome do workflow | `CI` |
| ID do job | `quality-gate` |
| Nome visível/check | `Quality gate` |
| Eventos | `pull_request` e `push` para `dev` e `main` |
| Runner | GitHub-hosted `ubuntu-24.04` |
| Timeout | 30 minutos |
| Java | Eclipse Temurin 21 |
| Cache | Dependências Maven, com chave derivada pelo `setup-java` a partir do `pom.xml` |
| Comando | `./mvnw clean verify` |
| Permissões | Somente `contents: read`; todas as demais permanecem sem acesso |
| Secrets | Nenhum |

As famílias estáveis selecionadas em 2026-08-26 são `actions/checkout@v6`, `actions/setup-java@v5` e
`actions/upload-artifact@v7`. No workflow, cada action será fixada pelo SHA completo da release revisada, com comentário
indicando a versão legível correspondente. Assim, uma tag móvel não poderá alterar o código executado sem revisão. A
atualização posterior desses SHAs será uma mudança explícita de manutenção.

No nível do workflow, `concurrency` agrupará execuções pelo nome do workflow e pelo número do Pull Request, usando a
referência Git como fallback para `push`. `cancel-in-progress: true` cancelará a execução anterior da mesma proposta ou
branch quando chegar um commit mais novo. Pull Requests diferentes não compartilharão o mesmo grupo.

O workflow não usará `pull_request_target`, ambientes, containers, serviços, MySQL, Docker Compose, Newman, Sonar,
deploy ou publicação de pacotes. O checkout manterá a profundidade padrão porque o gate não calcula diff nem depende do
histórico Git.

### Evidências e artifacts

As etapas de upload usarão `if: always()` para ainda executar depois de falha de teste ou cobertura, desde que o runner
não tenha sido encerrado antes de produzir arquivos. Serão publicados dois artifacts, com `run_id` e `run_attempt` no
nome para evitar colisões:

| Artifact | Conteúdo | Ausência | Retenção |
|---|---|---|---|
| `test-reports-*` | `target/surefire-reports/**` e `target/failsafe-reports/**` | `warn` | 30 dias |
| `jacoco-report-*` | `target/site/jacoco/**` | `error` | 30 dias |

A ausência de relatórios de teste pode ser esperada quando compilação ou resolução de dependências falha, e o comando
Maven já terá reprovado o job. A ausência do relatório JaCoCo será erro também no upload, como defesa adicional; a regra
Maven Enforcer é a garantia principal e mantém a mesma decisão no ambiente local.

Somente relatórios serão publicados. Não serão incluídos `target/classes`, JARs, dumps do processo, cache Maven,
arquivos `.env`, logs da aplicação ou qualquer diretório amplo do workspace.

### Proteção de branches

Depois que o workflow produzir ao menos uma execução válida, uma pessoa com permissão administrativa configurará a
proteção/ruleset de `dev` para exigir o contexto exato `Quality gate` antes do merge. Como `main` existe e é a branch
padrão/de release do repositório, a mesma exigência será aplicada a ela nesta feature.

A configuração deverá preservar as regras de revisão e demais proteções já existentes, exigir que a proposta esteja
atualizada com a branch base e selecionar GitHub Actions como origem esperada do check, quando a interface do
repositório oferecer essa opção. O nome `Quality gate` passa a ser contrato operacional: renomeá-lo exige primeiro
coordenar a alteração do required check para não bloquear ou liberar merges indevidamente.

O trigger de `push` mantém evidência para pushes diretos autorizados por exceções já existentes, mas não cria nem amplia
bypass. Se merge queue for habilitada no futuro, o evento `merge_group` deverá ser adicionado antes de tornar esse fluxo
obrigatório; merge queue não faz parte desta entrega.

### Documentação afetada

Na implementação, `README.md` deverá documentar:

- Java 21 como pré-requisito e `make verify`/`./mvnw clean verify` como reprodução local do gate;
- a regra global de pelo menos 80% de linhas e o caminho local `target/site/jacoco/index.html`;
- o check `Quality gate`, a página Actions e os artifacts de testes e cobertura;
- que falhas de teste, Modulith, relatório ausente ou cobertura insuficiente impedem aprovação.

`docs/Architecture.md` e `docs/Architecture-Decisions.md` deverão ser alinhados ao estado implementado, sem reescrever o
histórico de decisões. Não há mudança de endpoint, request, response, validação HTTP ou status code; portanto, OpenAPI,
Postman e as instruções manuais do fluxo da API não serão alterados.

## Interfaces e fluxo de dados

### Contratos alterados

Não há contrato HTTP, evento de domínio, porta de aplicação ou interface entre bounded contexts. Os contratos desta
feature são de build e operação:

| Contrato | Entrada | Saída/sucesso | Falha |
|---|---|---|---|
| Gate local | Java 21 e workspace do projeto | Exit code 0 de `./mvnw clean verify` | Exit code diferente de 0 |
| Cobertura | Classes e testes executados pelo JaCoCo | `LINE/COVEREDRATIO >= 0.80` no `BUNDLE` | Violação do JaCoCo |
| Relatório | Execução JaCoCo válida | `target/site/jacoco/jacoco.xml` existente | Violação do Enforcer |
| Check remoto | Commit de PR/push em `dev` ou `main` | Check `Quality gate` bem-sucedido | Check failure/cancelled |
| Evidência | Arquivos gerados no runner | Artifacts de testes e cobertura | Upload sinaliza ausência |

O exit code do Maven será propagado diretamente pelo shell do runner. O workflow não fará parsing de logs, HTML, XML
ou CSV para decidir a cobertura e não converterá falha em sucesso com `continue-on-error`.

### Compatibilidade

A alteração é compatível com consumidores da API e com o banco. A mudança deliberada de comportamento é que
`./mvnw verify`, `make verify` e `make coverage` passarão a falhar quando a cobertura global de linhas estiver abaixo de
80% ou quando o relatório obrigatório não existir. `make test` continuará executando somente a fase `test` e não será o
gate oficial de cobertura.

## Persistência e dados de bootstrap

Classificação: **no seed required**.

A feature não cria ou altera schema, entidades JPA, migrations Flyway, dados de referência, dados de demonstração ou
fixtures de domínio. O gate usa os testes existentes com H2 e não inicia MySQL. Nenhum arquivo será escrito fora de
`target/` durante o build local e dos diretórios temporários/cache administrados pelo runner no CI.

## Segurança e operação

### Revisão de segurança do desenho

| Tema | Avaliação e mitigação |
|---|---|
| Input e mass assignment | N/A: nenhum endpoint ou DTO é criado. |
| Autenticação/autorização da API | N/A: a aplicação não é iniciada como serviço e contratos JWT não mudam. |
| Código não confiável de PR | Usar `pull_request`, runner efêmero, `contents: read` e nenhum secret. |
| Permissões GitHub | Declarar somente `contents: read`; não escrever checks, PRs, packages ou deployments. |
| Supply chain | Fixar actions por SHA e a versão do novo plugin Maven; revisar origem e licença. |
| Dados sensíveis | Publicar somente relatórios; testes usam fixtures e não podem conter dados pessoais reais. |
| Secrets e credenciais | Não referenciar `secrets.*`, `.env`, banco, registry ou ambiente de deploy. |
| Logs e artifacts | Não incluir dumps, workspace inteiro, classes, JAR ou cache; retenção de 30 dias. |
| SQL e migrations | N/A: nenhuma mudança de persistência e nenhum banco externo no job. |
| Erros e disclosure | Logs mostram falhas de build/teste, sem stack trace HTTP ou segredo de aplicação. |
| Abuso de recursos | Timeout de 30 minutos, cancelamento de execuções obsoletas e um único job. |

Não há achado crítico ou alto conhecido no desenho. A revisão final deverá confirmar os SHAs das actions, a versão do
Enforcer resolvida pelo Wrapper, a ausência de secrets no contexto e o conteúdo real dos artifacts antes de registrar a
feature como implementada.

### Rollout

A ativação seguirá esta ordem após aprovação desta especificação e criação do plano:

1. configurar e validar o gate Maven localmente;
2. ativar o workflow e obter uma execução bem-sucedida;
3. revisar artifacts e permissões da execução;
4. configurar `Quality gate` como required check em `dev` e `main`;
5. verificar, em Pull Request controlado, os caminhos de sucesso e falha;
6. atualizar a documentação e registrar as evidências no plano.

Se a linha de base legítima estiver abaixo de 80%, o rollout será interrompido. O limite não será reduzido e testes não
serão removidos; o déficit deverá ser corrigido em escopo aprovado antes da proteção obrigatória.

### Recuperação

Uma falha operacional do GitHub Actions não justifica desabilitar o gate no `pom.xml`. A recuperação preferencial é
corrigir ou reexecutar o workflow. Se um SHA de action for revogado ou incompatível, ele será atualizado por mudança
revisada. Qualquer remoção temporária do required check exige decisão explícita da pessoa mantenedora e registro do
motivo; não será automatizada por esta feature.

## Estratégia de testes

### Verificações do build

- executar `./mvnw clean verify` com Java 21 e confirmar sucesso da suíte completa, do `ModuleStructureTest`, do
  relatório e do limite;
- confirmar no log a execução ordenada de `jacoco:report`, `jacoco:check` e da regra de existência do Enforcer;
- confirmar que HTML, XML e CSV são gerados em `target/site/jacoco`;
- calcular a cobertura de linhas do XML/CSV somente como evidência, sem usar esse cálculo como decisão do gate;
- realizar teste negativo controlado com limite temporariamente superior à cobertura observada, sem versionar a
  alteração, e confirmar exit code diferente de zero;
- realizar teste negativo com JaCoCo desabilitado em uma execução limpa e confirmar que a ausência do XML reprova pelo
  Enforcer;
- verificar que `make test` continua verde e que `make verify` e `make coverage` executam o gate limpo.

Nenhum teste existente será alterado apenas para acomodar o percentual. Uma falha funcional revelada pela suíte será
tratada separadamente, conforme a exclusão de escopo da especificação funcional.

### Verificações do workflow

- validar a sintaxe do YAML e revisar que há apenas um job/check chamado `Quality gate`;
- abrir ou atualizar Pull Request para `dev` e confirmar execução com Temurin 21 e Maven Wrapper;
- confirmar que um novo commit cancela a execução anterior do mesmo Pull Request;
- confirmar execução em push para `dev` e, quando aplicável ao fluxo, `main`;
- inspecionar os dois artifacts em uma execução bem-sucedida e seus nomes, conteúdo e retenção;
- provocar falha controlada de teste e confirmar check vermelho e publicação dos relatórios já produzidos;
- provocar falha controlada de cobertura e confirmar check vermelho sem parsing textual, com relatório publicado;
- confirmar que um relatório JaCoCo ausente nunca resulta em check verde;
- revisar na execução que o token possui somente leitura de conteúdo e que nenhum secret foi disponibilizado;
- confirmar nas regras do repositório que `Quality gate` é obrigatório e associado à origem esperada.

As alterações negativas usadas na validação serão temporárias e não integrarão a branch. Evidências, data, commit e
resultado de cada verificação serão registradas no `implementation-plan.md` somente após sua criação ser autorizada
pelas aprovações exigidas.

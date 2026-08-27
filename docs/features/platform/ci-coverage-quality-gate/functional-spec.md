# Especificação Funcional: Quality gate de testes e cobertura no CI

| Campo | Valor |
|---|---|
| Feature | `ci-coverage-quality-gate` |
| Status | Approved |
| Responsável | Time de Desenvolvimento |
| Atualizado em | 2026-08-26 |
| Aprovado por | Ivan |
| Aprovado em | 2026-08-26 |
| Referências | Requisito oficial de cobertura; regras e arquivos vigentes listados abaixo |

Referências detalhadas:

- Tech Challenge — cobertura automatizada mínima de 80% nos domínios críticos;
- `AGENTS.md` — seção “Testing and quality gates”;
- `docs/Architecture.md` — requisito de cobertura mínima de 80%;
- `.github/workflows/ci.yml`, `pom.xml` e `Makefile`.

## Problema e resultado esperado

O projeto gera um relatório JaCoCo durante `verify`, mas o build não aplica um limite mínimo de cobertura. Portanto, uma
regressão abaixo dos 80% exigidos pode concluir com sucesso. O workflow existente em `.github/workflows/ci.yml` está
desabilitado e diverge da configuração vigente do projeto: referencia Java 17, Maven global e uma leitura textual da
cobertura, enquanto o projeto exige Java 21 e execução pelo Maven Wrapper.

Também não existe hoje um status check ativo e obrigatório que impeça a integração de código quando testes, verificação
estrutural ou cobertura falham. A equipe depende de execução local e revisão manual para aplicar uma regra que deveria
ser repetível e objetiva.

O resultado esperado é que toda mudança proposta para as branches de integração protegidas seja validada
automaticamente pelo mesmo quality gate disponível localmente. A validação deve executar a suíte completa, verificar a
arquitetura modular e reprovar o build quando a cobertura de linhas do código de produção ficar abaixo de 80%. O
resultado e os relatórios devem permanecer consultáveis no GitHub para revisão e evidência da entrega.

## Atores e cenários

- **Pessoa desenvolvedora:** abre ou atualiza um Pull Request e recebe uma validação automática, sem precisar reproduzir
  manualmente uma regra exclusiva do GitHub.
- **Pessoa revisora:** consulta um único status check para saber se testes, estrutura modular e cobertura mínima foram
  aprovados, e acessa os relatórios gerados quando precisa investigar uma falha ou revisar evidências.
- **Pessoa mantenedora do repositório:** protege as branches de integração para impedir merge enquanto o quality gate
  obrigatório não estiver aprovado.
- **Equipe avaliadora:** encontra evidência reproduzível de que a suíte foi executada com Java 21 e de que a cobertura
  mínima exigida é efetivamente aplicada.

Cenários ponta a ponta:

1. Ao abrir ou atualizar um Pull Request destinado a uma branch protegida, o CI executa automaticamente o quality gate
   sobre o commit mais recente da proposta.
2. Quando algum teste falha, inclusive a verificação de fronteiras do Spring Modulith, o status check falha e o merge é
   bloqueado.
3. Quando todos os testes passam, mas a cobertura de linhas fica abaixo de 80%, o status check falha e informa que o
   limite mínimo não foi atendido.
4. Quando testes, estrutura modular e cobertura são aprovados, o status check conclui com sucesso e os relatórios de
   testes e cobertura ficam disponíveis para consulta.
5. Quando um novo commit substitui uma execução ainda em andamento no mesmo Pull Request, a validação anterior pode ser
   cancelada e somente o resultado correspondente ao commit mais recente pode liberar o merge.
6. Ao executar o quality gate local recomendado pelo projeto, a pessoa desenvolvedora recebe a mesma decisão de
   aprovação ou falha aplicada pelo CI.
7. Um push que alcance diretamente uma branch de integração também é validado, preservando evidência mesmo quando a
   política do repositório permitir uma exceção operacional ao fluxo normal de Pull Request.

## Regras de negócio

- O limite mínimo inicial é **80% de cobertura de linhas** sobre o conjunto de código de produção medido pelo JaCoCo.
- A política de cobertura pertence ao build do projeto e deve ser aplicada tanto localmente quanto no CI; o workflow não
  mantém um segundo cálculo independente nem interpreta texto do relatório para decidir aprovação.
- O quality gate oficial é a execução completa de `verify` pelo Maven Wrapper com Java 21. Não é permitido depender de
  uma instalação global de Maven.
- Nenhum teste pode ser desabilitado, ignorado ou enfraquecido para obter aprovação do workflow ou da cobertura.
- Falha de compilação, teste, verificação estrutural ou cobertura torna o status check inválido para merge.
- O relatório de cobertura e os relatórios de testes devem ser publicados como evidência da execução, inclusive quando
  a cobertura reprovar, sempre que os arquivos tiverem sido produzidos.
- A ausência inesperada do relatório JaCoCo em uma execução que alcançou a etapa de cobertura deve ser tratada como
  falha de validação, não como cobertura aprovada.
- A validação de Pull Request usa apenas permissões de leitura do conteúdo e não depende de secrets de aplicação,
  credenciais de banco, tokens de publicação ou acesso a ambientes de deploy.
- O workflow deve usar um nome de status check único e estável para que a proteção de branch não fique ambígua.
- A proteção deve exigir o status check no mínimo na branch `dev`. A aplicação da mesma regra em `main`, caso ela receba
  integrações ou releases, faz parte da configuração operacional da feature.
- Uma média global acima de 80% não autoriza reduzir deliberadamente testes de regras críticas. A revisão continua
  responsável por exigir cobertura adequada do código novo ou alterado; cobertura diferencial automatizada poderá ser
  avaliada separadamente.
- A ativação do gate não autoriza adicionar exclusões JaCoCo apenas para elevar artificialmente o percentual. Qualquer
  exclusão futura deve ter justificativa técnica explícita e revisão humana.

## Fora de escopo

- Executar o fluxo E2E Docker Compose + Newman como parte obrigatória de todo Pull Request; ele poderá usar workflow
  separado, manual, agendado ou orientado à entrega.
- Adicionar SonarCloud, Codecov ou outro serviço externo de qualidade/cobertura.
- Exigir 80% de cobertura de branches nesta primeira versão. Essa métrica deve continuar visível para evolução, mas não
  deve tornar o gate inicial incompatível com a linha de base atual sem trabalho de testes previamente aprovado.
- Implementar cobertura diferencial por linhas alteradas.
- Executar deploy, publicar imagem Docker, pacote Maven ou release.
- Introduzir banco MySQL, fornecedor externo real ou qualquer secret no job de validação de Pull Request.
- Corrigir regras de negócio, testes funcionais ou débitos de arquitetura não relacionados que venham a ser revelados
  pela primeira execução do workflow; esses achados devem ser tratados em escopo próprio.
- Produzir o relatório de vulnerabilidades ou adicionar scanners de dependência, código ou imagem; segurança de supply
  chain e vulnerability scan devem ser planejados em workflow/checkpoint próprios.

## Critérios de aceite

- [ ] Abrir ou atualizar um Pull Request para `dev` inicia automaticamente um status check único e identificável.
- [ ] O status check executa o build com Java 21 e pelo `./mvnw`, sem usar Maven global.
- [ ] O mesmo comando de quality gate usado no CI pode ser executado localmente e produz a mesma aprovação ou falha.
- [ ] Uma falha em qualquer teste automatizado, incluindo `ModuleStructureTest`, faz o status check falhar.
- [ ] Cobertura de linhas total igual ou superior a 80% permite que a etapa de cobertura seja aprovada.
- [ ] Cobertura de linhas total inferior a 80% faz o build e o status check falharem sem depender de parsing textual do
      relatório.
- [ ] O relatório HTML/XML do JaCoCo e os relatórios de testes produzidos ficam disponíveis como artifact da execução.
- [ ] A ausência inesperada do relatório de cobertura não resulta em status check aprovado.
- [ ] O workflow de Pull Request possui somente permissão de leitura do conteúdo e executa sem secrets de aplicação ou
      deploy.
- [ ] Um novo commit no mesmo Pull Request não pode ser liberado pelo resultado bem-sucedido de um commit anterior.
- [ ] Pushes em `dev` também executam o quality gate e preservam sua evidência.
- [ ] A branch `dev` exige o status check bem-sucedido antes do merge; `main` recebe a mesma proteção caso faça parte do
      fluxo de integração ou release do repositório.
- [ ] A configuração não adiciona exclusões de cobertura sem justificativa e não desabilita ou enfraquece testes.
- [ ] A documentação do projeto deixa de afirmar que JaCoCo ou CI estão ausentes e passa a explicar como executar o gate
      localmente, localizar o resultado no GitHub e consultar os artifacts.

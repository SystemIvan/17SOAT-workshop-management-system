# Adequação do README e do gatilho de CD

Mudança de manutenção solicitada por Matheus em 2026-10-31: tornar os testes automatizados visíveis no README e
executar o CD nas atualizações da `dev`, incluindo merges de PRs. Sem alteração de aplicação, contratos ou dados.

## Checkpoints

- [x] Incluir comandos de testes, cobertura mínima e links para os guias no README.
- [x] Alterar somente a branch do gatilho de CD para `dev` e alinhar a documentação de operação.
- [x] Revisar a diff e validar o workflow localmente com as ferramentas disponíveis.
- [x] Executar `make test` e `make verify`, registrando os resultados.
- [x] Realizar a revisão de segurança descrita abaixo.

## Segurança

- O CD continua restrito a `push`, agora na `dev`; PRs não recebem um novo gatilho com acesso a secrets.
- Permissões, actions fixadas por SHA, dependências entre jobs e environment `kind-ci` permanecem iguais.
- O environment `kind-ci` requer configuração dos secrets JWT e HMAC no repositório e autorização da branch `dev`
  caso existam restrições de branches no environment. A configuração remota não foi alterada nesta manutenção.
- Recomenda-se exigir PR pela proteção da `dev`; o workflow também executa em pushes diretos permitidos pelo GitHub.
- N/A: validação de entrada HTTP, autorização da API, persistência, migrações e exposição de dados de negócio,
  pois não há alteração nesses componentes. Nenhuma dependência ou segredo foi adicionado.
- Nenhum achado crítico ou alto identificado na alteração.

## Evidências

- `git diff --check`: passou.
- YAML validado com PyYAML (`BaseLoader`): gatilho exclusivamente `push` em `dev`; comparação com `HEAD` confirmou
  que jobs, permissões, dependências e environment não mudaram. `actionlint` não está instalado no ambiente.
- Links locais do README e do guia de CI/CD: destinos existentes.
- `make test`: passou fora do sandbox, 936 testes, zero falhas, erros ou testes ignorados. A primeira execução no
  sandbox teve três erros de inicialização do WireMock por bloqueio de sockets; a reexecução não alterou a suíte.
- `make verify`: passou fora do sandbox, 936 testes, zero falhas, erros ou testes ignorados; relatório JaCoCo
  gerado e gate de cobertura aprovado. Cobertura global de linhas: 95,28% (5.695 de 5.977).
- `ModuleStructureTest`: dois testes passaram, sem falhas, erros ou testes ignorados.

A publicação no GHCR e o smoke test no kind serão executados pelo GitHub Actions após a integração na `dev`;
não são executados como parte desta manutenção local.

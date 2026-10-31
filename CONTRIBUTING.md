# Contribuindo

Leia o [AGENTS.md](AGENTS.md) antes de alterar a aplicação: ele define as regras de arquitetura, o fluxo de
especificação (SDD), os testes exigidos, a revisão de segurança e as convenções de Git. Este arquivo reúne só os
comandos do dia a dia.

## Comandos de desenvolvimento

```bash
make test
make coverage
make verify
make run-dev
```

`make verify` e `make coverage` executam o quality gate completo por meio de `./mvnw clean verify`. O build reprova
quando a suíte, o `ModuleStructureTest`, a geração do relatório JaCoCo ou a cobertura global de linhas falha. O limite
mínimo é 80%, definido no `pom.xml`; o workflow não mantém um cálculo separado. Depois de uma execução local, abra
`target/site/jacoco/index.html` para consultar o relatório de cobertura.

## Fluxo de features

As especificações de features ficam em `docs/features/`. Alterações de contratos HTTP devem atualizar as expectativas
do OpenAPI gerado e a coleção em `docs/api/postman/workshop-management-system.postman_collection.json`.

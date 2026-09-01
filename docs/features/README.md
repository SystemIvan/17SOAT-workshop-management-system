# Especificações de Features

Use um diretório para cada feature não trivial, agrupado por bounded context:
`docs/features/<bounded-context>/<feature-slug>/`. Use `platform` para mudanças transversais que não pertencem a um
bounded context.

Os documentos são criados em português e de forma sequencial:

1. Crie `functional-spec.md` com comportamento desejado e critérios de aceite.
2. Obtenha aprovação humana explícita da spec funcional e registre responsável/data.
3. Somente então crie `technical-spec.md` com arquitetura, contratos, persistência, segurança e testes.
4. Obtenha aprovação humana explícita da spec técnica e registre responsável/data.
5. Somente então crie `implementation-plan.md` com checkpoints executáveis e evidências.

Os status permitidos são `Draft`, `Approved`, `In Progress` e `Implemented`. Um agente não pode inferir aprovação nem
aprovar em nome de uma pessoa. Mudança material em spec aprovada devolve seu status para `Draft` e invalida documentos
posteriores até nova aprovação e revisão.

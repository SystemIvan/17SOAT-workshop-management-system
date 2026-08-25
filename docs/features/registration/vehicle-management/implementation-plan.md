# Plano de Implementação: Gestão de Vehicles

| Campo | Valor |
|---|---|
| Feature | `vehicle-management` — `SCRUM-7`, `SCRUM-36`, `SCRUM-35` e `SCRUM-37` |
| Status | Quatro stories implementadas, aceitas e integradas em `dev` |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Especificação funcional | `SCRUM-35`: 2026-08-22; `SCRUM-37`: `Approved` por Ivan em 2026-08-23 |
| Especificação técnica | `SCRUM-35`: 2026-08-22; `SCRUM-37`: `Approved` por Ivan em 2026-08-23 |
| Autorização para codificar | `SCRUM-35`: 2026-08-22; `SCRUM-37`: 2026-08-23 |
| Aceite manual | `SCRUM-35` e `SCRUM-37`: Ivan Pimentel em 2026-08-23 |
| Baseline de fechamento | Branch em `5c96181`; árvore reconciliada verificada com 387 testes verdes |
| Escopo desta revisão | Plano de implementação da `SCRUM-37` |
| Integração em `dev` | `SCRUM-7`/`SCRUM-36`: PR #12 (`d21fd3f`); `SCRUM-35`/`SCRUM-37`: PR #26 (`6b9f223`) |

## Estado integrado em `dev`

Todos os checkpoints das quatro stories foram concluídos e aceitos. A PR #12 integrou cadastro e atualização
descritiva; a PR #26 integrou quilometragem, consultas, archive e elegibilidade para novas Service Orders. A iniciativa
JWT posterior restringiu `/api/vehicles/**` aos papéis `MANAGER` e `ADMIN`, resolvendo a dívida registrada nas revisões
originais.

As seções anteriores ao plano da `SCRUM-36` registram exclusivamente a implementação concluída da `SCRUM-7`. O plano
da `SCRUM-36` começa em “SCRUM-36 — atualização descritiva e de chassis” e está concluído.

O plano da `SCRUM-35` começa em “SCRUM-35 — quilometragem de Vehicle”. Ivan aprovou o plano e autorizou o código em
2026-08-22; a implementação foi aceita em 2026-08-23 e registrada no commit local `4f7d346`.

O plano da `SCRUM-37` começa em “SCRUM-37 — arquivamento, consultas e elegibilidade para novo trabalho”. A funcional,
a técnica e o plano foram aprovados em 2026-08-23; Ivan autorizou explicitamente o início do código na mesma data.

## Objetivo e restrições

Implementar somente o cadastro de Vehicle da `SCRUM-7`, associado pelo UUID `customerId` a um Customer ativo. O recurso
Vehicle expõe seu identificador próprio como `id`; `vehicleId` será usado apenas quando outro contrato referenciar esse
recurso, seguindo o padrão já aplicado a Customer e à collection Postman.

Chassis permanece opcional. Atualização descritiva, quilometragem, arquivamento, consultas adicionais e integração com
Service Order pertencem às stories seguintes e não serão antecipados.

Nenhum checkpoint de código começa enquanto este plano estiver `Draft`.

## Checkpoint 0 — preparar a baseline

- [x] Confirmar que o PR #12 continua no head `027cc75` ou identificar como Customer entrou em `dev`.
- [x] Se o PR #12 ainda estiver aberto, manter Vehicle como branch empilhada e usar Customer como futura base de PR.
- [x] N/A — Customer não entrou por merge commit durante esta implementação.
- [x] N/A — Customer não entrou por squash/rebase durante esta implementação.
- [x] Confirmar worktree limpa e executar o equivalente a `make test` como evidência de baseline.

Evidência esperada: SHA, relação com `dev`, estado do PR #12 e resultado da suíte antes da primeira mudança.

## Checkpoint 1 — implementar e testar o domínio

- [x] Criar testes unitários de `LicensePlate` para legado, hífen opcional, Mercosul, normalização e formatos inválidos.
- [x] Criar testes unitários de `ChassisNumber` para presença opcional, normalização, tamanho e caracteres inválidos.
- [x] Criar testes de `VehicleYear` com ano atual controlado e os limites 1886 e `currentYear + 1`.
- [x] Criar testes de `Vehicle` para `id`, `customerId`, descrições obrigatórias/normalizadas e `active=true`.
- [x] Implementar `Vehicle`, `LicensePlate`, `ChassisNumber` e `VehicleYear` livres de Spring, JPA e HTTP.
- [x] Manter chassis ausente sem objeto sentinela e sem string vazia.

Evidência esperada: testes de domínio verdes e cobertura de todas as invariantes e limites aprovados.

## Checkpoint 2 — implementar aplicação e consistência com Customer

- [x] Adicionar `CustomerRepository.findByIdForUpdate(UUID)` sem alterar as consultas existentes.
- [x] Implementar a leitura JPA com `PESSIMISTIC_WRITE` e teste de Customer ativo/arquivado.
- [x] Criar a porta mínima `VehicleRepository` com verificações de placa/chassis e `save`.
- [x] Criar `CreateVehicleRequest`, `VehicleResponse` com `id` e `VehicleMapper`.
- [x] Criar as exceções de duplicidade de placa e chassis.
- [x] Configurar `Clock` injetável e usar `Clock.fixed` nos testes.
- [x] Implementar `CreateVehicleUseCase` transacional na precedência aprovada.
- [x] Testar sucesso com/sem chassis, Customer ausente/arquivado, duplicidades e ausência de save parcial.
- [x] Demonstrar que Customer não é alterado nem recebe coleção de Vehicles.

Evidência esperada: testes de aplicação verdes, lock limitado à transação e dependência acíclica dentro de
`registration`.

## Checkpoint 3 — criar migration e adapter de persistência

- [x] Gerar timestamp UTC e criar `V<timestamp>__create_vehicles.sql` sem alterar migrations existentes.
- [x] Criar `vehicles` com UUID binário, FK para `customers.id`, índice por `customer_id` e sem cascade.
- [x] Criar constraints nomeadas para placa e chassis informado, permitindo múltiplos chassis `NULL`.
- [x] Criar `VehicleJpaEntity`, mapper, Spring Data repository e adapter da porta de domínio.
- [x] Usar `saveAndFlush` e traduzir somente as duas constraints únicas conhecidas.
- [x] Testar round-trip canônico, chassis presente/ausente, múltiplos `NULL`, FK e unicidade concorrente-like.
- [x] Confirmar Flyway e Hibernate `ddl-auto=validate` em banco de teste vazio.
- [x] Confirmar classificação de dados: **nenhum seed requerido**.

Evidência esperada: migration aplicada, mapeamento validado e falhas de integridade traduzidas sem detalhes internos.

## Checkpoint 4 — expor o contrato HTTP

- [x] Criar `POST /api/vehicles` com constructor injection e DTO validado.
- [x] Retornar `201`, `Location: /api/vehicles/{id}` e `VehicleResponse` com campo `id`.
- [x] Retornar chassis `null` quando omitido e valores canônicos quando presente.
- [x] Criar `VehicleExceptionHandler` restrito ao controller.
- [x] Implementar códigos `INVALID_VEHICLE`, `CUSTOMER_NOT_FOUND`, `CUSTOMER_ARCHIVED`,
  `VEHICLE_LICENSE_PLATE_ALREADY_EXISTS` e `VEHICLE_CHASSIS_ALREADY_EXISTS`.
- [x] Testar payloads válidos e inválidos, Customer, duplicidades, precedência e ausência de exposição interna.

Evidência esperada: testes MockMvc verdes para requests, response, `Location`, status e códigos estáveis.

## Checkpoint 5 — sincronizar contratos e documentação

- [x] Adicionar anotações Springdoc completas ao endpoint e aos DTOs.
- [x] Atualizar `OpenApiContractTest` para path, schemas, chassis nullable, campo `id` e respostas.
- [x] Atualizar a collection Postman com pasta Vehicle e exemplos com/sem chassis.
- [x] Fazer o script de cadastro salvar `pm.response.json().id` na variável `vehicleId` da collection.
- [x] Validar a collection com parser JSON.
- [x] Atualizar `docs/PROJECT-STRUCTURE.md`; a narrativa arquitetural legada permanece dívida já registrada.
- [x] Não criar OpenAPI YAML manual nem alterar Service Order nesta story.

Evidência esperada: `/v3/api-docs` como fonte de verdade, teste de contrato verde e collection Postman válida.

## Checkpoint 6 — segurança, qualidade e validação final

- [x] Executar a revisão de segurança abaixo e registrar o resultado de cada item.
- [x] Executar o equivalente a `make test` com o Maven provisionado pelo wrapper.
- [x] Executar o equivalente a `make verify` e confirmar zero falhas, erros ou skips inadequados.
- [x] Confirmar `ModuleStructureTest` e exatamente três módulos.
- [x] Gerar o JaCoCo pelo `verify` e revisar cobertura do slice alterado, sem regressão.
- [x] Validar manualmente no MySQL: normalização, chassis `NULL`, unicidade, FK, lock e resposta HTTP.
- [x] Atualizar critérios implementados nas specs e evidências deste plano.
- [x] Marcar a feature `Implemented` somente após todos os gates e achados críticos/altos resolvidos.

## Revisão de segurança

| Item | Verificação e mitigação planejada | Estado |
|---|---|---|
| Input e mass assignment | DTO fechado; sem `id`/`active`; validação na borda e no domínio | Pass |
| Autenticação/autorização | Resolvido posteriormente: JWT exige `MANAGER` ou `ADMIN` | Pass |
| Exposição de dados | Response contém somente Vehicle e UUIDs; não expõe contato nem `TaxId` do Customer | Pass |
| Segredos e logs | Nenhum segredo/dependência ou log novo; erros não repetem placa, chassis ou IDs | Pass |
| SQL e migration | JPA parametrizado, Flyway aditivo, FK `NO ACTION` e constraints nomeadas verificadas | Pass |
| Concorrência | `FOR UPDATE` curto no Customer e unique constraints como proteção autoritativa | Pass |
| Erros | Códigos estáveis verificados sem SQL, constraint, tipo interno ou stack trace no response | Pass |
| Dependências | N/A: nenhuma dependência nova adicionada | N/A |
| Abuso e enumeração | Payload limitado e endpoints protegidos por `MANAGER`/`ADMIN` | Pass |

Achados críticos ou altos impediriam a conclusão. A revisão foi concluída sem achados críticos ou altos; itens `N/A`
permanecem justificados na tabela.

## Evidências de verificação

| Evidência | Resultado |
|---|---|
| Baseline e relação com PR #12/`dev` | Branch empilhada em `027cc75`; PR #12 permaneceu pendente e sem merge |
| Testes de domínio | Value objects e aggregate verdes na suíte focada e completa |
| Testes de aplicação | Fluxos, precedência e ausência de save parcial verdes |
| Migration e persistência | Flyway/Hibernate verdes; H2 e MySQL validaram constraints, FK e chassis `NULL` |
| MockMvc e OpenAPI | Endpoint, schemas, responses, `Location`, `id` e chassis nullable verdes |
| Postman JSON | Parser JSON verde; exemplos com/sem chassis e captura de `vehicleId` |
| `make test` | Equivalente Maven: 112 testes, zero falhas, erros ou skips |
| `make verify` | Equivalente Maven: build, JAR e JaCoCo concluídos com sucesso |
| `make coverage` | Vehicle 97,80% de linhas; projeto 72,68%, déficit global preexistente |
| `ModuleStructureTest` | 2 testes verdes; exatamente três módulos e fronteiras válidas |
| Validação manual MySQL | MySQL 8.0.46: migration, normalização, dois `NULL`, unicidade, FK e HTTP validados |
| Revisão de segurança | Concluída; nenhum achado crítico ou alto |
| Commit local | `cd2f903 feat(registration): register customer vehicles` |

## Estratégia de commits

Commits devem permanecer coesos e usar Conventional Commits. A divisão preferencial é:

1. `feat(registration): model vehicle registration` — domínio, aplicação e testes unitários;
2. `feat(registration): persist vehicle registrations` — Flyway, JPA e testes de persistência;
3. `feat(registration): expose vehicle registration api` — HTTP, OpenAPI, Postman e testes de contrato.

Se a implementação revelar que um checkpoint não é revisável isoladamente, a divisão pode ser ajustada sem misturar
limpeza ou funcionalidades das stories `SCRUM-36`, `SCRUM-35` e `SCRUM-37`.

## Rollback e recuperação

- Antes de a migration integrar uma baseline compartilhada, ambiente local pode ser reconstruído do zero.
- Depois de aplicada em ambiente compartilhado, a migration é imutável; correções usam migration forward-only.
- Reverter somente a aplicação deixa a tabela aditiva sem uso e preserva os dados.
- Não executar `DROP TABLE`, cascade, hard delete ou reescrita de migrations como rollback operacional.
- Falha desconhecida de constraint ou lock deve interromper o fluxo e ser diagnosticada, não mascarada como sucesso.

## Gate para iniciar código da SCRUM-7

- [x] Ivan revisou este plano.
- [x] Ivan autorizou explicitamente iniciar a implementação da `SCRUM-7` em 2026-08-17.
- [x] Nenhum ponto técnico pendente exige mudança material nas specs aprovadas.

## SCRUM-36 — atualização descritiva e de chassis

### Objetivo e restrições

Implementar somente a atualização aprovada de marca, modelo, ano e cor, com inclusão ou substituição opcional de
chassis. Os quatro dados descritivos são obrigatórios. Chassis omitido, `null`, vazio ou em branco preserva o valor
atual; valor não vazio é validado e pode substituir o existente; remoção não será implementada.

O comando preserva `id`, `customerId`, placa, lifecycle, futura quilometragem e snapshots históricos. Não serão criadas
consulta adicional de Vehicle, integração com Service Lifecycle, alteração de Customer, migration ou seed.

Nenhum checkpoint de implementação começa antes da autorização explícita registrada no gate final deste plano.

### Checkpoint 0 — confirmar baseline e gates

- [x] Confirmar branch `feat/registration-vehicle-management`, head `cd2f903` e worktree rastreado limpo.
- [x] Confirmar que o PR #12 permanece pendente, sem executar merge, push ou novo PR.
- [x] Revalidar que as specs funcional e técnica da `SCRUM-36` estão `Approved`.
- [x] Executar a suíte focada de Vehicle como baseline antes da primeira alteração de código.
- [x] Registrar SHA, relação com `dev`, quantidade de testes e qualquer aviso preexistente.

Evidência esperada: baseline reproduzível e ausência de mudança remota ou sobreposição não reconciliada.

Evidência obtida: `cd2f903`, `dev` ancestral com divergência `0 4`, worktree rastreado limpo e 25 testes focados
verdes. Permanecem apenas os avisos preexistentes de H2/Flyway e do agent dinâmico do Mockito.

### Checkpoint 1 — domínio e invariantes

- [x] Criar testes de `Vehicle.updateDetails(...)` antes da implementação.
- [x] Cobrir atualização e normalização de marca, modelo, ano e cor.
- [x] Cobrir inclusão, substituição e repetição idempotente de chassis.
- [x] Cobrir preservação com chassis ausente no comando, `null`, vazio e em branco.
- [x] Cobrir rejeição de Vehicle arquivado com `VehicleArchivedException`.
- [x] Cobrir atomicidade quando descrição, ano ou chassis não vazio for inválido.
- [x] Demonstrar preservação de `id`, `customerId`, placa e `active`.
- [x] Tornar `year` mutável somente pelo método de negócio e não adicionar setters públicos.

Evidência esperada: testes de domínio verdes e todas as invariantes aprovadas protegidas pelo aggregate.

Evidência obtida: `VehicleTest` cobre atualização atômica, normalização, preservação de chassis e rejeição de Vehicle
arquivado. Identidade, associação, placa e lifecycle continuam sem setters e fora do comando.

### Checkpoint 2 — aplicação, DTOs e porta de repositório

- [x] Criar `UpdateVehicleRequest` com os quatro descritivos obrigatórios e chassis opcional.
- [x] Criar `VehicleNotFoundException` e reutilizar `VehicleChassisAlreadyExistsException`.
- [x] Ampliar `VehicleRepository` com `findByIdForUpdate` e precheck de chassis que exclui o próprio ID.
- [x] Implementar `UpdateVehicleUseCase` transacional com o `Clock` existente.
- [x] Preservar chassis sem conteúdo e evitar precheck quando o valor canônico não mudar.
- [x] Aplicar a precedência aprovada: validação, lookup, lifecycle, unicidade e save.
- [x] Testar Vehicle ausente, arquivado, chassis duplicado, idempotência e ausência de save após falha.
- [x] Confirmar por teste que Customer e Service Lifecycle não são consultados.

Evidência esperada: orquestração verde, sem persistência parcial e sem dependência entre módulos.

Evidência obtida: seis testes unitários do use case cobrem sucesso, quatro semânticas de preservação, idempotência,
not found, archive, duplicidade e validação antecipada; o único colaborador do use case é `VehicleRepository`.

### Checkpoint 3 — persistência e concorrência

- [x] Implementar `findByIdForUpdate` com `PESSIMISTIC_WRITE` sobre a linha do Vehicle.
- [x] Implementar consulta de chassis por valor excluindo o ID alvo.
- [x] Preservar `saveAndFlush` e a tradução da constraint `uk_vehicles_chassis_number`.
- [x] Testar round-trip das descrições e inclusão/substituição de chassis na mesma linha.
- [x] Testar preservação de ID, Customer, placa e lifecycle.
- [x] Testar que o próprio chassis não conflita e que o chassis de outro Vehicle conflita.
- [x] Testar que falha de unicidade preserva o chassis anterior.
- [x] Confirmar serialização de updates concorrentes do mesmo Vehicle pelo lock.
- [x] Confirmar Flyway/Hibernate verdes sem migration, backfill ou seed novo.

Evidência esperada: lock limitado à transação, constraint autoritativa e schema existente suficiente.

Evidência obtida: sete testes de persistência verdes; SQL emitido contém `for update`, e teste com duas transações
confirma bloqueio da segunda até o commit da primeira. As cinco migrations existentes validam o schema sem alteração.

### Checkpoint 4 — HTTP e tradução de falhas

- [x] Expor `PATCH /api/vehicles/{id}` no controller com constructor injection.
- [x] Retornar `200 OK` e reutilizar `VehicleResponse` completo.
- [x] Mapear `VEHICLE_NOT_FOUND`, `VEHICLE_ARCHIVED` e `VEHICLE_CHASSIS_ALREADY_EXISTS`.
- [x] Manter `VALIDATION_ERROR` e `INVALID_VEHICLE` conforme a origem da falha.
- [x] Testar sucesso com chassis omitido, `null`, vazio, em branco, incluído e substituído.
- [x] Testar descrições ausentes/inválidas, ano dinâmico, chassis inválido, not found, archive e duplicidade.
- [x] Testar que campos proibidos não alteram identidade, associação, placa ou lifecycle.
- [x] Verificar que respostas de erro não expõem placa, chassis, IDs, SQL, constraint ou stack trace.

Evidência esperada: MockMvc verde para contrato, precedência, idempotência e códigos estáveis.

Evidência obtida: seis testes MockMvc da atualização verdes; respostas usam apenas códigos e mensagens estáveis, sem
eco dos valores operacionais ou detalhes internos.

### Checkpoint 5 — OpenAPI, Postman e documentação

- [x] Documentar o `PATCH`, request, response, exemplos, nulabilidade e falhas com Springdoc em português.
- [x] Atualizar `OpenApiContractTest` com path, schemas, obrigatoriedade e responses.
- [x] Adicionar à pasta Vehicle do Postman exemplos de preservação, inclusão e substituição de chassis.
- [x] Reutilizar a variável `vehicleId` capturada pelo cadastro.
- [x] Validar a collection Postman com parser JSON.
- [x] Atualizar `docs/PROJECT-STRUCTURE.md` se a lista de componentes do slice exigir sincronização.
- [x] Confirmar que nenhum contrato de Customer ou Service Order foi alterado.

Evidência esperada: OpenAPI gerado como fonte de verdade, teste de contrato verde e collection válida.

Evidência obtida: cinco testes de `OpenApiContractTest` verdes; Postman validado por `ConvertFrom-Json`, com três
exemplos de atualização usando `{{vehicleId}}`; apenas o contrato de Vehicle recebeu novo path/schema.

### Checkpoint 6 — segurança, qualidade e validação final

- [x] Executar e registrar a revisão de segurança abaixo.
- [x] Executar testes focados durante o desenvolvimento.
- [x] Executar o equivalente a `make test` com Java 21 e o Maven provisionado pelo wrapper.
- [x] Executar o equivalente a `make verify` sem falhas, erros ou skips inadequados.
- [x] Confirmar `ModuleStructureTest` e exatamente três módulos acíclicos.
- [x] Revisar JaCoCo e manter pelo menos 80% de cobertura de linhas no slice alterado.
- [x] Validar manualmente no MySQL os cenários HTTP e a linha persistida.
- [x] Atualizar critérios implementados nas specs e registrar evidências neste plano e no tracker local.
- [x] Realizar aceite manual com Ivan antes de considerar a story concluída.
- [x] Criar commit local somente após autorização específica e gates verdes.

### Revisão de segurança planejada

| Item | Verificação e mitigação | Estado |
|---|---|---|
| Input e mass assignment | DTO fechado; domínio protege invariantes e campos imutáveis | Passed |
| Autenticação/autorização | Resolvido posteriormente: JWT exige `MANAGER` ou `ADMIN` | Passed |
| Exposição de dados | Response limitado ao Vehicle; erros não ecoam valores operacionais | Passed |
| Segredos e logs | Nenhum segredo ou log novo; diffs e mensagens revisados | Passed |
| SQL e persistência | JPA parametrizado, schema existente e nenhuma migration destrutiva | Passed |
| Concorrência | Lock por Vehicle, precheck excluindo ID e unique constraint final | Passed |
| Erros | Códigos estáveis sem SQL, constraints, tipos internos ou stack trace | Passed |
| Dependências | N/A planejado: nenhuma dependência nova | N/A |
| Abuso e enumeração | Payload limitado e endpoints protegidos por `MANAGER`/`ADMIN` | Passed |

Achado crítico ou alto bloqueia conclusão. Itens `N/A` deverão permanecer justificados no relatório final.

### Evidências a registrar

| Evidência | Resultado |
|---|---|
| Baseline e relação com PR #12/`dev` | `cd2f903`; `dev` ancestral, divergência `0 4`; PR #12 intocado |
| Testes de domínio | `VehicleTest` verde; 7 testes |
| Testes de aplicação | `UpdateVehicleUseCaseTest` verde; 6 testes |
| Persistência e concorrência | 7 testes verdes; `for update` e bloqueio entre duas transações confirmados |
| MockMvc e OpenAPI | 6 testes do PATCH e 5 testes OpenAPI verdes |
| Postman JSON | `ConvertFrom-Json` verde; três exemplos usam `{{vehicleId}}` |
| `make test` | Equivalente Maven: 132 testes, 0 falhas, 0 erros, 0 skips |
| `make verify` | Equivalente Maven verde; JAR e relatório JaCoCo gerados |
| JaCoCo | Vehicle 226/229 linhas, 98,69%; classes alteradas de produção, 100% |
| `ModuleStructureTest` | 2/2 verde; três módulos acíclicos |
| Validação manual MySQL | Preservação, inclusão, substituição, duplicidade, not found, inválido e archive verdes |
| Revisão de segurança | Nenhum achado crítico/alto; JWT e papéis permanecem dívida transversal |
| Aceite manual | Ivan aprovou a implementação em 2026-08-17 |
| Commit local | `d5adcc9 feat(registration): update vehicle details` |

### Estratégia de commit

A `SCRUM-36` deve produzir um único commit coeso após implementação, verificação e aceite:

```text
feat(registration): update vehicle details
```

Specs, plano, handoff e `IvanTasks.md` permanecem locais e não entram no commit. Não executar push, abrir PR ou mesclar
o PR #12 sem autorização explícita separada.

### Rollback e recuperação

- Não existe migration para reverter.
- Reverter a aplicação restaura somente o contrato e comportamento anteriores; dados já corrigidos permanecem válidos.
- Correção de dado equivocada usa novo comando válido, nunca SQL manual destrutivo ou hard delete.
- Falha antes do commit transacional preserva todos os valores anteriores.
- Falha técnica desconhecida deve interromper o fluxo e ser diagnosticada, não mascarada como sucesso.

### Gate para iniciar código da SCRUM-36

- [x] Especificação funcional aprovada por Ivan em 2026-08-17.
- [x] Especificação técnica aprovada por Ivan em 2026-08-17.
- [x] Ivan revisou e aprovou este plano de implementação em 2026-08-17.
- [x] Ivan autorizou explicitamente iniciar o código da `SCRUM-36` em 2026-08-17.
- [x] Nenhum ponto pendente exige mudança material nas specs aprovadas.

## SCRUM-35 — quilometragem de Vehicle

### Objetivo e restrições

Implementar `mileage` opcional no cadastro de Vehicle e um comando específico para primeiro registro ou atualização
monotônica. Quilometragem usa quilômetros inteiros; valor maior atualiza, valor igual é idempotente e valor menor,
negativo, fracionário ou incompatível com `long` falha sem persistência.

O slice permanece em `registration.vehicle`. Não serão incluídos archive, histórico de leituras, correção para baixo,
consulta adicional, alteração de `VehicleSnapshot`, integração com Service Lifecycle, autenticação ou mudança de outro
bounded context. Specs, plano, handoff e `IvanTasks.md` continuam locais e nunca entram no commit.

Os checkpoints foram aprovados e autorizados por Ivan em 2026-08-22. A implementação foi aceita e recebeu o commit
local `4f7d346` em 2026-08-23, sem push, PR, merge, Jira ou Miro.

### Checkpoint 0 — confirmar baseline e gates

- [x] Confirmar PR #12 mesclado em `dev` pelo commit `d21fd3f`.
- [x] Atualizar `dev` por fast-forward e criar `feat/registration-vehicle-management2` em `608dd29`.
- [x] Confirmar `d5adcc9` como ancestral da nova baseline e divergência zero contra `origin/dev`.
- [x] Confirmar worktree rastreado limpo e specs/tracker protegidos por `.git/info/exclude`.
- [x] Executar `verify` em Java 21 antes da primeira alteração de código.
- [x] Registrar aprovação funcional e técnica da `SCRUM-35` por Ivan em 2026-08-22.
- [x] Obter aprovação deste plano e autorização explícita para iniciar código em 2026-08-22.

Evidência obtida: branch local em `608dd29`, sem upstream; `verify` com 308 testes, zero falhas, erros ou skips;
`ModuleStructureTest` 2/2; `OpenApiContractTest` 5/5; sete migrations Flyway aplicadas na baseline de teste.

### Checkpoint 1 — domínio e propagação estrutural

- [x] Criar `Mileage` framework-free sobre `long`, com zero, positivos, `Long.MAX_VALUE` e rejeição de negativo.
- [x] Adicionar `Mileage` nullable ao estado de `Vehicle`, sem converter ausência em zero.
- [x] Ampliar `Vehicle.create(...)` e `Vehicle.reconstitute(...)` com mileage explícita.
- [x] Implementar `Vehicle.recordMileage(...)` com lifecycle, monotonicidade e retorno de mudança efetiva.
- [x] Criar `VehicleMileageCannotDecreaseException` com mensagem genérica em português.
- [x] Cobrir primeiro registro, aumento, igualdade, regressão, archive e preservação integral do aggregate.
- [x] Ajustar fixtures e mapeamentos necessários para manter o projeto compilável sem criar setters públicos.
- [x] Executar testes focados de `MileageTest` e `VehicleTest`.

Evidência esperada: domínio verde; valor igual não muda estado; falhas preservam mileage e todos os campos não
relacionados.

### Checkpoint 2 — migration e persistência

- [x] Gerar timestamp UTC e criar `V<timestamp>__add_vehicle_mileage.sql` sem alterar migrations existentes.
- [x] Adicionar `mileage BIGINT NULL` e `ck_vehicles_mileage_non_negative`.
- [x] Confirmar ausência de default e backfill; linhas existentes devem permanecer `NULL`.
- [x] Adicionar `Long mileage` nullable a `VehicleJpaEntity`.
- [x] Atualizar `VehiclePersistenceMapper` para round-trip de ausência e valores presentes.
- [x] Preservar UUID, Customer, placa, chassis, descrições, ano e lifecycle em todos os saves.
- [x] Testar migration sobre schema vazio e sobre Vehicle existente sem mileage.
- [x] Testar round-trip de `NULL`, zero, positivo e `Long.MAX_VALUE`.
- [x] Testar que a constraint rejeita valor negativo escrito fora do domínio.
- [x] Confirmar Flyway e Hibernate `ddl-auto=validate` verdes.
- [x] Registrar classificação de dados: **nenhum seed requerido**.

Evidência esperada: migration aditiva, mapeamento coerente e nenhuma fabricação ou perda de dados existentes.

### Checkpoint 3 — cadastro opcional e validação de boundary

- [x] Adicionar `Long mileage` opcional ao final de `CreateVehicleRequest`.
- [x] Criar `StrictLongDeserializer` local aos DTOs de Vehicle.
- [x] Aceitar somente token JSON inteiro ou `null`, rejeitando string, decimal, boolean, estrutura e overflow.
- [x] Aplicar `@PositiveOrZero` quando houver valor e manter omissão/`null` válidos no cadastro.
- [x] Converter o valor para `Mileage` antes de consultar Customer.
- [x] Estender `VehicleResponse` e `VehicleMapper` com `mileage` nullable em `integer/int64`.
- [x] Testar cadastro omitido, `null`, zero, positivo e `Long.MAX_VALUE`.
- [x] Testar negativo, decimal, string, boolean, estrutura e overflow sem persistência parcial.
- [x] Confirmar que requests antigos de cadastro continuam válidos.

Evidência esperada: cadastro compatível, parsing sem coerção ou truncamento e response distinguindo `null` de zero.

### Checkpoint 4 — comando monotônico e concorrência

- [x] Criar `UpdateVehicleMileageRequest` com `@NotNull`, `@PositiveOrZero` e parsing estrito.
- [x] Implementar `UpdateVehicleMileageUseCase` transacional.
- [x] Reutilizar `VehicleRepository.findByIdForUpdate(UUID)` e `save(Vehicle)` sem ampliar a porta.
- [x] Salvar com flush no primeiro registro ou aumento e não salvar quando o valor for igual.
- [x] Aplicar precedência: contrato, lookup, lifecycle, regressão e sucesso.
- [x] Testar primeiro registro, aumento, idempotência, regressão, not found e archive.
- [x] Confirmar por teste que Customer e outros módulos não são acessados.
- [x] Testar duas transações concorrentes com valores diferentes para impedir regressão.
- [x] Testar que updates concorrentes de Vehicles diferentes permanecem independentes.

Evidência esperada: orquestração e concorrência verdes, lock limitado à linha alvo e nenhuma persistência após falha ou
igualdade.

### Checkpoint 5 — HTTP, erros, OpenAPI e Postman

- [x] Expor `PATCH /api/vehicles/{id}/mileage` com constructor injection e DTO validado.
- [x] Retornar `200 OK` e `VehicleResponse` completo no primeiro registro, aumento e igualdade.
- [x] Mapear regressão para `409 VEHICLE_MILEAGE_CANNOT_DECREASE`.
- [x] Reutilizar `VEHICLE_NOT_FOUND`, `VEHICLE_ARCHIVED`, `VALIDATION_ERROR` e `INVALID_VEHICLE`.
- [x] Garantir que erros não exponham ID, valores, SQL, constraint, tipo interno ou stack trace.
- [x] Criar testes MockMvc para sucesso, idempotência, regressão, lifecycle e todos os tipos inválidos.
- [x] Documentar cadastro, update, schemas, unidade, nulabilidade, obrigatoriedade e erros com Springdoc em português.
- [x] Atualizar `OpenApiContractTest` para `integer/int64`, required/nullable, path e responses.
- [x] Atualizar Postman em `Registrations > Vehicle` com cadastro com/sem mileage, aumento, igualdade e regressão.
- [x] Reutilizar `{{vehicleId}}` e validar a collection com parser JSON.
- [x] Atualizar `docs/PROJECT-STRUCTURE.md` se a lista do slice exigir sincronização.
- [x] Confirmar que nenhum contrato de Customer, Stock & Procurement ou Service Lifecycle mudou.

Evidência esperada: MockMvc e OpenAPI verdes, collection válida e contratos sincronizados na mesma mudança.

### Checkpoint 6 — segurança, qualidade e validação final

- [x] Executar e registrar cada item da revisão de segurança abaixo.
- [x] Executar testes focados após cada checkpoint.
- [x] Executar `make test` com Java 21 ou registrar o equivalente do Wrapper se o launcher Windows impedir o Makefile.
- [x] Executar `make verify` antes da conclusão, sem falhas, erros ou skips inadequados.
- [x] Confirmar `ModuleStructureTest` e exatamente três módulos acíclicos.
- [x] Executar `make coverage` e revisar cobertura do slice, sem regressão e com alvo mínimo de 80%.
- [x] Validar a migration no MySQL 8.0.46 e registrar o aceite humano do fluxo HTTP.
- [x] Cobrir cadastro sem/com mileage, primeiro registro, aumento, igualdade, regressão, archive e concorrência.
- [x] Atualizar specs, critérios implementados, evidências deste plano, tracker e handoff local.
- [x] Realizar aceite manual com Ivan antes de considerar a story concluída.
- [x] Criar commit local somente após autorização específica e todos os gates verdes.
- [x] Não executar push, PR, merge, Jira ou Miro sem autorização separada.

### Revisão de segurança planejada

| Item | Verificação e mitigação planejada | Estado |
|---|---|---|
| Input e mass assignment | DTOs fechados; parser estrito; validação na borda e no domínio | Pass |
| Autenticação/autorização | Resolvido posteriormente: JWT exige `MANAGER` ou `ADMIN` | Pass |
| Exposição de dados | Response só com Vehicle; erros não ecoam IDs ou mileage atual/recebida | Pass |
| Segredos e logs | Nenhum segredo ou log novo; diffs e mensagens revisados | Pass |
| SQL e migration | JPA parametrizado; Flyway aditivo; `BIGINT NULL`; check não negativo | Pass |
| Concorrência | Lock por Vehicle e teste com duas transações; igualdade sem save | Pass |
| Erros | Códigos estáveis sem SQL, constraint, valores, tipos internos ou stack trace | Pass |
| Dependências | N/A planejado: nenhuma dependência nova | N/A |
| Abuso | Tipo, fração, negativo e overflow rejeitados; payload permanece pequeno | Pass |

Revisão concluída sem achado crítico ou alto. A dívida transversal de JWT foi resolvida posteriormente.

### Evidências a registrar

| Evidência | Resultado |
|---|---|
| Baseline e grafo | `608dd29`; `d5adcc9` ancestral; divergência zero contra `origin/dev` |
| Baseline `verify` | 308 testes, zero falhas/erros/skips; Java 21.0.12 |
| Domínio | `MileageTest` e `VehicleTest` verdes; ausência, limites, monotonicidade e lifecycle cobertos |
| Aplicação | Use cases verdes; igualdade sem save e falhas sem persistência parcial |
| Migration e persistência | H2 e MySQL 8.0.46 verdes; `BIGINT NULL`, check e round-trip validados |
| Concorrência | Duas transações no mesmo Vehicle preservam o maior valor confirmado; Vehicles distintos independentes |
| MockMvc e OpenAPI | Cadastro/update e erros verdes; `OpenApiContractTest` 6/6 |
| Postman JSON | Parser JSON verde; cenários com/sem mileage, aumento, igualdade e regressão |
| `make test` | Suítes focadas verdes; gate completo coberto pelo `verify` |
| `make verify` | 333 testes; zero falhas, erros ou skips; JAR e relatório JaCoCo gerados |
| `make coverage`/JaCoCo | Slice de produção alterado 100%; projeto 93,27% instruções e 93,77% linhas |
| `ModuleStructureTest` | 2/2; exatamente três módulos acíclicos |
| Validação manual MySQL | 8 migrations válidas; schema em `20260823021233`; fluxo aceito por Ivan |
| Revisão de segurança | Concluída sem achado crítico ou alto |
| Aceite manual | Ivan Pimentel em 2026-08-23 |
| Commit local | `4f7d346 feat(registration): update vehicle mileage` |

### Estratégia de commit

A `SCRUM-35` deve produzir um único commit coeso somente depois de implementação, verificação, revisão de segurança e
aceite manual:

```text
feat(registration): update vehicle mileage
```

O commit deve conter somente código, migration, testes e documentação rastreada da story. Specs, plano, handoff e
`IvanTasks.md` permanecem locais. Push, PR e merge exigem autorizações separadas.

### Rollback e recuperação

- A migration é forward-only e imutável depois de aplicada em baseline compartilhada.
- Reverter a aplicação deixa a coluna nullable sem uso e preserva mileage já registrada.
- Correção de schema usa nova migration; nunca editar migration aplicada nem executar `DROP` ou reset destrutivo.
- Falha antes do commit transacional preserva a leitura anterior e todos os demais campos.
- Valor registrado incorretamente não pode ser reduzido por esta story; correção exige feature futura aprovada.
- Falha técnica desconhecida deve interromper o fluxo e ser diagnosticada, nunca mascarada como sucesso.

### Gate para iniciar código da SCRUM-35

- [x] Especificação funcional aprovada por Ivan em 2026-08-22.
- [x] Especificação técnica aprovada por Ivan em 2026-08-22.
- [x] Ivan revisou e aprovou este plano de implementação em 2026-08-22.
- [x] Ivan autorizou explicitamente iniciar o código da `SCRUM-35` em 2026-08-22.
- [x] Nenhum ponto pendente exige mudança material nas specs aprovadas.

## SCRUM-37 — arquivamento, consultas e elegibilidade para novo trabalho

### Objetivo e restrições

Implementar archive lógico e idempotente de Vehicle, consulta histórica por ID, listagem não paginada de ativos e
verificação transacional de elegibilidade antes da criação de uma nova Service Order. Preservar os snapshots e ordens
existentes, os contratos atuais de cadastro/update e todas as identidades do Vehicle.

O trabalho inclui concluir o add-on da collection Postman aprovado por Ivan. A consolidação de exemplos já está no
commit local `44ce9b0`; os novos GETs e o archive serão adicionados quando os respectivos contratos existirem.

Não serão implementados reativação, hard delete, audit trail, ownership Customer/Vehicle, reconciliação de snapshot,
paginação, filtros, ordenação contratual, FK entre módulos, migration ou seed.

O plano foi aprovado e o código autorizado por Ivan em 2026-08-23. O aceite manual e qualquer commit permanecem gates
separados.

### Checkpoint 0 — confirmar baseline e gates

- [x] Confirmar branch `feat/registration-vehicle-management2`, HEAD `44ce9b0`, ausência de upstream e worktree limpa.
- [x] Confirmar `4f7d346` e `44ce9b0` como os dois commits locais à frente de `origin/dev`.
- [x] Revalidar que specs, plano, handoff e `IvanTasks.md` permanecem ignorados por `.git/info/exclude`.
- [x] Confirmar funcional e técnica aprovadas em 2026-08-23 e registrar aprovação deste plano separadamente.
- [x] Obter autorização explícita de Ivan para iniciar o código em 2026-08-23.
- [x] Executar `make verify` ou o equivalente do Maven Wrapper com Java 21 como baseline fresca.
- [x] Registrar `ModuleStructureTest`, `OpenApiContractTest`, contagem de testes e cobertura inicial.
- [x] Confirmar que nenhuma migration ou seed é necessária e que Jira/Miro permanecem intocados.

Evidência esperada: SHA/grafo, worktree, proteção local, gates humanos e suíte verde antes da primeira mudança.

Evidência obtida: `44ce9b0`, divergência `0 2` contra a referência local `origin/dev`, sem upstream e worktree rastreado
limpo; documentos locais protegidos por `.git/info/exclude`; `verify` com 333 testes, zero falhas, erros ou skips;
`ModuleStructureTest` 2/2; `OpenApiContractTest` 6/6; cobertura inicial de 93,27% de instruções e 93,77% de linhas.

### Checkpoint 1 — domínio, repository e aplicação de Vehicle

- [x] Criar primeiro testes de `Vehicle.archive()` para ativo, repetição e preservação integral do aggregate.
- [x] Implementar `Vehicle.archive()` com retorno booleano e sem setter público ou reativação.
- [x] Ampliar `VehicleRepository` com `findById(UUID)` e `findAllActive()`.
- [x] N/A — helper interno não foi necessário; a tradução de ausência permanece simples e localizada.
- [x] Implementar `ArchiveVehicleUseCase` transacional com lookup bloqueante e save somente quando houver mudança.
- [x] Implementar `GetVehicleUseCase` e `ListVehiclesUseCase` como transações read-only.
- [x] Reutilizar `VehicleResponse` e `VehicleMapper` sem alterar schema ou nulabilidade.
- [x] Testar ativo, arquivado, inexistente, lista mista, lista vazia e ausência de acesso a outros módulos.
- [x] Executar testes focados de domínio e aplicação antes de avançar.

Evidência esperada: lifecycle idempotente, queries com semânticas distintas e nenhuma mutação por leitura.

Evidência obtida: os testes `VehicleTest`, `ArchiveVehicleUseCaseTest` e `VehicleQueryUseCaseTest` passaram 19/19.
Archive preserva todo o aggregate, repetição não salva, GET histórico distingue ausência de archive e lista read-only
usa apenas a query operacional. Os novos use cases dependem somente de `VehicleRepository`.

### Checkpoint 2 — persistência e queries de lifecycle

- [x] Implementar `findAllByActiveTrue()` no repository JPA e mapear resultados no adapter.
- [x] Usar `JpaRepository.findById` para consulta histórica sem lock.
- [x] Reutilizar `findByIdForUpdate` para archive, mantendo `PESSIMISTIC_WRITE` sobre uma única linha.
- [x] Confirmar que archive atualiza somente `active` e preserva a mesma linha e todos os demais campos.
- [x] Testar round-trip de ativo/arquivado, GET histórico e exclusão de arquivados da lista operacional.
- [x] Testar que placa e chassis arquivados continuam protegidos pelas constraints únicas.
- [x] Confirmar que repetição idempotente não chama save e que ID inexistente não cria registro.
- [x] Executar Flyway e Hibernate `ddl-auto=validate` sem criar ou editar migration.
- [x] Registrar classificação **nenhum seed requerido**.

Evidência esperada: schema atual suficiente, queries corretas e nenhuma exclusão, backfill ou dado fabricado.

Evidência obtida: suíte focada conjunta 31/31; `VehicleRepositoryIntegrationTest` passou 12/12 com oito migrations,
Hibernate validate, mesma linha histórica, filtro `active=true` no banco e constraints de placa/chassis preservadas.

### Checkpoint 3 — API pública e port consumidor entre módulos

- [x] Criar `registration.vehicle.application.api` com `VehicleAvailabilityApi` e `VehicleAvailability`.
- [x] Expor somente esse package como `@NamedInterface("vehicle-availability-api")`.
- [x] Implementar a API com `findByIdForUpdate` e `Propagation.MANDATORY`, sem retornar domínio ou JPA.
- [x] Criar `VehicleEligibilityPort` e enum próprios em Service Lifecycle.
- [x] Criar adapter Java em processo que mapeia os três resultados da API pública para o port consumidor.
- [x] Injetar somente o port em `CreateServiceOrderUseCase`.
- [x] Validar o request/snapshot antes do port e exigir resultado `ACTIVE` antes de criar/salvar a ordem.
- [x] Criar exceptions consumidoras para Vehicle ausente e arquivado, sem importar exceptions de Registrations.
- [x] Garantir por teste que falha não consulta técnicos, não notifica e não salva Service Order.
- [x] Cobrir API, port, adapter e use case com testes unitários focados.

Evidência esperada: dependência `servicelifecycle -> registration::vehicle-availability-api`, sem imports internos ou
ciclo de módulos.

Evidência obtida: 12/12 testes unitários de API, adapter e criação de ordem; API com transação obrigatória e os três
resultados cobertos; teste de módulo em dependências diretas e `ModuleStructureTest` passaram 5/5. Service Lifecycle
importa somente a named interface pública por meio do adapter e o use case consumidor depende somente do próprio port.

### Checkpoint 4 — concorrência e consistência transacional

- [x] Testar archive obtendo o lock antes da criação de Service Order; a ordem deve ser rejeitada.
- [x] Testar criação obtendo o lock primeiro; a ordem confirma e o archive posterior não a altera.
- [x] Confirmar que o lock da API permanece até o commit da transação consumidora.
- [x] Testar que nenhuma ordem iniciada após o `204` do archive é persistida.
- [x] Testar archive concorrente com update descritivo e mileage sob o mesmo lifecycle confirmado.
- [x] Confirmar que operações em Vehicles diferentes não compartilham lock.
- [x] Revisar ordem de locks e ausência de caminho inverso que introduza deadlock.
- [x] Executar testes de integração transacional sem sleeps frágeis ou enfraquecimento de assertions.

Evidência esperada: as duas ordens de confirmação reproduzíveis, sem TOCTOU, lost update ou efeito parcial.

Evidência obtida: `VehicleLifecycleConcurrencyIntegrationTest` passou 5/5 usando latches e timeouts de Future, sem
`sleep`. As duas ordens de aquisição do lock foram reproduzidas; o lock da elegibilidade permaneceu até o commit
consumidor; archive serializou com details/mileage; Vehicles distintos progrediram em paralelo. Todos os fluxos
concorrentes adquirem primeiro e somente o lock pessimista da linha de Vehicle, sem caminho inverso identificado.

### Checkpoint 5 — HTTP e tradução de falhas

- [x] Expor `GET /api/vehicles/{id}` histórico com `200` para ativo/arquivado e `404` para ausente.
- [x] Expor `GET /api/vehicles` com todos os ativos, array vazio e nenhuma paginação/filtro.
- [x] Expor `DELETE /api/vehicles/{id}` com `204` no primeiro archive e na repetição.
- [x] Reutilizar `VEHICLE_NOT_FOUND`, `VALIDATION_ERROR` e `VehicleResponse` no controller de Vehicle.
- [x] Mapear no Service Lifecycle `404 VEHICLE_NOT_FOUND` e `409 VEHICLE_ARCHIVED`.
- [x] Documentar as novas respostas do `POST /api/service-orders` sem mudar seu request ou sucesso.
- [x] Testar UUID inválido, not found, archive idempotente, lista e representação completa via MockMvc.
- [x] Testar que erros não expõem UUID, placa, chassis, mileage, SQL, constraint ou tipo interno.
- [x] Confirmar que endpoints atuais de Customer e Vehicle permanecem compatíveis.

Evidência esperada: contratos HTTP e precedência de erros verdes, sem objetos de domínio/JPA na borda.

Evidência obtida: 31/31 testes MockMvc passaram nas cinco classes focadas, incluindo os endpoints anteriores de
cadastro, details e mileage. GET histórico retornou a representação completa para ativo/arquivado; lista mista e vazia,
DELETE repetido, IDs inválidos e falhas de elegibilidade foram cobertos sem eco de identificadores ou detalhes internos.

### Checkpoint 6 — OpenAPI, Postman e documentação

- [x] Documentar GET histórico, lista ativa e DELETE idempotente com Springdoc em português.
- [x] Atualizar `OpenApiContractTest` com paths, responses e array de `VehicleResponse`.
- [x] Preservar a consolidação Postman registrada em `44ce9b0` sem reintroduzir duplicidades.
- [x] Adicionar `Get vehicle`, `List vehicles` e `Archive vehicle` à pasta Vehicle.
- [x] Manter exatamente uma entrada por combinação de método e URL nas pastas Customer e Vehicle.
- [x] Confirmar exemplos completos de criação e atualização e descrições de campos opcionais.
- [x] Validar a collection por parser JSON e confrontá-la com a OpenAPI gerada.
- [x] Atualizar `docs/PROJECT-STRUCTURE.md` com queries, lifecycle e integração pública.
- [x] Registrar na documentação de Service Order a validação de Vehicle como evolução da SCRUM-37.
- [x] Confirmar ausência de YAML OpenAPI manual e de documentação conflitante sobre snapshot/ownership.

Evidência esperada: OpenAPI como fonte de verdade, collection objetiva e documentação estrutural coerente.

Evidência obtida: `OpenApiContractTest` passou 7/7 com paths, responses e array tipado de `VehicleResponse`; collection
parseada com seis requests de Vehicle e zero combinação método+URL duplicada nas pastas Customer/Vehicle; exemplos e
descrições anteriores preservados; `PROJECT-STRUCTURE.md` registra lifecycle, named interface, lock e ausência de
reconciliação do snapshot. Nenhum YAML OpenAPI/Swagger manual foi encontrado.

### Checkpoint 7 — segurança, qualidade e validação final

- [x] Executar e registrar cada item da revisão de segurança abaixo.
- [x] Executar testes focados após cada checkpoint e corrigir regressões dentro do escopo.
- [x] Executar `make test` com Java 21 ou o equivalente do Maven Wrapper no Windows.
- [x] Executar `make verify` sem falhas, erros ou skips inadequados.
- [x] Confirmar `ModuleStructureTest`, named interface autorizada e exatamente três módulos acíclicos.
- [x] Gerar JaCoCo pelo gate equivalente a `make coverage` e manter pelo menos 80% no slice.
- [x] Validar manualmente no MySQL archive, GET histórico, lista ativa e bloqueio de nova Service Order.
- [x] Usar dados sintéticos; não executar reset, hard delete ou alteração manual destrutiva.
- [x] Atualizar specs, critérios, evidências, tracker e handoff após todos os gates técnicos.
- [x] Obter aceite manual de Ivan antes de marcar a story como implementada.
- [x] Obter autorização específica antes de criar qualquer commit local.
- [x] Não executar push, PR, merge, Jira ou Miro sem autorização separada para a ação exata.

### Revisão de segurança planejada

| Item | Verificação e mitigação planejada | Estado |
|---|---|---|
| Input e mass assignment | GET/DELETE sem body; DTO fechado da ordem; UUID validado | Concluído |
| Autenticação/autorização | Resolvido posteriormente: JWT exige `MANAGER` ou `ADMIN` | Concluído |
| Exposição de dados | Só `VehicleResponse`; histórico aprovado inclui placa, chassis e `customerId` | Concluído |
| Enumeração | JWT limita o acesso a `MANAGER`/`ADMIN`; erros permanecem genéricos | Mitigado |
| Lista não paginada | Risco baixo aceito; contrato sem filtros ocultos e cobertura de lista vazia/mista | Aceito |
| Segredos e logs | Nenhum segredo, dependência ou log sensível novo | Concluído |
| SQL e persistência | JPA parametrizado; sem migration, FK, delete ou update em lote | Concluído |
| Concorrência | Mesmo lock no archive e elegibilidade; duas ordens de commit testadas | Concluído |
| Erros | Códigos estáveis sem SQL, constraints, tipos internos ou stack trace | Concluído |
| Dependências | Nenhuma biblioteca nova; named interface mínima e módulos acíclicos | Concluído |
| Abuso | DELETE idempotente, queries read-only e payload da ordem limitado | Concluído |

Achado crítico ou alto bloqueia conclusão. Riscos médio/baixo exigem evidência e mitigação registradas, mas não devem
ser ocultados ou reclassificados apenas para liberar o gate.

### Evidências a registrar

| Evidência | Resultado |
|---|---|
| Baseline e grafo | `44ce9b0`, divergência `0 2` contra `origin/dev`, sem upstream e worktree limpo |
| Baseline `verify` | 333 testes verdes; Modulith 2/2; OpenAPI 6/6; cobertura de linhas 93,77% |
| Domínio e aplicação Vehicle | 19/19 testes; archive, GET e lista preservam lifecycle e estado |
| Persistência | 12/12 testes; mesma linha, ativos filtrados no banco, identidades reservadas e zero migrations |
| API pública e adapter | UUID/enum, `MANDATORY`, named interface e mapeamento dos três resultados cobertos |
| Service Order | Ativo cria; ausente/arquivado falham antes de save, técnicos e notificação |
| Concorrência | 5/5 testes determinísticos; duas ordens de commit e locks por Vehicle comprovados |
| MockMvc e OpenAPI | 31/31 testes HTTP focados; `OpenApiContractTest` 7/7 |
| Postman JSON | Seis requests de Vehicle; parser válido e zero duplicidade método+URL em Customer/Vehicle |
| `make test` | Equivalente Maven Wrapper: 370 testes, zero falhas, erros ou skips |
| `make verify` | Equivalente Maven Wrapper: sucesso com 370 testes e JaCoCo gerado |
| `make coverage`/JaCoCo | Projeto: 93,43% instruções e 93,94% linhas; slice: 97,78% e 99,18% |
| `ModuleStructureTest` | 2/2; três módulos acíclicos e named interface autorizada |
| Validação manual MySQL | Archive/GET/list/repetição e `409 VEHICLE_ARCHIVED`; contagem de ordens permaneceu zero |
| Revisão de segurança | Concluída sem achado crítico ou alto; riscos médio/baixo aceitos permanecem explícitos |
| Aceite manual | Ivan Pimentel em 2026-08-23 |
| Commit local | `d73ff8d` após autorização específica de Ivan |

### Estratégia de commit

A implementação produziu um commit coeso depois de testes, documentação, revisão de segurança, aceite manual e
autorização específica:

```text
feat(registration): archive and query vehicles
```

O commit `d73ff8d` inclui código, testes e documentação rastreada da SCRUM-37. A consolidação anterior da collection
permanece em `44ce9b0`; as três operações novas do Postman entraram no commit da implementação. Specs, plano, handoff e
`IvanTasks.md` permanecem locais.

Ivan autorizou push e criação da PR Draft #26. Depois, autorizou corrigir os conflitos com `dev`; o merge não destrutivo
foi registrado em `5c96181`. A PR #26 foi posteriormente revisada e integrada em `dev` pelo merge commit `6b9f223`.
Jira e Miro não foram alterados por este plano.

### Rollback e recuperação

- Não existe migration para reverter ou editar.
- Reverter a aplicação remove endpoints e a nova verificação, mas preserva linhas já arquivadas com `active=false`.
- Rollback não reativa Vehicle nem altera Service Orders confirmadas antes do archive.
- A coluna `active` e os bloqueios existentes continuam compatíveis com a versão anterior.
- A collection pode ser revertida por commit posterior, nunca reescrevendo o commit de Ivan sem autorização.
- Falha antes do commit transacional preserva Vehicle e impede save/notificação parcial da nova Service Order.
- Falha técnica desconhecida deve interromper o fluxo e ser diagnosticada, nunca mascarada como sucesso.

### Gate para iniciar código da SCRUM-37

- [x] Especificação funcional aprovada por Ivan em 2026-08-23.
- [x] Especificação técnica aprovada por Ivan em 2026-08-23.
- [x] Ivan revisou e aprovou este plano de implementação em 2026-08-23.
- [x] Ivan autorizou explicitamente iniciar o código da `SCRUM-37` em 2026-08-23.
- [x] Nenhum ponto pendente exige mudança material nas specs aprovadas.

### Reconciliação posterior com dev

- [x] Integrar `origin/dev` em `ffc4eef` por merge, sem rebase ou reescrita dos commits publicados.
- [x] Resolver os três conflitos de conteúdo preservando elegibilidade de Vehicle, `initialAssessment` e diagnóstico.
- [x] Adaptar fixtures às APIs atuais de Service Order sem enfraquecer validações.
- [x] Validar OpenAPI 10/10, Modulith 2/2 e Postman sem duplicidades em Customer/Vehicle.
- [x] Executar `verify`: 387 testes, zero falhas, erros ou skips; cobertura global de 93,09%/93,66%.
- [x] Criar e publicar `5c96181 chore(registration): reconcile vehicle management with dev`.
- [x] Confirmar PR #26 integrada em `dev` pelo merge commit `6b9f223`.

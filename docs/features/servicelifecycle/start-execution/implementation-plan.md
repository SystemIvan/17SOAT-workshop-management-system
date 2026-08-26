# Plano de Implementação: Iniciar execução com Technician atribuído

| Campo | Valor |
|---|---|
| Feature | `start-execution` |
| Status | Implemented |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-08-26 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-08-26) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-08-26) |

## Checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 1 | Aplicar guarda no domínio | Completed |
| 2 | Cobrir domínio, caso de uso e HTTP | Completed |
| 3 | Atualizar OpenAPI, Postman e README | Completed |
| 4 | Executar gates e revisão de segurança | Completed |

## Checkpoint 1 — Aplicar guarda no domínio

Alterar `ServiceExecution.start()` para manter a exigência de `READY` e exigir
`assignedTechnicianId` antes de mudar o status para `IN_PROGRESS`. Não alterar persistência, schema,
dependências entre módulos ou outros casos de uso.

Verificação: teste unitário do domínio.

## Checkpoint 2 — Cobrir domínio, caso de uso e HTTP

Adicionar regressões para a recusa de `READY` sem Technician, preservação do estado e início bem-sucedido
com atribuição. No HTTP, validar `409 INVALID_STATE_TRANSITION`; manter `200` no caminho feliz.

Verificação: testes direcionados de domínio, use case e MockMvc.

## Checkpoint 3 — Atualizar contratos e manual

Documentar o novo `409` no Springdoc, ajustar a collection Postman para atribuir o Technician antes de
iniciar e atualizar o README com pré-requisitos, ordem de chamadas e resultados esperados.

Verificação: teste de contrato OpenAPI aplicável e inspeção da collection.

## Checkpoint 4 — Gates e revisão de segurança

Executar `make test`, `make verify`, `git diff --check` e revisar as superfícies de validação, erros,
autorização, dados sensíveis, persistência e dependências. Registrar evidências neste plano e marcá-lo
`Implemented` somente se todos os gates passarem.

## Revisão de segurança

| Item | Status | Evidência ou mitigação |
|---|---|---|
| Validação e mass assignment | N/A | Endpoint não recebe body; a guarda está no aggregate e não há campo novo a atribuir em massa. |
| Autenticação e autorização | N/A | A autorização do ator permanece fora do escopo, dependente de AD-016; não houve ampliação de acesso. |
| Exposição de dados operacionais | Resolved | `IllegalStateException` é mapeada ao código estável `INVALID_STATE_TRANSITION`, sem stack trace ou detalhes internos. |
| Segredos, credenciais e logs | N/A | Não foram criados logs, campos sensíveis ou dependências. |
| Persistência e migration | N/A | `assignedTechnicianId` já era persistido; não houve schema, migration ou seed. |
| Dependências e vulnerabilidades | N/A | Nenhuma dependência foi adicionada. |

## Evidências de execução

- `ServiceExecution.start()` agora rejeita `READY` sem `assignedTechnicianId` antes de alterar o estado.
- Foram incluídos testes de domínio, caso de uso e MockMvc para a nova recusa; os fixtures de execução
  concluída foram ajustados para atribuir um Technician de forma explícita.
- O Springdoc documenta o `409`, a collection Postman atribui o Technician imediatamente antes de
  `Start execution`, e o README registra o novo pré-requisito e a resposta esperada.
- `make test`: passou, **624 testes**, 0 falhas/erros; `ModuleStructureTest` incluído.
- `make verify`: passou, **624 testes**, 0 falhas/erros; cobertura JaCoCo **91,43%**.
- `git diff --check`: passou.

### Correção de regressão de fixtures — 2026-08-26

Uma execução posterior da suíte identificou 11 fixtures de testes de RF10/RF12/RF19/RF22/RF24 que montavam
diretamente uma execução `READY → IN_PROGRESS` ou `COMPLETED` sem atribuir Technician. Eles foram
atualizados para chamar `confirmTechnicianAssignment` antes de iniciar, preservando a nova regra de
produção e a intenção de cada teste. `make test` e `./mvnw verify` passaram após a correção, com 624
testes e 0 falhas/erros. A tentativa de `make verify` falhou somente no `clean`, antes dos testes, porque
um artefato temporário em `target/surefire-reports` não pôde ser removido; a validação sem `clean` passou.

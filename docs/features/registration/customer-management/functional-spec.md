# Especificação Funcional: Gestão de Customers

| Campo | Valor |
|---|---|
| Feature | `customer-management` |
| Status | Implemented e aceito (`SCRUM-6`, `SCRUM-34` e `SCRUM-33`) |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovado por | Ivan Pimentel (`SCRUM-33`) |
| Aprovado em | 2026-08-17 (`SCRUM-33`) |
| Jira | `SCRUM-6`, `SCRUM-34`, `SCRUM-33` no épico `SCRUM-13` |
| Validação atual | `SCRUM-6`, `SCRUM-34` e `SCRUM-33` aceitos manualmente por Ivan |
| Decisão atual | Feature de Customer implementada e aceita no escopo das três stories |
| Integração em `dev` | PR #12, merge commit `d21fd3f` |

Referências:

- [SCRUM-6 — RF01: cadastrar e identificar Customer por CPF/CNPJ][jira-scrum-6]
- [SCRUM-34 — RF02: atualizar contato do Customer](https://matheusapostulo10.atlassian.net/browse/SCRUM-34)
- [SCRUM-33 — arquivar Customer sem apagar histórico](https://matheusapostulo10.atlassian.net/browse/SCRUM-33)
- [Context Map no Miro](https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679674975757)
- [Ubiquitous Language e Aggregates atualizados no Miro][miro-aggregates]
- [Levantamento de Requisitos e Refinamento Técnico][miro-requirements]
- `AGENTS.md`
- `docs/PROJECT-STRUCTURE.md`

## Consolidação pós-implementação

As três stories estão implementadas em `dev`. A entrega consolidada inclui validação e unicidade de CPF/CNPJ,
identificação operacional por documento, atualização parcial de contato e Address, consulta histórica por ID, listagem
somente de ativos e arquivamento lógico idempotente. Os commits funcionais são `a7735e4`, `8e682e2` e `027cc75`,
integrados pela PR #12 no merge commit `d21fd3f`.

Depois da implementação original, a iniciativa transversal de autenticação JWT também foi integrada. No estado atual,
todo `/api/customers/**` exige um token válido com papel `MANAGER` ou `ADMIN`. As menções posteriores a JWT pendente
registram apenas a baseline histórica de cada story e não descrevem mais a proteção vigente em `dev`.

## Estado desta descoberta

Decidi agrupar as três stories de Customer em uma única feature e branch para reduzir o custo de aprovação e permitir
commits separados por comportamento. Essa decisão define apenas o escopo da descoberta; não significa que aprovei toda
esta especificação funcional antecipadamente.

Atualizei o trabalho corrente para `In Progress` no Jira. Vou confirmar o assignee e o status individual das demais
stories no Jira antes de iniciar cada uma, sem presumir atribuições que ainda não estejam registradas.

Alguns textos antigos ainda descrevem um módulo físico `customer`, AD-001 ou AD-017 como bloqueadores. A estrutura atual
do repositório e o Miro mais recente já estabelecem `registration` como bounded context físico, com Customer em
`registration.customer`, Flyway obrigatório e Hibernate em `validate`. Esses textos antigos não bloqueiam a descoberta,
mas deverão ser reconciliados quando a documentação estrutural afetada for atualizada.

## Problema e resultado esperado

Na baseline anterior ao `SCRUM-6`, o fluxo permitia criar, consultar, listar, renomear e atualizar email/telefone de um
Customer, mas mantinha CPF/CNPJ como texto sem validação de dígitos verificadores, não impedia duplicidade e não
oferecia identificação pelo documento. O `SCRUM-6` corrigiu essas lacunas de identidade, o `SCRUM-34` implementou a
parcial com Address e o `SCRUM-33` concluiu o ciclo de arquivamento lógico.

Isso permite identidade inválida ou duplicada, força o cliente HTTP a reenviar dados de contato que não deseja alterar e
torna uma exclusão física perigosa para o histórico da oficina.

O resultado esperado é uma gestão de Customer em que:

- CPF/CNPJ válido identifica o Customer de forma estável e imutável;
- a mesma identidade não pode ser cadastrada mais de uma vez;
- email, telefone e endereço opcional podem ser mantidos parcialmente;
- arquivamento remove o Customer das seleções para novos trabalhos sem apagar seu histórico;
- contratos REST continuam usando DTOs e preservam compatibilidade sempre que possível.

## Linguagem ubíqua

### Customer

Customer é a pessoa física ou jurídica responsável por um ou mais atendimentos da oficina. Possui identidade
própria, nome, `TaxId`, informações de contato e estado de ciclo de vida. Vehicle não pertence ao aggregate de
Customer; a relação implementada usa IDs estáveis entre os aggregates.

### TaxId

`TaxId` representa CPF ou CNPJ válido. Seu valor canônico contém somente dígitos:

- CPF possui 11 dígitos;
- CNPJ possui 14 dígitos;
- pontuação aceita na entrada é removida antes da validação;
- sequências repetidas e dígitos verificadores inválidos não representam um `TaxId` válido.

O valor não muda depois da criação do Customer. Validade é regra intrínseca do `TaxId`; unicidade depende do cadastro e
é regra coordenada pela aplicação e persistência.

### Contact Info

Contact Info reúne os value objects imutáveis `Email`, `Phone` e Address opcional. Email e telefone continuam
obrigatórios para um Customer válido.
Address pode estar ausente. Uma alteração de contato substitui somente os valores enviados e preserva os omitidos.
Quando informado no update, Address é substituído como um value object completo; sua remoção não pertence ao
`SCRUM-34`.

### Address

Address representa um endereço postal brasileiro opcional. Quando presente, contém:

- `street`, `number`, `city`, `state` e `postalCode` obrigatórios;
- `complement` e `neighborhood` opcionais;
- `state` como uma das 27 UFs brasileiras;
- `postalCode` aceita entrada com ou sem hífen e é normalizado para oito dígitos.

Aprovei essa estrutura para orientar a implementação do `SCRUM-34`. Não haverá internacionalização, geocodificação ou
consulta externa de CEP nesta story.

### Customer ativo e arquivado

Customer ativo pode ser selecionado para novos trabalhos e receber alterações administrativas. Customer arquivado
permanece persistido e consultável para fins históricos, mas não pode ser selecionado nem alterado como cadastro atual.
Arquivamento não significa exclusão física.

## Escopo funcional

### Cadastro e identificação

- cadastrar Customer pessoa física ou jurídica com nome, CPF/CNPJ e contato;
- aceitar CPF/CNPJ com ou sem pontuação e armazenar/retornar a forma canônica somente com dígitos;
- rejeitar CPF/CNPJ inválido antes de qualquer persistência;
- impedir duplicidade do mesmo `TaxId` normalizado;
- identificar um Customer ativo pelo `TaxId`;
- consultar por ID um Customer ativo ou arquivado para finalidade administrativa/histórica;
- manter o campo HTTP existente `document` como representação externa do `TaxId`, evitando quebra desnecessária.

### Manutenção de contato

- alterar email, telefone e/ou Address em um único comando parcial;
- exigir ao menos uma alteração no comando;
- preservar todo campo omitido;
- adicionar ou substituir Address somente como objeto completo;
- não remover Address nesta story;
- rejeitar email, telefone ou Address inválido;
- preservar ID, nome, `TaxId` e estado de ciclo de vida;
- manter o path `PATCH /api/customers/{id}/contact-info` e o wrapper `contactInfo`, sem invalidar clientes que já enviam
  email e telefone completos.

### Arquivamento e leitura histórica

- arquivar um Customer ativo sem apagar sua linha persistida;
- tratar novo pedido de arquivamento do mesmo Customer como operação idempotente;
- excluir Customers arquivados da listagem/seleção padrão usada por novos trabalhos;
- impedir identificação operacional por `TaxId` de um Customer arquivado;
- manter consulta histórica por ID;
- informar o estado ativo/arquivado no contrato administrativo;
- impedir rename e atualização de contato depois do arquivamento;
- nunca reutilizar o `TaxId` de um Customer arquivado em um novo cadastro.

## Atores e cenários

### Service Advisor

- cadastra um Customer com CPF/CNPJ válido e dados mínimos de contato;
- identifica um Customer ativo pelo CPF/CNPJ informado pelo cliente;
- recebe conflito quando o documento já existe, mesmo se enviado com outra pontuação;
- não consegue selecionar um Customer arquivado para novo atendimento.

### Usuário administrativo

- consulta o cadastro por ID, inclusive quando arquivado;
- altera somente os campos de contato necessários;
- adiciona ou substitui o Address opcional;
- arquiva o Customer sem apagar referências históricas;
- repete o comando de arquivamento sem produzir novo efeito.

### Consumidor de histórico

- continua lendo o Customer referenciado por registros anteriores;
- não recebe dados históricos reescritos por uma alteração cadastral posterior;
- usa IDs e snapshots definidos pelo contexto consumidor, sem importar o modelo interno de Registrations.

## Regras de negócio

### Identidade

- nome, `TaxId`, email e telefone são obrigatórios na criação;
- Address é opcional;
- CPF/CNPJ é normalizado antes da validação e comparação;
- CPF/CNPJ deve possuir tamanho, tipo e dígitos verificadores válidos;
- `TaxId` é imutável durante toda a vida do Customer;
- unicidade considera Customers ativos e arquivados;
- entradas formatadas e não formatadas do mesmo documento representam a mesma identidade;
- novo Customer começa ativo.

### Contato

- atualização parcial exige ao menos um campo solicitado;
- campo omitido permanece inalterado;
- email deve possuir formato sintaticamente válido e não pode ser vazio;
- telefone aceita formato internacional E.164 ou telefone brasileiro com 10 ou 11 dígitos, com ou sem pontuação;
- telefone brasileiro é normalizado com `+55`, e todo telefone é armazenado e retornado em formato E.164;
- Address informado deve respeitar a estrutura e os campos obrigatórios definidos nesta spec;
- Address informado substitui o Address atual por completo; ausência de `address` preserva o valor atual;
- `address: null` e qualquer tentativa de remoção são rejeitados nesta story;
- comando sem nenhum campo de contato é rejeitado; reenviar um valor igual ao atual é aceito de forma idempotente;
- nenhum comando de contato altera ID, nome, `TaxId` ou lifecycle;
- Customer arquivado não aceita atualização cadastral.

### Arquivamento

- arquivamento é lógico, irreversível nesta feature e idempotente;
- nenhuma operação de repository remove fisicamente o Customer;
- lookup histórico por ID inclui Customer arquivado;
- listagem e identificação para novo trabalho retornam somente Customers ativos;
- Customer arquivado não aceita rename nem mudança de contato;
- referências históricas existentes permanecem válidas.

### Falhas observáveis

- CPF/CNPJ ou contato inválido é rejeitado como erro de entrada sem persistência parcial;
- `TaxId` duplicado é conflito, inclusive quando a duplicidade está arquivada;
- consulta/comando para ID inexistente retorna not found;
- Customer arquivado usado em comando proibido retorna conflito de lifecycle;
- respostas não expõem classe de domínio, entidade JPA, SQL, constraint ou stack trace;
- códigos HTTP e códigos estáveis de erro serão detalhados na especificação técnica, preservando os comportamentos
  acima.

## Compatibilidade dos contratos existentes

- `POST /api/customers` continua aceitando `name`, `document` e `contactInfo`; Address será aditivo e opcional.
- respostas de Customer passam a apresentar `contactInfo.address`; quando ausente, seu valor é `null`.
- `document` continua como nome externo para reduzir quebra de clientes, mas seu valor passa a ser validado e
  retornado de forma normalizada.
- `PATCH /api/customers/{id}/contact-info` continua sendo o comando de contato, mantém o wrapper `contactInfo` e passa a
  aceitar alterações parciais.
- consulta atual por ID permanece disponível e ganha visibilidade do lifecycle de forma aditiva.
- listagem atual passa a representar a visão de Customers ativos; consulta histórica continua individual por ID.
- identificação por `TaxId` e arquivamento são capacidades novas. Paths, bodies e status exatos pertencem à
  especificação técnica.

Qualquer quebra diferente das mudanças funcionais explicitamente descritas acima exige retorno desta spec para `Draft` e
nova aprovação.

## Registro de validação funcional

### 2026-08-16 — SCRUM-6

- Validação realizada por mim, Ivan Pimentel.
- Resultado: validei manualmente o comportamento após testes no Postman e inspeção do MySQL por extensão SQL.
- Ambiente: Java 21.0.12, MySQL 8.0.46, banco local `workshop` e API em `http://localhost:8080`.
- Evidências observadas: cadastro válido HTTP 201; documento normalizado; identificação por CPF/CNPJ HTTP 200; dados
  persistidos e consultáveis no banco.
- Evidência automatizada complementar: `./mvnw verify` com 60 testes, zero falhas, erros ou testes ignorados; migrações
  Flyway, contrato OpenAPI e fronteiras do Spring Modulith verdes.
- Limite histórico desta validação: naquele momento, somente o comportamento do `SCRUM-6` estava implementado; as
  entregas posteriores de `SCRUM-34` e `SCRUM-33` possuem registros próprios abaixo.

### 2026-08-16 — decisões do SCRUM-34

- Revisei o requisito oficial do Tech Challenge, os critérios atuais do Jira e o contrato existente antes da decisão.
- Aprovei Address brasileiro estruturado e atômico, com `street`, `number`, `city`, `state` e `postalCode` obrigatórios;
  `complement` e `neighborhood` permanecem opcionais.
- Aprovei `Email`, `Phone` e `Address` como value objects imutáveis pertencentes ao Contact Info.
- Aprovei update parcial com campos omitidos preservados, mantendo o endpoint e o wrapper `contactInfo` atuais.
- Aprovei telefone brasileiro ou E.164 na entrada, sempre armazenado e retornado em E.164.
- Decidi não incluir remoção de Address no `SCRUM-34`, pois ela não é exigida pelo Tech Challenge nem pelo Jira.
- PATCH vazio será inválido; reenvio de valores iguais será idempotente.
- JWT continua obrigatório para a entrega geral do Tech Challenge, mas será tratado em trabalho próprio e não neste
  slice de manutenção de contato.
- Esta aprovação libera a implementação do `SCRUM-34`; não significa que a story já esteja concluída.

### 2026-08-16 — implementação e validação técnica do SCRUM-34

- O comportamento aprovado foi implementado no domínio, aplicação, HTTP e persistência.
- O gate completo passou com 72 testes e o código alterado alcançou 96,67% de cobertura de linhas.
- Flyway, Hibernate validate, OpenAPI, Postman e as fronteiras do Spring Modulith permaneceram verdes.
- O fluxo foi validado tecnicamente em MySQL 8.0.46 com dados fictícios, incluindo normalização e persistência.
- O aceite manual independente de Ivan permanece como revisão posterior e não foi presumido pelo agente.

### 2026-08-17 — aceite manual do SCRUM-34

- Ivan concluiu a validação manual pelo Postman e aceitou o comportamento entregue no commit `8e682e2`.
- Foram exercitados payloads válidos e inválidos, updates parciais, Address atômico e Customer inexistente.
- A prioridade atual de validação está correta: um corpo inválido é rejeitado antes da consulta de existência do
  Customer.
- Foi identificado que o parser de UUID do Java aceita algumas formas textuais encurtadas. Assim, remover um caractere
  pode produzir outro UUID válido e resultar em `CUSTOMER_NOT_FOUND` quando o corpo estiver correto.
- A validação estrita do UUID canônico de 36 caracteres foi registrada como melhoria transversal não bloqueante e ficou
  explicitamente fora do MVP atual.

### 2026-08-17 — aprovação funcional do SCRUM-33

- O Jira foi relido diretamente: a story está `In Progress`, atribuída a Ivan, prioridade Medium e planejamento `READY`.
- Os critérios vinculantes são: lifecycle ativo/arquivado, comando explícito de domínio, seleção somente de ativos,
  consulta histórica por ID, not found para ID ausente e proibição de exclusão física.
- O enforcement de Customer ativo dentro de Service Order pertence ao épico de Service Lifecycle e não será antecipado.
- A referência antiga a AD-017 não bloqueia a story: a política vigente do repositório exige nova migration Flyway e
  Hibernate `ddl-auto=validate`.
- Pacote funcional aprovado: arquivamento irreversível e idempotente; listagem e identificação por `TaxId`
  somente para ativos; consulta por ID histórica; unicidade de `TaxId` preservada também para arquivados; rename e
  atualização de contato bloqueados após o arquivamento.
- Ivan aprovou explicitamente esse pacote em 2026-08-17 e optou pela solução simples com lifecycle representado por
  booleano. Reativação permanece fora do MVP e o estado ativo não será derivado da existência de Service Order.

### 2026-08-17 — aceite manual do SCRUM-33

- Ivan confirmou explicitamente a conclusão da validação manual da implementação entregue no commit `027cc75`.
- Resultado: comportamento aceito; a feature de Customer está concluída no escopo de `SCRUM-6`, `SCRUM-34` e
  `SCRUM-33`.
- Este registro não presume cenários adicionais além da confirmação de aceite fornecida por Ivan.

## Decisões funcionais aprovadas

- [x] `TaxId` canônico é retornado somente com dígitos, mantendo `document` como nome do campo HTTP.
- [x] Unicidade de `TaxId` abrange Customers ativos e arquivados; documento arquivado não pode ser reutilizado.
- [x] Contact Info mantém email e telefone obrigatórios; Address é opcional.
- [x] Telefone aceita entrada brasileira ou E.164 e usa E.164 como representação canônica.
- [x] Address usa os campos e validações descritos nesta spec e é substituído como objeto completo.
- [x] Campo omitido em update parcial preserva o valor; remoção de Address fica fora do `SCRUM-34`.
- [x] Arquivamento é irreversível e idempotente nesta feature.
- [x] Listagem e lookup operacional por `TaxId` retornam somente ativos; lookup histórico por ID inclui arquivados.
- [x] Customer arquivado não aceita rename nem alteração de contato.
- [x] Os contratos atuais são evoluídos de forma aditiva conforme a seção de compatibilidade.

### Gate funcional da SCRUM-33

- [x] Ivan aprovou arquivamento irreversível e idempotente.
- [x] Ivan aprovou que listagem e identificação por `TaxId` representem somente Customers ativos.
- [x] Ivan aprovou que `GET /api/customers/{id}` continue histórico e inclua arquivados.
- [x] Ivan aprovou que Customer arquivado não aceite rename nem atualização de contato.
- [x] Ivan aprovou que o `TaxId` permaneça reservado depois do arquivamento.
- [x] Ivan aprovou a exposição aditiva do lifecycle no contrato de resposta.

## Dívidas deliberadamente adiadas

- Validação estrita da representação textual de UUID: paths com UUID deveriam aceitar somente a forma canônica com
  36 caracteres. O conversor padrão do Java aceita algumas formas encurtadas e pode transformá-las em outro UUID válido.
  O comportamento foi reproduzido fora do Postman, não é cache e não bloqueia o MVP atual por decisão de Ivan.
- Autenticação e autorização administrativa permanecem obrigatórias para a entrega geral, mas serão tratadas no slice
  próprio de segurança/JWT.

## Fora de escopo

- implementar Vehicle ou ServiceCatalog;
- manter uma coleção mutável de Vehicles dentro de Customer;
- implementar autenticação, JWT, autorização por papel ou ownership dentro desta feature; o requisito permanece
  obrigatório para a entrega geral do Tech Challenge e deve ser acompanhado separadamente;
- reativar Customer arquivado;
- apagar Customer fisicamente;
- reutilizar CPF/CNPJ arquivado;
- registrar histórico/versionamento de cada alteração de contato;
- remover Address depois de cadastrado; essa capacidade poderá ser avaliada em evolução futura;
- criar audit trail de ator, motivo ou data de arquivamento;
- validar endereço em serviço externo, geocodificar ou consultar CEP;
- internacionalizar Address para outros países;
- alterar regras de rename além de impedir alteração em Customer arquivado;
- alterar snapshots ou referências históricas pertencentes a Service Lifecycle;
- definir endpoints, schema, índices, classes ou estratégia de migração — assuntos da especificação técnica;
- alterar status, assignee ou conteúdo das stories no Jira.

## Critérios de aceite

### SCRUM-6 — identidade por CPF/CNPJ

- [x] CPF e CNPJ válidos, formatados ou não, permitem cadastrar um Customer.
- [x] A resposta apresenta o `document` normalizado somente com dígitos.
- [x] Tamanho inválido, caracteres não aceitos, sequência repetida e dígito verificador inválido são rejeitados antes da
  persistência.
- [x] Cadastro duplicado do mesmo `TaxId` normalizado retorna conflito.
- [x] Reutilização de `TaxId` depois do arquivamento continua proibida.
- [x] Um Customer pode ser identificado pelo `TaxId` normalizado.
- [x] `TaxId` não pode ser alterado por nenhum comando posterior à criação.
- [x] Entrada inválida e registro inexistente usam respostas de erro estáveis sem detalhes internos.

### SCRUM-34 — manutenção parcial de contato

- [x] Email, telefone e Address válidos podem ser alterados e são retornados após persistência.
- [x] É possível alterar qualquer subconjunto de email, telefone e Address.
- [x] Campo omitido permanece inalterado.
- [x] Address informado substitui o objeto completo; Address omitido permanece inalterado.
- [x] Comando sem qualquer alteração é rejeitado.
- [x] Email, telefone ou Address inválido é rejeitado sem salvar estado parcial.
- [x] ID, nome, `TaxId` e lifecycle permanecem inalterados.
- [x] Customer inexistente retorna not found.
- [x] Customer arquivado não aceita a alteração.
- [x] REST retorna somente DTOs, nunca objetos de domínio ou JPA.

### SCRUM-33 — arquivamento lógico

- [x] Arquivar um Customer ativo altera seu estado sem apagar a linha persistida.
- [x] Repetir o arquivamento produz o mesmo estado final sem duplicar efeitos.
- [x] Customer arquivado deixa de aparecer na listagem/seleção padrão de ativos.
- [x] Customer arquivado não é identificado como elegível para novo trabalho pelo `TaxId`.
- [x] Customer arquivado continua consultável por ID para finalidade histórica.
- [x] Customer arquivado não aceita rename nem atualização de contato.
- [x] Arquivar ID inexistente retorna not found.
- [x] Nenhum adapter executa exclusão física do Customer.
- [x] Referências e snapshots históricos existentes permanecem legíveis e inalterados.

### Critérios transversais

- [x] O comportamento existente de criação, consulta por ID, listagem, rename e atualização completa de contato
  permanece compatível, exceto pelas validações e regras de lifecycle explicitamente aprovadas nesta spec.
- [x] Todas as mudanças observáveis de request, response, validação ou status são documentadas posteriormente em
  OpenAPI e Postman na mesma feature.
- [x] Cada critério implementado é rastreável a teste automatizado e evidência no plano de implementação.
- [x] As fronteiras de `registration` permanecem válidas e nenhum modelo interno de outro bounded context é importado.

[jira-scrum-6]: https://matheusapostulo10.atlassian.net/browse/SCRUM-6
[miro-aggregates]: https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684049703
[miro-requirements]: https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679721508363

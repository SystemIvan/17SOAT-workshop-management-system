# Especificação Técnica: Gestão de Vehicles

| Campo | Valor |
|---|---|
| Feature | `vehicle-management` — `SCRUM-7`, `SCRUM-36`, `SCRUM-35` e `SCRUM-37` |
| Status | Quatro stories implementadas, aceitas e integradas em `dev` |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovação técnica | `SCRUM-7` e `SCRUM-36`: 2026-08-17; `SCRUM-35`: 2026-08-22; `SCRUM-37`: 2026-08-23 |
| Implementação aceita | `SCRUM-7` e `SCRUM-36`: 2026-08-17; `SCRUM-35` e `SCRUM-37`: Ivan em 2026-08-23 |
| Commit local | Stories: `cd2f903`, `d5adcc9`, `4f7d346`, `d73ff8d`; Postman: `44ce9b0`; reconciliação: `5c96181` |
| Integração remota | `SCRUM-7`/`SCRUM-36`: PR #12 (`d21fd3f`); `SCRUM-35`/`SCRUM-37`: PR #26 (`6b9f223`) |
| Especificação funcional | `SCRUM-35`: 2026-08-22; `SCRUM-37`: `Approved` por Ivan em 2026-08-23 |
| Escopo desta revisão | Desenho técnico aprovado da `SCRUM-37` |
| Baseline | Branch reconciliada e publicada em `5c96181`; `verify` com 387 testes verdes |

## Estado técnico integrado

O vertical slice completo permanece em `registration.vehicle`: aggregate e value objects livres de framework, use cases
transacionais, persistence adapter JPA, migrations Flyway, contratos HTTP e API pública mínima de elegibilidade. Service
Lifecycle consome a elegibilidade por port próprio e mantém o lock do Vehicle até o commit da nova Service Order.

A segurança transversal posterior protege `/api/vehicles/**` com JWT e os papéis `MANAGER` ou `ADMIN`. As quatro stories
foram integradas pelas PRs #12 e #26; as referências históricas a gates bloqueados ou PR Draft não representam o estado
atual.

As seções anteriores ao adendo da `SCRUM-36` documentam exclusivamente o cadastro entregue pela `SCRUM-7` e
permanecem como baseline implementada. O novo desenho técnico começa em “SCRUM-36 — atualização descritiva e de
chassis” e não altera retroativamente as decisões do commit `cd2f903`.

A seção “SCRUM-35 — quilometragem de Vehicle” registra o desenho aprovado por Ivan em 2026-08-22. A implementação foi
verificada, aceita em 2026-08-23 e registrada no commit local `4f7d346`.

A seção “SCRUM-37 — arquivamento, consultas e elegibilidade para novo trabalho” foi aprovada por Ivan em 2026-08-23.
O plano, o código e o aceite foram concluídos posteriormente, e a entrega foi integrada em `dev` pela PR #26.

## Contexto e desenho

A mudança permanece no bounded context `registration` e substitui somente o placeholder
`registration.vehicle.package-info` por um vertical slice próprio. Nenhum novo módulo Spring Modulith ou bounded context
é criado. A estrutura segue o padrão dos aggregates existentes:

- `registration.vehicle.domain.model`: `Vehicle`, `LicensePlate`, `ChassisNumber` e `VehicleYear`;
- `registration.vehicle.domain.repository`: porta `VehicleRepository`;
- `registration.vehicle.application.dto`: request, response e mapper;
- `registration.vehicle.application.exception`: conflitos de placa e chassis;
- `registration.vehicle.application.usecase`: `CreateVehicleUseCase`;
- `registration.vehicle.infrastructure.persistence`: entidade JPA, mapper, repository Spring Data e adapter;
- `registration.vehicle.infrastructure.web`: controller e tradução HTTP de falhas.

`Vehicle` é um aggregate root independente, livre de Spring, JPA e transporte. Seu estado inicial contém:

- `id: UUID`, gerado pelo domínio;
- `customerId: UUID`, referência estável sem associação de objeto ou coleção em Customer;
- `LicensePlate`, obrigatório;
- `ChassisNumber`, opcional;
- `brand`, `model` e `color`, obrigatórios, normalizados com `trim` e limitados no contrato;
- `VehicleYear`, obrigatório;
- `active: boolean`, iniciado como `true` para preparar o lifecycle aprovado sem antecipar o arquivamento.

O aggregate expõe `id()` como identificador. Não haverá setters públicos. Criação e futura mudança de estado
devem ocorrer por factories e métodos de negócio. `ChassisNumber` ausente é mantido como ausência no aggregate e como
`null` nos adapters; o value object somente existe quando um valor foi informado.

### Value objects e invariantes

`LicensePlate`:

- remove somente espaços externos e converte letras para maiúsculas com `Locale.ROOT`;
- aceita `AAA0000`, `AAA-0000` e `AAA0A00`;
- remove o hífen apenas da placa legada e mantém sete caracteres canônicos;
- rejeita espaços internos, hífen Mercosul, símbolos e qualquer outro formato.

`ChassisNumber`:

- é criado somente quando o campo não é `null`;
- remove espaços externos e converte letras para maiúsculas com `Locale.ROOT`;
- exige exatamente 17 caracteres ASCII alfanuméricos;
- rejeita vazio, espaços internos e separadores;
- não aplica regra adicional de fabricante, região, dígito ou caracteres especiais de VIN nesta story.

`VehicleYear`:

- aceita valor entre 1886 e `currentYear + 1`, inclusive;
- recebe o ano atual a partir de um `Clock` injetado na aplicação, evitando teste dependente do relógio real;
- não consulta infraestrutura nem relógio diretamente dentro do value object.

Marca, modelo e cor permanecem strings do aggregate para evitar value objects sem comportamento próprio. O domínio
rejeita valores nulos ou em branco e armazena os valores sem espaços externos.

## Coordenação com Customer

`CreateVehicleUseCase` injeta `CustomerRepository` e `VehicleRepository`. Essa dependência permanece dentro do mesmo
módulo `registration`, segue de Vehicle para o aggregate Customer já implementado e não cria ciclo.

A porta de Customer recebe `findByIdForUpdate(customerId)`, implementado com `PESSIMISTIC_WRITE`. A consulta possui
semântica histórica e serializa a criação de Vehicle com um arquivamento concorrente do mesmo Customer:

- ausência lança `CustomerNotFoundException`;
- Customer com `active=false` lança `CustomerArchivedException`;
- Customer ativo apenas autoriza a continuação do fluxo.

O objeto Customer é usado transitoriamente pela aplicação para verificar elegibilidade e nunca é armazenado dentro de
Vehicle, retornado no contrato ou alterado. Nenhuma coleção de Vehicles é adicionada a Customer. Não será criado um port
adicional nesta story porque os dois aggregates pertencem ao mesmo módulo e a extensão explícita do repository existente
oferece a consistência necessária sem acrescentar outra fronteira abstrata.

Se Vehicle obtiver o lock primeiro, seu cadastro lineariza antes do arquivamento; se o update de arquivamento obtiver o
lock primeiro, a leitura bloqueante observa `active=false` e rejeita a criação. O lock cobre somente a linha do Customer
referenciado e permanece dentro da transação curta do cadastro.

## Fluxo de aplicação e transação

`CreateVehicleUseCase.execute(CreateVehicleRequest)` usa `@Transactional` e executa, nesta ordem:

1. constrói o aggregate com `id` aleatório e `active=true`, validando value objects e dados descritivos;
2. consulta e bloqueia o Customer por `customerId`, exigindo que esteja ativo;
3. verifica duplicidade da placa canônica;
4. quando houver chassis, verifica duplicidade do chassis canônico;
5. salva e força flush por meio da porta de Vehicle;
6. mapeia o aggregate persistido para `VehicleResponse`.

Essa ordem faz dados inválidos falharem antes de consultas e define precedência determinística: Customer ausente ou
arquivado é observado antes de conflitos de identidade; se placa e chassis já existirem, o conflito de placa prevalece.
Qualquer exceção provoca rollback e não altera Customer.

As verificações `exists` produzem erros claros no fluxo comum. Constraints únicas no banco continuam sendo a proteção
autoritativa contra duas requisições concorrentes. O adapter usa `saveAndFlush` e traduz somente as constraints
conhecidas; violações desconhecidas continuam como falha técnica, sem serem mascaradas como regra de negócio.

Um bean `Clock` com o fuso padrão da aplicação será injetado no use case. Testes de aplicação usarão `Clock.fixed` para
exercitar os limites de `VehicleYear` de maneira determinística.

## Contrato HTTP

### `POST /api/vehicles`

Request `CreateVehicleRequest`:

```json
{
  "customerId": "ca0416e2-86da-4eaa-b27e-d4a9262f51e6",
  "licensePlate": "ABC-1234",
  "chassis": "9BWZZZ377VT004251",
  "brand": "Volkswagen",
  "model": "Gol",
  "year": 2026,
  "color": "Prata"
}
```

- `customerId`: `UUID` obrigatório;
- `licensePlate`: obrigatório, máximo de 16 caracteres na borda para acomodar espaços externos;
- `chassis`: opcional e nullable; quando presente, máximo de 32 caracteres na borda e validação completa no domínio;
- `brand` e `model`: obrigatórios, máximo de 100 caracteres cada;
- `year`: inteiro obrigatório, com mínimo estático 1886 na borda e limite dinâmico no domínio;
- `color`: obrigatório, máximo de 50 caracteres.

Bean Validation rejeita nulos, strings em branco e tamanhos abusivos antes do use case. As invariantes e normalizações
são repetidas no domínio para proteger qualquer entrada não HTTP.

Resposta `201 Created` com `Location: /api/vehicles/{id}` e `VehicleResponse`:

```json
{
  "id": "8aedf48c-96ed-4dad-b860-9ed8a527cfb9",
  "customerId": "ca0416e2-86da-4eaa-b27e-d4a9262f51e6",
  "licensePlate": "ABC1234",
  "chassis": "9BWZZZ377VT004251",
  "brand": "Volkswagen",
  "model": "Gol",
  "year": 2026,
  "color": "Prata",
  "active": true
}
```

Quando chassis for omitido ou `null`, a resposta usa `"chassis": null`. O response expõe records próprios e nunca
classes de domínio ou JPA. O campo identificador próprio se chama `id`, seguindo o padrão atual de Customer.

`VehicleController` recebe somente `CreateVehicleUseCase`, usa constructor injection e documenta operação, schemas,
status e erros com Springdoc. A collection Postman adiciona a pasta Vehicle, exemplo com chassis e exemplo sem chassis.

## Tradução de falhas

`VehicleExceptionHandler`, limitado a `VehicleController`, preserva o formato global `ErrorResponse`:

| Condição | HTTP | Código estável |
|---|---:|---|
| Bean Validation, JSON ou tipo inválido | 400 | `VALIDATION_ERROR` |
| Invariante de Vehicle inválida | 400 | `INVALID_VEHICLE` |
| Customer inexistente | 404 | `CUSTOMER_NOT_FOUND` |
| Customer arquivado | 409 | `CUSTOMER_ARCHIVED` |
| Placa duplicada | 409 | `VEHICLE_LICENSE_PLATE_ALREADY_EXISTS` |
| Chassis informado duplicado | 409 | `VEHICLE_CHASSIS_ALREADY_EXISTS` |

As duas exceções de Customer existentes serão reutilizadas para manter o mesmo significado e código. Mensagens serão em
português e não repetirão placa, chassis, `customerId`, SQL, nome de constraint, entidade ou stack trace.

O handler específico captura `IllegalArgumentException` originada pelo domínio de Vehicle para impedir que o handler
global legado a classifique como `INVALID_STOCK_ITEM`. A correção transversal desse código global permanece fora do
escopo e não será misturada nesta feature.

## Persistência e dados de bootstrap

Uma migration Flyway nova e aditiva, nomeada no momento da implementação como
`V<timestamp_utc>__create_vehicles.sql`, cria:

```sql
CREATE TABLE vehicles (
    id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    license_plate VARCHAR(7) NOT NULL,
    chassis_number VARCHAR(17) NULL,
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    model_year INTEGER NOT NULL,
    color VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_vehicles_license_plate UNIQUE (license_plate),
    CONSTRAINT uk_vehicles_chassis_number UNIQUE (chassis_number),
    CONSTRAINT ck_vehicles_model_year_min CHECK (model_year >= 1886),
    CONSTRAINT fk_vehicles_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);
```

Um índice `idx_vehicles_customer_id` será criado explicitamente para consultas futuras por Customer. Não haverá cascade
de exclusão; a FK usa o comportamento restritivo padrão. A regra dinâmica do limite superior de ano não é colocada em
`CHECK`, pois depende do ano corrente, e permanece no domínio/aplicação.

MySQL permite múltiplos valores `NULL` sob uma constraint unique, possibilitando vários Vehicles sem chassis e
mantendo unicidade somente para valores informados. O teste H2 em modo MySQL verificará essa semântica.

`VehicleJpaEntity` é separado do domínio, usa UUID binário conforme a configuração atual e implementa
`Persistable<UUID>` como Customer. `VehiclePersistenceMapper` faz round-trip de ausência de chassis, valores canônicos e
`active`. A porta mínima de `VehicleRepository` contém:

- `boolean existsByLicensePlate(LicensePlate licensePlate)`;
- `boolean existsByChassisNumber(ChassisNumber chassisNumber)`;
- `void save(Vehicle vehicle)`.

Não serão adicionados `find`, `list`, `delete` ou métodos de update sem uma story aprovada.

Classificação: **nenhum seed requerido**. Vehicle não é dado de referência obrigatório e exemplos de negócio não serão
inseridos em produção. A story não adiciona seeder de demonstração; testes criam fixtures próprias.

Hibernate permanece com `ddl-auto=validate`. A migration é forward-only e não altera tabelas ou dados de Customer.

## Compatibilidade e integração

- A mudança é aditiva: nenhum endpoint, payload ou comportamento existente é removido ou renomeado.
- O novo package continua interno ao módulo `registration`; `ModuleStructureTest` deve manter exatamente três módulos.
- Service Lifecycle não é alterado. O `CreateServiceOrderRequest` continua recebendo o snapshot atual nesta story.
- `service_orders.vehicle_id` não recebe FK para `vehicles` agora, pois a validação e o contrato entre módulos pertencem
  ao épico de Service Lifecycle.
- A futura integração usará API pública, evento ou port consumidor e continuará preservando `VehicleSnapshot` histórico.
- A branch está empilhada sobre `027cc75`. Antes da publicação, sua base será comparada à `dev` que recebeu o PR #12;
  merge por commit preservando o head não exige reescrita, enquanto squash/rebase exige realinhamento e nova validação.

## Segurança e operação

- DTO fechado impede mass assignment de `id`, `active` e estado interno.
- Bean Validation limita tamanho e tipo; o domínio repete invariantes relevantes.
- JPA usa parâmetros e não monta SQL com dados do usuário.
- Placa, chassis e identificadores não aparecem em mensagens de erro ou logs adicionados pela feature.
- A resposta contém dados operacionais do Vehicle e IDs, mas nenhum dado pessoal de contato ou documento do Customer.
- A distinção entre Customer ausente e arquivado foi aprovada funcionalmente; JWT limita os endpoints de Vehicle aos
  papéis `MANAGER` e `ADMIN`.
- Autenticação e autorização foram entregues posteriormente pelo slice transversal de JWT.
- Não há upload, chamada externa, segredo, credencial ou dependência nova.
- Constraints nomeadas protegem concorrência sem expor seus nomes ao cliente.
- Rollout aplica Flyway antes de iniciar a versão que contém `VehicleJpaEntity`; rollback de aplicação deixa a tabela
  aditiva sem uso e sem perda de dados.
- Não existe operação destrutiva, cascade ou hard delete nesta story.

## Estratégia de testes

### Domínio

- placas legada, legada com hífen e Mercosul; caixa, espaços externos e representação canônica;
- rejeição de formato, espaço interno, símbolo, hífen Mercosul e `null`;
- chassis ausente, válido, caixa/espaços externos, vazio, tamanho e caracteres inválidos;
- ano nos limites 1886 e `currentYear + 1`, além dos dois valores externos;
- marca, modelo e cor obrigatórios e normalizados;
- criação com `id`, `customerId` e `active=true`.

### Aplicação

- sucesso com e sem chassis usando `Clock.fixed`;
- Customer ativo, ausente e arquivado;
- placa duplicada e chassis duplicado, incluindo precedência quando ambos duplicam;
- nenhuma consulta/save após falha de validação e nenhum save após falhas de Customer ou unicidade;
- Customer permanece inalterado e o response usa `id`.

### Persistência

- Flyway e Hibernate validate em banco vazio;
- round-trip com chassis presente e ausente;
- múltiplos Vehicles com chassis `NULL`;
- constraints únicas de placa e chassis traduzidas pelo adapter sob cenário concorrente-like;
- FK impede `customerId` inexistente mesmo fora do use case;
- lock pessimista serializa cadastro com arquivamento concorrente do mesmo Customer;
- nomes, tamanhos, UUID binário, `active` e índice coerentes com o mapeamento.

### HTTP e contrato

- `201`, `Location`, body canônico e campo `id`;
- cadastro com chassis omitido e `null`;
- payloads inválidos, Customer ausente/arquivado e conflitos com os códigos estáveis definidos;
- presença de `id`, ausência de `vehicleId` próprio e nenhum objeto interno na resposta;
- `/v3/api-docs` contém path, schemas, nullable de chassis, respostas e exemplos;
- collection Postman permanece JSON válido e contém fluxos com e sem chassis.

### Gates

- `ModuleStructureTest` e fronteiras acíclicas;
- `make test` durante o desenvolvimento;
- `make verify` antes da conclusão;
- `make coverage`, sem reduzir a cobertura do código alterado e buscando pelo menos 80% no slice;
- validação manual em MySQL para normalização, chassis `NULL`, unicidade, FK e resposta HTTP.

## Gate técnico aprovado da SCRUM-7

- [x] Consulta direta e bloqueante a `CustomerRepository` dentro do mesmo módulo `registration`.
- [x] `POST /api/vehicles`, request e `VehicleResponse` com `id`.
- [x] `Clock` injetado e limite dinâmico de ano no domínio/aplicação.
- [x] Tabela `vehicles`, FK sem cascade, constraints únicas e chassis nullable.
- [x] Códigos estáveis de erro e respectiva precedência.
- [x] Classificação **nenhum seed requerido**.
- [x] Estratégia de segurança, testes e rollout.

## SCRUM-36 — atualização descritiva e de chassis

### Estado do gate técnico

Esta seção foi aprovada por Ivan Pimentel em 2026-08-17 e deriva da especificação funcional aprovada da `SCRUM-36`.
Essa aprovação libera somente a elaboração do plano de implementação; código, migration, endpoint e testes continuam
dependendo de autorização explícita para iniciar.

O desenho preserva a decisão de MVP sobre chassis: omissão, `null`, vazio ou somente espaços significa preservar;
valor não vazio permite incluir ou substituir; remoção continua indisponível. Os quatro dados descritivos permanecem
obrigatórios em cada comando.

### Impacto arquitetural

A mudança permanece integralmente em `registration.vehicle`, dentro do módulo Spring Modulith `registration`. Não cria
novo módulo, port entre bounded contexts ou dependência de Service Lifecycle. O slice existente será estendido com:

- comando de domínio para atualizar descrições e, opcionalmente, chassis;
- `UpdateVehicleRequest` e reutilização de `VehicleResponse`;
- `UpdateVehicleUseCase`;
- lookup bloqueante e verificação de chassis pertencente a outro Vehicle na porta `VehicleRepository`;
- `VehicleNotFoundException` e `VehicleArchivedException`;
- operação HTTP de atualização no `VehicleController` e novos mapeamentos no `VehicleExceptionHandler`.

O aggregate continua framework-free. A operação não acessa `registration.customer` porque `customerId` não muda. Ela
também não acessa `servicelifecycle`: os snapshots existentes pertencem ao consumidor e permanecem isolados por ausência
de chamada, import ou persistência compartilhada.

### Modelo de domínio

`Vehicle` passa a oferecer um método de negócio semelhante a:

```java
void updateDetails(
        String brand,
        String model,
        VehicleYear year,
        String color,
        ChassisNumber chassisUpdate)
```

`chassisUpdate == null` significa preservar o chassis atual, inclusive quando ele também é ausente. Um
`ChassisNumber` não nulo substitui o estado atual. A aplicação converte campo omitido, `null`, vazio ou em branco para
ausência de update; somente texto não vazio cria o value object.

O método:

1. rejeita `active=false` com `VehicleArchivedException`;
2. valida marca, modelo, ano e cor em variáveis locais;
3. somente depois de validar todo o conjunto atribui os novos valores;
4. substitui chassis apenas quando `chassisUpdate` não é nulo;
5. aceita o mesmo estado final de forma idempotente.

`year` deixa de ser `final`, mas continua encapsulado por `VehicleYear`. `id`, `customerId` e `licensePlate` permanecem
`final`; `active` não recebe mutação nesta story. Não serão adicionados setters públicos. A validação de marca, modelo e
cor reutiliza `normalizeRequired` e os limites atuais. `VehicleYear.create` continua usando o ano obtido pelo `Clock` já
configurado.

Todos os valores que podem falhar são validados antes da primeira atribuição. Assim, uma exceção dentro do aggregate
não deixa marca, modelo, ano, cor ou chassis parcialmente alterado em memória.

### DTO e fluxo de aplicação

`UpdateVehicleRequest` será um record fechado com:

- `brand`: obrigatório, não vazio, máximo de 100 caracteres;
- `model`: obrigatório, não vazio, máximo de 100 caracteres;
- `year`: inteiro obrigatório, mínimo estático de 1886 e limite superior dinâmico no domínio;
- `color`: obrigatório, não vazio, máximo de 50 caracteres;
- `chassis`: opcional e nullable; omitido, `null`, vazio ou em branco preserva o valor atual.

O campo chassis não recebe `@NotBlank`. Texto não vazio é convertido por `ChassisNumber`, que aplica `strip`, caixa
alta e os 17 caracteres alfanuméricos. A especificação OpenAPI documentará que valores sem conteúdo não removem nem
invalidam o chassis. Limite abusivo de texto não vazio será rejeitado pelas invariantes antes da persistência sem ecoar
o conteúdo recebido.

`UpdateVehicleUseCase.execute(UUID id, UpdateVehicleRequest request)` usa `@Transactional` e o `Clock` existente. A
ordem proposta é:

1. converter ano e eventual chassis não vazio em value objects;
2. localizar e bloquear o Vehicle pelo ID;
3. capturar o chassis atual para identificar mudança efetiva;
4. executar `vehicle.updateDetails(...)`, que rejeita Vehicle arquivado e valida o conjunto completo;
5. quando houver novo chassis diferente do atual, verificar se ele pertence a outro Vehicle;
6. salvar com flush e mapear o aggregate para `VehicleResponse`;
7. concluir a transação e liberar o lock.

Entrada inválida falha antes do lookup quando puder ser validada na borda ou ao construir os value objects. Depois do
lookup, Vehicle arquivado prevalece sobre conflito de chassis. A verificação de duplicidade ocorre antes do save no
fluxo comum, e a constraint única continua autoritativa contra corrida.

Chassis igual ao valor canônico atual não executa consulta de duplicidade. Chassis omitido, `null`, vazio ou em branco
também não executa consulta nem mutação. Nenhum caminho consulta Customer, placa ou Service Order.

### Contrato HTTP

#### `PATCH /api/vehicles/{id}`

Request:

```json
{
  "brand": "Volkswagen",
  "model": "Polo",
  "year": 2026,
  "color": "Azul",
  "chassis": "9BWZZZ377VT004251"
}
```

Os quatro campos descritivos são obrigatórios. `chassis` pode ser omitido ou enviado como `null` ou texto sem conteúdo
para preservar o valor atual. Quando contiver texto, permite preencher ou substituir chassis após normalização e
verificação de unicidade.

Sucesso retorna `200 OK` com o `VehicleResponse` completo já existente. O response mantém `id`, `customerId`,
`licensePlate`, `active` e o chassis atual ou novo, além das descrições atualizadas. Não será criado um segundo schema
de response nem serão expostos objetos de domínio/JPA.

`id`, `customerId`, `licensePlate`, `active` e futura quilometragem não pertencem ao request. Se aparecerem como campos
desconhecidos, nunca serão vinculados ao aggregate nem produzirão mass assignment. Uma política transversal de rejeição
de propriedades JSON desconhecidas não será criada somente para esta story.

O endpoint de cadastro permanece inalterado. A collection Postman adicionará uma requisição de atualização que usa a
variável `vehicleId`, além de exemplos de preservação, inclusão e substituição de chassis.

### Falhas e códigos estáveis

O handler específico de Vehicle será ampliado sem expor dados operacionais:

| Condição | HTTP | Código estável | Exceção prevista |
|---|---:|---|---|
| Bean Validation, JSON ou UUID inválido | 400 | `VALIDATION_ERROR` | Tratamento de boundary |
| Ano dinâmico, descrição ou chassis não vazio inválido | 400 | `INVALID_VEHICLE` | `IllegalArgumentException` |
| Vehicle inexistente | 404 | `VEHICLE_NOT_FOUND` | `VehicleNotFoundException` |
| Vehicle arquivado | 409 | `VEHICLE_ARCHIVED` | `VehicleArchivedException` |
| Chassis pertencente a outro Vehicle | 409 | `VEHICLE_CHASSIS_ALREADY_EXISTS` | Exceção existente |

Chassis omitido, `null`, vazio ou em branco não é falha. Reenvio do chassis do próprio Vehicle também não é conflito.
Mensagens permanecem em português e não repetem ID, placa, chassis, SQL, nome de constraint, entidade ou stack trace.

`VehicleNotFoundException` pertence à aplicação. `VehicleArchivedException` pertence ao domínio, pois protege qualquer
comando mutável futuro do aggregate. `VehicleChassisAlreadyExistsException` continua sendo reutilizada tanto no precheck
quanto na tradução da constraint `uk_vehicles_chassis_number` durante o flush.

Falhas inesperadas de banco, lock ou constraint desconhecida continuam técnicas e não serão mascaradas como uma regra de
negócio conhecida.

### Persistência, concorrência e dados

A porta `VehicleRepository` será ampliada com operações de intenção explícita:

```java
Optional<Vehicle> findByIdForUpdate(UUID id);

boolean existsByChassisNumberAndIdNot(ChassisNumber chassisNumber, UUID id);
```

`findByIdForUpdate` usa `PESSIMISTIC_WRITE` sobre a linha alvo. Isso serializa duas atualizações concorrentes do mesmo
Vehicle sem adicionar coluna de versão ou migration no MVP. O lock permanece na transação curta do use case.

`existsByChassisNumberAndIdNot` evita considerar o próprio registro como duplicado. Para atualizações concorrentes de
Vehicles diferentes, a constraint única existente continua sendo a proteção final; `saveAndFlush` traduz a violação
conhecida para `VehicleChassisAlreadyExistsException`.

Uma substituição bem-sucedida deixa o chassis anterior de ser o valor atual daquele Vehicle e o libera para eventual
correção em outro cadastro. Se a atualização falhar, rollback preserva o chassis anterior e nenhuma reserva é liberada.
Arquivamento futuro continua reservando o chassis que estiver associado ao Vehicle no momento do arquivamento.

`VehicleJpaEntity`, `VehiclePersistenceMapper` e a tabela `vehicles` já possuem todas as colunas necessárias. Marca,
modelo, ano, cor e chassis são atualizados na mesma linha; os demais campos são mapeados com os valores preservados.

Classificação de dados: **nenhum seed requerido**. A story não cria nem altera schema, migration, backfill, dado de
referência ou seeder. Flyway permanece inalterado e Hibernate continua com `ddl-auto=validate`.

### Compatibilidade e isolamento de snapshots

- `POST /api/vehicles` e seu contrato permanecem compatíveis.
- `VehicleResponse` é reutilizado sem adicionar, remover ou renomear campos.
- Nenhuma tabela ou API de Customer é consultada ou alterada.
- Nenhum tipo interno de Service Lifecycle é importado pelo slice de Vehicle.
- `VehicleSnapshot` existente continua sendo valor congelado e não recebe update ou backfill.
- A integração que formará novos snapshots com os dados mestres atuais permanece no épico consumidor.
- A substituição do chassis não altera snapshots atuais, que nem sequer contêm esse campo.
- Não é criada consulta GET de Vehicle como efeito colateral desta story.

### Segurança e operação

- DTO fechado aceita somente os cinco campos aprovados e impede mass assignment de identidade, associação e lifecycle.
- Bean Validation e o domínio limitam valores antes do save; chassis sem conteúdo é tratado como ausência de comando.
- O ID vem do path e é usado por repository parametrizado; nenhum SQL é montado com entrada do usuário.
- Lock pessimista é limitado a uma linha e uma transação curta.
- Constraints conhecidas são traduzidas sem revelar nomes ou valores.
- Responses contêm dados operacionais de Vehicle, mas não contato ou documento do Customer.
- Erros não ecoam chassis, placa ou IDs e não revelam se outro Vehicle específico possui o chassis.
- Autenticação e autorização foram incorporadas posteriormente; `/api/vehicles/**` exige `MANAGER` ou `ADMIN`.
- Não há dependência, upload, chamada externa, segredo, credencial, log ou operação destrutiva nova.
- Rollout não requer etapa de banco; rollback da aplicação mantém os dados gravados no schema já existente.

### Estratégia de testes

#### Domínio

- sucesso atualizando marca, modelo, ano e cor de Vehicle ativo;
- normalização e limites das descrições;
- inclusão e substituição de chassis;
- chassis ausente no comando preserva valor presente ou ausente;
- idempotência com mesmas descrições e mesmo chassis;
- Vehicle arquivado rejeita a operação;
- qualquer descrição ou ano inválido preserva todo o estado anterior;
- `id`, `customerId`, placa e `active` nunca mudam.

#### Aplicação

- sucesso com chassis omitido, `null`, vazio, em branco, novo e substituído;
- `Clock.fixed` para ano nos limites e fora deles;
- Vehicle inexistente e arquivado;
- chassis do próprio Vehicle sem consulta de duplicidade;
- chassis de outro Vehicle como conflito sem save;
- ordem de validação, lookup, lifecycle, unicidade e save;
- ausência de acesso a Customer e Service Lifecycle;
- rollback lógico: nenhuma chamada de save após falha.

#### Persistência e concorrência

- lookup por ID com `PESSIMISTIC_WRITE` e round-trip completo;
- atualização preserva ID, Customer, placa e lifecycle;
- inclusão e substituição de chassis na mesma linha;
- consulta de chassis exclui o próprio ID;
- constraint traduz corrida de dois Vehicles para o mesmo chassis;
- falha de unicidade mantém o chassis anterior;
- Flyway/Hibernate iniciam sem migration nova.

#### HTTP e contratos

- `PATCH` válido retorna `200` e `VehicleResponse` completo;
- request com chassis omitido, `null`, vazio, em branco, novo e substituído;
- descrições obrigatórias, limites, ano dinâmico e chassis não vazio inválido;
- `VEHICLE_NOT_FOUND`, `VEHICLE_ARCHIVED` e `VEHICLE_CHASSIS_ALREADY_EXISTS`;
- campos proibidos não alteram identidade, associação, placa ou lifecycle;
- OpenAPI contém path, request, response, exemplos, nulabilidade e erros;
- collection Postman permanece JSON válido e usa `vehicleId` existente.

#### Fronteiras e gates

- `ModuleStructureTest` preserva três módulos e dependências acíclicas;
- teste arquitetural ou inspeção confirma ausência de dependência de Vehicle para Service Lifecycle;
- snapshots existentes não são modificados; teste end-to-end da integração permanece N/A até o épico consumidor;
- suíte focada durante desenvolvimento, seguida por `make test`, `make verify` e revisão JaCoCo;
- validação manual em MySQL cobre update, preservação, inclusão, substituição, idempotência e conflito de chassis;
- revisão de segurança registra cada item e impede conclusão com achado crítico ou alto.

### Decisões técnicas aprovadas

- [x] Expor `PATCH /api/vehicles/{id}` com os quatro campos descritivos obrigatórios e chassis opcional.
- [x] Interpretar chassis omitido, `null`, vazio ou em branco como preservação, nunca remoção.
- [x] Reutilizar `VehicleResponse`, `Clock`, value objects e a constraint de chassis existentes.
- [x] Implementar `Vehicle.updateDetails(...)` atômico, sem setters e com proteção de lifecycle.
- [x] Usar `findByIdForUpdate` com lock pessimista para serializar updates do mesmo Vehicle.
- [x] Excluir o próprio ID do precheck e manter a constraint única como proteção concorrente final.
- [x] Adotar `VEHICLE_NOT_FOUND`, `VEHICLE_ARCHIVED` e reutilizar `VEHICLE_CHASSIS_ALREADY_EXISTS`.
- [x] Não consultar Customer, não alterar snapshots e não criar integração com Service Lifecycle.
- [x] Não criar migration, schema, seed, backfill, dependência ou operação destrutiva.
- [x] Atualizar Springdoc, teste OpenAPI e Postman junto do contrato HTTP.

### Gate para a próxima etapa

- [x] Ivan revisou e aprovou explicitamente este desenho técnico da `SCRUM-36` em 2026-08-17.
- [x] Nenhuma decisão pendente exige mudança material na especificação funcional aprovada.
- [x] O gate técnico foi concluído e permite atualizar o `implementation-plan.md` da `SCRUM-36`.

### Evidência de implementação da SCRUM-36

- O aggregate, use case, porta e adapter implementam a atualização atômica e o lock pessimista aprovados.
- `PATCH /api/vehicles/{id}`, Springdoc, teste OpenAPI e Postman estão sincronizados.
- O schema existente foi suficiente: **nenhum seed requerido** e nenhuma migration, backfill ou tabela nova.
- `test` e `verify` passaram com 132 testes; `ModuleStructureTest` passou 2/2 e o slice Vehicle atingiu 98,69% de
  linhas.
- A validação local em MySQL confirmou preservação, inclusão, substituição, duplicidade, not found e archive.
- Nenhum achado de segurança crítico ou alto permanece. Ivan aceitou a implementação e autorizou o commit local
  `d5adcc9` em 2026-08-17.

## SCRUM-35 — quilometragem de Vehicle

### Estado do gate técnico

A especificação funcional da `SCRUM-35` foi aprovada por Ivan Pimentel em 2026-08-22. Ela autoriza `mileage` opcional
no cadastro e um comando específico para primeiro registro ou atualização posterior, sempre em quilômetros inteiros e
sem regressão.

Esta seção técnica foi aprovada por Ivan Pimentel em 2026-08-22. A aprovação libera a criação do plano de implementação,
mas não autoriza antecipadamente código ou mudanças nos contratos e na persistência. O desenho parte da branch
`feat/registration-vehicle-management2` em `608dd29`; `verify` passou nessa baseline com 308 testes, sem falhas, erros
ou skips.

### Impacto arquitetural

A mudança permanece integralmente em `registration.vehicle`, dentro do módulo Spring Modulith `registration`. Nenhum
novo módulo, aggregate, port entre bounded contexts ou dependência de Service Lifecycle será criado. O slice atual será
estendido com:

- value object `Mileage` e comando monotônico no aggregate `Vehicle`;
- `mileage` opcional em `CreateVehicleRequest` e `VehicleResponse`;
- request fechado `UpdateVehicleMileageRequest`;
- `UpdateVehicleMileageUseCase` transacional;
- `VehicleMileageCannotDecreaseException` no domínio;
- endpoint específico no `VehicleController` e tradução no `VehicleExceptionHandler`;
- coluna nullable na tabela `vehicles`, introduzida por nova migration Flyway.

O update reutiliza `VehicleRepository.findByIdForUpdate(UUID)` e `save(Vehicle)`. Nenhum método novo de repository é
necessário. Cadastro continua consultando Customer ativo; atualização de quilometragem não consulta Customer, Stock &
Procurement ou Service Lifecycle.

### Modelo de domínio

`Mileage` será um value object framework-free com representação `long` e accessor `value()`. Sua criação:

- aceita valores entre zero e `Long.MAX_VALUE`, inclusive;
- rejeita valor negativo com mensagem em português;
- representa quilômetros inteiros, sem unidade configurável ou casas decimais;
- oferece comparação por valor para aplicar a monotonicidade.

O aggregate `Vehicle` passa a manter `Mileage mileage`, nullable internamente enquanto não houver leitura, e expõe
`Optional<Mileage> mileage()`. As factories `create(...)` e `reconstitute(...)` recebem o novo estado explicitamente.
`null` significa “não informado” e nunca é convertido em zero.

O comando de domínio terá comportamento equivalente a:

```java
boolean recordMileage(Mileage newMileage)
```

O método:

1. exige `newMileage` não nulo;
2. rejeita `active=false` com `VehicleArchivedException`;
3. aceita qualquer `Mileage` quando o estado atual não foi informado;
4. rejeita valor menor com `VehicleMileageCannotDecreaseException`;
5. retorna `false` e não atribui estado quando o valor é igual;
6. atribui o primeiro valor ou o valor maior e retorna `true`.

O retorno booleano informa à aplicação se houve mudança real e evita `saveAndFlush` no caso idempotente. Não haverá
setter público, incremento relativo, correção para baixo, histórico ou mutação de qualquer outro campo.

### Validação estrita do contrato numérico

Os dois requests usarão `Long mileage`, preservando o schema OpenAPI `integer/int64`. Para impedir coerções silenciosas
do Jackson, o campo usará um deserializer local `StrictLongDeserializer`, compartilhado apenas pelos DTOs de Vehicle.
Ele aceitará somente token JSON inteiro ou `null`; string, decimal, boolean, array, objeto e overflow produzirão
contrato inválido antes do use case.

No cadastro, `mileage` não recebe `@NotNull`; omissão e `null` são válidos. Quando presente, recebe
`@PositiveOrZero`. No request de atualização, o mesmo campo recebe `@NotNull` e `@PositiveOrZero`. O domínio repete a
invariante não negativa por meio de `Mileage`, protegendo chamadas que não passam pelo HTTP.

Mensagens de Bean Validation e domínio permanecem em português. O deserializer não inclui o valor recebido na mensagem
ou em logs.

### Fluxo de cadastro

`CreateVehicleUseCase.execute(CreateVehicleRequest)` continua `@Transactional` e passa a executar:

1. construir placa, chassis, ano e `Mileage` opcional;
2. construir `Vehicle` com `mileage` informada ou ausente;
3. bloquear e validar o Customer ativo;
4. verificar unicidade de placa e eventual chassis;
5. salvar com flush e retornar `VehicleResponse` incluindo `mileage`.

Valor de quilometragem inválido falha na borda ou no value object antes da consulta de Customer. A inclusão do campo não
muda a precedência aprovada para Customer, placa e chassis depois que todos os dados do Vehicle são válidos.

### Fluxo de atualização

`UpdateVehicleMileageUseCase.execute(UUID id, UpdateVehicleMileageRequest request)` usa `@Transactional` e executa:

1. converte o `Long` obrigatório em `Mileage`;
2. localiza e bloqueia o Vehicle com `findByIdForUpdate(id)`;
3. produz `VEHICLE_NOT_FOUND` quando a linha não existe;
4. executa `vehicle.recordMileage(mileage)`;
5. salva com flush somente quando o comando retorna `true`;
6. retorna `VehicleResponse` com o estado novo ou idempotente.

Como `recordMileage` verifica lifecycle antes da comparação, Vehicle arquivado produz `VEHICLE_ARCHIVED` mesmo quando
o valor seria igual ou menor. Entrada inválida continua falhando antes do lookup. Nenhum caminho consulta Customer ou
outro bounded context.

### Concorrência

O lookup existente usa `PESSIMISTIC_WRITE` sobre a linha de Vehicle. Duas atualizações do mesmo Vehicle são
serializadas dentro de transações curtas e cada comando compara contra o último estado confirmado:

- se o menor valor confirmar primeiro, o maior ainda será aceito;
- se o maior confirmar primeiro, o menor será rejeitado;
- dois valores iguais produzem no máximo uma mudança real e respostas com o mesmo estado;
- updates de Vehicles diferentes bloqueiam somente suas próprias linhas.

Não será adicionada coluna de versão. O lock pessimista já aprovado para a atualização descritiva é suficiente para o
MVP e impede lost update que faria a quilometragem retroceder.

### Contratos HTTP

#### Alteração aditiva de `POST /api/vehicles`

`CreateVehicleRequest` recebe `mileage` opcional:

```json
{
  "customerId": "ca0416e2-86da-4eaa-b27e-d4a9262f51e6",
  "licensePlate": "ABC-1234",
  "chassis": "9BWZZZ377VT004251",
  "brand": "Volkswagen",
  "model": "Gol",
  "year": 2026,
  "color": "Prata",
  "mileage": 42500
}
```

Omissão ou `null` cria o Vehicle sem leitura. Inteiro entre zero e `Long.MAX_VALUE` registra a primeira leitura.
Requests existentes permanecem válidos. Valor negativo, decimal, string, boolean ou fora do intervalo retorna `400` e
não cria Vehicle.

`VehicleResponse` recebe o campo nullable `mileage`, representado como `integer/int64`:

```json
{
  "id": "8aedf48c-96ed-4dad-b860-9ed8a527cfb9",
  "customerId": "ca0416e2-86da-4eaa-b27e-d4a9262f51e6",
  "licensePlate": "ABC1234",
  "chassis": "9BWZZZ377VT004251",
  "brand": "Volkswagen",
  "model": "Gol",
  "year": 2026,
  "color": "Prata",
  "mileage": 42500,
  "active": true
}
```

Responses de Vehicles sem leitura usam `"mileage": null`. A adição é compatível e será refletida também na resposta do
endpoint descritivo existente, sem permitir que seu request altere quilometragem.

#### `PATCH /api/vehicles/{id}/mileage`

Request `UpdateVehicleMileageRequest`:

```json
{
  "mileage": 43120
}
```

O campo é obrigatório, não nullable e aceita somente inteiro não negativo. Sucesso, inclusive idempotente, retorna
`200 OK` com o `VehicleResponse` completo. O endpoint usa `id` no path; a collection Postman usa a variável
`{{vehicleId}}` já existente.

Springdoc documentará schema, unidade em quilômetros, nulabilidade no cadastro/response, obrigatoriedade no update,
limite `int64`, monotonicidade, idempotência e respostas. `OpenApiContractTest` verificará os dois contratos. A pasta
`Registrations > Vehicle` do Postman receberá exemplos de cadastro com/sem quilometragem, aumento, repetição e redução
rejeitada.

### Falhas e códigos estáveis

| Condição | HTTP | Código estável | Origem |
|---|---:|---|---|
| JSON, tipo, overflow ou Bean Validation inválido | 400 | `VALIDATION_ERROR` | Boundary |
| Invariante negativa fora do HTTP | 400 | `INVALID_VEHICLE` | `Mileage` |
| Vehicle inexistente | 404 | `VEHICLE_NOT_FOUND` | Exceção existente |
| Vehicle arquivado | 409 | `VEHICLE_ARCHIVED` | Exceção existente |
| Valor menor que o atual | 409 | `VEHICLE_MILEAGE_CANNOT_DECREASE` | Nova exceção de domínio |

`VehicleMileageCannotDecreaseException` terá mensagem genérica em português e não revelará valor atual, valor recebido,
ID ou histórico. `VehicleExceptionHandler` fará o mapeamento específico antes do handler de
`IllegalArgumentException`. Falhas técnicas inesperadas de banco ou lock continuarão propagando sem serem mascaradas.

Precedência observável:

1. contrato ou valor inválido retorna `400` antes do lookup;
2. com entrada válida, Vehicle inexistente retorna `404`;
3. Vehicle arquivado retorna `409 VEHICLE_ARCHIVED`;
4. Vehicle ativo com regressão retorna `409 VEHICLE_MILEAGE_CANNOT_DECREASE`;
5. valor igual ou maior retorna `200`.

### Persistência e classificação de dados

Uma migration Flyway nova, aditiva e imutável será nomeada no momento da implementação como
`V<timestamp_utc>__add_vehicle_mileage.sql` e conterá o equivalente a:

```sql
ALTER TABLE vehicles
    ADD COLUMN mileage BIGINT NULL,
    ADD CONSTRAINT ck_vehicles_mileage_non_negative CHECK (mileage IS NULL OR mileage >= 0);
```

Linhas existentes permanecem `NULL`; não haverá default zero nem backfill. `VehicleJpaEntity` usa `Long mileage` com
`@Column(nullable = true)`. `VehiclePersistenceMapper` converte `null` para ausência e valores presentes para
`Mileage`, preservando o round-trip.

O tipo `BIGINT` assinado é compatível com `Long` e suporta o intervalo técnico aprovado. A constraint protege escrita
fora do domínio, enquanto a regra de não redução permanece no aggregate e na transação porque depende do estado atual.
Hibernate continua com `ddl-auto=validate`.

Classificação: **nenhum seed requerido**. Quilometragem é estado operacional por Vehicle, não dado de referência ou
demonstração. Testes usarão fixtures próprias e não dependerão dos seeders de desenvolvimento.

### Compatibilidade e isolamento

- A migration é nullable e não inventa quilometragem para linhas existentes.
- `POST /api/vehicles` permanece compatível porque o novo request field é opcional.
- `VehicleResponse` muda apenas de forma aditiva com `mileage` nullable.
- `PATCH /api/vehicles/{id}` não recebe `mileage` e continua preservando o estado.
- Nenhum endpoint, DTO, tabela ou dado de Customer é alterado.
- Nenhum tipo interno de Service Lifecycle é importado.
- `VehicleSnapshot` atual não recebe campo, FK, update ou backfill nesta story.
- Nenhuma consulta GET, lista, archive, histórico de leitura ou integração é criada.

### Segurança e operação

- DTOs fechados evitam mass assignment de identidade, associação, lifecycle e campos descritivos no update de mileage.
- Parser estrito, `@PositiveOrZero` e `Mileage` aplicam validação em camadas sem coerção ou truncamento.
- O limite `Long.MAX_VALUE` impede overflow entre JSON, domínio, JPA e MySQL.
- Repository e JPA usam parâmetros; nenhum SQL é montado com entrada do usuário.
- Lock pessimista restringe-se a uma linha e à transação curta do comando.
- Mensagens e logs não expõem ID, quilometragem atual/recebida, SQL, constraint ou stack trace.
- A resposta expõe somente estado operacional já aprovado do Vehicle, sem documento ou contato do Customer.
- Autenticação e autorização foram incorporadas posteriormente; `/api/vehicles/**` exige `MANAGER` ou `ADMIN`.
- Não há dependência externa, upload, chamada remota, segredo, credencial, log ou operação destrutiva nova.
- Rollout aplica a migration antes da aplicação; rollback da aplicação mantém a coluna nullable sem perda de dados.
- Nenhum achado crítico ou alto é conhecido no desenho; a revisão será repetida e registrada no plano.

### Estratégia de testes

#### Domínio

- `Mileage` aceita zero, positivos e `Long.MAX_VALUE`, e rejeita negativos;
- criação de Vehicle com leitura presente e ausente;
- primeiro registro com zero ou positivo;
- valor maior altera somente `mileage`;
- valor igual é idempotente e informa ausência de mudança;
- valor menor lança a exceção específica e preserva todo o estado;
- Vehicle arquivado rejeita antes da comparação;
- identidade, associação, descrições, chassis e lifecycle permanecem inalterados.

#### Aplicação

- cadastro com `mileage` omitida, `null`, zero e positiva;
- cadastro inválido não consulta Customer nem salva;
- primeiro registro e aumento executam um save;
- valor igual retorna sucesso sem save;
- valor menor, Vehicle ausente e arquivado não salvam;
- update não acessa Customer ou outro módulo;
- ordem de conversão, lookup, lifecycle, monotonicidade e persistência.

#### Persistência e concorrência

- migration parte do schema anterior, cria coluna nullable e preserva linhas existentes como `NULL`;
- Hibernate valida `Long mileage` contra `BIGINT`;
- round-trip de ausência, zero, positivo e `Long.MAX_VALUE`;
- constraint rejeita valor negativo escrito fora do domínio;
- update preserva todas as colunas não relacionadas;
- duas transações no mesmo Vehicle comprovam serialização e ausência de regressão;
- updates simultâneos de Vehicles diferentes permanecem independentes.

#### HTTP e contratos

- cadastro sem campo, com `null`, zero e positivo retorna `201` e `mileage` esperada;
- cadastro com negativo, decimal, string, boolean ou overflow retorna `400` sem persistência;
- `PATCH /{id}/mileage` maior e primeiro registro retornam `200` com estado atualizado;
- update igual retorna `200` idempotente;
- update menor retorna `409 VEHICLE_MILEAGE_CANNOT_DECREASE`;
- update nulo, ausente, negativo, decimal, string, boolean e overflow retorna `400 VALIDATION_ERROR`;
- not found e archived preservam os códigos existentes;
- endpoint descritivo preserva mileage e a inclui na resposta;
- OpenAPI expõe `integer/int64`, required/nullable corretos, exemplos e respostas;
- collection Postman permanece JSON válida e cobre os fluxos aprovados.

#### Gates

- suíte focada de domínio, aplicação, persistência, concorrência e MockMvc;
- startup com todas as migrations e `ddl-auto=validate`;
- `OpenApiContractTest` e validação da collection Postman;
- `ModuleStructureTest` com três módulos e dependências acíclicas;
- `make test` durante o desenvolvimento e `make verify` antes da conclusão;
- `make coverage` e revisão para não reduzir a cobertura do slice alterado;
- validação manual no MySQL de cadastro, primeiro registro, aumento, idempotência, regressão e constraint;
- revisão de segurança completa sem achado crítico ou alto pendente.

### Decisões técnicas aprovadas

- [x] Criar `Mileage` sobre `long`, nullable somente dentro do aggregate enquanto não houver leitura.
- [x] Adicionar `mileage` opcional ao cadastro e nullable ao `VehicleResponse`.
- [x] Usar parser JSON local estrito para rejeitar coerção, fração e overflow.
- [x] Expor `PATCH /api/vehicles/{id}/mileage` com request próprio e `200` no sucesso idempotente.
- [x] Implementar `Vehicle.recordMileage(...)` monotônico e salvar somente quando houver mudança real.
- [x] Reutilizar `findByIdForUpdate` para serializar updates concorrentes do mesmo Vehicle.
- [x] Mapear regressão para `409 VEHICLE_MILEAGE_CANNOT_DECREASE` sem revelar valores.
- [x] Criar migration nullable `BIGINT` com check não negativo, sem default ou backfill.
- [x] Classificar dados como **nenhum seed requerido**.
- [x] Preservar atualização descritiva, Customer, snapshots e demais bounded contexts.
- [x] Atualizar Springdoc, OpenAPI contract e Postman junto dos contratos HTTP.
- [x] Executar todos os gates e a revisão de segurança definidos nesta seção.

### Gate para a próxima etapa

- [x] Ivan revisou e aprovou explicitamente o desenho técnico da `SCRUM-35` em 2026-08-22.
- [x] Nenhuma decisão pendente exige mudança material na especificação funcional aprovada.
- [x] O gate técnico foi concluído e permite criar o plano de implementação da `SCRUM-35`.

## SCRUM-37 — arquivamento, consultas e elegibilidade para novo trabalho

### Estado do gate técnico

A especificação funcional da `SCRUM-37` foi aprovada por Ivan Pimentel em 2026-08-23. Ela autoriza archive lógico,
GET histórico por ID, listagem GET de ativos, bloqueio de nova Service Order para Vehicle ausente ou arquivado e o
add-on de organização da collection Postman.

Esta seção técnica foi aprovada por Ivan Pimentel em 2026-08-23. O desenho partiu da branch
`feat/registration-vehicle-management2` em `44ce9b0`, após uma baseline de 333 testes. Ivan aprovou o plano e autorizou
o código na mesma data. A implementação foi verificada inicialmente com 370 testes, aceita e registrada em `d73ff8d`;
a reconciliação posterior com `dev` foi verificada com 387 testes e registrada em `5c96181`.

### Impacto arquitetural

A mudança estende `registration.vehicle` dentro do módulo Spring Modulith `registration` e altera somente o fluxo de
criação em `servicelifecycle.serviceorder`. Nenhum novo módulo, bounded context, aggregate ou dependência externa será
criado.

O slice de Vehicle recebe:

- comando idempotente de archive no aggregate;
- use cases de archive, consulta histórica por ID e listagem de ativos;
- queries históricas, operacionais e bloqueantes na porta de repository;
- operações GET e DELETE no controller e documentação Springdoc;
- API pública mínima de disponibilidade para consumo entre módulos.

Service Lifecycle recebe:

- port consumidor para verificar a elegibilidade de um `vehicleId`;
- adapter Java em processo para a API pública de Registrations;
- falhas próprias do consumidor para Vehicle ausente e arquivado;
- validação no início da criação de Service Order, antes de save e notificação.

A direção permanece `servicelifecycle -> registration`, já existente no sistema, sem dependência reversa. O código de
Service Lifecycle não importa aggregate, repository, entidade, exception ou package interno de Vehicle.

### Modelo de domínio e lifecycle

`Vehicle` recebe um comando equivalente a:

```java
boolean archive()
```

O método altera somente `active` de `true` para `false`. Na primeira chamada retorna `true`; para Vehicle já arquivado,
retorna `false` e não altera o estado. `id`, `customerId`, placa, chassis, descrições, ano, cor e mileage permanecem
inalterados.

O lifecycle continua irreversível no MVP. Não haverá `reactivate`, setter público de `active` nem hard delete. Os
comandos existentes `updateDetails(...)` e `recordMileage(...)` já verificam `active` e continuam lançando
`VehicleArchivedException` antes de qualquer mutação.

As constraints únicas existentes continuam reservando placa e chassis em ambos os estados. Archive não consulta nem
altera Customer e não percorre Service Orders históricas.

### Repository e consultas

A porta `VehicleRepository` será estendida com:

```java
Optional<Vehicle> findById(UUID id);
List<Vehicle> findAllActive();
```

`findById(UUID)` é histórico, não bloqueante e retorna ativos ou arquivados. `findAllActive()` é operacional, filtra
`active=true` no banco e não define ordenação. O método existente `findByIdForUpdate(UUID)` continua sendo a única
consulta bloqueante e será reutilizado por archive, updates e verificação de novo trabalho.

`VehicleJpaRepository` adiciona `findAllByActiveTrue()`. O `findById` herdado de `JpaRepository` atende à consulta
histórica. O adapter converte os resultados para aggregates antes de devolvê-los à aplicação; DTO e JPA nunca se tornam
contratos da porta.

### Use cases de Vehicle

`ArchiveVehicleUseCase.execute(UUID id)` usa `@Transactional` e executa:

1. localiza e bloqueia a linha com `findByIdForUpdate(id)`;
2. lança `VehicleNotFoundException` quando o ID não existe;
3. executa `vehicle.archive()`;
4. salva com flush somente quando o estado mudou;
5. retorna sem body tanto na primeira chamada quanto na repetição idempotente.

`GetVehicleUseCase.execute(UUID id)` usa `@Transactional(readOnly = true)`, chama `findById`, traduz ausência para
`VehicleNotFoundException` e retorna `VehicleResponse` completo em qualquer lifecycle.

`ListVehiclesUseCase.execute()` usa `@Transactional(readOnly = true)`, chama `findAllActive` e mapeia a lista para
`List<VehicleResponse>`. Zero resultados produz lista vazia; não haverá paginação, filtro ou ordenação adicional.

Um helper interno semelhante a `VehicleFinder` pode centralizar somente a tradução de ausência. Ele não recebe Spring
nem expõe o aggregate fora de `registration.vehicle`.

### Integração com Service Lifecycle

#### API pública de Registrations

O package `registration.vehicle.application.api` será exposto por
`@NamedInterface("vehicle-availability-api")` e conterá apenas:

```java
public interface VehicleAvailabilityApi {
    VehicleAvailability checkForNewWork(UUID vehicleId);
}

public enum VehicleAvailability {
    ACTIVE,
    ARCHIVED,
    NOT_FOUND
}
```

A implementação interna de Registrations usa `VehicleRepository.findByIdForUpdate(vehicleId)` e mapeia o resultado
para o enum público. Ela não retorna `Vehicle`, `VehicleResponse`, JPA ou exception interna.

O método participa obrigatoriamente da transação do consumidor por
`@Transactional(propagation = Propagation.MANDATORY)`. Isso impede que o lock seja liberado antes da confirmação da
Service Order. A API aceita apenas UUID e não altera o aggregate.

#### Port e adapter consumidores

`servicelifecycle.serviceorder.application.port.VehicleEligibilityPort` pertence ao consumidor e usa um enum próprio
com os mesmos três resultados. `CreateServiceOrderUseCase` depende somente desse port.

Um adapter em `servicelifecycle.serviceorder.infrastructure.registration` implementa o port, chama
`VehicleAvailabilityApi` no mesmo processo e converte o enum público para o enum do consumidor. Essa camada evita que a
aplicação de Service Lifecycle dependa diretamente do contrato de Registrations e mantém a substituição por outro
adapter possível sem antecipar HTTP interno.

O adapter não compara `customerId`, placa, descrições ou snapshot. Sua única responsabilidade é responder se o
`vehicleId` existe e está ativo para novo trabalho.

### Fluxo transacional da nova Service Order

`CreateServiceOrderUseCase.execute(CreateServiceOrderRequest)` permanece `@Transactional` e passa a executar:

1. validar e mapear `VehicleSnapshotRequest` e prioridade sem produzir efeito externo;
2. consultar `VehicleEligibilityPort` para o `vehicleId` recebido;
3. lançar falha do consumidor para `NOT_FOUND` ou `ARCHIVED`;
4. criar o aggregate `ServiceOrder` quando o resultado for `ACTIVE`;
5. salvar a Service Order;
6. notificar técnicos ativos pelo comportamento já existente;
7. manter o lock de Vehicle até o commit da transação.

O request e o snapshot continuam sendo fornecidos pelo cliente. A verificação não compara o `customerId` da ordem com
o proprietário do Vehicle e não recalcula o `VehicleSnapshot` a partir do cadastro vivo.

Falha de elegibilidade ocorre antes de `ServiceOrderRepository.save`, consulta de técnicos ou notificação. Nenhum ID de
Service Order é persistido e nenhum efeito parcial é produzido nesse caminho.

### Concorrência

Archive e criação de Service Order usam `PESSIMISTIC_WRITE` sobre a mesma linha de Vehicle e compartilham a transação
JPA do monólito:

- se a criação obtiver o lock primeiro, confirma a Service Order antes de o archive prosseguir;
- se o archive confirmar primeiro, a criação bloqueada relê `active=false` e é rejeitada;
- depois do `204` do archive, uma transação posterior nunca confirma nova ordem para o mesmo Vehicle;
- updates de detalhes e mileage são serializados pelo mesmo lock e observam o lifecycle confirmado mais recente;
- operações em Vehicles diferentes não bloqueiam umas às outras por desenho.

Nenhuma coluna de versão será adicionada. O fluxo adquire somente um lock de Vehicle antes de salvar a nova Service
Order e não existe caminho inverso que bloqueie Service Order antes do mesmo Vehicle; o risco de deadlock não é ampliado
por uma segunda ordem de locks.

### Contratos HTTP de Vehicle

#### `GET /api/vehicles/{id}`

Retorna `200 OK` com `VehicleResponse` completo para Vehicle ativo ou arquivado:

```json
{
  "id": "8aedf48c-96ed-4dad-b860-9ed8a527cfb9",
  "customerId": "ca0416e2-86da-4eaa-b27e-d4a9262f51e6",
  "licensePlate": "ABC1234",
  "chassis": "9BWZZZ377VT004251",
  "brand": "Volkswagen",
  "model": "Gol",
  "year": 2026,
  "color": "Prata",
  "mileage": 42500,
  "active": false
}
```

ID inexistente retorna `404 VEHICLE_NOT_FOUND`; UUID inválido retorna `400 VALIDATION_ERROR`.

#### `GET /api/vehicles`

Retorna `200 OK` com array de `VehicleResponse`, contendo somente `active=true`. Não recebe query parameters. Uma base
sem Vehicles ativos retorna `[]`. A ordem dos itens não integra o contrato.

#### `DELETE /api/vehicles/{id}`

Retorna `204 No Content` para o primeiro archive e para repetição sobre Vehicle já arquivado. ID inexistente retorna
`404 VEHICLE_NOT_FOUND`; UUID inválido retorna `400 VALIDATION_ERROR`. A operação não recebe request body.

`VehicleController` passa a receber os três novos use cases por constructor injection. Springdoc documenta semântica
histórica, filtro de ativos, idempotência, schemas e respostas sem duplicar um YAML manual.

### Alteração de `POST /api/service-orders`

O request, o `201 Created` e o `ServiceOrderResponse` permanecem inalterados. A operação passa a documentar e produzir:

| Condição do `vehicleId` | HTTP | Código estável |
|---|---:|---|
| Vehicle inexistente | 404 | `VEHICLE_NOT_FOUND` |
| Vehicle arquivado | 409 | `VEHICLE_ARCHIVED` |

Service Lifecycle define exceptions próprias para essas duas condições e as traduz em
`ServiceLifecycleExceptionHandler`. Exceptions internas de Registrations não atravessam o port. As mensagens são
genéricas e não incluem `vehicleId`, placa, chassis, mileage, SQL ou estado interno.

Precedência observável:

1. JSON, Bean Validation ou snapshot inválido retorna `400` antes da consulta de elegibilidade;
2. Vehicle inexistente retorna `404 VEHICLE_NOT_FOUND`;
3. Vehicle arquivado retorna `409 VEHICLE_ARCHIVED`;
4. Vehicle ativo segue o fluxo existente e pode retornar suas falhas já documentadas.

### Persistência e classificação de dados

Não haverá migration. A coluna `vehicles.active BOOLEAN NOT NULL`, as constraints únicas de placa/chassis e o campo
`service_orders.vehicle_id` já existem. Archive atualiza somente a linha de Vehicle; consultas não alteram dados.

Não será criada FK de `service_orders.vehicle_id` para `vehicles`. Service Order preserva a referência por UUID e seu
snapshot próprio, e a regra de elegibilidade é aplicada na aplicação sob lock. Essa decisão evita acoplamento de schema
entre os módulos e não muda registros históricos.

Classificação: **nenhum seed requerido**. A feature não cria dado de referência ou demonstração. Testes usam fixtures
próprias; o banco local será validado sem reset, hard delete ou reescrita de snapshots.

### Add-on da collection Postman

A consolidação solicitada por Ivan está registrada no commit local `44ce9b0` e será preservada na SCRUM-37:

- Customer mantém `Create customer` com contato/endereço completos e uma única
  `Update customer contact information`;
- Vehicle mantém `Create vehicle`, `Update vehicle details` e `Update vehicle mileage`, cada um uma única vez;
- exemplos de criação e atualização apresentam todos os campos aceitos;
- descrições em inglês informam campos opcionais e semântica de preservação;
- variações de chassis ou contato não duplicam método e URL.

Durante a implementação dos novos contratos serão adicionadas exatamente as entradas `Get vehicle`, `List vehicles` e
`Archive vehicle`. A collection será validada por parser JSON e comparada à OpenAPI gerada.

### Compatibilidade e documentação

- os três endpoints de Vehicle são aditivos;
- `VehicleResponse` não muda nomes, tipos ou nulabilidade;
- cadastro, update descritivo e mileage mantêm seus requests e respostas;
- `POST /api/service-orders` preserva request e sucesso, mas passa a rejeitar IDs antes aceitos sem verificação;
- Service Orders existentes e seus snapshots não recebem update, migration, backfill ou invalidação;
- OpenAPI, Postman e `docs/PROJECT-STRUCTURE.md` serão atualizados no mesmo checkpoint de contratos;
- a documentação de Service Order registrará a nova elegibilidade como evolução aprovada pela SCRUM-37;
- `ModuleStructureTest` deve continuar encontrando três módulos acíclicos e somente a named interface autorizada.

### Segurança e operação

- GET e DELETE aceitam somente UUID no path; nenhum body permite mass assignment de `active` ou outro campo.
- Lista e consulta expõem dados operacionais do Vehicle, mas não documento, email, telefone ou endereço do Customer.
- O risco original de enumeração anônima foi mitigado pela iniciativa JWT: listagem e consulta exigem `MANAGER` ou
  `ADMIN`. Logs e erros continuam sem repetir placa, chassis ou `customerId`.
- Risco baixo aceito no MVP: a lista não paginada pode crescer. O contrato segue Customer e poderá receber paginação em
  story posterior sem introduzir filtros ocultos agora.
- O filtro `active=true` é aplicado no repository, evitando carregar arquivados para removê-los em memória.
- O lock pessimista impede TOCTOU entre elegibilidade e archive e permanece restrito a uma linha e transação curta.
- Falhas de elegibilidade ocorrem antes de save, consulta de técnicos e notificação.
- A API pública entre módulos usa UUID e enum, sem domínio, JPA, PII, SQL ou exception interna.
- Não há dependência nova, chamada de rede, segredo, credencial, upload, hard delete ou migration.
- Nenhum achado crítico ou alto é conhecido no desenho; riscos médio/baixo serão revisados no plano.

### Estratégia de testes

#### Domínio

- archive ativo muda somente `active` e informa mudança;
- archive repetido é idempotente e informa ausência de mudança;
- todos os demais campos, inclusive mileage ausente ou presente, permanecem idênticos;
- updates descritivo e de mileage continuam rejeitando Vehicle arquivado.

#### Aplicação de Vehicle

- archive ativo salva uma vez; repetição não salva; ID inexistente lança not found;
- GET por ID retorna ativo ou arquivado e falha para ausente;
- listagem retorna somente ativos, resposta completa e coleção vazia;
- use cases de consulta são read-only e não acessam Customer ou Service Lifecycle;
- API pública retorna `ACTIVE`, `ARCHIVED` e `NOT_FOUND` sob transação obrigatória.

#### Service Lifecycle e módulos

- port ativo permite criação, save e notificações existentes;
- port not found produz `VEHICLE_NOT_FOUND` sem save, consulta de técnicos ou notificação;
- port archived produz `VEHICLE_ARCHIVED` sem efeito parcial;
- request ou snapshot inválido falha antes do port;
- adapter mapeia todos os resultados da API pública sem expor tipos internos;
- `@ApplicationModuleTest` resolve a named interface e o adapter entre os módulos;
- `ModuleStructureTest` confirma dependência acíclica e ausência de imports internos.

#### Persistência e concorrência

- round-trip do archive preserva a mesma linha, ID, identidades e mileage;
- `findById` inclui arquivados e `findAllActive` os exclui;
- constraints de placa e chassis continuam reservando identidades arquivadas;
- archive concorrente com update observa o lifecycle confirmado mais recente;
- archive concorrente com criação de Service Order comprova as duas ordens de confirmação;
- depois do commit do archive, nenhuma criação posterior é persistida.

#### HTTP, OpenAPI e Postman

- GET por ID cobre ativo, arquivado, ausente e UUID inválido;
- GET da coleção cobre múltiplos ativos, exclusão de arquivados e array vazio;
- DELETE cobre primeiro archive, repetição, ausente e UUID inválido;
- respostas GET contêm todos os dez campos públicos e não expõem domínio/JPA;
- criação de Service Order cobre Vehicle ativo, ausente e arquivado com códigos estáveis;
- OpenAPI contém paths, schemas, respostas e semântica histórica/operacional;
- Postman contém uma entrada em inglês por operação, exemplos completos e JSON válido.

#### Gates e validação local

- testes focados de domínio, aplicação, adapters, persistência, concorrência e MockMvc;
- `OpenApiContractTest`, `ModuleStructureTest` e testes de módulo da integração;
- `make test` durante o desenvolvimento e `make verify` antes da conclusão;
- `make coverage`, sem reduzir cobertura do código alterado e com alvo mínimo de 80%;
- MySQL local: archive idempotente, GET histórico, lista ativa, identidades reservadas e bloqueio de nova ordem;
- revisão de segurança sem achado crítico ou alto pendente;
- atualização das documentações de API, estrutura e integração afetadas.

### Decisões técnicas aprovadas

- [x] Implementar `Vehicle.archive()` idempotente e salvar somente quando `active` mudar.
- [x] Usar `findByIdForUpdate` no archive e manter GET/list como queries não bloqueantes e read-only.
- [x] Expor `GET /api/vehicles/{id}`, `GET /api/vehicles` e `DELETE /api/vehicles/{id}`.
- [x] Retornar todos os ativos na lista não paginada e nenhum arquivado, sem ordem contratual.
- [x] Reutilizar `VehicleResponse` completo nos dois GETs sem alterar seu schema.
- [x] Criar named interface mínima de disponibilidade em Registrations com UUID e enum.
- [x] Criar port consumidor e adapter Java em Service Lifecycle, sem HTTP interno ou import de domínio Vehicle.
- [x] Manter o lock de elegibilidade até o commit da Service Order por transação obrigatória compartilhada.
- [x] Mapear Vehicle ausente/arquivado no POST de Service Order para `404 VEHICLE_NOT_FOUND` e
  `409 VEHICLE_ARCHIVED`.
- [x] Não validar ownership Customer/Vehicle nem reconciliar o snapshot enviado.
- [x] Não criar migration, FK, seed, reativação, hard delete, paginação ou filtros.
- [x] Preservar e concluir o add-on Postman com uma entrada em inglês por operação e exemplos completos.
- [x] Executar a estratégia de testes, documentação e segurança descrita nesta seção.

### Gate para a próxima etapa

- [x] Ivan revisou e aprovou explicitamente o desenho técnico da `SCRUM-37` em 2026-08-23.
- [x] Nenhuma decisão pendente exige retorno da especificação funcional para `Draft`.
- [x] O gate técnico foi concluído e permite criar o plano de implementação da `SCRUM-37`.

### Evidência de implementação da SCRUM-37

- O archive lógico e idempotente, os dois GETs e a elegibilidade de nova Service Order foram implementados conforme o
  desenho aprovado, sem migration, FK, seed, hard delete, ownership ou reconciliação de snapshot.
- A named interface pública usa somente UUID e enum; Service Lifecycle consome seu próprio port e mantém o lock de
  Vehicle até o commit da transação da ordem.
- Antes da reconciliação, as suítes `test` e `verify` passaram com 370 testes. Depois da integração com `dev`, o
  `verify` final passou com 387 testes, zero falhas, erros ou skips. `ModuleStructureTest` passou 2/2 e
  `OpenApiContractTest` passou 10/10.
- Após a reconciliação, JaCoCo registrou 93,09% de instruções e 93,66% de linhas no projeto. O slice original da
  SCRUM-37 havia registrado 97,78% e 99,18%, respectivamente.
- A validação MySQL confirmou archive, GET histórico, lista ativa, repetição idempotente e rejeição da nova ordem com
  `409 VEHICLE_ARCHIVED`, sem persistência parcial.
- A revisão de segurança não encontrou achado crítico ou alto. JWT resolveu a enumeração anônima; a lista não paginada
  permanece risco baixo aceito no MVP.
- OpenAPI, Postman e `docs/PROJECT-STRUCTURE.md` foram sincronizados. Ivan aceitou a implementação e autorizou o commit
  `d73ff8d` e a PR Draft #26 em 2026-08-23. Depois, autorizou a correção dos conflitos; `origin/dev` em `ffc4eef` foi
  integrado pelo merge não destrutivo `5c96181`, publicado sem force-push. A PR #26 foi posteriormente mesclada em
  `dev` pelo commit `6b9f223`.

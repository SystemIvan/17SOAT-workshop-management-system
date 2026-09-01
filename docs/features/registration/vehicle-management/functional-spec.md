# Especificação Funcional: Gestão de Vehicles

| Campo | Valor |
|---|---|
| Feature | `vehicle-management` |
| Status | `SCRUM-7`, `SCRUM-36`, `SCRUM-35` e `SCRUM-37` implementadas e aceitas |
| Responsável | Ivan Pimentel |
| Atualizado em | 2026-08-25 |
| Aprovação funcional | `SCRUM-7` e `SCRUM-36`: 2026-08-17; `SCRUM-35`: 2026-08-22; `SCRUM-37`: 2026-08-23 |
| Implementação aceita | `SCRUM-7` e `SCRUM-36`: 2026-08-17; `SCRUM-35` e `SCRUM-37`: Ivan em 2026-08-23 |
| Commit local | Stories: `cd2f903`, `d5adcc9`, `4f7d346`, `d73ff8d`; Postman: `44ce9b0`; reconciliação: `5c96181` |
| Integração remota | `SCRUM-7`/`SCRUM-36`: PR #12 (`d21fd3f`); `SCRUM-35`/`SCRUM-37`: PR #26 (`6b9f223`) |
| Jira | `SCRUM-7`, `SCRUM-36`, `SCRUM-35` e `SCRUM-37` no épico `SCRUM-13` |
| Escopo desta revisão | Especificação funcional aprovada da `SCRUM-37` |
| Gate atual | Implementação aceita e integrada em `dev` |

Referências:

- [SCRUM-7 — RF03: cadastrar Vehicle para Customer ativo][jira-scrum-7]
- [SCRUM-36 — RF04: atualizar dados descritivos de Vehicle][jira-scrum-36]
- [SCRUM-35 — RF05: atualizar quilometragem monotonicamente][jira-scrum-35]
- [SCRUM-37 — RF06: arquivar Vehicle logicamente][jira-scrum-37]
- [Context Map no Miro][miro-context-map]
- [Aggregates no Miro][miro-aggregates]
- `AGENTS.md`
- `docs/features/platform/context-alignment-and-project-standards/functional-spec.md`
- `docs/features/platform/context-alignment-and-project-standards/technical-spec.md`
- `docs/Architecture.md`

## Consolidação pós-implementação

As quatro stories estão integradas em `dev`. O slice final inclui cadastro vinculado a Customer ativo, atualização de
dados descritivos e chassis, quilometragem monotônica, detalhe histórico, lista ativa, archive lógico e bloqueio de
Vehicle ausente ou arquivado em novas Service Orders. `Vehicle` permanece aggregate independente em `registration`,
relacionado a Customer e Service Order somente por UUID e contratos públicos mínimos.

A iniciativa transversal de JWT foi integrada posteriormente. Todo `/api/vehicles/**` exige token válido com papel
`MANAGER` ou `ADMIN`. As menções a JWT pendente e PR #26 Draft nas seções históricas descrevem o estado observado
durante cada implementação, não o estado atual de `dev`.

## Estado da descoberta

A `SCRUM-7` foi relida diretamente no Jira em 2026-08-17. A story está `In Progress`, atribuída a Ivan Pimentel e
define o Vehicle como aggregate root independente de Registrations, associado a Customer por ID.

O texto da issue ainda cita o antigo módulo físico `customer` e registra AD-001 como bloqueio. Essa orientação foi
superada pela especificação de alinhamento aprovada pelo time: `registration` é o bounded context físico, e
`registration.vehicle` já existe como package placeholder reservado. A feature não criará um novo módulo de topo nem
colocará Vehicle dentro do aggregate de Customer.

As dependências funcionais de Customer (`SCRUM-6` e `SCRUM-33`) e as entregas `SCRUM-7` e `SCRUM-36` foram incorporadas
à `dev` pelo merge commit `d21fd3f`. A baseline foi reconciliada em `608dd29`, preservando `d5adcc9` na ancestralidade.

Esta especificação foi aprovada por Ivan Pimentel em 2026-08-17. A aprovação cobre o comportamento funcional completo,
incluindo os refinamentos de chassis opcional e do identificador contratual `id`. Ela libera a elaboração da
especificação técnica, mas não aprova antecipadamente o desenho técnico, o plano ou a implementação.

## Problema e resultado esperado

A oficina ainda não possui cadastro mestre de Vehicle. Sem esse cadastro, o Service Advisor precisa repetir dados do
veículo ao iniciar atendimentos e não consegue selecionar de forma confiável um Vehicle associado a um Customer ativo.
Também não existe uma identidade persistente que impeça placas ou chassis informados em duplicidade.

O resultado esperado é permitir o cadastro de um Vehicle válido e unicamente identificado, associado por ID a um
Customer existente e ativo. O Vehicle recebe identidade e lifecycle próprios dentro de Registrations, sem ser mantido
como coleção mutável do Customer, e fica elegível para consumo futuro pelo fluxo de Service Order.

## Linguagem ubíqua

### Vehicle

Vehicle é o cadastro mestre de um veículo atendido pela oficina. Possui `id` próprio, `customerId`, placa,
chassis/VIN opcional, marca, modelo, ano, cor e estado de lifecycle. A criação produz um Vehicle ativo.

Vehicle é um aggregate root independente. Customer não controla seu estado e não mantém uma coleção mutável de
Vehicles. A associação identifica o Customer responsável no momento do cadastro por meio de `customerId`.

### License Plate

`LicensePlate` identifica o Vehicle pela placa brasileira. São válidos os formatos canônicos:

- legado: `AAA0000`;
- Mercosul: `AAA0A00`.

A entrada aceita letras maiúsculas ou minúsculas e espaços externos. Para a placa legada, também aceita o hífen usual
`AAA-0000`. A representação canônica usa sete caracteres em maiúsculas e sem separador. Espaços internos, hífen em
placa Mercosul e qualquer outra formatação são inválidos.

### Chassis Number

`ChassisNumber` representa o chassis/VIN opcional do Vehicle. Quando informado, possui exatamente 17 caracteres
alfanuméricos. A entrada aceita letras maiúsculas ou minúsculas e espaços externos; a representação canônica usa letras
maiúsculas. Espaços internos, separadores e valor vazio são inválidos. A ausência do campo é representada por `null`.

Esta story não acrescenta validação de fabricante, região, ano codificado no VIN nem dígito verificador, pois esses
comportamentos não constam nos critérios atuais da `SCRUM-7`.

### Dados descritivos

Marca, modelo e cor descrevem o Vehicle e são obrigatórios. Valores compostos somente por espaços são inválidos; espaços
externos não fazem parte do valor armazenado. Limites técnicos de tamanho serão definidos na especificação técnica sem
alterar esse comportamento funcional.

### Ano do Vehicle

O ano é obrigatório e representa o ano-modelo. A `SCRUM-7` exige rejeitar uma faixa inválida, mas não define os limites.
Para fechar essa lacuna, este rascunho propõe aceitar anos entre 1886 e o ano civil seguinte ao da requisição,
inclusive.
O limite superior permite o cadastro comercial de um próximo ano-modelo; valores fora da faixa são inválidos.

Essa faixa foi aprovada por Ivan Pimentel em 2026-08-17.

## Atores e cenários

### Service Advisor

- informa o Customer responsável e os dados de identificação e descrição do Vehicle;
- cadastra o Vehicle quando o Customer existe, está ativo e todos os dados são válidos e únicos;
- recebe uma falha clara quando o Customer não existe ou está arquivado;
- recebe conflito quando a placa ou o chassis informado já identifica outro Vehicle;
- pode usar futuramente o `vehicleId` em um fluxo de Service Order, sem copiar o aggregate de Registrations.

### Usuário administrativo

- identifica o Vehicle cadastrado por seu `id`, placa e, quando presente, chassis canônicos na resposta de
  criação;
- não consegue criar identidades duplicadas por diferença de caixa ou formatação;
- não altera acidentalmente o cadastro do Customer ao registrar um Vehicle.

### Consumidor de Service Lifecycle

- referencia futuramente o Vehicle pelo ID e preserva seus próprios snapshots históricos;
- não importa tipos internos de Vehicle ou Customer;
- não tem sua integração implementada antecipadamente nesta story.

## Regras de negócio

### Criação e associação

- todo Vehicle possui `id` próprio e inicia ativo;
- `customerId`, placa, marca, modelo, ano e cor são obrigatórios;
- chassis é opcional; quando enviado, não pode ser vazio e deve ser válido;
- o `customerId` deve identificar um Customer existente e ativo no momento da criação;
- Customer inexistente impede o cadastro como not found;
- Customer arquivado impede o cadastro como conflito de lifecycle;
- falha na validação do Customer ou do Vehicle não persiste estado parcial;
- cadastrar um Vehicle não altera o Customer.

### Identidade e unicidade

- placa deve corresponder ao formato legado ou Mercosul definido nesta spec;
- chassis informado deve possuir exatamente 17 caracteres alfanuméricos;
- placa e chassis informado usam suas representações canônicas para validação, comparação e resposta;
- placa é única entre todos os Vehicles;
- chassis informado é único entre todos os Vehicles; ausência de chassis não constitui identidade nem conflito;
- a unicidade permanece reservada durante todo o lifecycle, inclusive após o futuro arquivamento do Vehicle;
- duplicidade de placa e duplicidade de chassis informado são conflitos observáveis distintos;
- placa e chassis não são alterados nesta story.

### Dados descritivos e ano

- marca, modelo e cor não podem ser nulos, vazios ou compostos somente por espaços;
- ano deve estar dentro da faixa funcional aprovada;
- valores inválidos são rejeitados antes da persistência;
- quilometragem não é informada por esta story e será tratada pela `SCRUM-35`.

### Falhas observáveis

- dados inválidos são rejeitados como erro de entrada;
- placa duplicada e chassis informado duplicado são rejeitados como conflitos, sem expor constraint, SQL ou detalhes
  internos;
- Customer inexistente é distinguido de Customer arquivado;
- nenhuma falha expõe classe de domínio, entidade JPA ou stack trace;
- códigos HTTP, códigos estáveis de erro e precedência quando mais de uma condição é inválida pertencem à especificação
  técnica, preservando as distinções funcionais acima.

## Compatibilidade e contratos funcionais

- a feature adiciona uma capacidade REST de cadastro de Vehicle sem alterar os contratos atuais de Customer;
- requests e responses usam DTOs; objetos de domínio e persistência não são contratos HTTP;
- o contrato retorna `id`, `customerId`, placa canônica, chassis canônico ou `null`, dados descritivos, ano e
  estado ativo;
- paths, status, payloads exatos, limites de campo e estratégia de concorrência serão definidos somente na
  especificação técnica;
- integrações com Service Order usarão uma API pública, IDs, eventos ou port pertencente ao consumidor, nunca imports
  internos entre bounded contexts.

## Fora de escopo da SCRUM-7

- atualizar marca, modelo, ano ou cor (`SCRUM-36`);
- registrar ou alterar quilometragem (`SCRUM-35`);
- arquivar, reativar ou excluir Vehicle (`SCRUM-37`);
- transferir um Vehicle para outro Customer;
- alterar placa ou chassis depois da criação;
- listar, pesquisar ou consultar Vehicles além da resposta necessária ao cadastro;
- criar Service Order, gerar `VehicleSnapshot` ou implementar integração com Service Lifecycle;
- alterar qualquer dado ou coleção dentro de Customer;
- criar um módulo de topo `vehicle` ou um novo bounded context;
- definir classes, schema, migration Flyway, endpoint, código HTTP ou mecanismo de lock/concorrência;
- implementar autenticação, autorização ou ownership; esses controles permanecem no slice transversal de segurança;
- alterar status, assignee, descrição ou comentários da `SCRUM-7` no Jira.

## Decisões funcionais aprovadas

- [x] Usar `id` como nome do identificador próprio do Vehicle no contrato.
- [x] Tratar chassis como opcional; quando informado, exigir formato válido e unicidade.
- [x] Normalizar caixa e espaços externos; aceitar `AAA-0000` como entrada legada e retornar placa sem hífen.
- [x] Considerar placa e chassis informado únicos sem diferença de caixa e reservá-los após arquivamento futuro.
- [x] Aceitar ano-modelo entre 1886 e o ano civil seguinte ao da requisição, inclusive.
- [x] Criar todo Vehicle como ativo e associado por `customerId`, sem coleção mutável em Customer.
- [x] Diferenciar Customer inexistente de Customer arquivado nas falhas observáveis.

## Critérios de aceite da SCRUM-7

### Cadastro válido

- [x] Dados válidos e únicos associados a um Customer ativo criam exatamente um Vehicle.
- [x] O Vehicle criado recebe `id` próprio, inicia ativo e referencia somente o `customerId`.
- [x] A resposta apresenta placa e chassis informado em representação canônica e preserva os dados válidos.
- [x] O cadastro não altera nenhum estado do Customer.

### Validação

- [x] Placa legado `AAA0000` e placa Mercosul `AAA0A00` são aceitas após a normalização aprovada.
- [x] Placa fora dos formatos permitidos é rejeitada sem persistência.
- [x] Chassis omitido ou `null` permite o cadastro e é retornado como `null`.
- [x] Chassis informado vazio ou diferente de 17 caracteres alfanuméricos é rejeitado sem persistência.
- [x] Marca, modelo ou cor ausente, vazia ou composta somente por espaços é rejeitada.
- [x] Ano fora da faixa aprovada é rejeitado; os dois limites da faixa são aceitos.

### Unicidade e Customer

- [x] Placa já cadastrada é rejeitada como conflito, inclusive quando varia apenas caixa ou o hífen legado aceito.
- [x] Chassis informado já cadastrado é rejeitado como conflito, inclusive quando varia apenas caixa.
- [x] As reservas de placa e chassis informado consideram Vehicles arquivados quando o lifecycle for introduzido.
- [x] Customer inexistente impede o cadastro e produz not found.
- [x] Customer arquivado impede o cadastro e produz conflito de lifecycle.
- [x] Qualquer falha deixa o cadastro sem persistência parcial.

### Fronteiras

- [x] Vehicle possui identidade, repository e lifecycle próprios dentro de `registration.vehicle`.
- [x] Customer não recebe uma coleção mutável de Vehicle e não é alterado pelo cadastro.
- [x] Nenhum contrato HTTP expõe objetos de domínio ou JPA.
- [x] Nenhum tipo interno de outro bounded context é importado.
- [x] Integração e snapshots de Service Order permanecem inalterados nesta story.

[jira-scrum-7]: https://matheusapostulo10.atlassian.net/browse/SCRUM-7
[jira-scrum-36]: https://matheusapostulo10.atlassian.net/browse/SCRUM-36
[jira-scrum-35]: https://matheusapostulo10.atlassian.net/browse/SCRUM-35
[jira-scrum-37]: https://matheusapostulo10.atlassian.net/browse/SCRUM-37
[miro-context-map]: https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679674975757
[miro-aggregates]: https://miro.com/app/board/uXjVH9faCu4=/?moveToWidget=3458764679684049703

## SCRUM-36 — Atualização dos dados descritivos e do chassis de Vehicle

### Estado da descoberta

A `SCRUM-36` foi relida diretamente no Jira em 2026-08-17. A story está `In Progress`, atribuída a Ivan Pimentel,
possui prioridade Medium e depende da `SCRUM-7`, que está `Done`. Seus critérios permitem alterar somente marca,
modelo, ano e cor, preservando identidade, associação, placa, chassis, quilometragem, lifecycle e snapshots históricos.

Ivan ampliou explicitamente o rascunho funcional em 2026-08-17 para permitir também o preenchimento de um chassis
ausente ou a substituição de um chassis existente. Essa decisão da sessão prevalece sobre a redação original da issue
para esta especificação local e integra o pacote funcional aprovado por Ivan em 2026-08-17. O Jira não será alterado sem
autorização específica.

As referências da issue ao antigo módulo físico `customer` e ao bloqueio por AD-001 estão desatualizadas diante do
alinhamento já aprovado e do código aceito da `SCRUM-7`. Vehicle permanece um aggregate independente em
`registration.vehicle`; essa reconciliação não cria um novo bounded context nem amplia o escopo da story.

Esta seção foi aprovada por Ivan Pimentel em 2026-08-17. A especificação técnica e o plano existentes continuam
aprovados e concluídos somente para a `SCRUM-7`; eles não definem nem autorizam a implementação da `SCRUM-36`. Esta
aprovação libera somente a elaboração da especificação técnica, que exige um novo aceite antes do plano ou do código.

### Problema e resultado esperado

Marca, modelo, ano-modelo, cor ou chassis podem ficar ausentes, incorretos ou desatualizados depois do cadastro inicial.
Sem um comando controlado de correção, o cadastro mestre deixa de representar o Vehicle atual ou exige mudanças
indevidas em outros campos de identidade e associação.

O resultado esperado é permitir que um usuário administrativo corrija o conjunto descritivo de um Vehicle ativo,
preencha seu chassis quando ausente ou substitua o chassis existente, e receba o estado atualizado. A operação preserva
todos os demais dados do aggregate e nunca reescreve informações já congeladas em Service Orders existentes.

### Linguagem e escopo dos dados atualizáveis

Para esta story, dados descritivos significam exatamente:

- `brand`: marca do Vehicle;
- `model`: modelo do Vehicle;
- `year`: ano-modelo do Vehicle;
- `color`: cor do Vehicle.

O comando também pode receber `chassis` para completar um cadastro que ainda não o possui ou substituir o valor atual.
Quando informado, o chassis mantém sua função de identidade alternativa e todas as regras de formato, normalização e
unicidade aprovadas no cadastro.

Placa permanece uma identidade imutável, `customerId` é a associação estável e `id` é a identidade do aggregate.
Quilometragem e lifecycle possuem comandos próprios e não podem ser alterados por esta operação.

O rascunho propõe receber os quatro campos descritivos como um conjunto completo. A omissão de qualquer um deles é
inválida, em vez de significar preservação implícita. Essa proposta mantém um contrato pequeno, não introduz semântica
de update parcial que o Jira não exige e torna explícito o estado final solicitado pelo usuário.

Chassis possui semântica diferente: sua omissão, envio como `null`, vazio ou composto somente por espaços significa
“não alterar” e preserva o valor atual. Somente o envio de um valor não vazio solicita inclusão ou substituição e aciona
as regras de formato e unicidade. A remoção de chassis fica fora desta story.

### Atores e cenários

#### Usuário administrativo

- identifica o Vehicle pelo ID e corrige marca, modelo, ano e cor em uma única operação;
- pode informar um chassis válido para preencher um cadastro sem chassis ou substituir o valor existente;
- recebe o estado completo atualizado quando todos os dados são válidos;
- pode reenviar o mesmo conjunto descritivo sem produzir mudança adicional;
- pode reenviar o mesmo chassis canônico sem produzir mudança adicional;
- recebe falha clara quando o Vehicle não existe, está arquivado, contém dados inválidos ou o novo chassis já pertence a
  outro Vehicle;
- não consegue usar o comando para transferir o Vehicle, trocar placa, remover chassis, registrar quilometragem ou mudar
  o lifecycle.

#### Consumidor de Service Lifecycle

- mantém inalterado cada `VehicleSnapshot` já criado, mesmo quando os dados mestres do Vehicle são corrigidos;
- poderá usar os dados mestres mais recentes em novos snapshots quando a integração entre contextos for especificada;
- não importa tipos internos de `registration.vehicle` e não recebe integração antecipada nesta story.

### Regras de negócio aprovadas

#### Atualização e validação

- somente Vehicle ativo aceita atualização descritiva;
- Vehicle inexistente impede a operação como not found;
- Vehicle arquivado impede a operação como conflito de lifecycle, sem alteração de estado;
- `brand`, `model`, `year` e `color` formam um conjunto completo e obrigatório;
- marca, modelo e cor seguem as mesmas regras do cadastro: removem espaços externos e não aceitam valor nulo, vazio ou
  composto somente por espaços;
- marca, modelo e cor preservam os limites vigentes de 100, 100 e 50 caracteres, respectivamente;
- ano segue a faixa funcional já aprovada: de 1886 ao ano civil seguinte ao da requisição, inclusive;
- chassis omitido, `null`, vazio ou composto somente por espaços preserva o valor atual sem falha;
- chassis não vazio remove espaços externos, usa letras maiúsculas e exige exatamente 17 caracteres alfanuméricos;
- chassis não vazio com separadores ou em formato inválido é rejeitado;
- chassis válido pode preencher um valor ausente ou substituir um valor existente;
- chassis informado permanece único entre todos os Vehicles, inclusive os futuramente arquivados;
- informar o mesmo chassis canônico do próprio Vehicle é idempotente e não constitui duplicidade;
- chassis pertencente a outro Vehicle produz conflito específico de unicidade;
- todos os novos valores são validados antes de qualquer mutação; uma falha preserva integralmente o estado anterior;
- reenviar exatamente os mesmos dados é idempotente e retorna o mesmo estado final válido.

#### Preservação do aggregate e de outros contextos

- a operação não altera `id`, `customerId`, placa, quilometragem ou `active`;
- a operação não consulta nem altera Customer, porque não modifica sua associação; o estado do Customer não muda a
  validade de uma correção descritiva ou de chassis em um Vehicle que permanece ativo;
- a reserva do chassis anterior permanece até a atualização ser concluída com sucesso; detalhes de concorrência e
  persistência pertencem à especificação técnica;
- a reserva da placa não é alterada ou recalculada;
- nenhum `VehicleSnapshot` existente é localizado, atualizado, substituído ou recriado;
- o estado mestre atualizado não retroage sobre Service Orders já existentes;
- a integração para formar novos snapshots continua pertencendo ao contexto consumidor e permanece fora desta story.

#### Falhas observáveis

- dados descritivos ausentes ou inválidos e chassis não vazio em formato inválido são rejeitados como erro de entrada;
- chassis já utilizado por outro Vehicle é rejeitado como conflito de unicidade distinto;
- Vehicle inexistente é distinguido de Vehicle arquivado;
- uma falha não persiste atualização parcial;
- nenhuma falha expõe SQL, constraint, entidade JPA, classe de domínio ou stack trace;
- paths, método HTTP, status, códigos estáveis de erro e precedência técnica serão definidos somente após este gate.

### Compatibilidade funcional

- a capacidade de atualização é aditiva e não muda o comportamento de `POST /api/vehicles`;
- a resposta reutiliza a representação pública completa do Vehicle, apresenta os quatro valores atualizados e o chassis
  novo ou preservado;
- campos preservados continuam com os mesmos nomes e valores observáveis de antes da atualização;
- objetos de domínio e persistência não se tornam contratos HTTP;
- nenhum contrato de Customer ou Service Lifecycle é alterado;
- a ausência atual de integração entre Vehicle e Service Order não autoriza reescrever snapshots enviados ou
  persistidos pelo consumidor.

### Fora de escopo da SCRUM-36

- atualização parcial ou remoção de qualquer dado descritivo;
- alteração de placa, `customerId` ou `id`;
- remoção de chassis ou liberação de seu valor para reutilização por outro Vehicle;
- registro ou alteração de quilometragem (`SCRUM-35`);
- arquivamento, reativação ou exclusão de Vehicle (`SCRUM-37`);
- listar, pesquisar ou criar uma nova consulta de Vehicle;
- alterar Customer ou bloquear a correção por causa do estado atual de Customer;
- criar, atualizar ou migrar `VehicleSnapshot` e implementar a integração com Service Lifecycle;
- adicionar histórico de alterações, versão, auditoria, motivo, data ou ator da correção;
- definir endpoint, verbo HTTP, DTO, código HTTP, código de erro, transação ou estratégia de persistência;
- implementar autenticação, autorização ou ownership;
- alterar status, descrição, assignee ou comentários da `SCRUM-36` no Jira.

### Decisões funcionais aprovadas

- [x] Permitir alteração de `brand`, `model`, `year`, `color` e, opcionalmente, do chassis.
- [x] Exigir os quatro campos em cada comando, sem update parcial ou preservação por omissão.
- [x] Reutilizar normalização, limites de tamanho e faixa de ano aprovados no cadastro.
- [x] Preservar chassis quando omitido, `null`, vazio ou em branco; preencher ou substituir com valor não vazio.
- [x] Rejeitar somente chassis não vazio em formato inválido e manter a remoção de chassis fora de escopo.
- [x] Preservar unicidade global do chassis e tratar o mesmo valor do próprio Vehicle como idempotente.
- [x] Distinguir chassis inválido de chassis já pertencente a outro Vehicle.
- [x] Aceitar atualização apenas de Vehicle ativo; usar not found para ausente e conflito para arquivado.
- [x] Considerar idempotente o reenvio do mesmo conjunto descritivo.
- [x] Preservar todo o estado não descritivo sem consultar novamente o Customer associado.
- [x] Manter snapshots existentes imutáveis e deixar a formação de novos snapshots para a integração consumidora.

### Critérios de aceite da SCRUM-36

#### Atualização válida

- [x] Um Vehicle ativo existente aceita um conjunto válido de marca, modelo, ano e cor.
- [x] A operação persiste e retorna os quatro valores normalizados e o chassis novo ou preservado.
- [x] Um Vehicle sem chassis aceita um chassis válido e passa a retorná-lo em representação canônica.
- [x] Um Vehicle com chassis aceita sua substituição por outro chassis válido e disponível.
- [x] Chassis omitido, `null`, vazio ou em branco preserva o valor atual sem falha.
- [x] O reenvio dos mesmos valores produz sucesso idempotente e o mesmo estado final.

#### Validação e atomicidade

- [x] A omissão de qualquer campo descritivo é rejeitada sem persistência.
- [x] Marca, modelo ou cor nula, vazia, em branco ou acima do limite vigente é rejeitada sem persistência.
- [x] Ano anterior a 1886 ou posterior ao ano civil seguinte à requisição é rejeitado sem persistência.
- [x] Os limites válidos do ano e dos tamanhos dos textos são aceitos.
- [x] Chassis não vazio fora do formato aprovado é rejeitado sem persistência.
- [x] Chassis já pertencente a outro Vehicle é rejeitado como conflito sem liberar nenhum dos dois valores.
- [x] Reenviar o mesmo chassis canônico do próprio Vehicle não produz conflito.
- [x] Quando qualquer valor é inválido ou duplicado, nenhum dado descritivo ou chassis é alterado.

#### Identidade, associação e lifecycle

- [x] A atualização preserva `id`, `customerId`, placa e `active`.
- [x] A quilometragem permanece inalterada quando for introduzida pela `SCRUM-35`.
- [x] Vehicle inexistente produz not found e não cria cadastro novo.
- [x] Vehicle arquivado produz conflito de lifecycle e não é alterado.
- [x] O comando não consulta, altera ou reativa o Customer associado.

#### Fronteiras e histórico

- [x] Nenhum `VehicleSnapshot` existente é reescrito depois da atualização.
- [x] Nenhum contrato ou dado de Customer e Service Lifecycle é alterado.
- [x] Nenhum objeto de domínio ou JPA é exposto como contrato HTTP.
- [x] A operação não antecipa quilometragem, arquivamento, consulta adicional ou integração entre módulos.

## SCRUM-35 — Atualização monotônica da quilometragem de Vehicle

### Estado da descoberta

A `SCRUM-35` foi relida diretamente no Jira em 2026-08-22. A story está `In Progress`, atribuída e reportada por Ivan
Pimentel, possui prioridade Medium e depende da `SCRUM-7`, que está `Done`. O Jira exige um comando de aggregate que
aceite quilometragem maior ou igual à atual, rejeite valor negativo ou decrescente sem persistência, altere somente um
Vehicle e respeite o tratamento já definido para Vehicle ausente ou arquivado.

O texto da issue ainda cita o antigo módulo físico `customer` e o bloqueio por AD-001. Essas referências estão
desatualizadas diante do alinhamento arquitetural aprovado e da implementação já integrada: Vehicle permanece um
aggregate independente em `registration.vehicle`, dentro do bounded context Registrations.

O PR #12 foi mesclado em `dev` pelo commit `d21fd3f`. A branch `feat/registration-vehicle-management2` parte da baseline
`608dd29`, na qual `d5adcc9` é ancestral. O gate `verify` dessa baseline passou com 308 testes, sem falhas, erros ou
skips, antes deste rascunho.

Esta seção foi aprovada por Ivan Pimentel em 2026-08-22, incluindo `mileage` opcional no cadastro e o comando específico
para registro ou atualização posterior. Os gates seguintes também foram aprovados; Ivan aceitou a implementação em
2026-08-23, registrada no commit local `4f7d346`.

### Problema e resultado esperado

O cadastro mestre de Vehicle ainda não registra sua quilometragem atual. Sem esse estado, a oficina não consegue manter
uma referência confiável do hodômetro e pode aceitar por engano uma correção que faça o valor retroceder.

O resultado esperado é permitir que um usuário autorizado informe opcionalmente a quilometragem ao cadastrar um
Vehicle e a registre ou atualize posteriormente por um comando específico, sem nunca reduzir o valor já registrado.
As duas operações preservam os limites do aggregate e não reescrevem snapshots ou dados de outros bounded contexts.

### Linguagem e estado funcional

#### Quilometragem atual

`mileage` representa a leitura atual do hodômetro em quilômetros inteiros. Frações, valores negativos e valores que não
possam ser interpretados como um número inteiro são inválidos. Não há um limite máximo de negócio específico no MVP; o
contrato técnico deverá suportar valores inteiros não negativos sem truncamento silencioso.

Esta implementação amplia de forma compatível o cadastro de Vehicle para aceitar `mileage` opcional. Quando o campo for
omitido ou enviado como `null`, o Vehicle permanece com quilometragem **não informada**. Quando presente, o valor deve
ser um número inteiro maior ou igual a zero e passa a ser a primeira leitura registrada.

Vehicles já existentes permanecem com quilometragem não informada até receberem o primeiro comando específico. Esse
estado não equivale a `0 km` e não fabrica uma leitura de hodômetro para dados já persistidos. O primeiro comando aceita
qualquer valor inteiro não negativo, inclusive zero.

Depois que uma leitura for registrada no cadastro ou no comando específico, somente um valor maior altera a
quilometragem; um valor igual é sucesso idempotente e mantém o estado; um valor menor é rejeitado.

#### Monotonicidade

Monotonicidade significa que a quilometragem persistida de um Vehicle nunca pode diminuir. A comparação usa o estado
mais recente do próprio aggregate, inclusive quando existem comandos concorrentes. Se duas atualizações competirem, uma
delas não poderá sobrescrever uma quilometragem maior já confirmada com um valor menor.

### Atores e cenários

#### Service Advisor

- identifica um Vehicle pelo `id` e registra sua quilometragem atual em quilômetros inteiros;
- pode informar a primeira leitura durante o cadastro ou deixá-la não informada;
- registra o primeiro valor quando ainda não existe leitura anterior;
- aumenta a quilometragem quando a nova leitura é maior que a atual;
- pode reenviar a leitura atual e recebe sucesso idempotente, sem mudança observável de estado;
- recebe falha clara para valor negativo, fracionário, decrescente, Vehicle ausente ou Vehicle arquivado;
- não consegue alterar dados descritivos, identidade, associação ou lifecycle por esse comando.

#### Consumidor de Service Lifecycle

- mantém inalterados os `VehicleSnapshot` já existentes;
- não recebe integração, snapshot de quilometragem ou contrato entre módulos antecipado nesta story;
- continua sem importar tipos internos de `registration.vehicle`.

### Regras de negócio propostas

#### Primeiro registro e atualizações posteriores

- o cadastro aceita `mileage` opcional;
- `mileage` omitida ou `null` no cadastro mantém a quilometragem não informada;
- `mileage` presente no cadastro deve ser um número inteiro maior ou igual a zero e se torna a primeira leitura;
- valor negativo, fracionário ou malformado no cadastro rejeita todo o Vehicle sem persistência parcial;
- Vehicle cadastrado sem leitura pode permanecer com quilometragem não informada até o primeiro comando específico;
- o primeiro comando específico aceita qualquer número inteiro maior ou igual a zero;
- depois do primeiro registro, valor maior é aceito e se torna a nova quilometragem atual;
- valor igual ao atual é idempotente, retorna sucesso e preserva exatamente o mesmo estado;
- valor menor que o atual é rejeitado e não persiste nenhuma mudança;
- valor negativo ou fracionário é rejeitado e não persiste nenhuma mudança;
- o comando trata uma única leitura absoluta do hodômetro, não um incremento a somar ao valor atual;
- todos os valores são validados antes da mutação do aggregate.

#### Lifecycle, isolamento e concorrência

- somente Vehicle ativo aceita o primeiro registro ou a atualização de quilometragem;
- Vehicle inexistente impede a operação como not found;
- Vehicle arquivado impede a operação como conflito de lifecycle, sem alteração de estado;
- atualizar um Vehicle não lê, altera ou bloqueia outro Vehicle sem relação com o comando;
- comandos concorrentes para o mesmo Vehicle são avaliados contra o estado confirmado mais recente;
- a ordem de conclusão nunca pode fazer a quilometragem persistida retroceder;
- falha de validação, lifecycle ou concorrência preserva integralmente o estado anterior.

#### Estado preservado

- a operação altera somente `mileage` quando recebe um valor maior ou registra a primeira leitura;
- `id`, `customerId`, placa, chassis, marca, modelo, ano, cor e `active` permanecem inalterados;
- o Customer associado não é consultado nem alterado;
- nenhum `VehicleSnapshot`, Service Order ou outro dado de Service Lifecycle é localizado ou reescrito;
- nenhum histórico de leituras, data, motivo, ator ou trilha de auditoria é criado nesta story.

#### Falhas observáveis

- no comando específico, entrada ausente, nula, fracionária, malformada ou negativa é rejeitada como erro de entrada;
- valor menor que a quilometragem atual é rejeitado como violação da regra monotônica;
- Vehicle inexistente é distinguido de Vehicle arquivado;
- falhas não ecoam dados operacionais desnecessários nem expõem SQL, entidade JPA, classe interna ou stack trace;
- paths, método HTTP, status, códigos estáveis de erro e precedência técnica serão definidos após o gate funcional.

### Compatibilidade funcional

- a capacidade é aditiva e não altera os comandos existentes de cadastro e atualização descritiva;
- o cadastro de Vehicle recebe `mileage` como campo opcional, preservando requests existentes que não o enviam;
- `mileage` omitida ou `null` não é convertida para zero; valor presente válido é persistido como primeira leitura;
- a representação pública de Vehicle poderá expor aditivamente `mileage`, usando ausência explícita enquanto nenhuma
  leitura tiver sido registrada e o valor inteiro atual depois do primeiro registro;
- o comando de quilometragem retorna o estado público atualizado ou idempotente do mesmo Vehicle;
- requests e responses continuam usando DTOs, sem expor objetos de domínio ou persistência;
- nenhum contrato de Customer, Stock & Procurement ou Service Lifecycle é alterado.

### Fora de escopo da SCRUM-35

- reduzir, zerar ou corrigir a quilometragem depois do primeiro registro;
- tratar troca, rollover ou defeito do hodômetro;
- registrar histórico de leituras, data, origem, motivo, ator ou auditoria;
- calcular distância percorrida, consumo, manutenção preventiva ou próxima revisão;
- alterar marca, modelo, ano, cor, chassis, placa, `customerId`, `id` ou lifecycle;
- arquivar, reativar ou excluir Vehicle (`SCRUM-37`);
- criar consulta adicional ou listagem de Vehicles;
- alterar ou criar `VehicleSnapshot` e integrar a quilometragem com Service Lifecycle;
- definir endpoint, verbo HTTP, DTO, código HTTP, código de erro, lock, schema ou migration;
- implementar autenticação, autorização ou ownership;
- alterar status, descrição, assignee, links ou comentários da `SCRUM-35` no Jira;
- alterar Miro.

### Decisões funcionais aprovadas

- [x] Representar quilometragem como quilômetros inteiros por meio do campo canônico `mileage`.
- [x] Permitir `mileage` opcional no cadastro; omissão ou `null` mantém o valor não informado.
- [x] Aceitar zero ou qualquer inteiro positivo como primeira leitura, no cadastro ou no comando específico.
- [x] Manter `mileage` não informada para Vehicles existentes até o primeiro comando, sem presumir `0 km`.
- [x] Aceitar e persistir somente valor maior depois do primeiro registro.
- [x] Tratar valor igual como sucesso idempotente, sem mudança observável de estado.
- [x] Rejeitar valor negativo, fracionário ou menor que o atual sem persistência.
- [x] Interpretar a entrada como leitura absoluta do hodômetro, e não como incremento.
- [x] Aplicar a regra somente a Vehicle ativo, distinguindo ausente de arquivado.
- [x] Garantir monotonicidade também entre comandos concorrentes para o mesmo Vehicle.
- [x] Preservar todo estado não relacionado e todos os snapshots existentes.
- [x] Manter histórico de leituras, correção para baixo e integração com Service Lifecycle fora do MVP desta story.

### Critérios de aceite da SCRUM-35

#### Primeiro registro

- [x] O cadastro sem `mileage` ou com `null` cria o Vehicle com quilometragem não informada.
- [x] O cadastro com zero ou inteiro positivo cria o Vehicle com essa primeira leitura persistida e retornada.
- [x] Valor negativo, fracionário ou malformado no cadastro rejeita todo o Vehicle sem persistência parcial.
- [x] Vehicle ativo com `mileage` não informada aceita zero ou um inteiro positivo no comando específico.
- [x] O primeiro registro altera exatamente um Vehicle e preserva todos os demais campos.
- [x] No comando específico, valor negativo, fracionário, nulo, ausente ou malformado é rejeitado sem registrar
  quilometragem.

#### Atualização monotônica e idempotência

- [x] Valor maior que a quilometragem atual é aceito, persistido e retornado.
- [x] Valor igual à quilometragem atual retorna sucesso idempotente e mantém o mesmo estado.
- [x] Valor menor que a quilometragem atual é rejeitado e o valor anterior permanece persistido.
- [x] Uma falha não altera nenhum campo do Vehicle nem produz persistência parcial.

#### Lifecycle, isolamento e concorrência

- [x] Vehicle inexistente produz not found e não cria cadastro novo.
- [x] Vehicle arquivado produz conflito de lifecycle e não é alterado.
- [x] Atualizar um Vehicle não modifica outro Vehicle nem o Customer associado.
- [x] Atualizações concorrentes nunca deixam persistido um valor menor que outro já confirmado.

#### Compatibilidade e fronteiras

- [x] Requests antigos de cadastro continuam válidos porque `mileage` é opcional.
- [x] A atualização descritiva continua compatível e não aceita alteração indireta de `mileage`.
- [x] A resposta pública diferencia quilometragem não informada de `0 km` e retorna o valor atual após o registro.
- [x] `id`, `customerId`, placa, chassis, marca, modelo, ano, cor e `active` permanecem inalterados.
- [x] Nenhum `VehicleSnapshot`, Service Order ou contrato de outro bounded context é alterado.
- [x] Nenhum objeto de domínio ou JPA é exposto como contrato HTTP.

## SCRUM-37 — Arquivamento lógico de Vehicle

### Estado da descoberta funcional

Esta especificação usa somente as fontes locais já preservadas. O resumo da `SCRUM-37` registra três critérios
vinculantes:

- arquivar Vehicle logicamente e excluí-lo de novas Service Orders;
- preservar consultas históricas por ID e os `VehicleSnapshot` já existentes;
- retornar not found ao tentar arquivar um Vehicle inexistente.

Nenhuma leitura ou alteração de Jira/Miro foi realizada neste gate. Ivan confirmou em 2026-08-23 que o resumo local e
as decisões propostas representam o comportamento desejado, incluindo os GETs e o add-on da collection Postman.

O código já persiste `active` em Vehicle e bloqueia atualização descritiva ou de quilometragem quando o aggregate está
arquivado. Ainda não existem comando de arquivamento, consulta pública de Vehicle por ID ou listagem GET de ativos. A
criação atual de Service Order aceita `vehicleId` e `VehicleSnapshot` enviados pelo cliente sem consultar Registrations;
portanto, hoje ela não consegue excluir Vehicle arquivado de novo trabalho.

Esta seção foi aprovada por Ivan Pimentel em 2026-08-23. Os gates técnico e de plano foram concluídos depois dessa
aprovação, e Ivan autorizou o código na mesma data. A implementação foi verificada e aceita manualmente em 2026-08-23.

### Problema e resultado esperado

Um Vehicle que não pode mais receber novos serviços precisa sair do fluxo operacional sem perder sua identidade nem o
histórico da oficina. Exclusão física apagaria a referência mestre e poderia quebrar rastreabilidade, enquanto continuar
aceitando novas Service Orders para um Vehicle arquivado violaria o lifecycle.

O resultado esperado é um arquivamento lógico, irreversível no MVP e seguro para repetição. Depois de confirmado, o
Vehicle permanece consultável para fins históricos, conserva todos os dados cadastrais e deixa de ser elegível para nova
Service Order. Ordens já existentes continuam usando seus próprios snapshots imutáveis.

### Atores e cenários

- Um atendente ou administrador arquiva um Vehicle que não deve receber novos trabalhos.
- O mesmo comando pode ser repetido sem apagar dados nem produzir efeitos adicionais.
- Um atendente consulta por ID um Vehicle ativo ou arquivado para identificar uma referência histórica.
- Um atendente lista todos os Vehicles ativos e consulta suas informações cadastrais completas.
- Ao abrir uma nova Service Order, o sistema verifica a elegibilidade atual do `vehicleId` antes de confirmar a ordem.
- Uma Service Order anterior ao arquivamento continua legível e mantém o `VehicleSnapshot` original.

### Linguagem e estados funcionais

#### Vehicle ativo

- nasce com `active=true` e permanece elegível para os comandos já aprovados;
- pode ser associado a uma nova Service Order, desde que as demais regras desse fluxo também sejam satisfeitas;
- aparece na listagem operacional de Vehicles;
- sua identidade é composta pelo `id` estável e pelas identidades de placa e chassis quando informado.

#### Vehicle arquivado

- permanece persistido com o mesmo `id`, `customerId`, placa, chassis, descrições, ano, cor e mileage;
- não aceita atualização descritiva nem registro ou aumento de mileage;
- não pode ser usado para confirmar uma nova Service Order;
- continua disponível na consulta histórica por ID e informa `active=false`;
- não aparece na listagem operacional de Vehicles ativos;
- não pode ser reativado, excluído fisicamente nem ter sua identidade reutilizada nesta story.

#### Consulta histórica por ID

O critério local exige preservar leitura histórica, mas a API pública de Vehicle ainda não oferece consulta por ID. A
especificação introduz `GET /api/vehicles/{id}` como parte da `SCRUM-37`, retornando ativos e arquivados. Sem essa
adição, o critério ficaria verificável apenas internamente e não teria um comportamento público equivalente ao de
Customer.

A consulta histórica retorna o estado público completo e atual do Vehicle, inclusive `active` e mileage quando
informada. Vehicle inexistente continua distinto de Vehicle arquivado.

#### Listagem operacional de Vehicles

`GET /api/vehicles` retorna, sem paginação, todos os Vehicles ativos, seguindo a semântica atual de
`GET /api/customers`. Cada item usa a representação pública completa de Vehicle: `id`, `customerId`, `licensePlate`,
`chassis`, `brand`, `model`, `year`, `color`, `mileage` e `active`.

- Vehicle arquivado não aparece na coleção, mas permanece acessível pela consulta histórica por ID;
- a ausência de Vehicles ativos retorna sucesso com coleção vazia;
- a listagem não oferece busca, filtro, paginação nem garantia de ordenação nesta story;
- a leitura não altera Vehicle, Customer, Service Order ou qualquer snapshot.

### Regras de negócio

#### Arquivamento

- arquivar um Vehicle ativo altera somente seu lifecycle para arquivado;
- repetir o arquivamento é idempotente e mantém o mesmo estado final;
- o registro persistido nunca é removido e nenhuma referência histórica é reescrita;
- placa e o chassis, quando informado, permanecem reservados após o arquivamento para evitar ambiguidade histórica;
- Customer associado e outros Vehicles não são alterados;
- o arquivamento não depende da inexistência de Service Orders abertas ou concluídas.

#### Elegibilidade para nova Service Order

- uma nova Service Order só pode ser confirmada quando o `vehicleId` referencia um Vehicle ativo;
- Vehicle inexistente ou arquivado rejeita a criação antes de save, notificação ou outro efeito parcial;
- a validação de lifecycle não altera o `VehicleSnapshot` recebido nem snapshots já persistidos;
- validar se o `customerId` da ordem é o proprietário do Vehicle não faz parte desta especificação;
- validar se o snapshot recebido coincide com os dados vivos do Vehicle não faz parte desta especificação.

#### Concorrência entre arquivamento e novo trabalho

Arquivamento e criação concorrente de Service Order devem produzir uma ordem observável e consistente:

- se a Service Order for confirmada primeiro, o arquivamento posterior não altera nem invalida essa ordem;
- se o arquivamento for confirmado primeiro, a nova Service Order é rejeitada;
- depois de o sistema responder sucesso ao arquivamento, nenhuma Service Order posterior pode ser confirmada para o
  mesmo Vehicle.

#### Histórico e snapshots

- `VehicleSnapshot` permanece pertencendo à Service Order e nunca é recalculado a partir do cadastro vivo;
- arquivar Vehicle não modifica status, execução, Estimate, prioridade ou qualquer outro dado de Service Order;
- consultas históricas de Service Order continuam retornando o snapshot gravado na criação;
- a consulta histórica de Vehicle retorna o cadastro mestre, não substitui nem corrige snapshots.

### Falhas e precedência funcional

- contrato inválido é rejeitado antes de procurar ou alterar Vehicle;
- arquivar ID inexistente retorna not found e não cria registro;
- repetir o arquivamento de um Vehicle existente retorna sucesso idempotente;
- consulta histórica de ID inexistente retorna not found;
- tentativa de nova Service Order com Vehicle inexistente ou arquivado falha sem persistência ou notificação parcial;
- a ausência de Vehicles ativos na listagem retorna coleção vazia e não é tratada como not found;
- erros não expõem SQL, constraint, entidade JPA, classe interna, stack trace ou dados operacionais desnecessários;
- método e path do arquivamento, status HTTP, códigos estáveis e precedência técnica serão definidos após este gate.

### Compatibilidade funcional

- cadastro, atualização descritiva e atualização de mileage mantêm seus contratos atuais;
- `active` já existe na representação pública e passa a refletir o arquivamento sem novo campo incompatível;
- as consultas GET por ID e da coleção são aditivas e reutilizam a representação pública completa de Vehicle;
- o fluxo de criação de Service Order passa a validar elegibilidade, corrigindo a lacuna hoje documentada nesse slice;
- Service Orders existentes, seus IDs e snapshots permanecem funcionalmente inalterados pelo arquivamento;
- nenhum objeto de domínio ou JPA passa a ser exposto como contrato HTTP.

### Add-on — organização da collection Postman

A collection deve apresentar uma entrada por operação, em vez de repetir o mesmo endpoint para demonstrar combinações
de campos opcionais. O exemplo principal de criação ou atualização mostra todos os campos aceitos pelo respectivo
request, enquanto a descrição identifica o que pode ser omitido e qual estado é preservado.

- nomes de folders e requests permanecem em inglês e seguem o padrão verbo + recurso ou intenção;
- Customer mantém uma única criação completa, incluindo contato e endereço, e uma única atualização de contato com
  email, telefone e endereço;
- Vehicle mantém uma única criação completa, uma única atualização descritiva completa e o comando separado de mileage;
- chassis e mileage continuam opcionais na criação mesmo aparecendo no exemplo completo;
- chassis continua opcional na atualização descritiva, com omissão, `null`, vazio ou branco preservando o valor atual;
- variações positivas ou negativas não criam entradas duplicadas para o mesmo método e URL;
- quando a SCRUM-37 for implementada, a pasta Vehicle receberá `Get vehicle`, `List vehicles` e `Archive vehicle`;
- a organização não altera comportamento da API, e a OpenAPI gerada permanece a fonte de verdade dos contratos.

### Fora de escopo da SCRUM-37

- reativar Vehicle arquivado;
- excluir Vehicle fisicamente ou remover referências existentes;
- arquivamento em lote;
- registrar motivo, data, ator, observação ou histórico de transições;
- transferir Vehicle entre Customers ou alterar `customerId`;
- liberar ou reutilizar placa/chassis de Vehicle arquivado;
- listar Vehicles arquivados ou oferecer busca, filtros, paginação ou ordenação contratual;
- alterar marca, modelo, ano, cor, chassis, placa ou mileage durante o arquivamento;
- validar ownership entre `customerId` e `vehicleId` na Service Order;
- substituir o snapshot enviado por uma cópia automática do cadastro vivo;
- alterar Service Orders ou `VehicleSnapshot` já persistidos;
- implementar autenticação, autorização por papel ou ownership;
- definir classes, ports, locks, schema, migration ou estratégia de integração entre módulos;
- alterar status, descrição, assignee, links ou comentários no Jira;
- alterar Miro.

### Decisões funcionais aprovadas

- [x] Arquivamento é lógico, irreversível e idempotente no MVP.
- [x] Arquivar altera somente `active` e preserva integralmente todos os demais campos.
- [x] Placa e chassis permanecem reservados em todos os estados do lifecycle.
- [x] Vehicle arquivado continua bloqueado para atualização descritiva e de mileage.
- [x] Consulta histórica por ID é incluída nesta story e retorna ativos ou arquivados.
- [x] `GET /api/vehicles` lista todos os Vehicles ativos com a representação pública completa.
- [x] A listagem segue Customers: não paginada, exclui arquivados e retorna coleção vazia quando não há ativos.
- [x] Nova Service Order exige Vehicle existente e ativo antes de qualquer persistência ou notificação.
- [x] A validação de elegibilidade não confere ownership nem reconcilia o snapshot recebido.
- [x] Concorrência respeita a ordem de confirmação entre nova Service Order e arquivamento.
- [x] Service Orders e `VehicleSnapshot` existentes permanecem inalterados.
- [x] A collection Postman usa uma entrada em inglês por operação e exemplos com todos os campos aceitos.
- [x] Reativação, hard delete e audit trail permanecem fora do MVP.
- [x] Contratos técnicos e integração entre módulos serão decididos apenas na especificação técnica.

### Critérios de aceite da SCRUM-37

#### Arquivamento e idempotência

- [x] Arquivar Vehicle ativo retorna sucesso e passa a representá-lo com `active=false`.
- [x] Repetir o arquivamento retorna sucesso idempotente sem alterar qualquer outro campo.
- [x] Arquivar Vehicle inexistente retorna not found e não cria registro.
- [x] A linha, o `id`, `customerId`, placa, chassis, descrições, ano, cor e mileage permanecem preservados.
- [x] Placa e chassis de Vehicle arquivado não podem ser reutilizados em novo cadastro.
- [x] Nenhum adapter ou fluxo executa hard delete.

#### Lifecycle e consultas GET

- [x] Vehicle arquivado rejeita atualização descritiva e de mileage sem persistência parcial.
- [x] `GET /api/vehicles/{id}` retorna Vehicle ativo ou arquivado com o lifecycle correto.
- [x] Consulta de ID inexistente retorna not found.
- [x] `GET /api/vehicles` retorna todos os Vehicles ativos e exclui todos os arquivados.
- [x] Cada item listado contém `id`, `customerId`, `licensePlate`, `chassis`, `brand`, `model`, `year`, `color`,
  `mileage` e `active`.
- [x] Nenhum Vehicle ativo retorna sucesso com coleção vazia.
- [x] A listagem não introduz busca, filtros, paginação ou ordenação contratual.

#### Novos trabalhos e concorrência

- [x] Nova Service Order para Vehicle ativo continua válida quando os demais dados são aceitos.
- [x] Nova Service Order para Vehicle inexistente ou arquivado é rejeitada antes de save e notificação.
- [x] Se a ordem for confirmada antes do arquivamento, ela permanece válida e histórica.
- [x] Se o arquivamento for confirmado primeiro, a criação concorrente da ordem é rejeitada.
- [x] Depois do sucesso do arquivamento, nenhuma ordem posterior é confirmada para o mesmo Vehicle.

#### Histórico, compatibilidade e fronteiras

- [x] Service Orders existentes preservam `vehicleId`, status, relacionamentos e `VehicleSnapshot` sem alteração.
- [x] Cadastro e comandos já existentes preservam seus contratos e comportamentos aprovados.
- [x] Customer, outros Vehicles, Stock & Procurement e demais aggregates não são alterados pelo arquivamento.
- [x] A integração necessária respeita as fronteiras Modulith e não expõe pacotes internos de outro módulo.
- [x] Requests e responses continuam usando DTOs, nunca objetos de domínio ou JPA.

#### Add-on da collection Postman

- [x] A pasta Customer possui somente uma entrada por operação, com criação e atualização de contato completas.
- [x] A pasta Vehicle possui somente uma entrada para criação, atualização descritiva e atualização de mileage.
- [x] Todos os nomes das operações de Customer e Vehicle seguem o padrão existente em inglês.
- [x] Os exemplos completos incluem todos os campos aceitos e as descrições explicam os campos opcionais.
- [x] `Get vehicle`, `List vehicles` e `Archive vehicle` foram adicionados com os contratos implementados.
- [x] A collection continua válida como JSON e sincronizada com a OpenAPI gerada.

# Especificação Funcional: Abertura completa de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-full-intake` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-16 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-16 |
| Referências | Enunciado do Tech Challenge Fase 2 (API de abertura de OS); `service-order-creation`; `service-order-initial-assessment`; `perform-diagnosis`; `estimate-generation`; `registration/customer-management`; `registration/vehicle-management`; ADR-005 |

## Problema e resultado esperado

O enunciado da Fase 2 pede uma API de abertura de OS que receba, em uma única chamada, os dados do cliente, do
veículo, dos serviços e das peças. Hoje essa abertura exige várias chamadas em sequência, cada uma em um endpoint:

1. identificar ou cadastrar o Customer (`/api/customers`);
2. cadastrar o Vehicle (`/api/vehicles`);
3. criar a Service Order (`POST /api/service-orders`), informando IDs e digitando à mão um `vehicleSnapshot`;
4. atribuir o Technician responsável pelo diagnóstico;
5. registrar o Diagnosis com os serviços e as peças;
6. gerar o Estimate.

Esta feature adiciona uma **abertura completa**: uma única operação que recebe os dados de negócio, conduz esses passos
na ordem correta e devolve a Service Order aberta. A operação é **aditiva**: os endpoints existentes, inclusive
`POST /api/service-orders`, continuam com o mesmo contrato e o mesmo comportamento.

Ao final de uma abertura completa bem-sucedida:

- o Customer está identificado pelo CPF/CNPJ, já existente ou recém-cadastrado;
- o Vehicle está identificado pela placa, já existente ou recém-cadastrado, e vinculado a esse Customer;
- existe uma Service Order com o `VehicleSnapshot` copiado do cadastro do Vehicle, e não digitado pelo cliente da API;
- se serviços foram informados, existe um Diagnosis com uma Service Execution por serviço, os Stock Requirements de
  cada uma e um Estimate gerado automaticamente;
- se nenhum serviço foi informado, a Service Order fica em `RECEIVED`, como na criação atual.

## Atores e cenários

- **Atendente (papel `MANAGER` ou `ADMIN`)** abre a OS no balcão.

### Cenário 1: cliente e veículo novos, com serviços e peças

O atendente informa o nome, o CPF/CNPJ e o contato do Customer; a placa, a marca, o modelo, o ano e a cor do Vehicle;
a prioridade e a triagem inicial; o Technician responsável pelo diagnóstico; e os serviços do Service Catalog, cada um
com as peças/insumos e as quantidades. O sistema cadastra o Customer e o Vehicle, abre a Service Order, registra o
Diagnosis e gera o Estimate. A resposta traz a Service Order em `AWAITING_APPROVAL` e a identificação do Estimate
gerado.

### Cenário 2: cliente e veículo já cadastrados

O CPF/CNPJ e a placa já existem e estão ativos, e o veículo pertence a esse cliente. O sistema reaproveita os cadastros
existentes, sem alterá-los, e segue o mesmo fluxo do cenário 1.

### Cenário 3: cliente já cadastrado, veículo novo

O sistema reaproveita o Customer e cadastra o Vehicle vinculado a ele.

### Cenário 4: abertura sem serviços

O atendente informa apenas o Customer, o Vehicle, a prioridade e a triagem inicial. O sistema abre a Service Order em
`RECEIVED`, sem Diagnosis e sem Estimate. Diagnóstico e orçamento seguem depois pelos endpoints já existentes.

### Cenário 5: falha em qualquer etapa

Qualquer rejeição (dado inválido, serviço arquivado, peça inexistente, veículo de outro cliente etc.) cancela a
abertura inteira: nenhum Customer, Vehicle, Service Order, Diagnosis ou Estimate fica gravado.

## Regras de negócio

### Atomicidade

- A abertura completa é tudo ou nada. Se qualquer etapa falhar, nenhum dado de nenhuma etapa permanece gravado.
- Efeitos externos, como notificações por e-mail, só ocorrem depois que a abertura inteira foi confirmada. Uma falha de
  notificação não desfaz a abertura, mantendo o comportamento atual das notificações.

### Identificação do Customer

- O Customer é identificado pelo CPF/CNPJ, com as mesmas regras de validação e normalização do cadastro atual
  (formatado ou só números).
- Se existir um Customer **ativo** com esse documento, ele é reaproveitado. Os demais dados enviados (nome, contato)
  **não** atualizam o cadastro existente; alterações cadastrais continuam nos endpoints de `/api/customers`.
- Se existir um Customer **arquivado** com esse documento, a abertura é rejeitada. A abertura não reativa nem duplica
  Customers arquivados.
- Se não existir Customer com esse documento, um novo é cadastrado com as mesmas regras e validações de
  `POST /api/customers`. Nesse caso, nome e contato passam a ser obrigatórios.

### Identificação do Vehicle

- O Vehicle é identificado pela placa, com as mesmas regras de validação e normalização do cadastro atual (padrão
  antigo ou Mercosul).
- Se existir um Vehicle **ativo** com essa placa **vinculado ao mesmo Customer**, ele é reaproveitado, sem alterar o
  cadastro.
- Se existir um Vehicle ativo com essa placa vinculado a **outro** Customer, a abertura é rejeitada como conflito. A
  transferência de propriedade fica fora de escopo.
- Se existir um Vehicle **arquivado** com essa placa, a abertura é rejeitada, mantendo a regra atual de que veículo
  arquivado não recebe trabalho novo.
- Se não existir Vehicle com essa placa, um novo é cadastrado para o Customer identificado, com as mesmas regras de
  `POST /api/vehicles`. Nesse caso, marca, modelo, ano e cor passam a ser obrigatórios; chassi e quilometragem
  continuam opcionais.

### Abertura da Service Order

- Valem todas as regras de `service-order-creation` e `service-order-initial-assessment`: status inicial `RECEIVED`,
  prioridade padrão `NORMAL` e `initialAssessment` obrigatório e não vazio.
- O `VehicleSnapshot` (placa, marca, modelo, ano) é copiado do cadastro do Vehicle identificado ou criado. O cliente
  da API não envia o snapshot.
- Os Technicians ativos continuam sendo notificados da nova Service Order, como na criação atual.

### Serviços e peças (opcionais)

- A lista de serviços é opcional. Se vier vazia ou ausente, a abertura termina com a Service Order em `RECEIVED`
  (cenário 4).
- Se houver ao menos um serviço, o Technician responsável pelo diagnóstico é obrigatório e deve existir. Ele é
  registrado tanto como responsável pelo diagnóstico quanto como autor do Diagnosis.
- Cada serviço referencia um Catalog Service **ativo**. Serviço inexistente ou arquivado rejeita a abertura, como no
  Diagnosis atual.
- Cada serviço pode trazer peças/insumos, cada um com um Stock Item existente e uma quantidade inteira positiva.
  Stock Item inexistente rejeita a abertura.
- **Nome e preço vêm do cadastro, não do cliente da API.** O nome e o preço do serviço são copiados do Service Catalog,
  e o nome, o tipo e o preço de cada peça são copiados do Stock Item, no momento da abertura. Isso difere do endpoint
  de Diagnosis atual, em que esses valores vêm no request, e impede que o balcão defina preços arbitrários.
- A disponibilidade de estoque é avaliada como no Diagnosis atual. Falta de peça **não** rejeita a abertura: fica
  registrada na Service Execution e pode gerar Purchase Demand, conforme as regras já aprovadas de Stock &
  Procurement.

### Geração automática do Estimate

- Quando há serviços, o Estimate é gerado automaticamente a partir do Diagnosis recém-registrado, com as mesmas regras
  de `estimate-generation` (snapshot comercial congelado, `lineTotal` e `total`, revalidação de disponibilidade).
- A Service Order termina em `AWAITING_APPROVAL`, e o Customer é notificado do Estimate gerado como no fluxo atual.
- A aprovação ou recusa do Estimate continua nos fluxos existentes (decisão interna e webhook externo).

### Permissões

- A abertura completa exige autenticação com papel `MANAGER` ou `ADMIN`, porque cadastra Customer e Vehicle e gera
  Estimate, ações que hoje já são restritas a esses papéis. `TECHNICIAN` não tem acesso a essa operação.

### Respostas e falhas

- Sucesso: a resposta traz a Service Order aberta (no mesmo formato da consulta detalhada atual), a identificação do
  Customer e do Vehicle utilizados, a indicação de que cada um foi reaproveitado ou criado e, quando houver, a
  identificação do Estimate gerado.
- Dados inválidos ou ausentes (inclusive CPF/CNPJ ou placa inválidos e campos obrigatórios de cadastro novo) são
  rejeitados como erro de validação.
- Technician, Catalog Service ou Stock Item inexistentes são rejeitados como não encontrados.
- Customer arquivado, Vehicle arquivado, Vehicle de outro Customer ou Catalog Service arquivado são rejeitados como
  conflito.
- Todas as falhas usam o formato de erro padrão (`ErrorResponse`) com código estável, sem expor detalhes internos.

## Fora de escopo

- Alterar o contrato ou o comportamento de `POST /api/service-orders` ou de qualquer endpoint existente.
- Atualizar dados de um Customer ou Vehicle já cadastrado durante a abertura.
- Reativar Customer ou Vehicle arquivado, ou transferir um Vehicle para outro Customer.
- Reservar estoque na abertura; a reserva continua acontecendo após a aprovação do Estimate.
- Aprovar ou recusar o Estimate na própria abertura.
- Preços negociados ou descontos informados no balcão.
- Validar especialidade ou disponibilidade do Technician (dívida técnica TD-002).
- Idempotência por chave de requisição: duas chamadas iguais abrem duas Service Orders, como hoje.

## Critérios de aceite

- [ ] Uma abertura com Customer e Vehicle novos e ao menos um serviço com peças cadastra o Customer e o Vehicle, cria a
  Service Order em `AWAITING_APPROVAL`, com uma Service Execution por serviço e um Estimate gerado, e devolve sucesso
  com os identificadores.
- [ ] Uma abertura com CPF/CNPJ e placa já cadastrados, ativos e vinculados entre si reaproveita os cadastros sem
  alterar nenhum dado deles, mesmo que o request traga nome, contato ou dados de veículo diferentes.
- [ ] Uma abertura com Customer existente e placa nova cadastra o Vehicle vinculado ao Customer existente.
- [ ] Uma abertura sem serviços cria a Service Order em `RECEIVED`, sem Diagnosis e sem Estimate.
- [ ] O `VehicleSnapshot` da Service Order criada é igual à placa, marca, modelo e ano do cadastro do Vehicle
  utilizado.
- [ ] O nome e o preço de cada Service Execution e de cada Stock Requirement são iguais aos do Service Catalog e do
  Stock Item no momento da abertura, independentemente de qualquer valor enviado no request.
- [ ] Uma placa ativa vinculada a outro Customer rejeita a abertura como conflito.
- [ ] Customer arquivado, Vehicle arquivado ou Catalog Service arquivado rejeitam a abertura como conflito.
- [ ] Technician, Catalog Service ou Stock Item inexistentes rejeitam a abertura como não encontrados.
- [ ] Serviços informados sem Technician responsável rejeitam a abertura como erro de validação.
- [ ] CPF/CNPJ inválido, placa inválida, quantidade de peça não positiva, `initialAssessment` vazio ou campos
  obrigatórios de cadastro novo ausentes rejeitam a abertura como erro de validação.
- [ ] Em qualquer rejeição, inclusive quando a falha acontece depois de Customer ou Vehicle terem sido cadastrados na
  mesma operação, nenhum Customer, Vehicle, Service Order, Diagnosis ou Estimate permanece gravado.
- [ ] Falta de estoque de uma peça não rejeita a abertura e fica registrada na Service Execution, como no Diagnosis
  atual.
- [ ] Um usuário `TECHNICIAN` ou não autenticado não consegue realizar a abertura completa.
- [ ] `POST /api/service-orders` e os demais endpoints existentes mantêm contrato e comportamento inalterados (testes
  existentes continuam verdes).
- [ ] O fluxo está documentado no OpenAPI, na coleção Postman e no README, com instruções de teste manual.

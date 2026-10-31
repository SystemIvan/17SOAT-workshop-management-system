# Especificação Funcional: Infraestrutura Kubernetes, IaC e entrega contínua

| Campo | Valor |
|---|---|
| Feature | `kubernetes-infrastructure-delivery` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-14 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-14 |
| Referências | Enunciado do Tech Challenge — Fase 2; `docs/adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md` |

Referências detalhadas:

- Tech Challenge — Fase 2, seções "Infraestrutura", "Integração Contínua/Entrega Contínua" e "Entregáveis";
- `docs/adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md` — local com kind primeiro, AWS Academy (EKS + RDS)
  depois;
- `docs/adr/ADR-001-escolha-do-banco-de-dados.md` — MySQL como banco da aplicação;
- `docs/adr/ADR-007-external-gateway-hmac-authentication.md` — segredo HMAC do gateway externo;
- `docs/features/platform/ci-coverage-quality-gate/` — quality gate de Pull Request já existente;
- `.github/workflows/ci.yml`, `Dockerfile`, `docker-compose.yml` e `src/main/resources/application.properties`.

## Problema e resultado esperado

A oficina expandiu para novas unidades e precisa de alta disponibilidade e capacidade de absorver picos de ordens de
serviço. Hoje a aplicação só roda via `docker-compose` em uma única máquina: não existe orquestração que recrie a
aplicação quando ela falha, nem escalabilidade automática, nem infraestrutura provisionada por código. A pipeline
existente apenas compila e testa; nenhuma etapa publica a imagem ou faz deploy. Também não há forma de a plataforma
saber se a aplicação está viva e pronta para atender.

O resultado esperado é que a mesma aplicação, empacotada em uma única imagem, possa ser:

1. executada localmente para desenvolvimento com um único comando;
2. implantada em um cluster Kubernetes local cujo cluster e banco de dados são criados e destruídos por código;
3. escalada automaticamente conforme o consumo de CPU e memória;
4. implantada automaticamente por uma pipeline a cada push na branch de trabalho, com verificação final de que a API
   responde;
5. levada, com a mesma imagem e os mesmos manifestos base, a um ambiente AWS (EKS + RDS) provisionado por código.

Tudo isso deve estar documentado de forma que um avaliador consiga reproduzir os passos e que a demonstração em vídeo
mostre deploy, execução da pipeline, consumo das APIs e escalabilidade automática.

## Atores e cenários

- **Pessoa desenvolvedora:** sobe o ambiente local, provisiona e destrói a infraestrutura, aplica os manifestos e
  acompanha a pipeline.
- **Cliente da API (Postman/Swagger):** consome as APIs da oficina no ambiente local ou na AWS sem saber quantas
  cópias da aplicação estão rodando.
- **Plataforma (Kubernetes):** consulta a saúde da aplicação para decidir se a reinicia e se envia tráfego a ela.
- **Pipeline (GitHub Actions no fork pessoal):** valida, empacota, publica e implanta a aplicação a cada push na branch
  configurada.
- **Equipe avaliadora:** lê o README, reproduz os passos e assiste ao vídeo.
- **Grupo:** compara as implementações individuais e escolhe a que será entregue.

Cenários ponta a ponta:

1. **Desenvolvimento local com containers.** A pessoa desenvolvedora executa um único comando e obtém aplicação, banco,
   SMTP de teste e simulador de fornecedor rodando. A aplicação lê banco, SMTP e segredos exclusivamente por
   variáveis de ambiente.
2. **Saúde da aplicação.** A plataforma consulta se a aplicação está viva (o processo responde) e se está pronta (consegue
   atender, incluindo acesso ao banco). Enquanto não estiver pronta, não recebe requisições; se deixar de estar viva,
   é reiniciada.
3. **Provisionamento local.** Um comando de IaC cria o cluster local, o componente de métricas e o banco de dados MySQL.
   Outro comando destrói tudo o que foi criado.
4. **Deploy local.** A aplicação é implantada no cluster com configuração e segredos injetados pelo cluster, aplica as
   migrations no banco recém-criado e fica acessível ao Postman a partir da máquina da pessoa desenvolvedora.
5. **Escalabilidade automática.** Sob carga gerada propositalmente, o número de cópias da aplicação aumenta até um
   limite máximo; quando a carga cessa, volta gradualmente ao mínimo. O cliente da API continua usando o mesmo
   endereço o tempo todo.
6. **Resiliência.** Se uma cópia da aplicação é encerrada, outra é criada automaticamente. Se o banco é reiniciado no
   ambiente local, os dados gravados continuam existindo.
7. **Atualização sem queda.** Ao implantar uma nova versão que não fica pronta (por exemplo, sem acesso ao banco), a
   versão anterior continua atendendo.
8. **Pipeline.** Um push na branch configurada dispara, em ordem: build, testes automatizados, build da imagem, publicação
   da imagem no registry com tag pelo SHA do commit, deploy do banco, aplicação dos manifestos em um cluster criado no
   próprio job e um smoke test. Falha em qualquer etapa interrompe as seguintes e torna a execução reprovada.
9. **Ambiente AWS.** Com credenciais válidas da sessão do Learner Lab, um comando de IaC cria a rede, o cluster EKS e o
   banco RDS MySQL; a mesma imagem é implantada; a API fica acessível por um endereço público; a escalabilidade
   automática funciona como no local. Ao fim do uso, um comando destrói todos os recursos criados.
10. **Credenciais expiradas.** Quando a sessão do Learner Lab expira, a pipeline e os comandos de IaC da AWS falham com
    erro de autenticação, sem efeitos parciais silenciosos; a pessoa desenvolvedora renova as credenciais seguindo o
    procedimento documentado.

## Regras de negócio

### Configuração e segredos

- A aplicação obtém toda configuração dependente de ambiente (banco, SMTP, URL do fornecedor, segredos JWT e HMAC,
  chave do fornecedor) por variáveis de ambiente. Nenhum ambiente exige alterar código ou recompilar a imagem.
- Os nomes das variáveis de ambiente formam um contrato único, igual no `docker-compose` e no Kubernetes.
- Nenhum segredo real é versionado no repositório: nem em manifestos, nem em arquivos de IaC, nem no workflow.
  Valores de exemplo só são permitidos quando claramente identificados como desenvolvimento local.
- No Kubernetes, valores não sensíveis vêm de ConfigMap e valores sensíveis vêm de Secret.
- Na pipeline, segredos e credenciais vêm de secrets de um environment do fork pessoal, nunca de secrets de nível de
  repositório nem do repositório compartilhado.

### Imagem

- Existe uma única imagem da aplicação para todos os ambientes; o que muda entre ambientes é só a configuração.
- Toda imagem publicada pela pipeline recebe tag igual ao SHA do commit que a gerou. O deploy referencia essa tag, nunca
  `latest`.
- A imagem roda com usuário sem privilégio de administrador e não contém segredos.
- A aplicação respeita os limites de memória do container em que roda.

### Saúde

- A verificação de "viva" não depende do banco de dados, para que uma indisponibilidade do banco não provoque
  reinícios em cascata da aplicação.
- A verificação de "pronta" inclui o acesso ao banco de dados.
- As verificações de saúde não exigem autenticação, mas não expõem detalhes internos (versões, configuração, nomes de
  hosts, credenciais ou stack traces).

### Escalabilidade

- O número de cópias da aplicação varia automaticamente entre um mínimo e um máximo definidos por ambiente, com base
  no consumo de CPU e de memória.
- Toda cópia da aplicação declara quanto de CPU e memória reserva e qual o limite que pode usar.

### Banco de dados

- O banco é provisionado pela IaC nos dois ambientes: MySQL dentro do cluster no local; RDS MySQL na AWS.
- O schema continua sendo criado e evoluído exclusivamente pelas migrations Flyway da aplicação; a validação de schema
  pelo Hibernate continua em `validate`.
- No ambiente local, os dados do banco sobrevivem ao reinício do container do banco.
- Dados de demonstração só são inseridos quando explicitamente habilitados para aquele ambiente, conforme as regras de
  seed do `AGENTS.md`.

### Pipeline

- A pipeline de entrega dispara apenas por push na(s) branch(es) configurada(s). O nome da branch aparece em um único
  ponto do workflow, e todos os pontos onde ele aparece ficam documentados para facilitar a troca.
- As etapas seguem a ordem do cenário 8; uma etapa só roda se as anteriores tiverem passado.
- O quality gate de Pull Request existente (`ci.yml`) mantém seu comportamento e seu nome de status check.
- O smoke test reprova a execução se a aplicação implantada não ficar pronta ou se uma chamada básica à API não
  retornar o resultado esperado.
- A pipeline não publica imagens nem altera recursos dos outros integrantes.

### Infraestrutura como código

- Criar e destruir cada ambiente é feito por comandos de IaC documentados, sem passos manuais no console, exceto obter
  as credenciais da sessão do Learner Lab.
- O state da IaC nunca é versionado. No ambiente local ele fica na máquina; na AWS fica em armazenamento remoto (S3).
- Na AWS, a IaC usa somente a `LabRole` fornecida pelo Learner Lab e não cria roles de IAM.
- Antes de qualquer criação de recursos na AWS, é apresentada a estimativa de consumo de crédito por hora.
- Ao fim de cada uso da AWS, todos os recursos que geram custo são destruídos.

### Contratos HTTP

- Nenhum endpoint de negócio existente muda de contrato. Os únicos endpoints novos são os de saúde, fora do prefixo
  `/api`.

## Fora de escopo

- A abertura de OS recebendo dados de cliente, veículo, serviços e peças em uma única chamada, que é tratada em feature
  própria do bounded context `servicelifecycle`.
- HTTPS/TLS, domínio próprio e certificados.
- Observabilidade além das verificações de saúde: métricas de negócio, dashboards, logs centralizados e alertas.
- Alta disponibilidade multi-AZ do banco, backups automatizados, réplicas de leitura e recuperação de desastre.
- Escalonamento automático da quantidade de máquinas do cluster (apenas as cópias da aplicação escalam).
- Gerenciadores externos de segredos (AWS Secrets Manager, Vault) e rotação automática de segredos.
- Serviços reais de e-mail (como Amazon SES) e fornecedor real: os dois ambientes usam SMTP de teste e simulador de
  fornecedor.
- Cluster permanente para a pipeline: o cluster usado no job é criado e descartado em cada execução.
- Deploy a partir de `main`, de `dev` ou de branches de outros integrantes, e qualquer mudança no repositório
  compartilhado (environments, secrets, proteção de branch).
- Varredura de vulnerabilidades da imagem.

## Critérios de aceite

### Containers

- [ ] Um único comando sobe aplicação, banco, SMTP de teste e simulador de fornecedor localmente, e a API responde.
- [ ] A aplicação sobe com banco, SMTP e segredos definidos apenas por variáveis de ambiente, sem alterar código.
- [ ] Os nomes das variáveis de ambiente são os mesmos no `docker-compose` e no Kubernetes.
- [ ] A imagem roda com usuário sem privilégio de administrador e não contém segredos.

### Saúde

- [ ] Existe uma verificação de "viva" que responde com sucesso mesmo com o banco indisponível.
- [ ] Existe uma verificação de "pronta" que falha quando o banco está indisponível e passa quando ele volta.
- [ ] As duas verificações respondem sem autenticação e sem expor detalhes internos.

### Provisionamento e deploy local

- [ ] Um comando de IaC cria o cluster local, o componente de métricas e o MySQL; outro comando destrói tudo.
- [ ] Após aplicar os manifestos, a aplicação fica pronta, aplica as migrations e responde ao Postman a partir da
      máquina local.
- [ ] Configuração não sensível vem de ConfigMap e segredos vêm de Secret.
- [ ] Encerrar manualmente uma cópia da aplicação faz o cluster criar outra automaticamente.
- [ ] Reiniciar o banco local não apaga os dados gravados.
- [ ] Uma nova versão que não fica pronta não substitui a versão que está atendendo.

### Escalabilidade

- [ ] Sob carga, o número de cópias da aplicação aumenta acima do mínimo sem ultrapassar o máximo.
- [ ] Sem carga, o número de cópias volta ao mínimo.
- [ ] Durante a escala, o cliente da API continua usando o mesmo endereço.

### Pipeline

- [ ] Push na branch configurada dispara a pipeline; push em outras branches não dispara a entrega.
- [ ] A pipeline executa, nesta ordem: build, testes, build da imagem, publicação com tag pelo SHA, deploy do banco,
      aplicação dos manifestos e smoke test.
- [ ] Falha em testes interrompe a pipeline antes de publicar a imagem.
- [ ] O smoke test reprova a execução quando a aplicação não fica pronta.
- [ ] Segredos usados pela pipeline vêm do environment do fork pessoal, e nenhum segredo aparece nos logs.
- [ ] O nome da branch de disparo aparece em um único ponto do workflow, e sua localização está documentada.
- [ ] O quality gate de Pull Request existente continua com o mesmo comportamento.

### AWS

- [ ] As restrições do Learner Lab foram confirmadas antes de escrever a IaC da AWS.
- [ ] A estimativa de consumo de crédito por hora foi apresentada antes do primeiro `apply`.
- [ ] Um comando de IaC cria rede, EKS (usando a `LabRole`) e RDS MySQL, com state remoto em S3.
- [ ] A mesma imagem usada no local é implantada na AWS e a API responde por endereço público.
- [ ] A escalabilidade automática funciona na AWS como no local.
- [ ] Um comando destrói todos os recursos que geram custo.
- [ ] Credenciais expiradas fazem a pipeline falhar com erro de autenticação.
- [ ] O procedimento de renovação de credenciais por sessão está documentado.

### Documentação

- [ ] O README descreve a solução e os objetivos da fase, o desenho da arquitetura (componentes, infraestrutura e
      fluxo de deploy) e as instruções de execução local, deploy em Kubernetes e provisionamento com Terraform.
- [ ] O README lista os recursos criados pela IaC em cada ambiente.
- [ ] O README aponta para a collection das APIs e para o vídeo de demonstração.

## Pontos a decidir na technical-spec

- Mínimo e máximo de cópias e limites de CPU/memória por ambiente.
- Forma de acesso à API no cluster local (porta exposta pelo cluster ou encaminhamento de porta).
- Como o MySQL local é provisionado pela IaC e como o deploy do banco aparece na pipeline em cada ambiente.
- Se o ambiente AWS habilita dados de demonstração.
- Ferramenta do teste de carga.

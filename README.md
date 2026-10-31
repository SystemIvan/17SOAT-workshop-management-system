# Workshop Management System

API REST para a gestão de uma oficina mecânica: cadastro de clientes e veículos, abertura e acompanhamento de ordens
de serviço, orçamento com aprovação pelo cliente, execução, estoque e compras. É um monólito modular em Java 21 e
Spring Boot 4, com MySQL, empacotado em uma única imagem que roda no Docker Compose, em Kubernetes local (kind) e na
AWS (EKS + RDS).

Projeto do Tech Challenge FIAP (pós-graduação em Software Architecture, turma 17SOAT).

## Objetivos da fase 2

A oficina expandiu para novas unidades e precisa de alta disponibilidade e capacidade para absorver picos de ordens de
serviço. A fase 2 leva a mesma aplicação a uma plataforma orquestrada:

- **APIs do fluxo principal:** abertura completa da OS, consulta de status, aprovação do orçamento por webhook
  assinado, listagem ordenada e notificações por e-mail;
- **Containers e saúde:** imagem única, configuração por variáveis de ambiente, probes de liveness e readiness;
- **Kubernetes:** Deployment, Service, ConfigMap, Secret e HPA, com escala automática sob carga;
- **Infraestrutura como código:** cluster, componente de métricas e banco MySQL criados e destruídos por Terraform,
  no kind (local) e na AWS (VPC, EKS e RDS);
- **CI/CD:** a cada push, build, testes, imagem publicada no GHCR e deploy num cluster efêmero com smoke test; sob
  demanda, deploy do banco e da aplicação no EKS com smoke test pelo load balancer.

## Visão geral

```mermaid
flowchart LR
    code["Código<br/>(GitHub)"] -->|"push"| cd["Pipeline<br/>GitHub Actions"]
    cd -->|"build + testes<br/>docker build"| ghcr[("Imagem<br/>GHCR :sha")]
    tf["Terraform"]
    client(["Postman / k6"])
    subgraph aws["AWS Academy"]
        eks["EKS"] --> rds[("RDS MySQL 8.4")]
    end
    subgraph local["Local e pipeline"]
        kind["Cluster kind"] --> db[("MySQL 8.4<br/>StatefulSet")]
    end
    ghcr -->|"pull :sha"| eks
    ghcr -->|"pull :sha"| kind
    tf -.->|"cria rede, cluster e banco"| aws
    tf -.->|"cria cluster e banco"| local
    client -->|"HTTP via load balancer"| eks
    client -->|"HTTP"| kind
```

A mesma imagem, com tag pelo SHA do commit, roda no kind (local e na pipeline) e no EKS; muda só a configuração. No
PC, a imagem é construída localmente e carregada no kind. Módulos, containers e a infraestrutura provisionada na AWS
(VPC, subnets, NAT, load balancer, EKS e RDS) em detalhe: [visão geral da arquitetura](docs/architecture/overview.md).

## Início rápido

Escolha como deseja testar antes de executar os comandos:

| Objetivo | Caminho |
| --- | --- |
| Testar as APIs e o fluxo de negócio com menos recursos | [Opção A: Docker Compose](#opção-a-docker-compose) |
| Testar a implantação Kubernetes, probes, rolling update e escala automática (HPA) | [Opção B: Kubernetes local](#opção-b-kubernetes-local-kind--terraform) |

Cada opção sobe seu próprio ambiente completo. Para ir direto ao Kubernetes, siga a opção B desde a preparação
das dependências. Execute um ambiente por vez: ambos usam as portas 8080 e 8025.

### Opção A: Docker Compose

Pré-requisito: Docker com Docker Compose.

```bash
docker compose up -d --build                              # API, MySQL, Mailpit e simulador do fornecedor
curl http://localhost:8080/actuator/health/readiness      # {"status":"UP"} quando estiver pronta (~3 min)
```

Depois que a readiness responder `{"status":"UP"}`, abra o Swagger em `http://localhost:8080/swagger-ui.html`,
faça login em `POST /api/auth/login` com a conta de bootstrap `admin` / `changeme123` e use o token no botão
**Authorize**. Os e-mails enviados aparecem no Mailpit, em `http://localhost:8025`.

> A conta `admin` é criada por migration em todos os ambientes. Troque a senha fora de um ambiente de demonstração.

Detalhes: [execução local com Docker Compose](docs/setup/local-docker-compose.md).

### Opção B: Kubernetes local (kind + Terraform)

Siga o [guia completo de Kubernetes local](docs/setup/local-kubernetes.md), desde a preparação das dependências.
Todas as etapas estão lá, em ordem: conferir ferramentas e recursos, criar a infraestrutura, implantar a aplicação,
testar a API e encerrar o ambiente. O guia também inclui comandos para Ubuntu/WSL e solução de problemas.

## Documentação

| Quero... | Leia |
| --- | --- |
| Assistir à demonstração da fase 2 (deploy, CI/CD, APIs e HPA) | [Vídeo no YouTube](https://www.youtube.com/watch?v=-1JPvTIRDf8) |
| Entender a arquitetura (contexto, módulos, implantação, ADRs) | [Visão geral da arquitetura](docs/architecture/overview.md) |
| Rodar localmente com Docker Compose | [Execução local com Docker Compose](docs/setup/local-docker-compose.md) |
| Rodar no Kubernetes local com Terraform | [Ambiente Kubernetes local](docs/setup/local-kubernetes.md) |
| Provisionar e implantar na AWS (EKS + RDS) com Terraform | [Ambiente AWS Academy](docs/setup/aws-academy.md) |
| Ver o HPA escalar sob carga | [Teste de carga e HPA](docs/operations/load-testing-hpa.md) |
| Configurar variáveis de ambiente e segredos | [Configuração](docs/setup/configuration.md) |
| Entender a pipeline e os workflows | [CI/CD](docs/operations/ci-cd.md) |
| Importar a collection completa das APIs | [Collection Postman](docs/api/postman/workshop-management-system.postman_collection.json) (o contrato OpenAPI fica no Swagger, em `/swagger-ui.html`) |
| Consumir a API e testar o fluxo completo | [API](docs/api/README.md) e [teste manual pelo Postman](docs/api/manual-testing.md) |
| Contribuir com código | [CONTRIBUTING.md](CONTRIBUTING.md) e [AGENTS.md](AGENTS.md) |
| Ver tudo, organizado por público | [Índice da documentação](docs/README.md) |

## Stack

| Camada | Tecnologia |
| --- | --- |
| Aplicação | Java 21, Spring Boot 4.1, Spring Modulith 2.1, Spring Data JPA, Spring Security (JWT e HMAC) |
| Banco | MySQL 8.4, migrations com Flyway |
| Qualidade | JUnit, MockMvc, JaCoCo (mínimo de 80%), Postman/Newman, OWASP Dependency-Check e ZAP |
| Containers | Docker (build multi-stage, usuário não-root), Docker Compose |
| Kubernetes | kind (local), Amazon EKS (AWS), Kustomize, HPA, metrics-server |
| Infraestrutura | Terraform (providers kind, Helm, Kubernetes e AWS), state remoto em S3 na AWS |
| Nuvem | AWS Academy: VPC, NAT Gateway, Load Balancer, EKS, RDS MySQL |
| CI/CD | GitHub Actions, GitHub Container Registry (GHCR) |

## Estrutura do repositório

| Caminho | Conteúdo |
| --- | --- |
| `src/` | Código e testes da aplicação |
| `k8s/` | Manifestos Kubernetes: `base/` e `overlays/` por ambiente (Kustomize) |
| `infra/` | Terraform, uma pasta por stack (`local/cluster`, `local/database`, `aws/cluster`, `aws/database`) |
| `load-test/` | Script k6 do teste de carga |
| `docker/` | Arquivos de apoio dos containers (mapeamentos do simulador do fornecedor) |
| `docs/` | Documentação, ADRs, especificações de features e collection Postman |
| `.github/workflows/` | CI (`ci.yml`), entrega no kind (`cd.yml`), entrega na AWS (`cd-aws.yml`) e scan de segurança |

## Status da fase 2

- [x] APIs do fluxo principal e testes dos fluxos críticos
- [x] Dockerfile, Docker Compose e health checks
- [x] Terraform local: cluster kind, metrics-server e MySQL
- [x] Manifestos Kubernetes com HPA e teste de carga
- [x] Pipeline de entrega com cluster efêmero e smoke test
- [x] Ambiente AWS Academy (EKS + RDS), com pipeline de deploy manual
- [x] [Vídeo de demonstração](https://www.youtube.com/watch?v=-1JPvTIRDf8): deploy, CI/CD, APIs e escala na AWS
- [ ] PDF de entrega

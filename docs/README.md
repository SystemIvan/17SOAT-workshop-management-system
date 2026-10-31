# Documentação

Índice de toda a documentação do projeto, organizado por quem está lendo. Cada documento responde a uma pergunta; se
duas páginas parecerem dizer a mesma coisa, a mais específica é a fonte da verdade e a outra deve apenas apontar para
ela.

## Para avaliar o projeto

| Documento | Responde |
| --- | --- |
| [Visão geral da arquitetura](architecture/overview.md) | Como o sistema é dividido e onde ele roda (C4 + implantação no kind e na AWS) |
| [Decisões arquiteturais (ADRs)](adr/) | Por que cada escolha estrutural foi feita |
| [Architecture.md](Architecture.md) | Requisitos oficiais, domínio (DDD), aggregates e linguagem ubíqua |
| [Relatório de vulnerabilidades](security/vulnerability-report.md) | Achados dos scans de segurança e o tratamento de cada um |

## Para rodar o sistema

| Documento | Tipo | Responde |
| --- | --- | --- |
| [Execução local com Docker Compose](setup/local-docker-compose.md) | Tutorial | Como subir a API no dia a dia |
| [Ambiente Kubernetes local](setup/local-kubernetes.md) | Tutorial | Como criar o cluster e o banco com Terraform e implantar a aplicação |
| [Ambiente AWS Academy](setup/aws-academy.md) | Tutorial | Como criar rede, EKS e RDS com Terraform, implantar, usar e destruir sem deixar custo |
| [Configuração](setup/configuration.md) | Referência | Quais variáveis de ambiente existem e de onde vem cada valor |
| [Verificação de saúde](operations/health-checks.md) | Explicação | O que liveness e readiness significam e como testá-las |
| [Teste de carga e HPA](operations/load-testing-hpa.md) | How-to | Como gerar carga e observar a escala automática |
| [CI/CD](operations/ci-cd.md) | Referência | O que cada workflow faz (inclusive a entrega na AWS) e como configurar o fork |

## Para consumir a API

| Documento | Responde |
| --- | --- |
| [API](api/README.md) | Autenticação (JWT e HMAC) e recursos disponíveis |
| [Teste manual pelo Postman](api/manual-testing.md) | Como executar o fluxo completo, do cadastro à entrega |
| [Collection Postman](api/postman/workshop-management-system.postman_collection.json) | As requisições, com asserções, para importar no Postman ou rodar no Newman |
| Swagger UI (`/swagger-ui.html`) e OpenAPI (`/v3/api-docs`) | O contrato de cada endpoint, gerado pela aplicação (fonte da verdade) |

## Para contribuir

| Documento | Responde |
| --- | --- |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Comandos de desenvolvimento e quality gates |
| [AGENTS.md](../AGENTS.md) | Regras do projeto: arquitetura, SDD, testes, segurança, Git |
| [Especificações de features](features/README.md) | O fluxo SDD e as specs de cada feature, por bounded context |
| [PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md) | Pacotes e camadas de cada módulo |
| [Architecture-Decisions.md](Architecture-Decisions.md) | Decisões de time em aberto e resolvidas (AD-xxx) |
| [RFCs](rfc/) | Propostas de desenho discutidas antes da implementação |
| [Dívida técnica](tech-debt/) | Pendências conhecidas e aceitas, com o motivo |
| [Backlog](backlog.md) | Ideias deixadas fora do MVP |
| [Glossário](learning/glossario.md) | Conceitos de containers, Kubernetes, Terraform e CI/CD usados no projeto |

## Como manter esta documentação

- **Um documento, uma pergunta.** Conteúdo novo entra no documento cuja pergunta ele responde; se nenhum servir, crie
  um arquivo novo e adicione uma linha aqui.
- **Apontar em vez de copiar.** O contrato da API é o OpenAPI gerado; variáveis de ambiente vivem em
  [Configuração](setup/configuration.md); decisões vivem nas ADRs.
- **Comandos testados.** Todo comando de um guia deve ter sido executado; registre a saída esperada e como verificar.
- **Português** para o texto, com nomes de código, arquivos, endpoints e status na forma original.

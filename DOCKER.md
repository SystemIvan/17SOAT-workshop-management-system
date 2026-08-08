# 🐳 Workshop Management System - Docker Setup

Guia completo para rodar a aplicação usando Docker Compose.

---

## 📋 Pré-requisitos

- Docker (versão 20.10+)
- Docker Compose (versão 2.0+)
- Git

Verifique:
```bash
docker --version
docker-compose --version
```

---

## 🚀 Quick Start (2 minutos)

### 1. Clone o Repositório
```bash
git clone <repo-url>
cd workshop-management-system
```

### 2. Configure Variáveis de Ambiente
```bash
# Copie o arquivo de exemplo
cp .env.example .env

# Edite se necessário (padrões já estão preenchidos)
# DB_PASSWORD=workshop_pass
# APP_PORT=8080
# etc
```

### 3. Inicie os Containers
```bash
docker-compose up -d
```

### 4. Aguarde o Health Check
```bash
# Verificar status
docker-compose ps

# Ver logs de inicialização
docker-compose logs -f app
```

### 5. Teste a Aplicação
```bash
# Health check
curl http://localhost:8080/actuator/health

# Listar Service Orders
curl http://localhost:8080/api/service-orders
```

---

## 📊 Estrutura do Docker Compose

```yaml
┌─────────────────────────────────────┐
│   workshop-app (Spring Boot)        │
│   - Porta 8080                      │
│   - Java 21                         │
│   - Spring Boot 4.1                 │
└─────────────┬───────────────────────┘
              │ (jdbc:mysql://mysql:3306)
┌─────────────▼───────────────────────┐
│   workshop-mysql (MySQL 8.0)        │
│   - Porta 3306                      │
│   - Database: workshop              │
│   - Volume: mysql_data (persistente)│
└─────────────────────────────────────┘
```

---

## 🛠️ Usando Makefile (Recomendado)

```bash
# Ver todos os comandos disponíveis
make help

# Iniciar containers
make docker-up

# Ver logs
make docker-logs

# Parar containers
make docker-down

# Limpar tudo (volumes inclusos)
make docker-clean

# Rebuild completo
make rebuild
```

---

## 📝 Comandos Principais

### Iniciar Aplicação
```bash
docker-compose up -d
```

### Ver Status
```bash
docker-compose ps
```

### Ver Logs (Tempo Real)
```bash
docker-compose logs -f app
```

### Parar Aplicação
```bash
docker-compose down
```

### Parar e Remover Volumes (Reset Completo)
```bash
docker-compose down -v
```

### Acessar MySQL Shell
```bash
docker-compose exec mysql mysql -uworkshop_user -pworkshop_pass workshop
```

### Rodar Comando na App
```bash
docker-compose exec app curl http://localhost:8080/api/service-orders
```

---

## 🔧 Configuração

### Variáveis de Ambiente (.env)

```bash
# Database
DB_ROOT_PASSWORD=root                   # Senha do root MySQL
DB_NAME=workshop                        # Nome do banco
DB_USERNAME=workshop_user               # Usuário DB
DB_PASSWORD=workshop_pass               # Senha DB
DB_PORT=3306                           # Porta MySQL (host)

# Application
APP_PORT=8080                          # Porta Spring Boot
DDL_AUTO=update                        # JPA DDL strategy (create-drop/update/validate)
SHOW_SQL=false                         # Log SQL queries

# Java
JAVA_OPTS=-Xms512m -Xmx1024m          # Memory JVM
```

### Alterar Configuração em Runtime

Edite `.env` e reinicie:
```bash
# Altere .env
nano .env

# Reinicie
docker-compose restart app
```

---

## 📊 Verificar Status da Aplicação

### Health Check
```bash
curl http://localhost:8080/actuator/health

# Resposta esperada:
# {"status":"UP"}
```

### Listar Service Orders
```bash
curl http://localhost:8080/api/service-orders
```

### Listar Logs Detalhados
```bash
docker-compose logs app
```

---

## 🐛 Troubleshooting

### Erro: "Port 8080 is already in use"
```bash
# Mude a porta no .env
APP_PORT=8081

# Ou finalize o processo usando a porta
lsof -i :8080
kill -9 <PID>
```

### Erro: "MySQL connection refused"
```bash
# Aguarde o health check do MySQL
docker-compose logs mysql

# Se não iniciar, aumente timeout
docker-compose up -d --wait
```

### Erro: "Database doesn't exist"
```bash
# Reinicie com ddl-auto=create
DDL_AUTO=create docker-compose up -d app

# Ou recrie os volumes
docker-compose down -v && docker-compose up -d
```

### Ver Logs Detalhados
```bash
# Todos os containers
docker-compose logs

# Apenas app
docker-compose logs app

# Apenas MySQL
docker-compose logs mysql

# Últimas 50 linhas
docker-compose logs --tail=50
```

### Acessar Shell da App
```bash
docker-compose exec app /bin/sh

# Dentro do container:
java -version
ls -la
```

---

## 🔐 Segurança em Produção

### Mudar Senhas Padrão (.env)

```bash
DB_ROOT_PASSWORD=seu_password_super_secreto
DB_PASSWORD=outro_password_aleatorio
```

### Usar Secrets Docker (Produção)
```yaml
services:
  mysql:
    environment:
      MYSQL_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
```

### Desabilitar Acesso Direto ao MySQL
```yaml
# Remover porta exposta do MySQL
mysql:
  ports:
    # - "3306:3306"  ← Comentar em produção
```

---

## 📦 Build Manual da Imagem

```bash
# Build
docker build -t workshop-management-system:1.0.0 .

# Run sem Compose
docker run -d \
  --name workshop-app \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/workshop \
  -e SPRING_DATASOURCE_USERNAME=workshop_user \
  -e SPRING_DATASOURCE_PASSWORD=workshop_pass \
  workshop-management-system:1.0.0
```

---

## 📈 Monitoramento

### Métricas da Aplicação
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Logs com Filtro
```bash
# Apenas erros
docker-compose logs --tail=100 app | grep ERROR

# Apenas requests HTTP
docker-compose logs --tail=100 app | grep REST
```

---

## 🚀 Deploy em Produção (Avançado)

### AWS ECS / ECR

```bash
# Tag da imagem
docker tag workshop-management-system:latest \
  <AWS_ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/workshop:latest

# Push
docker push <AWS_ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/workshop:latest
```

### Docker Swarm

```bash
docker swarm init
docker stack deploy -c docker-compose.yml workshop
```

---

## 📚 Referências

- [Docker Official Docs](https://docs.docker.com/)
- [Docker Compose Docs](https://docs.docker.com/compose/)
- [Spring Boot Docker Docs](https://spring.io/guides/gs/spring-boot-docker/)
- [MySQL Docker Image](https://hub.docker.com/_/mysql)

---

## ❓ Dúvidas?

Verifique os logs:
```bash
docker-compose logs -f
```

Ou recrie do zero:
```bash
docker-compose down -v
docker-compose up -d
```

---

**Last Updated:** Agosto 2026  
**Version:** 1.0.0  
**Status:** ✅ Production Ready

.PHONY: help build up down logs test clean docker-build docker-up docker-down docker-clean dev

help:
	@echo "Workshop Management System - Makefile Commands"
	@echo ""
	@echo "Development:"
	@echo "  make dev              - Rodar aplicação localmente (requer MySQL)"
	@echo "  make test             - Rodar testes unitários"
	@echo "  make compile          - Compilar o projeto"
	@echo "  make clean            - Limpar arquivos de build"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-build     - Build da imagem Docker"
	@echo "  make docker-up        - Iniciar containers (MySQL + App)"
	@echo "  make docker-down      - Parar containers"
	@echo "  make docker-clean     - Remove containers e volumes"
	@echo "  make docker-logs      - Ver logs da aplicação"
	@echo ""
	@echo "Database:"
	@echo "  make db-init          - Inicializar banco de dados"
	@echo "  make db-shell         - Acessar shell do MySQL"
	@echo ""

# ============ Development Commands ============

dev:
	@echo "🚀 Starting application locally..."
	mvn spring-boot:run

test:
	@echo "🧪 Running tests..."
	mvn clean test

compile:
	@echo "🔨 Compiling project..."
	mvn clean compile

clean:
	@echo "🧹 Cleaning project..."
	mvn clean

# ============ Docker Commands ============

docker-build:
	@echo "🐳 Building Docker image..."
	docker build -t workshop-management-system:latest .
	@echo "✅ Image built successfully!"

docker-up:
	@echo "🚀 Starting Docker containers..."
	@if [ ! -f .env ]; then \
		echo "⚠️  .env not found. Creating from .env.example..."; \
		cp .env.example .env; \
	fi
	docker-compose up -d
	@echo "✅ Containers started!"
	@echo "📍 App: http://localhost:8080"
	@echo "📍 MySQL: localhost:3306"

docker-down:
	@echo "⬇️  Stopping Docker containers..."
	docker-compose down
	@echo "✅ Containers stopped!"

docker-clean:
	@echo "🧹 Removing Docker containers and volumes..."
	docker-compose down -v
	@echo "✅ Cleaned!"

docker-logs:
	@echo "📋 Showing application logs..."
	docker-compose logs -f app

docker-ps:
	@echo "📊 Docker containers status:"
	docker-compose ps

# ============ Database Commands ============

db-init:
	@echo "🗄️  Initializing database..."
	docker-compose exec mysql mysql -uworkshop_user -pworkshop_pass workshop < scripts/init-db.sql
	@echo "✅ Database initialized!"

db-shell:
	@echo "💻 Accessing MySQL shell..."
	docker-compose exec mysql mysql -uworkshop_user -pworkshop_pass workshop

# ============ Useful Commands ============

status:
	@echo "📊 System Status:"
	@docker-compose ps
	@echo ""
	@echo "📝 Recent logs:"
	@docker-compose logs --tail=10 app

restart:
	@echo "🔄 Restarting containers..."
	docker-compose restart

rebuild: docker-clean docker-build docker-up
	@echo "✅ Rebuild complete!"

# ============ CI/CD Style Commands ============

ci-test:
	@echo "🔍 Running CI tests..."
	mvn clean verify

ci-build: ci-test docker-build
	@echo "✅ CI build successful!"

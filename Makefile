MVNW := ./mvnw
COMPOSE := docker compose

.PHONY: help test coverage verify compile build clean run run-dev \
	docker-build docker-up docker-down docker-reset docker-logs docker-ps db-shell

help:
	@echo "Workshop Management System"
	@echo ""
	@echo "Development:"
	@echo "  make test          Run the automated tests"
	@echo "  make coverage      Run verification and generate target/site/jacoco/index.html"
	@echo "  make verify        Run the complete local quality gate"
	@echo "  make compile       Compile the application"
	@echo "  make build         Build the application JAR"
	@echo "  make run           Run without demonstration seeds"
	@echo "  make run-dev       Run with the dev profile and idempotent demo seeds"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-up     Start MySQL and the application in dev mode"
	@echo "  make docker-down   Stop containers without deleting data"
	@echo "  make docker-reset  Stop containers and DELETE the local MySQL volume"
	@echo "  make docker-logs   Follow application logs"
	@echo "  make db-shell      Open the MySQL client"

test:
	$(MVNW) test

coverage:
	$(MVNW) verify

verify:
	$(MVNW) verify

compile:
	$(MVNW) compile

build:
	$(MVNW) package

clean:
	$(MVNW) clean

run:
	$(MVNW) spring-boot:run

run-dev:
	SPRING_PROFILES_ACTIVE=dev APP_SEED_ENABLED=true $(MVNW) spring-boot:run

docker-build:
	$(COMPOSE) build app

docker-up:
	@if [ ! -f .env ]; then cp .env.example .env; fi
	$(COMPOSE) up -d --build

docker-down:
	$(COMPOSE) down

docker-reset:
	@echo "WARNING: deleting containers and the local MySQL volume"
	$(COMPOSE) down --volumes

docker-logs:
	$(COMPOSE) logs --follow app

docker-ps:
	$(COMPOSE) ps

db-shell:
	$(COMPOSE) exec mysql sh -lc 'mysql -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" "$$MYSQL_DATABASE"'

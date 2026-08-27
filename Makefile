ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif
COMPOSE := docker compose

.PHONY: help test coverage verify compile build clean run run-dev \
	docker-build docker-up docker-up-interactive docker-down docker-reset docker-logs docker-ps db-shell e2e

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
	@echo "  make docker-up-interactive  Start MySQL and the application in the foreground"
	@echo "  make docker-down   Stop containers without deleting data"
	@echo "  make docker-reset  Stop containers and DELETE the local MySQL volume"
	@echo "  make docker-logs   Follow application logs"
	@echo "  make db-shell      Open the MySQL client"
	@echo ""
	@echo "E2E:"
	@echo "  make e2e           Run the Postman collection as an E2E smoke suite via Newman"
	@echo "                     (requires the app reachable, e.g. after make docker-up; override with BASE_URL=...)"

test:
	$(MVNW) test

coverage:
	$(MVNW) clean verify

verify:
	$(MVNW) clean verify

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

docker-up-interactive:
	@if [ ! -f .env ]; then cp .env.example .env; fi
	$(COMPOSE) up --build

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

# Runs the requests named below, one Newman invocation per request, in this order. This mirrors the
# happy-path sequence documented in README.md without duplicating requests into a second collection.
# Newman's --folder flag matches individual request names too, but it does NOT reorder execution: a
# single invocation with several --folder flags still runs matched items in the collection's own
# physical order, not the order the flags were given. Chaining one invocation per step, wiring
# --export-environment into the next call's --environment, is what makes the flag order authoritative
# and lets pm.environment.set(...) values (id captured by an earlier step) survive into later steps.
# Read/list/archive/deactivate requests are intentionally excluded: they either don't affect state or
# would break later steps (e.g. archiving the customer the rest of the flow depends on).
E2E_ENV_FILE := .e2e-newman-env.json
E2E_STEPS := \
	"Login (bootstrap admin)" \
	"Create customer" \
	"Create vehicle" \
	"Create technician" \
	"Create catalog service" \
	"Create stock item" \
	"Create service order" \
	"Get service order status" \
	"Assign diagnosis assignee" \
	"Perform diagnosis" \
	"Get service order status" \
	"Generate estimate" \
	"Get estimate" \
	"Decide estimate lines" \
	"Get service order status" \
	"Assign technician" \
	"Start execution" \
	"Update execution progress" \
	"Complete execution" \
	"Get service order status" \
	"Finalize service order"

e2e:
	@rm -f $(E2E_ENV_FILE)
	@base="$${BASE_URL:-http://localhost:8080}"; \
	first=1; \
	for step in $(E2E_STEPS); do \
		if [ $$first -eq 1 ]; then \
			npx --yes newman run docs/api/postman/workshop-management-system.postman_collection.json \
				--env-var baseUrl="$$base" --folder "$$step" \
				--export-environment $(E2E_ENV_FILE) || exit 1; \
			first=0; \
		else \
			npx --yes newman run docs/api/postman/workshop-management-system.postman_collection.json \
				--environment $(E2E_ENV_FILE) --folder "$$step" \
				--export-environment $(E2E_ENV_FILE) || exit 1; \
		fi; \
	done
	@rm -f $(E2E_ENV_FILE)

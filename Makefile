ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif
COMPOSE := docker compose

.PHONY: help test coverage verify compile build clean run run-dev \
	docker-build docker-up docker-up-interactive docker-down docker-reset docker-logs docker-ps db-shell e2e \
	sca dast

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
	@echo "Security:"
	@echo "  make sca           Run OWASP Dependency-Check over every declared dependency"
	@echo "                     (export NVD_API_KEY first; the raw report lands in"
	@echo "                     target/dependency-check/ and is never committed)"
	@echo "  make dast          Run OWASP ZAP against the running app via its OpenAPI contract"
	@echo "                     (requires the app reachable, e.g. after make docker-up;"
	@echo "                     override with DAST_BASE_URL=...; report in target/zap/)"
	@echo ""
	@echo "E2E:"
	@echo "  make e2e           Run the Postman collection as an E2E smoke suite via Newman"
	@echo "                     (requires the app reachable, e.g. after make docker-up; override with BASE_URL=...)"

test:
	$(MVNW) test

# Deliberately not bound to a Maven lifecycle phase, so `make verify` keeps its current cost.
# skipTestScope=false includes test-scope dependencies, as required by the challenge baseline.
sca:
	@if [ -z "$(NVD_API_KEY)" ]; then \
		echo "WARNING: NVD_API_KEY is not set. The NVD download will be heavily rate limited and may take"; \
		echo "         over 30 minutes or fail. Request a free key at nvd.nist.gov/developers/request-an-api-key"; \
	fi
	$(MVNW) org.owasp:dependency-check-maven:check -DskipTestScope=false -Dnvd.api.key=$(NVD_API_KEY)

# Requires the application to be reachable (e.g. after make docker-up); override with BASE_URL=...
# The scan is driven by the published OpenAPI contract rather than a blind crawl, and authenticates as
# ADMIN because every business endpoint requires a JWT.
# MSYS_NO_PATHCONV=1 is mandatory on Git Bash for Windows: without it /zap/wrk is rewritten to a Windows
# path, the volume is never mounted and ZAP aborts. It is harmless on Linux and macOS.
DAST_BASE_URL ?= http://localhost:8080
dast:
	@mkdir -p target/zap
	@TOKEN=$$(curl -sf -X POST "$(DAST_BASE_URL)/api/auth/login" \
		-H 'Content-Type: application/json' \
		-d '{"username":"admin","password":"changeme123"}' \
		| sed -E 's/.*"token"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/'); \
	if [ -z "$$TOKEN" ]; then echo "Could not obtain an ADMIN token; is the app running?" >&2; exit 1; fi; \
	AUTH_CFG="-config replacer.full_list(0).description=auth -config replacer.full_list(0).enabled=true -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Authorization -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=\"Bearer $$TOKEN\""; \
	MSYS_NO_PATHCONV=1 docker run --rm --network host \
		-v "$$(pwd)/target/zap:/zap/wrk:rw" \
		ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
		-t "$(DAST_BASE_URL)/v3/api-docs" -f openapi -O "$(DAST_BASE_URL)" -I \
		-r zap-report.html -J zap-report.json \
		-z "$$AUTH_CFG"

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
	"Get average execution time" \
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

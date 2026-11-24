# OrderFlow — atalhos de desenvolvimento. `make help` lista os alvos.
# Requer GNU Make, Docker, Java 21 e Node 24. No Windows, o Make usa o sh do Git.

SHELL := sh
.DEFAULT_GOAL := help

# Variáveis do .env (POSTGRES_*, KAFKA_PORT, KEYCLOAK_PORT...) ficam disponíveis
# para o Compose e para o perfil `dev` do backend.
-include .env
export

COMPOSE := docker compose --env-file .env -f infra/docker-compose.yml
MVNW    := cd backend && ./mvnw -B
NPM     := cd frontend && npm

.PHONY: help env-check up down logs ps \
        frontend-install backend-run frontend-run \
        test test-backend test-frontend test-e2e \
        lint lint-backend lint-frontend format \
        build docker-build clean

help: ## Lista os alvos disponíveis
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

env-check:
	@test -f .env || { echo "Arquivo .env não encontrado. Rode: cp .env.example .env"; exit 1; }

## --- Infra ---------------------------------------------------------------

up: env-check ## Sobe Postgres, Kafka, Kafka UI e Keycloak e espera ficarem healthy
	$(COMPOSE) up -d --wait

down: ## Derruba a infra (mantém os volumes)
	$(COMPOSE) down

logs: ## Acompanha os logs da infra
	$(COMPOSE) logs -f

ps: ## Estado dos containers da infra
	$(COMPOSE) ps

## --- Execução local -------------------------------------------------------

frontend-install: ## Instala as dependências do frontend (npm ci)
	$(NPM) ci

backend-run: env-check ## API em http://localhost:8080 (perfil dev)
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

frontend-run: ## SPA em http://localhost:4200
	$(NPM) start

## --- Qualidade ------------------------------------------------------------

test: test-backend test-frontend ## Testes backend (unit + integração) e frontend (Jest)

test-backend: ## Backend: unit, integração (Testcontainers), ArchUnit, cobertura e lint
	$(MVNW) verify

test-frontend: ## Frontend: Jest com cobertura
	$(NPM) run test:ci

test-e2e: ## Playwright + axe (sobe o dev server sozinho)
	$(NPM) run e2e

lint: lint-backend lint-frontend ## Verifica formatação e lint (backend e frontend)

lint-backend:
	$(MVNW) spotless:check checkstyle:check

lint-frontend:
	$(NPM) run lint

format: ## Aplica formatação (Spotless, ESLint --fix, Prettier)
	$(MVNW) spotless:apply
	$(NPM) run format

## --- Build ---------------------------------------------------------------

build: ## Gera o jar do backend e o bundle do frontend
	$(MVNW) package -DskipTests
	$(NPM) run build

docker-build: ## Gera as imagens orderflow-api e orderflow-web
	docker build -t orderflow-api:local backend
	docker build -t orderflow-web:local frontend

clean: ## Remove artefatos de build
	$(MVNW) clean
	rm -rf frontend/dist frontend/coverage frontend/.angular frontend/playwright-report frontend/test-results

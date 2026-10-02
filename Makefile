.DEFAULT_GOAL := help

# Arquivo local opcional: as variáveis também são exportadas para Gradle e Compose.
-include .env
.EXPORT_ALL_VARIABLES:

GRADLEW ?= ./gradlew
COMPOSE ?= docker compose
POSTGRES_PORT ?= 5432
REDIS_PORT ?= 6379
KAFKA_PORT ?= 9092
DATABASE_URL ?= jdbc:postgresql://localhost:$(POSTGRES_PORT)/mydatabase
KAFKA_BOOTSTRAP_SERVERS ?= localhost:$(KAFKA_PORT)
TOPIC ?= events.demo
TEST ?=

.PHONY: help env check-db check-env up down logs ps config run build test test-http test-integration test-class clean kafka-topics kafka-create-topic

help: ## Lista os comandos e suas descrições (comando padrão).
	@printf 'Kotlin Template — comandos disponíveis\n\n'
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  %-20s %s\n", $$1, $$2}' $(firstword $(MAKEFILE_LIST))
	@printf '\nExemplos: make test-class TEST=com.kotlin.template.identity.AuthHttpTests\n          make kafka-create-topic TOPIC=events.demo\n'

env: ## Cria .env com segredos aleatórios; preserva um arquivo existente.
	@set -eu; if [ -e .env ]; then printf '.env já existe; arquivo preservado.\n'; else \
		command -v openssl >/dev/null || { printf 'Instale OpenSSL para gerar os segredos.\n' >&2; exit 1; }; \
		task_database_password=$$(openssl rand -hex 24); \
		task_jwt_secret=$$(openssl rand -base64 32); \
		(umask 077; set -C; printf 'DATABASE_PASSWORD=%s\nJWT_SECRET=%s\n' "$$task_database_password" "$$task_jwt_secret" > .env); \
		printf '.env criado com permissões restritas.\n'; \
	fi

# Verificações internas usam o ambiente para não imprimir segredos nas receitas.
check-db:
	@test -n "$${DATABASE_PASSWORD:-}" || { printf 'Defina DATABASE_PASSWORD ou execute make env.\n' >&2; exit 1; }

check-env: check-db
	@test -n "$${JWT_SECRET:-}" || { printf 'Defina JWT_SECRET ou execute make env.\n' >&2; exit 1; }

up: check-db ## Inicia PostgreSQL, Redis e Kafka e aguarda os healthchecks.
	@$(COMPOSE) up -d --wait

down: check-db ## Para e remove os containers da infraestrutura local.
	@$(COMPOSE) down

logs: check-db ## Acompanha os logs dos serviços do Compose.
	@$(COMPOSE) logs -f

ps: check-db ## Mostra o estado e as portas dos serviços locais.
	@$(COMPOSE) ps

config: check-db ## Valida o Compose sem exibir configurações ou segredos.
	@$(COMPOSE) config --quiet

run: check-env ## Inicia a infraestrutura e executa a aplicação com bootRun (JDK 25).
	@$(MAKE) --no-print-directory up
	@$(GRADLEW) bootRun

build: ## Compila e empacota a aplicação, executando os testes.
	@$(GRADLEW) build

test: ## Executa todos os testes; integração exige Docker.
	@$(GRADLEW) test

test-http: ## Executa validação, autenticação e autorização HTTP, sem Docker.
	@$(GRADLEW) test --tests 'com.kotlin.template.identity.AuthHttpTests'

test-integration: ## Executa os testes de integração com PostgreSQL, Redis e Kafka via Testcontainers.
	@$(GRADLEW) test --tests '*IntegrationTests'

test-class: ## Executa uma classe ou padrão: make test-class TEST=<classe>.
	@test -n "$${TEST:-}" || { printf 'Informe TEST=<classe ou padrão>.\n' >&2; exit 1; }
	@$(GRADLEW) test --tests "$$TEST"

clean: ## Remove os artefatos do build Gradle.
	@$(GRADLEW) clean

kafka-topics: check-db ## Lista os tópicos do broker local (infraestrutura deve estar ativa).
	@$(COMPOSE) exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list

kafka-create-topic: check-db ## Cria um tópico local: make kafka-create-topic TOPIC=events.demo.
	@$(COMPOSE) exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic "$$TOPIC" --partitions 1 --replication-factor 1

.PHONY: test-customer customer-demo

test-customer: ## Executa testes unitários e integração do fluxo Customer (exige Docker).
	@$(GRADLEW) test --tests 'com.kotlin.template.customer.*'

customer-demo: ## Demonstra CRUD, cache e notificações na API em execução (exige curl, jq e OpenSSL).
	@./scripts/customer-demo.sh

.PHONY: test-restassured coverage

test-restassured: ## Executa testes RestAssured com HTTP real e PostgreSQL, Redis e Kafka (exige Docker).
	@$(GRADLEW) test --tests 'com.kotlin.template.api.RestAssuredIntegrationTests'

coverage: ## Executa a suite completa, gera relatórios JaCoCo e atualiza o badge local do README.
	@$(GRADLEW) coverage

# Docker Compose owns the runtime; no host .env or fixed container names are needed.
COMPOSE ?= docker compose

.PHONY: dev-up dev-down dev-logs docker-shell build run status e2e

dev-up run:
	$(COMPOSE) up -d --build --wait --wait-timeout 1800

# Persistent database, content, models and secrets remain in their named volumes.
dev-down:
	$(COMPOSE) down

dev-logs:
	$(COMPOSE) logs -f --tail=100

# Diagnostic runtime shell. Sources and Maven are not installed in the app image.
docker-shell:
	$(COMPOSE) exec app sh

build:
	$(COMPOSE) build app

status:
	$(COMPOSE) ps -a

e2e:
	./run-e2e.sh

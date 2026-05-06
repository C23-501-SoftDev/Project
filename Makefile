COMPOSE := docker compose --env-file .env
SCRIPTS_DEV := ./scripts/dev

.PHONY: dev-up dev-down dev-logs docker-up docker-down docker-logs docker-shell docker-up-extras

# One-click development environment setup
dev-up:
	$(SCRIPTS_DEV)/bootstrap.sh

# Stop development containers
dev-down:
	$(COMPOSE) down

# View live logs from application and database
dev-logs:
	$(COMPOSE) logs -f

# Direct Docker compose commands (for advanced users)
docker-up:
	$(COMPOSE) up -d --build

docker-up-extras:
	$(COMPOSE) --profile extras up -d --build

docker-down:
	$(COMPOSE) down

docker-logs:
	$(COMPOSE) logs -f

docker-shell:
	$(COMPOSE) exec app sh

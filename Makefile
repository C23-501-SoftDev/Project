# Compose and scripts
COMPOSE := docker compose --env-file .env
SCRIPTS_DEV := ./scripts/dev
ifeq ($(OS),Windows_NT)
DEV_BOOTSTRAP := powershell -NoProfile -ExecutionPolicy Bypass -File $(SCRIPTS_DEV)/bootstrap.ps1
else
DEV_BOOTSTRAP := bash $(SCRIPTS_DEV)/bootstrap.sh
endif

.PHONY: dev-up dev-down dev-logs docker-up docker-down docker-logs docker-shell docker-up-extras

# One-click development environment setup
dev-up:
	@echo "Starting dev environment..."
	@$(DEV_BOOTSTRAP)

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
	$(COMPOSE) exec app sh -c "cd /workspace && exec sh"

ifeq ($(OS),Windows_NT)
attach:
	@powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev/attach.ps1
else
attach:
	@./scripts/dev/attach.sh
endif

ifeq ($(OS),Windows_NT)
attach-vscode:
	@powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev/attach-vscode.ps1
else
attach-vscode:
	@./scripts/dev/attach-vscode.sh
endif

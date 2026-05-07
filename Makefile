# Compose and scripts
COMPOSE := docker compose --env-file .env
SCRIPTS_DEV := ./scripts/dev

.PHONY: dev-up dev-down dev-logs docker-up docker-down docker-logs docker-shell docker-up-extras

# Detect environment (Windows / WSL / Git Bash)
ifeq ($(OS),Windows_NT)
	ifeq ($(strip $(shell where bash 2>nul)),)
		ifeq ($(strip $(shell where wsl 2>nul)),)
			HAS_BASH :=
		else
			HAS_WSL := 1
		endif
	else
		HAS_BASH := 1
	endif
else
	HAS_BASH := 1
endif

ifeq ($(HAS_WSL),1)
	WSL_CURDIR := $(shell wsl wslpath '$(CURDIR)')
endif

# One-click development environment setup
dev-up:
	@echo "Starting dev environment..."
	@if [ "$(HAS_BASH)" = "1" ]; then \
		bash $(SCRIPTS_DEV)/bootstrap.sh; \
	elif [ "$(HAS_WSL)" = "1" ]; then \
		wsl bash -lc 'cd "$(WSL_CURDIR)" && ./scripts/dev/bootstrap.sh'; \
	else \
		echo "No bash or WSL found. Please install WSL2 or Git Bash and ensure 'bash' is in PATH."; exit 1; \
	fi

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

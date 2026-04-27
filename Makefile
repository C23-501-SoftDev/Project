COMPOSE := docker compose --env-file .env

.PHONY: docker-up docker-down docker-logs docker-shell docker-up-extras

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

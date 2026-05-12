# Compose and scripts
COMPOSE := docker compose --env-file .env

.PHONY: dev-up dev-down dev-logs docker-shell build run

# One-click development environment setup
dev-up:
	$(COMPOSE) up -d --build

# Stop development containers
dev-down:
	$(COMPOSE) down

# View live logs from application and database
dev-logs:
	$(COMPOSE) logs -f

# Вход в контейнер приложения
docker-shell:
	docker exec -it kb_app /bin/bash

# Компиляция приложения в контейнере
build:
	docker exec kb_app mvn clean install -DskipTests

# Запуск приложения в контейнере
run:
	docker exec kb_app mvn spring-boot:run

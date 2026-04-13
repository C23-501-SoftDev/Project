# Quality gates (перед завершением change)

Цель: минимально гарантировать, что фича корректно встроена в систему и не ломает сборку.

## Обязательный минимум (backend)
Запускать из `D:\Soft-dev\Project\backend`.

1) Сборка:
- `mvn clean compile`

2) Тесты:
- `mvn test`

3) Smoke-check API (опционально, если есть окружение):
- `mvn spring-boot:run` (dev, требуется PostgreSQL)
- затем проверить `/actuator/health`

## Если нет PostgreSQL
Не менять security/авторизацию “для демо” в main-ветке.  
Для проверки без Postgres используйте тесты (`mvn test`), или поднимайте Postgres локально по `backend/readme.md`.


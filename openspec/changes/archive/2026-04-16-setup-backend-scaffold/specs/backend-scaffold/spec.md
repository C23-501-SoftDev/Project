## ADDED Requirements

### Requirement: Build system compiles successfully
Проект MUST компилироваться без ошибок с использованием Maven и Java 17.

#### Scenario: Maven clean compile
- **WHEN** выполняется команда `mvn clean compile` из директории `backend/`
- **THEN** сборка завершается со статусом `BUILD SUCCESS` и без ошибок компиляции

### Requirement: Application starts without errors
Приложение Spring Boot MUST запускаться и открывать настроенный порт.

#### Scenario: Запуск приложения
- **WHEN** выполняется команда `mvn spring-boot:run`
- **THEN** приложение выводит в лог `Started Application` менее чем за 30 секунд

### Requirement: Health endpoint responds
Эндпоинт `/actuator/health` MUST возвращать HTTP 200 с телом `{"status":"UP"}`.

#### Scenario: Проверка работоспособности возвращает UP
- **WHEN** отправляется GET запрос на `/actuator/health`
- **THEN** статус ответа 200 и тело содержит `"status":"UP"`
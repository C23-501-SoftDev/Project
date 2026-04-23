## 1. Конфигурация сборки

- [ ] 1.1 Создать `pom.xml` с родительским артефактом Spring Boot 3.4.4
- [ ] 1.2 Добавить основные зависимости (Web, Security, JPA, Validation, Actuator)
- [ ] 1.3 Добавить зависимости для баз данных (PostgreSQL, Liquibase, H2 для тестов)
- [ ] 1.4 Добавить вспомогательные зависимости (Lombok, MapStruct, JWT, JGit, SpringDoc)
- [ ] 1.5 Настроить `maven-compiler-plugin` с обработчиками аннотаций Lombok + MapStruct
- [ ] 1.6 Настроить `spring-boot-maven-plugin` для создания исполняемого JAR

## 2. Структура проекта

- [ ] 2.1 Создать корневой пакет `src/main/java/com/knowledgebase/`
- [ ] 2.2 Создать подпакет `interfaces/rest` для контроллеров и DTO
- [ ] 2.3 Создать подпакет `domain` для сущностей и доменных сервисов
- [ ] 2.4 Создать подпакет `infrastructure` для репозиториев
- [ ] 2.5 Создать подпакет `application` для сервисного слоя
- [ ] 2.6 Создать подпакет `config` для классов конфигурации Spring
- [ ] 2.7 Создать директорию `src/main/resources/`
- [ ] 2.8 Создать директории `src/test/java/` и `src/test/resources/`

## 3. Конфигурационные файлы

- [ ] 3.1 Создать `application.yml` с базовой конфигурацией Spring Boot
- [ ] 3.2 Создать `application-test.yml` с конфигурацией тестовой базы данных H2
- [ ] 3.3 Настроить путь к changelog Liquibase в `application.yml`
- [ ] 3.4 Создать главный класс Application с аннотацией `@SpringBootApplication`

## 4. Настройка миграций базы данных

- [ ] 4.1 Создать директорию `src/main/resources/db/changelog/`
- [ ] 4.2 Создать начальный файл changelog `master.xml`

## 5. Верификация

- [ ] 5.1 Запустить `mvn clean compile` — убедиться в BUILD SUCCESS
- [ ] 5.2 Запустить приложение — убедиться в отсутствии ошибок
- [ ] 5.3 Проверить, что `/actuator/health` возвращает статус UP
- [ ] 5.4 Выполнить проверки качества согласно `openspec/quality-gates.md`
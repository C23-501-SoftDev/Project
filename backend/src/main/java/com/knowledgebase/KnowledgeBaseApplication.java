package com.knowledgebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Главный класс приложения "База знаний" (Knowledge Base).
 *
 * Архитектура: Clean Architecture
 * Слои:
 *   - domain        → бизнес-сущности, интерфейсы репозиториев, доменные события
 *   - application   → use cases (AuthService, UserService, SpaceService, PermissionService)
 *   - infrastructure → JPA, JWT, Spring Security, конфигурации
 *   - interfaces    → REST контроллеры, DTO, маперы, обработка ошибок
 *
 * Стек: Java 17, Spring Boot 3.4.x, PostgreSQL 18, Liquibase, JWT (jjwt 0.11.5)
 *
 * Запуск:
 * - Dev профиль:  SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
 * - Тесты:        mvn test
 * - Сборка JAR:   mvn clean package -DskipTests
 *
 * После запуска:
 * - Swagger UI:   http://localhost:8080/swagger-ui.html
 * - Health check: http://localhost:8080/actuator/health
 *
 * Первичные данные: администратор admin/admin123 (из Liquibase changelog 006)
 */
@SpringBootApplication
public class KnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
    }
}

package com.knowledgebase.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI / Swagger UI.
 *
 * Swagger UI доступен по адресу: /swagger-ui.html
 * OpenAPI JSON: /api-docs
 *
 * Настройка JWT Bearer аутентификации в Swagger UI:
 * 1. Нажмите кнопку "Authorize"
 * 2. Введите: Bearer <ваш_токен>
 * 3. Все последующие запросы будут содержать заголовок Authorization
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Knowledge Base API")
                        .description("""
                                REST API для системы управления базой знаний.
                                
                                ## Аутентификация
                                API использует JWT-токены. Для получения токена отправьте POST на `/api/auth/login`.
                                Полученный токен передавайте в заголовке: `Authorization: Bearer <token>`
                                
                                ## Роли
                                - **ADMIN** — полный доступ, управление пользователями и пространствами
                                - **EDITOR** — создание/редактирование документов в разрешённых пространствах
                                - **READER** — только чтение в разрешённых пространствах
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Knowledge Base Team")
                                .email("support@knowledgebase.local"))
                        .license(new License()
                                .name("Internal Use Only")))

                // Настройка JWT Bearer схемы аутентификации
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Введите JWT токен (без префикса 'Bearer ')")));
    }
}

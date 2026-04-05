package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса аутентификации (POST /api/auth/login).
 */
@Schema(description = "Запрос аутентификации")
public record LoginRequest(

    @Schema(description = "Логин пользователя", example = "admin")
    @NotBlank(message = "Логин не может быть пустым")
    String login,

    @Schema(description = "Пароль пользователя", example = "admin123")
    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    String password
) {}

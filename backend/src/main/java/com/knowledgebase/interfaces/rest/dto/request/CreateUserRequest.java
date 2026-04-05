package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.GlobalRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * DTO для создания нового пользователя (POST /api/admin/users).
 * Доступно только для ADMIN.
 */
@Schema(description = "Запрос на создание пользователя")
public record CreateUserRequest(

    @Schema(description = "Логин пользователя (уникальный)", example = "john.doe")
    @NotBlank(message = "Логин не может быть пустым")
    @Size(min = 3, max = 100, message = "Логин должен содержать от 3 до 100 символов")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Логин может содержать только буквы, цифры, точки, дефисы и подчёркивания")
    String login,

    @Schema(description = "Email пользователя (уникальный)", example = "john.doe@example.com")
    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Некорректный формат email")
    @Size(max = 255, message = "Email не может превышать 255 символов")
    String email,

    @Schema(description = "Пароль (минимум 6 символов)", example = "securePass123")
    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 100, message = "Пароль должен содержать от 6 до 100 символов")
    String password,

    @Schema(description = "Глобальная роль пользователя", example = "EDITOR",
            allowableValues = {"ADMIN", "EDITOR", "READER"})
    @NotNull(message = "Роль не может быть null")
    GlobalRole role
) {}

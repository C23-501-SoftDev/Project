package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.GlobalRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными пользователя.
 * Не содержит passwordHash — никогда не передаём хеш пароля клиенту!
 */
@Schema(description = "Данные пользователя")
public record UserResponse(

    @Schema(description = "Уникальный ID пользователя", example = "1")
    Long id,

    @Schema(description = "Логин пользователя", example = "admin")
    String login,

    @Schema(description = "Email пользователя", example = "admin@example.com")
    String email,

    @Schema(description = "Глобальная роль", example = "READER")
    GlobalRole role,

    @Schema(description = "Флаг администратора", example = "true")
    boolean isAdmin,

    @Schema(description = "Флаг soft-удаления", example = "false")
    boolean isDeleted,

    @Schema(description = "Дата создания")
    LocalDateTime createdAt,

    @Schema(description = "Дата последнего обновления")
    LocalDateTime updatedAt
) {}

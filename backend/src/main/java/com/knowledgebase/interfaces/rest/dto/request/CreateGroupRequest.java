package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для создания группы пользователей (POST /api/admin/groups).
 */
@Schema(description = "Запрос на создание группы пользователей")
public record CreateGroupRequest(

    @Schema(description = "Уникальное название группы", example = "Backend Team")
    @NotBlank(message = "Название группы не может быть пустым")
    @Size(min = 2, max = 200, message = "Название должно содержать от 2 до 200 символов")
    String name,

    @Schema(description = "Описание группы", example = "Команда бэкенд-разработки")
    @Size(max = 1000, message = "Описание не может быть длиннее 1000 символов")
    String description
) {}

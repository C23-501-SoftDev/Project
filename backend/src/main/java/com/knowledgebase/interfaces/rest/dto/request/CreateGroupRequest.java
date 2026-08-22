package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO создания группы пользователей (US4.1.8).
 */
@Schema(description = "Запрос на создание группы пользователей")
public record CreateGroupRequest(

    @Schema(description = "Уникальное название группы", example = "Аналитики")
    @NotBlank(message = "Название группы обязательно")
    @Size(max = 200, message = "Название группы не может быть длиннее 200 символов")
    String name,

    @Schema(description = "Описание группы", example = "Группа бизнес-аналитиков проекта")
    String description
) {
}

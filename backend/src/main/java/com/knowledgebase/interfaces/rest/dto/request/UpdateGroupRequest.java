package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO обновления группы пользователей (US4.1.8).
 */
@Schema(description = "Запрос на обновление группы пользователей")
public record UpdateGroupRequest(

    @Schema(description = "Новое название группы", example = "Аналитики")
    @NotBlank(message = "Название группы обязательно")
    @Size(max = 200, message = "Название группы не может быть длиннее 200 символов")
    String name,

    @Schema(description = "Новое описание группы", example = "Группа бизнес-аналитиков проекта")
    String description
) {
}

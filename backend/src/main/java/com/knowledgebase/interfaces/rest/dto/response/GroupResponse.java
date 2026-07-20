package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO группы пользователей (US4.1.8).
 */
@Schema(description = "Группа пользователей")
public record GroupResponse(

    @Schema(description = "ID группы", example = "1")
    Long id,

    @Schema(description = "Название группы", example = "Аналитики")
    String name,

    @Schema(description = "Описание группы", example = "Группа бизнес-аналитиков проекта")
    String description,

    @Schema(description = "Количество участников", example = "3")
    long memberCount,

    @Schema(description = "Дата создания")
    LocalDateTime createdAt,

    @Schema(description = "Дата последнего обновления")
    LocalDateTime updatedAt
) {
}

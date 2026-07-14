package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными группы пользователей.
 */
@Schema(description = "Данные группы пользователей")
public record GroupResponse(

    @Schema(description = "Уникальный ID группы", example = "1")
    Long id,

    @Schema(description = "Название группы", example = "Backend Team")
    String name,

    @Schema(description = "Описание группы")
    String description,

    @Schema(description = "Дата создания")
    LocalDateTime createdAt,

    @Schema(description = "Дата последнего обновления")
    LocalDateTime updatedAt
) {}

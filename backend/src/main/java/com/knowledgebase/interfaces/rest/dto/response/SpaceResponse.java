package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными пространства документов.
 */
@Schema(description = "Данные пространства документов")
public record SpaceResponse(

    @Schema(description = "Уникальный ID пространства", example = "1")
    Long id,

    @Schema(description = "Название пространства", example = "Backend Development")
    String name,

    @Schema(description = "Описание пространства")
    String description,

    @Schema(description = "ID пользователя-владельца", example = "1")
    Long ownerId,

    @Schema(description = "Дата создания")
    LocalDateTime createdAt,

    @Schema(description = "Дата последнего обновления")
    LocalDateTime updatedAt
) {}

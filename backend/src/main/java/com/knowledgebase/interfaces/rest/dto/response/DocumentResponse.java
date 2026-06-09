package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO для ответа с данными документа.
 */
@Schema(description = "Данные документа")
public record DocumentResponse(

    @Schema(description = "ID документа", example = "101")
    Long id,

    @Schema(description = "Заголовок документа", example = "Введение в проект")
    String title,

    @Schema(description = "ID пространства", example = "1")
    Long spaceId,

    @Schema(description = "Название пространства", example = "Общее")
    String spaceName,
    @Schema(description = "ID автора", example = "2")
    Long authorId,

    @Schema(description = "Логин автора", example = "ivanov")
    String authorLogin,
    @Schema(description = "Статус документа", example = "DRAFT")
    DocumentStatus status,

    @Schema(description = "Содержимое документа (Markdown)", example = "# Текст")
    String content,

    @Schema(description = "Содержимое документа (HTML, сгенерированный из Markdown)")
    String contentHtml,

    @Schema(description = "Путь к файлу в Git", example = "spaces/1/101.md")
    String gitFilePath,

    @Schema(description = "Дата создания")
    LocalDateTime createdAt,

    @Schema(description = "Дата обновления")
    LocalDateTime updatedAt
) {}


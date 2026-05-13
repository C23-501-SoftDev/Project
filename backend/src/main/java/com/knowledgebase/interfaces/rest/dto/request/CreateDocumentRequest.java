package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO для создания нового документа (POST /api/documents).
 */
@Schema(description = "Запрос на создание нового документа")
public record CreateDocumentRequest(

    @Schema(description = "Заголовок документа", example = "Введение в проект")
    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(max = 500, message = "Заголовок не может превышать 500 символов")
    String title,

    @Schema(description = "ID пространства", example = "1")
    @NotNull(message = "ID пространства не может быть null")
    Long spaceId,

    @Schema(description = "Содержимое документа (Markdown)", example = "# Заголовок\nТекст документа")
    String content,

    @Schema(description = "ID родительского документа (null если корневой)", example = "null")
    Long parentDocumentId,

    @Schema(description = "ID шаблона (опционально)", example = "null")
    Long templateId
) {}

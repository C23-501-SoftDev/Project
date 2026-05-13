package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * DTO для обновления документа (PUT /api/documents/{id}).
 */
@Schema(description = "Запрос на обновление документа")
public record UpdateDocumentRequest(

    @Schema(description = "Новый заголовок (null = без изменений)", example = "Обновленное введение")
    @Size(max = 500, message = "Заголовок не может превышать 500 символов")
    String title,

    @Schema(description = "Новое содержимое Markdown (null = без изменений)", example = "# Новый контент")
    String content,

    @Schema(description = "Новый статус документа (null = без изменений)", example = "PUBLISHED")
    DocumentStatus status
) {}

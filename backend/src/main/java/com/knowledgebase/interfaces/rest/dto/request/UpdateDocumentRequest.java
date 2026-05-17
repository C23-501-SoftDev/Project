package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * DTO для обновления документа (PUT /api/documents/{id}).
 */
@Schema(description = "Запрос на обновление документа")
public record UpdateDocumentRequest(
    @Size(max = 500, message = "Заголовок не может превышать 500 символов")
    String title,
    String content,
    DocumentStatus status,
    Long parentId
) {}


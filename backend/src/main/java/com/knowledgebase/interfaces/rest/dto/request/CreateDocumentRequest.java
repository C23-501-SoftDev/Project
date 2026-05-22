package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание документа")
public record CreateDocumentRequest(
    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(max = 500, message = "Заголовок не может превышать 500 символов")
    String title,
    String content,
    @NotNull(message = "ID пространства обязателен")
    Long spaceId,
    Long parentId,
    Long templateId
) {}

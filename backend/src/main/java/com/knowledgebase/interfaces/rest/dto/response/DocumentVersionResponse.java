package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * DTO ответа с информацией о версии (коммите) документа.
 */
@Schema(description = "Информация о версии документа")
public record DocumentVersionResponse(
        @Schema(description = "ID коммита", example = "abc123456789")
        String commitId,

        @Schema(description = "Имя автора изменения", example = "Иван Иванов")
        String authorName,

        @Schema(description = "Email автора изменения", example = "ivan@example.com")
        String authorEmail,

        @Schema(description = "Комментарий к коммиту", example = "Update document: Title")
        String commitMessage,

        @Schema(description = "Дата и время изменения", example = "2026-06-06T12:34:56")
        LocalDateTime timestamp
) {}
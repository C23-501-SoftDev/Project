package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа по вложению документа.
 */
@Schema(description = "Данные вложения документа")
public record AttachmentResponse(

        @Schema(description = "ID вложения", example = "10")
        Long id,

        @Schema(description = "ID документа", example = "101")
        Long documentId,

        @Schema(description = "Оригинальное имя файла", example = "specification.pdf")
        String filename,

        @Schema(description = "MIME-тип файла", example = "application/pdf")
        String contentType,

        @Schema(description = "Размер файла в байтах", example = "123456")
        long sizeBytes,

        @Schema(description = "ID пользователя, загрузившего файл", example = "2")
        Long uploadedBy,

        @Schema(description = "Логин загрузившего пользователя", example = "editor")
        String uploadedByLogin,

        @Schema(description = "Дата загрузки")
        LocalDateTime uploadedAt
) {}

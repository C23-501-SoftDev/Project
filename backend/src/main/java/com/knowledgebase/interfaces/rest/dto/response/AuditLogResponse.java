package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO записи журнала аудита (US4.1.5).
 */
@Schema(description = "Запись журнала аудита действий системы")
public record AuditLogResponse(

    @Schema(description = "ID записи", example = "1")
    Long id,

    @Schema(description = "Момент действия", example = "2026-07-20T12:00:00")
    LocalDateTime createdAt,

    @Schema(description = "ID пользователя, совершившего действие", example = "1")
    Long userId,

    @Schema(description = "Логин пользователя на момент действия", example = "admin")
    String userLogin,

    @Schema(description = "Тип действия", example = "DOCUMENT_CREATED")
    String actionType,

    @Schema(description = "Тип ресурса", example = "DOCUMENT")
    String resourceType,

    @Schema(description = "ID затронутого ресурса", example = "42")
    Long resourceId,

    @Schema(description = "Детали действия", example = "title='Инструкция'")
    String details,

    @Schema(description = "IP-адрес клиента", example = "127.0.0.1")
    String ipAddress
) {
}

package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными права доступа к пространству.
 */
@Schema(description = "Право доступа к пространству")
public record SpacePermissionResponse(

    @Schema(description = "Уникальный ID права", example = "1")
    Long id,

    @Schema(description = "ID пространства", example = "1")
    Long spaceId,

    @Schema(description = "ID пользователя", example = "2")
    Long userId,

    @Schema(description = "Тип права доступа", example = "WRITE")
    PermissionType permissionType,

    @Schema(description = "Дата выдачи права")
    LocalDateTime grantedAt
) {}

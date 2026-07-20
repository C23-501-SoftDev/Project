package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO права группы на пространство (US4.2.2).
 */
@Schema(description = "Право группы на пространство")
public record SpaceGroupPermissionResponse(

    @Schema(description = "ID права", example = "1")
    Long id,

    @Schema(description = "ID пространства", example = "1")
    Long spaceId,

    @Schema(description = "ID группы", example = "1")
    Long groupId,

    @Schema(description = "Название группы", example = "Аналитики")
    String groupName,

    @Schema(description = "Тип права", example = "READ")
    String permissionType,

    @Schema(description = "Дата выдачи права")
    LocalDateTime grantedAt
) {
}

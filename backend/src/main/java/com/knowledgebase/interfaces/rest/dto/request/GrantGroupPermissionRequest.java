package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO назначения права группе на пространство (US4.2.2).
 */
@Schema(description = "Запрос на назначение права группе на пространство")
public record GrantGroupPermissionRequest(

    @Schema(description = "ID группы", example = "1")
    @NotNull(message = "ID группы обязателен")
    Long groupId,

    @Schema(description = "Тип права", example = "READ")
    @NotNull(message = "Тип права обязателен")
    PermissionType permissionType
) {
}

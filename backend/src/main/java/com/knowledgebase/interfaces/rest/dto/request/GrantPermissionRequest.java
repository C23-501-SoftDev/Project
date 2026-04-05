package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для назначения прав на пространство (POST /api/admin/spaces/{spaceId}/permissions).
 */
@Schema(description = "Запрос на назначение права доступа к пространству")
public record GrantPermissionRequest(

    @Schema(description = "ID пользователя", example = "2")
    @NotNull(message = "ID пользователя не может быть null")
    Long userId,

    @Schema(description = "Тип права доступа", example = "WRITE",
            allowableValues = {"READ", "WRITE", "OWNER"})
    @NotNull(message = "Тип права не может быть null")
    PermissionType permissionType
) {}

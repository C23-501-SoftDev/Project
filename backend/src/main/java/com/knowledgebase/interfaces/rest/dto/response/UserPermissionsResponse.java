package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO ответа для эндпоинта GET /api/user/permissions?spaceId={id}.
 * Содержит права пользователя в пространстве и флаги для UI.
 */
@Schema(description = "Права пользователя в пространстве")
public record UserPermissionsResponse(

    @Schema(description = "ID пространства", example = "1")
    Long spaceId,

    @Schema(description = "Список прав пользователя в пространстве", example = "[\"READ\", \"WRITE\"]")
    List<PermissionType> permissions,

    @Schema(description = "Флаг: может ли пользователь читать документы", example = "true")
    boolean canRead,

    @Schema(description = "Флаг: может ли пользователь редактировать документы", example = "true")
    boolean canEdit,

    @Schema(description = "Флаг: может ли пользователь создавать документы", example = "true")
    boolean canCreate
) {}

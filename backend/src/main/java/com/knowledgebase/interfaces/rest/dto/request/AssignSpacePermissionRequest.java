package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос на назначение права доступа к пространству")
public record AssignSpacePermissionRequest(

        @Schema(description = "ID пользователя", example = "5")
        @NotNull(message = "ID пользователя не может быть null")
        Long userId,

        @Schema(description = "Тип право доступа", example = "READ",
                allowableValues = {"READ", "WRITE", "OWNER"})
        @NotNull(message = "Тип права не может быть null")
        PermissionType permissionType
) {}

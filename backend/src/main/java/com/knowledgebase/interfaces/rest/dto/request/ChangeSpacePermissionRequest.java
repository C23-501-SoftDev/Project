package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос на изменение типа уже назначенного права доступа")
public record ChangeSpacePermissionRequest(

        @Schema(description = "Новый тип права доступа", example = "OWNER",
                allowableValues = {"READ", "WRITE", "OWNER"})
        @NotNull(message = "Тип права не может быть null")
        PermissionType permissionType
) {}

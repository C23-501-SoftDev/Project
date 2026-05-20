package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Назначение права доступа к пространству")
public record SpacePermissionAssignmentResponse(

        @Schema(description = "Уникальный ID записи о назначении", example = "42")
        Long permissionId,

        @Schema(description = "ID пространства", example = "1")
        Long spaceId,

        @Schema(description = "ID пользователя", example = "2")
        Long userId,

        @Schema(description = "Логин пользователя", example = "ivanov")
        String userLogin,

        @Schema(description = "Email пользователя", example = "ivanov@example.com")
        String userEmail,

        @Schema(description = "Тип назначенного права", example = "WRITE")
        PermissionType permissionType,

        @Schema(description = "Дата выдачи права")
        LocalDateTime grantedAt
) {

    public static SpacePermissionAssignmentResponse from(SpacePermission permission, User userOrNull) {
        if (permission == null) {
            return null;
        }
        String login = userOrNull != null ? userOrNull.getLogin() : null;
        String email = userOrNull != null ? userOrNull.getEmail() : null;
        return new SpacePermissionAssignmentResponse(
                permission.getId(),
                permission.getSpaceId(),
                permission.getUserId(),
                login,
                email,
                permission.getPermissionType(),
                permission.getGrantedAt()
        );
    }
}

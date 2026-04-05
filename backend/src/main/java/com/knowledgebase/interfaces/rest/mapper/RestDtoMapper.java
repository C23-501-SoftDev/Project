package com.knowledgebase.interfaces.rest.mapper;

import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.dto.response.SpacePermissionResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpaceResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Маппер DTO ↔ Domain для слоя interfaces.
 *
 * Преобразует доменные объекты в DTO для HTTP-ответов.
 * Написан вручную (без MapStruct) для полного контроля над маппингом.
 */
@Component
public class RestDtoMapper {

    // ── User ──────────────────────────────────────────────────────────────────

    /**
     * Конвертирует доменный User в UserResponse DTO.
     * Никогда не включает passwordHash в ответ!
     */
    public UserResponse toUserResponse(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    // ── Space ─────────────────────────────────────────────────────────────────

    public SpaceResponse toSpaceResponse(Space space) {
        if (space == null) return null;
        return new SpaceResponse(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getOwnerId(),
                space.getCreatedAt(),
                space.getUpdatedAt()
        );
    }

    // ── SpacePermission ───────────────────────────────────────────────────────

    public SpacePermissionResponse toSpacePermissionResponse(SpacePermission permission) {
        if (permission == null) return null;
        return new SpacePermissionResponse(
                permission.getId(),
                permission.getSpaceId(),
                permission.getUserId(),
                permission.getPermissionType(),
                permission.getGrantedAt()
        );
    }
}

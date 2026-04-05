package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.infrastructure.persistence.entity.SpacePermissionJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменным объектом SpacePermission и JPA-сущностью SpacePermissionJpaEntity.
 */
@Component
public class SpacePermissionJpaMapper {

    public SpacePermissionJpaEntity toJpaEntity(SpacePermission permission) {
        if (permission == null) return null;

        SpacePermissionJpaEntity entity = new SpacePermissionJpaEntity();
        entity.setId(permission.getId());
        entity.setSpaceId(permission.getSpaceId());
        entity.setUserId(permission.getUserId());
        // PermissionType в БД хранится в верхнем регистре: READ, WRITE, OWNER
        entity.setPermissionType(permission.getPermissionType() != null
                ? permission.getPermissionType().name()
                : null);
        entity.setGrantedAt(permission.getGrantedAt());
        return entity;
    }

    public SpacePermission toDomain(SpacePermissionJpaEntity entity) {
        if (entity == null) return null;

        PermissionType permissionType = entity.getPermissionType() != null
                ? PermissionType.valueOf(entity.getPermissionType())
                : null;

        return SpacePermission.restore(
                entity.getId(),
                entity.getSpaceId(),
                entity.getUserId(),
                permissionType,
                entity.getGrantedAt()
        );
    }
}

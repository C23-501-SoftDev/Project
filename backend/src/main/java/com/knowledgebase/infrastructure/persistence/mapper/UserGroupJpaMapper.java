package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменным объектом UserGroup и JPA-сущностью UserGroupJpaEntity.
 */
@Component
public class UserGroupJpaMapper {

    public UserGroupJpaEntity toJpaEntity(UserGroup group) {
        if (group == null) return null;

        UserGroupJpaEntity entity = new UserGroupJpaEntity();
        entity.setId(group.getId());
        entity.setName(group.getName());
        entity.setDescription(group.getDescription());
        entity.setCreatedAt(group.getCreatedAt());
        entity.setUpdatedAt(group.getUpdatedAt());
        return entity;
    }

    public UserGroup toDomain(UserGroupJpaEntity entity) {
        if (entity == null) return null;

        return UserGroup.restore(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

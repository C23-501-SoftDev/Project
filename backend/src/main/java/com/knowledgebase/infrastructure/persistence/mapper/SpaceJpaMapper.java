package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.Space;
import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменным объектом Space и JPA-сущностью SpaceJpaEntity.
 */
@Component
public class SpaceJpaMapper {

    public SpaceJpaEntity toJpaEntity(Space space) {
        if (space == null) return null;

        SpaceJpaEntity entity = new SpaceJpaEntity();
        entity.setId(space.getId());
        entity.setName(space.getName());
        entity.setDescription(space.getDescription());
        entity.setOwnerId(space.getOwnerId());
        entity.setCreatedAt(space.getCreatedAt());
        entity.setUpdatedAt(space.getUpdatedAt());
        return entity;
    }

    public Space toDomain(SpaceJpaEntity entity) {
        if (entity == null) return null;

        return Space.restore(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getOwnerId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

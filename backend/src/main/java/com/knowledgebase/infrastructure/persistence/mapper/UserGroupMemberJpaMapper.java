package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.UserGroupMember;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupMemberJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменным объектом UserGroupMember и JPA-сущностью UserGroupMemberJpaEntity.
 */
@Component
public class UserGroupMemberJpaMapper {

    public UserGroupMemberJpaEntity toJpaEntity(UserGroupMember member) {
        if (member == null) return null;

        UserGroupMemberJpaEntity entity = new UserGroupMemberJpaEntity();
        entity.setId(member.getId());
        entity.setGroupId(member.getGroupId());
        entity.setUserId(member.getUserId());
        entity.setAddedAt(member.getAddedAt());
        return entity;
    }

    public UserGroupMember toDomain(UserGroupMemberJpaEntity entity) {
        if (entity == null) return null;

        return UserGroupMember.restore(
                entity.getId(),
                entity.getGroupId(),
                entity.getUserId(),
                entity.getAddedAt()
        );
    }
}

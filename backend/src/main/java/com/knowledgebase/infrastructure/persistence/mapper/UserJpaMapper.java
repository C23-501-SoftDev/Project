package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменным объектом User и JPA-сущностью UserJpaEntity.
 *
 * Ручная реализация вместо MapStruct для управления конвертацией
 * Enum GlobalRole ↔ String (значения в БД: "Admin", "Editor", "Reader").
 */
@Component
public class UserJpaMapper {

    /**
     * Преобразует доменный объект в JPA-сущность для сохранения в БД.
     */
    public UserJpaEntity toJpaEntity(User user) {
        if (user == null) return null;

        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.getId());
        entity.setLogin(user.getLogin());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setEmail(user.getEmail());
        // Конвертируем enum → строку для БД (Admin, Editor, Reader)
        entity.setRole(user.getRole() != null ? user.getRole().getDbValue() : null);
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    /**
     * Восстанавливает доменный объект из JPA-сущности.
     */
    public User toDomain(UserJpaEntity entity) {
        if (entity == null) return null;

        // Конвертируем строку из БД → enum
        GlobalRole role = entity.getRole() != null
                ? GlobalRole.fromDbValue(entity.getRole())
                : GlobalRole.READER;

        return User.restore(
                entity.getId(),
                entity.getLogin(),
                entity.getPasswordHash(),
                entity.getEmail(),
                role,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

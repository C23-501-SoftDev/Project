package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Членство пользователя в группе (Domain Layer) — US4.1.9.
 *
 * Связь "многие-ко-многим" между пользователями и группами.
 * Пользователь не может состоять в одной группе дважды.
 */
public class GroupMember {

    private final Long id;
    private final Long groupId;
    private final Long userId;
    private final LocalDateTime addedAt;

    private GroupMember(Long id, Long groupId, Long userId, LocalDateTime addedAt) {
        this.id = id;
        this.groupId = groupId;
        this.userId = userId;
        this.addedAt = addedAt;
    }

    /**
     * Фабричный метод добавления пользователя в группу.
     */
    public static GroupMember create(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            throw new IllegalArgumentException("groupId и userId обязательны");
        }
        return new GroupMember(null, groupId, userId, LocalDateTime.now());
    }

    /**
     * Восстановление членства из хранилища.
     */
    public static GroupMember restore(Long id, Long groupId, Long userId, LocalDateTime addedAt) {
        return new GroupMember(id, groupId, userId, addedAt);
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getAddedAt() { return addedAt; }
}

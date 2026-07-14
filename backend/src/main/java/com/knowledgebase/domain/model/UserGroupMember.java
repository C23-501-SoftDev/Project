package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель членства пользователя в группе.
 *
 * Связывает пользователя с группой (отношение «многие-ко-многим»).
 * Через членство пользователь наследует права доступа, назначенные группе.
 *
 * Бизнес-правила:
 * - Пользователь не может дважды входить в одну и ту же группу
 *   (уникальность пары group_id + user_id)
 */
public class UserGroupMember {

    private Long id;

    /** ID группы */
    private Long groupId;

    /** ID пользователя */
    private Long userId;

    /** Дата добавления в группу */
    private LocalDateTime addedAt;

    private UserGroupMember() {}

    /**
     * Фабричный метод для создания нового членства.
     *
     * @param groupId ID группы
     * @param userId  ID пользователя
     * @return новый экземпляр UserGroupMember
     */
    public static UserGroupMember create(Long groupId, Long userId) {
        UserGroupMember member = new UserGroupMember();
        member.groupId = groupId;
        member.userId = userId;
        member.addedAt = LocalDateTime.now();
        return member;
    }

    /**
     * Фабричный метод для восстановления членства из хранилища.
     */
    public static UserGroupMember restore(Long id, Long groupId, Long userId, LocalDateTime addedAt) {
        UserGroupMember member = new UserGroupMember();
        member.id = id;
        member.groupId = groupId;
        member.userId = userId;
        member.addedAt = addedAt;
        return member;
    }

    // Getters

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getAddedAt() { return addedAt; }

    @Override
    public String toString() {
        return "UserGroupMember{id=" + id + ", groupId=" + groupId + ", userId=" + userId + "}";
    }
}

package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель права доступа к пространству.
 *
 * Связывает пользователя с пространством, определяя тип доступа:
 * READ, WRITE или OWNER.
 *
 * Уникальность: один пользователь не может иметь одинаковый тип права
 * в одном пространстве дважды (UNIQUE constraint на space_id + user_id + permission_type).
 *
 * При удалении пользователя или пространства права автоматически удаляются (CASCADE).
 */
public class SpacePermission {

    private Long id;

    /** ID пространства */
    private Long spaceId;

    /** ID пользователя */
    private Long userId;

    /** Тип права: READ, WRITE или OWNER */
    private PermissionType permissionType;

    /** Дата выдачи права */
    private LocalDateTime grantedAt;

    private SpacePermission() {}

    /**
     * Фабричный метод для создания нового права доступа.
     *
     * @param spaceId        ID пространства
     * @param userId         ID пользователя
     * @param permissionType тип права
     * @return новый экземпляр SpacePermission
     */
    public static SpacePermission grant(Long spaceId, Long userId, PermissionType permissionType) {
        SpacePermission permission = new SpacePermission();
        permission.spaceId = spaceId;
        permission.userId = userId;
        permission.permissionType = permissionType;
        permission.grantedAt = LocalDateTime.now();
        return permission;
    }

    /**
     * Фабричный метод для восстановления из хранилища.
     */
    public static SpacePermission restore(Long id, Long spaceId, Long userId,
                                          PermissionType permissionType, LocalDateTime grantedAt) {
        SpacePermission permission = new SpacePermission();
        permission.id = id;
        permission.spaceId = spaceId;
        permission.userId = userId;
        permission.permissionType = permissionType;
        permission.grantedAt = grantedAt;
        return permission;
    }

    // Getters

    public Long getId() { return id; }
    public Long getSpaceId() { return spaceId; }
    public Long getUserId() { return userId; }
    public PermissionType getPermissionType() { return permissionType; }
    public LocalDateTime getGrantedAt() { return grantedAt; }

    @Override
    public String toString() {
        return "SpacePermission{id=" + id + ", spaceId=" + spaceId +
               ", userId=" + userId + ", type=" + permissionType + "}";
    }
}

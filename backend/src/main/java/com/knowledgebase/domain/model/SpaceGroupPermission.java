package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Право группы на пространство (Domain Layer) — US4.2.2.
 *
 * Аналог {@link SpacePermission}, но для группы пользователей:
 * право действует на всех участников группы.
 */
public class SpaceGroupPermission {

    private final Long id;
    private final Long spaceId;
    private final Long groupId;
    private final PermissionType permissionType;
    private final LocalDateTime grantedAt;

    private SpaceGroupPermission(Long id, Long spaceId, Long groupId,
                                 PermissionType permissionType, LocalDateTime grantedAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.groupId = groupId;
        this.permissionType = permissionType;
        this.grantedAt = grantedAt;
    }

    /**
     * Фабричный метод выдачи права группе.
     */
    public static SpaceGroupPermission grant(Long spaceId, Long groupId, PermissionType permissionType) {
        if (spaceId == null || groupId == null || permissionType == null) {
            throw new IllegalArgumentException("spaceId, groupId и permissionType обязательны");
        }
        return new SpaceGroupPermission(null, spaceId, groupId, permissionType, LocalDateTime.now());
    }

    /**
     * Восстановление права из хранилища.
     */
    public static SpaceGroupPermission restore(Long id, Long spaceId, Long groupId,
                                               PermissionType permissionType, LocalDateTime grantedAt) {
        return new SpaceGroupPermission(id, spaceId, groupId, permissionType, grantedAt);
    }

    public Long getId() { return id; }
    public Long getSpaceId() { return spaceId; }
    public Long getGroupId() { return groupId; }
    public PermissionType getPermissionType() { return permissionType; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}

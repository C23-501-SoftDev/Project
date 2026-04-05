package com.knowledgebase.domain.event;

/**
 * Событие предоставления прав на пространство.
 *
 * Публикуется при назначении нового права пользователю.
 * Слушатели могут уведомить пользователя об изменении прав.
 */
public class SpacePermissionGrantedEvent extends DomainEvent {

    private final Long spaceId;
    private final Long userId;
    private final String permissionType;

    public SpacePermissionGrantedEvent(Long spaceId, Long userId, String permissionType) {
        super();
        this.spaceId = spaceId;
        this.userId = userId;
        this.permissionType = permissionType;
    }

    public Long getSpaceId() { return spaceId; }
    public Long getUserId() { return userId; }
    public String getPermissionType() { return permissionType; }
}

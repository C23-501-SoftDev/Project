package com.knowledgebase.domain.exception;

public class SpacePermissionNotFoundException extends DomainException {
    public SpacePermissionNotFoundException(Long permissionId) {
        super("Право доступа не найдено: " + permissionId);
    }

    public SpacePermissionNotFoundException(Long spaceId, Long userId) {
        super("Права доступа не найдены для пользователя " + userId + " в пространстве " + spaceId);
    }
}

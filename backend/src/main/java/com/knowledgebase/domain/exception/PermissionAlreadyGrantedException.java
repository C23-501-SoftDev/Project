package com.knowledgebase.domain.exception;

import com.knowledgebase.domain.model.PermissionType;

public class PermissionAlreadyGrantedException extends ConflictException {
    public PermissionAlreadyGrantedException(Long spaceId, Long userId, PermissionType type) {
        super("Пользователь " + userId + " уже имеет право " + type + " в пространстве " + spaceId);
    }
}

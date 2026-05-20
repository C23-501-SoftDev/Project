package com.knowledgebase.domain.exception;

public class LastOwnerProtectionException extends ConflictException {
    public LastOwnerProtectionException(Long spaceId) {
        super("Невозможно отозвать или понизить единственного владельца пространства " + spaceId);
    }
}

package com.knowledgebase.domain.service;

import com.knowledgebase.domain.exception.InvalidPermissionAssignmentException;
import com.knowledgebase.domain.exception.LastOwnerProtectionException;
import com.knowledgebase.domain.exception.PermissionAlreadyGrantedException;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import org.springframework.stereotype.Service;

@Service
public class DefaultSpacePermissionAssignmentPolicy implements SpacePermissionAssignmentPolicy {

    @Override
    public void validateGrant(User targetUser, Long spaceId, PermissionType type, boolean alreadyExists) {
        if (targetUser.isDeleted()) {
            throw new InvalidPermissionAssignmentException(
                    "Невозможно назначить право удалённому пользователю");
        }
        if (alreadyExists) {
            throw new PermissionAlreadyGrantedException(spaceId, targetUser.getId(), type);
        }
    }

    @Override
    public void validateRevoke(SpacePermission permission, long ownersInSpace) {
        if (permission.getPermissionType() == PermissionType.OWNER && ownersInSpace <= 1) {
            throw new LastOwnerProtectionException(permission.getSpaceId());
        }
    }

    @Override
    public void validateChange(SpacePermission current, PermissionType newType, long ownersInSpace, boolean newTypeAlreadyExists) {
        if (current.getPermissionType() == newType) {
            throw new InvalidPermissionAssignmentException(
                    "Новый тип права совпадает с текущим");
        }
        if (newTypeAlreadyExists) {
            throw new PermissionAlreadyGrantedException(
                    current.getSpaceId(), current.getUserId(), newType);
        }
        if (current.getPermissionType() == PermissionType.OWNER && newType != PermissionType.OWNER && ownersInSpace <= 1) {
            throw new LastOwnerProtectionException(current.getSpaceId());
        }
    }
}

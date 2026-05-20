package com.knowledgebase.domain.service;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;

public interface SpacePermissionAssignmentPolicy {

    void validateGrant(User targetUser, Long spaceId, PermissionType type, boolean alreadyExists);

    void validateRevoke(SpacePermission permission, long ownersInSpace);

    void validateChange(SpacePermission current, PermissionType newType, long ownersInSpace, boolean newTypeAlreadyExists);
}

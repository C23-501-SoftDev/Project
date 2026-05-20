package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.SpacePermissionGrantedEvent;
import com.knowledgebase.domain.exception.LastOwnerProtectionException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.SpacePermissionNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.domain.service.SpacePermissionAssignmentPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpacePermissionAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(SpacePermissionAssignmentService.class);

    private final SpacePermissionRepository permissionRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final SpacePermissionAssignmentPolicy policy;
    private final ApplicationEventPublisher eventPublisher;

    public SpacePermissionAssignmentService(SpacePermissionRepository permissionRepository,
                                            SpaceRepository spaceRepository,
                                            UserRepository userRepository,
                                            SpacePermissionAssignmentPolicy policy,
                                            ApplicationEventPublisher eventPublisher) {
        this.permissionRepository = permissionRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SpacePermission assign(Long spaceId, Long userId, PermissionType type) {
        log.debug("Назначение права: spaceId={}, userId={}, type={}", spaceId, userId, type);

        requireSpaceExists(spaceId);
        User targetUser = requireUserExists(userId);

        boolean alreadyExists = permissionRepository
                .existsBySpaceIdAndUserIdAndPermissionType(spaceId, userId, type);

        policy.validateGrant(targetUser, spaceId, type, alreadyExists);

        SpacePermission saved = permissionRepository.save(
                SpacePermission.grant(spaceId, userId, type));

        eventPublisher.publishEvent(
                new SpacePermissionGrantedEvent(spaceId, userId, type.name()));

        log.info("Право назначено: spaceId={}, userId={}, type={}, permissionId={}",
                spaceId, userId, type, saved.getId());
        return saved;
    }

    @Transactional
    public SpacePermission changeType(Long spaceId, Long permissionId, PermissionType newType) {
        log.debug("Изменение типа права: spaceId={}, permissionId={}, newType={}",
                spaceId, permissionId, newType);

        SpacePermission current = loadPermissionInSpace(spaceId, permissionId);

        long ownersInSpace = countOwners(spaceId);
        boolean newTypeAlreadyExists = permissionRepository
                .existsBySpaceIdAndUserIdAndPermissionType(spaceId, current.getUserId(), newType);

        policy.validateChange(current, newType, ownersInSpace, newTypeAlreadyExists);

        permissionRepository.deleteById(current.getId());
        SpacePermission replacement = permissionRepository.save(
                SpacePermission.grant(spaceId, current.getUserId(), newType));

        eventPublisher.publishEvent(
                new SpacePermissionGrantedEvent(spaceId, current.getUserId(), newType.name()));

        log.info("Тип права изменён: spaceId={}, userId={}, {} -> {}, newPermissionId={}",
                spaceId, current.getUserId(), current.getPermissionType(), newType, replacement.getId());
        return replacement;
    }

    @Transactional
    public void revoke(Long spaceId, Long permissionId) {
        log.debug("Отзыв права: spaceId={}, permissionId={}", spaceId, permissionId);

        SpacePermission permission = loadPermissionInSpace(spaceId, permissionId);

        long ownersInSpace = countOwners(spaceId);
        policy.validateRevoke(permission, ownersInSpace);

        permissionRepository.deleteById(permission.getId());

        log.info("Право отозвано: spaceId={}, userId={}, type={}, permissionId={}",
                spaceId, permission.getUserId(), permission.getPermissionType(), permissionId);
    }

    @Transactional
    public void revokeAllForUser(Long spaceId, Long userId) {
        log.debug("Отзыв всех прав пользователя в пространстве: spaceId={}, userId={}", spaceId, userId);

        requireSpaceExists(spaceId);
        requireUserExists(userId);

        List<SpacePermission> userPermissions =
                permissionRepository.findBySpaceIdAndUserId(spaceId, userId);
        if (userPermissions.isEmpty()) {
            throw new SpacePermissionNotFoundException(spaceId, userId);
        }

        boolean userIsOwner = userPermissions.stream()
                .anyMatch(p -> p.getPermissionType() == PermissionType.OWNER);
        if (userIsOwner && countOwners(spaceId) <= 1) {
            throw new LastOwnerProtectionException(spaceId);
        }

        permissionRepository.deleteBySpaceIdAndUserId(spaceId, userId);

        log.info("Отозвано {} прав пользователя {} в пространстве {}",
                userPermissions.size(), userId, spaceId);
    }

    public List<SpacePermission> listAssignments(Long spaceId) {
        requireSpaceExists(spaceId);
        return permissionRepository.findBySpaceId(spaceId);
    }

    public List<SpacePermission> listAssignmentsForUser(Long spaceId, Long userId) {
        requireSpaceExists(spaceId);
        requireUserExists(userId);
        return permissionRepository.findBySpaceIdAndUserId(spaceId, userId);
    }

    private void requireSpaceExists(Long spaceId) {
        if (spaceRepository.findById(spaceId).isEmpty()) {
            throw new SpaceNotFoundException(spaceId);
        }
    }

    private User requireUserExists(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private SpacePermission loadPermissionInSpace(Long spaceId, Long permissionId) {
        SpacePermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new SpacePermissionNotFoundException(permissionId));

        if (!permission.getSpaceId().equals(spaceId)) {
            throw new SpacePermissionNotFoundException(permissionId);
        }
        return permission;
    }

    private long countOwners(Long spaceId) {
        return permissionRepository.findBySpaceId(spaceId).stream()
                .filter(p -> p.getPermissionType() == PermissionType.OWNER)
                .count();
    }
}

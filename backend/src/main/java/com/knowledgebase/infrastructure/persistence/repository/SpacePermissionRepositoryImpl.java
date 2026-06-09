package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.infrastructure.persistence.entity.SpacePermissionJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.SpacePermissionJpaMapper;
import com.knowledgebase.infrastructure.logging.SystemLogger;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория SpacePermissionRepository через Spring Data JPA.
 */
@Repository
public class SpacePermissionRepositoryImpl implements SpacePermissionRepository {

    private static final SystemLogger systemLog = SystemLogger.getLogger(SpacePermissionRepositoryImpl.class, "repository.space_permission");

    private final SpacePermissionJpaRepository jpaRepository;
    private final SpacePermissionJpaMapper mapper;

    public SpacePermissionRepositoryImpl(SpacePermissionJpaRepository jpaRepository,
                                          SpacePermissionJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SpacePermission save(SpacePermission permission) {
        try {
            SpacePermissionJpaEntity entity = mapper.toJpaEntity(permission);
            SpacePermissionJpaEntity saved = jpaRepository.save(entity);
            return mapper.toDomain(saved);
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "save_space_permission",
                    ex,
                    "entity_id", permission == null ? null : permission.getId(),
                    "space_id", permission == null ? null : permission.getSpaceId(),
                    "user_id", permission == null ? null : permission.getUserId()
            );
            throw ex;
        }
    }

    @Override
    public Optional<SpacePermission> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<SpacePermission> findBySpaceIdAndUserId(Long spaceId, Long userId) {
        return jpaRepository.findBySpaceIdAndUserId(spaceId, userId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SpacePermission> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SpacePermission> findBySpaceId(Long spaceId) {
        return jpaRepository.findBySpaceId(spaceId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsBySpaceIdAndUserIdAndPermissionType(Long spaceId, Long userId,
                                                              PermissionType permissionType) {
        return jpaRepository.existsBySpaceIdAndUserIdAndPermissionType(
                spaceId, userId, permissionType.name());
    }

    @Override
    public boolean hasWriteAccess(Long spaceId, Long userId) {
        return jpaRepository.hasWriteAccess(spaceId, userId);
    }

    @Override
    public boolean hasReadAccess(Long spaceId, Long userId) {
        return jpaRepository.hasReadAccess(spaceId, userId);
    }

    @Override
    public void deleteById(Long id) {
        try {
            jpaRepository.deleteById(id);
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "delete_space_permission",
                    ex,
                    "entity_id", id
            );
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteBySpaceIdAndUserId(Long spaceId, Long userId) {
        try {
            jpaRepository.deleteBySpaceIdAndUserId(spaceId, userId);
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "delete_space_permissions_for_user",
                    ex,
                    "space_id", spaceId,
                    "user_id", userId
            );
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteBySpaceIdAndUserIdAndPermissionType(Long spaceId, Long userId, PermissionType permissionType) {
        try {
            jpaRepository.deleteBySpaceIdAndUserIdAndPermissionType(spaceId, userId, permissionType.name());
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "delete_space_permission_type",
                    ex,
                    "space_id", spaceId,
                    "user_id", userId,
                    "permission_type", permissionType
            );
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteBySpaceId(Long spaceId) {
        try {
            jpaRepository.deleteBySpaceId(spaceId);
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "delete_space_permissions",
                    ex,
                    "space_id", spaceId
            );
            throw ex;
        }
    }
}

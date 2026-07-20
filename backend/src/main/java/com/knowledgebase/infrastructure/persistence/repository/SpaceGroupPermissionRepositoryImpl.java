package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpaceGroupPermission;
import com.knowledgebase.domain.repository.SpaceGroupPermissionRepository;
import com.knowledgebase.infrastructure.persistence.entity.SpaceGroupPermissionJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория SpaceGroupPermissionRepository через Spring Data JPA (US4.2.2).
 */
@Repository
public class SpaceGroupPermissionRepositoryImpl implements SpaceGroupPermissionRepository {

    private final SpaceGroupPermissionJpaRepository jpaRepository;

    public SpaceGroupPermissionRepositoryImpl(SpaceGroupPermissionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SpaceGroupPermission save(SpaceGroupPermission permission) {
        SpaceGroupPermissionJpaEntity entity = new SpaceGroupPermissionJpaEntity();
        entity.setId(permission.getId());
        entity.setSpaceId(permission.getSpaceId());
        entity.setGroupId(permission.getGroupId());
        entity.setPermissionType(permission.getPermissionType().name());
        entity.setGrantedAt(permission.getGrantedAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<SpaceGroupPermission> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SpaceGroupPermission> findBySpaceId(Long spaceId) {
        return jpaRepository.findBySpaceId(spaceId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SpaceGroupPermission> findByGroupId(Long groupId) {
        return jpaRepository.findByGroupId(groupId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SpaceGroupPermission> findByMemberUserId(Long userId) {
        return jpaRepository.findByMemberUserId(userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsBySpaceIdAndGroupIdAndPermissionType(Long spaceId, Long groupId,
                                                              PermissionType permissionType) {
        return jpaRepository.existsBySpaceIdAndGroupIdAndPermissionType(
                spaceId, groupId, permissionType.name());
    }

    @Override
    public boolean hasReadAccessViaGroups(Long spaceId, Long userId) {
        return jpaRepository.hasReadAccessViaGroups(spaceId, userId);
    }

    @Override
    public boolean hasWriteAccessViaGroups(Long spaceId, Long userId) {
        return jpaRepository.hasWriteAccessViaGroups(spaceId, userId);
    }

    @Override
    public List<PermissionType> findTypesBySpaceIdAndMemberUserId(Long spaceId, Long userId) {
        return jpaRepository.findTypesBySpaceIdAndMemberUserId(spaceId, userId)
                .stream()
                .map(PermissionType::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBySpaceIdAndGroupIdAndPermissionType(Long spaceId, Long groupId,
                                                           PermissionType permissionType) {
        jpaRepository.deleteBySpaceIdAndGroupIdAndPermissionType(spaceId, groupId, permissionType.name());
    }

    @Override
    @Transactional
    public void deleteByGroupId(Long groupId) {
        jpaRepository.deleteByGroupId(groupId);
    }

    @Override
    @Transactional
    public void deleteBySpaceId(Long spaceId) {
        jpaRepository.deleteBySpaceId(spaceId);
    }

    private SpaceGroupPermission toDomain(SpaceGroupPermissionJpaEntity entity) {
        return SpaceGroupPermission.restore(
                entity.getId(),
                entity.getSpaceId(),
                entity.getGroupId(),
                PermissionType.valueOf(entity.getPermissionType()),
                entity.getGrantedAt());
    }
}

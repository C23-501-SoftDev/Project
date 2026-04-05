package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.infrastructure.persistence.entity.SpacePermissionJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.SpacePermissionJpaMapper;
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

    private final SpacePermissionJpaRepository jpaRepository;
    private final SpacePermissionJpaMapper mapper;

    public SpacePermissionRepositoryImpl(SpacePermissionJpaRepository jpaRepository,
                                          SpacePermissionJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SpacePermission save(SpacePermission permission) {
        SpacePermissionJpaEntity entity = mapper.toJpaEntity(permission);
        SpacePermissionJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
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
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBySpaceIdAndUserId(Long spaceId, Long userId) {
        jpaRepository.deleteBySpaceIdAndUserId(spaceId, userId);
    }
}

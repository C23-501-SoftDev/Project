package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.SpaceJpaMapper;
import com.knowledgebase.infrastructure.logging.SystemLogger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория SpaceRepository через Spring Data JPA.
 */
@Repository
public class SpaceRepositoryImpl implements SpaceRepository {

    private static final SystemLogger systemLog = SystemLogger.getLogger(SpaceRepositoryImpl.class, "repository.space");

    private final SpaceJpaRepository jpaRepository;
    private final SpaceJpaMapper mapper;

    public SpaceRepositoryImpl(SpaceJpaRepository jpaRepository, SpaceJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Space save(Space space) {
        try {
            SpaceJpaEntity entity = mapper.toJpaEntity(space);
            SpaceJpaEntity saved = jpaRepository.save(entity);
            return mapper.toDomain(saved);
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "save_space",
                    ex,
                    "entity_id", space == null ? null : space.getId()
            );
            throw ex;
        }
    }

    @Override
    public Optional<Space> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Space> findByName(String name) {
        return jpaRepository.findByNameAndIsDeletedFalse(name)
                .map(mapper::toDomain);
    }

    @Override
    public List<Space> findAllByIdIn(Set<Long> ids) {
        return jpaRepository.findAllByIdInAndIsDeletedFalse(ids)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Space> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<SpaceJpaEntity> entities = jpaRepository.findByIsDeletedFalse(pageable).getContent();
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Space> findAllIncludeDeleted(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<SpaceJpaEntity> entities = jpaRepository.findAll(pageable).getContent();
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Space> findDeleted(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<SpaceJpaEntity> entities = jpaRepository.findByIsDeletedTrue(pageable).getContent();
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countDeleted() {
        return jpaRepository.countByIsDeletedTrue();
    }

    @Override
    public long countAllIncludeDeleted() {
        return jpaRepository.count();
    }

    @Override
    public List<Space> findByOwnerId(Long ownerId) {
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        return jpaRepository.findByOwnerIdAndIsDeletedFalse(ownerId, pageable)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        try {
            // We will use SpaceService for soft delete, but keeping this for hard delete if ever needed
            // though typically we should replace this with soft delete logic if we want consistency
            jpaRepository.findById(id).ifPresent(entity -> {
                entity.setDeleted(true);
                jpaRepository.save(entity);
            });
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Database operation failed",
                    "delete_space",
                    ex,
                    "entity_id", id
            );
            throw ex;
        }
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByNameAndIsDeletedFalse(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return jpaRepository.existsByNameAndIdNotAndIsDeletedFalse(name, id);
    }

    @Override
    public long count() {
        return jpaRepository.countByIsDeletedFalse();
    }
}

package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Data JPA репозиторий для SpaceJpaEntity.
 * Используется внутри SpaceRepositoryImpl.
 */
public interface SpaceJpaRepository extends JpaRepository<SpaceJpaEntity, Long> {

    Optional<SpaceJpaEntity> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIdNotAndIsDeletedFalse(String name, Long id);

    Page<SpaceJpaEntity> findByOwnerIdAndIsDeletedFalse(Long ownerId, Pageable pageable);

    Page<SpaceJpaEntity> findByIsDeletedFalse(Pageable pageable);

    Page<SpaceJpaEntity> findByIsDeletedTrue(Pageable pageable);

    List<SpaceJpaEntity> findAllByIdInAndIsDeletedFalse(Set<Long> ids);

    long countByIsDeletedFalse();

    long countByIsDeletedTrue();
}

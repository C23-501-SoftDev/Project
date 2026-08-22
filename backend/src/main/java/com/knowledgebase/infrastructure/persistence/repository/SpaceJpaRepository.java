package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Data JPA репозиторий для SpaceJpaEntity.
 * Используется внутри SpaceRepositoryImpl.
 */
public interface SpaceJpaRepository extends JpaRepository<SpaceJpaEntity, Long> {

    Optional<SpaceJpaEntity> findByNameAndIsDeletedFalse(String name);

    Optional<SpaceJpaEntity> findByIdAndIsDeletedFalse(Long id);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIdNotAndIsDeletedFalse(String name, Long id);

    Page<SpaceJpaEntity> findByOwnerIdAndIsDeletedFalse(Long ownerId, Pageable pageable);

    Page<SpaceJpaEntity> findByOwnerIdAndIsDeletedTrue(Long ownerId, Pageable pageable);

    Page<SpaceJpaEntity> findByOwnerId(Long ownerId, Pageable pageable);

    List<SpaceJpaEntity> findByIsDeletedFalse(org.springframework.data.domain.Sort sort);

    Page<SpaceJpaEntity> findByIsDeletedFalse(Pageable pageable);

    List<SpaceJpaEntity> findByIsDeletedFalseOrderByCreatedAtDesc();

    Page<SpaceJpaEntity> findByIsDeletedTrue(Pageable pageable);

    List<SpaceJpaEntity> findAllByIdInAndIsDeletedFalse(Set<Long> ids);

    long countByIsDeletedFalse();

    long countByIsDeletedTrue();

    long countByOwnerIdAndIsDeletedFalse(Long ownerId);

    long countByOwnerIdAndIsDeletedTrue(Long ownerId);

    long countByOwnerId(Long ownerId);

    @Query("SELECT DISTINCT s.ownerId FROM SpaceJpaEntity s")
    List<Long> findDistinctOwnerIds();

    @Query("SELECT DISTINCT s.ownerId FROM SpaceJpaEntity s WHERE s.isDeleted = false")
    List<Long> findDistinctOwnerIdsByIsDeletedFalse();

    @Query("SELECT DISTINCT s.ownerId FROM SpaceJpaEntity s WHERE s.isDeleted = true")
    List<Long> findDistinctOwnerIdsByIsDeletedTrue();
}

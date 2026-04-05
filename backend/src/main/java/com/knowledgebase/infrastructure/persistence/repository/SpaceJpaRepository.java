package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для SpaceJpaEntity.
 * Используется внутри SpaceRepositoryImpl.
 */
public interface SpaceJpaRepository extends JpaRepository<SpaceJpaEntity, Long> {

    Optional<SpaceJpaEntity> findByName(String name);

    boolean existsByName(String name);

    Page<SpaceJpaEntity> findByOwnerId(Long ownerId, Pageable pageable);
}

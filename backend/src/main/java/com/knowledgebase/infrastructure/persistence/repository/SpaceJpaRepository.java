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

    Optional<SpaceJpaEntity> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<SpaceJpaEntity> findByOwnerId(Long ownerId, Pageable pageable);

    List<SpaceJpaEntity> findAllByIdIn(Set<Long> ids);
}

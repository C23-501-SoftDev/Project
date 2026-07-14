package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserGroupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для UserGroupJpaEntity.
 * Используется внутри UserGroupRepositoryImpl.
 *
 * Пагинацию обеспечивает унаследованный {@code findAll(Pageable)} из JpaRepository.
 */
public interface UserGroupJpaRepository extends JpaRepository<UserGroupJpaEntity, Long> {

    Optional<UserGroupJpaEntity> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}

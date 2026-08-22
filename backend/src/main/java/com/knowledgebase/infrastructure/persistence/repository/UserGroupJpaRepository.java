package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserGroupJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA репозиторий для UserGroupJpaEntity (US4.1.8).
 * Используется внутри UserGroupRepositoryImpl.
 */
public interface UserGroupJpaRepository extends JpaRepository<UserGroupJpaEntity, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<UserGroupJpaEntity> findAll(Pageable pageable);
}

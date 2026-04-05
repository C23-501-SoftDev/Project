package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для UserJpaEntity.
 *
 * Это инфраструктурный интерфейс, используемый внутри UserRepositoryImpl.
 * НЕ должен использоваться напрямую в application или domain слоях.
 *
 * @see UserRepositoryImpl — адаптер для domain-интерфейса UserRepository
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByLogin(String login);

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    Page<UserJpaEntity> findAll(Pageable pageable);

    /**
     * Проверяет, является ли пользователь владельцем пространств.
     * Используется при проверке возможности удаления (ON DELETE RESTRICT).
     *
     * Нативный SQL, потому что JPA Entity Graph между UserJpaEntity и SpaceJpaEntity
     * здесь не нужен — нам достаточно простого COUNT.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM spaces WHERE owner_id = :userId", nativeQuery = true)
    boolean hasOwnedSpaces(@Param("userId") Long userId);
}

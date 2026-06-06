package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    Optional<UserJpaEntity> findByLoginAndIsDeletedFalse(String login);

    Optional<UserJpaEntity> findByLogin(String login);

    Optional<UserJpaEntity> findByEmailAndIsDeletedFalse(String email);

    Optional<UserJpaEntity> findByIdAndIsDeletedFalse(Long id);

    boolean existsByLoginAndIsDeletedFalse(String login);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    Page<UserJpaEntity> findByIsDeletedFalse(Pageable pageable);

    Page<UserJpaEntity> findAll(Pageable pageable);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM spaces WHERE owner_id = :userId", nativeQuery = true)
    boolean hasOwnedSpaces(@Param("userId") Long userId);

    List<UserJpaEntity> findByIdInAndIsDeletedFalse(List<Long> ids);

    @Query("SELECT u FROM UserJpaEntity u WHERE " +
           "(:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)")
    Page<UserJpaEntity> findByStatusFilter(@Param("includeDeleted") Boolean includeDeleted, Pageable pageable);

    @Query("SELECT u FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(:roles IS NULL OR u.role IN :roles)")
    Page<UserJpaEntity> findByStatusAndRoles(@Param("includeDeleted") Boolean includeDeleted,
                                              @Param("roles") List<String> roles,
                                              Pageable pageable);

    @Query("SELECT u FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(LOWER(u.login) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<UserJpaEntity> findByStatusAndSearch(@Param("includeDeleted") Boolean includeDeleted,
                                               @Param("search") String search,
                                               Pageable pageable);

    @Query("SELECT u FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(:roles IS NULL OR u.role IN :roles) AND " +
           "(LOWER(u.login) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<UserJpaEntity> findAllWithFilters(@Param("includeDeleted") Boolean includeDeleted,
                                              @Param("roles") List<String> roles,
                                              @Param("search") String search,
                                              Pageable pageable);

    @Query("SELECT COUNT(u) FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true))")
    long countByStatusFilter(@Param("includeDeleted") Boolean includeDeleted);

    @Query("SELECT COUNT(u) FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(:roles IS NULL OR u.role IN :roles)")
    long countByStatusAndRoles(@Param("includeDeleted") Boolean includeDeleted,
                                 @Param("roles") List<String> roles);

    @Query("SELECT COUNT(u) FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(LOWER(u.login) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    long countByStatusAndSearch(@Param("includeDeleted") Boolean includeDeleted,
                                  @Param("search") String search);

    @Query("SELECT COUNT(u) FROM UserJpaEntity u WHERE " +
           "((:includeDeleted IS NULL AND u.isDeleted = false) OR " +
           "(:includeDeleted = true) OR " +
           "(:includeDeleted = false AND u.isDeleted = true)) AND " +
           "(:roles IS NULL OR u.role IN :roles) AND " +
           "(LOWER(u.login) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    long countAllWithFilters(@Param("includeDeleted") Boolean includeDeleted,
                               @Param("roles") List<String> roles,
                               @Param("search") String search);
}

package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.SpacePermissionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA репозиторий для SpacePermissionJpaEntity.
 * Используется внутри SpacePermissionRepositoryImpl.
 */
public interface SpacePermissionJpaRepository
        extends JpaRepository<SpacePermissionJpaEntity, Long> {

    List<SpacePermissionJpaEntity> findBySpaceIdAndUserId(Long spaceId, Long userId);

    List<SpacePermissionJpaEntity> findByUserId(Long userId);

    List<SpacePermissionJpaEntity> findBySpaceId(Long spaceId);

    boolean existsBySpaceIdAndUserIdAndPermissionType(Long spaceId, Long userId, String permissionType);

    /**
     * Проверяет наличие права на запись (WRITE или OWNER).
     */
    @Query("""
            SELECT COUNT(sp) > 0 FROM SpacePermissionJpaEntity sp
            WHERE sp.spaceId = :spaceId
            AND sp.userId = :userId
            AND sp.permissionType IN ('WRITE', 'OWNER')
            """)
    boolean hasWriteAccess(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    /**
     * Проверяет наличие любого права (READ, WRITE или OWNER).
     */
    @Query("""
            SELECT COUNT(sp) > 0 FROM SpacePermissionJpaEntity sp
            WHERE sp.spaceId = :spaceId
            AND sp.userId = :userId
            """)
    boolean hasReadAccess(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM SpacePermissionJpaEntity sp
            WHERE sp.spaceId = :spaceId
            AND sp.userId = :userId
            """)
    void deleteBySpaceIdAndUserId(@Param("spaceId") Long spaceId, @Param("userId") Long userId);
}

package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.SpaceGroupPermissionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA репозиторий для SpaceGroupPermissionJpaEntity (US4.2.2).
 * Используется внутри SpaceGroupPermissionRepositoryImpl.
 *
 * Запросы «через группы» соединяют права групп с членством пользователя
 * (user_group_members) — так пользователь наследует права своих групп.
 */
public interface SpaceGroupPermissionJpaRepository extends JpaRepository<SpaceGroupPermissionJpaEntity, Long> {

    List<SpaceGroupPermissionJpaEntity> findBySpaceId(Long spaceId);

    List<SpaceGroupPermissionJpaEntity> findByGroupId(Long groupId);

    boolean existsBySpaceIdAndGroupIdAndPermissionType(Long spaceId, Long groupId, String permissionType);

    /**
     * Все права групп, в которых состоит пользователь.
     */
    @Query("""
            SELECT p FROM SpaceGroupPermissionJpaEntity p, UserGroupMemberJpaEntity m
            WHERE p.groupId = m.groupId AND m.userId = :userId
            """)
    List<SpaceGroupPermissionJpaEntity> findByMemberUserId(@Param("userId") Long userId);

    /**
     * Есть ли у пользователя хотя бы одно право на пространство через группы.
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM SpaceGroupPermissionJpaEntity p, UserGroupMemberJpaEntity m
            WHERE p.groupId = m.groupId AND m.userId = :userId AND p.spaceId = :spaceId
            """)
    boolean hasReadAccessViaGroups(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    /**
     * Есть ли у пользователя право записи (WRITE/OWNER) на пространство через группы.
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM SpaceGroupPermissionJpaEntity p, UserGroupMemberJpaEntity m
            WHERE p.groupId = m.groupId AND m.userId = :userId AND p.spaceId = :spaceId
            AND p.permissionType IN ('WRITE', 'OWNER')
            """)
    boolean hasWriteAccessViaGroups(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    /**
     * Типы прав пользователя в пространстве, полученные через группы.
     */
    @Query("""
            SELECT DISTINCT p.permissionType FROM SpaceGroupPermissionJpaEntity p, UserGroupMemberJpaEntity m
            WHERE p.groupId = m.groupId AND m.userId = :userId AND p.spaceId = :spaceId
            """)
    List<String> findTypesBySpaceIdAndMemberUserId(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM SpaceGroupPermissionJpaEntity p
            WHERE p.spaceId = :spaceId AND p.groupId = :groupId AND p.permissionType = :permissionType
            """)
    void deleteBySpaceIdAndGroupIdAndPermissionType(@Param("spaceId") Long spaceId,
                                                    @Param("groupId") Long groupId,
                                                    @Param("permissionType") String permissionType);

    @Modifying
    @Query("DELETE FROM SpaceGroupPermissionJpaEntity p WHERE p.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("DELETE FROM SpaceGroupPermissionJpaEntity p WHERE p.spaceId = :spaceId")
    void deleteBySpaceId(@Param("spaceId") Long spaceId);
}

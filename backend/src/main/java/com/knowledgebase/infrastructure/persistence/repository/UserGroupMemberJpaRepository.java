package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserGroupMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA репозиторий для UserGroupMemberJpaEntity (US4.1.9).
 * Используется внутри GroupMemberRepositoryImpl.
 */
public interface UserGroupMemberJpaRepository extends JpaRepository<UserGroupMemberJpaEntity, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    List<UserGroupMemberJpaEntity> findByGroupIdOrderByAddedAtAsc(Long groupId);

    long countByGroupId(Long groupId);

    @Query("SELECT m.groupId FROM UserGroupMemberJpaEntity m WHERE m.userId = :userId")
    List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserGroupMemberJpaEntity m WHERE m.groupId = :groupId AND m.userId = :userId")
    void deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserGroupMemberJpaEntity m WHERE m.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}

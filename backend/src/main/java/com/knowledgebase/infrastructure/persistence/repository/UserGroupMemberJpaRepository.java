package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.UserGroupMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA репозиторий для UserGroupMemberJpaEntity.
 * Используется внутри UserGroupMemberRepositoryImpl.
 */
public interface UserGroupMemberJpaRepository extends JpaRepository<UserGroupMemberJpaEntity, Long> {

    List<UserGroupMemberJpaEntity> findByGroupId(Long groupId);

    List<UserGroupMemberJpaEntity> findByUserId(Long userId);

    Optional<UserGroupMemberJpaEntity> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    @Modifying
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    void deleteByGroupId(Long groupId);
}

package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.GroupMember;
import com.knowledgebase.domain.repository.GroupMemberRepository;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupMemberJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория GroupMemberRepository через Spring Data JPA (US4.1.9).
 */
@Repository
public class GroupMemberRepositoryImpl implements GroupMemberRepository {

    private final UserGroupMemberJpaRepository jpaRepository;

    public GroupMemberRepositoryImpl(UserGroupMemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GroupMember save(GroupMember member) {
        UserGroupMemberJpaEntity entity = new UserGroupMemberJpaEntity();
        entity.setId(member.getId());
        entity.setGroupId(member.getGroupId());
        entity.setUserId(member.getUserId());
        entity.setAddedAt(member.getAddedAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public boolean existsByGroupIdAndUserId(Long groupId, Long userId) {
        return jpaRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Override
    public List<GroupMember> findByGroupId(Long groupId) {
        return jpaRepository.findByGroupIdOrderByAddedAtAsc(groupId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> findGroupIdsByUserId(Long userId) {
        return jpaRepository.findGroupIdsByUserId(userId);
    }

    @Override
    public long countByGroupId(Long groupId) {
        return jpaRepository.countByGroupId(groupId);
    }

    @Override
    @Transactional
    public void deleteByGroupIdAndUserId(Long groupId, Long userId) {
        jpaRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Override
    @Transactional
    public void deleteByGroupId(Long groupId) {
        jpaRepository.deleteByGroupId(groupId);
    }

    private GroupMember toDomain(UserGroupMemberJpaEntity entity) {
        return GroupMember.restore(
                entity.getId(),
                entity.getGroupId(),
                entity.getUserId(),
                entity.getAddedAt());
    }
}

package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.UserGroupMember;
import com.knowledgebase.domain.repository.UserGroupMemberRepository;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupMemberJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.UserGroupMemberJpaMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория UserGroupMemberRepository через Spring Data JPA.
 */
@Repository
public class UserGroupMemberRepositoryImpl implements UserGroupMemberRepository {

    private final UserGroupMemberJpaRepository jpaRepository;
    private final UserGroupMemberJpaMapper mapper;

    public UserGroupMemberRepositoryImpl(UserGroupMemberJpaRepository jpaRepository,
                                         UserGroupMemberJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserGroupMember save(UserGroupMember member) {
        UserGroupMemberJpaEntity entity = mapper.toJpaEntity(member);
        UserGroupMemberJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<UserGroupMember> findByGroupId(Long groupId) {
        return jpaRepository.findByGroupId(groupId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserGroupMember> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserGroupMember> findByGroupIdAndUserId(Long groupId, Long userId) {
        return jpaRepository.findByGroupIdAndUserId(groupId, userId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByGroupIdAndUserId(Long groupId, Long userId) {
        return jpaRepository.existsByGroupIdAndUserId(groupId, userId);
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
}

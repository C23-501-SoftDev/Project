package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.UserGroupRepository;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория UserGroupRepository через Spring Data JPA (US4.1.8).
 */
@Repository
public class UserGroupRepositoryImpl implements UserGroupRepository {

    private final UserGroupJpaRepository jpaRepository;

    public UserGroupRepositoryImpl(UserGroupJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserGroup save(UserGroup group) {
        UserGroupJpaEntity entity = toJpaEntity(group);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<UserGroup> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return jpaRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public List<UserGroup> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return jpaRepository.findAll(pageable).getContent()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private UserGroupJpaEntity toJpaEntity(UserGroup group) {
        UserGroupJpaEntity entity = new UserGroupJpaEntity();
        entity.setId(group.getId());
        entity.setName(group.getName());
        entity.setDescription(group.getDescription());
        entity.setCreatedAt(group.getCreatedAt());
        entity.setUpdatedAt(group.getUpdatedAt());
        return entity;
    }

    private UserGroup toDomain(UserGroupJpaEntity entity) {
        return UserGroup.restore(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

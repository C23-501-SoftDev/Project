package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.UserGroupRepository;
import com.knowledgebase.infrastructure.persistence.entity.UserGroupJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.UserGroupJpaMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория UserGroupRepository через Spring Data JPA.
 */
@Repository
public class UserGroupRepositoryImpl implements UserGroupRepository {

    private final UserGroupJpaRepository jpaRepository;
    private final UserGroupJpaMapper mapper;

    public UserGroupRepositoryImpl(UserGroupJpaRepository jpaRepository, UserGroupJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserGroup save(UserGroup group) {
        UserGroupJpaEntity entity = mapper.toJpaEntity(group);
        UserGroupJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserGroup> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UserGroup> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public List<UserGroup> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return jpaRepository.findAll(pageable).getContent()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return jpaRepository.count();
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
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}

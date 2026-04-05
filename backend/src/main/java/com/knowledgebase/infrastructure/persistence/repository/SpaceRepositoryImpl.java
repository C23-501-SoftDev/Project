package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.SpaceJpaMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория SpaceRepository через Spring Data JPA.
 */
@Repository
public class SpaceRepositoryImpl implements SpaceRepository {

    private final SpaceJpaRepository jpaRepository;
    private final SpaceJpaMapper mapper;

    public SpaceRepositoryImpl(SpaceJpaRepository jpaRepository, SpaceJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Space save(Space space) {
        SpaceJpaEntity entity = mapper.toJpaEntity(space);
        SpaceJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Space> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Space> findByName(String name) {
        return jpaRepository.findByName(name)
                .map(mapper::toDomain);
    }

    @Override
    public List<Space> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return jpaRepository.findAll(pageable)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Space> findByOwnerId(Long ownerId) {
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        return jpaRepository.findByOwnerId(ownerId, pageable)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}

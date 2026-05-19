package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Template;
import com.knowledgebase.domain.repository.TemplateRepository;
import com.knowledgebase.infrastructure.persistence.mapper.TemplateJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TemplateRepositoryImpl implements TemplateRepository {
    private final TemplateJpaRepository jpaRepository;
    private final TemplateJpaMapper mapper;

    public TemplateRepositoryImpl(TemplateJpaRepository jpaRepository, TemplateJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Template> findAll() {
        return mapper.toDomainList(jpaRepository.findAll());
    }

    @Override
    public Optional<Template> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
}

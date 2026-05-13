package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.infrastructure.persistence.entity.DocumentJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.DocumentJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DocumentJpaRepository jpaRepository;
    private final DocumentJpaMapper mapper;

    public DocumentRepositoryImpl(DocumentJpaRepository jpaRepository, DocumentJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Document save(Document document) {
        DocumentJpaEntity entity = mapper.toEntity(document);
        DocumentJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Document> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Document> findBySpaceId(Long spaceId, boolean includeDeleted) {
        List<DocumentJpaEntity> entities;
        if (includeDeleted) {
            entities = jpaRepository.findBySpaceId(spaceId);
        } else {
            entities = jpaRepository.findBySpaceIdAndStatusNot(spaceId, "Deleted");
        }
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Document> findByAuthorId(Long authorId) {
        return jpaRepository.findByAuthorId(authorId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}

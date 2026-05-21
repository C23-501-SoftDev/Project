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
    public boolean existsByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId) {
        if (parentId == null) {
            return jpaRepository.countByTitleAndSpaceIdAndNoParent(title, spaceId) > 0;
        }
        return jpaRepository.countByTitleAndSpaceIdAndParentId(title, spaceId, parentId) > 0;
    }

    @Override
    public boolean existsByTitleAndSpaceIdAndNoParent(String title, Long spaceId) {
        return jpaRepository.countByTitleAndSpaceIdAndNoParent(title, spaceId) > 0;
    }

    @Override
    public List<Document> findAll(boolean includeDeleted) {
        List<DocumentJpaEntity> entities = includeDeleted ? jpaRepository.findAll() : jpaRepository.findByStatusNot("Deleted");
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Document> findAccessibleByUserId(Long userId, boolean includeDeleted) {
        // Получаем ID пространств, доступных пользователю
        List<com.knowledgebase.domain.model.SpacePermission> permissions = 
            com.knowledgebase.application.ApplicationContextHolder.getBean(com.knowledgebase.domain.repository.SpacePermissionRepository.class)
            .findByUserId(userId);
        
        java.util.Set<Long> accessibleSpaceIds = permissions.stream()
                .map(com.knowledgebase.domain.model.SpacePermission::getSpaceId)
                .collect(java.util.stream.Collectors.toSet());

        if (accessibleSpaceIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<DocumentJpaEntity> entities;
        if (includeDeleted) {
            entities = jpaRepository.findAllBySpaceIdIn(accessibleSpaceIds);
        } else {
            entities = jpaRepository.findAllBySpaceIdInAndStatusNot(accessibleSpaceIds, "Deleted");
        }
        
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Long> findAncestorIds(Long documentId) {
        return jpaRepository.findAncestorIds(documentId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}

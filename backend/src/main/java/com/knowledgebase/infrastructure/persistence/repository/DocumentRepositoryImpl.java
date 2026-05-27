package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.infrastructure.persistence.entity.DocumentJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.DocumentJpaMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DocumentJpaRepository jpaRepository;
    private final DocumentJpaMapper mapper;
    private final SpacePermissionRepository spacePermissionRepository;

    public DocumentRepositoryImpl(DocumentJpaRepository jpaRepository,
                                  DocumentJpaMapper mapper,
                                  SpacePermissionRepository spacePermissionRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.spacePermissionRepository = spacePermissionRepository;
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
    public List<Document> findBySpaceIdPaged(Long spaceId, boolean includeDeleted, int page, int size) {
        var pageable = PageRequest.of(page, size);
        if (includeDeleted) {
            return jpaRepository.findBySpaceId(spaceId, pageable).stream()
                    .map(mapper::toDomain).collect(Collectors.toList());
        }
        return jpaRepository.findBySpaceIdAndStatusNot(spaceId, "Deleted", pageable).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countBySpaceId(Long spaceId, boolean includeDeleted) {
        if (includeDeleted) {
            return jpaRepository.countBySpaceId(spaceId);
        }
        return jpaRepository.countBySpaceIdAndStatusNot(spaceId, "Deleted");
    }

    @Override
    public List<Document> findBySpaceIdIn(List<Long> spaceIds, boolean includeDeleted) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocumentJpaEntity> entities;
        if (includeDeleted) {
            entities = jpaRepository.findBySpaceIdIn(spaceIds);
        } else {
            entities = jpaRepository.findBySpaceIdInAndStatusNot(spaceIds, "Deleted");
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
    public Optional<Document> findBySpaceIdAndTitle(Long spaceId, String title) {
        return jpaRepository.findBySpaceIdAndTitle(spaceId, title).map(mapper::toDomain);
    }

    @Override
    public List<Document> findAll(boolean includeDeleted) {
        List<DocumentJpaEntity> entities = includeDeleted ? jpaRepository.findAll() : jpaRepository.findByStatusNot("Deleted");
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Document> findAccessibleByUserId(Long userId, boolean includeDeleted) {
        Set<Long> accessibleSpaceIds = spacePermissionRepository.findByUserId(userId).stream()
                .map(com.knowledgebase.domain.model.SpacePermission::getSpaceId)
                .collect(Collectors.toSet());

        if (accessibleSpaceIds.isEmpty()) {
            return Collections.emptyList();
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

    @Override
    public boolean hasChildren(Long id) {
        return jpaRepository.existsByParentDocumentId(id);
    }
}

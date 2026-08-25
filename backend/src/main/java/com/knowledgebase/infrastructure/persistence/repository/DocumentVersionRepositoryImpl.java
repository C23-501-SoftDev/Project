package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.infrastructure.persistence.entity.DocumentVersionJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentVersionRepositoryImpl implements DocumentVersionRepository {
    private final DocumentVersionJpaRepository jpaRepository;

    public DocumentVersionRepositoryImpl(DocumentVersionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DocumentVersion save(DocumentVersion version) {
        DocumentVersionJpaEntity entity = new DocumentVersionJpaEntity();
        entity.setId(version.id());
        entity.setDocumentId(version.documentId());
        entity.setGitHash(version.gitHash());
        entity.setGitFilePath(version.gitFilePath());
        entity.setAuthorId(version.authorId());
        entity.setComment(version.comment());
        entity.setCreatedAt(version.createdAt());
        DocumentVersionJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DocumentVersion> findByDocumentIdAndGitHash(Long documentId, String gitHash) {
        return jpaRepository.findByDocumentIdAndGitHash(documentId, gitHash).map(this::toDomain);
    }

    @Override
    public List<DocumentVersion> findByDocumentIdOrderByCreatedAtAsc(Long documentId) {
        return jpaRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(this::toDomain)
                .toList();
    }

    private DocumentVersion toDomain(DocumentVersionJpaEntity entity) {
        return new DocumentVersion(entity.getId(), entity.getDocumentId(), entity.getGitHash(),
                entity.getGitFilePath(), entity.getAuthorId(), entity.getComment(), entity.getCreatedAt());
    }
}

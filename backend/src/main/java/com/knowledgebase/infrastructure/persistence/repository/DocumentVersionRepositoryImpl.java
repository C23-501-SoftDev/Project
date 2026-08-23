package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.infrastructure.persistence.entity.DocumentVersionJpaEntity;
import org.springframework.stereotype.Repository;

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
        entity.setAuthorId(version.authorId());
        entity.setComment(version.comment());
        entity.setCreatedAt(version.createdAt());
        DocumentVersionJpaEntity saved = jpaRepository.save(entity);
        return new DocumentVersion(saved.getId(), saved.getDocumentId(), saved.getGitHash(),
                saved.getAuthorId(), saved.getComment(), saved.getCreatedAt());
    }
}

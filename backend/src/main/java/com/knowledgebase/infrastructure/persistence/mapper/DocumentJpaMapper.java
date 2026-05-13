package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.infrastructure.persistence.entity.DocumentJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменной моделью Document и JPA-сущностью DocumentJpaEntity.
 */
@Component
public class DocumentJpaMapper {

    public Document toDomain(DocumentJpaEntity entity) {
        if (entity == null) return null;

        return Document.restore(
                entity.getId(),
                entity.getTitle(),
                entity.getGitFilePath(),
                DocumentStatus.fromDbValue(entity.getStatus()),
                entity.getAuthorId(),
                entity.getSpaceId(),
                entity.getTemplateId(),
                entity.getParentDocumentId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public DocumentJpaEntity toEntity(Document domain) {
        if (domain == null) return null;

        DocumentJpaEntity entity = new DocumentJpaEntity();
        entity.setId(domain.getId());
        entity.setTitle(domain.getTitle());
        entity.setGitFilePath(domain.getGitFilePath());
        entity.setStatus(domain.getStatus().getDbValue());
        entity.setAuthorId(domain.getAuthorId());
        entity.setSpaceId(domain.getSpaceId());
        entity.setTemplateId(domain.getTemplateId());
        entity.setParentDocumentId(domain.getParentDocumentId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
}

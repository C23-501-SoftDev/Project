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
                entity.getPreviousStatus() != null ? DocumentStatus.fromDbValue(entity.getPreviousStatus()) : null,
                entity.getAuthorId(),
                entity.getSpaceId(),
                entity.getTemplateId(),
                entity.getParentDocumentId(),
                entity.getPreviousParentId(),
                entity.getSortOrder(),
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
        entity.setPreviousStatus(domain.getPreviousStatus() != null ? domain.getPreviousStatus().getDbValue() : null);
        entity.setAuthorId(domain.getAuthorId());
        entity.setSpaceId(domain.getSpaceId());
        entity.setTemplateId(domain.getTemplateId());
        entity.setParentDocumentId(domain.getParentDocumentId());
        entity.setPreviousParentId(domain.getPreviousParentId());
        entity.setSortOrder(domain.getSortOrder());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
}

package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.infrastructure.persistence.entity.AttachmentJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Маппер между доменной моделью Attachment и JPA-сущностью AttachmentJpaEntity.
 */
@Component
public class AttachmentJpaMapper {

    public Attachment toDomain(AttachmentJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return Attachment.restore(
                entity.getId(),
                entity.getDocumentId(),
                entity.getVersionId(),
                entity.getFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStoragePath(),
                entity.getUploadedBy(),
                entity.getUploadedAt()
        );
    }

    public AttachmentJpaEntity toEntity(Attachment domain) {
        if (domain == null) {
            return null;
        }

        AttachmentJpaEntity entity = new AttachmentJpaEntity();
        entity.setId(domain.getId());
        entity.setDocumentId(domain.getDocumentId());
        entity.setVersionId(domain.getVersionId());
        entity.setFilename(domain.getFilename());
        entity.setContentType(domain.getContentType());
        entity.setSizeBytes(domain.getSizeBytes());
        entity.setStoragePath(domain.getStoragePath());
        entity.setUploadedBy(domain.getUploadedBy());
        entity.setUploadedAt(domain.getUploadedAt());
        return entity;
    }
}

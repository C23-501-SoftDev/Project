package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.AttachmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA репозиторий для AttachmentJpaEntity.
 */
public interface AttachmentJpaRepository extends JpaRepository<AttachmentJpaEntity, Long> {

    List<AttachmentJpaEntity> findByDocumentIdOrderByUploadedAtDesc(Long documentId);

    Optional<AttachmentJpaEntity> findByIdAndDocumentId(Long id, Long documentId);
}

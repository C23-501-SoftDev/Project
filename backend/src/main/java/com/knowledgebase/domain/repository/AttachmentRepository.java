package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Attachment;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория метаданных вложений.
 */
public interface AttachmentRepository {

    Attachment save(Attachment attachment);

    Optional<Attachment> findById(Long id);

    List<Attachment> findByDocumentId(Long documentId, boolean includeDeleted);

    void deleteById(Long id);

    boolean existsById(Long id);
}

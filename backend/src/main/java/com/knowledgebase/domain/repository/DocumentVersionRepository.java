package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.DocumentVersion;

import java.util.List;
import java.util.Optional;

/** Port for persisting document-version metadata independently of Git. */
public interface DocumentVersionRepository {
    DocumentVersion save(DocumentVersion version);

    Optional<DocumentVersion> findByDocumentIdAndGitHash(Long documentId, String gitHash);

    List<DocumentVersion> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}

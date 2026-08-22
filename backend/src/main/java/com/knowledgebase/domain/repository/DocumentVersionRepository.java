package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.DocumentVersion;

/** Port for persisting document-version metadata independently of Git. */
public interface DocumentVersionRepository {
    DocumentVersion save(DocumentVersion version);
}

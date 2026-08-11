package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.repository.DocumentContentRepository.CommitLogEntry;
import java.util.List;

public interface VersionRepository {
    void saveVersion(Long documentId, String gitHash, Long authorId, String comment);
    List<CommitLogEntry> findVersionsByDocumentId(Long documentId);
}

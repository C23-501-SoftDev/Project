package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/** Metadata which links a document save to its immutable Git commit. */
public record DocumentVersion(
        Long id,
        Long documentId,
        String gitHash,
        String gitFilePath,
        Long authorId,
        String comment,
        LocalDateTime createdAt) {

    public static DocumentVersion create(Long documentId, String gitHash, Long authorId,
                                         String comment, LocalDateTime createdAt) {
        return create(documentId, gitHash, null, authorId, comment, createdAt);
    }

    public static DocumentVersion create(Long documentId, String gitHash, String gitFilePath, Long authorId,
                                         String comment, LocalDateTime createdAt) {
        return new DocumentVersion(null, documentId, gitHash, gitFilePath, authorId, comment, createdAt);
    }
}

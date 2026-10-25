package com.knowledgebase.domain.rag;

import java.time.LocalDateTime;

/** A retrieved excerpt whose source is an immutable document version. */
public record RagChunk(long documentId, long spaceId, String versionSha,
                       int chunkIndex, String text, double score, LocalDateTime sourceUpdatedAt) { }

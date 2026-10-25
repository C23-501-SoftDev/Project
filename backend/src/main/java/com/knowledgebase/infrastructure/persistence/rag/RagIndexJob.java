package com.knowledgebase.infrastructure.persistence.rag;

import java.time.LocalDateTime;

public record RagIndexJob(long documentId, long spaceId, String versionSha, String gitFilePath,
                          String embeddingModel, int embeddingDimensions, String leaseToken,
                          LocalDateTime sourceUpdatedAt) { }

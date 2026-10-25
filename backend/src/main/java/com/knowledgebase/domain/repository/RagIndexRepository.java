package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.rag.RagChunk;
import java.util.List;

public interface RagIndexRepository {
    List<RagChunk> search(float[] query, String model, List<Long> authorizedDocumentIds,
                          int limit, double minimumScore, int maxChunksPerDocument);

    boolean isAvailable();
}

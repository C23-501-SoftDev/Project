package com.knowledgebase.domain.rag;

import java.util.List;

/** Provider boundary. Returned finite, nonzero vectors preserve the order of texts. */
public interface RagEmbeddingClient {
    List<float[]> embed(List<String> texts);
}

package com.knowledgebase.domain.rag;

import java.util.List;

public record RagAnswer(String answer, List<Source> sources, boolean insufficientEvidence, String requestId) {
    public RagAnswer { sources = List.copyOf(sources); }
    public record Source(long documentId, String title, String url, String versionSha, int chunkIndex) {}
}

package com.knowledgebase.domain.model;

import java.util.List;

/** Immutable comparison result for two registered versions of one document. */
public record DocumentDiff(Long documentId, String fromHash, String toHash, List<DiffLine> lines) {
    public DocumentDiff {
        lines = List.copyOf(lines);
    }
}

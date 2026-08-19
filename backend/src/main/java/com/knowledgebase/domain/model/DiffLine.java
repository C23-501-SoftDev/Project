package com.knowledgebase.domain.model;

/** A single, unrendered line in a document diff. */
public record DiffLine(DiffLineType type, Integer beforeLineNumber, Integer afterLineNumber, String content) {
}

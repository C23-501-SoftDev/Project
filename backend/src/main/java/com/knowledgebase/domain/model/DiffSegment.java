package com.knowledgebase.domain.model;

/** A fragment of a line whose display state is independent of the whole line. */
public record DiffSegment(DiffSegmentType type, String content) {
    public DiffSegment {
        content = content == null ? "" : content;
    }
}

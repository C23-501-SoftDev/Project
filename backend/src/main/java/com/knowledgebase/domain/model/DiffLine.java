package com.knowledgebase.domain.model;

import java.util.List;

/** A single, unrendered line in a document diff. */
public record DiffLine(DiffLineType type, Integer beforeLineNumber, Integer afterLineNumber, String content,
                       List<DiffSegment> segments) {
    public DiffLine {
        content = content == null ? "" : content;
        segments = List.copyOf(segments == null ? List.of() : segments);
    }

    public DiffLine(DiffLineType type, Integer beforeLineNumber, Integer afterLineNumber, String content) {
        this(type, beforeLineNumber, afterLineNumber, content,
                List.of(new DiffSegment(switch (type) {
                    case CONTEXT -> DiffSegmentType.UNCHANGED;
                    case REMOVED -> DiffSegmentType.REMOVED;
                    case ADDED -> DiffSegmentType.ADDED;
                    case MODIFIED -> DiffSegmentType.UNCHANGED;
                }, content)));
    }
}

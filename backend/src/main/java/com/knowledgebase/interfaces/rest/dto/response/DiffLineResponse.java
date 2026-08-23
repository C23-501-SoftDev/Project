package com.knowledgebase.interfaces.rest.dto.response;

/** JSON representation of a single line in a document version diff. */
public record DiffLineResponse(String type, Integer beforeLineNumber, Integer afterLineNumber, String content,
                               java.util.List<DiffSegmentResponse> segments) {
}

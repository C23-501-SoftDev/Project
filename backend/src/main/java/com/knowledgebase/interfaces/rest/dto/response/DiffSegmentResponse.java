package com.knowledgebase.interfaces.rest.dto.response;

/** JSON representation of a highlighted fragment in a diff line. */
public record DiffSegmentResponse(String type, String content) {
}

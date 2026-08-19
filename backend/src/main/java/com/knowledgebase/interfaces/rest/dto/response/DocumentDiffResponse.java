package com.knowledgebase.interfaces.rest.dto.response;

import java.util.List;

/** JSON representation of the comparison of two versions of one document. */
public record DocumentDiffResponse(Long documentId, String fromHash, String toHash, List<DiffLineResponse> lines) {
}

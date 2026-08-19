package com.knowledgebase.interfaces.rest.dto.response;

import java.time.LocalDateTime;

public record DocumentVersionResponse(
        String gitHash,
        String comment,
        LocalDateTime createdAt) {
}

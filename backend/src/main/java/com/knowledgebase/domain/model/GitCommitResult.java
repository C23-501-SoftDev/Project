package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/** Immutable details of a commit created while persisting a document. */
public record GitCommitResult(String hash, LocalDateTime committedAt) {
}

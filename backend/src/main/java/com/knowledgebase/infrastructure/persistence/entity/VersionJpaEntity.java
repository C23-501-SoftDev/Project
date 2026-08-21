package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "versions")
public class VersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "git_hash", nullable = false, length = 40)
    private String gitHash;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "restored_from_version_id")
    private Long restoredFromVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public VersionJpaEntity() {}

    public VersionJpaEntity(Long documentId, String gitHash, Long authorId, String comment) {
        this.documentId = documentId;
        this.gitHash = gitHash;
        this.authorId = authorId;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getGitHash() { return gitHash; }
    public void setGitHash(String gitHash) { this.gitHash = gitHash; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
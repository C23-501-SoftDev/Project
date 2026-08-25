package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA-сущность документа.
 * Отражает таблицу documents в PostgreSQL.
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_documents_author", columnList = "author_id"),
    @Index(name = "idx_documents_space_status", columnList = "space_id, status"),
    @Index(name = "idx_documents_sibling_order", columnList = "space_id, parent_document_id, sort_order, id"),
    @Index(name = "uq_documents_git_file_path", columnList = "git_file_path", unique = true)
})
public class DocumentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "git_file_path", nullable = false, unique = true, length = 500)
    private String gitFilePath;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "parent_document_id")
    private Long parentDocumentId;

    @Column(name = "previous_parent_id")
    private Long previousParentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getParentDocumentId() { return parentDocumentId; }
    public void setParentDocumentId(Long parentDocumentId) { this.parentDocumentId = parentDocumentId; }

    public Long getPreviousParentId() { return previousParentId; }
    public void setPreviousParentId(Long previousParentId) { this.previousParentId = previousParentId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DocumentJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGitFilePath() { return gitFilePath; }
    public void setGitFilePath(String gitFilePath) { this.gitFilePath = gitFilePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

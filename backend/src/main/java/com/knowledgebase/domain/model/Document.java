package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель документа.
 */
public class Document {

    private Long id;
    private String title;
    private String gitFilePath;
    private DocumentStatus status;
    private DocumentStatus previousStatus;
    private Long authorId;
    private Long spaceId;
    private Long templateId;
    private Long parentDocumentId;
    private Long previousParentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deletedWithSpace = false;

    private Document() {}

    /**
     * Фабричный метод для создания нового документа.
     */
    public static Document create(String title, Long authorId, Long spaceId, String gitFilePath, Long templateId) {
        Document document = new Document();
        document.title = title;
        document.authorId = authorId;
        document.spaceId = spaceId;
        document.gitFilePath = gitFilePath;
        document.templateId = templateId;
        document.status = DocumentStatus.DRAFT;
        document.createdAt = LocalDateTime.now();
        document.updatedAt = LocalDateTime.now();
        return document;
    }

    /**
     * Фабричный метод для восстановления документа из хранилища.
     */
    public static Document restore(Long id, String title, String gitFilePath, DocumentStatus status,
                                   DocumentStatus previousStatus,
                                   Long authorId, Long spaceId, Long templateId, Long parentDocumentId,
                                   Long previousParentId,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        Document document = new Document();
        document.id = id;
        document.title = title;
        document.gitFilePath = gitFilePath;
        document.status = status;
        document.previousStatus = previousStatus;
        document.authorId = authorId;
        document.spaceId = spaceId;
        document.templateId = templateId;
        document.parentDocumentId = parentDocumentId;
        document.previousParentId = previousParentId;
        document.createdAt = createdAt;
        document.updatedAt = updatedAt;
        return document;
    }

    // Бизнес-логика

    public void updateMetadata(String title, DocumentStatus status) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Обновляет путь к файлу в Git.
     */
    public void updateGitFilePath(String gitFilePath) {
        this.gitFilePath = gitFilePath;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive(String archivedGitPath) {
        this.previousStatus = this.status;
        this.previousParentId = this.parentDocumentId;
        this.status = DocumentStatus.DELETED;
        this.gitFilePath = archivedGitPath;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive(String archivedGitPath, Long originalParentId) {
        this.previousStatus = this.status;
        this.previousParentId = originalParentId;
        this.status = DocumentStatus.DELETED;
        this.gitFilePath = archivedGitPath;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsDeletedWithSpace(boolean deletedWithSpace) {
        this.deletedWithSpace = deletedWithSpace;
    }

    public boolean isDeletedWithSpace() {
        return deletedWithSpace;
    }

    public void restore(String originalGitPath, Long restoredParentId) {
        if (this.previousStatus != null) {
            this.status = this.previousStatus;
        } else {
            this.status = DocumentStatus.DRAFT;
        }
        if (restoredParentId != null) {
            this.parentDocumentId = restoredParentId;
        }
        this.previousStatus = null;
        this.previousParentId = null;
        this.gitFilePath = originalGitPath;
        this.updatedAt = LocalDateTime.now();
        this.deletedWithSpace = false;
    }

    public void restore(String originalGitPath) {
        restore(originalGitPath, this.previousParentId);
    }

    public void moveToSpace(Long newSpaceId) {
        this.spaceId = newSpaceId;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getGitFilePath() { return gitFilePath; }
    public DocumentStatus getStatus() { return status; }
    public DocumentStatus getPreviousStatus() { return previousStatus; }
    public Long getAuthorId() { return authorId; }
    public Long getSpaceId() { return spaceId; }
    public Long getTemplateId() { return templateId; }
    public Long getParentDocumentId() { return parentDocumentId; }
    public Long getPreviousParentId() { return previousParentId; }

    public void setParentDocumentId(Long parentDocumentId) {
        this.parentDocumentId = parentDocumentId;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPreviousParentId(Long previousParentId) {
        this.previousParentId = previousParentId;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}


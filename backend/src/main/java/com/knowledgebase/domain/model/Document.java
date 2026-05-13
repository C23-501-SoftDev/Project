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
    private Long authorId;
    private Long spaceId;
    private Long templateId;
    private Long parentDocumentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Document() {}

    /**
     * Фабричный метод для создания нового документа.
     */
    public static Document create(String title, Long authorId, Long spaceId, String gitFilePath) {
        Document document = new Document();
        document.title = title;
        document.authorId = authorId;
        document.spaceId = spaceId;
        document.gitFilePath = gitFilePath;
        document.status = DocumentStatus.DRAFT;
        document.createdAt = LocalDateTime.now();
        document.updatedAt = LocalDateTime.now();
        return document;
    }

    /**
     * Фабричный метод для восстановления документа из хранилища.
     */
    public static Document restore(Long id, String title, String gitFilePath, DocumentStatus status,
                                   Long authorId, Long spaceId, Long templateId, Long parentDocumentId,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        Document document = new Document();
        document.id = id;
        document.title = title;
        document.gitFilePath = gitFilePath;
        document.status = status;
        document.authorId = authorId;
        document.spaceId = spaceId;
        document.templateId = templateId;
        document.parentDocumentId = parentDocumentId;
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
        this.status = DocumentStatus.DELETED;
        this.gitFilePath = archivedGitPath;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getGitFilePath() { return gitFilePath; }
    public DocumentStatus getStatus() { return status; }
    public Long getAuthorId() { return authorId; }
    public Long getSpaceId() { return spaceId; }
    public Long getTemplateId() { return templateId; }
    public Long getParentDocumentId() { return parentDocumentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

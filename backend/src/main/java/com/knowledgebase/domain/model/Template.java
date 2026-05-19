package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель шаблона документа.
 */
public class Template {
    private Long id;
    private String name;
    private String description;
    private String content;
    private String role;
    private boolean isSystem;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Template(Long id, String name, String description, String content, String role, boolean isSystem, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.content = content;
        this.role = role;
        this.isSystem = isSystem;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public String getRole() { return role; }
    public boolean isSystem() { return isSystem; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

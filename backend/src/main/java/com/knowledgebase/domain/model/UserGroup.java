package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Группа пользователей (Domain Layer) — US4.1.8.
 *
 * Используется для массового назначения прав на пространства:
 * права, выданные группе, действуют на всех её участников.
 */
public class UserGroup {

    private final Long id;
    private String name;
    private String description;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private UserGroup(Long id, String name, String description,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Фабричный метод создания новой группы.
     */
    public static UserGroup create(String name, String description) {
        validateName(name);
        LocalDateTime now = LocalDateTime.now();
        return new UserGroup(null, name.trim(), description, now, now);
    }

    /**
     * Восстановление группы из хранилища.
     */
    public static UserGroup restore(Long id, String name, String description,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new UserGroup(id, name, description, createdAt, updatedAt);
    }

    /**
     * Обновляет название и описание группы.
     */
    public void update(String name, String description) {
        validateName(name);
        this.name = name.trim();
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название группы не может быть пустым");
        }
        if (name.trim().length() > 200) {
            throw new IllegalArgumentException("Название группы не может быть длиннее 200 символов");
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

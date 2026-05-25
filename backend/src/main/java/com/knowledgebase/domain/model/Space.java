package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель пространства документов.
 *
 * Пространство — логическая группировка документов с едиными настройками прав доступа.
 * Каждое пространство имеет владельца (owner), который получает право OWNER автоматически.
 *
 * Бизнес-правила:
 * - Название пространства уникально в системе
 * - Нельзя удалить пространство, если в нём есть документы (ON DELETE RESTRICT)
 * - Пространства не поддерживают soft-удаление
 */
public class Space {

    private Long id;

    /** Уникальное название пространства */
    private String name;

    private String description;

    /** ID пользователя-владельца */
    private Long ownerId;

    /** Флаг soft-удаления */
    private boolean isDeleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Space() {}

    /**
     * Фабричный метод для создания нового пространства.
     *
     * @param name        уникальное название
     * @param description описание пространства
     * @param ownerId     ID пользователя-владельца
     * @return новый экземпляр Space
     */
    public static Space create(String name, String description, Long ownerId) {
        Space space = new Space();
        space.name = name;
        space.description = description;
        space.ownerId = ownerId;
        space.isDeleted = false;
        space.createdAt = LocalDateTime.now();
        space.updatedAt = LocalDateTime.now();
        return space;
    }

    /**
     * Фабричный метод для восстановления пространства из хранилища.
     */
    public static Space restore(Long id, String name, String description, Long ownerId,
                                boolean isDeleted,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        Space space = new Space();
        space.id = id;
        space.name = name;
        space.description = description;
        space.ownerId = ownerId;
        space.isDeleted = isDeleted;
        space.createdAt = createdAt;
        space.updatedAt = updatedAt;
        return space;
    }

    /** Мягкое удаление пространства */
    public void softDelete() {
        this.isDeleted = true;
        this.updatedAt = LocalDateTime.now();
    }

    /** Проверяет, удалено ли пространство */
    public boolean isDeleted() {
        return isDeleted;
    }

    /** Обновляет информацию о пространстве */
    public void update(String name, String description, Long ownerId) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.updatedAt = LocalDateTime.now();
    }

    /** Обновляет описание пространства */
    public void updateDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /** Передаёт права владения другому пользователю */
    public void transferOwnership(Long newOwnerId) {
        this.ownerId = newOwnerId;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getOwnerId() { return ownerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Space{id=" + id + ", name='" + name + "', ownerId=" + ownerId + "}";
    }
}

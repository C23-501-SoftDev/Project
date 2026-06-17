package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель группы пользователей.
 *
 * Группа — именованное объединение пользователей, созданное администратором
 * для упрощения массового назначения прав доступа на пространства.
 *
 * Это чистый объект домена (POJO), не зависящий от фреймворков и инфраструктуры.
 *
 * Бизнес-правила:
 * - Название группы уникально в системе
 * - Группа не имеет владельца — управление централизовано через администратора
 * - При удалении группы каскадно удаляются её состав и права на пространства
 */
public class UserGroup {

    private Long id;

    /** Уникальное название группы */
    private String name;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Приватный конструктор — используйте фабричные методы
    private UserGroup() {}

    /**
     * Фабричный метод для создания новой группы.
     *
     * @param name        уникальное название группы
     * @param description описание группы (может быть null)
     * @return новый экземпляр UserGroup
     */
    public static UserGroup create(String name, String description) {
        UserGroup group = new UserGroup();
        group.name = name;
        group.description = description;
        group.createdAt = LocalDateTime.now();
        group.updatedAt = LocalDateTime.now();
        return group;
    }

    /**
     * Фабричный метод для восстановления группы из хранилища.
     * Используется в маппере при чтении из БД.
     */
    public static UserGroup restore(Long id, String name, String description,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        UserGroup group = new UserGroup();
        group.id = id;
        group.name = name;
        group.description = description;
        group.createdAt = createdAt;
        group.updatedAt = updatedAt;
        return group;
    }

    /** Обновляет название и описание группы */
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters — изменение только через методы домена

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "UserGroup{id=" + id + ", name='" + name + "'}";
    }
}

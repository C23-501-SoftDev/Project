package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель пользователя системы.
 *
 * Это чистый объект домена (Plain Old Java Object), не зависящий от
 * фреймворков и инфраструктуры. Содержит только бизнес-логику и данные.
 *
 * Согласно Clean Architecture:
 * - Не содержит JPA/Hibernate аннотаций
 * - Не зависит от Spring или других фреймворков
 * - Конструируется через фабричный метод create()
 */
public class User {

    private Long id;

    /** Уникальный логин для входа в систему */
    private String login;

    /** Хеш пароля (BCrypt) — никогда не хранится в открытом виде */
    private String passwordHash;

    /** Уникальный email пользователя */
    private String email;

    /** Глобальная роль: ADMIN, EDITOR, READER */
    private GlobalRole role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Приватный конструктор — используйте фабричные методы
    private User() {}

    /**
     * Фабричный метод для создания нового пользователя.
     * Устанавливает дефолтную роль READER и временные метки.
     *
     * @param login      уникальный логин
     * @param passwordHash хеш пароля (BCrypt)
     * @param email      уникальный email
     * @param role       глобальная роль
     * @return новый экземпляр User
     */
    public static User create(String login, String passwordHash, String email, GlobalRole role) {
        User user = new User();
        user.login = login;
        user.passwordHash = passwordHash;
        user.email = email;
        user.role = role != null ? role : GlobalRole.READER;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    /**
     * Фабричный метод для восстановления пользователя из хранилища.
     * Используется в маппере при чтении из БД.
     */
    public static User restore(Long id, String login, String passwordHash, String email,
                               GlobalRole role, LocalDateTime createdAt, LocalDateTime updatedAt) {
        User user = new User();
        user.id = id;
        user.login = login;
        user.passwordHash = passwordHash;
        user.email = email;
        user.role = role;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        return user;
    }

    // Методы бизнес-логики

    /** Проверяет, является ли пользователь администратором */
    public boolean isAdmin() {
        return GlobalRole.ADMIN.equals(this.role);
    }

    /** Проверяет, является ли пользователь редактором */
    public boolean isEditor() {
        return GlobalRole.EDITOR.equals(this.role);
    }

    /** Обновляет данные профиля пользователя */
    public void updateProfile(String login, String email, GlobalRole role) {
        if (login != null && !login.isBlank()) {
            this.login = login;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        if (role != null) {
            this.role = role;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /** Обновляет хеш пароля пользователя */
    public void updatePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters — нет сеттеров, изменение только через методы домена

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public GlobalRole getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", login='" + login + "', role=" + role + "}";
    }
}

package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA-сущность пользователя.
 *
 * Это инфраструктурный класс, отражающий таблицу users в PostgreSQL.
 * Не используется напрямую в бизнес-логике — только через доменный класс User.
 *
 * Маппинг: UserJpaEntity ↔ User (domain) выполняется в UserJpaMapper.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "uq_users_login", columnList = "login", unique = true),
    @Index(name = "uq_users_email", columnList = "email", unique = true)
})
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true, length = 100)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Роль хранится как строка (Admin, Editor, Reader).
     * Не используем @Enumerated(EnumType.STRING) напрямую т.к.
     * enum называется GlobalRole, а в БД значения с заглавной буквы (Admin, не ADMIN).
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // JPA требует конструктор без аргументов
    public  UserJpaEntity() {}

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

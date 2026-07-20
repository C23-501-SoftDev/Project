package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA-сущность записи журнала аудита (US4.1.5).
 * Отражает таблицу audit_log в PostgreSQL.
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_log_user", columnList = "user_id"),
    @Index(name = "idx_audit_log_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_log_action_type", columnList = "action_type")
})
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_login", length = 100)
    private String userLogin;

    @Column(name = "action_type", nullable = false, length = 60)
    private String actionType;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public AuditLogJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserLogin() { return userLogin; }
    public void setUserLogin(String userLogin) { this.userLogin = userLogin; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}

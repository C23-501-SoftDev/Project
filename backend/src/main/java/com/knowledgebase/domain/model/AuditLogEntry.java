package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Запись журнала аудита (Domain Layer) — US4.1.5.
 *
 * Фиксирует критическое действие пользователя в системе:
 * кто, когда, что сделал, над каким ресурсом и с какого IP-адреса.
 */
public class AuditLogEntry {

    private final Long id;
    private final LocalDateTime createdAt;
    private final Long userId;
    private final String userLogin;
    private final String actionType;
    private final String resourceType;
    private final Long resourceId;
    private final String details;
    private final String ipAddress;

    private AuditLogEntry(Long id, LocalDateTime createdAt, Long userId, String userLogin,
                          String actionType, String resourceType, Long resourceId,
                          String details, String ipAddress) {
        this.id = id;
        this.createdAt = createdAt;
        this.userId = userId;
        this.userLogin = userLogin;
        this.actionType = actionType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    /**
     * Фабричный метод для новой записи аудита.
     */
    public static AuditLogEntry create(Long userId, String userLogin, String actionType,
                                       String resourceType, Long resourceId,
                                       String details, String ipAddress) {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType обязателен для записи аудита");
        }
        return new AuditLogEntry(null, LocalDateTime.now(), userId, userLogin,
                actionType, resourceType, resourceId, details, ipAddress);
    }

    /**
     * Восстановление записи из хранилища.
     */
    public static AuditLogEntry restore(Long id, LocalDateTime createdAt, Long userId, String userLogin,
                                        String actionType, String resourceType, Long resourceId,
                                        String details, String ipAddress) {
        return new AuditLogEntry(id, createdAt, userId, userLogin,
                actionType, resourceType, resourceId, details, ipAddress);
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getUserId() { return userId; }
    public String getUserLogin() { return userLogin; }
    public String getActionType() { return actionType; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
}

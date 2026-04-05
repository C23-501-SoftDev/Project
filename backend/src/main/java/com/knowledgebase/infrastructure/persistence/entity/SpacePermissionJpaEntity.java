package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA-сущность права доступа к пространству.
 * Отражает таблицу space_permissions в PostgreSQL.
 */
@Entity
@Table(name = "space_permissions",
    indexes = {
        @Index(name = "idx_space_permissions_space", columnList = "space_id"),
        @Index(name = "idx_space_permissions_user", columnList = "user_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_space_permissions",
            columnNames = {"space_id", "user_id", "permission_type"})
    }
)
public class SpacePermissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Тип права: READ, WRITE, OWNER.
     * Хранится как строка в верхнем регистре.
     */
    @Column(name = "permission_type", nullable = false, length = 20)
    private String permissionType;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    public SpacePermissionJpaEntity() {}

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
}

package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA-сущность права группы на пространство (US4.2.2).
 * Отражает таблицу space_group_permissions в PostgreSQL.
 */
@Entity
@Table(name = "space_group_permissions",
    indexes = {
        @Index(name = "idx_space_group_permissions_space", columnList = "space_id"),
        @Index(name = "idx_space_group_permissions_group", columnList = "group_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_space_group_permissions",
            columnNames = {"space_id", "group_id", "permission_type"})
    }
)
public class SpaceGroupPermissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /**
     * Тип права: READ, WRITE, OWNER.
     */
    @Column(name = "permission_type", nullable = false, length = 20)
    private String permissionType;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    public SpaceGroupPermissionJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
}

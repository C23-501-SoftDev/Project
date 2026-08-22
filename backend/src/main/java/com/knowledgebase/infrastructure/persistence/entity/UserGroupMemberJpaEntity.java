package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA-сущность членства пользователя в группе (US4.1.9).
 * Отражает таблицу user_group_members в PostgreSQL.
 */
@Entity
@Table(name = "user_group_members",
    indexes = {
        @Index(name = "idx_user_group_members_group", columnList = "group_id"),
        @Index(name = "idx_user_group_members_user", columnList = "user_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_group_members", columnNames = {"group_id", "user_id"})
    }
)
public class UserGroupMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public UserGroupMemberJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}

package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.GroupMember;

import java.util.List;

/**
 * Интерфейс репозитория членства в группах (Domain Layer) — US4.1.9.
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.GroupMemberRepositoryImpl}
 */
public interface GroupMemberRepository {

    /**
     * Сохраняет членство пользователя в группе.
     */
    GroupMember save(GroupMember member);

    /**
     * Проверяет, состоит ли пользователь в группе.
     */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Возвращает всех участников группы.
     */
    List<GroupMember> findByGroupId(Long groupId);

    /**
     * Возвращает ID групп, в которых состоит пользователь.
     */
    List<Long> findGroupIdsByUserId(Long userId);

    /**
     * Возвращает количество участников группы.
     */
    long countByGroupId(Long groupId);

    /**
     * Удаляет пользователя из группы.
     */
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Удаляет всех участников группы (при удалении группы).
     */
    void deleteByGroupId(Long groupId);
}

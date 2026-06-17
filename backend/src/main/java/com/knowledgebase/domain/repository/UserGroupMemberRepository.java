package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.UserGroupMember;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория членства в группах (Domain Layer).
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.UserGroupMemberRepositoryImpl}
 */
public interface UserGroupMemberRepository {

    /**
     * Сохраняет членство (добавление пользователя в группу).
     */
    UserGroupMember save(UserGroupMember member);

    /**
     * Возвращает состав группы (всех её участников).
     */
    List<UserGroupMember> findByGroupId(Long groupId);

    /**
     * Возвращает все группы, в которых состоит пользователь.
     */
    List<UserGroupMember> findByUserId(Long userId);

    /**
     * Находит конкретное членство по паре группа + пользователь.
     */
    Optional<UserGroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Проверяет, состоит ли пользователь в группе.
     */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Возвращает количество участников группы.
     */
    long countByGroupId(Long groupId);

    /**
     * Удаляет членство пользователя в группе.
     */
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Удаляет весь состав группы (используется при удалении группы).
     */
    void deleteByGroupId(Long groupId);
}

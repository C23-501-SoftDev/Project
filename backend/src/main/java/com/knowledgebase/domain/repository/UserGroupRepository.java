package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.UserGroup;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория групп пользователей (Domain Layer) — US4.1.8.
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.UserGroupRepositoryImpl}
 */
public interface UserGroupRepository {

    /**
     * Сохраняет группу.
     */
    UserGroup save(UserGroup group);

    /**
     * Находит группу по ID.
     */
    Optional<UserGroup> findById(Long id);

    /**
     * Проверяет, существует ли группа с данным названием.
     */
    boolean existsByName(String name);

    /**
     * Проверяет, существует ли другая группа с данным названием (для обновления).
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Возвращает все группы с пагинацией (сортировка по названию).
     */
    List<UserGroup> findAll(int page, int size);

    /**
     * Возвращает общее количество групп.
     */
    long count();

    /**
     * Удаляет группу по ID.
     */
    void deleteById(Long id);
}

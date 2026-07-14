package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.UserGroup;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория групп пользователей (Domain Layer).
 *
 * Определяет контракт работы с группами независимо от технологии хранения.
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.UserGroupRepositoryImpl}
 */
public interface UserGroupRepository {

    /**
     * Сохраняет группу (создание или обновление).
     * @param group группа для сохранения
     * @return сохранённая группа с присвоенным ID
     */
    UserGroup save(UserGroup group);

    /**
     * Находит группу по ID.
     */
    Optional<UserGroup> findById(Long id);

    /**
     * Находит группу по уникальному имени.
     */
    Optional<UserGroup> findByName(String name);

    /**
     * Возвращает все группы с пагинацией.
     * @param page номер страницы (0-based)
     * @param size размер страницы
     */
    List<UserGroup> findAll(int page, int size);

    /**
     * Возвращает общее количество групп.
     */
    long count();

    /**
     * Проверяет, существует ли группа с данным именем.
     */
    boolean existsByName(String name);

    /**
     * Проверяет, существует ли группа с данным именем, исключая указанный ID.
     * Используется при проверке уникальности во время обновления.
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Удаляет группу по ID.
     */
    void deleteById(Long id);
}

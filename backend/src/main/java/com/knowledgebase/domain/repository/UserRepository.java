package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория пользователей (Domain Layer).
 *
 * Определяет контракт для работы с пользователями, не зависящий от
 * конкретной технологии хранения данных (JPA, MongoDB, etc.).
 *
 * Реализация находится в infrastructure слое:
 * @see com.knowledgebase.infrastructure.persistence.repository.UserRepositoryImpl
 *
 * Паттерн Repository позволяет заменить реализацию без изменения бизнес-логики.
 */
public interface UserRepository {

    /**
     * Сохраняет пользователя (создание или обновление).
     * @param user пользователь для сохранения
     * @return сохранённый пользователь с присвоенным ID
     */
    User save(User user);

    /**
     * Находит пользователя по ID.
     * @param id уникальный идентификатор
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findById(Long id);

    /**
     * Находит пользователя по логину.
     * Используется при аутентификации.
     * @param login логин пользователя
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findByLogin(String login);

    /**
     * Находит пользователя по email.
     * Используется при проверке уникальности email.
     * @param email адрес электронной почты
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findByEmail(String email);

    /**
     * Возвращает всех пользователей с пагинацией.
     * @param page  номер страницы (0-based)
     * @param size  размер страницы
     * @param sortBy поле для сортировки
     * @param sortDir направление сортировки (asc/desc)
     * @return список пользователей
     */
    List<User> findAll(int page, int size, String sortBy, String sortDir);

    /**
     * Возвращает общее количество пользователей.
     * Используется для пагинации.
     */
    long count();

    /**
     * Удаляет пользователя по ID.
     * @param id ID пользователя
     */
    void deleteById(Long id);

    /**
     * Проверяет, существует ли пользователь с данным логином.
     * @param login логин для проверки
     * @return true если существует
     */
    boolean existsByLogin(String login);

    /**
     * Проверяет, существует ли пользователь с данным email.
     * @param email email для проверки
     * @return true если существует
     */
    boolean existsByEmail(String email);

    /**
     * Проверяет, является ли пользователь автором документов.
     * Используется при проверке возможности удаления (ON DELETE RESTRICT).
     * @param userId ID пользователя
     * @return true если у пользователя есть документы
     */
    boolean hasDocuments(Long userId);

    /**
     * Проверяет, является ли пользователь владельцем пространств.
     * Используется при проверке возможности удаления (ON DELETE RESTRICT).
     * @param userId ID пользователя
     * @return true если пользователь владеет хотя бы одним пространством
     */
    boolean hasOwnedSpaces(Long userId);

    /**
     * Проверяет, создавал ли пользователь версии документов.
     * Используется при проверке возможности удаления (ON DELETE RESTRICT).
     * @param userId ID пользователя
     * @return true если у пользователя есть версии
     */
    boolean hasVersions(Long userId);
}

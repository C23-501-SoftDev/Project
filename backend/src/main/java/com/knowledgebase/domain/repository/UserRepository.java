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
     * Находит активного пользователя по ID (is_deleted = false).
     * @param id уникальный идентификатор
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findById(Long id);

    /**
     * Находит пользователя по ID, включая удалённых.
     * @param id уникальный идентификатор
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findByIdIncludingDeleted(Long id);

    /**
     * Находит пользователя по логину, включая удалённых.
     * @param login логин пользователя
     * @return Optional с пользователем или пустой Optional
     */
    Optional<User> findByLoginIncludingDeleted(String login);

    /**
     * Находит активного пользователя по логину (is_deleted = false).
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
     * Возвращает всех активных пользователей (is_deleted = false) с пагинацией.
     * @param page  номер страницы (0-based)
     * @param size  размер страницы
     * @param sortBy поле для сортировки
     * @param sortDir направление сортировки (asc/desc)
     * @return список пользователей
     */
    List<User> findAllActive(int page, int size, String sortBy, String sortDir);

    /**
     * Возвращает всех пользователей, включая удалённых, с пагинацией.
     * @param page  номер страницы (0-based)
     * @param size  размер страницы
     * @param sortBy поле для сортировки
     * @param sortDir направление сортировки (asc/desc)
     * @return список пользователей
     */
    List<User> findAllIncludingDeleted(int page, int size, String sortBy, String sortDir);

    /**
     * Возвращает общее количество активных пользователей (is_deleted = false).
     * Используется для пагинации.
     */
    long countActive();

    /**
     * Возвращает общее количество пользователей, включая удалённых.
     */
    long countAll();

    /**
     * Проверяет, существует ли активный пользователь с данным логином (is_deleted = false).
     * @param login логин для проверки
     * @return true если существует
     */
    boolean existsByLogin(String login);

    /**
     * Проверяет, существует ли пользователь с данным логином, включая удалённых.
     * @param login логин для проверки
     * @return true если существует
     */
    boolean existsByLoginIncludingDeleted(String login);

    /**
     * Проверяет, существует ли активный пользователь с данным email (is_deleted = false).
     * @param email email для проверки
     * @return true если существует
     */
    boolean existsByEmail(String email);

    /**
     * Проверяет, существует ли пользователь с данным email, включая удалённых.
     * @param email email для проверки
     * @return true если существует
     */
    boolean existsByEmailIncludingDeleted(String email);

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

    /**
    /**
     * Находит активных пользователей по списку ID.
     * @param ids список ID пользователей
     * @return список активных пользователей
     */
    List<User> findActiveByIds(List<Long> ids);

    /**
     * Возвращает пользователей с применением фильтров и пагинацией.
     * @param page номер страницы (0-based)
     * @param size размер страницы
     * @param sortBy поле сортировки
     * @param sortDir направление сортировки
     * @param includeDeleted включать ли удалённых пользователей (null=active only, true=all, false=deleted only)
     * @param roles фильтр по ролям (null или пустой = без фильтра)
     * @param search поиск по логину/email (null или пустой = без фильтра)
     * @return список пользователей
     */
    List<User> findAllWithFilters(int page, int size, String sortBy, String sortDir, Boolean includeDeleted, List<String> roles, String search);

    /**
     * Возвращает общее количество пользователей с применением фильтров.
     * @param includeDeleted включать ли удалённых пользователей
     * @param roles фильтр по ролям
     * @param search поиск по логину/email
     * @return количество пользователей
     */
    long countWithFilters(Boolean includeDeleted, List<String> roles, String search);
}

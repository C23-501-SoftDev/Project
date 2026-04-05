package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpacePermission;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория прав доступа к пространствам (Domain Layer).
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.SpacePermissionRepositoryImpl}
 */
public interface SpacePermissionRepository {

    /**
     * Сохраняет право доступа.
     */
    SpacePermission save(SpacePermission permission);

    /**
     * Находит право по ID.
     */
    Optional<SpacePermission> findById(Long id);

    /**
     * Возвращает все права пользователя в конкретном пространстве.
     * Используется в эндпоинте GET /api/user/permissions?spaceId={id}
     *
     * @param spaceId ID пространства
     * @param userId  ID пользователя
     * @return список прав (может содержать READ, WRITE, OWNER)
     */
    List<SpacePermission> findBySpaceIdAndUserId(Long spaceId, Long userId);

    /**
     * Возвращает все права пользователя во всех пространствах.
     * Используется в эндпоинте GET /api/user/spaces
     *
     * @param userId ID пользователя
     * @return список всех прав
     */
    List<SpacePermission> findByUserId(Long userId);

    /**
     * Возвращает все права для конкретного пространства.
     * Используется при отображении списка участников пространства.
     *
     * @param spaceId ID пространства
     */
    List<SpacePermission> findBySpaceId(Long spaceId);

    /**
     * Проверяет, имеет ли пользователь конкретное право на пространство.
     *
     * @param spaceId        ID пространства
     * @param userId         ID пользователя
     * @param permissionType тип права для проверки
     */
    boolean existsBySpaceIdAndUserIdAndPermissionType(Long spaceId, Long userId,
                                                      PermissionType permissionType);

    /**
     * Проверяет, имеет ли пользователь право на запись (WRITE или OWNER) в пространстве.
     * Оптимизированный метод для проверки прав редактирования.
     *
     * @param spaceId ID пространства
     * @param userId  ID пользователя
     * @return true если пользователь имеет WRITE или OWNER право
     */
    boolean hasWriteAccess(Long spaceId, Long userId);

    /**
     * Проверяет, имеет ли пользователь право на чтение (READ, WRITE или OWNER) в пространстве.
     *
     * @param spaceId ID пространства
     * @param userId  ID пользователя
     * @return true если пользователь имеет хотя бы одно право
     */
    boolean hasReadAccess(Long spaceId, Long userId);

    /**
     * Удаляет право доступа по ID.
     */
    void deleteById(Long id);

    /**
     * Удаляет все права пользователя в пространстве.
     * Используется при отзыве всех прав сразу.
     */
    void deleteBySpaceIdAndUserId(Long spaceId, Long userId);
}

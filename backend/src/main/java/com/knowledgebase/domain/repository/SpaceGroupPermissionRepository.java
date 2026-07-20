package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.SpaceGroupPermission;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория прав групп на пространства (Domain Layer) — US4.2.2.
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.SpaceGroupPermissionRepositoryImpl}
 */
public interface SpaceGroupPermissionRepository {

    /**
     * Сохраняет право группы.
     */
    SpaceGroupPermission save(SpaceGroupPermission permission);

    /**
     * Находит право по ID.
     */
    Optional<SpaceGroupPermission> findById(Long id);

    /**
     * Возвращает все права групп для пространства.
     */
    List<SpaceGroupPermission> findBySpaceId(Long spaceId);

    /**
     * Возвращает все права пространства для конкретной группы.
     */
    List<SpaceGroupPermission> findByGroupId(Long groupId);

    /**
     * Возвращает права групп, в которых состоит пользователь (join через членство).
     * Используется при построении списка доступных пространств.
     */
    List<SpaceGroupPermission> findByMemberUserId(Long userId);

    /**
     * Проверяет наличие конкретного права у группы.
     */
    boolean existsBySpaceIdAndGroupIdAndPermissionType(Long spaceId, Long groupId, PermissionType permissionType);

    /**
     * Проверяет, имеет ли пользователь право на чтение пространства через какую-либо группу.
     */
    boolean hasReadAccessViaGroups(Long spaceId, Long userId);

    /**
     * Проверяет, имеет ли пользователь право на запись (WRITE/OWNER) через какую-либо группу.
     */
    boolean hasWriteAccessViaGroups(Long spaceId, Long userId);

    /**
     * Возвращает типы прав пользователя в пространстве, полученные через группы.
     */
    List<PermissionType> findTypesBySpaceIdAndMemberUserId(Long spaceId, Long userId);

    /**
     * Удаляет право по ID.
     */
    void deleteById(Long id);

    /**
     * Удаляет конкретное право группы в пространстве.
     */
    void deleteBySpaceIdAndGroupIdAndPermissionType(Long spaceId, Long groupId, PermissionType permissionType);

    /**
     * Удаляет все права группы (при удалении группы).
     */
    void deleteByGroupId(Long groupId);

    /**
     * Удаляет все права групп для пространства (при полном удалении пространства).
     */
    void deleteBySpaceId(Long spaceId);
}

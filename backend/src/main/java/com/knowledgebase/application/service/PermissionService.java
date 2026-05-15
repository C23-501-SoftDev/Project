package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис проверки прав доступа (Application Layer).
 *
 * Реализует логику RBAC из US4.1.3:
 *
 * | Флаг isAdmin | /api/admin/** | Создание/редактирование | Чтение |
 * |--------------|--------------|------------------------|--------|
 * | true         | всегда       | всегда                 | всегда |
 * | false        | никогда      | WRITE/OWNER в space    | любое право в space |
 *
 * Используется в @PreAuthorize выражениях и напрямую в сервисах.
 */
@Service("permissionService")
@Transactional(readOnly = true)
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final SpacePermissionRepository permissionRepository;

    public PermissionService(SpacePermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /**
     * Проверяет, может ли пользователь создавать/редактировать документы в пространстве.
     *
     * Логика:
     * - isAdmin=true → всегда true
     * - EDITOR → true если есть WRITE или OWNER в пространстве
     * - READER/GUEST → всегда false
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return true если операция разрешена
     */
    public boolean canWrite(Long userId, boolean isAdmin, Long spaceId) {
        if (isAdmin) {
            return true;
        }
        if (spaceId == null) {
            return false;
        }
        // EDITOR/READER/GUEST: проверяем права на пространство
        return permissionRepository.hasWriteAccess(spaceId, userId);
    }

    /**
     * Проверяет, может ли пользователь читать документы в пространстве.
     *
     * Логика:
     * - isAdmin=true → всегда true
     * - EDITOR/READER → true если есть хоть одно право (READ, WRITE, OWNER)
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return true если чтение разрешено
     */
    public boolean canRead(Long userId, boolean isAdmin, Long spaceId) {
        if (isAdmin) {
            return true;
        }
        if (spaceId == null) {
            return false;
        }
        // EDITOR и READER: нужно явное право на пространство
        return permissionRepository.hasReadAccess(spaceId, userId);
    }

    /**
     * Возвращает список типов прав пользователя в пространстве.
     * Используется в GET /api/user/permissions?spaceId={id}
     *
     * Для ADMIN возвращает [READ, WRITE, OWNER] — полный набор прав.
     * Для остальных — фактические права из space_permissions.
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return список прав
     */
    public List<PermissionType> getUserPermissions(Long userId, boolean isAdmin, Long spaceId) {
        if (isAdmin) {
            // ADMIN имеет все права неявно
            return List.of(PermissionType.READ, PermissionType.WRITE, PermissionType.OWNER);
        }

        return permissionRepository.findBySpaceIdAndUserId(spaceId, userId)
                .stream()
                .map(p -> p.getPermissionType())
                .toList();
    }

    /**
     * Возвращает флаги прав для UI (canRead, canWrite, canCreate).
     * Используется фронтендом для скрытия кнопок редактирования.
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return флаги прав
     */
    public PermissionFlags getPermissionFlags(Long userId, boolean isAdmin, Long spaceId) {
        boolean read = canRead(userId, isAdmin, spaceId);
        boolean write = canWrite(userId, isAdmin, spaceId);
        return new PermissionFlags(read, write, write); // canCreate == canWrite
    }

    /**
     * Вспомогательный класс для передачи флагов прав во фронтенд.
     */
    public static class PermissionFlags {
        public final boolean canRead;
        public final boolean canEdit;
        public final boolean canCreate;

        public PermissionFlags(boolean canRead, boolean canEdit, boolean canCreate) {
            this.canRead = canRead;
            this.canEdit = canEdit;
            this.canCreate = canCreate;
        }
    }
}

package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.UserRepository;
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
    private final UserRepository userRepository;

    public PermissionService(SpacePermissionRepository permissionRepository,
                             UserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Проверяет, может ли пользователь создавать/редактировать документы в пространстве.
     *
     * Логика (обновлено):
     * - isAdmin=true → всегда true
     * - EDITOR (WRITER) → всегда true
     * - READER/GUEST → true, если есть явное право WRITE или OWNER в пространстве
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
        if (userId == null) {
            return false;
        }
        
        // 1. Проверяем глобальную роль (EDITOR всегда может писать)
        boolean isGlobalEditor = userRepository.findById(userId)
                .map(user -> user.getRole() == GlobalRole.EDITOR)
                .orElse(false);
        
        if (isGlobalEditor) {
            return true;
        }

        // 2. Если не глобальный редактор, проверяем явные права на пространство (для READER/GUEST)
        if (spaceId == null) {
            return false;
        }
        return permissionRepository.hasWriteAccess(spaceId, userId);
    }

    /**
     * Проверяет, может ли пользователь читать документы в пространстве.
     *
     * Логика (обновлено):
     * - ADMIN, READER, EDITOR: всегда true (видят все пространства)
     * - GUEST: только если есть явное право на пространство
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
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    if (user.getRole() == GlobalRole.READER || user.getRole() == GlobalRole.EDITOR) {
                        return true;
                    }
                    // GUEST: проверяем явные права
                    return permissionRepository.hasReadAccess(spaceId, userId);
                })
                .orElse(false);
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

        if (userId == null) {
            return List.of();
        }

        return userRepository.findById(userId)
                .map(user -> {
                    // Базовые права на основе роли
                    java.util.Set<PermissionType> types = new java.util.HashSet<>();
                    types.add(PermissionType.READ); // Все видят всё (кроме GUEST, но canRead это разрулит)
                    
                    if (user.getRole() == GlobalRole.EDITOR) {
                        types.add(PermissionType.WRITE);
                    }
                    
                    // Добавляем явные права из БД (они могут дать WRITE пользователю с ролью READER)
                    if (spaceId != null) {
                        permissionRepository.findBySpaceIdAndUserId(spaceId, userId)
                                .forEach(p -> types.add(p.getPermissionType()));
                    }
                    
                    return types.stream().toList();
                })
                .orElse(List.of());
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

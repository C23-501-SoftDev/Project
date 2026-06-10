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
 * | true         | всегда       | по роли/правам         | всегда |
 * | false        | никогда      | по роли/правам         | по роли/правам |
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
     * Проверяет, может ли пользователь создавать/редактировать/удалять/восстанавливать документы в пространстве.
     *
     * Логика (обновлено):
     * - EDITOR (WRITER) → всегда true
     * - READER/GUEST → true, если есть явное право WRITE или OWNER в пространстве
     * - isAdmin=true сам по себе не дает прав записи в документы
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора (не используется для записи)
     * @param spaceId  ID пространства
     * @return true если операция разрешена
     */
    public boolean canWrite(Long userId, boolean isAdmin, Long spaceId) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    // EDITOR -> всегда true (согласно GlobalRole.java)
                    if (user.getRole() == GlobalRole.EDITOR) {
                        return true;
                    }

                    // Для всех остальных (включая ADMIN с ролью READER/GUEST):
                    // проверяем явные права на пространство (WRITE или OWNER)
                    if (spaceId == null) {
                        return false;
                    }
                    return permissionRepository.hasWriteAccess(spaceId, userId);
                })
                .orElse(false);
    }

    /**
     * Проверяет, может ли пользователь читать документы в пространстве.
     *
     * Логика (обновлено):
     * - READER, EDITOR: всегда true (видят все пространства)
     * - GUEST: только если есть явное право на пространство
     * - isAdmin=true (не GUEST): всегда true
     *
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return true если чтение разрешено
     */
    public boolean canRead(Long userId, boolean isAdmin, Long spaceId) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    // ADMIN (не GUEST) видит всё
                    if (isAdmin && user.getRole() != GlobalRole.GUEST) {
                        return true;
                    }
                    // READER и EDITOR: всегда true
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
     * @param userId   ID пользователя
     * @param isAdmin  флаг администратора
     * @param spaceId  ID пространства
     * @return список прав
     */
    public List<PermissionType> getUserPermissions(Long userId, boolean isAdmin, Long spaceId) {
        if (userId == null) {
            return List.of();
        }

        return userRepository.findById(userId)
                .map(user -> {
                    java.util.Set<PermissionType> types = new java.util.HashSet<>();
                    
                    // Базовое чтение
                    if ((isAdmin && user.getRole() != GlobalRole.GUEST) || 
                        user.getRole() == GlobalRole.READER || 
                        user.getRole() == GlobalRole.EDITOR) {
                        types.add(PermissionType.READ);
                    }
                    
                    // EDITOR всегда имеет WRITE
                    if (user.getRole() == GlobalRole.EDITOR) {
                        types.add(PermissionType.WRITE);
                    }
                    
                    // Явные права из БД
                    if (spaceId != null) {
                        permissionRepository.findBySpaceIdAndUserId(spaceId, userId)
                                .forEach(p -> types.add(p.getPermissionType()));
                    }

                    // Если есть WRITE или OWNER, READ не нужен в списке
                    if (types.contains(PermissionType.WRITE) || types.contains(PermissionType.OWNER)) {
                        types.remove(PermissionType.READ);
                    }
                    
                    return types.stream().toList();
                })
                .orElse(List.of());
    }

    /**
     * Возвращает флаги прав для UI (canRead, canWrite, canCreate).
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

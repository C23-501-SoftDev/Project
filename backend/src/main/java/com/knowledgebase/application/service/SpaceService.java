package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Сервис управления пространствами документов (Application Layer).
 *
 * Реализует use cases:
 * - Создание пространства с автоматическим назначением права OWNER
 * - Получение списка доступных пространств для пользователя
 * - Управление правами доступа к пространствам
 */
@Service
@Transactional(readOnly = true)
public class SpaceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceService.class);

    private final SpaceRepository spaceRepository;
    private final SpacePermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final com.knowledgebase.domain.repository.DocumentContentRepository contentRepository;
    private final DocumentService documentService;
    private final com.knowledgebase.domain.repository.DocumentRepository documentRepository;

    public SpaceService(SpaceRepository spaceRepository,
                        SpacePermissionRepository permissionRepository,
                        UserRepository userRepository,
                        com.knowledgebase.domain.repository.DocumentContentRepository contentRepository,
                        DocumentService documentService,
                        com.knowledgebase.domain.repository.DocumentRepository documentRepository) {
        this.spaceRepository = spaceRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.contentRepository = contentRepository;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
    }

    /**
     * Создаёт новое пространство.
     * Автоматически назначает право OWNER создателю.
     *
     * @param name        уникальное название
     * @param description описание пространства
     * @param ownerId     ID пользователя-владельца
     * @return созданное пространство
     * @throws ConflictException если имя уже занято
     * @throws UserNotFoundException если владелец не найден
     */
    @Transactional
    public Space createSpace(String name, String description, Long ownerId) {
        log.debug("Создание пространства: name={}, ownerId={}", name, ownerId);

        // Проверяем существование владельца
        if (!userRepository.findById(ownerId).isPresent()) {
            throw new UserNotFoundException(ownerId);
        }

        // Проверяем уникальность имени
        if (spaceRepository.existsByName(name)) {
            throw new ConflictException("Пространство с именем '" + name + "' уже существует");
        }

        // Создаём пространство
        Space space = Space.create(name, description, ownerId);
        Space savedSpace = spaceRepository.save(space);
        
        // Создаём директорию для пространства в Git (например, "spaces/name")
        // Можно использовать JGit, чтобы создать пустой .keep файл
        String sanitizedName = name.replaceAll("[\\\\/:*?\"<>|\\s]", "-");
        contentRepository.saveContent("spaces/" + sanitizedName + "/.keep", "", "Create space: " + name, "System", "system@knowledgebase.com");

        // Автоматически назначаем право OWNER создателю
        SpacePermission ownerPermission = SpacePermission.grant(
                savedSpace.getId(), ownerId, PermissionType.OWNER);
        permissionRepository.save(ownerPermission);

        log.info("Пространство создано: id={}, name={}, owner={}", savedSpace.getId(), name, ownerId);
        return savedSpace;
    }

    /**
     * Возвращает пространство по ID.
     *
     * @throws SpaceNotFoundException если не найдено
     */
    public Space getSpaceById(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));
    }

    /**
     * Обновляет данные пространства.
     * Если владелец изменился, обновляет права доступа.
     *
     * @param spaceId     ID пространства
     * @param name        новое имя
     * @param description новое описание
     * @param ownerId     новый владелец
     * @return обновленное пространство
     */
    @Transactional
    public Space updateSpace(Long spaceId, String name, String description, Long ownerId) {
        log.debug("Обновление пространства: id={}, name={}, ownerId={}", spaceId, name, ownerId);

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        // Проверяем уникальность имени, если оно изменилось
        if (!space.getName().equals(name) && spaceRepository.existsByName(name)) {
            throw new ConflictException("Пространство с именем '" + name + "' уже существует");
        }

        // Проверяем существование нового владельца
        if (!userRepository.findById(ownerId).isPresent()) {
            throw new UserNotFoundException(ownerId);
        }

        Long oldOwnerId = space.getOwnerId();
        space.update(name, description, ownerId);
        Space updatedSpace = spaceRepository.save(space);

        // Если владелец изменился, обновляем права
        if (!oldOwnerId.equals(ownerId)) {
            log.info("Смена владельца пространства {}: {} -> {}", spaceId, oldOwnerId, ownerId);
            
            // Удаляем старое право OWNER
            permissionRepository.deleteBySpaceIdAndUserIdAndPermissionType(
                    spaceId, oldOwnerId, PermissionType.OWNER);
            
            // Назначаем новое право OWNER
            SpacePermission newOwnerPermission = SpacePermission.grant(
                    spaceId, ownerId, PermissionType.OWNER);
            permissionRepository.save(newOwnerPermission);
        }

        // Если имя изменилось, переименовываем директорию в Git
        if (!space.getName().equals(name)) {
            String oldSanitizedName = space.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-");
            String newSanitizedName = name.replaceAll("[\\\\/:*?\"<>|\\s]", "-");
            String oldPath = "spaces/" + oldSanitizedName;
            String newPath = "spaces/" + newSanitizedName;
            contentRepository.moveContent(oldPath, newPath, "Rename space from " + space.getName() + " to " + name);
        }

        return updatedSpace;
    }

    /**
     * Передает владение всеми пространствами от одного пользователя другому.
     *
     * @param fromUserId ID старого владельца
     * @param toUserId   ID нового владельца
     */
    @Transactional
    public void transferOwnership(Long fromUserId, Long toUserId) {
        log.info("Передача владения пространствами: от {} к {}", fromUserId, toUserId);

        if (!userRepository.findById(toUserId).isPresent()) {
            throw new UserNotFoundException(toUserId);
        }

        List<Space> ownedSpaces = spaceRepository.findByOwnerId(fromUserId);
        for (Space space : ownedSpaces) {
            updateSpace(space.getId(), space.getName(), space.getDescription(), toUserId);
        }
    }

    /**
     * Удаляет пространство (soft-delete).
     * Документы внутри становятся недоступными для восстановления при восстановлении пространства.
     */
    @Transactional
    public void deleteSpace(Long spaceId) {
        log.info("Мягкое удаление пространства: id={}", spaceId);
        
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        // Устанавливаем флаг удаления
        space.softDelete();
        spaceRepository.save(space);

        List<com.knowledgebase.domain.model.Document> documents = documentService.getDocumentsInSpace(spaceId, false);
        for (com.knowledgebase.domain.model.Document doc : documents) {
            // Удаляем документ, перепривязывать детей НЕ нужно (false), 
            // так как мы сохраняем структуру в БД для восстановления
            documentService.deleteDocument(doc.getId(), false);
            
            // Устанавливаем флаг, что документ удален при удалении пространства
            doc.markAsDeletedWithSpace(true);
            documentRepository.save(doc);
        }

    }

    /**
     * Удаляет пространство и все его документы навсегда (hard-delete).
     */
    @Transactional
    public void hardDeleteSpace(Long spaceId) {
        log.info("Полное удаление пространства: id={}", spaceId);
        
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        // Сначала удаляем документы
        List<com.knowledgebase.domain.model.Document> documents = documentService.getDocumentsInSpace(spaceId, true);
        for (com.knowledgebase.domain.model.Document doc : documents) {
            documentService.hardDeleteDocument(doc.getId());
        }

        // Удаляем права доступа
        permissionRepository.deleteBySpaceId(spaceId);

        // Удаляем само пространство
        spaceRepository.deleteById(spaceId);

        // Удаляем директорию в Git
        String sanitizedName = space.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-");
        contentRepository.deleteContent("spaces/" + sanitizedName, "Hard delete space: " + space.getName());
    }

    /**
     * Восстанавливает пространство (отменяет soft-delete).
     *
     * @param spaceId ID пространства
     */
    @Transactional
    public void restoreSpace(Long spaceId) {
        log.info("Восстановление пространства: id={}", spaceId);
        
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        // Восстанавливаем пространство
        space.restore(); 
        spaceRepository.save(space);
        spaceRepository.flush();

        // Восстанавливаем документы в пространстве в порядке иерархии (сначала родители, потом дети)
        // Восстанавливаем только те документы, которые стали DELETED из-за удаления всего пространства.
        // Для этого используем время обновления (updatedAt) или сравнение с временем удаления пространства.
        // Так как `Document` не хранит информацию о том, КЕМ и ПОЧЕМУ он был удален,
        // мы предполагаем, что если статус DELETED, но он не был удален "давно", это удаление пространства.
        // УПРОЩЕНИЕ: в текущей модели достаточно проверить, не был ли документ в архиве до удаления пространства.
        // Поскольку такой информации нет, мы вводим логику: при удалении пространства, все документы получают
        // updatedAt, равный времени удаления пространства.
        
        List<com.knowledgebase.domain.model.Document> documents = documentService.getDocumentsInSpace(spaceId, true);
        
        // ВАЖНО: При удалении пространства мы должны были обновить updatedAt для всех его документов.
        // Это позволит отличить их от документов, удаленных ранее.
        // До предположения, что это реализовано, добавим проверку статуса.
        
        java.util.Map<Long, List<com.knowledgebase.domain.model.Document>> childrenMap = documents.stream()
                .filter(d -> d.getParentDocumentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(com.knowledgebase.domain.model.Document::getParentDocumentId));

        List<com.knowledgebase.domain.model.Document> roots = documents.stream()
                .filter(d -> d.getParentDocumentId() == null)
                .toList();

        restoreHierarchy(roots, childrenMap);
    }

    private void restoreHierarchy(List<com.knowledgebase.domain.model.Document> nodes, 
                                 java.util.Map<Long, List<com.knowledgebase.domain.model.Document>> childrenMap) {
        for (com.knowledgebase.domain.model.Document doc : nodes) {
            // Восстанавливаем только если документ был помечен как удаленный вместе с пространством
            if (doc.getStatus() == com.knowledgebase.domain.model.DocumentStatus.DELETED &&
                doc.isDeletedWithSpace()) {
                documentService.restoreDocument(doc.getId(), true);
            }
            List<com.knowledgebase.domain.model.Document> children = childrenMap.getOrDefault(doc.getId(), java.util.Collections.emptyList());
            if (!children.isEmpty()) {
                restoreHierarchy(children, childrenMap);
            }
        }
    }

    /**
     * Возвращает пространства по фильтру статуса (для ADMIN).
     */
    public List<Space> getSpacesByStatus(String status, int page, int size) {
        return getSpacesByStatusAndOwner(status, null, page, size);
    }

    /**
     * Возвращает пространства по фильтру статуса и владельца (для ADMIN).
     */
    public List<Space> getSpacesByStatusAndOwner(String status, Long ownerId, int page, int size) {
        if (ownerId != null) {
            return spaceRepository.findByOwnerIdWithStatus(ownerId, status, page, size);
        }
        
        if ("deleted".equals(status) || "inactive".equals(status)) {
            return spaceRepository.findDeleted(page, size);
        } else if ("all".equals(status)) {
            return spaceRepository.findAllIncludeDeleted(page, size);
        }
        return spaceRepository.findAll(page, size);
    }

    /**
     * Возвращает количество пространств по фильтру статуса.
     */
    public long countSpacesByStatus(String status) {
        return countSpacesByStatusAndOwner(status, null);
    }

    /**
     * Возвращает количество пространств по фильтру статуса и владельца.
     */
    public long countSpacesByStatusAndOwner(String status, Long ownerId) {
        if (ownerId != null) {
            return spaceRepository.countByOwnerIdWithStatus(ownerId, status);
        }
        
        if ("deleted".equals(status) || "inactive".equals(status)) {
            return spaceRepository.countDeleted();
        } else if ("all".equals(status)) {
            return spaceRepository.countAllIncludeDeleted();
        }
        return spaceRepository.count();
    }

    /**
     * Возвращает список администраторов, владеющих пространствами с учётом статуса.
     */
    public List<com.knowledgebase.domain.model.User> getAdminSpaceOwners(String status) {
        List<Long> ownerIds = spaceRepository.findDistinctOwnerIdsByStatus(status);
        if (ownerIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        return userRepository.findActiveByIds(ownerIds);
    }

    /**
     * Возвращает пространства, доступные пользователю.
     *
     * @param userId    ID текущего пользователя
     * @param isAdmin   true если пользователь ADMIN
     * @param page      номер страницы
     * @param size      размер страницы
     * @return список доступных пространств
     */
    /**
     * Возвращает пространства, доступные пользователю.
     *
     * @param userId    ID текущего пользователя
     * @param isAdmin   true если пользователь ADMIN
     * @return список доступных пространств
     */
    public List<Space> getSpacesForUser(Long userId, boolean isAdmin) {
        return getSpacesForUser(userId, isAdmin, null);
    }

    /**
     * Возвращает пространства, доступные пользователю с учетом требуемого уровня доступа.
     *
     * @param userId         ID текущего пользователя
     * @param isAdmin        true если пользователь ADMIN
     * @param requiredAccess минимально требуемый тип права
     * @return список доступных пространств
     */
    public List<Space> getSpacesForUser(Long userId, boolean isAdmin, PermissionType requiredAccess) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }

        com.knowledgebase.domain.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return java.util.Collections.emptyList();
        }

        com.knowledgebase.domain.model.GlobalRole role = user.getRole();

        if (isAdmin && role != com.knowledgebase.domain.model.GlobalRole.GUEST) {
            if (role == com.knowledgebase.domain.model.GlobalRole.READER) {
                if (requiredAccess == PermissionType.WRITE || requiredAccess == PermissionType.OWNER) {
                    return findSpacesByExplicitPermissions(userId, requiredAccess);
                }
            }
            return spaceRepository.findAllActive();
        }

        if (role == com.knowledgebase.domain.model.GlobalRole.EDITOR) {
            return spaceRepository.findAllActive();
        }

        if (role == com.knowledgebase.domain.model.GlobalRole.READER) {
            if (requiredAccess == null || requiredAccess == PermissionType.READ) {
                return spaceRepository.findAllActive();
            }
            return findSpacesByExplicitPermissions(userId, requiredAccess);
        }

        return findSpacesByExplicitPermissions(userId, requiredAccess);
    }

    /**
     * Возвращает пространства пользователя с поиском по названию.
     */
    public List<Space> searchSpacesForUser(Long userId, boolean isAdmin, PermissionType requiredAccess, String query, int page, int size) {
        if (query == null || query.isBlank()) {
            return getSpacesForUser(userId, isAdmin, requiredAccess);
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<Space> matchedSpaces = getAccessibleSpaces(userId, isAdmin, requiredAccess).stream()
                .filter(space -> space.getName() != null && space.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .toList();
        return paginate(matchedSpaces, page, size);
    }

    private List<Space> getAccessibleSpaces(Long userId, boolean isAdmin, PermissionType requiredAccess) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }

        com.knowledgebase.domain.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return java.util.Collections.emptyList();
        }

        com.knowledgebase.domain.model.GlobalRole role = user.getRole();

        if (isAdmin && role != com.knowledgebase.domain.model.GlobalRole.GUEST) {
            if (requiredAccess == PermissionType.WRITE || requiredAccess == PermissionType.OWNER) {
                if (role == com.knowledgebase.domain.model.GlobalRole.READER) {
                    return findSpacesByExplicitPermissions(userId, requiredAccess);
                }
            }
            return spaceRepository.findAllActive();
        }

        if (role == com.knowledgebase.domain.model.GlobalRole.EDITOR) {
            return spaceRepository.findAllActive();
        }

        if (role == com.knowledgebase.domain.model.GlobalRole.READER && (requiredAccess == null || requiredAccess == PermissionType.READ)) {
            return spaceRepository.findAllActive();
        }

        return findSpacesByExplicitPermissions(userId, requiredAccess);
    }

    private List<Space> paginate(List<Space> spaces, int page, int size) {
        if (spaces.isEmpty() || size <= 0) {
            return java.util.Collections.emptyList();
        }

        int fromIndex = Math.min(Math.max(page, 0) * size, spaces.size());
        int toIndex = Math.min(fromIndex + size, spaces.size());
        if (fromIndex >= toIndex) {
            return java.util.Collections.emptyList();
        }
        return spaces.subList(fromIndex, toIndex);
    }

    private List<Space> findSpacesByExplicitPermissions(Long userId, PermissionType requiredAccess) {
        List<SpacePermission> permissions = permissionRepository.findByUserId(userId);
        
        // Фильтруем по требуемому уровню доступа
        java.util.stream.Stream<SpacePermission> stream = permissions.stream();
        if (requiredAccess != null) {
            stream = stream.filter(p -> {
                if (requiredAccess == PermissionType.READ) return true;
                if (requiredAccess == PermissionType.WRITE) {
                    return p.getPermissionType() == PermissionType.WRITE || p.getPermissionType() == PermissionType.OWNER;
                }
                if (requiredAccess == PermissionType.OWNER) {
                    return p.getPermissionType() == PermissionType.OWNER;
                }
                return false;
            });
        }
        
        List<Long> spaceIds = stream
                .map(SpacePermission::getSpaceId)
                .distinct()
                .toList();

        if (spaceIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return spaceRepository.findAllByIdIn(new java.util.HashSet<>(spaceIds));
    }

    /**
     * Назначает право доступа пользователю на пространство.
     *
     * @param spaceId        ID пространства
     * @param userId         ID пользователя
     * @param permissionType тип права
     * @return созданное право
     * @throws ConflictException если такое право уже существует
     */
    @Transactional
    public SpacePermission grantPermission(Long spaceId, Long userId, PermissionType permissionType) {
        log.debug("Назначение права: spaceId={}, userId={}, type={}", spaceId, userId, permissionType);

        // Проверяем существование пространства
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }

        // Проверяем существование пользователя
        if (!userRepository.findByIdIncludingDeleted(userId).isPresent()) {
            throw new UserNotFoundException(userId);
        }

        // Проверяем дублирование или избыточность (если есть WRITE/OWNER, то READ уже не нужен)
        if (permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(spaceId, userId, permissionType)) {
            throw new ConflictException("У пользователя уже есть такие права");
        }

        if (permissionType == PermissionType.READ) {
            boolean hasHigherAccess = permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(spaceId, userId, PermissionType.WRITE) ||
                                     permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(spaceId, userId, PermissionType.OWNER);
            if (hasHigherAccess) {
                throw new ConflictException("У пользователя уже есть такие права");
            }
        }

        // Улучшение: Не выдаем READER'у право READ, так как оно у него есть глобально.
        com.knowledgebase.domain.model.User targetUser = userRepository.findByIdIncludingDeleted(userId).get();
        if (targetUser.getRole() == com.knowledgebase.domain.model.GlobalRole.READER && permissionType == PermissionType.READ) {
            throw new ConflictException("У пользователя уже есть такие права");
        }
        
        // EDITOR'у тоже не нужно выдавать READ или WRITE
        if (targetUser.getRole() == com.knowledgebase.domain.model.GlobalRole.EDITOR && (permissionType == PermissionType.READ || permissionType == PermissionType.WRITE)) {
             throw new ConflictException("У пользователя уже есть такие права");
        }

        // Если выдается WRITE, удаляем READ
        if (permissionType == PermissionType.WRITE) {
            permissionRepository.deleteBySpaceIdAndUserIdAndPermissionType(spaceId, userId, PermissionType.READ);
        }
        // Если выдается OWNER, удаляем READ и WRITE
        if (permissionType == PermissionType.OWNER) {
            permissionRepository.deleteBySpaceIdAndUserIdAndPermissionType(spaceId, userId, PermissionType.READ);
            permissionRepository.deleteBySpaceIdAndUserIdAndPermissionType(spaceId, userId, PermissionType.WRITE);
        }

        SpacePermission permission = SpacePermission.grant(spaceId, userId, permissionType);
        SpacePermission saved = permissionRepository.save(permission);

        log.info("Право назначено: spaceId={}, userId={}, type={}", spaceId, userId, permissionType);
        return saved;
    }

    /**
     * Возвращает все права пользователя в пространстве.
     * Используется в GET /api/user/permissions?spaceId={id}
     *
     * @param spaceId ID пространства
     * @param userId  ID пользователя
     */
    public List<SpacePermission> getUserPermissionsInSpace(Long spaceId, Long userId) {
        // Проверяем существование пространства
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        return permissionRepository.findBySpaceIdAndUserId(spaceId, userId);
    }

    /**
     * Возвращает все права доступа для указанного пространства (для ADMIN).
     *
     * @param spaceId ID пространства
     * @return список всех прав доступа
     * @throws SpaceNotFoundException если пространство не найдено
     */
    public List<SpacePermission> getPermissionsForSpace(Long spaceId) {
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        return permissionRepository.findBySpaceId(spaceId);
    }
}

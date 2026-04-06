package com.knowledgebase.application.service;

import com.knowledgebase.application.dto.PagedResult;
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

import java.util.Comparator;
import java.util.List;

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

    public SpaceService(SpaceRepository spaceRepository,
                        SpacePermissionRepository permissionRepository,
                        UserRepository userRepository) {
        this.spaceRepository = spaceRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
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
     * Возвращает все пространства (для ADMIN).
     * Доступ проверяется в контроллере через @PreAuthorize.
     *
     * @param page номер страницы
     * @param size размер страницы
     */
    public PagedResult<Space> getAllSpaces(int page, int size) {
        List<Space> items = spaceRepository.findAll(page, size);
        long total = spaceRepository.count();
        return new PagedResult<>(items, total);
    }

    /**
     * Возвращает пространства, доступные пользователю.
     *
     * Для ADMIN — все пространства.
     * Для EDITOR/READER — только те, где есть запись в space_permissions.
     *
     * @param userId    ID текущего пользователя
     * @param isAdmin   true если пользователь ADMIN
     * @param page      номер страницы
     * @param size      размер страницы
     * @return список доступных пространств
     */
    public PagedResult<Space> getSpacesForUser(Long userId, boolean isAdmin, int page, int size) {
        if (isAdmin) {
            // ADMIN видит все пространства
            return getAllSpaces(page, size);
        }

        // Получаем все права пользователя
        List<SpacePermission> permissions = permissionRepository.findByUserId(userId);

        // Из прав получаем ID пространств
        List<Long> spaceIds = permissions.stream()
                .map(SpacePermission::getSpaceId)
                .distinct()
                .toList();

        // Загружаем пространства по ID
        List<Space> allAccessible = spaceIds.stream()
                .map(spaceId -> spaceRepository.findById(spaceId).orElse(null))
                .filter(space -> space != null)
                .toList();

        // Стабилизируем порядок (как в репозитории: createdAt DESC)
        List<Space> sorted = allAccessible.stream()
                .sorted(Comparator.comparing(Space::getCreatedAt).reversed())
                .toList();

        long total = sorted.size();
        int safeSize = Math.max(size, 1);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, sorted.size());
        int to = Math.min(from + safeSize, sorted.size());

        List<Space> pageItems = sorted.subList(from, to);
        return new PagedResult<>(pageItems, total);
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
        if (!userRepository.findById(userId).isPresent()) {
            throw new UserNotFoundException(userId);
        }

        // Проверяем дублирование
        if (permissionRepository.existsBySpaceIdAndUserIdAndPermissionType(
                spaceId, userId, permissionType)) {
            throw new ConflictException(
                "Пользователь уже имеет право " + permissionType + " на это пространство");
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
}

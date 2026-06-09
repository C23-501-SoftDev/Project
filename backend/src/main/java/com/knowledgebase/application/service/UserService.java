package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.UserCreatedEvent;
import com.knowledgebase.domain.exception.AccessDeniedException;
import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления пользователями (Application Layer).
 *
 * Реализует use cases:
 * - Создание пользователя (только через администратора)
 * - Обновление профиля (логин, email, роль, isAdmin)
 * - Смена пароля
 * - Soft-delete вместо hard-delete
 * - Восстановление удалённых пользователей
 * - Получение списка пользователей с фильтрацией по status
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SpaceService spaceService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher,
                       SpaceService spaceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.spaceService = spaceService;
    }

    /**
     * Создаёт нового пользователя.
     * Доступно только для администратора (проверяется в контроллере через @PreAuthorize).
     *
     * @param login    уникальный логин
     * @param email    уникальный email
     * @param password пароль в открытом виде (будет захеширован)
     * @param role     глобальная роль
     * @param isAdmin  флаг администратора
     * @return созданный пользователь
     * @throws ConflictException если логин или email уже заняты
     */
    @Transactional
    public User createUser(String login, String email, String password, GlobalRole role, boolean isAdmin) {
        log.debug("Создание пользователя: login={}, email={}, role={}, isAdmin={}", login, email, role, isAdmin);

        // Проверяем уникальность логина (включая удалённых — логин всегда уникален)
        if (userRepository.existsByLoginIncludingDeleted(login)) {
            throw new ConflictException("Пользователь с логином '" + login + "' уже существует");
        }

        // Проверяем уникальность email (включая удалённых — email всегда уникален)
        if (userRepository.existsByEmailIncludingDeleted(email)) {
            throw new ConflictException("Пользователь с email '" + email + "' уже существует");
        }

        // Хешируем пароль перед сохранением
        String passwordHash = passwordEncoder.encode(password);

        // Создаём доменный объект через фабричный метод
        User user = User.create(login, passwordHash, email, role, isAdmin);

        // Сохраняем
        User savedUser = userRepository.save(user);

        // Публикуем событие создания (для уведомлений, аудита и т.д.)
        eventPublisher.publishEvent(new UserCreatedEvent(savedUser.getId(),
                savedUser.getEmail(), savedUser.getLogin()));

        log.info("Пользователь создан: id={}, login={}", savedUser.getId(), login);
        return savedUser;
    }

    /**
     * Обновляет профиль пользователя (логин, email, роль, isAdmin).
     * Пароль не обновляется этим методом — используйте changePassword().
     *
     * @param userId        ID обновляемого пользователя
     * @param login         новый логин (null = без изменений)
     * @param email         новый email (null = без изменений)
     * @param role          новая роль (null = без изменений)
     * @param isAdmin       новый флаг администратора
     * @param currentUserId ID текущего пользователя (для проверки прав)
     * @return обновлённый пользователь
     */
    @Transactional
    public User updateUser(Long userId, String login, String email, GlobalRole role, boolean isAdmin, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Запрещаем администратору снимать с себя права админа
        if (userId.equals(currentUserId) && user.getIsAdmin() && !isAdmin) {
            throw new AccessDeniedException("Администратор не может снять с себя права администратора");
        }

        // Проверяем уникальность нового логина (если изменяется)
        if (login != null && !login.equals(user.getLogin()) && userRepository.existsByLoginIncludingDeleted(login)) {
            throw new ConflictException("Пользователь с логином '" + login + "' уже существует");
        }

        // Проверяем уникальность нового email (если изменяется)
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmailIncludingDeleted(email)) {
            throw new ConflictException("Пользователь с email '" + email + "' уже существует");
        }

        // Применяем изменения через метод домена
        user.updateProfile(login, email, role, isAdmin);

        User updated = userRepository.save(user);
        log.info("Пользователь обновлён: id={}", userId);
        return updated;
    }

    /**
     * Меняет пароль пользователя.
     * Только администратор может выполнить эту операцию.
     *
     * @param userId      ID пользователя
     * @param newPassword новый пароль в открытом виде (будет захеширован)
     */
    @Transactional
    public void changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newHash = passwordEncoder.encode(newPassword);
        user.updatePasswordHash(newHash);

        userRepository.save(user);
        log.info("Пароль изменён для пользователя: id={}", userId);
    }

    /**
     * Выполняет soft-delete пользователя.
     * Вместо физического удаления устанавливает флаг isDeleted = true.
     * Данные пользователя сохраняются для сохранения истории авторства.
     *
     * @param userId ID удаляемого пользователя
     * @throws UserNotFoundException если пользователь не найден
     */
    @Transactional
    public User deleteUser(Long userId) {
        // Запрещаем удаление системного администратора (ID=1)
        if (userId.equals(1L)) {
            throw new AccessDeniedException("Удаление системного администратора запрещено");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Передаем владение пространствами системному администратору (ID=1)
        spaceService.transferOwnership(userId, 1L);

        // Выполняем soft-delete через доменный метод
        user.softDelete();
        User saved = userRepository.save(user);

        log.info("Пользователь soft-удалён: id={}", userId);
        return saved;
    }

    /**
     * Восстанавливает soft-удалённого пользователя.
     *
     * @param userId ID восстанавливаемого пользователя
     * @return восстановленный пользователь
     * @throws UserNotFoundException если пользователь не найден или не был удалён
     */
    @Transactional
    public User restoreUser(Long userId) {
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            throw new ConflictException("Пользователь не был удалён");
        }

        user.restore();
        User saved = userRepository.save(user);

        log.info("Пользователь восстановлен: id={}", userId);
        return saved;
    }

    /**
     * Возвращает пользователя по ID.
     *
     * @throws UserNotFoundException если не найден
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Возвращает пользователя по ID, включая удалённых.
     */
    public User getUserByIdIncludingDeleted(Long userId) {
        return userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Возвращает список активных пользователей (is_deleted = false) с пагинацией.
     *
     * @param page    номер страницы (0-based)
     * @param size    размер страницы
     * @param sortBy  поле сортировки
     * @param sortDir направление (asc/desc)
     * @return список пользователей на странице
     */
    public List<User> getAllUsers(int page, int size, String sortBy, String sortDir) {
        return userRepository.findAllActive(page, size, sortBy, sortDir);
    }

    /**
     * Возвращает список всех пользователей (включая удалённых) с пагинацией.
     */
    public List<User> getAllUsersIncludingDeleted(int page, int size, String sortBy, String sortDir) {
        return userRepository.findAllIncludingDeleted(page, size, sortBy, sortDir);
    }

    /**
     * Возвращает общее количество активных пользователей (для пагинации).
     */
    public long countUsers() {
        return userRepository.countActive();
    }

    /**
     * Возвращает общее количество пользователей, включая удалённых.
     */
    public long countUsersIncludingDeleted() {
        return userRepository.countAll();
    }

    /**
     * Возвращает список пользователей с применением всех фильтров и пагинацией.
     *
     * @param page номер страницы (0-based)
     * @param size размер страницы
     * @param sortBy поле сортировки
     * @param sortDir направление сортировки
     * @param statusFilter статус: "active", "deleted", "all"
     * @param roles фильтр по ролям (null или пустой = без фильтра)
     * @param isAdmin фильтр по статусу админа (null или пустой = без фильтра)
     * @param search поиск по логину/email (null или пустой = без фильтра)
     * @return список пользователей
     */
    public List<User> getUsersWithFilters(int page, int size, String sortBy, String sortDir,
                                          String statusFilter, List<String> roles, List<String> isAdmin, String search) {
        Boolean includeDeleted = switch (statusFilter) {
            case "deleted" -> false;
            case "all" -> true;
            default -> null;
        };

        return userRepository.findAllWithFilters(page, size, sortBy, sortDir, includeDeleted, roles, isAdmin, search);
    }

    /**
     * Возвращает общее количество пользователей с применением фильтров.
     */
    public long countUsersWithFilters(String statusFilter, List<String> roles, List<String> isAdmin, String search) {
        Boolean includeDeleted = switch (statusFilter) {
            case "deleted" -> false;
            case "all" -> true;
            default -> null;
        };

        return userRepository.countWithFilters(includeDeleted, roles, isAdmin, search);
    }
}

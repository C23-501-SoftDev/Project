package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.UserCreatedEvent;
import com.knowledgebase.domain.exception.AccessDeniedException;
import com.knowledgebase.domain.exception.ConflictException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.logging.SystemLogger;
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
    private static final SystemLogger systemLog = SystemLogger.getLogger(UserService.class, "service.user");

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
        systemLog.info(
                "Service operation started",
                "create_user",
                "started",
                "role", role,
                "is_admin", isAdmin
        );
        try {
        log.debug("Creating user: role={}, isAdmin={}", role, isAdmin);

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

        log.info("User created: id={}", savedUser.getId());
        systemLog.info(
                "Service operation completed",
                "create_user",
                "success",
                "user_id", savedUser.getId(),
                "role", savedUser.getRole(),
                "is_admin", savedUser.isAdmin()
        );
        return savedUser;
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Service operation failed",
                    "create_user",
                    ex,
                    "role", role,
                    "is_admin", isAdmin
            );
            throw ex;
        }
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
        systemLog.info(
                "Service operation started",
                "update_user",
                "started",
                "user_id", userId,
                "current_user_id", currentUserId,
                "role", role,
                "is_admin", isAdmin
        );
        try {
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
        systemLog.info(
                "Service operation completed",
                "update_user",
                "success",
                "user_id", userId,
                "current_user_id", currentUserId
        );
        return updated;
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Service operation failed",
                    "update_user",
                    ex,
                    "user_id", userId,
                    "current_user_id", currentUserId,
                    "role", role,
                    "is_admin", isAdmin
            );
            throw ex;
        }
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
        systemLog.info(
                "Service operation started",
                "change_password",
                "started",
                "user_id", userId
        );
        try {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newHash = passwordEncoder.encode(newPassword);
        user.updatePasswordHash(newHash);

        userRepository.save(user);
        log.info("Пароль изменён для пользователя: id={}", userId);
        systemLog.info(
                "Service operation completed",
                "change_password",
                "success",
                "user_id", userId
        );
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Service operation failed",
                    "change_password",
                    ex,
                    "user_id", userId
            );
            throw ex;
        }
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
        systemLog.info(
                "Service operation started",
                "delete_user",
                "started",
                "user_id", userId
        );
        try {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Если пользователь владеет какими-то пространствами, передаем их системному администратору (ID=1)
        if (!userId.equals(1L)) {
            spaceService.transferOwnership(userId, 1L);
        }

        // Выполняем soft-delete через доменный метод
        user.softDelete();
        User saved = userRepository.save(user);

        log.info("Пользователь soft-удалён: id={}", userId);
        systemLog.info(
                "Service operation completed",
                "delete_user",
                "success",
                "user_id", userId
        );
        return saved;
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Service operation failed",
                    "delete_user",
                    ex,
                    "user_id", userId
            );
            throw ex;
        }
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
        systemLog.info(
                "Service operation started",
                "restore_user",
                "started",
                "user_id", userId
        );
        try {
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            throw new ConflictException("Пользователь не был удалён");
        }

        user.restore();
        User saved = userRepository.save(user);

        log.info("Пользователь восстановлен: id={}", userId);
        systemLog.info(
                "Service operation completed",
                "restore_user",
                "success",
                "user_id", userId
        );
        return saved;
        } catch (RuntimeException ex) {
            systemLog.error(
                    "Service operation failed",
                    "restore_user",
                    ex,
                    "user_id", userId
            );
            throw ex;
        }
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
}

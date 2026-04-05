package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.UserCreatedEvent;
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
 * - Обновление профиля (логин, email, роль)
 * - Смена пароля
 * - Удаление с проверкой ссылочной целостности
 * - Получение списка пользователей
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Создаёт нового пользователя.
     * Доступно только для администратора (проверяется в контроллере через @PreAuthorize).
     *
     * @param login    уникальный логин
     * @param email    уникальный email
     * @param password пароль в открытом виде (будет захеширован)
     * @param role     глобальная роль
     * @return созданный пользователь
     * @throws ConflictException если логин или email уже заняты
     */
    @Transactional
    public User createUser(String login, String email, String password, GlobalRole role) {
        log.debug("Создание пользователя: login={}, email={}, role={}", login, email, role);

        // Проверяем уникальность логина
        if (userRepository.existsByLogin(login)) {
            throw new ConflictException("Пользователь с логином '" + login + "' уже существует");
        }

        // Проверяем уникальность email
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Пользователь с email '" + email + "' уже существует");
        }

        // Хешируем пароль перед сохранением
        String passwordHash = passwordEncoder.encode(password);

        // Создаём доменный объект через фабричный метод
        User user = User.create(login, passwordHash, email, role);

        // Сохраняем
        User savedUser = userRepository.save(user);

        // Публикуем событие создания (для уведомлений, аудита и т.д.)
        eventPublisher.publishEvent(new UserCreatedEvent(savedUser.getId(),
                savedUser.getEmail(), savedUser.getLogin()));

        log.info("Пользователь создан: id={}, login={}", savedUser.getId(), login);
        return savedUser;
    }

    /**
     * Обновляет профиль пользователя (логин, email, роль).
     * Пароль не обновляется этим методом — используйте changePassword().
     *
     * @param userId ID обновляемого пользователя
     * @param login  новый логин (null = без изменений)
     * @param email  новый email (null = без изменений)
     * @param role   новая роль (null = без изменений)
     * @return обновлённый пользователь
     */
    @Transactional
    public User updateUser(Long userId, String login, String email, GlobalRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Проверяем уникальность нового логина (если изменяется)
        if (login != null && !login.equals(user.getLogin()) && userRepository.existsByLogin(login)) {
            throw new ConflictException("Пользователь с логином '" + login + "' уже существует");
        }

        // Проверяем уникальность нового email (если изменяется)
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("Пользователь с email '" + email + "' уже существует");
        }

        // Применяем изменения через метод домена
        user.updateProfile(login, email, role);

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
     * Удаляет пользователя.
     * Проверяет наличие связанных данных (ON DELETE RESTRICT).
     *
     * @param userId ID удаляемого пользователя
     * @throws UserNotFoundException если пользователь не найден
     * @throws ConflictException если пользователь имеет документы, пространства или версии
     */
    @Transactional
    public void deleteUser(Long userId) {
        // Проверяем существование
        if (!userRepository.findById(userId).isPresent()) {
            throw new UserNotFoundException(userId);
        }

        // Проверка ON DELETE RESTRICT: документы
        if (userRepository.hasDocuments(userId)) {
            throw new ConflictException(
                "Невозможно удалить пользователя: он является автором документов. " +
                "Сначала удалите или переназначьте документы.");
        }

        // Проверка ON DELETE RESTRICT: пространства
        if (userRepository.hasOwnedSpaces(userId)) {
            throw new ConflictException(
                "Невозможно удалить пользователя: он является владельцем пространств. " +
                "Сначала удалите или передайте права на пространства.");
        }

        // Проверка ON DELETE RESTRICT: версии
        if (userRepository.hasVersions(userId)) {
            throw new ConflictException(
                "Невозможно удалить пользователя: он создавал версии документов. " +
                "Сначала удалите связанные версии.");
        }

        userRepository.deleteById(userId);
        log.info("Пользователь удалён: id={}", userId);
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
     * Возвращает список всех пользователей с пагинацией.
     *
     * @param page    номер страницы (0-based)
     * @param size    размер страницы
     * @param sortBy  поле сортировки
     * @param sortDir направление (asc/desc)
     * @return список пользователей на странице
     */
    public List<User> getAllUsers(int page, int size, String sortBy, String sortDir) {
        return userRepository.findAll(page, size, sortBy, sortDir);
    }

    /**
     * Возвращает общее количество пользователей (для пагинации).
     */
    public long countUsers() {
        return userRepository.count();
    }
}

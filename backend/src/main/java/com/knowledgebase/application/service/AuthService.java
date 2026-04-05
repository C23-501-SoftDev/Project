package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.InvalidCredentialsException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации (Application Layer).
 *
 * Отвечает за:
 * - Верификацию логина/пароля
 * - Генерацию JWT-токена (делегируется JwtTokenProvider)
 *
 * Не содержит HTTP-специфической логики — только бизнес-правила.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Инъекция через конструктор — лучшая практика (без @Autowired на поле)
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Аутентифицирует пользователя по логину и паролю.
     *
     * @param login    логин пользователя
     * @param password пароль в открытом виде
     * @return User если аутентификация успешна
     * @throws InvalidCredentialsException если логин/пароль неверны
     */
    public User authenticate(String login, String password) {
        // Ищем пользователя по логину
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> {
                    log.warn("Попытка входа с несуществующим логином: {}", login);
                    // Не раскрываем, что именно неверно (логин или пароль) — безопасность
                    return new InvalidCredentialsException();
                });

        // Проверяем хеш пароля (BCrypt)
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Неверный пароль для пользователя: {}", login);
            throw new InvalidCredentialsException();
        }
        log.info("Успешная аутентификация пользователя: {} (роль: {})", login, user.getRole());
        return user;
    }

    /**
     * Возвращает текущего пользователя по ID.
     * Используется в эндпоинте GET /api/auth/me
     *
     * @param userId ID из JWT токена
     * @return User
     * @throws UserNotFoundException если пользователь не найден
     */
    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}

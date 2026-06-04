package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.InvalidCredentialsException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.logging.SystemLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final SystemLogger log = SystemLogger.getLogger(AuthService.class, "service.auth");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String login, String password) {
        log.info("Service operation started", "authenticate", "started");
        try {
            User user = userRepository.findByLoginIncludingDeleted(login)
                    .orElseThrow(InvalidCredentialsException::new);

            if (user.isDeleted()) {
                throw new InvalidCredentialsException();
            }

            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }

            log.info(
                    "Service operation completed",
                    "authenticate",
                    "success",
                    "user_id", user.getId(),
                    "role", user.getRole(),
                    "is_admin", user.isAdmin()
            );
            return user;
        } catch (InvalidCredentialsException ex) {
            log.warn("Service operation failed", "authenticate", "invalid_credentials");
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Service operation failed", "authenticate", ex);
            throw ex;
        }
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}

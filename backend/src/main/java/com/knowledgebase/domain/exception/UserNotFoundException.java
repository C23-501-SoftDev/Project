package com.knowledgebase.domain.exception;

/**
 * Исключение: пользователь не найден.
 * HTTP статус: 404 Not Found
 */
public class UserNotFoundException extends DomainException {

    public UserNotFoundException(Long id) {
        super("Пользователь с ID " + id + " не найден");
    }

    public UserNotFoundException(String login) {
        super("Пользователь с логином '" + login + "' не найден");
    }
}

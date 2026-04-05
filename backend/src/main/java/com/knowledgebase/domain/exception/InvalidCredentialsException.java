package com.knowledgebase.domain.exception;

/**
 * Исключение: неверные учётные данные при аутентификации.
 * HTTP статус: 401 Unauthorized
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Неверный логин или пароль");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

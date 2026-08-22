package com.knowledgebase.domain.exception;

/**
 * Исключение бизнес-валидации пространства (Domain Layer).
 *
 * Примеры: попытка назначить владельцем пространства пользователя
 * без прав администратора (US4.2.1).
 *
 * Обрабатывается в GlobalExceptionHandler → HTTP 422 Unprocessable Entity.
 */
public class SpaceValidationException extends DomainException {

    public SpaceValidationException(String message) {
        super(message);
    }
}

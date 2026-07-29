package com.knowledgebase.domain.exception;

/**
 * Исключение: группа пользователей не найдена (US4.1.8).
 * Обрабатывается в GlobalExceptionHandler → HTTP 404 Not Found.
 */
public class GroupNotFoundException extends DomainException {

    public GroupNotFoundException(Long groupId) {
        super("Группа с ID " + groupId + " не найдена");
    }

    public GroupNotFoundException(String message) {
        super(message);
    }
}

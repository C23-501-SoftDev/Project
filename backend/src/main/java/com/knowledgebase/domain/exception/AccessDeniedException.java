package com.knowledgebase.domain.exception;

/**
 * Исключение: недостаточно прав для выполнения операции.
 * HTTP статус: 403 Forbidden
 *
 * Отличается от Spring Security AccessDeniedException тем, что является
 * доменным исключением и содержит бизнес-контекст.
 */
public class AccessDeniedException extends DomainException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(Long userId, Long spaceId) {
        super("Пользователь " + userId + " не имеет прав на пространство " + spaceId);
    }
}

package com.knowledgebase.domain.exception;

/**
 * Исключение: конфликт данных (нарушение уникальности или ссылочной целостности).
 * HTTP статус: 409 Conflict
 *
 * Примеры:
 * - Попытка создать пользователя с существующим логином
 * - Попытка удалить пользователя с документами
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}

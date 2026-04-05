package com.knowledgebase.domain.exception;

/**
 * Базовое доменное исключение.
 * Все кастомные исключения наследуются от этого класса.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.knowledgebase.domain.exception;

/**
 * Исключение: нарушение правил загрузки вложений.
 */
public class AttachmentValidationException extends DomainException {

    public AttachmentValidationException(String message) {
        super(message);
    }
}

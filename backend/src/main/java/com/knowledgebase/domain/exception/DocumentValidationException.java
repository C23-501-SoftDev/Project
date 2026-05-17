package com.knowledgebase.domain.exception;

/**
 * Исключение, возникающее при нарушении бизнес-правил валидации документа.
 */
public class DocumentValidationException extends DomainException {
    public DocumentValidationException(String message) {
        super(message);
    }
}

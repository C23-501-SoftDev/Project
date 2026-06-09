package com.knowledgebase.domain.exception;

/**
 * Исключение: вложение не найдено.
 */
public class AttachmentNotFoundException extends DomainException {

    public AttachmentNotFoundException(Long id) {
        super("Вложение с ID " + id + " не найдено");
    }
}

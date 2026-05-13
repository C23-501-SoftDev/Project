package com.knowledgebase.domain.exception;

/**
 * Исключение: документ не найден.
 * HTTP статус: 404 Not Found
 */
public class DocumentNotFoundException extends DomainException {

    public DocumentNotFoundException(Long id) {
        super("Документ с ID " + id + " не найден");
    }

    public DocumentNotFoundException(String gitFilePath) {
        super("Документ по пути '" + gitFilePath + "' не найден");
    }
}

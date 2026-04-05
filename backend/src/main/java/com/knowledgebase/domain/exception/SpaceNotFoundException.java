package com.knowledgebase.domain.exception;

/**
 * Исключение: пространство не найдено.
 * HTTP статус: 404 Not Found
 */
public class SpaceNotFoundException extends DomainException {

    public SpaceNotFoundException(Long id) {
        super("Пространство с ID " + id + " не найдено");
    }

    public SpaceNotFoundException(String name) {
        super("Пространство с именем '" + name + "' не найдено");
    }
}

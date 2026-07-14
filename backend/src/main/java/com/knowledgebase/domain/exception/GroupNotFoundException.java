package com.knowledgebase.domain.exception;

/**
 * Исключение: группа пользователей не найдена.
 * HTTP статус: 404 Not Found
 */
public class GroupNotFoundException extends DomainException {

    public GroupNotFoundException(Long id) {
        super("Группа с ID " + id + " не найдена");
    }

    public GroupNotFoundException(String name) {
        super("Группа с именем '" + name + "' не найдена");
    }
}

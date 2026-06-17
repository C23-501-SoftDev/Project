package com.knowledgebase.domain.exception;

/**
 * Исключение: членство пользователя в группе не найдено.
 * Возникает при попытке удалить из группы пользователя, который в ней не состоит.
 * HTTP статус: 404 Not Found
 */
public class GroupMembershipNotFoundException extends DomainException {

    public GroupMembershipNotFoundException(Long groupId, Long userId) {
        super("Пользователь с ID " + userId + " не состоит в группе с ID " + groupId);
    }
}

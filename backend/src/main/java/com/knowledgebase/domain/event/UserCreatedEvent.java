package com.knowledgebase.domain.event;

/**
 * Событие создания пользователя.
 *
 * Публикуется после успешного создания пользователя.
 * Слушатели могут отправить приветственное письмо, записать в аудит и т.д.
 *
 * Расширение: реализуйте @EventListener для обработки этого события.
 */
public class UserCreatedEvent extends DomainEvent {

    private final Long userId;
    private final String email;
    private final String login;

    public UserCreatedEvent(Long userId, String email, String login) {
        super();
        this.userId = userId;
        this.email = email;
        this.login = login;
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getLogin() { return login; }
}

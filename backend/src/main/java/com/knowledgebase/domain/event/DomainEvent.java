package com.knowledgebase.domain.event;

import java.time.LocalDateTime;

/**
 * Базовый класс для доменных событий.
 *
 * Доменные события позволяют реализовать асинхронную обработку
 * (email-уведомления, аудит) без жёсткой связи между компонентами.
 *
 * Расширение: для обработки событий используйте Spring ApplicationEventPublisher
 * и аннотацию @EventListener в отдельных компонентах.
 *
 * Пример использования:
 * <pre>
 *   applicationEventPublisher.publishEvent(new UserCreatedEvent(user.getId(), user.getEmail()));
 * </pre>
 */
public abstract class DomainEvent {

    private final LocalDateTime occurredAt;

    protected DomainEvent() {
        this.occurredAt = LocalDateTime.now();
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}

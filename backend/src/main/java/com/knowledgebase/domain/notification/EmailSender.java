package com.knowledgebase.domain.notification;

/**
 * Порт отправки email-сообщений (Domain Layer).
 *
 * Абстракция, через которую application-слой отправляет письма, не зная
 * о конкретном транспорте (SMTP/JavaMailSender). Реализации находятся в
 * infrastructure:
 * <ul>
 *   <li>{@code SpringMailEmailSender} — реальная асинхронная отправка через SMTP
 *       (активна при {@code app.notifications.enabled=true});</li>
 *   <li>{@code LoggingEmailSender} — заглушка по умолчанию, только логирует письмо
 *       (когда уведомления выключены или SMTP не настроен).</li>
 * </ul>
 *
 * Контракт: реализация НЕ должна выбрасывать исключения вызывающему коду —
 * сбой отправки письма не должен ломать основную бизнес-операцию. Ошибки
 * обрабатываются и логируются внутри реализации.
 */
public interface EmailSender {

    /**
     * Отправляет письмо. Реализация может выполнять отправку асинхронно.
     *
     * @param message сообщение для отправки (получатель, тема, текст)
     */
    void send(EmailMessage message);
}

package com.knowledgebase.infrastructure.notification;

import com.knowledgebase.domain.notification.EmailMessage;
import com.knowledgebase.domain.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Заглушка {@link EmailSender} по умолчанию (Infrastructure Layer).
 *
 * Активна, когда рассылка выключена ({@code app.notifications.enabled=false}
 * или свойство не задано). Письма не отправляются по SMTP — вместо этого
 * факт «письмо было бы отправлено» пишется в лог. Это позволяет запускать
 * приложение в dev/test без настроенного почтового сервера, не ломая
 * подписку на доменные события.
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("[notifications disabled] Email skipped: to={} subject='{}'",
                message.to(), message.subject());
    }
}

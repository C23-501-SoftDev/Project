package com.knowledgebase.infrastructure.notification;

import com.knowledgebase.domain.notification.EmailMessage;
import com.knowledgebase.domain.notification.EmailSender;
import com.knowledgebase.infrastructure.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Реализация {@link EmailSender} поверх Spring {@link JavaMailSender} (Infrastructure Layer).
 *
 * Активна только когда {@code app.notifications.enabled=true} — в этом случае
 * Spring Boot автоконфигурирует {@link JavaMailSender} из блока {@code spring.mail.*}.
 *
 * Отправка выполняется асинхронно в пуле {@code mailTaskExecutor}, чтобы не
 * блокировать основной поток (требование US4.3.1). Ошибки SMTP обрабатываются
 * явно и НЕ пробрасываются вызывающему коду — сбой письма не ломает бизнес-операцию.
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "enabled", havingValue = "true")
public class SpringMailEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SpringMailEmailSender.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SpringMailEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    @Async("mailTaskExecutor")
    public void send(EmailMessage message) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(properties.getFrom());
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());

            mailSender.send(mail);

            log.info("Email sent to {} (subject='{}')", message.to(), message.subject());
        } catch (MailException ex) {
            // Письмо не должно ломать основную операцию — логируем и продолжаем.
            log.error("Failed to send email to {}: {}", message.to(), ex.getMessage());
        }
    }
}

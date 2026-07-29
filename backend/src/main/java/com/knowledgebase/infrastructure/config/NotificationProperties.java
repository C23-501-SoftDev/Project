package com.knowledgebase.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки подсистемы уведомлений (Infrastructure Layer).
 *
 * Привязывается к префиксу {@code app.notifications} в application.yml:
 * <pre>
 * app:
 *   notifications:
 *     enabled: true
 *     from: no-reply@knowledgebase.local
 *     admin-email: admin@knowledgebase.local
 * </pre>
 *
 * Параметры самого SMTP-сервера (host, port, username, password, ssl/tls)
 * берутся из стандартного блока {@code spring.mail.*} и задаются через
 * переменные окружения в профилях (dev/prod).
 */
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    /** Главный выключатель рассылки. При false письма не отправляются (только лог). */
    private boolean enabled = false;

    /** Адрес отправителя (поле From). */
    private String from = "no-reply@knowledgebase.local";

    /** Адрес администратора по умолчанию (например, для тестового письма). */
    private String adminEmail = "admin@knowledgebase.local";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }
}

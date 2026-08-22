package com.knowledgebase.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки нейроассистента редактирования текста (Infrastructure Layer).
 *
 * Привязывается к префиксу {@code app.ai} в application.yml. Значения берутся
 * из переменных окружения (AI_ENABLED, AI_API_KEY, AI_MODEL и т.д.).
 *
 * Используется провайдер routerai.ru — OpenAI-совместимый API
 * ({@code POST {base-url}/chat/completions}, авторизация {@code Bearer <api-key>}).
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Главный выключатель. При false эндпоинт /api/ai/** возвращает 503. */
    private boolean enabled = false;

    /** Базовый URL OpenAI-совместимого API. */
    private String baseUrl = "https://routerai.ru/api/v1";

    /** API-ключ (передаётся в заголовке Authorization: Bearer). */
    private String apiKey = "";

    /** Идентификатор модели (самая дешёвая текстовая — deepseek/deepseek-v4-pro). */
    private String model = "deepseek/deepseek-v4-pro";

    /** Таймаут запроса к нейросети, секунды. */
    private int timeoutSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Готова ли подсистема к работе (включена и задан ключ). */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

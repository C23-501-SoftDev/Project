package com.knowledgebase.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgebase.infrastructure.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис нейроассистента для редактирования текста (Application Layer).
 *
 * Отправляет фрагмент документа в OpenAI-совместимый API routerai.ru и возвращает
 * переписанный результат. Фронтенд присылает ключ фиксированного действия или
 * пользовательскую инструкцию, итоговый промпт формируется здесь.
 */
@Service
public class AiTextService {

    private static final Logger log = LoggerFactory.getLogger(AiTextService.class);

    private static final String BASE_INSTRUCTION =
            "Ты — редактор текста в вики-системе «База знаний». На вход подаётся фрагмент "
            + "документа в формате Markdown. %s "
            + "Сохраняй Markdown-разметку и язык оригинала. "
            + "Верни ТОЛЬКО итоговый текст без пояснений, без обрамляющих кавычек и без блока ```.";

    private static final String CUSTOM_PROMPT_INSTRUCTION =
            "Выполни пользовательскую инструкцию над текстом. Инструкция автора: \"%s\". "
            + "Если инструкция просит изменить структуру, сохрани корректный Markdown.";

    /** Ключ действия -> инструкция для модели. Порядок сохраняется для UI. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("formal", "Перепиши его в официально-деловом стиле.");
        ACTIONS.put("professional", "Перепиши его в профессиональном техническом стиле, точно и по существу.");
        ACTIONS.put("simple", "Упрости текст: сделай его понятным и лёгким для чтения, убери сложные обороты.");
        ACTIONS.put("friendly", "Перепиши его в дружелюбном неформальном стиле.");
        ACTIONS.put("shorter", "Сократи текст, сохранив ключевую суть.");
        ACTIONS.put("longer", "Расширь и дополни текст деталями, сохраняя смысл.");
        ACTIONS.put("grammar", "Исправь орфографические, грамматические и пунктуационные ошибки, не меняя смысл и стиль.");
    }

    private final RestClient aiRestClient;
    private final AiProperties properties;

    public AiTextService(@Qualifier("aiRestClient") RestClient aiRestClient, AiProperties properties) {
        this.aiRestClient = aiRestClient;
        this.properties = properties;
    }

    /** Включён ли нейроассистент и задан ли ключ. */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** Список доступных действий (ключи) — для отображения на фронтенде. */
    public List<String> availableActions() {
        return List.copyOf(ACTIONS.keySet());
    }

    /**
     * Преобразует текст согласно выбранному действию или пользовательскому промпту.
     *
     * @param text   исходный Markdown-фрагмент
     * @param action ключ действия из {@link #ACTIONS}
     * @param prompt пользовательская инструкция
     * @return переписанный текст
     * @throws IllegalArgumentException если действие неизвестно или текст пуст
     * @throws AiServiceException       при ошибке обращения к нейросети
     */
    public String transform(String text, String action, String prompt) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Пустой текст для обработки");
        }
        boolean hasAction = action != null && !action.isBlank();
        boolean hasPrompt = prompt != null && !prompt.isBlank();
        if (hasAction == hasPrompt) {
            throw new IllegalArgumentException("Укажите либо тип преобразования, либо пользовательский промпт");
        }

        String instruction = hasAction ? instructionForAction(action) : instructionForPrompt(prompt);
        String systemPrompt = String.format(BASE_INSTRUCTION, instruction);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0.5);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", text)
        ));

        try {
            JsonNode response = aiRestClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                throw new AiServiceException("Нейросеть вернула пустой ответ");
            }
            return content.trim();
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Ошибка запроса к нейросети (mode={}): {}", hasAction ? action : "custom-prompt", ex.getMessage());
            throw new AiServiceException(ex.getMessage());
        }
    }

    private String instructionForAction(String action) {
        String instruction = ACTIONS.get(action);
        if (instruction == null) {
            throw new IllegalArgumentException("Неизвестное действие: " + action);
        }
        return instruction;
    }

    private String instructionForPrompt(String prompt) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (normalizedPrompt.isBlank()) {
            throw new IllegalArgumentException("Пользовательский промпт не может быть пустым");
        }
        if (normalizedPrompt.length() > 1000) {
            throw new IllegalArgumentException("Слишком длинный промпт (макс. 1000 символов)");
        }
        return String.format(CUSTOM_PROMPT_INSTRUCTION, normalizedPrompt.replace("\"", "\\\""));
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText(null);
        }
        return null;
    }

    /** Ошибка обращения к нейросети (провайдер недоступен / вернул ошибку). */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) {
            super(message);
        }
    }
}

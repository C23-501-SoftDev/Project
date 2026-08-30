package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос на преобразование текста нейроассистентом.
 */
@Schema(description = "Запрос нейроассистента: текст и тип преобразования или пользовательский промпт")
public record AiTransformRequest(

    @Schema(description = "Исходный текст (Markdown)", example = "# Заголовок\nтекст документа")
    @NotBlank(message = "Текст не может быть пустым")
    @Size(max = 20000, message = "Слишком длинный текст (макс. 20000 символов)")
    String text,

    @Schema(description = "Тип преобразования", example = "formal",
            allowableValues = {"formal", "professional", "simple", "friendly", "shorter", "longer", "grammar"})
    String action,

    @Schema(description = "Пользовательская инструкция для преобразования текста",
            example = "Перепиши как краткую инструкцию для новичка")
    @Size(max = 1000, message = "Слишком длинный промпт (макс. 1000 символов)")
    String prompt
) {
    public boolean hasAction() {
        return action != null && !action.isBlank();
    }

    public boolean hasPrompt() {
        return prompt != null && !prompt.isBlank();
    }
}

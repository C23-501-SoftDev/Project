package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос на преобразование текста нейроассистентом.
 */
@Schema(description = "Запрос нейроассистента: текст и тип преобразования")
public record AiTransformRequest(

    @Schema(description = "Исходный текст (Markdown)", example = "# Заголовок\nтекст документа")
    @NotBlank(message = "Текст не может быть пустым")
    @Size(max = 20000, message = "Слишком длинный текст (макс. 20000 символов)")
    String text,

    @Schema(description = "Тип преобразования", example = "formal",
            allowableValues = {"formal", "professional", "simple", "friendly", "shorter", "longer", "grammar"})
    @NotBlank(message = "Не указан тип преобразования")
    String action
) {
}

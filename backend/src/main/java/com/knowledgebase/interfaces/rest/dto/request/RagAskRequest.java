package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Вопрос по опубликованным статьям, доступным текущему пользователю")
public record RagAskRequest(
        @NotBlank(message = "Введите вопрос")
        @Size(max = 2000, message = "Вопрос должен содержать не более 2000 символов")
        String question,
        @Size(max = 20, message = "Выберите не более 20 пространств")
        List<@NotNull @Positive Long> spaceIds) {

    public RagAskRequest {
        question = question == null ? null : question.strip();
    }
}

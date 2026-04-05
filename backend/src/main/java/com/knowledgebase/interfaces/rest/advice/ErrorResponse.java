package com.knowledgebase.interfaces.rest.advice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Стандартный формат ответа при ошибках API.
 *
 * Все ошибки возвращаются в единообразном формате, что упрощает
 * обработку ошибок на фронтенде.
 *
 * Пример ответа:
 * <pre>
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Пользователь с ID 42 не найден",
 *   "path": "/api/admin/users/42",
 *   "fieldErrors": null
 * }
 * </pre>
 */
@Schema(description = "Стандартный ответ при ошибке")
public record ErrorResponse(

    @Schema(description = "Дата и время ошибки")
    LocalDateTime timestamp,

    @Schema(description = "HTTP статус код", example = "404")
    int status,

    @Schema(description = "Название HTTP ошибки", example = "Not Found")
    String error,

    @Schema(description = "Сообщение об ошибке", example = "Пользователь с ID 42 не найден")
    String message,

    @Schema(description = "URL запроса, вызвавшего ошибку", example = "/api/admin/users/42")
    String path,

    @Schema(description = "Список ошибок валидации полей (при ошибке 400)")
    List<FieldError> fieldErrors
) {
    /**
     * Конструктор для простых ошибок без ошибок валидации.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }

    /**
     * Конструктор для ошибок валидации с полями.
     */
    public static ErrorResponse ofValidation(String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), 400, "Bad Request", message, path, fieldErrors);
    }

    /**
     * Информация об ошибке конкретного поля (для ошибок валидации 400).
     */
    @Schema(description = "Ошибка конкретного поля")
    public record FieldError(

        @Schema(description = "Название поля", example = "email")
        String field,

        @Schema(description = "Отклонённое значение", example = "invalid-email")
        Object rejectedValue,

        @Schema(description = "Сообщение об ошибке", example = "Некорректный формат email")
        String message
    ) {}
}

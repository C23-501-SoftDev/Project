package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO ответа при успешной аутентификации (POST /api/auth/login).
 */
@Schema(description = "Ответ при успешной аутентификации")
public record LoginResponse(

    @Schema(description = "JWT токен для авторизации запросов",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token,

    @Schema(description = "Тип токена", example = "Bearer")
    String tokenType,

    @Schema(description = "Данные аутентифицированного пользователя")
    UserResponse user
) {
    /** Удобный конструктор с дефолтным tokenType = "Bearer" */
    public LoginResponse(String token, UserResponse user) {
        this(token, "Bearer", user);
    }
}
